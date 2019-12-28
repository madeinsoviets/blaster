package com.blaster.gl

class GLMesh(
        private val verticesBuffer: GLBuffer,
        private val indicesBuffer: GLBuffer,
        private val indicesCount: Int,
        private val attributes: List<GLAttribute>) : GLBindable {

    private val backend = GLBackendLocator.instance()

    constructor(vertices: FloatArray, indices: IntArray, attributes: List<GLAttribute>)
            : this(
                GLBuffer(GLBackendLocator.instance().GL_ARRAY_BUFFER, vertices),
                GLBuffer(GLBackendLocator.instance().GL_ELEMENT_ARRAY_BUFFER, indices),
                indices.size, attributes)

    private fun bindVertexPointers() {
        var stride = 0
        var offset = 0
        attributes.forEach { stride += it.size * 4 }
        attributes.forEach {
            glCheck {
                backend.glEnableVertexAttribArray(it.location)
                backend.glVertexAttribPointer(it.location, it.size, backend.GL_FLOAT, false, stride, offset)
            }
            offset += it.size * 4
        }
    }

    private fun disableVertexPointers() {
        attributes.forEach {
            glCheck { backend.glDisableVertexAttribArray(it.location) }
        }
    }

    override fun bind() {
        indicesBuffer.bind()
        verticesBuffer.bind()
        bindVertexPointers()
    }

    override fun unbind() {
        disableVertexPointers()
        verticesBuffer.unbind()
        indicesBuffer.unbind()
    }

    fun draw(mode: Int = backend.GL_TRIANGLES) {
        glCheck { backend.glDrawElements(mode, indicesCount, backend.GL_UNSIGNED_INT, 0) }
    }
}