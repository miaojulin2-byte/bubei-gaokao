package cn.bubei.gaokao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.bubei.gaokao.data.Card
import cn.bubei.gaokao.data.Passage
import cn.bubei.gaokao.ui.AppState
import cn.bubei.gaokao.ui.BooksScreen
import cn.bubei.gaokao.ui.CardDetail
import cn.bubei.gaokao.ui.GaokaoTheme
import cn.bubei.gaokao.ui.HomeScreen
import cn.bubei.gaokao.ui.MeScreen
import cn.bubei.gaokao.ui.Page
import cn.bubei.gaokao.ui.PassageDetail
import cn.bubei.gaokao.ui.SearchScreen
import cn.bubei.gaokao.ui.StarsScreen
import cn.bubei.gaokao.ui.StudyScreen
import cn.bubei.gaokao.ui.Tab

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
                val session = app.session
                Box(Modifier.fillMaxSize()) {
                    when {
                        session != null -> StudyScreen(app, session)
                        detail != null -> CardDetail(app, detail!!.first, detail!!.second) { detail = null }
                        passage != null -> PassageDetail(passage!!) { passage = null }
                        app.page == Page.SEARCH -> SearchScreen(app) { detail = it to 0 }
                        app.page == Page.STARS -> StarsScreen(app) { detail = it to 0 }
                        else -> Column(Modifier.fillMaxSize()) {
                            // 内容区在底栏之上，首页风景图不会延伸到底栏后面。
                            Box(Modifier.weight(1f)) {
                                when (app.tab) {
                                    Tab.HOME -> HomeScreen(app, { card, ex -> detail = card to ex }, { passage = it.passage })
                                    Tab.BOOKS -> BooksScreen(app)
                                    Tab.ME -> MeScreen(app)
                                }
                            }
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                listOf(
                                    Triple(Tab.HOME, Icons.Default.Home, "首页"),
                                    Triple(Tab.BOOKS, Icons.Default.Menu, "书库"),
                                    Triple(Tab.ME, Icons.Default.Person, "我的"),
                                ).forEach { (tab, icon, label) ->
                                    NavigationBarItem(
                                        selected = app.tab == tab, onClick = { app.tab = tab },
                                        icon = { Icon(icon, contentDescription = label) }, label = { Text(label) },
                                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    )
                                }
                            }
                        }
                    }
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
