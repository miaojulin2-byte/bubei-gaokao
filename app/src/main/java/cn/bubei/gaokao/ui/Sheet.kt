package cn.bubei.gaokao.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SLIDE_MS = 320

/** 整页从哪边进来：底部滑上（书库、学习等）、顶部淡入（搜索）、右边推入（日历等二级页）。 */
enum class Edge { BOTTOM, TOP, END }

/**
 * 盖在首页上的整页。value 不为空时滑进来，变回空时滑出；滑出动画期间仍用最后一次的值绘制。
 */
@Composable
fun <T : Any> Overlay(value: T?, from: Edge = Edge.BOTTOM, content: @Composable (T) -> Unit) {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    val ease = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)
    AnimatedVisibility(
        visible = value != null,
        enter = when (from) {
            Edge.BOTTOM -> slideInVertically(ease) { it }
            Edge.TOP -> fadeIn(tween(200)) + slideInVertically(ease) { -it / 6 }
            Edge.END -> slideInHorizontally(ease) { it }
        },
        exit = when (from) {
            Edge.BOTTOM -> slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it }
            Edge.TOP -> fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 6 }
            Edge.END -> slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it }
        },
    ) {
        last?.let { content(it) }
    }
}

/** 带标题栏的整页：左上角 ⌄ 收起，标题居中，和不背单词的“我的内容”一样。 */
@Composable
fun Sheet(visible: Boolean, title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Overlay(if (visible) Unit else null) {
        BackHandler(enabled = visible, onBack = onClose)
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起", modifier = Modifier.padding(2.dp))
                }
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
            }
            content()
        }
    }
}
