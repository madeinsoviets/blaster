package com.blaster.hitables

import com.blaster.material.Material
import com.blaster.math.AABB
import com.blaster.math.Ray
import com.blaster.math.Vec3
import com.blaster.tracing.HitRecord

data class RectXY(
    val x0: Float, val x1: Float,
    val y0: Float, val y1: Float,
    val k: Float,
    val material: Material
) : Hitable {

    private val aabb = AABB(Vec3(x0, y0, k - 0.0001f), Vec3(x1, y1, k + 0.0001f))

    override fun aabb(): AABB = aabb

    override fun hit(ray: Ray, tMin: Float, tMax: Float): HitRecord? {
        val t = (k - ray.origin.z) / ray.direction.z
        if (t < tMin || t > tMax) {
            return null
        }
        val x = ray.origin.x + t * ray.direction.x
        val y = ray.origin.y + t * ray.direction.y
        if (x < x0 || x > x1 || y < y0 || y > y1) {
            return null
        }
        val u = (x - x0) / (x1 - x0)
        val v = (y - y0) / (y1 - y0)
        return HitRecord(t, ray.pointAtParameter(t), u, v, Vec3(0f, 0f, 1f), material)
    }
}