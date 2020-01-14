package com.blaster.common

import org.joml.AABBf
import org.joml.Vector2f
import org.joml.Vector3f
import java.util.*

val VECTOR_2D_CENTER = Vector2f()
val VECTOR_2D_LEFT = Vector2f(-1f, 0f)

val VECTOR_UP = Vector3f(0f, 1f, 0f)

private val random = Random()
fun randomFloat(min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE) =
        min + random.nextFloat() * (max - min)
fun randomVector3f(min: Vector3f, max: Vector3f) =
        Vector3f(randomFloat(min.x, max.x), randomFloat(min.y, max.y), randomFloat(min.z, max.z))

fun extractColors(hex: String): Vector3f {
    val integerHex = Integer.parseInt(hex, 16)
    val rIntValue = (integerHex / 256 / 256) % 256
    val gIntValue = (integerHex / 256      ) % 256
    val bIntValue = (integerHex            ) % 256
    return Vector3f(rIntValue / 255.0f, gIntValue / 255.0f, bIntValue / 255.0f)
}

fun AABBf.width() = maxX - minX
fun AABBf.height() = maxY - minY
fun AABBf.depth() = maxZ - minZ
fun AABBf.center() = Vector3f(minX + (maxX - minX) / 2f, minY + (maxY - minY) / 2f, minZ + (maxZ - minZ) / 2f)

fun lerpf(from: Float, to: Float, t: Float) = (1f - t) * from + t * to