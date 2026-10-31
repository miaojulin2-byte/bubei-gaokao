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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.Card
import cn.bubei.gaokao.data.Example
import cn.bubei.gaokao.data.Passage
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
    val open: SnapshotStateList<Int> = remember(spot) { spot.marked.toMutableStateList() }
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
            if (text.translated) Text(
                if (all) "收起译文" else "全部译文", fontSize = 14.sp,
                color = if (all) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (all) accent else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { all = !all; if (!all) open.clear() }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        PullToSearch({ onClose(); app.page = Page.SEARCH }) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                item {
                    Column(Modifier.padding(horizontal = 28.dp).padding(top = 12.dp, bottom = 8.dp)) {
                        Text(text.title, fontFamily = Serif, fontSize = 26.sp)
                        Text(listOf(text.author, text.volume).filter { it.isNotEmpty() }.joinToString("  ·  "), color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        if (text.translated) Text("点一句看这一句的译文", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                }
                items(rows.size) { r ->
                    val (i, first, gap) = rows[r]
                    val line = text.lines[i]
                    val shown = all || i in open
                    if (gap) Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier.fillMaxWidth().clickable(enabled = text.translated) { if (i in open) open.remove(i) else open.add(i) }
                            .padding(horizontal = 28.dp, vertical = 5.dp),
                    ) {
                        Text(
                            (if (first && text.translated) "　　" else "") + line.original, fontFamily = Serif, fontSize = 19.sp, lineHeight = 32.sp,
                            color = if (i in spot.marked) accent else MaterialTheme.colorScheme.onSurface,
                        )
                        if (shown && line.translation.isNotEmpty()) Text(line.translation, color = muted, fontSize = 15.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
                    }
                }
                if (text.translated) item {
                    Text(
                        "译文为本应用逐句整理的参考今译，不是教材官方译文。", color = muted, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 28.dp),
                    )
                }
            }
        }
    }
}

/** 例句在课文里的位置；对不上课文（课外、初中篇目）时为空。 */
@Composable
fun exampleSpot(ex: Example?): TextSpot? {
    val context = LocalContext.current
    return remember(ex) { ex?.let { Texts.locate(context, it.title, it.sentence) } }
}

/** 文言例句的整句今译。 */
@Composable
fun ExampleTranslation(spot: TextSpot?, modifier: Modifier = Modifier) {
    spot ?: return
    Text(
        spot.range.joinToString("") { spot.text.lines[it].translation },
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, lineHeight = 24.sp, modifier = modifier,
    )
}

/** 出处《篇名》：能对上课文时着色加下划线，点了打开全文并标出这几句；对不上时是普通灰字。 */
@Composable
fun SourceTitle(label: String, spot: TextSpot?, onOpenText: ((TextSpot) -> Unit)?, modifier: Modifier = Modifier, fontSize: Int = 13) {
    val link = spot != null && onOpenText != null
    Text(
        label, fontSize = fontSize.sp,
        color = if (link) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = if (link) TextDecoration.Underline else null,
        modifier = if (link) modifier.clickable { onOpenText!!(spot!!) } else modifier,
    )
}

/** 选择题题干里引的课文原句和整句今译；同一篇的几句列在一起，下面一个可点的出处。 */
@Composable
fun QuotedSources(card: Card.Choice, onOpenText: ((TextSpot) -> Unit)?, header: @Composable () -> Unit) {
    val context = LocalContext.current
    val spots = remember(card) { Texts.quotes(context, card.stem) }
    if (spots.isEmpty()) return
    header()
    spots.groupBy { it.text }.forEach { (text, same) ->
        same.forEach { spot ->
            val lines = spot.range.map { text.lines[it] }
            Text(lines.joinToString("") { it.original }, fontFamily = Serif, fontSize = 16.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 12.dp))
            ExampleTranslation(spot)
        }
        SourceTitle("《${text.title}》", same[0].copy(also = same.drop(1).map { it.range }), onOpenText, Modifier.padding(top = 6.dp))
    }
}

/** 默写题答案在原文里的位置：出处是文言课文时用带译文的课文，否则用古诗文全文。 */
@Composable
fun passageSpot(passage: Passage): TextSpot? {
    val context = LocalContext.current
    return remember(passage) { Texts.locate(context, passage) }
}
