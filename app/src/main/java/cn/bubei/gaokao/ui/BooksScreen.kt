package cn.bubei.gaokao.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.Subject
import cn.bubei.gaokao.data.daysToGaokao

/** 书库：当前科目的学习计划和新学范围，对应不背单词“我的学习”里的计划卡。 */
@Composable
fun BooksScreen(app: AppState) {
    @Suppress("UNUSED_EXPRESSION") app.version
    val subject = app.subject
    val planner = app.planner()
    val overview = planner.overview()
    val decks = planner.decks()
    val perDay = overview.newPerDay
    val daysLeft = if (perDay == 0) null else (overview.unlearnedInScope + perDay - 1) / perDay
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        item {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Subject.entries.forEach { s ->
                    val selected = s == subject
                    Text(
                        s.title, fontSize = 14.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { app.selectSubject(s) }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        item {
            Panel {
                Text("计划", color = muted, fontSize = 13.sp)
                Text(subject.title, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                LinearProgressIndicator(
                    progress = { if (overview.total == 0) 0f else overview.learned.toFloat() / overview.total },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, drawStopIndicator = {},
                )
                Text("已学 ${overview.learned}/${overview.total}", color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                val examDays = daysToGaokao(app.day)
                Text(
                    when {
                        daysLeft == null -> "已暂停新学"
                        daysLeft == 0 -> "所选范围已学完，只剩复习"
                        daysLeft > examDays -> "按现在的速度，学完所选要 $daysLeft 天，晚于高考（还有 $examDays 天），可以调高每日新学"
                        else -> "按现在的速度，再学 $daysLeft 天学完所选，离高考还有 $examDays 天"
                    },
                    color = if (daysLeft != null && daysLeft > examDays) MaterialTheme.colorScheme.primary else muted,
                    fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp),
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
            Text(
                "新学范围（复习不受范围限制）", color = muted, fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        items(decks, key = { it.deck.id }) { d ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    val all = decks.filter { it.selected }.map { it.deck.id }.toMutableSet()
                    if (d.selected) all.remove(d.deck.id) else all.add(d.deck.id)
                    if (all.isNotEmpty()) { app.store.setDecks(subject, all); app.changed() }
                }.padding(horizontal = 12.dp, vertical = 2.dp),
            ) {
                Checkbox(checked = d.selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(d.deck.title, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        LinearProgressIndicator(
                            progress = { d.learned.toFloat() / d.deck.count.coerceAtLeast(1) },
                            modifier = Modifier.weight(1f).height(3.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, drawStopIndicator = {},
                        )
                        Text("${d.learned}/${d.deck.count}", color = muted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
private fun Stepper(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .clickable(onClick = onClick).padding(top = 4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
