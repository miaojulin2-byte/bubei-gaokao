package cn.bubei.gaokao.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 底栏的线条图标，照不背单词的样子：细线、圆头、不填色。 */
private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
        stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
    ).build()

/** 摊开的书。 */
val BookIcon = lineIcon("book") {
    moveTo(12f, 6.5f)
    curveTo(10f, 5f, 6.5f, 4.5f, 3.5f, 5f); verticalLineTo(18.5f)
    curveTo(6.5f, 18f, 10f, 18.5f, 12f, 20f)
    curveTo(14f, 18.5f, 17.5f, 18f, 20.5f, 18.5f); verticalLineTo(5f)
    curveTo(17.5f, 4.5f, 14f, 5f, 12f, 6.5f); close()
    moveTo(12f, 6.5f); verticalLineTo(20f)
}

/** 本子：带书脊线和两道横线的小册子。 */
val NotebookIcon = lineIcon("notebook") {
    moveTo(6.5f, 3.5f); horizontalLineTo(18f)
    curveTo(18.8f, 3.5f, 19.5f, 4.2f, 19.5f, 5f); verticalLineTo(19f)
    curveTo(19.5f, 19.8f, 18.8f, 20.5f, 18f, 20.5f); horizontalLineTo(6.5f)
    curveTo(5.7f, 20.5f, 5f, 19.8f, 5f, 19f); verticalLineTo(5f)
    curveTo(5f, 4.2f, 5.7f, 3.5f, 6.5f, 3.5f); close()
    moveTo(8.5f, 3.5f); verticalLineTo(20.5f)
    moveTo(11.5f, 8.5f); horizontalLineTo(16.5f)
    moveTo(11.5f, 12f); horizontalLineTo(15f)
}

/** 人像。 */
val PersonIcon = lineIcon("person") {
    moveTo(15.5f, 8f)
    arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 8.5f, y1 = 8f)
    arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 15.5f, y1 = 8f); close()
    moveTo(4.5f, 20f)
    curveTo(5f, 16f, 8f, 14f, 12f, 14f)
    curveTo(16f, 14f, 19f, 16f, 19.5f, 20f)
}
