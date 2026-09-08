package com.terra.gba.ui

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.terra.gba.core.GpspCore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** A GL surface for the 240×160 GBA framebuffer; it never draws emulation pixels with Canvas. */
class GlesGameSurface(context: Context) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(FrameRenderer())
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

private class FrameRenderer : GLSurfaceView.Renderer {
    private var program = 0
    private var texture = 0
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(16 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f,
            ))
            position(0)
        }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        texture = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0.025f, 0.035f, 0.07f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val pixels = if (GpspCore.available) GpspCore.runFrame() ?: preview() else preview()

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, WIDTH, HEIGHT, 0,
            GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, pixels,
        )
        GLES20.glUseProgram(program)
        drawQuad()
    }

    private fun drawQuad() {
        val position = GLES20.glGetAttribLocation(program, "position")
        val textureCoordinate = GLES20.glGetAttribLocation(program, "textureCoordinate")
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
        vertices.position(2)
        GLES20.glEnableVertexAttribArray(textureCoordinate)
        GLES20.glVertexAttribPointer(textureCoordinate, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun preview(): ByteBuffer = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 2)
        .order(ByteOrder.nativeOrder())
        .apply {
            repeat(HEIGHT) { y ->
                repeat(WIDTH) { x ->
                    val color = if ((x / 12 + y / 12) % 2 == 0) 0x19E7 else 0x29E9
                    putShort(color.toShort())
                }
            }
            position(0)
        }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
        }
    }

    private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
    }

    private companion object {
        const val WIDTH = 240
        const val HEIGHT = 160
        const val VERTEX_SHADER = "attribute vec2 position; attribute vec2 textureCoordinate; varying vec2 uv; void main() { gl_Position = vec4(position, 0.0, 1.0); uv = textureCoordinate; }"
        const val FRAGMENT_SHADER = "precision mediump float; varying vec2 uv; uniform sampler2D texture; void main() { gl_FragColor = texture2D(texture, uv); }"
    }
}
