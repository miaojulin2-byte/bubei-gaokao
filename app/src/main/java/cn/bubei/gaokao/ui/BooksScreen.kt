package cn.bubei.gaokao.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.DeckProgress
import cn.bubei.gaokao.data.Subject

/** 一本书：教材分册，或一组真题。生物按分册把各章合在一起。 */
private class Book(val title: String, val cover: Cover, val decks: List<DeckProgress>) {
    val count get() = decks.sumOf { it.deck.count }
    val learned get() = decks.sumOf { it.learned }
}

/** 封面：教材用国家中小学智慧教育平台的封面图；真题等没有教材的，按系列名、书名画一个。 */
private class Cover(val series: String, val name: String, val volume: String, val color: Color, val image: String? = null)

private val wenyanCovers = mapOf(
    "必修上" to ("必修 上册" to "covers/wenyan-1.jpg"),
    "必修下" to ("必修 下册" to "covers/wenyan-2.jpg"),
    "选择性必修上" to ("选择性必修 上册" to "covers/wenyan-3.jpg"),
    "选择性必修中" to ("选择性必修 中册" to "covers/wenyan-4.jpg"),
    "选择性必修下" to ("选择性必修 下册" to "covers/wenyan-5.jpg"),
)

private val biologyCovers = mapOf(
    "分子与细胞" to ("必修 1" to "covers/bio-1.jpg"),
    "遗传与进化" to ("必修 2" to "covers/bio-2.jpg"),
    "稳态与调节" to ("选择性必修 1" to "covers/bio-3.jpg"),
    "生物与环境" to ("选择性必修 2" to "covers/bio-4.jpg"),
    "生物技术与工程" to ("选择性必修 3" to "covers/bio-5.jpg"),
)

private val examCover = Color(0xFF3A3633)

private fun books(subject: Subject, decks: List<DeckProgress>): List<Book> = when (subject) {
    Subject.WENYAN -> decks.map { d ->
        val c = wenyanCovers[d.deck.id]
        if (c != null) Book(d.deck.title, Cover("普通高中教科书", "语文", c.first, examCover, c.second), listOf(d))
        else Book(d.deck.title, Cover("文言文", d.deck.title.substringAfter("·"), d.deck.title.substringBefore("·", ""), examCover), listOf(d))
    }
    Subject.GUSHI -> decks.map { d -> Book(d.deck.title, Cover("名篇名句默写", d.deck.title, "", examCover), listOf(d)) }
    Subject.BIOLOGY -> decks.groupBy { it.deck.id.substringBefore(" · ") }.map { (name, ds) ->
        val c = biologyCovers[name]
        if (c != null) Book(name, Cover("普通高中教科书", "生物学", c.first, examCover, c.second), ds)
        else Book(name, Cover("生物学", name, "", examCover), ds)
    }
}

/** 书库：当前科目的计划和各册书，对应不背单词的“我的词书”。 */
@Composable
fun BooksScreen(app: AppState) {
    @Suppress("UNUSED_EXPRESSION") app.version
    val subject = app.subject
    val planner = app.planner()
    val overview = planner.overview()
    val decks = planner.decks()
    val books = books(subject, decks)
    val perDay = overview.newPerDay
    val daysLeft = if (perDay == 0) null else (overview.unlearnedInScope + perDay - 1) / perDay
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var expanded by remember(subject) { mutableStateOf<String?>(null) }

    fun select(ids: Set<String>, on: Boolean) {
        val all = decks.filter { it.selected }.map { it.deck.id }.toMutableSet()
        if (on) all += ids else all -= ids
        if (all.isNotEmpty()) { app.store.setDecks(subject, all); app.changed() }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
        item {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Subject.entries.forEach { s -> Chip(s.title, s == subject) { app.selectSubject(s) } }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(subject.title, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        Text("${overview.learned} / ${overview.total}", color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (daysLeft != null && daysLeft > 0) Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$daysLeft 天", fontSize = 22.sp, fontWeight = FontWeight.Medium,
                            color = if (daysLeft > app.daysToExam()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text("学完所选", color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                LinearProgressIndicator(
                    progress = { if (overview.total == 0) 0f else overview.learned.toFloat() / overview.total },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, drawStopIndicator = {},
                )
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("每日新学", fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Stepper("−") { app.store.setNewPerDay(subject, (perDay - 5).coerceAtLeast(0)); app.changed() }
                    Text("$perDay ${subject.unit}", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 14.dp))
                    Stepper("+") { app.store.setNewPerDay(subject, (perDay + 5).coerceAtMost(100)); app.changed() }
                }
            }
        }
        item {
            // 全部书籍：一键全选；全选时再点不取消（至少要留一本）。
            val selected = decks.count { it.selected }
            val state = when (selected) { 0 -> ToggleableState.Off; decks.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
            val toggle = { if (state != ToggleableState.On) select(decks.map { it.deck.id }.toSet(), true) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = toggle).padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("全部书籍", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("${books.size} 本 · 已选 ${books.count { b -> b.decks.any { it.selected } }} 本", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                TriStateCheckbox(state, onClick = toggle)
            }
        }
        items(books, key = { it.title }) { book ->
            val selected = book.decks.count { it.selected }
            val state = when (selected) { 0 -> ToggleableState.Off; book.decks.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
            val chapters = book.decks.size > 1
            val toggle = { select(book.decks.map { it.deck.id }.toSet(), state != ToggleableState.On) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    if (chapters) expanded = if (expanded == book.title) null else book.title else toggle()
                }.padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            ) {
                CoverView(book.cover, 58.dp)
                Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                    Text(book.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (chapters) "${book.decks.size} 章  " + if (expanded == book.title) "▴" else "▾" else book.cover.volume.ifEmpty { " " },
                        color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
                    )
                    Progress(book.learned, book.count)
                }
                TriStateCheckbox(state, onClick = toggle)
            }
            if (expanded == book.title) book.decks.forEach { d ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { select(setOf(d.deck.id), !d.selected) }
                        .padding(start = 94.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(d.deck.title.substringAfter(" · "), fontSize = 14.sp)
                        Progress(d.learned, d.deck.count)
                    }
                    Checkbox(checked = d.selected, onCheckedChange = { select(setOf(d.deck.id), it) })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Progress(learned: Int, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        LinearProgressIndicator(
            progress = { learned.toFloat() / count.coerceAtLeast(1) },
            modifier = Modifier.weight(1f).height(3.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, drawStopIndicator = {},
        )
        Text("$learned/$count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

/** 画出来的书封面，宽高比 3:4。 */
@Composable
private fun CoverView(cover: Cover, width: Dp) {
    if (cover.image != null) {
        val context = LocalContext.current
        val bitmap = remember(cover.image) { context.assets.open(cover.image).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }
        Image(
            bitmap, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(width, width * 4 / 3).shadow(3.dp, RoundedCornerShape(3.dp)).clip(RoundedCornerShape(3.dp)),
        )
        return
    }
    val k = width.value / 58f
    val long = cover.name.length > 3
    Box(
        Modifier.size(width, width * 4 / 3).shadow(3.dp, RoundedCornerShape(3.dp)).clip(RoundedCornerShape(3.dp))
            .background(Brush.linearGradient(listOf(cover.color, cover.color.copy(alpha = 0.8f).compositeOver(Color.Black))))
    ) {
        Box(Modifier.width((4 * k).dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.18f)))
        Column(Modifier.fillMaxSize().padding(start = (9 * k).dp, end = (4 * k).dp, top = (7 * k).dp, bottom = (7 * k).dp)) {
            Text(cover.series, color = Color.White.copy(alpha = 0.85f), fontSize = (5.5 * k).sp, lineHeight = (7 * k).sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(
                cover.name, color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold,
                fontSize = ((if (long) 9 else 14) * k).sp, lineHeight = ((if (long) 11 else 16) * k).sp,
            )
            if (cover.volume.isNotEmpty()) Text(
                cover.volume, color = Color.White.copy(alpha = 0.9f), fontSize = (6 * k).sp, lineHeight = (8 * k).sp,
                modifier = Modifier.padding(top = (2 * k).dp),
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.size((10 * k).dp, (1.5 * k).dp).background(Color.White.copy(alpha = 0.7f)))
        }
    }
}

@Composable
fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp)
    ) { content() }
}

@Composable
fun Stepper(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .clickable(onClick = onClick).padding(top = 4.dp),
        textAlign = TextAlign.Center,
    )
}
