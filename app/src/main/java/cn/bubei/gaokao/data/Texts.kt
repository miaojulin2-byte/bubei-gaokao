package cn.bubei.gaokao.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一句原文和它的今译。 */
data class Line(val original: String, val translation: String)

/** 教材文言课文全文。paragraphs 按教材分段，每段若干句。 */
class WenyanText(val id: String, val title: String, val author: String, val volume: String, val paragraphs: List<List<Line>>) {
    val lines: List<Line> = paragraphs.flatten()
    /** 文言课文有逐句今译；古诗文全文没有。 */
    val translated = lines.any { it.translation.isNotEmpty() }
    private val plain = lines.map { hanzi(it.original) }
    private val joined = plain.joinToString("")
    private val ends = plain.runningFold(0) { at, s -> at + s.length }.drop(1)

    /** 引文在第几句到第几句（含）；引文可以只是一句的一部分，也可以跨几句。找不到返回 null。 */
    fun locate(quote: String): IntRange? {
        val q = hanzi(quote)
        if (q.isEmpty()) return null
        val at = joined.indexOf(q)
        if (at < 0) return null
        val first = ends.indexOfFirst { it > at }
        val last = ends.indexOfFirst { it >= at + q.length }
        return first..last
    }
}

/** 课文里的一处位置：第 range 句，用于从搜索结果、例句跳到课文。also 是同一篇里要一起标出的其他几句。 */
data class TextSpot(val text: WenyanText, val range: IntRange, val also: List<IntRange> = emptyList()) {
    /** 要标出的所有句子。 */
    val marked: Set<Int> get() = (listOf(range) + also).flatMap { it.toList() }.toSet()
}

object Texts {
    private var all: List<WenyanText>? = null
    private var poems: List<WenyanText>? = null

    /** 五册教材文言课文，带逐句今译。 */
    @Synchronized
    fun all(context: Context): List<WenyanText> = all ?: load(context, "wenyan-texts.json").also { all = it }

    /** 古诗文默写题出处的全文。 */
    @Synchronized
    fun poems(context: Context): List<WenyanText> = poems ?: load(context, "gushi-texts.json").also { poems = it }

    private fun load(context: Context, file: String) = JSONObject(
        context.assets.open(file).bufferedReader().readText()
    ).getJSONArray("texts").let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WenyanText(
                o.getString("id"), o.getString("t"), o.getString("a"), o.getString("v"),
                o.getJSONArray("p").list { p -> p.list { l -> Line(l.getString(0), l.getString(1)) } },
            )
        }
    }

    /** 默写题答案在原文哪里。出处是文言课文的用带译文的课文，否则用古诗文全文。 */
    fun locate(context: Context, passage: Passage): TextSpot? = locate(context, passage.title, passage.middle)
        ?: poems(context).firstOrNull { it.title == passage.title }?.let { t -> t.locate(passage.middle)?.let { TextSpot(t, it) } }

    /**
     * 选择题题干里引的课文句子：同一行里有《篇名》和引号里的句子，或“①句子（《篇名》）”。
     * 只在篇名对得上的课文里找，题干引的考试原文、课外篇目都跳过。
     */
    fun quotes(context: Context, stem: String): List<TextSpot> = stem.lines().flatMap { line ->
        val titles = Regex("《([^》]+)》").findAll(line).map { it.groupValues[1] }.toList()
        val quoted = Regex("“([^”]+)”").findAll(line).map { it.groupValues[1] } +
            listOfNotNull(Regex("^[①-⑳](.+?)(（《|$)").find(line)?.groupValues?.get(1))
        quoted.mapNotNull { q ->
            if (hanzi(q).length < 2) return@mapNotNull null
            all(context).filter { t -> titles.any { t.title.contains(it) || it.contains(t.title) } }
                .firstNotNullOfOrNull { t -> t.locate(q)?.let { TextSpot(t, it) } }
        }.toList()
    }.distinct()

    /** 例句在哪篇课文的哪几句。先按篇名找，篇名对不上（如节选标题不同）再全库找。 */
    fun locate(context: Context, title: String, sentence: String): TextSpot? {
        val texts = all(context)
        val named = texts.filter { it.title.contains(title) || title.contains(it.title) }
        return (named + (texts - named.toSet())).firstNotNullOfOrNull { t -> t.locate(sentence)?.let { TextSpot(t, it) } }
    }

    private inline fun <T> JSONArray.list(f: (JSONArray) -> T) = (0 until length()).map { f(getJSONArray(it)) }
}

/** 只留汉字，用来比对标点写法不同的同一句话。 */
fun hanzi(s: String) = s.filter { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
