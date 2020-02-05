package com.blaster.ofc

import com.blaster.common.*
import com.blaster.gl.GlState
import com.blaster.platform.LwjglWindow
import com.blaster.platform.WasdInput
import com.blaster.scene.Camera
import com.blaster.scene.Controller
import com.blaster.techniques.ImmediateTechnique
import org.lwjgl.glfw.GLFW
import java.lang.IllegalStateException
import java.util.regex.Pattern

const val HEIGHT = 100f
const val SIDE = 25f // 25m each block
const val CITY_SIDE = SIDE * 25

// todo: flat terrain, aabb grid, endless repeating of grid
// todo: level of details for meshes from split grammars
// todo: animated details with billboards
// todo: no roads: everything is flying in echelons
// todo: graph of navigation for vehicles/pedestrians

private val SPLIT_LINE = Pattern.compile(":\\s+")
private val SPLIT_RULES = Pattern.compile("\\s+")

private enum class GrammarNodeCnt {
    ONE,            // 1
    NONE_OR_ONE,    // ?
    NONE_OR_MORE,   // *
    ONE_OR_MORE,    // +
}

private data class GrammarNode(
        val label: String,
        val splitRule: SplitRule,
        val children: List<Pair<GrammarNode, GrammarNodeCnt>>?)

typealias SplitRule = (aabb: aabb) -> List<aabb>

class Grammar private constructor() {

    private lateinit var root: GrammarNode

    fun walk(start: aabb) {
        walkInternal(start, root)
    }

    private fun walkInternal(bound: aabb, node: GrammarNode) {
        val partitions = node.splitRule.invoke(bound)
        if (node.children == null) {
            return // terminal
        }
        val iterator = partitions.iterator()
        node.children.forEach {
            check(iterator.hasNext()) { "Not enough partitions to cater for children!" }
            val child = it.first
            val cnt = it.second
            when (cnt) {
                GrammarNodeCnt.NONE_OR_ONE -> TODO()
                GrammarNodeCnt.NONE_OR_MORE -> TODO()
                GrammarNodeCnt.ONE_OR_MORE -> {
                    while (iterator.hasNext()) {
                        walkInternal(iterator.next(), child)
                    }
                }
                GrammarNodeCnt.ONE -> {
                    walkInternal(iterator.next(), child)
                }
            }
        }
    }

    companion object {
        fun compile(grammar: String, splitRules: Map<String, SplitRule>): Grammar
        {
            var rootLabel: String? = null
            val parsed = mutableMapOf<String, List<String>>()
            grammar.lines().forEach {
                if (!it.isBlank()) {
                    val trimmed = it.trim()
                    val split = trimmed.split(SPLIT_LINE)
                    val label = split[0]
                    val rules = split[1]
                    if (rootLabel == null) {
                        rootLabel = label
                    }
                    val rulesSplit = rules.split(SPLIT_RULES)
                    check(!parsed.contains(label))
                    parsed[label] = rulesSplit
                }
            }
            val result = Grammar()
            result.root = parseNode(rootLabel!!, parsed, splitRules)
            return result
        }

        private fun parseNode(label: String,
                              parsed: Map<String, List<String>>,
                              splitRules: Map<String, SplitRule>): GrammarNode {
            if (label.filter { !it.isUpperCase() }.count() == 0) {
                return GrammarNode(label, splitRules.getValue(label), null) // terminal node
            }
            val rules = parsed.getValue(label)
            val children = mutableListOf<Pair<GrammarNode, GrammarNodeCnt>>()
            rules.forEach {
                val (child, cnt) = when {
                    it.endsWith("?") -> it.removeSuffix("?") to GrammarNodeCnt.NONE_OR_ONE
                    it.endsWith("*") -> it.removeSuffix("*") to GrammarNodeCnt.NONE_OR_MORE
                    it.endsWith("+") -> it.removeSuffix("+") to GrammarNodeCnt.ONE_OR_MORE
                    else -> it to GrammarNodeCnt.ONE
                }

                children.add(parseNode(child, parsed, splitRules) to cnt)
            }
            return GrammarNode(label, splitRules.getValue(label), children)
        }
    }
}

private val aabbs = mutableListOf<aabb>()

private val grammar = Grammar.compile(
        """
        mooncity:   block+
        block:      building+
        building:   base roof floor+ 
        base:       SHAPE
        floor:      SHAPE
        roof:       SHAPE
    """,
        mapOf(
                "mooncity"  to ::mooncity,
                "block"     to ::block,
                "building"  to ::building,
                "base"      to ::base,
                "roof"      to ::roof,
                "floor"     to ::floor,
                "SHAPE"     to ::shape
        )).walk(aabb(vec3(-CITY_SIDE, 0f, -CITY_SIDE), vec3(CITY_SIDE, HEIGHT, CITY_SIDE)))

fun mooncity(aabb: aabb) = aabb.randomSplit(listOf(0, 2), SIDE)
fun block(aabb: aabb) = aabb.selectCentersInside(randomi(1, 5), 20f, 10f, 15f)
        .map { it.splitByAxis(1, randomf(0.7f, 1.0f)).first() }

fun building(aabb: aabb): List<aabb> {
    val result = mutableListOf<aabb>()
    var split = aabb.splitByAxis(1, 0.1f)
    result.add(split.first()) // base
    split = split.last().splitByAxis(1, 0.9f)
    result.add(split.last()) // roof
    split = split.first().splitByAxis(1, 0.5f)
    result.addAll(split)
    return result
}

fun base(aabb: aabb) = listOf(aabb)
fun roof(aabb: aabb) = listOf(aabb)
fun floor(aabb: aabb) = listOf(aabb)

fun shape(aabb: aabb): List<aabb> {
    aabbs.add(aabb)
    return listOf()
}

fun aabb.randomSplit(axises: List<Int> = listOf(0, 1, 2), min: Float): List<aabb> {
    val axisesCopy = ArrayList(axises)
    while(axisesCopy.isNotEmpty()) {
        val axis = axisesCopy.random()
        val length = when (axis) {
            0 -> width()
            1 -> height()
            2 -> depth()
            else -> throw IllegalStateException("wtf?!")
        }
        val from  = 0.3f
        val to = 0.7f
        val minLength = length * 0.3f
        if (minLength > min) {
            return splitByAxis(axis, randomf(from, to))
                    .flatMap { it.randomSplit(axises, min) }
        } else {
            axisesCopy.remove(axis)
        }
    }
    return listOf(this) // terminal
}

fun aabb.splitByAxis(axis: Int, ratio: Float): List<aabb> {
    val (start, end) = when (axis) {
        0 -> minX to maxX
        1 -> minY to maxY
        2 -> minZ to maxZ
        else -> throw IllegalArgumentException("wtf?!")
    }
    check(end > start)
    val length = end - start
    val threshold = start + length * ratio
    return when (axis) {
        0 -> listOf(aabb(start, minY, minZ, threshold, maxY, maxZ), aabb(threshold, minY, minZ, end, maxY, maxZ))
        1 -> listOf(aabb(minX, start, minZ, maxX, threshold, maxZ), aabb(minX, threshold, minZ, maxX, end, maxZ))
        2 -> listOf(aabb(minX, minY, start, maxX, maxY, threshold), aabb(minX, minY, threshold, maxX, maxY, end))
        else -> throw IllegalArgumentException("wtf?!")
    }
}

// todo: right now is only xz
// circle x = r cos(t)    y = r sin(t)
fun aabb.selectCentersInside(cnt: Int, maxDelta: Float, fromR: Float, toR: Float): List<aabb> {
    val result = mutableListOf<aabb>()
    val current = center()
    while (result.size != cnt) {
        val r = randomf(0f, maxDelta)
        val t = randomf(0f, 1f)
        val center = vec2(current.x + r * cosf(t), current.z + r * sinf(t))
        val size = randomf(fromR, toR)
        result.add(aabb(center.x - size, minY, center.y - size, center.x + size, maxY, center.y + size))
    }
    return result
}

private val immediateTechnique = ImmediateTechnique()

private val camera = Camera()
private val controller = Controller(position = vec3(0f, HEIGHT * 2f, 0f), yaw = radf(45f), velocity = 1f)
private val wasd = WasdInput(controller)
private var mouseControl = false

/*private val aabb = aabb(-SIDE, 0f, -SIDE, SIDE, HEIGHT, SIDE)

data class Section(val node: Node<Any>)

class Grid {
    val sections: List<Section>

    init {
        val result = mutableListOf<Section>()
        for (row in 0 until COUNT) {
            for (column in 0 until COUNT) {
                val pos = vec3(row * SIDE, 0f, column * SIDE)
                val node = Node<Any>()
                node.setPosition(pos)
                result.add(Section(node))
            }
        }
        sections = result
    }

    fun get(row: Int, column: Int): Section {
        check(row in 0 until COUNT)
        check(column in 0 until COUNT)
        return sections[row + column * COUNT]
    }
}

private val grid = Grid()*/

private val window = object : LwjglWindow(isHoldingCursor = false) {
    override fun onCreate() {

    }

    override fun onResize(width: Int, height: Int) {
        GlState.apply(width, height, color = color(0f))
        camera.setPerspective(width, height)
        immediateTechnique.resize(camera)
    }

    private fun tick() {
        controller.apply { position, direction ->
            camera.setPosition(position)
            camera.lookAlong(direction)
        }
    }

    private fun draw() {
        GlState.clear()
        val identity = mat4()
        aabbs.forEach { immediateTechnique.aabb(camera, it, identity, color = color(0f, 1f, 0f)) }
    }

    override fun onTick() {
        tick()
        draw()
    }

    override fun mouseBtnPressed(btn: Int) {
        if (btn == GLFW.GLFW_MOUSE_BUTTON_1) {
            mouseControl = true
        }
    }

    override fun mouseBtnReleased(btn: Int) {
        if (btn == GLFW.GLFW_MOUSE_BUTTON_1) {
            mouseControl = false
        }
    }

    override fun onCursorDelta(delta: vec2) {
        if (mouseControl) {
            wasd.onCursorDelta(delta)
        }
    }

    override fun keyPressed(key: Int) {
        wasd.keyPressed(key)
    }

    override fun keyReleased(key: Int) {
        wasd.keyReleased(key)
    }
}

fun main() {
    window.show()
}