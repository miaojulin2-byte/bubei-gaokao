package cn.bubei.gaokao.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 和首页一样往下拉打开搜索。包住一块可滚动的内容：内容已到顶还往下拉时整块跟着下移，
 * 顶上出现“下拉搜索”，拉过 96dp 松手就打开搜索。
 */
@Composable
fun PullToSearch(onSearch: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val search by rememberUpdatedState(onSearch)
    val connection = remember(threshold) {
        object : NestedScrollConnection {
            // 已经拉下来时往上推，先把拉下的距离收回去，再让内容滚动。
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || pull <= 0f || available.y >= 0f) return Offset.Zero
                val used = maxOf(available.y, -pull / 0.6f)
                pull = (pull + used * 0.6f).coerceAtLeast(0f)
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                pull = (pull + available.y * 0.6f).coerceAtMost(threshold * 1.5f)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                if (pull >= threshold) search()
                animate(pull, 0f) { v, _ -> pull = v }
                return available
            }
        }
    }
    Box(modifier.nestedScroll(connection)) {
        Box(Modifier.graphicsLayer { translationY = pull }) { content() }
        val progress = (pull / threshold).coerceAtMost(1f)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopCenter)
                .graphicsLayer { alpha = progress; translationY = pull * 0.35f }
                .clip(RoundedCornerShape(50)).background(Color(0x59000000)).padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(if (progress >= 1f) "松开搜索" else "下拉搜索", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
