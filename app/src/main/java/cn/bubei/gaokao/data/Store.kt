package cn.bubei.gaokao.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** 学习日在凌晨 4 点切换，熬夜学习仍算前一天。 */
fun today(): Long = LocalDateTime.now().minusHours(4).toLocalDate().toEpochDay()

data class Notebook(val id: Long, val name: String, val count: Int)

data class MemoryRow(
    val cardId: String, val stability: Double, val difficulty: Double,
    val lastDay: Long, val dueDay: Long, val reps: Int, val lapses: Int,
) {
    val memory get() = Fsrs.Memory(stability, difficulty)
}

class Store(context: Context) : SQLiteOpenHelper(context, "study.db", null, 3) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE memory(card_id TEXT PRIMARY KEY, subject TEXT NOT NULL, s REAL NOT NULL, d REAL NOT NULL,
               last_day INTEGER NOT NULL, due_day INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX memory_due ON memory(subject, due_day)")
        db.execSQL(
            """CREATE TABLE log(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, day INTEGER NOT NULL,
               subject TEXT NOT NULL, card_id TEXT NOT NULL, grade INTEGER NOT NULL, is_new INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX log_day ON log(day, subject)")
        createNotebooks(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            createNotebooks(db)
            // v2 的收藏放进一个本子。
            if (oldVersion == 2) {
                val stars = db.rawQuery("SELECT card_id, subject, ts FROM star", null).use { c ->
                    buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getLong(2))) }
                }
                if (stars.isNotEmpty()) {
                    val id = db.insert("notebook", null, ContentValues().apply { put("name", "我的本子"); put("ts", System.currentTimeMillis()) })
                    for ((card, subject, ts) in stars) db.insert("notebook_card", null, ContentValues().apply {
                        put("notebook_id", id); put("card_id", card); put("subject", subject); put("ts", ts)
                    })
                }
                db.execSQL("DROP TABLE star")
            }
        }
    }

    private fun createNotebooks(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE notebook(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, ts INTEGER NOT NULL)")
        db.execSQL(
            """CREATE TABLE notebook_card(notebook_id INTEGER NOT NULL, card_id TEXT NOT NULL, subject TEXT NOT NULL,
               ts INTEGER NOT NULL, PRIMARY KEY(notebook_id, card_id))"""
        )
    }

    // ---- 设置 ----

    var subject: Subject
        get() = Subject.entries.firstOrNull { it.key == prefs.getString("subject", null) } ?: Subject.WENYAN
        set(value) = prefs.edit().putString("subject", value.key).apply()

    fun newPerDay(s: Subject) = prefs.getInt("new_${s.key}", s.defaultNew)
    fun setNewPerDay(s: Subject, n: Int) = prefs.edit().putInt("new_${s.key}", n).apply()

    fun retention(s: Subject) = prefs.getFloat("retention_${s.key}", 0.9f).toDouble()
    fun setRetention(s: Subject, r: Double) = prefs.edit().putFloat("retention_${s.key}", r.toFloat()).apply()

    /** 新学范围：选中的分册或章节。为空表示还没选过，按全部处理。 */
    fun decks(s: Subject): Set<String> = prefs.getStringSet("decks_${s.key}", emptySet())!!.toSet()
    fun setDecks(s: Subject, decks: Set<String>) = prefs.edit().putStringSet("decks_${s.key}", decks).apply()

    /** 高考年份。默认 2028：2026 年秋季入读高二。 */
    var gaokaoYear: Int
        get() = prefs.getInt("gaokao_year", 2028)
        set(value) = prefs.edit().putInt("gaokao_year", value).apply()

    // ---- 本子 ----

    /** 本子按新建顺序排列。 */
    fun notebooks(): List<Notebook> = readableDatabase.rawQuery(
        "SELECT n.id, n.name, COUNT(c.card_id) FROM notebook n LEFT JOIN notebook_card c ON c.notebook_id = n.id GROUP BY n.id ORDER BY n.ts",
        null,
    ).use { c -> buildList { while (c.moveToNext()) add(Notebook(c.getLong(0), c.getString(1), c.getInt(2))) } }

    fun createNotebook(name: String): Long = writableDatabase.insert("notebook", null, ContentValues().apply {
        put("name", name); put("ts", System.currentTimeMillis())
    })

    fun renameNotebook(id: Long, name: String) {
        writableDatabase.update("notebook", ContentValues().apply { put("name", name) }, "id=?", arrayOf(id.toString()))
    }

    fun deleteNotebook(id: Long) {
        writableDatabase.delete("notebook_card", "notebook_id=?", arrayOf(id.toString()))
        writableDatabase.delete("notebook", "id=?", arrayOf(id.toString()))
    }

    /** 本子里的卡片，最近放进去的在前。 */
    fun notebookCards(id: Long): List<Pair<Subject, String>> = readableDatabase.rawQuery(
        "SELECT subject, card_id FROM notebook_card WHERE notebook_id=? ORDER BY ts DESC", arrayOf(id.toString()),
    ).use { c ->
        buildList { while (c.moveToNext()) Subject.entries.firstOrNull { it.key == c.getString(0) }?.let { add(it to c.getString(1)) } }
    }

    /** 这张卡在哪些本子里。 */
    fun notebooksOf(cardId: String): Set<Long> = readableDatabase.rawQuery(
        "SELECT notebook_id FROM notebook_card WHERE card_id=?", arrayOf(cardId),
    ).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    fun setInNotebook(id: Long, s: Subject, cardId: String, inside: Boolean) {
        if (inside) writableDatabase.insertWithOnConflict("notebook_card", null, ContentValues().apply {
            put("notebook_id", id); put("card_id", cardId); put("subject", s.key); put("ts", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE)
        else writableDatabase.delete("notebook_card", "notebook_id=? AND card_id=?", arrayOf(id.toString(), cardId))
    }

    // ---- 学习记录 ----

    /** 清空学习记录（s 为空时清空三科）。本子和设置保留。 */
    fun reset(s: Subject?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (table in listOf("memory", "log")) {
                if (s == null) db.delete(table, null, null) else db.delete(table, "subject=?", arrayOf(s.key))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun memories(s: Subject): Map<String, MemoryRow> = readableDatabase.rawQuery(
        "SELECT card_id, s, d, last_day, due_day, reps, lapses FROM memory WHERE subject=?", arrayOf(s.key)
    ).use { c ->
        buildMap {
            while (c.moveToNext()) put(
                c.getString(0),
                MemoryRow(c.getString(0), c.getDouble(1), c.getDouble(2), c.getLong(3), c.getLong(4), c.getInt(5), c.getInt(6)),
            )
        }
    }

    fun newLearnedOn(s: Subject, day: Long): Int = count(
        "SELECT COUNT(*) FROM log WHERE subject=? AND day=? AND is_new=1", s.key, day.toString()
    )

    /**
     * 记录一次作答。每张卡每天只有第一次作答进入 FSRS；
     * 同一天里忘记后再练的作答只记日志，不重复改动记忆状态。
     */
    fun answer(s: Subject, cardId: String, grade: Int, day: Long = today()) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val old = db.rawQuery(
                "SELECT card_id, s, d, last_day, due_day, reps, lapses FROM memory WHERE card_id=?", arrayOf(cardId)
            ).use { c ->
                if (c.moveToFirst()) MemoryRow(c.getString(0), c.getDouble(1), c.getDouble(2), c.getLong(3), c.getLong(4), c.getInt(5), c.getInt(6)) else null
            }
            if (old == null || old.lastDay < day) {
                val elapsed = if (old == null) 0.0 else (day - old.lastDay).toDouble()
                val next = Fsrs.next(old?.memory, elapsed, grade)
                val interval = Fsrs.interval(next, retention(s))
                db.insertWithOnConflict("memory", null, ContentValues().apply {
                    put("card_id", cardId); put("subject", s.key)
                    put("s", next.stability); put("d", next.difficulty)
                    put("last_day", day); put("due_day", day + interval)
                    put("reps", (old?.reps ?: 0) + 1)
                    put("lapses", (old?.lapses ?: 0) + if (old != null && grade == 1) 1 else 0)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.insert("log", null, ContentValues().apply {
                put("ts", System.currentTimeMillis()); put("day", day); put("subject", s.key)
                put("card_id", cardId); put("grade", grade); put("is_new", if (old == null) 1 else 0)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 预览：按某个评分作答后多少天再见。 */
    fun previewInterval(s: Subject, row: MemoryRow?, grade: Int, day: Long = today()): Int {
        if (row != null && row.lastDay >= day) return 0
        val next = Fsrs.next(row?.memory, if (row == null) 0.0 else (day - row.lastDay).toDouble(), grade)
        return Fsrs.interval(next, retention(s))
    }

    /** 每天的作答次数（所有科目），用于日历和连续天数。 */
    fun activity(): Map<Long, Int> = readableDatabase.rawQuery("SELECT day, COUNT(*) FROM log GROUP BY day", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) }
    }

    fun answersOn(day: Long): Int = count("SELECT COUNT(*) FROM log WHERE day=?", day.toString())

    private fun count(sql: String, vararg args: String) = readableDatabase.rawQuery(sql, args).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

    // ---- 备份 ----

    fun export(): String {
        val db = readableDatabase
        fun table(name: String) = db.rawQuery("SELECT * FROM $name", null).use { c ->
            JSONArray().apply {
                while (c.moveToNext()) put(JSONObject().apply {
                    for (i in 0 until c.columnCount) when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> put(c.getColumnName(i), c.getLong(i))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> put(c.getColumnName(i), c.getDouble(i))
                        else -> put(c.getColumnName(i), c.getString(i))
                    }
                })
            }
        }
        val settings = JSONObject()
        for ((k, v) in prefs.all) settings.put(k, if (v is Set<*>) JSONArray(v.toList()) else v)
        return JSONObject()
            .put("app", "bubei-gaokao").put("version", 1)
            .put("memory", table("memory")).put("log", table("log")).put("notebook", table("notebook")).put("notebook_card", table("notebook_card")).put("settings", settings)
            .toString()
    }

    /** 整体替换当前记录。先完整解析再写入，任何一步失败都不改动现有数据。 */
    fun import(text: String) {
        val json = JSONObject(text)
        require(json.optString("app") == "bubei-gaokao") { "不是不背高考的备份文件" }
        val memory = json.getJSONArray("memory")
        val log = json.getJSONArray("log")
        val notebook = json.optJSONArray("notebook") ?: JSONArray()
        val notebookCard = json.optJSONArray("notebook_card") ?: JSONArray()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("memory", null, null)
            db.delete("log", null, null)
            db.delete("notebook_card", null, null)
            db.delete("notebook", null, null)
            for (table in listOf("memory" to memory, "log" to log, "notebook" to notebook, "notebook_card" to notebookCard)) {
                for (i in 0 until table.second.length()) {
                    val row = table.second.getJSONObject(i)
                    db.insertOrThrow(table.first, null, ContentValues().apply {
                        for (key in row.keys()) when (val v = row.get(key)) {
                            is Int -> put(key, v.toLong())
                            is Long -> put(key, v)
                            is Number -> put(key, v.toDouble())
                            else -> put(key, v.toString())
                        }
                    })
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val settings = json.optJSONObject("settings") ?: return
        prefs.edit().apply {
            clear()
            for (key in settings.keys()) when (val v = settings.get(key)) {
                is JSONArray -> putStringSet(key, (0 until v.length()).map { v.getString(it) }.toSet())
                is Boolean -> putBoolean(key, v)
                is Int -> if (key.startsWith("retention_")) putFloat(key, v.toFloat()) else putInt(key, v)
                is Double -> putFloat(key, v.toFloat())
                else -> putString(key, v.toString())
            }
        }.apply()
    }
}
