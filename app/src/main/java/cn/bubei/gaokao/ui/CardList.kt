package cn.bubei.gaokao.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.Card
import cn.bubei.gaokao.data.Subject
import cn.bubei.gaokao.data.TextSpot
import cn.bubei.gaokao.data.Texts
import cn.bubei.gaokao.data.hanzi

/** 列表里一张卡片的标题和摘要。 */
private fun Card.title(): String = when (this) {
    is Card.Word -> word
    is Card.Dictation -> answers.firstOrNull()?.joinToString("，").orEmpty()
    is Card.Choice -> source
    is Card.Cloze -> deck.substringAfter(" · ")
}

private fun Card.summary(): String = when (this) {
    is Card.Word -> gloss
    is Card.Dictation -> stem.replace("{}", "____")
    is Card.Choice -> stem
    is Card.Cloze -> parts.joinToString("") { it.first }
}

private fun Card.searchText(): String = when (this) {
    is Card.Word -> word + "\n" + gloss + "\n" + examples.joinToString("\n") { "${it.title} ${it.sentence} ${it.meaning}" }
    is Card.Dictation -> stem + "\n" + source + "\n" + answers.flatten().joinToString("\n") +
        contexts.filterNotNull().joinToString("\n") { "${it.title} ${it.author}" }
    is Card.Choice -> stem + "\n" + source + "\n" + options.joinToString("\n") + "\n" + explanation
    is Card.Cloze -> deck + "\n" + parts.joinToString("") { it.first }
}

/** 三科一起搜。标题正好是搜索词的排最前，其次是标题里含搜索词的。 */
private fun search(app: AppState, query: String): List<Card> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return Subject.entries.flatMap { app.content(it).cards }
        .filter { it.searchText().contains(q) }
        .sortedBy { card -> card.title().let { if (it == q) 0 else if (it.contains(q)) 1 else 2 } }
        .take(200)
}

/** 搜到的课文：篇名命中时 range 为空，否则是命中的那几句。 */
private class TextHit(val spot: TextSpot, val byTitle: Boolean)

/**
 * 搜文言课文：篇名、原文（不论标点，可跨句）、译文关键词。
 * 每篇先看篇名，再逐句找；一句也没中时把整篇连起来再找一次，接住跨句的引文。
 */
private fun searchTexts(app: AppState, query: String): List<TextHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val han = hanzi(q)
    val titles = mutableListOf<TextHit>()
    val lines = mutableListOf<TextHit>()
    for (text in Texts.all(app.context)) {
        if (text.title.contains(q) || (han.isNotEmpty() && hanzi(text.title).contains(han))) titles += TextHit(TextSpot(text, IntRange.EMPTY), true)
        val before = lines.size
        text.lines.forEachIndexed { i, line ->
            if ((han.isNotEmpty() && hanzi(line.original).contains(han)) || line.translation.contains(q)) lines += TextHit(TextSpot(text, i..i), false)
        }
        if (lines.size == before) text.locate(q)?.let { lines += TextHit(TextSpot(text, it), false) }
    }
    return titles + lines.take(100)
}

@Composable
fun SearchScreen(app: AppState, onOpenText: (TextSpot) -> Unit, onOpen: (Card) -> Unit) {
    BackHandler { app.page = null }
    val query = app.searchQuery
    val results = remember(query) { search(app, query) }
    val texts = remember(query) { searchTexts(app, query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
            IconButton(onClick = { app.page = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("课文、原句、字词、知识点", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    BasicTextField(
                        query, { app.searchQuery = it }, singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) Icon(
                    Icons.Default.Close, contentDescription = "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { app.searchQuery = "" },
                )
            }
        }
        if (query.isNotBlank() && results.isEmpty() && texts.isEmpty()) Empty("没有找到「${query.trim()}」")
        CardList(app, results, onOpen) {
            if (texts.isNotEmpty()) item { GroupLabel("课文 · ${texts.size}") }
            items(texts.size) { TextHitRow(texts[it], query.trim()) { onOpenText(texts[it].spot) } }
            if (texts.isNotEmpty() && results.isNotEmpty()) item { GroupLabel("卡片 · ${results.size}") }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

/** 篇名命中显示篇名和作者；句子命中显示原句（搜索词着色）和这句的译文。 */
@Composable
private fun TextHitRow(hit: TextHit, query: String, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val text = hit.spot.text
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (hit.byTitle) {
            Text(highlight(text.title, query, accent), fontFamily = Serif, fontSize = 17.sp)
            Text("${text.author}  ·  ${text.volume}  ·  ${text.lines.size} 句", color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
        } else {
            val lines = hit.spot.range.map { text.lines[it] }
            Text(
                highlight(lines.joinToString("") { it.original }, query, accent), fontFamily = Serif, fontSize = 16.sp, lineHeight = 26.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Text(
                highlight(lines.joinToString("") { it.translation }, query, accent), color = muted, fontSize = 13.sp, lineHeight = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
            )
            Text("《${text.title}》", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

val Subject.short get() = when (this) { Subject.WENYAN -> "文言"; Subject.GUSHI -> "古诗文"; Subject.BIOLOGY -> "生物" }

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 14.sp,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
fun Empty(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(top = 48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
fun CardList(app: AppState, cards: List<Card>, onOpen: (Card) -> Unit, header: LazyListScope.() -> Unit = {}) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize()) {
        header()
        items(cards, key = { it.id }) { card ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(card) }.padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            card.title(), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontFamily = if (card.subject == Subject.BIOLOGY) null else Serif, modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            card.subject.short, color = card.subject.accent(false), fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(4.dp))
                                .background(card.subject.accent(false).copy(alpha = 0.1f)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    Text(card.summary(), color = muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                SaveButton(app, card)
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
