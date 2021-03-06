package com.blaster.material

import com.blaster.hitables.HitRecord
import com.blaster.texture.Texture
import com.blaster.math.Ray
import com.blaster.math.Vec3

data class IsotropicMaterial(var texture: Texture) : Material {
    override fun scattered(ray: Ray, hit: HitRecord): ScatterResult? {
        val scattered = Ray(hit.point, Vec3.randomInUnitSphere())
        return ScatterResult(texture.value(hit.u, hit.v, hit.point), scattered)
    }

    override fun emitted(u: Float, v: Float, point: Vec3)= Vec3()
}