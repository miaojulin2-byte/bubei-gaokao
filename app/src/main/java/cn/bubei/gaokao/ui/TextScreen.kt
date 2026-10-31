package cn.bubei.gaokao.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.Card
import cn.bubei.gaokao.data.TextSpot
import cn.bubei.gaokao.data.Texts

/**
 * 课文全文，一句一行。点一句在它下面展开这一句的今译，再点收起；右上角可以一次展开全部，仍是逐句对照。
 * 从搜索或例句进来时，那几句着色、预先展开并滚到眼前。
 */
@Composable
fun TextScreen(app: AppState, spot: TextSpot, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val text = spot.text
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var all by rememberSaveable { mutableStateOf(false) }
    val open: SnapshotStateList<Int> = remember(spot) { spot.range.toList().toMutableStateList() }
    // 每句一项；段首前插一项空白作段距。第 0 项是篇名。
    val rows = remember(text) {
        buildList {
            var i = 0
            text.paragraphs.forEachIndexed { p, para ->
                para.forEachIndexed { k, _ -> add(Triple(i++, k == 0, p > 0 && k == 0)) }
            }
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(spot) {
        if (!spot.range.isEmpty() && spot.range.first > 0) list.scrollToItem((rows.indexOfFirst { it.first == spot.range.first } - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "关闭") }
            Text(text.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                if (all) "收起译文" else "全部译文", fontSize = 14.sp,
                color = if (all) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (all) accent else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { all = !all; if (!all) open.clear() }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            IconButton(onClick = { onClose(); app.page = Page.SEARCH }) { Icon(Icons.Default.Search, contentDescription = "搜索") }
        }
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            item {
                Column(Modifier.padding(horizontal = 28.dp).padding(top = 12.dp, bottom = 8.dp)) {
                    Text(text.title, fontFamily = Serif, fontSize = 26.sp)
                    Text("${text.author}  ·  ${text.volume}", color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    Text("点一句看这一句的译文", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
                }
            }
            items(rows.size) { r ->
                val (i, first, gap) = rows[r]
                val line = text.lines[i]
                val shown = all || i in open
                if (gap) Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.fillMaxWidth().clickable { if (i in open) open.remove(i) else open.add(i) }
                        .padding(horizontal = 28.dp, vertical = 5.dp),
                ) {
                    Text(
                        (if (first) "　　" else "") + line.original, fontFamily = Serif, fontSize = 19.sp, lineHeight = 32.sp,
                        color = if (i in spot.range) accent else MaterialTheme.colorScheme.onSurface,
                    )
                    if (shown) Text(line.translation, color = muted, fontSize = 15.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
                }
            }
            item {
                Text(
                    "译文为本应用逐句整理的参考今译，不是教材官方译文。", color = muted, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 28.dp),
                )
            }
        }
    }
}

/** 文言例句的整句今译，以及跳到课文的入口。例句对不上课文时不显示。 */
@Composable
fun ExampleTranslation(card: Card.Word, exampleIndex: Int, onOpenText: ((TextSpot) -> Unit)?) {
    val context = LocalContext.current
    val ex = card.exampleFor(exampleIndex) ?: return
    val spot = remember(ex) { Texts.locate(context, ex.title, ex.sentence) } ?: return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            spot.range.joinToString("") { spot.text.lines[it].translation },
            color = muted, fontSize = 15.sp, lineHeight = 24.sp,
        )
        if (onOpenText != null) Text(
            "看《${spot.text.title}》全文 ›", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp).clickable { onOpenText(spot) }.padding(vertical = 4.dp),
        )
    }
}
