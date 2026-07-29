package com.juying.app.source

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.net.URI

/**
 * Downloads resolved play streams for offline use.
 *
 * Direct MP4 streams are copied as MP4. Simple, non-encrypted HLS playlists
 * are downloaded together with their TS/fMP4 segments and rewritten to a
 * local relative m3u8 playlist. Encrypted/adaptive playlists are kept as a
 * playlist only and remain subject to the source's access policy.
 */
class VideoDownloadManager(private val context: Context) {
    data class DownloadedFile(val file: File, val playableOffline: Boolean)

    private val client by lazy { com.juying.app.engine.NetworkClient.create(context) }

    suspend fun download(
        url: String,
        headers: Map<String, String>?,
        referer: String?,
        title: String,
        episodeName: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): DownloadedFile? = withContext(Dispatchers.IO) {
        val root = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            "lanerc/${safe(title)}"
        )
        root.mkdirs()
        var suffix = if (isHls(url)) "m3u8" else "mp4"
        var output = File(root, "${safe(episodeName)}.$suffix")
        val infoFile = File(root, "${safe(episodeName)}.info")
        if (output.exists() && output.length() > 0L) {
            val complete = if (suffix == "m3u8") {
                isOfflineHlsComplete(output)
            } else {
                infoFile.exists()
            }
            if (complete) return@withContext DownloadedFile(output, true)
            output.delete()
        }

        val request = request(url, headers, referer)
        val response = try { client.newCall(request).execute() } catch (_: Exception) { return@withContext null }
        response.use { res ->
            if (!res.isSuccessful) return@withContext null
            val responseIsHls = isHls(url) || isHls(res.header("Content-Type").orEmpty())
            if (responseIsHls && suffix != "m3u8") {
                suffix = "m3u8"
                output = File(root, "${safe(episodeName)}.$suffix")
                if (output.exists() && isOfflineHlsComplete(output)) {
                    return@withContext DownloadedFile(output, true)
                }
                output.delete()
            }
            if (!responseIsHls) {
                val body = res.body ?: return@withContext null
                val total = body.contentLength().coerceAtLeast(0L)
                val partial = File(root, "${output.name}.part")
                partial.delete()
                body.byteStream().use { input ->
                    partial.outputStream().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var done = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } >= 0) {
                            if (read == 0) continue
                            out.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
                if (partial.length() <= 0L) {
                    partial.delete()
                    return@withContext null
                }
                if (!partial.renameTo(output)) {
                    try {
                        partial.copyTo(output, overwrite = true)
                        partial.delete()
                    } catch (_: Exception) {
                        partial.delete()
                        output.delete()
                        return@withContext null
                    }
                }
                return@withContext DownloadedFile(output, true)
            }

            val playlist = res.body?.string().orEmpty()
            if (playlist.isBlank()) return@withContext null
            val segmentLines = playlist.lines().filter { it.isNotBlank() && !it.startsWith("#") }
            if (segmentLines.isEmpty() || !playlist.contains("#EXTINF")) {
                // A master playlist is not a completed offline video. Do not
                // leave it behind where the downloads screen can mistake it
                // for a playable cache entry.
                return@withContext DownloadedFile(output, false)
            }

            val rewritten = StringBuilder()
            val episodePrefix = safe(episodeName)
            val resourceUriRegex = Regex("URI=\"([^\"]+)\"")
            val directiveResources = playlist.lines().count { line ->
                (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP")) &&
                    resourceUriRegex.containsMatchIn(line) &&
                    !line.contains("METHOD=NONE")
            }
            var downloaded = 0L
            val total = (segmentLines.size + directiveResources).toLong()
            var segmentIndex = 0
            var auxiliaryIndex = 0
            var complete = true
            playlist.lines().forEach { line ->
                if (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP")) {
                    val match = resourceUriRegex.find(line)
                    if (match == null || line.contains("METHOD=NONE")) {
                        rewritten.appendLine(line)
                    } else {
                        val child = match.groupValues[1]
                        val remoteUrl = resolve(url, child)
                        val defaultExtension = if (line.startsWith("#EXT-X-KEY")) "key" else "mp4"
                        val localName = "${episodePrefix}_aux_${auxiliaryIndex.toString().padStart(3, '0')}." +
                            resourceExtension(remoteUrl, defaultExtension)
                        auxiliaryIndex += 1
                        if (downloadResource(remoteUrl, File(root, localName), headers, referer)) {
                            rewritten.appendLine(line.replace(child, localName))
                            downloaded += 1
                            onProgress(downloaded, total)
                        } else {
                            complete = false
                        }
                    }
                } else if (line.isBlank() || line.startsWith("#")) {
                    rewritten.appendLine(line)
                } else {
                    val segmentUrl = resolve(url, line)
                    val segmentName = "${episodePrefix}_segment_${segmentIndex.toString().padStart(5, '0')}." +
                        resourceExtension(segmentUrl, "ts")
                    segmentIndex += 1
                    if (downloadResource(segmentUrl, File(root, segmentName), headers, referer)) {
                        rewritten.appendLine(segmentName)
                        downloaded += 1
                        onProgress(downloaded, total)
                    } else {
                        complete = false
                    }
                }
            }
            if (!complete || downloaded != total || total <= 0L) {
                root.listFiles()
                    ?.filter { it.name.startsWith("${episodePrefix}_segment_") || it.name.startsWith("${episodePrefix}_aux_") }
                    ?.forEach { it.delete() }
                output.delete()
                return@withContext DownloadedFile(output, false)
            }
            val partialPlaylist = File(root, "${output.name}.part")
            partialPlaylist.writeText(rewritten.toString())
            if (!partialPlaylist.renameTo(output)) {
                partialPlaylist.copyTo(output, overwrite = true)
                partialPlaylist.delete()
            }
            return@withContext DownloadedFile(output, isOfflineHlsComplete(output))
        }
    }

    suspend fun downloadCover(coverUrl: String, title: String): File? = withContext(Dispatchers.IO) {
        if (coverUrl.isBlank()) return@withContext null
        val root = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            "lanerc/${safe(title)}"
        )
        root.mkdirs()
        val coverFile = File(root, "cover.jpg")
        if (coverFile.exists() && coverFile.length() > 0L) return@withContext coverFile

        try {
            val req = Request.Builder().url(coverUrl).get().build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful && res.body != null) {
                    coverFile.outputStream().use { out ->
                        res.body!!.byteStream().use { input -> input.copyTo(out) }
                    }
                    return@withContext coverFile
                }
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    suspend fun saveMetadata(title: String, episodeName: String, coverUrl: String, videoFile: File) = withContext(Dispatchers.IO) {
        val root = videoFile.parentFile ?: return@withContext
        val coverFile = downloadCover(coverUrl, title)
        val infoFile = File(root, "${safe(episodeName)}.info")
        val content = """
            title=$title
            episodeName=$episodeName
            coverPath=${coverFile?.absolutePath.orEmpty()}
            videoPath=${videoFile.absolutePath}
        """.trimIndent()
        try { infoFile.writeText(content) } catch (_: Exception) {}
    }

    fun getDownloadedItems(): List<DownloadedItemInfo> {
        val rootDir = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            "lanerc"
        )
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()

        val results = mutableListOf<DownloadedItemInfo>()
        rootDir.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
            folder.listFiles()?.filter { it.name.endsWith(".info") }?.forEach { infoFile ->
                try {
                    val lines = infoFile.readLines().associate { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
                    }
                    val title = lines["title"].orEmpty().ifBlank { folder.name }
                    val episodeName = lines["episodeName"].orEmpty().ifBlank { infoFile.nameWithoutExtension }
                    val videoPath = lines["videoPath"].orEmpty()
                    val videoFile = File(videoPath).takeIf { it.exists() && it.length() > 0L }
                        ?: folder.listFiles()?.firstOrNull { (it.name.endsWith(".mp4") || it.name.endsWith(".m3u8")) && it.name.startsWith(safe(episodeName)) }

                    if (videoFile != null && videoFile.exists()) {
                        val coverPath = lines["coverPath"].orEmpty().ifBlank {
                            File(folder, "cover.jpg").takeIf { it.exists() }?.absolutePath
                        }
                        val sizeBytes = calculateFolderSize(videoFile)
                        val sizeFormatted = formatFileSize(sizeBytes)

                        results.add(
                            DownloadedItemInfo(
                                title = title,
                                episodeName = episodeName,
                                videoFile = videoFile,
                                coverPath = coverPath,
                                fileSizeFormatted = sizeFormatted
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        return results.sortedByDescending { it.videoFile.lastModified() }
    }

    fun deleteDownload(item: DownloadedItemInfo): Boolean {
        try {
            val videoFile = item.videoFile
            val parent = videoFile.parentFile
            if (videoFile.extension.equals("m3u8", ignoreCase = true)) {
                localPlaylistResources(videoFile).forEach { it.delete() }
            }
            if (videoFile.exists()) videoFile.delete()
            val infoFile = File(parent, "${safe(item.episodeName)}.info")
            if (infoFile.exists()) infoFile.delete()
            val episodePrefix = safe(item.episodeName)
            parent?.listFiles()
                ?.filter { it.name.startsWith("${episodePrefix}_segment_") || it.name.startsWith("${episodePrefix}_aux_") }
                ?.forEach { it.delete() }
            if (parent != null && parent.listFiles().isNullOrEmpty()) {
                parent.delete()
            }
            return true
        } catch (_: Exception) { return false }
    }

    private fun calculateFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) {
            if (file.name.endsWith(".m3u8")) {
                var total = file.length()
                localPlaylistResources(file).forEach { total += it.length() }
                return total
            }
            return file.length()
        }
        return 0L
    }

    private fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "未知大小"
        val mb = sizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    private fun request(url: String, headers: Map<String, String>?, referer: String?): Request {
        val builder = Request.Builder().url(url).get()
        referer?.takeIf { it.isNotBlank() && !it.equals("never", true) }?.let {
            builder.header("Referer", it)
        }
        headers.orEmpty().forEach { (key, value) ->
            if (value.isNotBlank() && !key.equals("user-agent", true)) builder.header(key, value)
        }
        headers.orEmpty().entries.firstOrNull { it.key.equals("user-agent", true) }?.value?.let {
            builder.header("User-Agent", it)
        }
        return builder.build()
    }

    private fun downloadResource(
        url: String,
        destination: File,
        headers: Map<String, String>?,
        referer: String?
    ): Boolean {
        val partial = File(destination.parentFile, "${destination.name}.part")
        partial.delete()
        val response = try {
            client.newCall(request(url, headers, referer)).execute()
        } catch (_: Exception) {
            return false
        }
        response.use { resource ->
            val body = resource.body
            if (!resource.isSuccessful || body == null) return false
            return try {
                partial.outputStream().use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                }
                if (partial.length() <= 0L) {
                    partial.delete()
                    false
                } else {
                    if (!partial.renameTo(destination)) {
                        partial.copyTo(destination, overwrite = true)
                        partial.delete()
                    }
                    true
                }
            } catch (_: Exception) {
                partial.delete()
                false
            }
        }
    }

    private fun isOfflineHlsComplete(playlist: File): Boolean {
        if (!playlist.exists() || playlist.length() <= 0L) return false
        val text = try { playlist.readText() } catch (_: Exception) { return false }
        if (!text.contains("#EXTINF")) return false
        val resources = localPlaylistResources(playlist)
        val uriRegex = Regex("URI=\"([^\"]+)\"")
        val expectedResources = text.lines().count { line ->
            (line.isNotBlank() && !line.startsWith("#")) ||
                ((line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP")) &&
                    uriRegex.containsMatchIn(line) &&
                    !line.contains("METHOD=NONE"))
        }
        if (expectedResources <= 0 || resources.size < expectedResources) return false
        return resources.all { it.exists() && it.length() > 0L }
    }

    private fun localPlaylistResources(playlist: File): Set<File> {
        val parent = playlist.parentFile ?: return emptySet()
        val uriRegex = Regex("URI=\"([^\"]+)\"")
        return try {
            buildSet {
                playlist.readLines().forEach { line ->
                    val reference = when {
                        line.isNotBlank() && !line.startsWith("#") -> line.trim()
                        line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP") ->
                            uriRegex.find(line)?.groupValues?.getOrNull(1)
                        else -> null
                    } ?: return@forEach
                    if (!reference.startsWith("http://", true) &&
                        !reference.startsWith("https://", true)
                    ) {
                        add(File(parent, Uri.decode(reference)))
                    }
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun resourceExtension(url: String, fallback: String): String {
        val candidate = try {
            URI(url).path.substringAfterLast('.', "")
        } catch (_: Exception) {
            ""
        }
        return candidate.lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            ?: fallback
    }

    private fun resolve(base: String, child: String): String =
        try { URI(base).resolve(Uri.decode(child)).toString() } catch (_: Exception) { child }

    private fun isHls(value: String): Boolean =
        value.lowercase().contains(".m3u8") || value.lowercase().contains("mpegurl")

    private fun safe(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(80).ifBlank { "video" }
}

data class DownloadedItemInfo(
    val title: String,
    val episodeName: String,
    val videoFile: File,
    val coverPath: String?,
    val fileSizeFormatted: String
)
