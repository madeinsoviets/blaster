package com.blaster.texture

import com.blaster.math.Vec3

data class ConstantTexture(val color: Vec3) : Texture {
    override fun value(u: Float, v: Float, point: Vec3) = color
}