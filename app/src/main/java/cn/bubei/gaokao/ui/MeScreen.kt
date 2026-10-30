package cn.bubei.gaokao.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import java.time.YearMonth
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.Subject
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun MeScreen(app: AppState) {
    @Suppress("UNUSED_EXPRESSION") app.version
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val activity = app.store.activity()
    val today = app.day
    val streak = streak(activity, today)
    val context = app.context
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(app.store.export().toByteArray()) } }
            .onSuccess { Toast.makeText(context, "已导出", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show() }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pendingImport = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Panel {
            Row {
                Stat("今日作答", "${activity[today] ?: 0}", "次", Modifier.weight(1f))
                Stat("累计学习", "${activity.size}", "天", Modifier.weight(1f))
            }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { app.page = Page.CALENDAR }) {
                Text("学习日历", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("连续学习 $streak 天  ›", color = muted, fontSize = 13.sp)
            }
            WeekStrip(activity, today, Modifier.padding(top = 16.dp))
        }
        Panel {
            Text("各科进度", color = muted, fontSize = 13.sp)
            Subject.entries.forEach { s ->
                val o = app.planner(s).overview()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                    Text(s.title, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("已学 ${o.learned}  ·  长期记忆 ${o.longTerm}  ·  待复习 ${o.due}", color = muted, fontSize = 12.sp)
                }
            }
        }
        Panel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { app.page = Page.NOTEBOOKS }.padding(vertical = 4.dp),
            ) {
                Text("★", color = androidx.compose.ui.graphics.Color(0xFFE0A526), fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
                Text("我的本子", fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("${app.store.notebooks().size} 本  ›", color = muted, fontSize = 15.sp)
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("高考年份", fontSize = 16.sp, modifier = Modifier.weight(1f))
                val year = app.store.gaokaoYear
                Stepper("−") { app.store.gaokaoYear = (year - 1).coerceAtLeast(LocalDate.now().year); app.changed() }
                Text("$year", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 14.dp))
                Stepper("+") { app.store.gaokaoYear = (year + 1).coerceAtMost(LocalDate.now().year + 3); app.changed() }
            }
        }
        Panel {
            val subject = app.subject
            var retention by remember(subject) { mutableStateOf(app.store.retention(subject).toFloat()) }
            Text("${subject.title} · 目标记忆率", color = muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = retention, onValueChange = { retention = (it * 100).roundToInt() / 100f },
                    onValueChangeFinished = { app.store.setRetention(subject, retention.toDouble()); app.changed() },
                    valueRange = 0.8f..0.97f, modifier = Modifier.weight(1f),
                )
                Text("${(retention * 100).roundToInt()}%", fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("学习记录", color = muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { exporter.launch("不背高考-备份-${LocalDate.now()}.json") }) { Text("导出") }
                TextButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Text("从备份恢复") }
                TextButton(onClick = { confirmReset = true }) { Text("重置", color = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }

    if (confirmReset) ResetDialog(app) { confirmReset = false }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("用备份替换当前记录？") },
            text = { Text("当前三科的学习记录和设置会被备份里的内容整体替换。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    runCatching { app.store.import(text) }
                        .onSuccess { app.selectSubject(app.store.subject); app.changed(); Toast.makeText(context, "已恢复", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "恢复失败：${it.message}", Toast.LENGTH_LONG).show() }
                }) { Text("替换") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ResetDialog(app: AppState, onDone: () -> Unit) {
    val context = app.context
    fun reset(s: Subject?) {
        app.store.reset(s)
        app.changed()
        onDone()
        Toast.makeText(context, "已重置", Toast.LENGTH_SHORT).show()
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("重置学习记录？") },
        text = { Text("已学的卡片会变回没学过，相应的学习日历记录也会删除，不能撤销。本子和设置保留。") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { reset(app.subject) }) { Text("只重置${app.subject.short}", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { reset(null) }) { Text("三科全部重置", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDone) { Text("取消") }
            }
        },
    )
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
        }
    }
}

private val weekLabels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** 连续学习天数：今天还没学时从昨天往前数。 */
fun streak(activity: Map<Long, Int>, today: Long): Int =
    generateSequence(if (activity.containsKey(today)) today else today - 1) { it - 1 }.takeWhile { activity.containsKey(it) }.count()

/** 本周一到周日，照不背单词“签到日历”卡片：学过的日子实心圆，今天写“今”。 */
@Composable
private fun WeekStrip(activity: Map<Long, Int>, today: Long, modifier: Modifier) {
    val monday = today - (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
    Column(modifier) {
        WeekHeader(today - monday)
        Row(Modifier.padding(top = 10.dp)) {
            for (i in 0 until 7) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { DayDot(monday + i, activity, today) }
        }
    }
}

@Composable
private fun WeekHeader(todayIndex: Long?) {
    Row {
        weekLabels.forEachIndexed { i, label ->
            Text(
                label, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                color = if (i.toLong() == todayIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayDot(day: Long, activity: Map<Long, Int>, today: Long) {
    val accent = MaterialTheme.colorScheme.primary
    val studied = activity.containsKey(day)
    val filled = studied || day == today
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(32.dp).clip(CircleShape)
            .background(if (day == today && !studied) accent.copy(alpha = 0.18f) else if (filled) accent else Color.Transparent),
    ) {
        Text(
            if (day == today) "今" else LocalDate.ofEpochDay(day).dayOfMonth.toString(),
            fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = when {
                studied -> Color.White
                day == today -> accent
                day > today -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** 学习日历整页：按月翻，下面是连续和累计天数。 */
@Composable
fun CalendarPage(app: AppState) {
    BackHandler { app.page = null }
    val activity = app.store.activity()
    val today = app.day
    val thisMonth = YearMonth.from(LocalDate.ofEpochDay(today))
    val first = activity.keys.minOrNull()?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: thisMonth
    var month by remember { mutableStateOf(thisMonth) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = { app.page = null }, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text("学习日历", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val canBack = month > first
                val canForward = month < thisMonth
                Text("‹", fontSize = 26.sp, color = if (canBack) muted else muted.copy(alpha = 0.3f),
                    modifier = Modifier.clip(CircleShape).clickable(enabled = canBack) { month = month.minusMonths(1) }.padding(horizontal = 14.dp))
                Text("${month.year}年${month.monthValue}月", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp))
                Text("›", fontSize = 26.sp, color = if (canForward) muted else muted.copy(alpha = 0.3f),
                    modifier = Modifier.clip(CircleShape).clickable(enabled = canForward) { month = month.plusMonths(1) }.padding(horizontal = 14.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val todayIndex = if (month == thisMonth) (LocalDate.ofEpochDay(today).dayOfWeek.value - 1).toLong() else null
            WeekHeader(todayIndex)
            val start = month.atDay(1).toEpochDay()
            val lead = month.atDay(1).dayOfWeek.value - 1
            val cells = lead + month.lengthOfMonth()
            for (week in 0 until (cells + 6) / 7) Row(Modifier.padding(top = 14.dp)) {
                for (d in 0 until 7) {
                    val index = week * 7 + d - lead
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (index in 0 until month.lengthOfMonth()) DayDot(start + index, activity, today)
                        else Spacer(Modifier.size(32.dp))
                    }
                }
            }
            Row(Modifier.padding(top = 32.dp)) {
                Stat("连续学习", "${streak(activity, today)}", "天", Modifier.weight(1f))
                Stat("累计学习", "${activity.size}", "天", Modifier.weight(1f))
            }
        }
    }
}
