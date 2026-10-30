package cn.bubei.gaokao.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.Card
import cn.bubei.gaokao.data.Scene
import cn.bubei.gaokao.data.Subject
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private val shadow = TextStyle(shadow = Shadow(Color(0x99000000), blurRadius = 12f))

/** 生物首页：一张显微照片（或程序画的 DNA）配一张讲同一个东西的卡片。 */
private class BioScene(val image: String?, val cardId: String)

private val bioScenes = listOf(
    BioScene(null, "anki-1693149782555-c1"),
    BioScene("backdrops/chloro.jpg", "anki-1684071387570-basic"),
    BioScene("backdrops/neurons.jpg", "anki-1701615514180-c1"),
    BioScene("backdrops/blood.jpg", "anki-1687098847266-basic"),
    BioScene("backdrops/plasmo.jpg", "anki-1685968597918-basic"),
    BioScene("backdrops/yeast.jpg", "anki-1686455507173-basic"),
    BioScene("backdrops/bee.jpg", "anki-1710078823969-c1"),
)

/**
 * 文言首页每天一个必修课文里的常考实词。画好插图的词优先，例句用插图画的那一句；
 * 其余的词（至少三个例句）隔天换一个，例句按天轮换。
 */
private fun dailyWord(app: AppState): Pair<Card.Word, Int>? {
    val illustrated = illustratedCards(app.context).mapNotNull { app.content(Subject.WENYAN).byId[it] as? Card.Word }
    if (illustrated.isNotEmpty()) {
        val word = illustrated[(app.day % illustrated.size).toInt()]
        return word to (loadIllustration(app.context, word.id)?.example ?: 0)
    }
    val words = app.content(Subject.WENYAN).cards.filterIsInstance<Card.Word>()
        .filter { it.pos == "实词" && it.examples.size >= 3 && it.deck.startsWith("必修") }
    val word = words.getOrNull(((app.day * 37) % words.size.coerceAtLeast(1)).toInt()) ?: return null
    return word to (app.day % word.examples.size).toInt()
}

/**
 * 首页：三科各占一页，左右滑动切换，和不背单词一样往下拉打开搜索。
 * 文言是按例句语境画的水墨动画，古诗文是与诗句相配的风景照，生物是显微照片或旋转的 DNA。
 * 滑动时背景比文字移得慢，有前后层次。
 */
@Composable
fun HomeScreen(app: AppState, onOpenCard: (Card, Int) -> Unit, onOpenPassage: (Scene) -> Unit) {
    @Suppress("UNUSED_EXPRESSION") app.version
    val subjects = Subject.entries
    val pager = rememberPagerState(initialPage = app.subject.ordinal) { subjects.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { app.selectSubject(subjects[it]) } }
    // 在书库等页面换了科目，首页跟着翻过去。
    LaunchedEffect(app.subject) {
        if (pager.settledPage != app.subject.ordinal && !pager.isScrollInProgress) pager.animateScrollToPage(app.subject.ordinal)
    }

    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val pull = remember { Animatable(0f) }
    val position = pager.currentPage + pager.currentPageOffsetFraction
    // 文言页是浅色宣纸，顶栏和底栏的字随滑动在白色和墨色之间过渡。
    val paperness = (1f - position.absoluteValue).coerceIn(0f, 1f)
    val chrome = lerp(Color.White, Ink, paperness)

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (pull.value >= threshold) app.page = Page.SEARCH
                    scope.launch { pull.animateTo(0f) }
                },
                onDragCancel = { scope.launch { pull.animateTo(0f) } },
            ) { change, dy ->
                change.consume()
                scope.launch { pull.snapTo((pull.value + dy * 0.6f).coerceIn(0f, threshold * 1.5f)) }
            }
        }
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            SubjectPage(app, subjects[page], pager, page, pull.value, onOpenCard, onOpenPassage)
        }

        val progress = (pull.value / threshold).coerceAtMost(1f)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                .graphicsLayer { alpha = progress; translationY = pull.value * 0.35f }
                .clip(RoundedCornerShape(50)).background(Color(0x59000000)).padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(if (progress >= 1f) "松开搜索" else "下拉搜索", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { translationY = pull.value }.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        ) {
            SubjectSwitch(position, chrome, paperness) { i -> scope.launch { pager.animateScrollToPage(i) } }
            Spacer(Modifier.weight(1f))
            Text(
                "距高考 ${app.daysToExam()} 天", color = chrome, fontSize = 13.sp,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f * (1 - paperness)), blurRadius = 12f)),
            )
        }

        // 底栏：三个线条图标，直接画在背景上。
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            NavIcon(BookIcon, "书库", chrome) { app.tab = Tab.BOOKS }
            NavIcon(NotebookIcon, "本子", chrome) { app.page = Page.NOTEBOOKS }
            NavIcon(PersonIcon, "我的", chrome) { app.tab = Tab.ME }
        }
    }
}

@Composable
private fun SubjectPage(
    app: AppState, s: Subject, pager: PagerState, page: Int, pull: Float,
    onOpenCard: (Card, Int) -> Unit, onOpenPassage: (Scene) -> Unit,
) {
    val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
    val overview = app.planner(s).overview()
    val word = remember(app.day) { dailyWord(app) }
    val bio = bioScenes[(app.day % bioScenes.size).toInt()]
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // 背景只移动页面位移的一半左右，形成视差。
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = offset * size.width * 0.55f }) {
            when (s) {
                Subject.WENYAN -> InkLandscape(
                    motifsOf(word?.let { (w, i) -> w.exampleFor(i)?.sentence }.orEmpty()),
                    foreground = word?.let { loadIllustration(app.context, it.first.id) } == null,
                )
                Subject.GUSHI -> DriftingPhoto(app.scene(Subject.GUSHI).image)
                Subject.BIOLOGY -> if (bio.image == null) DnaHelix() else DriftingPhoto(bio.image)
            }
        }
        if (s != Subject.WENYAN) Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x8C000000), 0.3f to Color(0x4D000000), 0.5f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0x80000000))
            )
        )
        Column(
            Modifier.fillMaxSize().graphicsLayer {
                translationY = pull
                alpha = 1f - offset.absoluteValue * 0.7f
                translationX = -offset * size.width * 0.08f
            }.padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(60.dp))
            when (s) {
                Subject.WENYAN -> word?.let { (w, i) -> WenyanFeature(w, i) { onOpenCard(w, i) } }
                Subject.GUSHI -> GushiFeature(app, app.scene(Subject.GUSHI), onOpenCard, onOpenPassage)
                Subject.BIOLOGY -> (app.content(Subject.BIOLOGY).byId[bio.cardId] as? Card.Cloze)?.let { BiologyFeature(it) { onOpenCard(it, 0) } }
            }
            // 文言词义插图：铺满屏宽，底边贴着 Learn/Review 卡片。
            if (s == Subject.WENYAN && word != null && loadIllustration(app.context, word.first.id) != null) {
                IllustrationView(word.first.id, Modifier.weight(1f).requiredWidth(LocalConfiguration.current.screenWidthDp.dp).padding(bottom = 8.dp))
            } else Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudyCard("Learn", "新学", overview.newLeft, s.accent(false), Modifier.weight(1f)) { app.startLearn(s) }
                StudyCard("Review", "复习", overview.due, s.accent(false), Modifier.weight(1f)) { app.startReview(s) }
            }
            Spacer(Modifier.height(78.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun WenyanFeature(card: Card.Word, exampleIndex: Int, onClick: () -> Unit) {
    val ex = card.exampleFor(exampleIndex)
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).clickable(onClick = onClick)) {
        Text(card.word, color = Ink, fontFamily = Serif, fontSize = 64.sp)
        Text(ex?.meaning.orEmpty(), color = Ink.copy(alpha = 0.85f), fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp))
        if (ex != null) {
            Text(
                highlight(ex.sentence, card.word, Color(0xFFB8492F)), color = Ink, fontFamily = Serif,
                fontSize = 18.sp, lineHeight = 30.sp, modifier = Modifier.padding(top = 20.dp),
            )
            Text("《${ex.title}》", color = Ink.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun GushiFeature(app: AppState, scene: Scene, onOpenCard: (Card, Int) -> Unit, onOpenPassage: (Scene) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp).clickable {
            // 有考过这句的默写题就打开那道题，和搜索点进去的页面一样；没有就看原文。
            val card = app.content(Subject.GUSHI).cards.firstOrNull { c ->
                c is Card.Dictation && c.answers.flatten().any { it.length >= 4 && scene.line.contains(it) }
            }
            if (card != null) onOpenCard(card, 0) else onOpenPassage(scene)
        }
    ) {
        Text(scene.line, color = Color.White, fontFamily = Serif, fontSize = 26.sp, lineHeight = 42.sp, style = shadow)
        Text(
            "—— ${scene.passage.author}《${scene.passage.title}》", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, style = shadow,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun BiologyFeature(card: Card.Cloze, onClick: () -> Unit) {
    Column(
        Modifier.padding(top = 20.dp).fillMaxWidth(0.62f).clip(RoundedCornerShape(16.dp)).background(Color(0x66000000))
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Text(card.deck.substringAfter(" · "), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Text(
            clozeText(card, revealed = true, hint = false, color = Color(0xFFB6F0CF)), color = Color.White,
            fontSize = 16.sp, lineHeight = 26.sp, maxLines = 9, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** 科目切换：白色（文言页为墨色）药丸随翻页滑动。 */
@Composable
private fun SubjectSwitch(position: Float, chrome: Color, paperness: Float, onSelect: (Int) -> Unit) {
    val chip = 64.dp
    Box(Modifier.clip(RoundedCornerShape(50)).background(lerp(Color(0x33000000), Color(0x14000000), paperness)).padding(3.dp)) {
        Box(
            Modifier.offset(x = chip * position).width(chip).height(32.dp).clip(RoundedCornerShape(50))
                .background(lerp(Color.White.copy(alpha = 0.92f), Ink, paperness))
        )
        Row {
            Subject.entries.forEachIndexed { i, s ->
                val near = (1f - (position - i).absoluteValue).coerceIn(0f, 1f)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.width(chip).height(32.dp).clip(RoundedCornerShape(50)).clickable { onSelect(i) },
                ) {
                    Text(
                        s.short, fontSize = 14.sp, fontWeight = if (near > 0.5f) FontWeight.Medium else FontWeight.Normal,
                        color = lerp(chrome, lerp(Color(0xFF242220), Paper, paperness), near),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavIcon(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun StudyCard(title: String, label: String, count: Int, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.78f))
            .clickable(enabled = count > 0, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(title, color = Color(0xFF242220), fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(if (count > 0) count.toString() else "✓", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(if (count > 0) "  待$label" else "  今日完成", color = Color(0xFF7D7873), fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}
