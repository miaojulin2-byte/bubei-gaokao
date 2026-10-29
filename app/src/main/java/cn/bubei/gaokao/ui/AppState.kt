package cn.bubei.gaokao.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.bubei.gaokao.data.Content
import cn.bubei.gaokao.data.Planner
import cn.bubei.gaokao.data.Session
import cn.bubei.gaokao.data.Store
import cn.bubei.gaokao.data.Subject
import cn.bubei.gaokao.data.today

enum class Tab { HOME, BOOKS, ME }

/** 界面状态。数据都在 Store 里，这里只保存当前在看哪一页，以及让界面刷新的版本号。 */
class AppState(val context: Context) {
    val store = Store(context)
    var subject by mutableStateOf(store.subject); private set
    var tab by mutableStateOf(Tab.HOME)
    var session by mutableStateOf<Session?>(null); private set

    /** 学习进度变化后加一，让依赖数据的界面重新读取。 */
    var version by mutableIntStateOf(0); private set

    /** 会话内每次作答后加一，让学习页刷新。 */
    var step by mutableIntStateOf(0); private set

    val day get() = today()
    fun content(s: Subject = subject) = Content.of(context, s)
    fun planner(s: Subject = subject) = Planner(store, content(s))
    fun scene() = Content.scenes(context).let { it[(day % it.size).toInt()] }

    fun selectSubject(s: Subject) {
        subject = s
        store.subject = s
    }

    fun startLearn() = start(planner().newQueue())
    fun startReview() = start(planner().reviewQueue())

    private fun start(cards: List<cn.bubei.gaokao.data.Card>) {
        if (cards.isNotEmpty()) session = Session(store, subject, cards)
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
