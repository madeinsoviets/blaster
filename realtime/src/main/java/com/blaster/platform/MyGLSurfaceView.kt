package com.blaster.platform

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class MyGLSurfaceView(context: Context?, attrs: AttributeSet?) : GLSurfaceView(context, attrs) {
    /*private val pixelDecoder = object : PixelDecoder() {
        override fun decodePixels(inputStream: InputStream): Decoded {
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val decoded = BitmapFactory.decodeStream(inputStream, null, options)
            val buffer = ByteBuffer.allocateDirect(decoded!!.byteCount).order(ByteOrder.nativeOrder())
            decoded.copyPixelsToBuffer(buffer)
            buffer.position(0)
            return Decoded(buffer, decoded.width, decoded.height)
                    .also { decoded.recycle() }
        }
    }

    private val simple = object : Renderer {
        //private val renderer = SimpleRenderer(pixelDecoder)

        override fun onDrawFrame(gl: GL10?) {
            renderer.onDraw()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            renderer.onChange(width, height)
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            renderer.onCreate()
        }
    }

    private val deferred = object : Renderer {
        private val renderer = DeferredRenderer()

        override fun onDrawFrame(gl: GL10?) {
            renderer.onDraw()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            renderer.onChange(width, height)
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            renderer.onCreate()
        }
    }

    init {
        setEGLContextClientVersion(2)
        //setRenderer(simple)
        setRenderer(deferred)
    }*/
}