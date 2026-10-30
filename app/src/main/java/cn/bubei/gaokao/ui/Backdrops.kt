package cn.bubei.gaokao.ui

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.ui.viewinterop.AndroidView
import cn.bubei.gaokao.data.Subject
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 某科今天的首页背景，首页和学习页共用，保证两边是同一幅画。
 * still 为 true 时照片不推移、动画画作只取第一帧（学习页会再模糊，不必动）。
 */
@Composable
fun SubjectBackdrop(app: AppState, s: Subject, still: Boolean = false) {
    val context = LocalContext.current
    when (s) {
        Subject.WENYAN -> {
            val word = remember(app.day) { dailyWord(app) }
            val painting = word?.let { paintingOf(context, it.first.id) }
            val animated = word?.let { animatedPaintingOf(context, it.first.id) }
            when {
                animated != null && !still -> AnimatedPainting(animated)
                painting != null -> if (still) StillPhoto(painting) else DriftingPhoto(painting)
                else -> InkLandscape(
                    motifsOf(word?.let { (w, i) -> w.exampleFor(i)?.sentence }.orEmpty()),
                    foreground = word?.let { loadIllustration(context, it.first.id) } == null,
                )
            }
        }
        Subject.GUSHI -> app.scene(Subject.GUSHI).image.let { image ->
            // 名句画：paintings/gushi-XX.jpg 旁边有同名 .webp 动画就播动画。
            val animated = animatedPaintingOf(context, image.substringAfterLast('/').removeSuffix(".jpg"))
            when {
                animated != null && !still -> AnimatedPainting(animated)
                still -> StillPhoto(image)
                else -> DriftingPhoto(image)
            }
        }
        Subject.BIOLOGY -> bioScenes[(app.day % bioScenes.size).toInt()].image.let {
            // 生物图：biology/bio-XX.jpg 旁边有同名 .webp（实拍延时）就播动画。
            val animated = it?.removeSuffix(".jpg")?.plus(".webp")?.takeIf { webp ->
                context.assets.list("biology").orEmpty().contains(webp.substringAfterLast('/'))
            }
            when {
                it == null -> DnaHelix()
                animated != null && !still -> AnimatedPainting(animated)
                still -> StillPhoto(it)
                else -> DriftingPhoto(it)
            }
        }
    }
}

@Composable
fun StillPhoto(asset: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(asset) { context.assets.open(asset).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }
    Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.fillMaxSize())
}

/** 循环播放的动态 WebP（Android 9 起支持）；更早的系统只显示第一帧。 */
@Composable
fun AnimatedPainting(asset: String, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < 28) return StillPhoto(asset, modifier)
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.assets, asset))
                setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE; start() }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

/** 慢慢推近、平移的照片，让静图有点动感。 */
@Composable
fun DriftingPhoto(asset: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(asset) { context.assets.open(asset).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }
    val t by rememberInfiniteTransition(label = "drift").animateFloat(
        0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse), label = "t",
    )
    Image(
        bitmap, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize().graphicsLayer {
            val s = 1.06f + 0.1f * t
            scaleX = s; scaleY = s
            translationX = (t - 0.5f) * size.width * 0.05f
            translationY = (0.5f - t) * size.height * 0.02f
        },
    )
}

// ---- 文言：水墨山水 ----

val Paper = Color(0xFFF2ECDF)
val Ink = Color(0xFF26231F)

/** 画面里能出现的景物，按例句里的字挑选。 */
enum class Motif { MOON, SNOW, RAIN, WATER, BIRDS, BANNERS, CITY, WILLOW, BLOSSOM }

private val motifWords = mapOf(
    Motif.MOON to "月夜宵星",
    Motif.SNOW to "雪寒冰霜冬",
    Motif.RAIN to "雨",
    Motif.WATER to "江河水舟船渔波湖海流泊渡涛浪川溪泉潭洲",
    Motif.BIRDS to "鸟雁鹤鹭鸥鹰燕鹊禽飞",
    Motif.BANNERS to "兵卒战军伐攻师将戍甲守敌骑",
    Motif.CITY to "城宫楼阙殿都庙堂台",
    Motif.WILLOW to "风柳杨",
    Motif.BLOSSOM to "花梅桃春芳杏",
)

/** 按例句语境挑景物：句中有“月”画月夜，有“卒、攻”插军旗，有“舟、江”画江上小舟……什么都没有就画江山。 */
fun motifsOf(sentence: String): Set<Motif> =
    motifWords.filterValues { chars -> chars.any { it in sentence } }.keys.ifEmpty { setOf(Motif.WATER) }

/**
 * 宣纸上的水墨山水：远近四层山峦以不同速度横移，山脚雾带飘过；
 * 再按例句语境添景：月夜、风雪、雨、江上小舟、飞鸟、军旗、城楼、柳枝、落花。全是程序画的。
 */
@Composable
fun InkLandscape(motifs: Set<Motif>, foreground: Boolean = true, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ink")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "t")
    val bob by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4_200, easing = LinearEasing)), label = "bob")
    val fall by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "fall")
    val night = Motif.MOON in motifs
    val snow = Motif.SNOW in motifs
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                when {
                    night -> listOf(Color(0xFFC9CCCB), Color(0xFFDCDAD2), Color(0xFFE6E0D2))
                    snow -> listOf(Color(0xFFDAD8D2), Color(0xFFE9E6DF), Color(0xFFF1EEE7))
                    else -> listOf(Color(0xFFF6F1E6), Paper, Color(0xFFE8DFCC))
                }
            )
        )
        if (night) {
            // 月：淡淡的光晕加一轮白月。
            val c = Offset(w * 0.76f, h * 0.38f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f)), center = c, radius = w * 0.2f), radius = w * 0.2f, center = c)
            drawCircle(Color(0xFFFBF8EF), radius = w * 0.055f, center = c)
        } else if (!snow) {
            // 朱砂色的日头，落在远山后面，露出半个。
            drawCircle(Color(0xFFC2452D).copy(alpha = 0.72f), radius = w * 0.06f, center = Offset(w * 0.78f, h * 0.47f))
        }
        // 有插图时近处的山和江面交给插图画，这里只画远山。
        val water = foreground && Motif.WATER in motifs
        val layers = if (!foreground) listOf(
            Layer(0.50f, 0.11f, Color(0xFF9FA7A6), 0.3f, 1, 0.3f),
            Layer(0.55f, 0.10f, Color(0xFF6E7775), 0.38f, 2, 1.1f),
        ) else listOf(
            Layer(0.52f, 0.11f, Color(0xFF9FA7A6), 0.32f, 1, 0.3f),
            Layer(0.56f, 0.12f, Color(0xFF6E7775), 0.5f, 2, 1.1f),
            Layer(0.61f, 0.10f, Color(0xFF454B49), 0.7f, 3, 2.3f),
            Layer(if (water) 0.66f else 0.70f, 0.06f, Color(0xFF26231F), 0.85f, 4, 3.7f),
        )
        layers.forEachIndexed { i, layer ->
            val shift = t * (i + 1)
            drawMountains(layer, shift)
            if (snow) drawSnowCaps(layer, shift)
            if (i == 1 && Motif.CITY in motifs) city(w * 0.42f, ridgeY(layer, 0.42f, shift) + h * 0.035f)
            if (i == 2 && Motif.BANNERS in motifs) for ((k, x) in listOf(0.18f, 0.5f, 0.62f).withIndex()) {
                banner(Offset(w * x, ridgeY(layer, x, shift) + h * 0.012f), fall * 6 + k)
            }
            mist(h * (layer.base + 0.03f), t * (0.7f + i * 0.4f), i)
        }
        if (water) {
            val y0 = h * 0.68f
            for (k in 0 until 6) {
                val y = y0 + k * h * 0.022f
                val shift = ((t * 3 + k * 0.17f) % 1f) * w
                drawLine(Ink.copy(alpha = 0.10f - k * 0.012f), Offset(shift - w * 0.2f, y), Offset(shift + w * 0.05f, y), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(Ink.copy(alpha = 0.08f - k * 0.01f), Offset((shift + w * 0.55f) % w, y + 6f), Offset((shift + w * 0.72f) % w, y + 6f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            boat(Offset(w * 0.3f, y0 + h * 0.03f), sin(bob * 2 * PI).toFloat())
        }
        if (Motif.WILLOW in motifs) willow(sin(bob * 2 * PI).toFloat())
        if (Motif.BLOSSOM in motifs) blossom(fall)
        if (Motif.BIRDS in motifs) birds(t * 6 % 1f, bob)
        if (Motif.RAIN in motifs) rain(fall)
        if (snow) snowflakes(fall)
    }
}

/** 某层山在横向位置 x（0~1）处的山脊高度。 */
private fun DrawScope.ridgeY(layer: Layer, x: Float, t: Float): Float {
    val u = (x + (t * layer.speed) % 1f) * 2 * PI
    val s = layer.seed
    // 低频定山形，高频只给一点点起伏，免得山脊像锯齿。
    val ridge = 0.55 * sin(u * (1 + s % 2) + s) + 0.3 * sin(u * (2 + s % 3) + s * 1.7) +
        0.11 * sin(u * (4 + s % 2) + s * 0.3) + 0.04 * sin(u * (9 + s) + s)
    return size.height * (layer.base - layer.height * (0.5f + 0.5f * ridge.toFloat()))
}

/** 山顶一层白：沿山脊往下一小段留白。 */
private fun DrawScope.drawSnowCaps(layer: Layer, t: Float) {
    val path = Path()
    val steps = 80
    for (i in 0..steps) {
        val x = i / steps.toFloat()
        val y = ridgeY(layer, x, t)
        if (i == 0) path.moveTo(0f, y) else path.lineTo(x * size.width, y)
    }
    drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = size.height * 0.008f, cap = StrokeCap.Round))
}

/** 飘动的军旗：旗杆加一面随风起伏的三角旗。 */
private fun DrawScope.banner(foot: Offset, phase: Float) {
    val u = size.width / 400f
    val top = Offset(foot.x, foot.y - 46 * u)
    drawLine(Ink.copy(alpha = 0.85f), foot, top, strokeWidth = 1.6f * u, cap = StrokeCap.Round)
    val flag = Path().apply {
        moveTo(top.x, top.y)
        for (k in 1..10) {
            val f = k / 10f
            lineTo(top.x + f * 26 * u, top.y + 2 * u + sin((f * 3 + phase) * 2 * PI).toFloat() * 2.5f * u * f)
        }
        for (k in 10 downTo 0) {
            val f = k / 10f
            lineTo(top.x + f * 26 * u, top.y + 14 * u - f * 6 * u + sin((f * 3 + phase) * 2 * PI).toFloat() * 2.5f * u * f)
        }
        close()
    }
    drawPath(flag, Color(0xFFB8492F).copy(alpha = 0.85f))
}

/** 城楼：一段带垛口的城墙，正中一座重檐门楼。 */
private fun DrawScope.city(cx: Float, base: Float) {
    val u = size.width / 400f
    val ink = Ink.copy(alpha = 0.55f)
    drawRect(ink, Offset(cx - 70 * u, base - 14 * u), Size(140 * u, 14 * u))
    for (k in 0 until 14) drawRect(ink, Offset(cx - 70 * u + k * 10 * u, base - 18 * u), Size(6 * u, 4 * u))
    drawRect(ink, Offset(cx - 14 * u, base - 30 * u), Size(28 * u, 16 * u))
    for ((y, half) in listOf(base - 30 * u to 24 * u, base - 42 * u to 18 * u)) {
        drawPath(Path().apply {
            moveTo(cx - half, y); quadraticBezierTo(cx, y - 4 * u, cx + half, y)
            lineTo(cx + half * 0.6f, y - 7 * u); lineTo(cx - half * 0.6f, y - 7 * u); close()
        }, ink)
    }
    drawRect(ink, Offset(cx - 9 * u, base - 42 * u), Size(18 * u, 5 * u))
}

/** 左上角垂下的柳枝，随风轻摆。 */
private fun DrawScope.willow(wave: Float) {
    val u = size.width / 400f
    val root = Offset(-10 * u, size.height * 0.05f)
    drawLine(Ink.copy(alpha = 0.7f), root, Offset(90 * u, size.height * 0.09f), strokeWidth = 4 * u, cap = StrokeCap.Round)
    for (k in 0 until 9) {
        val x = 10 * u + k * 10 * u
        val y = size.height * 0.055f + k * 4 * u
        val len = size.height * (0.12f + (k % 3) * 0.04f)
        val sway = wave * (8 + k) * u
        val path = Path().apply { moveTo(x, y); quadraticBezierTo(x + sway * 0.4f, y + len * 0.5f, x + sway, y + len) }
        drawPath(path, Color(0xFF55604F).copy(alpha = 0.55f), style = Stroke(width = 1.3f * u, cap = StrokeCap.Round))
    }
}

/** 右上角一枝梅，花瓣慢慢飘落。 */
private fun DrawScope.blossom(fall: Float) {
    val u = size.width / 400f
    val w = size.width
    val branch = Path().apply {
        moveTo(w + 10 * u, size.height * 0.04f)
        quadraticBezierTo(w - 60 * u, size.height * 0.07f, w - 110 * u, size.height * 0.13f)
    }
    drawPath(branch, Ink.copy(alpha = 0.8f), style = Stroke(width = 3.5f * u, cap = StrokeCap.Round))
    val flowers = listOf(w - 30 * u to 0.055f, w - 62 * u to 0.08f, w - 88 * u to 0.105f, w - 108 * u to 0.128f, w - 48 * u to 0.09f)
    for ((x, fy) in flowers) {
        val c = Offset(x, size.height * fy)
        for (p in 0 until 5) {
            val a = p * 2 * PI / 5
            drawCircle(Color(0xFFD9707A).copy(alpha = 0.8f), radius = 3.2f * u, center = Offset(c.x + cos(a).toFloat() * 3.5f * u, c.y + sin(a).toFloat() * 3.5f * u))
        }
        drawCircle(Color(0xFFF4D27A), radius = 1.5f * u, center = c)
    }
    for (k in 0 until 7) {
        val p = (fall + k / 7f) % 1f
        val x = w - 40 * u - k * 22 * u - p * 60 * u + sin((p * 3 + k) * 2 * PI).toFloat() * 10 * u
        val y = size.height * (0.1f + p * 0.6f)
        drawCircle(Color(0xFFD9707A).copy(alpha = 0.7f * (1 - p)), radius = 2.4f * u, center = Offset(x, y))
    }
}

/** 一行飞鸟从右往左飞，翅膀上下扇动。 */
private fun DrawScope.birds(p: Float, flap: Float) {
    val u = size.width / 400f
    for (k in 0 until 5) {
        val x = size.width * (1.1f - p * 1.3f) + k * 18 * u
        val y = size.height * 0.36f + k * 7 * u - (k % 2) * 4 * u
        val wing = sin((flap + k * 0.2f) * 2 * PI).toFloat() * 4 * u
        val path = Path().apply {
            moveTo(x - 7 * u, y - wing); quadraticBezierTo(x - 3 * u, y - 2 * u, x, y); quadraticBezierTo(x + 3 * u, y - 2 * u, x + 7 * u, y - wing)
        }
        drawPath(path, Ink.copy(alpha = 0.75f), style = Stroke(width = 1.6f * u, cap = StrokeCap.Round))
    }
}

/** 斜斜的雨丝。 */
private fun DrawScope.rain(p: Float) {
    val u = size.width / 400f
    for (k in 0 until 70) {
        val x = ((k * 0.618f) % 1f) * size.width * 1.2f
        val y = (((k * 0.377f) + p * 4) % 1f) * size.height
        drawLine(Ink.copy(alpha = 0.16f), Offset(x, y), Offset(x - 6 * u, y + 22 * u), strokeWidth = 1f * u)
    }
}

/** 飘落的雪花，近大远小，左右轻晃。 */
private fun DrawScope.snowflakes(p: Float) {
    val u = size.width / 400f
    for (k in 0 until 60) {
        val depth = (k % 3 + 1) / 3f
        val x = ((k * 0.618f) % 1f) * size.width + sin((p * 2 + k * 0.3f) * 2 * PI).toFloat() * 8 * u
        val y = (((k * 0.377f) + p * (0.6f + depth)) % 1f) * size.height
        drawCircle(Color.White.copy(alpha = 0.95f), radius = (1.2f + 1.6f * depth) * u, center = Offset(x, y))
        drawCircle(Ink.copy(alpha = 0.08f), radius = (1.2f + 1.6f * depth) * u, center = Offset(x, y), style = Stroke(0.6f * u))
    }
}

private class Layer(val base: Float, val height: Float, val color: Color, val alpha: Float, val seed: Int, val speed: Float)

/** 一层山：几组正弦叠加成起伏的山脊，频率取整数倍，横移一整屏后正好接上。 */
private fun DrawScope.drawMountains(layer: Layer, t: Float) {
    val h = size.height
    val path = Path()
    val steps = 80
    for (i in 0..steps) {
        val x = i / steps.toFloat()
        val y = ridgeY(layer, x, t)
        if (i == 0) path.moveTo(0f, y) else path.lineTo(x * size.width, y)
    }
    path.lineTo(size.width, h); path.lineTo(0f, h); path.close()
    // 山顶墨重，往下渐淡，像墨在纸上晕开。
    drawPath(
        path,
        Brush.verticalGradient(
            0f to layer.color.copy(alpha = layer.alpha),
            1f to layer.color.copy(alpha = 0f),
            startY = h * (layer.base - layer.height), endY = h * (layer.base + 0.16f),
        ),
    )
}

/** 一条雾带：几团横向拉长的白雾，慢慢飘。 */
private fun DrawScope.mist(y: Float, t: Float, seed: Int) {
    val w = size.width
    for (k in 0 until 3) {
        val cx = (((t * 0.6f + k / 3f + seed * 0.21f) % 1f) * 1.6f - 0.3f) * w
        val rx = w * (0.35f + 0.1f * k)
        val ry = size.height * 0.025f
        drawOval(
            Brush.radialGradient(listOf(Paper.copy(alpha = 0.85f), Paper.copy(alpha = 0f)), center = Offset(cx, y), radius = rx),
            topLeft = Offset(cx - rx, y - ry), size = Size(rx * 2, ry * 2),
        )
    }
}

/** 小舟和蓑衣人，随波上下、微微摆动。 */
private fun DrawScope.boat(at: Offset, wave: Float) {
    val u = size.width / 400f
    val y = at.y + wave * 3 * u
    val tilt = wave * 2f
    val hull = Path().apply {
        moveTo(at.x - 34 * u, y - 4 * u)
        quadraticBezierTo(at.x, y + 9 * u, at.x + 38 * u, y - 7 * u)
        quadraticBezierTo(at.x + 2 * u, y + 2 * u, at.x - 34 * u, y - 4 * u)
        close()
    }
    drawPath(hull, Ink.copy(alpha = 0.85f))
    // 人：斗笠和身子。
    drawLine(Ink.copy(alpha = 0.85f), Offset(at.x + 6 * u, y - 3 * u), Offset(at.x + 6 * u + tilt, y - 15 * u), strokeWidth = 4 * u, cap = StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(at.x - 2 * u + tilt, y - 15 * u); lineTo(at.x + 6 * u + tilt, y - 21 * u); lineTo(at.x + 14 * u + tilt, y - 15 * u); close()
    }, Ink.copy(alpha = 0.85f))
    // 鱼竿。
    drawLine(Ink.copy(alpha = 0.6f), Offset(at.x + 8 * u + tilt, y - 10 * u), Offset(at.x + 44 * u, y - 30 * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
    drawLine(Ink.copy(alpha = 0.35f), Offset(at.x + 44 * u, y - 30 * u), Offset(at.x + 46 * u, y + 6 * u), strokeWidth = 0.6f * u)
}

// ---- 生物：旋转的 DNA ----

private val basePairs = listOf(Color(0xFF4FC3F7) to Color(0xFFFFB74D), Color(0xFF81C784) to Color(0xFFE57373))

/**
 * 三维 DNA 双螺旋：两条骨架绕竖轴旋转，碱基对按 A-T、G-C 两种颜色连在中间；
 * 按深度排序，远处的小而暗，近处的大而亮。
 */
@Composable
fun DnaHelix(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "dna").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "t",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(Color(0xFF123447), Color(0xFF07121B)), center = Offset(size.width * 0.7f, size.height * 0.45f), radius = size.height * 0.8f))
        // 背景里漂着的小颗粒，增加纵深感。
        for (k in 0 until 40) {
            val px = ((k * 0.618f + t / (2 * PI).toFloat() * (0.05f + (k % 5) * 0.01f)) % 1f) * size.width
            val py = ((k * 0.377f) % 1f) * size.height
            drawCircle(Color.White.copy(alpha = 0.05f + (k % 4) * 0.03f), radius = 1.5f + (k % 3), center = Offset(px, py))
        }
        val cx = size.width * 0.72f
        val radius = size.width * 0.16f
        val top = size.height * 0.06f
        val n = 34
        val step = size.height * 0.88f / n
        class Dot(val x: Float, val y: Float, val z: Float, val color: Color)
        val dots = mutableListOf<Dot>()
        val rungs = mutableListOf<Triple<Dot, Dot, Int>>()
        for (i in 0..n) {
            val a = i * 0.52f + t
            val y = top + i * step
            val tiltX = (y - size.height / 2) * 0.12f
            val d1 = Dot(cx + tiltX + radius * cos(a), y, sin(a), Color(0xFFE0F2F1))
            val d2 = Dot(cx + tiltX + radius * cos(a + PI.toFloat()), y, sin(a + PI.toFloat()), Color(0xFFE0F2F1))
            dots += d1; dots += d2
            rungs += Triple(d1, d2, i)
        }
        // 碱基对先画（在两条骨架之间），按平均深度排序。
        rungs.sortedBy { (a, b, _) -> a.z + b.z }.forEach { (a, b, i) ->
            val (c1, c2) = basePairs[i % 2].let { if ((i / 2) % 2 == 0) it else it.second to it.first }
            val mid = Offset((a.x + b.x) / 2, a.y)
            val depth = ((a.z + b.z) / 2 + 1) / 2
            drawLine(c1.copy(alpha = 0.35f + 0.55f * depth), Offset(a.x, a.y), mid, strokeWidth = 5f + 4f * depth, cap = StrokeCap.Round)
            drawLine(c2.copy(alpha = 0.35f + 0.55f * depth), mid, Offset(b.x, b.y), strokeWidth = 5f + 4f * depth, cap = StrokeCap.Round)
        }
        // 骨架：相邻点连成带子，再在每个点画一颗“磷酸-脱氧核糖”小球。
        for (strand in 0..1) {
            val pts = dots.filterIndexed { i, _ -> i % 2 == strand }
            val path = Path()
            pts.forEachIndexed { i, d -> if (i == 0) path.moveTo(d.x, d.y) else path.lineTo(d.x, d.y) }
            drawPath(path, Color(0xFFB2DFDB).copy(alpha = 0.25f), style = Stroke(width = 3f))
        }
        dots.sortedBy { it.z }.forEach { d ->
            val depth = (d.z + 1) / 2
            val r = 6f + 7f * depth
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.5f + 0.5f * depth), Color(0xFF4DB6AC).copy(alpha = 0.4f + 0.5f * depth)),
                    center = Offset(d.x - r * 0.3f, d.y - r * 0.3f), radius = r * 1.4f,
                ),
                radius = r, center = Offset(d.x, d.y),
            )
        }
    }
}
