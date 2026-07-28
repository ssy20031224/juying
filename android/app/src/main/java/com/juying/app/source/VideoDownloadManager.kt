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
        val suffix = if (isHls(url)) "m3u8" else "mp4"
        val output = File(root, "${safe(episodeName)}.$suffix")
        if (output.exists() && output.length() > 0L) {
            return@withContext DownloadedFile(output, true)
        }

        val request = request(url, headers, referer)
        val response = try { client.newCall(request).execute() } catch (_: Exception) { return@withContext null }
        response.use { res ->
            if (!res.isSuccessful) return@withContext null
            if (!isHls(url) && !isHls(res.header("Content-Type").orEmpty())) {
                val body = res.body ?: return@withContext null
                val total = body.contentLength().coerceAtLeast(0L)
                body.byteStream().use { input ->
                    output.outputStream().use { out ->
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
                return@withContext DownloadedFile(output, true)
            }

            val playlist = res.body?.string().orEmpty()
            if (playlist.isBlank()) return@withContext null
            val segmentLines = playlist.lines().filter { it.isNotBlank() && !it.startsWith("#") }
            if (segmentLines.isEmpty() || !playlist.contains("#EXTINF")) {
                // Master playlists are saved for re-resolution rather than
                // pretending they are fully offline.
                output.writeText(playlist)
                return@withContext DownloadedFile(output, false)
            }

            val rewritten = StringBuilder()
            var downloaded = 0L
            val total = segmentLines.size.toLong()
            playlist.lines().forEach { line ->
                if (line.isBlank() || line.startsWith("#")) {
                    rewritten.appendLine(line)
                } else {
                    val segmentName = "segment_${downloaded.toString().padStart(5, '0')}.ts"
                    val segmentUrl = resolve(url, line)
                    val segmentRequest = request(segmentUrl, headers, referer)
                    val segmentResponse = try { client.newCall(segmentRequest).execute() } catch (_: Exception) { null }
                    segmentResponse?.use { segment ->
                        if (segment.isSuccessful && segment.body != null) {
                            File(root, segmentName).outputStream().use { out ->
                                segment.body!!.byteStream().use { input -> input.copyTo(out) }
                            }
                            rewritten.appendLine(segmentName)
                            downloaded += 1
                            onProgress(downloaded, total)
                        } else {
                            rewritten.appendLine(line)
                        }
                    } ?: rewritten.appendLine(line)
                }
            }
            output.writeText(rewritten.toString())
            return@withContext DownloadedFile(output, downloaded > 0)
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
            if (videoFile.exists()) videoFile.delete()
            val infoFile = File(parent, "${safe(item.episodeName)}.info")
            if (infoFile.exists()) infoFile.delete()
            parent?.listFiles()?.filter { it.name.startsWith("segment_") }?.forEach { it.delete() }
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
                file.parentFile?.listFiles()?.filter { it.name.startsWith("segment_") }?.forEach { total += it.length() }
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
