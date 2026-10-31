package cn.bubei.gaokao.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.Card
import cn.bubei.gaokao.data.Example
import cn.bubei.gaokao.data.Passage
import cn.bubei.gaokao.data.TextSpot

/** 把句子里的目标词标出颜色。 */
fun highlight(sentence: String, word: String, color: Color): AnnotatedString = buildAnnotatedString {
    val at = if (word.isEmpty()) -1 else sentence.indexOf(word)
    if (at < 0) { append(sentence); return@buildAnnotatedString }
    append(sentence.substring(0, at))
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(word) }
    append(sentence.substring(at + word.length))
}

fun Card.Word.exampleFor(index: Int): Example? = examples.getOrNull(index.mod(examples.size.coerceAtLeast(1)))

/** 生物填空：未揭晓时空白显示为 ①②…，揭晓后填入并着色。 */
fun clozeText(card: Card.Cloze, revealed: Boolean, hint: Boolean, color: Color): AnnotatedString = buildAnnotatedString {
    var n = 0
    for ((text, hidden) in card.parts) {
        if (!hidden) { append(text.replace("____", "＿＿")); continue }
        n++
        if (card.isQuestion) {
            if (revealed) { append("\n\n"); withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) { append(text.trim()) } }
            continue
        }
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, textDecoration = if (revealed) null else TextDecoration.Underline)) {
            append(
                when {
                    revealed -> text
                    hint -> " ${text.trim().take(1)}${"＿".repeat((text.trim().length - 1).coerceIn(1, 6))} "
                    else -> "  ${circled(n)}  "
                }
            )
        }
    }
}

private fun circled(n: Int) = if (n in 1..20) ('①' + (n - 1)).toString() else "($n)"

@Composable
fun CardFront(card: Card, exampleIndex: Int, hint: Boolean, onChoose: (Int) -> Unit = {}, onOpenText: ((TextSpot) -> Unit)? = null) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (card) {
        is Card.Word -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(card.word, fontFamily = Serif, fontSize = if (card.word.length > 4) 34.sp else 52.sp, textAlign = TextAlign.Center)
            if (card.pos.isNotEmpty()) Text(card.pos, color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            card.exampleFor(exampleIndex)?.let { ex ->
                Spacer(Modifier.height(36.dp))
                Text(highlight(ex.sentence, card.word, accent), fontFamily = Serif, fontSize = 20.sp, lineHeight = 34.sp, textAlign = TextAlign.Center)
                Text("《${ex.title}》", color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            if (hint) {
                Spacer(Modifier.height(28.dp))
                val others = card.examples.filterIndexed { i, _ -> i != exampleIndex.mod(card.examples.size.coerceAtLeast(1)) }.take(2)
                HintBox {
                    val meaning = card.exampleFor(exampleIndex)?.meaning.orEmpty()
                    if (meaning.isNotEmpty()) Text("这里的意思以「${meaning.take(1)}」开头，共 ${meaning.length} 字", color = muted, fontSize = 14.sp)
                    ExampleTranslation(exampleSpot(card.exampleFor(exampleIndex)), Modifier.padding(top = 8.dp))
                    others.forEach { Text(highlight(it.sentence, card.word, accent), fontFamily = Serif, fontSize = 15.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
        is Card.Dictation -> Column(modifier = Modifier.fillMaxWidth()) {
            SourceTag(card.source)
            Spacer(Modifier.height(20.dp))
            Text(dictationStem(card, filled = null, hint = hint, color = accent), fontSize = 19.sp, lineHeight = 34.sp)
            Text(
                if (card.open) "开放题：写出任意一组符合要求的名句" else "写在纸上，再翻看对照",
                color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 24.dp),
            )
        }
        is Card.Choice -> Column(modifier = Modifier.fillMaxWidth()) {
            SourceTag(card.source)
            Spacer(Modifier.height(18.dp))
            ChoiceStem(card)
            Spacer(Modifier.height(20.dp))
            card.options.forEachIndexed { i, option -> OptionRow(card, i, option, picked = null, onClick = { onChoose(i) }) }
        }
        is Card.Cloze -> Column(modifier = Modifier.fillMaxWidth()) {
            Text(card.deck.substringAfter(" · "), color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Text(clozeText(card, revealed = false, hint = hint, color = accent), fontSize = 19.sp, lineHeight = 32.sp)
            if (hint && card.isQuestion) {
                Spacer(Modifier.height(20.dp))
                HintBox { Text("答案以「${card.parts[1].first.trim().take(2)}」开头", color = muted, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
fun CardBack(card: Card, exampleIndex: Int, picked: Int? = null, onOpenText: ((TextSpot) -> Unit)? = null) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (card) {
        is Card.Word -> Column(modifier = Modifier.fillMaxWidth()) {
            val ex = card.exampleFor(exampleIndex)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(card.word, fontFamily = Serif, fontSize = 36.sp)
                if (card.pos.isNotEmpty()) Text(card.pos, color = muted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
            }
            if (ex != null) {
                Text(ex.meaning.ifEmpty { card.gloss }, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp))
                Text(highlight(ex.sentence, card.word, accent), fontFamily = Serif, fontSize = 17.sp, lineHeight = 28.sp, modifier = Modifier.padding(top = 10.dp))
                val spot = exampleSpot(ex)
                SourceTitle("《${ex.title}》", spot, onOpenText, Modifier.padding(top = 4.dp))
                ExampleTranslation(spot, Modifier.padding(top = 10.dp))
            }
            if (card.gloss != ex?.meaning) {
                Section("义项")
                Text(card.gloss.replace(Regex("(?=[②-⑳])"), "\n"), fontSize = 15.sp, lineHeight = 25.sp)
            }
            val others = card.examples.filter { it != ex }
            if (others.isNotEmpty()) {
                Section("其他例句")
                others.forEach {
                    Text(highlight(it.sentence, card.word, accent), fontFamily = Serif, fontSize = 15.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 10.dp))
                    Row(Modifier.padding(top = 2.dp)) {
                        Text("${it.meaning}  ·  ", color = muted, fontSize = 12.sp)
                        SourceTitle("《${it.title}》", exampleSpot(it), onOpenText, fontSize = 12)
                    }
                }
            }
        }
        is Card.Dictation -> Column(modifier = Modifier.fillMaxWidth()) {
            SourceTag(card.source)
            Spacer(Modifier.height(20.dp))
            Text(dictationStem(card, filled = card.answers[0], hint = false, color = accent), fontSize = 19.sp, lineHeight = 34.sp)
            card.contexts[0]?.let { PassageView(it, card.answers[0], onOpenText) }
                ?: Text("原文不在本应用的篇目库中（课外或初中篇目）", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            if (card.note.isNotEmpty()) Text(card.note, color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            if (card.answers.size > 1) {
                Section("其他可接受的答案")
                card.answers.drop(1).forEachIndexed { i, alt ->
                    Text(alt.joinToString("，"), fontFamily = Serif, fontSize = 17.sp, color = accent, modifier = Modifier.padding(top = 10.dp))
                    card.contexts[i + 1]?.let { Text("${it.author}《${it.title}》", color = muted, fontSize = 12.sp) }
                }
            }
        }
        is Card.Choice -> Column(modifier = Modifier.fillMaxWidth()) {
            SourceTag(card.source)
            Spacer(Modifier.height(18.dp))
            ChoiceStem(card)
            Spacer(Modifier.height(20.dp))
            card.options.forEachIndexed { i, option -> OptionRow(card, i, option, picked = picked ?: card.answer, onClick = null) }
            Section(if (picked == null) "解析" else if (picked == card.answer) "答对了 · 解析" else "答错了 · 解析")
            Text(card.explanation, fontSize = 15.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 6.dp))
            if (card.subject == cn.bubei.gaokao.data.Subject.WENYAN) QuotedSources(card, onOpenText) { Section("课文原句") }
        }
        is Card.Cloze -> Column(modifier = Modifier.fillMaxWidth()) {
            Text(card.deck.substringAfter(" · "), color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Text(clozeText(card, revealed = true, hint = false, color = accent), fontSize = 19.sp, lineHeight = 32.sp)
            card.images.forEach { AssetImage("bio/$it") }
        }
    }
}

/** 默写题干：{} 处为空。未揭晓时显示下划线空格（提示时带首字），揭晓后填入答案。 */
fun dictationStem(card: Card.Dictation, filled: List<String>?, hint: Boolean, color: Color): AnnotatedString = buildAnnotatedString {
    val pieces = card.stem.split("{}")
    pieces.forEachIndexed { i, piece ->
        append(piece)
        if (i == pieces.lastIndex) return@forEachIndexed
        val answer = card.answers[0].getOrElse(i) { "" }
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, textDecoration = if (filled == null) TextDecoration.Underline else null)) {
            append(
                when {
                    filled != null -> filled.getOrElse(i) { "" }
                    hint && !card.open -> " " + answer.take(1) + "\u3000".repeat((answer.length - 1).coerceIn(2, 8)) + " "
                    else -> "\u3000".repeat(answer.length.coerceIn(4, 9))
                }
            )
        }
    }
}

/** 原文前后文：答案句着色，前后各一句淡色。 */
@Composable
fun PassageView(passage: Passage, answer: List<String>, onOpenText: ((TextSpot) -> Unit)? = null) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // 出处能对上全文时可点，打开全文并标出答案句。
    Spacer(Modifier.height(24.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SourceTitle("${passage.author}《${passage.title}》", passageSpot(passage), onOpenText, Modifier.padding(top = 14.dp, bottom = 2.dp))
    Text(
        buildAnnotatedString {
            if (passage.before.isNotEmpty()) withStyle(SpanStyle(color = muted)) { append(passage.before) }
            var rest = passage.middle
            for (line in answer) {
                val at = rest.indexOf(line)
                if (at < 0) continue
                append(rest.substring(0, at))
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) { append(line) }
                rest = rest.substring(at + line.length)
            }
            append(rest)
            if (passage.after.isNotEmpty()) withStyle(SpanStyle(color = muted)) { append(passage.after) }
        },
        fontFamily = Serif, fontSize = 17.sp, lineHeight = 30.sp, modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ChoiceStem(card: Card.Choice) {
    val accent = MaterialTheme.colorScheme.primary
    card.stem.split("\n").forEachIndexed { i, line ->
        Text(
            if (card.highlight.isEmpty()) AnnotatedString(line) else highlight(line, card.highlight, accent),
            fontFamily = if (card.subject == cn.bubei.gaokao.data.Subject.BIOLOGY) null else Serif,
            fontSize = if (i == 0) 18.sp else 17.sp, lineHeight = 30.sp,
            modifier = Modifier.padding(top = if (i == 0) 0.dp else 10.dp),
        )
    }
}

/** 选项行。picked 为空表示还没作答；作答后正确项标绿，选错的一项标红。 */
@Composable
private fun OptionRow(card: Card.Choice, index: Int, text: String, picked: Int?, onClick: (() -> Unit)?) {
    val right = Color(0xFF2E8B57)
    val wrong = Color(0xFFC0392B)
    val state = when {
        picked == null -> null
        index == card.answer -> right
        index == picked -> wrong
        else -> null
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .background(state?.copy(alpha = 0.1f) ?: MaterialTheme.colorScheme.surfaceContainer, shape)
            .border(1.dp, state ?: MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (card.options.size > 2) Text("ABCD"[index].toString(), fontWeight = FontWeight.Medium, color = state ?: MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
        Text(text, fontSize = 16.sp, lineHeight = 24.sp, color = state ?: MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (state != null) Text(if (state == right) "✓" else "✗", color = state, fontSize = 18.sp)
    }
}

@Composable
private fun SourceTag(source: String) {
    Text(
        source, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
        modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@Composable
fun HintBox(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun AssetImage(path: String) {
    val context = LocalContext.current
    val bitmap = remember(path) { context.assets.open(path).use { BitmapFactory.decodeStream(it) } } ?: return
    Image(
        bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}
