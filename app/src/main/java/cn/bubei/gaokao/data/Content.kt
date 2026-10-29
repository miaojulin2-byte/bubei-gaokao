package cn.bubei.gaokao.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** know / dontKnow：学习页两个主按钮的文字，按科目说法不同。 */
enum class Subject(val key: String, val title: String, val unit: String, val defaultNew: Int, val know: String, val dontKnow: String) {
    WENYAN("wenyan", "文言实词", "词", 20, "认识", "不认识"),
    GUSHI("gushi", "古诗文默写", "题", 10, "会写", "写不出"),
    BIOLOGY("biology", "生物", "题", 20, "会", "不会"),
}

data class Deck(val id: String, val title: String, val count: Int)

/** 例句：出处、原句、在该句中的意思。 */
data class Example(val title: String, val sentence: String, val meaning: String)

/** 原文片段：答案句 middle 及其前后各一句。 */
data class Passage(val title: String, val author: String, val before: String, val middle: String, val after: String)

sealed class Card {
    abstract val id: String
    abstract val deck: String
    abstract val subject: Subject

    /** 文言实词：看字和例句，回想词义。 */
    data class Word(
        override val id: String, override val deck: String,
        val word: String, val pos: String, val gloss: String, val examples: List<Example>,
    ) : Card() {
        override val subject get() = Subject.WENYAN
    }

    /** 古诗文理解性默写：读情境题干，写出原句。answers 每项是一组可接受的答案，开放题有多组。 */
    data class Dictation(
        override val id: String, override val deck: String,
        val source: String, val stem: String, val answers: List<List<String>>, val open: Boolean,
        val note: String, val contexts: List<Passage?>,
    ) : Card() {
        override val subject get() = Subject.GUSHI
    }

    /** 生物填空或问答：parts 中 hidden 为待回忆部分。 */
    data class Cloze(
        override val id: String, override val deck: String,
        val noteId: String, val parts: List<Pair<String, Boolean>>, val images: List<String>,
    ) : Card() {
        override val subject get() = Subject.BIOLOGY

        /** 只有“题干 + 答案”两段时按问答题展示，其余按填空展示。 */
        val isQuestion get() = parts.size == 2 && !parts[0].second && parts[1].second
    }
}

/** 首页风景：一张图配三科各一张卡片，图里画的就是卡片写的景。 */
data class Scene(
    val image: String, val line: String, val passage: Passage,
    /** 文言、生物各配一张卡；古诗文直接显示原句。 */
    val cards: Map<Subject, String>, val example: Int,
)

class SubjectContent(val subject: Subject, val decks: List<Deck>, val cards: List<Card>) {
    val byId: Map<String, Card> = cards.associateBy { it.id }
    fun inDeck(deck: String) = cards.filter { it.deck == deck }
}

object Content {
    private val cache = mutableMapOf<Subject, SubjectContent>()
    private var scenes: List<Scene>? = null

    @Synchronized
    fun of(context: Context, subject: Subject): SubjectContent = cache.getOrPut(subject) {
        val json = JSONObject(context.assets.open("${subject.key}.json").bufferedReader().readText())
        val decks = json.getJSONArray("decks").objects().map { Deck(it.getString("id"), it.getString("title"), it.getInt("count")) }
        val cards = json.getJSONArray("cards").objects().map { parse(subject, it) }
        SubjectContent(subject, decks, cards)
    }

    @Synchronized
    fun scenes(context: Context): List<Scene> = scenes ?: JSONArray(
        context.assets.open("scenes.json").bufferedReader().readText()
    ).objects().map {
        Scene(
            it.getString("img"), it.getString("line"),
            Passage(it.getString("title"), it.getString("author"), it.getString("before"), it.getString("line"), it.getString("after")),
            listOf(Subject.WENYAN, Subject.BIOLOGY).associateWith { s -> it.getString(s.key) }, it.getInt("example"),
        )
    }.also { scenes = it }

    private fun parse(subject: Subject, o: JSONObject): Card = when (subject) {
        Subject.WENYAN -> Card.Word(
            o.getString("id"), o.getString("d"), o.getString("w"), o.getString("p"), o.getString("g"),
            o.getJSONArray("x").arrays().map { Example(it.getString(0), it.getString(1), it.getString(2)) },
        )
        Subject.GUSHI -> Card.Dictation(
            o.getString("id"), o.getString("d"), o.getString("src"), o.getString("stem"),
            o.getJSONArray("ans").arrays().map { it.strings() }, o.getBoolean("open"), o.getString("note"),
            o.getJSONArray("ctx").let { ctx ->
                (0 until ctx.length()).map { i ->
                    ctx.optJSONObject(i)?.let { p -> Passage(p.getString("t"), p.getString("a"), p.getString("b"), p.getString("m"), p.getString("f")) }
                }
            },
        )
        Subject.BIOLOGY -> Card.Cloze(
            o.getString("id"), o.getString("d"), o.getString("n"),
            o.getJSONArray("parts").arrays().map { it.getString(0) to (it.getInt(1) == 1) },
            o.getJSONArray("img").strings(),
        )
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.arrays() = (0 until length()).map { getJSONArray(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
