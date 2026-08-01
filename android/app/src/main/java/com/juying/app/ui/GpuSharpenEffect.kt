package com.juying.app.ui

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * User-visible Anime4K-style GPU processing modes.
 *
 * The Android app remains on Media3 so playback, offline files, PiP and source
 * failover continue to share one state machine. Lanerc uses libmpv's
 * `glsl-shaders` property; here the equivalent local GLSL work is hosted by
 * Media3's OpenGL frame processor instead of introducing a second player.
 */
internal enum class Anime4kMode(val label: String) {
    OFF("关闭"),
    PERFORMANCE("性能优先（推荐）"),
    HIGH_QUALITY("高质量")
}

/**
 * A real local GPU upscaling and adaptive line-restoration pass inspired by
 * Anime4K. It increases the processing texture size (up to the mode cap) and
 * reconstructs animation edges; it does not claim to be a 4K source, an NPU
 * model, or to recover detail that is absent from the input video.
 */
@UnstableApi
internal class Anime4kGpuEffect(
    private val mode: Anime4kMode
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) {
            throw VideoFrameProcessingException(
                IllegalArgumentException("Anime4K GPU enhancement currently supports SDR video only")
            )
        }
        require(mode != Anime4kMode.OFF)
        return Anime4kShaderProgram(context, mode)
    }
}

@UnstableApi
private class Anime4kShaderProgram(
    context: Context,
    private val mode: Anime4kMode
) : BaseGlShaderProgram(false, 1) {
    private val glProgram: GlProgram = try {
        GlProgram(
            context,
            "shaders/vertex_shader_transformation_es2.glsl",
            "shaders/fragment_shader_anime4k_es2.glsl"
        )
    } catch (error: Exception) {
        throw VideoFrameProcessingException(error)
    }

    init {
        try {
            glProgram.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4)
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identity)
            glProgram.setFloatUniform(
                "uEdgeStrength",
                if (mode == Anime4kMode.HIGH_QUALITY) 0.34f else 0.22f
            )
            glProgram.setFloatUniform(
                "uLineStrength",
                if (mode == Anime4kMode.HIGH_QUALITY) 0.18f else 0.10f
            )
        } catch (error: Exception) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val safeWidth = inputWidth.coerceAtLeast(1)
        val safeHeight = inputHeight.coerceAtLeast(1)
        val maxWidth = if (mode == Anime4kMode.HIGH_QUALITY) 3840 else 2560
        val maxHeight = if (mode == Anime4kMode.HIGH_QUALITY) 2160 else 1440
        val scale = min(
            2f,
            min(maxWidth.toFloat() / safeWidth, maxHeight.toFloat() / safeHeight)
        ).coerceAtLeast(1f)
        try {
            glProgram.setFloatUniform("uTexelWidth", 1f / safeWidth)
            glProgram.setFloatUniform("uTexelHeight", 1f / safeHeight)
        } catch (error: Exception) {
            throw VideoFrameProcessingException(error)
        }
        return Size(
            (safeWidth * scale).roundToInt().coerceAtLeast(safeWidth),
            (safeHeight * scale).roundToInt().coerceAtLeast(safeHeight)
        )
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (error: Exception) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }
}
