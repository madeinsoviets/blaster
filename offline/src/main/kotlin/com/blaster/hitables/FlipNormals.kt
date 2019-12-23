package com.blaster.hitables

import com.blaster.math.AABB
import com.blaster.tracing.HitRecord
import com.blaster.math.Ray

data class FlipNormals(val hitable: Hitable) : Hitable {
    override fun hit(ray: Ray, tMin: Float, tMax: Float): HitRecord? {
        val hit = hitable.hit(ray, tMin, tMax)
        if (hit != null) {
            hit.normal = hit.normal.negate()
        }
        return hit
    }

    override fun aabb(): AABB = hitable.aabb()
}