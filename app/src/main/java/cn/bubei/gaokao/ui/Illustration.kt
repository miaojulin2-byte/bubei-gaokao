package cn.bubei.gaokao.ui

import android.content.Context
import android.util.Xml
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import org.xmlpull.v1.XmlPullParser
import kotlin.math.PI
import kotlin.math.sin

/**
 * 词义插图：assets/illustrations/ 下的 SVG，只用 path、circle、ellipse、rect、line、polygon 和 g。
 * <g> 上可以写 data-anim 让这一组动起来：
 * - bob：上下起伏；sway：绕 data-cx,data-cy 摆动（度）；march：左右来回走；
 * - flutter：绕支点横向伸缩，像旗子飘；rise：慢慢升起再落下。
 * data-amp 是幅度，data-period 是周期（秒），data-phase 是相位（0~1）。
 */
internal class Shape(val path: Path, val fill: Color?, val stroke: Color?, val strokeWidth: Float)

internal class Group(
    val anim: String?, val amp: Float, val period: Float, val phase: Float, val cx: Float, val cy: Float,
    val children: List<Any>,
)

/** example：这幅图画的是卡片的第几个例句。 */
class Illustration internal constructor(internal val width: Float, internal val height: Float, val example: Int, internal val root: Group)

private val cache = mutableMapOf<String, Illustration?>()

/** 读一幅插图；没有这个文件时返回 null。 */
fun loadIllustration(context: Context, name: String): Illustration? = synchronized(cache) {
    cache.getOrPut(name) {
        runCatching { context.assets.open("illustrations/$name.svg").use { parse(it) } }.getOrNull()
    }
}

/** 用 Blender 渲染好的整幅词义画（assets/paintings/<卡片 id>.jpg），竖屏全幅。 */
fun paintingOf(context: Context, cardId: String): String? =
    "paintings/$cardId.jpg".takeIf { context.assets.list("paintings").orEmpty().contains("$cardId.jpg") }

/** 同一幅画的循环动画（.webp），没有就是 null。 */
fun animatedPaintingOf(context: Context, cardId: String): String? =
    "paintings/$cardId.webp".takeIf { context.assets.list("paintings").orEmpty().contains("$cardId.webp") }

/** 词义画画的是哪一句例句：paintings/examples.json，{卡片 id: 例句序号}，由 scripts/blender/render.py 渲染时写入。 */
private var paintedExamples: Map<String, Int>? = null

fun paintedExample(context: Context, cardId: String): Int? = synchronized(cache) {
    val map = paintedExamples ?: runCatching {
        val json = org.json.JSONObject(context.assets.open("paintings/examples.json").bufferedReader().use { it.readText() })
        json.keys().asSequence().associateWith { json.getInt(it) }
    }.getOrDefault(emptyMap()).also { paintedExamples = it }
    map[cardId]
}

/** 有插图（矢量图或 Blender 词义画）的卡片 id。 */
fun illustratedCards(context: Context): List<String> {
    val svgs = context.assets.list("illustrations").orEmpty().filter { it.endsWith(".svg") }.map { it.removeSuffix(".svg") }
    val paintings = context.assets.list("paintings").orEmpty().filter { it.endsWith(".jpg") }.map { it.removeSuffix(".jpg") }
    return (svgs + paintings).distinct().sorted()
}

/** 插图对应的例句序号：词义画优先，其次矢量图里的 data-example。 */
fun illustratedExample(context: Context, cardId: String): Int =
    paintedExample(context, cardId) ?: loadIllustration(context, cardId)?.example ?: 0

private fun parse(input: java.io.InputStream): Illustration {
    val p = Xml.newPullParser()
    p.setInput(input, "utf-8")
    var width = 400f
    var height = 300f
    var example = 0
    val stack = ArrayDeque<Pair<Map<String, String>, MutableList<Any>>>()
    var root: Group? = null
    while (p.next() != XmlPullParser.END_DOCUMENT) {
        when (p.eventType) {
            XmlPullParser.START_TAG -> {
                val a = (0 until p.attributeCount).associate { p.getAttributeName(it) to p.getAttributeValue(it) }
                when (p.name) {
                    "svg" -> {
                        a["viewBox"]?.split(" ", ",")?.filter { it.isNotBlank() }?.map { it.toFloat() }?.let { width = it[2]; height = it[3] }
                        example = a["data-example"]?.toIntOrNull() ?: 0
                        stack.addLast(a to mutableListOf())
                    }
                    "g" -> stack.addLast(a to mutableListOf())
                    else -> shape(p.name, a, stack.lastOrNull()?.first.orEmpty())?.let { stack.last().second += it }
                }
            }
            XmlPullParser.END_TAG -> if (p.name == "g" || p.name == "svg") {
                val (a, children) = stack.removeLast()
                val group = Group(
                    a["data-anim"], a["data-amp"]?.toFloat() ?: 3f, a["data-period"]?.toFloat() ?: 4f,
                    a["data-phase"]?.toFloat() ?: 0f, a["data-cx"]?.toFloat() ?: 0f, a["data-cy"]?.toFloat() ?: 0f, children,
                )
                if (stack.isEmpty()) root = group else stack.last().second += group
            }
        }
    }
    return Illustration(width, height, example, root ?: Group(null, 0f, 1f, 0f, 0f, 0f, emptyList()))
}

private fun color(value: String?, opacity: Float): Color? {
    if (value == null || value == "none") return null
    val hex = value.removePrefix("#")
    val argb = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("").toLong(16) or 0xFF000000
        6 -> hex.toLong(16) or 0xFF000000
        else -> return null
    }
    return Color(argb).copy(alpha = opacity)
}

/** 组上写的 fill、stroke 等会传给组里的图形，图形自己写的优先。 */
private fun shape(tag: String, own: Map<String, String>, inherited: Map<String, String>): Shape? {
    val a = inherited.filterKeys { it in setOf("fill", "stroke", "stroke-width", "opacity", "fill-opacity") } + own
    fun f(k: String) = a[k]?.toFloatOrNull() ?: 0f
    val path = when (tag) {
        "path" -> PathParser().parsePathString(a["d"] ?: return null).toPath()
        "circle" -> Path().apply { addOval(Rect(Offset(f("cx"), f("cy")), f("r"))) }
        "ellipse" -> Path().apply { addOval(Rect(f("cx") - f("rx"), f("cy") - f("ry"), f("cx") + f("rx"), f("cy") + f("ry"))) }
        "rect" -> Path().apply { addRect(Rect(f("x"), f("y"), f("x") + f("width"), f("y") + f("height"))) }
        "line" -> Path().apply { moveTo(f("x1"), f("y1")); lineTo(f("x2"), f("y2")) }
        "polygon", "polyline" -> Path().apply {
            val n = (a["points"] ?: return null).split(" ", ",").filter { it.isNotBlank() }.map { it.toFloat() }
            for (i in n.indices step 2) if (i == 0) moveTo(n[0], n[1]) else lineTo(n[i], n[i + 1])
            if (tag == "polygon") close()
        }
        else -> return null
    }
    val opacity = a["opacity"]?.toFloatOrNull() ?: 1f
    val fill = if (tag == "line" || tag == "polyline") null
    else color(a["fill"] ?: "#26231f", opacity * (a["fill-opacity"]?.toFloatOrNull() ?: 1f))
    return Shape(path, fill, color(a["stroke"], opacity), a["stroke-width"]?.toFloatOrNull() ?: 1f)
}

/** 画插图并让各组按 data-anim 动起来。插图等比缩放，宽度铺满，底边对齐 modifier 的底。 */
@Composable
fun IllustrationView(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val art = remember(name) { loadIllustration(context, name) } ?: return
    val clock by rememberInfiniteTransition(label = "art").animateFloat(
        0f, 120f, infiniteRepeatable(tween(120_000, easing = LinearEasing)), label = "clock",
    )
    Canvas(modifier) {
        val k = size.width / art.width
        translate(top = size.height - art.height * k) {
            scale(k, k, pivot = Offset.Zero) { draw(art.root, clock) }
        }
    }
}

private fun DrawScope.draw(group: Group, seconds: Float) {
    val wave = sin(((seconds / group.period) + group.phase) * 2 * PI).toFloat()
    val pivot = Offset(group.cx, group.cy)
    withTransform({
        when (group.anim) {
            "bob" -> translate(top = wave * group.amp)
            "march" -> translate(left = wave * group.amp, top = -kotlin.math.abs(sin(((seconds / group.period * 4) + group.phase) * PI).toFloat()) * group.amp * 0.25f)
            "sway" -> rotate(wave * group.amp, pivot)
            "flutter" -> scale(1f + wave * group.amp / 100f, 1f, pivot)
            "rise" -> translate(top = (wave + 1) / 2 * group.amp)
        }
    }) {
        for (child in group.children) when (child) {
            is Group -> draw(child, seconds)
            is Shape -> {
                child.fill?.let { drawPath(child.path, it) }
                child.stroke?.let { drawPath(child.path, it, style = Stroke(child.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
        }
    }
}
