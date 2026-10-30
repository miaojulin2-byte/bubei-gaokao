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

@Composable
fun SearchScreen(app: AppState, onOpen: (Card) -> Unit) {
    BackHandler { app.page = null }
    val query = app.searchQuery
    val results = remember(query) { search(app, query) }
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
                    if (query.isEmpty()) Text("字词、名句、知识点", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
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
        if (query.isNotBlank() && results.isEmpty()) Empty("没有找到「${query.trim()}」")
        CardList(app, results, onOpen)
    }
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
fun CardList(app: AppState, cards: List<Card>, onOpen: (Card) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize()) {
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
