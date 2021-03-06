package com.blaster.gl

import java.nio.ByteBuffer

private val backend = GlLocator.locate()

data class GlTexData(
        val internalFormat: Int = backend.GL_RGBA,
        val pixelFormat: Int = backend.GL_RGBA,
        val pixelType: Int = backend.GL_UNSIGNED_BYTE,
        val width: Int, val height: Int, val pixels: ByteBuffer?)

class GlTexture(val target: Int = backend.GL_TEXTURE_2D, val unit: Int = 0) : GlBindable {
    var handle: Int? = null

    init {
        handle = glCheck { backend.glGenTextures() }
        check(handle!! > 0)
    }

    init {
        glBind(this) {
            glCheck { backend.glTexParameteri(target, backend.GL_TEXTURE_MIN_FILTER, backend.GL_NEAREST) }
            glCheck { backend.glTexParameteri(target, backend.GL_TEXTURE_MAG_FILTER, backend.GL_NEAREST) }
            glCheck { backend.glTexParameteri(target, backend.GL_TEXTURE_WRAP_S, backend.GL_CLAMP_TO_EDGE) } // todo: mirrored repeat, GL_CLAMP_TO_EDGE
            glCheck { backend.glTexParameteri(target, backend.GL_TEXTURE_WRAP_T, backend.GL_CLAMP_TO_EDGE) } // todo: mirrored repeat, GL_CLAMP_TO_EDGE
            glCheck { backend.glTexParameteri(target, backend.GL_TEXTURE_WRAP_R, backend.GL_CLAMP_TO_EDGE) } // todo: mirrored repeat, GL_CLAMP_TO_EDGE
        }
    }

    constructor(target: Int = backend.GL_TEXTURE_2D, unit: Int = 0,
                width: Int, height: Int,
                internalFormat: Int = backend.GL_RGBA, pixelFormat: Int = backend.GL_RGBA, pixelType: Int = backend.GL_UNSIGNED_BYTE,
                pixels: ByteBuffer? = null) : this(target, unit) {
        glBind(this) {
            glCheck { backend.glTexImage2D(target, 0, internalFormat, width, height, 0, pixelFormat, pixelType, pixels) }
        }
    }

    constructor(unit: Int = 0, sides: List<GlTexData>) : this(backend.GL_TEXTURE_CUBE_MAP, unit) {
        loadCubemap(sides)
    }

    private fun loadCubemap(sides: List<GlTexData>) {
        glBind(this) {
            sides.forEachIndexed { index, side ->
                glCheck { backend.glTexImage2D(backend.GL_TEXTURE_CUBE_MAP_POSITIVE_X + index, 0,
                        side.internalFormat, side.width, side.height, 0,
                        side.pixelFormat, side.pixelType, side.pixels) }
            }
        }
    }

    fun updatePixels(uOffset: Int, vOffset: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteBuffer) {
        glBind(this) {
            glCheck { backend.glTexSubImage2D(target, 0, uOffset, vOffset, width, height, format, type, pixels) }
        }
    }

    fun free() {
        glCheck { backend.glDeleteTextures(handle!!) }
        handle = null
    }

    override fun bind() {
        glCheck { backend.glActiveTexture(backend.GL_TEXTURE0 + unit) }
        glCheck { backend.glBindTexture(target, handle!!) }
    }

    override fun unbind() {
        glCheck {
            backend.glActiveTexture(backend.GL_TEXTURE0 + unit)
            backend.glBindTexture(target, 0)
        }
    }
}