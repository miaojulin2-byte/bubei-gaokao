package cn.bubei.gaokao.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一句原文和它的今译。 */
data class Line(val original: String, val translation: String)

/** 教材文言课文全文。paragraphs 按教材分段，每段若干句。 */
class WenyanText(val id: String, val title: String, val author: String, val volume: String, val paragraphs: List<List<Line>>) {
    val lines: List<Line> = paragraphs.flatten()
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

/** 课文里的一处位置：第 range 句，用于从搜索结果、例句跳到课文。 */
data class TextSpot(val text: WenyanText, val range: IntRange)

object Texts {
    private var all: List<WenyanText>? = null

    @Synchronized
    fun all(context: Context): List<WenyanText> = all ?: JSONObject(
        context.assets.open("wenyan-texts.json").bufferedReader().readText()
    ).getJSONArray("texts").let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WenyanText(
                o.getString("id"), o.getString("t"), o.getString("a"), o.getString("v"),
                o.getJSONArray("p").list { p -> p.list { l -> Line(l.getString(0), l.getString(1)) } },
            )
        }
    }.also { all = it }

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
