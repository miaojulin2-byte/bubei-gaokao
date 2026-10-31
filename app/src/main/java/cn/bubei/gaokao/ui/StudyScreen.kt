package cn.bubei.gaokao.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.Session
import cn.bubei.gaokao.data.Session.Stage

@Composable
fun StudyScreen(app: AppState, session: Session) {
    @Suppress("UNUSED_EXPRESSION") app.step
    BackHandler { app.closeSession() }
    val card = session.current
    // 和不背单词一样：学习页的底是首页那幅画，强模糊再提亮，颜色连贯又不干扰读题。
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().blur(32.dp)) { SubjectBackdrop(app, session.subject, still = true) }
        val veil = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.background
        Box(Modifier.fillMaxSize().background(veil.copy(alpha = if (Build.VERSION.SDK_INT >= 31) 0.72f else 0.9f)))
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = { app.closeSession() }) { Icon(Icons.Default.Close, contentDescription = "结束") }
            LinearProgressIndicator(
                progress = { if (session.total == 0) 1f else session.finished.toFloat() / session.total },
                modifier = Modifier.weight(1f).height(4.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                drawStopIndicator = {},
            )
            Text(
                "${session.finished}/${session.total}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            )
            session.current?.let { SaveButton(app, it) }
        }
        if (card == null) {
            Finished(app, session)
            return@Column
        }
        val exampleIndex = session.memoryOf(card)?.reps ?: 0
        PullToSearch({ app.page = Page.SEARCH }, Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (session.stage == Stage.BACK) CardBack(card, exampleIndex, session.picked, onOpenText = { app.reading = it })
                else CardFront(card, exampleIndex, hint = session.stage == Stage.HINT, onChoose = { i -> app.act { choose(i) } }, onOpenText = { app.reading = it })
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (session.stage) {
                // 选择题在卡片里直接点选项，底部不放按钮。
                Stage.FRONT -> if (card !is cn.bubei.gaokao.data.Card.Choice) {
                    BigButton(session.subject.know, primary = true) { app.act { know() } }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("提示一下", Modifier.weight(1f)) { app.act { hint() } }
                        BigButton(session.subject.dontKnow, Modifier.weight(1f)) { app.act { dontKnow() } }
                    }
                }
                Stage.HINT -> {
                    BigButton("想起来了", primary = true) { app.act { knowAfterHint() } }
                    BigButton("还是不会") { app.act { dontKnow() } }
                }
                Stage.BACK -> {
                    val grade = session.pendingGrade
                    BigButton("下一个", primary = true, sub = nextLabel(session.nextInterval(grade))) { app.act { commit() } }
                    if (card is cn.bubei.gaokao.data.Card.Choice) {
                        if (session.slow) BigButton("不算慢", sub = nextLabel(session.nextInterval(3))) { app.act { commit(3) } }
                        else if (grade > 1) BigButton("其实是蒙的", sub = nextLabel(session.nextInterval(2))) { app.act { commit(2) } }
                    } else if (grade > 1) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("记错了", Modifier.weight(1f)) { app.act { commit(1) } }
                        if (session.slow) BigButton("不算慢", Modifier.weight(1f), sub = nextLabel(session.nextInterval(3))) { app.act { commit(3) } }
                        else if (grade == 3) BigButton("太简单", Modifier.weight(1f), sub = nextLabel(session.nextInterval(4))) { app.act { commit(4) } }
                    }
                }
            }
        }
    }
}

private fun nextLabel(days: Int) = when (days) {
    0 -> "今天已练过"
    1 -> "明天再见"
    else -> "$days 天后再见"
}

@Composable
private fun BigButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, sub: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
    val m = modifier.fillMaxWidth().height(if (sub != null) 58.dp else 52.dp)
    if (primary) Button(onClick, m, shape = shape, contentPadding = PaddingValues(0.dp)) { content() }
    else OutlinedButton(
        onClick, m, shape = shape, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) { content() }
}

@Composable
private fun Finished(app: AppState, session: Session) {
    LaunchedEffect(Unit) { app.changed() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("本组完成", fontFamily = Serif, fontSize = 34.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "${session.total} ${session.subject.unit}" + if (session.forgotten > 0) "，其中 ${session.forgotten} 次忘记后重练" else "，全部一次通过",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(220.dp, 52.dp)) { BigButton("回到首页", primary = true) { app.closeSession() } }
        TextButton(onClick = { app.closeSession(); app.tab = Tab.ME }, modifier = Modifier.padding(top = 8.dp)) { Text("看看进度") }
    }
}

/** 卡片详情（首页、搜索、本子点进来）：只看不评分。 */
@Composable
fun CardDetail(app: AppState, card: cn.bubei.gaokao.data.Card, exampleIndex: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    @Suppress("UNUSED_EXPRESSION") app.version
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "关闭") }
            Spacer(Modifier.weight(1f))
            SaveButton(app, card)
        }
        PullToSearch({ onClose(); app.page = Page.SEARCH }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 32.dp)) {
                CardBack(card, exampleIndex, onOpenText = { app.reading = it })
            }
        }
    }
}

@Composable
fun PassageDetail(passage: cn.bubei.gaokao.data.Passage, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "关闭") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 32.dp)) {
            Text(passage.middle, fontFamily = Serif, fontSize = 26.sp, lineHeight = 42.sp, color = MaterialTheme.colorScheme.primary)
            PassageView(passage, listOf(passage.middle))
        }
    }
}
