package cn.bubei.gaokao.ui

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
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
    val streak = generateSequence(if (activity.containsKey(today)) today else today - 1) { it - 1 }
        .takeWhile { activity.containsKey(it) }.count()
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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Panel {
            Row {
                Stat("连续学习", "$streak 天", Modifier.weight(1f))
                Stat("累计学习", "${activity.size} 天", Modifier.weight(1f))
                Stat("今日作答", "${activity[today] ?: 0} 次", Modifier.weight(1f))
            }
            Spacer(Modifier.padding(top = 18.dp))
            Calendar(activity, today)
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
                modifier = Modifier.fillMaxWidth().clickable { app.page = Page.STARS }.padding(vertical = 4.dp),
            ) {
                Text("★", color = androidx.compose.ui.graphics.Color(0xFFE0A526), fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
                Text("收藏夹", fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("${app.store.starred().size}  ›", color = muted, fontSize = 15.sp)
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
        text = { Text("已学的卡片会变回没学过，相应的学习日历记录也会删除，不能撤销。收藏和设置保留。") },
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
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 最近 5 周的学习日历，颜色深浅表示当天作答次数。 */
@Composable
private fun Calendar(activity: Map<Long, Int>, today: Long) {
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    val weekday = LocalDate.ofEpochDay(today).dayOfWeek.value - 1
    val start = today - weekday - 28
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            "一二三四五六日".forEach {
                Text(it.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        for (week in 0 until 5) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (d in 0 until 7) {
                val day = start + week * 7 + d
                val n = activity[day] ?: 0
                val color = when {
                    day > today -> empty.copy(alpha = 0.4f)
                    n == 0 -> empty
                    else -> accent.copy(alpha = (0.3f + n / 120f).coerceAtMost(1f))
                }
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(5.dp)).background(color))
            }
        }
    }
}
