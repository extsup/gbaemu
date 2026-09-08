package com.terra.gba.ui

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLU
import android.opengl.Matrix
import com.terra.gba.core.GpspCore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** A GL surface for the 240×160 GBA framebuffer; it never draws emulation pixels with Canvas. */
class GlesGameSurface(context: Context) : GLSurfaceView(context) {
    init { setEGLContextClientVersion(2); setRenderer(FrameRenderer()); renderMode = RENDERMODE_CONTINUOUSLY }
}

private class FrameRenderer : GLSurfaceView.Renderer {
    private var program = 0; private var texture = 0
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f, 0f,0f, 1f,-1f, 1f,0f, -1f,1f, 0f,1f, 1f,1f, 1f,1f)); position(0)
    }
    override fun onSurfaceCreated(config: javax.microedition.khronos.egl.EGLConfig?) {
        program = shader("attribute vec2 p;attribute vec2 uv;varying vec2 v;void main(){gl_Position=vec4(p,0.,1.);v=uv;}",
            "precision mediump float;varying vec2 v;uniform sampler2D s;void main(){gl_FragColor=texture2D(s,v);}")
        texture = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
    }
    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, w: Int, h: Int) { GLES20.glViewport(0, 0, w, h) }
    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClearColor(.025f,.035f,.07f,1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val pixels = if (GpspCore.available) GpspCore.runFrame() ?: preview() else preview()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGB,240,160,0,GLES20.GL_RGB,GLES20.GL_UNSIGNED_SHORT_5_6_5,pixels)
        GLES20.glUseProgram(program); vertices.position(0)
        val p=GLES20.glGetAttribLocation(program,"p"); val uv=GLES20.glGetAttribLocation(program,"uv")
        GLES20.glEnableVertexAttribArray(p); GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,16,vertices)
        vertices.position(2); GLES20.glEnableVertexAttribArray(uv); GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
    }
    private fun preview(): ByteBuffer = ByteBuffer.allocateDirect(240*160*2).order(ByteOrder.nativeOrder()).apply {
        repeat(160) { y -> repeat(240) { x -> putShort(if ((x/12+y/12)%2==0) 0x19E7 else 0x29E9) } }; position(0)
    }
    private fun shader(v:String,f:String):Int { fun compile(t:Int,s:String)=GLES20.glCreateShader(t).also { GLES20.glShaderSource(it,s); GLES20.glCompileShader(it) }; return GLES20.glCreateProgram().also { GLES20.glAttachShader(it,compile(GLES20.GL_VERTEX_SHADER,v)); GLES20.glAttachShader(it,compile(GLES20.GL_FRAGMENT_SHADER,f)); GLES20.glLinkProgram(it) } }
}
