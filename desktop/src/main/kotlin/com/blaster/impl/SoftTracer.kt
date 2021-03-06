package com.blaster.impl

import com.blaster.assets.AssetStream
import com.blaster.assets.ShadersLib
import com.blaster.auxiliary.*
import com.blaster.entity.Controller
import com.blaster.entity.Light
import com.blaster.entity.Material
import com.blaster.gl.GlLocator
import com.blaster.gl.GlMesh
import com.blaster.gl.GlState
import com.blaster.gl.GlTexture
import com.blaster.platform.LwjglWindow
import com.blaster.platform.WasdInput
import com.blaster.techniques.SimpleTechnique
import kotlinx.coroutines.*
import org.joml.Intersectionf
import java.lang.Float.max
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

private val backend = GlLocator.locate()

private const val VIEWPORT_WIDTH = 10.2f
private const val VIEWPORT_HEIGHT = 7.6f

private const val FOCUS_DISTANCE = 10.0f

private const val VIEWPORT_LEFT = (-VIEWPORT_WIDTH / 2f).toInt()
private const val VIEWPORT_BOTTOM = (-VIEWPORT_HEIGHT / 2f).toInt()

private const val RESOLUTION_WIDTH = 1024
private const val RESOLUTION_HEIGHT = 768

private const val REGION_WIDTH = 128
private const val REGION_HEIGHT = 128

private const val REGIONS_CNT_U = RESOLUTION_WIDTH / REGION_WIDTH
private const val REGIONS_CNT_V = RESOLUTION_HEIGHT / REGION_HEIGHT

private val NANOS_PER_FRAME = TimeUnit.MILLISECONDS.toNanos(16)

private const val PIXEL_SIZE = 3 // r, g, b

private lateinit var viewportRect: GlMesh
private lateinit var viewportTexture: GlTexture

private val upVec = vec3().up()
private val viewM = mat4().identity()
private val projectionM = mat4().identity()
private val modelM = mat4().identity()

private var mouseControl = false
private var keyPressed = false

private val assetStream = AssetStream()
private val shadersLib = ShadersLib(assetStream)

private val camera = RtrCamera()
private val sceneVersion = Version()

private val controller = Controller(velocity = 1f, position = vec3().back())
private val wasdInput = WasdInput(controller)

private val simpleTechnique = SimpleTechnique()

private val spheres = (1..100)
        .map {
            HitableSphere(
                vec3().rand(vec3(-50f), vec3(50f)),
                randf(1f, 5f),
                Material.MATERIALS.values.random())
        }
        .toList()

private val scene = HitableGroup(spheres)

private val light = Light(color().yellow(), true)
private val lightPos = vec3().back()

private val regionsLow = mutableListOf<RegionTask>()
private val regionsMed = mutableListOf<RegionTask>()
private val regionsHgh = mutableListOf<RegionTask>()

private val regionsDone = Collections.synchronizedList(mutableListOf<RegionTask>())

private var currentJob: Job = Job()

private val blinnPhongRtrTechnique = RtrBlinnPhongTechnique()
private val background = mutableListOf<color>()

private val mainDispatcher = Executor { runnable -> runnable.run() }.asCoroutineDispatcher()

private fun createBackground() {
    for (v in 0 until RESOLUTION_HEIGHT) {
        val color = color().cian().mul(v.toFloat() / RESOLUTION_HEIGHT)
        for (u in 0 until RESOLUTION_WIDTH) {
            background.add(color)
        }
    }
}

private class RegionTask(val u: Int, val v: Int, val uStep: Int, val vStep: Int) : Comparable<RegionTask> {
    val byteBuffer: ByteBuffer = ByteBuffer
            .allocateDirect(REGION_WIDTH * REGION_HEIGHT * PIXEL_SIZE * 4)
            .order(ByteOrder.nativeOrder())
    val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()

    val uFrom = u * REGION_WIDTH
    val vFrom = v * REGION_HEIGHT

    override fun compareTo(other: RegionTask): Int {
        val center = vec2(REGIONS_CNT_U / 2f, REGIONS_CNT_V / 2f)
        val thisSqDist = vec2(u.toFloat(), v.toFloat()).distanceSquared(center).toInt()
        val otherSqDist = vec2(other.u.toFloat(), other.v.toFloat()).distanceSquared(center).toInt()
        return thisSqDist - otherSqDist
    }
}

private fun createRegionTasks() {
    for (u in 0 until REGIONS_CNT_U) {
        for (v in 0 until REGIONS_CNT_V) {
            regionsLow.add(RegionTask(u, v, 32, 32))
        }
    }
    regionsLow.sort()
    for (u in 0 until REGIONS_CNT_U) {
        for (v in 0 until REGIONS_CNT_V) {
            regionsMed.add(RegionTask(u, v, 4, 4))
        }
    }
    regionsMed.sort()
    for (u in 0 until REGIONS_CNT_U) {
        for (v in 0 until REGIONS_CNT_V) {
            regionsHgh.add(RegionTask(u, v, 1, 1))
        }
    }
    regionsHgh.sort()
}

private class RtrCamera {
    val position: vec3 = vec3().zero()
    val direction: vec3 = vec3().back()

    private val basisX = vec3()
    private val basisY = vec3()
    private val basisZ = vec3()

    fun updateBasis() {
        direction.negate(basisZ)
        upVec.cross(basisZ, basisX)
        basisX.normalize()
        basisZ.cross(basisX, basisY)
        basisY.normalize()
    }

    fun ray(u: Float, v: Float): ray {
        val x = VIEWPORT_LEFT + VIEWPORT_WIDTH * (u + 0.5f) / RESOLUTION_WIDTH
        val y = VIEWPORT_BOTTOM + VIEWPORT_HEIGHT * (v + 0.5f) / RESOLUTION_HEIGHT
        val dir = vec3(direction).mul(FOCUS_DISTANCE)
        val uComp = vec3(basisX).mul(x)
        val vComp = vec3(basisY).mul(y)
        dir.add(uComp).add(vComp)
        dir.normalize()
        return ray(position, dir)
    }
}

private class RtrBlinnPhongTechnique {
    // h = (v + l) / |v + l|
    // L = kA * Ia + kD * Id * max(0f, n.dot(l)) + kS * Is max(0, n.dot(h))^p

    fun compute(u: Float, v: Float): color {
        val ray = camera.ray(u, v)
        val result = scene.hit(ray, 0f, Float.MAX_VALUE)
        return if (result != null) {
            val lightDir = vec3()
            lightPos.sub(result.point, lightDir).normalize()
            val color = color().set(computeAmbient(result.material))
            val shadowRay = ray(result.point, lightDir)
            if (scene.hit(shadowRay, 0.1f, Float.MAX_VALUE) == null) {
                color.add(computeDiffuseSpecular(camera.position, result.point, result.normal, result.material, lightDir, light))
            }
            color
        } else {
            background[u.toInt() + v.toInt() * RESOLUTION_WIDTH]
        }
    }

    private fun computeAmbient(material: Material) : color {
        val ambientContrib = vec3()
        material.ambient.mul(light.intensity, ambientContrib)
        return ambientContrib
    }

    private fun computeDiffuseSpecular(eye: vec3, point: vec3, normal: vec3, material: Material, lightDir: vec3, light: Light): color {
        val result = color()
        val viewDir = vec3()
        val halfVec = vec3()
        eye.sub(point, viewDir).normalize()
        viewDir.add(lightDir, halfVec).normalize()
        val diffuseContrib = vec3()
        val specularContrib = vec3()
        diffuseContrib.set(material.diffuse).mul(light.intensity).mul(max(0f, normal.dot(lightDir)))
        specularContrib.set(material.specular).mul(light.intensity).mul(powf(max(0f, normal.dot(halfVec)), material.shine))
        result.add(diffuseContrib).add(specularContrib)
        return result
    }
}

private data class HitResult(val t: Float, val point: vec3, val normal: vec3, val material: Material)

private interface Hitable {
    fun hit(ray: ray, t0: Float, t1: Float): HitResult?
}

private data class HitableGroup(val hitables: List<Hitable>) : Hitable {
    override fun hit(ray: ray, t0: Float, t1: Float): HitResult? {
        var tMax = t1
        var result: HitResult? = null
        for (hitable in hitables) {
            val hit = hitable.hit(ray, t0, tMax)
            if (hit != null) {
                tMax = hit.t
                result = hit
            }
        }
        return result
    }
}

private data class HitableSphere(
        private val center: vec3, private val radius: Float, private val material: Material) : Hitable {

    private val sphere = sphere(center, radius)

    override fun hit(ray: ray, t0: Float, t1: Float): HitResult? {
        val result = vec2()
        if (Intersectionf.intersectRaySphere(ray, sphere, result)) {
            if (result.x in t0..t1) {
                return hitResult(result.x, ray)
            }
            if (result.y in t0..t1) {
                return hitResult(result.y, ray)
            }
        }
        return null
    }

    private fun hitResult(t: Float, ray: ray): HitResult {
        val point = vec3().pointAt(t, ray)
        val normal = vec3(point).sub(center).div(radius)
        return HitResult(t, point, normal, material)
    }
}

private fun updateRegions() = runBlocking(mainDispatcher) {
    currentJob.cancel()
    regionsDone.clear()
    camera.updateBasis()
    regionsLow.forEach {
        updateRegion(it, this)
    }
    currentJob = GlobalScope.launch {
        regionsMed.forEach {
            launch(Dispatchers.Default) {
                updateRegion(it, this)
            }
        }
        regionsHgh.forEach {
            launch(Dispatchers.Default) {
                updateRegion(it, this)
            }
        }
    }
}

private fun updateRegion(task: RegionTask, scope: CoroutineScope) {
    check(REGION_WIDTH % task.uStep == 0 && task.uStep <= REGION_WIDTH)
    check(REGION_HEIGHT % task.vStep == 0 && task.vStep <= REGION_HEIGHT)
    val uHalf = task.uStep / 2f
    val vHalf = task.vStep / 2f
    val uRange = 0 until REGION_WIDTH
    val vRange = 0 until REGION_HEIGHT
    for (v in vRange step task.vStep) {
        for (u in uRange step  task.uStep) {
            if (!scope.isActive) {
                return
            }
            val color = blinnPhongRtrTechnique.compute(task.uFrom + u + uHalf, task.vFrom + v + vHalf)
            fillRegion(u, v, task.uStep, task.vStep, color, task.floatBuffer)
        }
    }
    regionsDone.add(task)
}

private fun fillRegion(fromU: Int, fromV: Int, width: Int, height: Int, color: color, buffer: FloatBuffer) {

    val line = ByteBuffer.allocateDirect(width * PIXEL_SIZE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    (0 until width).forEach {
        line.put(color)
    }


    //val uRange = fromU until fromU + width
    val vRange = fromV until fromV + height
    for (v in vRange) {
        val offset = (fromU + v * REGION_WIDTH) * PIXEL_SIZE
        buffer.position(offset)
        buffer.put(line)



        /*for (u in uRange) {
            // todo: lines by copying readily available line of color in FloatBuffer
            val offset = (u + v * REGION_WIDTH) * PIXEL_SIZE
            buffer.position(offset)
            buffer.put(color)
        }*/
    }
}

private fun updateViewportTexture(regionTask: RegionTask) {
    viewportTexture.updatePixels(
            uOffset = regionTask.uFrom, vOffset = regionTask.vFrom,
            width = REGION_WIDTH, height = REGION_HEIGHT,
            format = backend.GL_RGB, type = backend.GL_FLOAT, pixels = regionTask.byteBuffer)
}

private val window = object : LwjglWindow(isHoldingCursor = false) {
    override fun onCreate() {
        createBackground()
        createRegionTasks()
        viewportTexture = GlTexture(
                unit = 0,
                width = RESOLUTION_WIDTH, height = RESOLUTION_HEIGHT,
                internalFormat = backend.GL_RGB, pixelFormat = backend.GL_RGB, pixelType = backend.GL_FLOAT)
        viewportRect = GlMesh.rect()
        simpleTechnique.create(shadersLib)
    }

    override fun onTick() {
        if (keyPressed || mouseControl) {
            sceneVersion.increment()
        }
        val elapsed = measureNanoTime {
            GlState.clear()
            GlState.drawWithNoCulling {
                simpleTechnique.draw(viewM, projectionM) {
                    simpleTechnique.instance(viewportRect, viewportTexture, modelM)
                }
            }
            controller.apply { position, direction ->
                camera.position.set(position)
                camera.direction.set(direction)
            }
            if (sceneVersion.check()) {
                updateRegions()
            }
        }
        var left = NANOS_PER_FRAME - elapsed
        while (left > 0 && regionsDone.isNotEmpty()) {
            val bite = measureNanoTime {
                updateViewportTexture(regionsDone.removeAt(0))
            }
            left -= bite
        }
    }

    override fun onCursorDelta(delta: vec2) {
        if (mouseControl) {
            wasdInput.onCursorDelta(delta)
        }
    }

    override fun mouseBtnPressed(btn: Int) {
        mouseControl = true
    }

    override fun mouseBtnReleased(btn: Int) {
        mouseControl = false
    }
    override fun keyPressed(key: Int) {
        wasdInput.keyPressed(key)
        keyPressed = true
    }

    override fun keyReleased(key: Int) {
        wasdInput.keyReleased(key)
        keyPressed = false
    }
}

fun main() {
    window.show()
}
