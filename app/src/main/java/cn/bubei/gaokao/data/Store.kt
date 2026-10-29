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

data class MemoryRow(
    val cardId: String, val stability: Double, val difficulty: Double,
    val lastDay: Long, val dueDay: Long, val reps: Int, val lapses: Int,
) {
    val memory get() = Fsrs.Memory(stability, difficulty)
}

class Store(context: Context) : SQLiteOpenHelper(context, "study.db", null, 1) {
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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

    // ---- 学习记录 ----

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
            .put("memory", table("memory")).put("log", table("log")).put("settings", settings)
            .toString()
    }

    /** 整体替换当前记录。先完整解析再写入，任何一步失败都不改动现有数据。 */
    fun import(text: String) {
        val json = JSONObject(text)
        require(json.optString("app") == "bubei-gaokao") { "不是不背高考的备份文件" }
        val memory = json.getJSONArray("memory")
        val log = json.getJSONArray("log")
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("memory", null, null)
            db.delete("log", null, null)
            for (table in listOf("memory" to memory, "log" to log)) {
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
