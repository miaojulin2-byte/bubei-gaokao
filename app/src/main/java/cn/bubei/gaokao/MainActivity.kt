package cn.bubei.gaokao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.bubei.gaokao.data.Card
import cn.bubei.gaokao.data.Passage
import cn.bubei.gaokao.ui.AppState
import cn.bubei.gaokao.ui.BookPage
import cn.bubei.gaokao.ui.BooksScreen
import cn.bubei.gaokao.ui.CalendarPage
import cn.bubei.gaokao.ui.CardDetail
import cn.bubei.gaokao.ui.Edge
import cn.bubei.gaokao.ui.GaokaoTheme
import cn.bubei.gaokao.ui.HomeScreen
import cn.bubei.gaokao.ui.MeScreen
import cn.bubei.gaokao.ui.Overlay
import cn.bubei.gaokao.ui.Page
import cn.bubei.gaokao.ui.PassageDetail
import cn.bubei.gaokao.ui.SearchScreen
import cn.bubei.gaokao.ui.Sheet
import cn.bubei.gaokao.ui.NotebookPage
import cn.bubei.gaokao.ui.NotebookPicker
import cn.bubei.gaokao.ui.NotebooksScreen
import cn.bubei.gaokao.ui.StudyScreen
import cn.bubei.gaokao.ui.Tab
import cn.bubei.gaokao.ui.TextScreen

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app = AppState(applicationContext)
        setContent {
            GaokaoTheme(app.subject) {
                var detail by remember { mutableStateOf<Pair<Card, Int>?>(null) }
                var passage by remember { mutableStateOf<Passage?>(null) }
                // 和不背单词一样：首页是底，书库、本子、我的、学习都从底部滑上来盖住它，下拉或返回收起。
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(app, { card, ex -> detail = card to ex }, { passage = it.passage })
                    Sheet(app.tab == Tab.BOOKS, "书库", onClose = { app.tab = Tab.HOME }) { BooksScreen(app) }
                    Sheet(app.tab == Tab.ME, "我的", onClose = { app.tab = Tab.HOME }) { MeScreen(app) }
                    Sheet(app.page == Page.NOTEBOOKS, "我的本子", onClose = { app.page = null }) { NotebooksScreen(app) }
                    Overlay(app.notebook, from = Edge.END) { NotebookPage(app, it) { card -> detail = card to 0 } }
                    Overlay(app.book, from = Edge.END) { BookPage(app, it) { card -> detail = card to 0 } }
                    Overlay(if (app.page == Page.CALENDAR) Unit else null, from = Edge.END) { CalendarPage(app) }
                    Overlay(app.session) { StudyScreen(app, it) }
                    // 搜索在学习页之上，学习、复习中途也能搜；从搜索点开的卡片和课文再盖在搜索上。
                    Overlay(if (app.page == Page.SEARCH) Unit else null, from = Edge.TOP) {
                        SearchScreen(app, onOpenText = { app.reading = it }) { detail = it to 0 }
                    }
                    Overlay(detail) { (card, ex) -> CardDetail(app, card, ex) { detail = null } }
                    Overlay(app.reading, from = Edge.END) { TextScreen(app, it) { app.reading = null } }
                    Overlay(passage) { PassageDetail(it) { passage = null } }
                    app.saving?.let { NotebookPicker(app, it) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 跨过凌晨 4 点回到应用时刷新今日数字。
        if (::app.isInitialized) app.changed()
    }
}
