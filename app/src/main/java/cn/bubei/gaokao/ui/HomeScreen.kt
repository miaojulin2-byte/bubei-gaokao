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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.Card
import cn.bubei.gaokao.data.Scene
import cn.bubei.gaokao.data.Subject
import cn.bubei.gaokao.data.daysToGaokao

private val shadow = TextStyle(shadow = Shadow(Color(0x99000000), blurRadius = 12f))

/**
 * 首页：风景图只铺在底栏上方。图上的文字随科目变化，但都写的是图里的景：
 * 古诗文是这句原文，文言是这句里的一个词，生物是画面能说明的知识点。
 */
@Composable
fun HomeScreen(app: AppState, onOpenCard: (Card, Int) -> Unit, onOpenPassage: (Scene) -> Unit) {
    @Suppress("UNUSED_EXPRESSION") app.version
    val scene = remember(app.day) { app.scene() }
    val overview = app.planner().overview()
    val context = app.context
    val bitmap = remember(scene.image) { context.assets.open(scene.image).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }

    Box(Modifier.fillMaxSize()) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x66000000), 0.35f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0x80000000))
            )
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                SubjectSwitch(app.subject, app::selectSubject)
                Spacer(Modifier.weight(1f))
                Text("距高考 ${daysToGaokao(app.day)} 天", color = Color.White, fontSize = 13.sp, style = shadow)
            }
            Featured(app, scene, Modifier.padding(top = 48.dp), onOpenCard, onOpenPassage)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudyCard("Learn", "新学", overview.newLeft, Modifier.weight(1f)) { app.startLearn() }
                StudyCard("Review", "复习", overview.due, Modifier.weight(1f)) { app.startReview() }
            }
            Text(
                "${app.subject.title}  ·  已学 ${overview.learned}/${overview.total}  ·  今日新学 ${overview.newDoneToday}/${overview.newPerDay}",
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, style = shadow,
                modifier = Modifier.padding(top = 10.dp, bottom = 14.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun SubjectSwitch(current: Subject, onSelect: (Subject) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x33000000)).padding(3.dp)) {
        Subject.entries.forEach { s ->
            val selected = s == current
            Text(
                when (s) { Subject.WENYAN -> "文言"; Subject.GUSHI -> "古诗文"; Subject.BIOLOGY -> "生物" },
                color = if (selected) Color(0xFF242220) else Color.White,
                fontSize = 14.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (selected) Color.White.copy(alpha = 0.92f) else Color.Transparent)
                    .clickable { onSelect(s) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Featured(app: AppState, scene: Scene, modifier: Modifier, onOpenCard: (Card, Int) -> Unit, onOpenPassage: (Scene) -> Unit) {
    val white = Color.White
    when (app.subject) {
        Subject.GUSHI -> Column(modifier.fillMaxWidth().clickable { onOpenPassage(scene) }) {
            Text(scene.line, color = white, fontFamily = Serif, fontSize = 26.sp, lineHeight = 42.sp, style = shadow)
            Text(
                "—— ${scene.passage.author}《${scene.passage.title}》", color = white.copy(alpha = 0.9f), fontSize = 14.sp, style = shadow,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Subject.WENYAN -> {
            val card = app.content(Subject.WENYAN).byId[scene.cards[Subject.WENYAN]] as? Card.Word ?: return
            val ex = card.exampleFor(scene.example)
            Column(modifier.fillMaxWidth().clickable { onOpenCard(card, scene.example) }) {
                Text(card.word, color = white, fontFamily = Serif, fontSize = 56.sp, style = shadow)
                Text(ex?.meaning.orEmpty(), color = white, fontSize = 17.sp, style = shadow, modifier = Modifier.padding(top = 4.dp))
                if (ex != null) {
                    Text(
                        highlight(ex.sentence, card.word, Color(0xFFFFE08A)), color = white, fontFamily = Serif,
                        fontSize = 18.sp, lineHeight = 30.sp, style = shadow, modifier = Modifier.padding(top = 20.dp),
                    )
                    Text("《${ex.title}》", color = white.copy(alpha = 0.85f), fontSize = 13.sp, style = shadow, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Subject.BIOLOGY -> {
            val card = app.content(Subject.BIOLOGY).byId[scene.cards[Subject.BIOLOGY]] as? Card.Cloze ?: return
            Column(
                modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x47000000))
                    .clickable { onOpenCard(card, 0) }.padding(18.dp)
            ) {
                Text(card.deck.substringAfter(" · "), color = white.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(
                    clozeText(card, revealed = true, hint = false, color = Color(0xFFB6F0CF)), color = white,
                    fontSize = 17.sp, lineHeight = 28.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StudyCard(title: String, label: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.78f))
            .clickable(enabled = count > 0, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(title, color = Color(0xFF242220), fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(
                if (count > 0) count.toString() else "✓", color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                fontSize = 18.sp, fontWeight = FontWeight.Medium,
            )
            Text(if (count > 0) "  待$label" else "  今日完成", color = Color(0xFF7D7873), fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.height(0.dp))
    }
}
