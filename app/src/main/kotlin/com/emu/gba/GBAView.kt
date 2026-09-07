package com.emu.gba

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GBAView(context: Context) : GLSurfaceView(context) {
    private val gbaRenderer = GBARenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(gbaRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun pause() { onPause() }
    fun resume() { onResume() }

    private class GBARenderer : GLSurfaceView.Renderer {
        private val GBA_W = 240
        private val GBA_H = 160
        private val frameBuffer = IntArray(GBA_W * GBA_H)
        private val frameBufferSize = GBA_W * GBA_H

        private var program = 0
        private var textureId = 0
        private var positionHandle = 0
        private var texCoordHandle = 0

        private val vertices = floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        )

        private var vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }

        private val vertexShaderCode = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        private val fragmentShaderCode = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
            program = GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vertexShader)
                GLES20.glAttachShader(it, fragmentShader)
                GLES20.glLinkProgram(it)
            }
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            textureId = texIds[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, GBA_W, GBA_H, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            val gameRatio = GBA_W.toFloat() / GBA_H.toFloat()
            val screenRatio = width.toFloat() / height.toFloat()
            
            var vw = width
            var vh = height
            var vx = 0
            var vy = 0

            if (screenRatio > gameRatio) {
                vh = height
                vw = (height * gameRatio).toInt()
                vx = (width - vw) / 2
                vy = 0
            } else {
                vw = width
                vh = (width / gameRatio).toInt()
                vx = 0
                vy = (height - vh)
            }

            GLES20.glViewport(vx, vy, vw, vh)
        }

        override fun onDrawFrame(gl: GL10?) {
            GBAEngine.nativeRunFrame()
            GBAEngine.nativeGetFramebuffer(frameBuffer)

            for (i in frameBuffer.indices) {
                val c = frameBuffer[i]
                val r = (c shr 16) and 0xFF
                val b = c and 0xFF
                frameBuffer[i] = (c and 0xFF00FF00.toInt()) or (b shl 16) or r
            }

            val buffer = ByteBuffer.allocateDirect(frameBufferSize * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer()
            buffer.put(frameBuffer)
            buffer.position(0)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, GBA_W, GBA_H, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            vertexBuffer.position(2)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
            vertexBuffer.position(0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}
