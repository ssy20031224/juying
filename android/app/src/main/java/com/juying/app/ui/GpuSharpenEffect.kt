package com.juying.app.ui

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.FloatRange
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Lightweight real-time unsharp-mask pass executed by Media3's OpenGL video
 * effect pipeline. This improves edge definition but deliberately does not
 * claim to invent 4K detail or use an NPU model.
 */
@UnstableApi
internal class GpuSharpenEffect(
    @FloatRange(from = 0.0, to = 0.35)
    private val strength: Float = 0.12f
) : GlEffect {
    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean
    ): GlShaderProgram {
        if (useHdr) {
            throw VideoFrameProcessingException(
                IllegalArgumentException("GPU sharpen currently supports SDR video only")
            )
        }
        return GpuSharpenShaderProgram(context, strength.coerceIn(0f, 0.35f))
    }
}

@UnstableApi
private class GpuSharpenShaderProgram(
    context: Context,
    private val strength: Float
) : BaseGlShaderProgram(false, 1) {
    private val glProgram: GlProgram = try {
        GlProgram(
            context,
            "shaders/vertex_shader_transformation_es2.glsl",
            "shaders/fragment_shader_sharpen_es2.glsl"
        )
    } catch (error: Exception) {
        throw VideoFrameProcessingException(error)
    }

    init {
        try {
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                4
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identity)
            glProgram.setFloatUniform("uSharpenStrength", strength)
        } catch (error: Exception) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        try {
            glProgram.setFloatUniform("uTexelWidth", 1f / inputWidth.coerceAtLeast(1))
            glProgram.setFloatUniform("uTexelHeight", 1f / inputHeight.coerceAtLeast(1))
        } catch (error: Exception) {
            throw VideoFrameProcessingException(error)
        }
        return Size(inputWidth, inputHeight)
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
