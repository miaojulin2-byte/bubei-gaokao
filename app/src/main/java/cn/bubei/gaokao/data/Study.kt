package cn.bubei.gaokao.data

import android.os.SystemClock
import java.time.LocalDate

/** 距 year 年高考第一天（6 月 7 日）还有几天；已过则为 0。 */
fun daysToGaokao(day: Long, year: Int): Long = maxOf(LocalDate.of(year, 6, 7).toEpochDay() - day, 0)

data class Overview(
    val newLeft: Int, val newPerDay: Int, val newDoneToday: Int,
    val due: Int, val learned: Int, val total: Int, val longTerm: Int,
    /** 新学范围内还没学过的卡片数，用来估算剩余天数。 */
    val unlearnedInScope: Int,
)

data class DeckProgress(val deck: Deck, val learned: Int, val selected: Boolean)

/** 长期记忆：稳定度达到 30 天。只说明记忆模型的估计，不代表掌握。 */
private const val LONG_TERM_DAYS = 30.0

class Planner(private val store: Store, private val content: SubjectContent) {
    private val subject = content.subject

    private fun scope(): Set<String> = store.decks(subject) ?: content.decks.map { it.id }.toSet()

    fun overview(day: Long = today()): Overview {
        val memories = store.memories(subject)
        val doneToday = store.newLearnedOn(subject, day)
        val perDay = store.newPerDay(subject)
        val scope = scope()
        val unlearned = content.cards.count { it.deck in scope && it.id !in memories }
        return Overview(
            newLeft = minOf(maxOf(perDay - doneToday, 0), unlearned),
            newPerDay = perDay, newDoneToday = doneToday,
            due = reviewQueue(day).size,
            learned = memories.size, total = content.cards.size,
            longTerm = memories.values.count { it.stability >= LONG_TERM_DAYS },
            unlearnedInScope = unlearned,
        )
    }

    fun decks(): List<DeckProgress> {
        val memories = store.memories(subject)
        val learned = content.cards.groupingBy { it.deck }.eachCount().mapValues { (deck, _) ->
            content.cards.count { it.deck == deck && it.id in memories }
        }
        val scope = scope()
        return content.decks.map { DeckProgress(it, learned[it.id] ?: 0, it.id in scope) }
    }

    /** 今天的新卡：按教材顺序取；生物同一条笔记拆出的几张填空当天只取一张。 */
    fun newQueue(day: Long = today()): List<Card> {
        val memories = store.memories(subject)
        val limit = overview(day).newLeft
        val scope = scope()
        val takenNotes = mutableSetOf<String>()
        content.cards.forEach { card ->
            val row = memories[card.id]
            if (card is Card.Cloze && row != null && row.lastDay == day) takenNotes += card.noteId
        }
        val candidates = content.cards.filter { card ->
            card.deck in scope && card.id !in memories && !(card is Card.Cloze && !takenNotes.add(card.noteId))
        }
        // 真题、辨析等选择题每天约占三成，与教材卡交替出现，不让某一类连着占满好几天。
        val (choices, others) = candidates.partition { it is Card.Choice }
        val choiceCount = minOf(choices.size, if (others.isEmpty()) limit else (limit * 3 + 9) / 10)
        val picked = choices.take(choiceCount).toMutableList()
        val rest = others.take(limit - picked.size).toMutableList()
        picked += choices.drop(choiceCount).take(limit - picked.size - rest.size)
        val out = mutableListOf<Card>()
        while (picked.isNotEmpty() || rest.isNotEmpty()) {
            if (rest.isNotEmpty()) out += rest.removeAt(0)
            if (rest.isNotEmpty()) out += rest.removeAt(0)
            if (picked.isNotEmpty()) out += picked.removeAt(0)
        }
        return out
    }

    /**
     * 到期复习：逾期越久越靠前。生物同一条笔记拆出的几张填空一天只复习一张，
     * 免得前一张刚把后一张的答案露出来；其余的顺延到明天。
     */
    fun reviewQueue(day: Long = today()): List<Card> {
        val memories = store.memories(subject)
        val notes = memories.values.filter { it.lastDay == day }
            .mapNotNull { (content.byId[it.cardId] as? Card.Cloze)?.noteId }.toMutableSet()
        return memories.values
            .filter { it.dueDay <= day && it.lastDay < day }
            .sortedWith(compareBy({ it.dueDay }, { it.stability }))
            .mapNotNull { content.byId[it.cardId] }
            .filter { it !is Card.Cloze || notes.add(it.noteId) }
    }
}

/**
 * 一组学习。模仿不背单词：先看题，选认识 / 提示一下 / 不认识，再看答案；
 * 看完答案还能说“记错了”。忘记的卡片在本组稍后再出现，直到答对为止。
 */
class Session(private val store: Store, val subject: Subject, cards: List<Card>) {
    enum class Stage { FRONT, HINT, BACK }

    private val queue = ArrayDeque(cards)
    val total = cards.size
    var finished = 0; private set
    var forgotten = 0; private set
    val current: Card? get() = queue.firstOrNull()
    var stage = Stage.FRONT; private set

    /** 揭晓答案时预定的评分；“下一个”按它提交。 */
    var pendingGrade = 3; private set
    private val memories = store.memories(subject).toMutableMap()

    /** 选择题选了第几项；答对按“良好”、答错按“忘记”提交，不用自评。 */
    var picked: Int? = null; private set

    /** 这张卡从出现到作答用的毫秒数，和是否慢到按“模糊”算。 */
    private var shownAt = SystemClock.elapsedRealtime()
    private var answerMs: Long? = null
    var slow = false; private set

    /** 答对了先看快慢：想得久说明记忆已经松了，按“困难”算，间隔短一些。 */
    private fun recalled() {
        val ms = SystemClock.elapsedRealtime() - shownAt
        answerMs = ms
        slow = ms > current?.let(::expectedMs) ?: Long.MAX_VALUE
        pendingGrade = if (slow) 2 else 3
        stage = Stage.BACK
    }

    fun choose(option: Int) {
        val card = current as? Card.Choice ?: return
        picked = option
        if (option == card.answer) recalled() else { answerMs = SystemClock.elapsedRealtime() - shownAt; pendingGrade = 1; stage = Stage.BACK }
    }

    fun know() = recalled()
    fun hint() { stage = Stage.HINT }
    fun knowAfterHint() { answerMs = SystemClock.elapsedRealtime() - shownAt; pendingGrade = 2; stage = Stage.BACK }
    fun dontKnow() { answerMs = SystemClock.elapsedRealtime() - shownAt; pendingGrade = 1; stage = Stage.BACK }

    /** 下一次见面还要几天；今天忘了要重练的返回 0。 */
    fun nextInterval(grade: Int): Int = current?.let { store.previewInterval(memories[it.id], grade) } ?: 0

    fun memoryOf(card: Card) = memories[card.id]

    fun commit(grade: Int = pendingGrade) {
        val card = queue.removeFirstOrNull() ?: return
        store.answer(subject, card.id, grade, answerMs)
        memories[card.id] = store.memories(subject)[card.id]!!
        if (grade == 1) {
            forgotten++
            queue.add(minOf(3, queue.size), card)
        } else {
            finished++
        }
        stage = Stage.FRONT
        pendingGrade = 3
        picked = null
        slow = false
        answerMs = null
        shownAt = SystemClock.elapsedRealtime()
    }
}

/** 认出这张卡大概要多久：题面越长给的时间越多，超过就算“想了很久”。 */
private fun expectedMs(card: Card): Long = when (card) {
    is Card.Word -> 6_000L
    is Card.Dictation -> 8_000L + 60L * card.stem.length
    is Card.Choice -> 15_000L + 80L * (card.stem.length + card.options.sumOf { it.length })
    is Card.Cloze -> 6_000L + 70L * card.parts.sumOf { it.first.length }
}
