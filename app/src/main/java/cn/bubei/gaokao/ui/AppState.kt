package cn.bubei.gaokao.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.bubei.gaokao.data.Card
import cn.bubei.gaokao.data.Content
import cn.bubei.gaokao.data.Planner
import cn.bubei.gaokao.data.Session
import cn.bubei.gaokao.data.Store
import cn.bubei.gaokao.data.Subject
import cn.bubei.gaokao.data.TextSpot
import cn.bubei.gaokao.data.daysToGaokao
import cn.bubei.gaokao.data.today

enum class Tab { HOME, BOOKS, ME }

/** 盖在主界面上的整页：搜索、我的本子、学习日历。 */
enum class Page { SEARCH, NOTEBOOKS, CALENDAR }

/** 界面状态。数据都在 Store 里，这里只保存当前在看哪一页，以及让界面刷新的版本号。 */
class AppState(val context: Context) {
    val store = Store(context)
    var subject by mutableStateOf(store.subject); private set
    var tab by mutableStateOf(Tab.HOME)
    var session by mutableStateOf<Session?>(null); private set
    var page by mutableStateOf<Page?>(null)

    /** 搜索词放在这里，从详情返回时还在。 */
    var searchQuery by mutableStateOf("")

    /** 学习进度变化后加一，让依赖数据的界面重新读取。 */
    var version by mutableIntStateOf(0); private set

    /** 会话内每次作答后加一，让学习页刷新。 */
    var step by mutableIntStateOf(0); private set

    val day get() = today()
    fun content(s: Subject = subject) = Content.of(context, s)
    fun planner(s: Subject = subject) = Planner(store, content(s))
    fun daysToExam() = daysToGaokao(day, store.gaokaoYear)
    /** 卡片是否在任何一个本子里。读 version 让界面在本子变化后刷新。 */
    fun isSaved(card: Card): Boolean = version >= 0 && store.notebooksOf(card.id).isNotEmpty()

    /** 正在选“放进哪个本子”的卡片。 */
    var saving by mutableStateOf<Card?>(null)

    /** 正在读的课文（从搜索或例句进来）。 */
    var reading by mutableStateOf<TextSpot?>(null)

    /** 正在看的本子。 */
    var notebook by mutableStateOf<Long?>(null)
    /** 今天某科的首页风景。三科错开取图，同一天三科的图各不相同，每天轮换。 */
    fun scene(s: Subject = subject) = Content.scenes(context).let { scenes ->
        val offset = when (s) { Subject.WENYAN -> 0; Subject.GUSHI -> 3; Subject.BIOLOGY -> 5 }
        scenes[((day + offset) % scenes.size).toInt()]
    }

    fun selectSubject(s: Subject) {
        subject = s
        store.subject = s
    }

    fun startLearn(s: Subject = subject) = start(s, planner(s).newQueue())
    fun startReview(s: Subject = subject) = start(s, planner(s).reviewQueue())

    private fun start(s: Subject, cards: List<Card>) {
        if (cards.isNotEmpty()) session = Session(store, s, cards)
    }

    fun act(block: Session.() -> Unit) {
        session?.block()
        step++
    }

    fun closeSession() {
        session = null
        changed()
    }

    fun changed() { version++ }
}
