package cn.bubei.gaokao.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.bubei.gaokao.data.Card
import cn.bubei.gaokao.data.Notebook
import cn.bubei.gaokao.data.Subject

private val saved = Color(0xFFE0A526)

/** 卡片右上角的 ☆：点开选放进哪个本子。在任何一个本子里就显示实心。 */
@Composable
fun SaveButton(app: AppState, card: Card) {
    val inside = app.isSaved(card)
    IconButton(onClick = { app.saving = card }) {
        Text(if (inside) "★" else "☆", fontSize = 22.sp, color = if (inside) saved else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 选本子：勾上就放进去，取消就拿出来；最下面可以新建本子。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookPicker(app: AppState, card: Card) {
    @Suppress("UNUSED_EXPRESSION") app.version
    val notebooks = app.store.notebooks()
    val inside = app.store.notebooksOf(card.id)
    var naming by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { app.saving = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("放进本子", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
            Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                notebooks.forEach { nb ->
                    val on = nb.id in inside
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            app.store.setInNotebook(nb.id, card.subject, card.id, !on); app.changed()
                        }.padding(horizontal = 12.dp),
                    ) {
                        Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(nb.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("${nb.count}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { naming = true }.padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Text("＋", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, modifier = Modifier.padding(end = 14.dp))
                Text("新建本子", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
            }
        }
    }
    if (naming) NameDialog("新建本子", "") { name ->
        naming = false
        if (name != null) {
            val id = app.store.createNotebook(name)
            app.store.setInNotebook(id, card.subject, card.id, true)
            app.changed()
        }
    }
}

/** 我的本子：本子列表，点进去看里面的卡片。 */
@Composable
fun NotebooksScreen(app: AppState) {
    @Suppress("UNUSED_EXPRESSION") app.version
    val notebooks = app.store.notebooks()
    var naming by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Panel {
            if (notebooks.isEmpty()) Text(
                "还没有本子。学习时点右上角的 ☆，就能把卡片放进本子。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp,
            )
            notebooks.forEachIndexed { i, nb ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NotebookRow(nb) { app.notebook = nb.id }
            }
        }
        TextButton(onClick = { naming = true }, modifier = Modifier.padding(start = 20.dp)) { Text("＋ 新建本子", fontSize = 16.sp) }
    }
    if (naming) NameDialog("新建本子", "") { name ->
        naming = false
        if (name != null) { app.store.createNotebook(name); app.changed() }
    }
}

@Composable
private fun NotebookRow(nb: Notebook, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        ) {
            Text(nb.name.take(1), color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Text(nb.name, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Text("${nb.count} 张  ›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

/** 一个本子里的卡片，可按科目筛选；右上角菜单改名、删除。 */
@Composable
fun NotebookPage(app: AppState, id: Long, onOpen: (Card) -> Unit) {
    BackHandler { app.notebook = null }
    @Suppress("UNUSED_EXPRESSION") app.version
    val nb = app.store.notebooks().firstOrNull { it.id == id }
    var filter by remember { mutableStateOf<Subject?>(null) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val all = app.store.notebookCards(id)
    val cards = all.filter { filter == null || it.first == filter }.mapNotNull { (s, cardId) -> app.content(s).byId[cardId] }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = { app.notebook = null }, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text(nb?.name.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("删除本子", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            (listOf<Subject?>(null) + Subject.entries).forEach { s ->
                Chip("${s?.short ?: "全部"} ${all.count { s == null || it.first == s }}", s == filter) { filter = s }
            }
        }
        if (cards.isEmpty()) Empty("本子里还没有卡片")
        CardList(app, cards, onOpen)
    }
    if (renaming) NameDialog("重命名", nb?.name.orEmpty()) { name ->
        renaming = false
        if (name != null) { app.store.renameNotebook(id, name); app.changed() }
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("删除「${nb?.name.orEmpty()}」？") },
        text = { Text("只删除本子，卡片本身和学习记录不受影响。") },
        confirmButton = {
            TextButton(onClick = {
                deleting = false
                app.notebook = null
                app.store.deleteNotebook(id)
                app.changed()
            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
    )
}

/** 输入本子名字。取消时回调 null。 */
@Composable
private fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it.take(20) }, singleLine = true, placeholder = { Text("比如：易错实词、细胞结构") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("取消") } },
    )
}
