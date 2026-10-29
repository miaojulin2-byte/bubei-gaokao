package cn.bubei.gaokao.data

import java.time.LocalDate

/** 高考第一天。过了今年的就看明年。 */
fun daysToGaokao(day: Long = today()): Long {
    val date = LocalDate.ofEpochDay(day)
    var exam = LocalDate.of(date.year, 6, 7)
    if (date.isAfter(exam)) exam = exam.plusYears(1)
    return exam.toEpochDay() - day
}

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

    private fun scope(): Set<String> = store.decks(subject).ifEmpty { content.decks.map { it.id }.toSet() }

    fun overview(day: Long = today()): Overview {
        val memories = store.memories(subject)
        val doneToday = store.newLearnedOn(subject, day)
        val perDay = store.newPerDay(subject)
        val scope = scope()
        val unlearned = content.cards.count { it.deck in scope && it.id !in memories }
        return Overview(
            newLeft = minOf(maxOf(perDay - doneToday, 0), unlearned),
            newPerDay = perDay, newDoneToday = doneToday,
            due = memories.values.count { it.dueDay <= day && it.lastDay < day },
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
        val out = mutableListOf<Card>()
        for (card in content.cards) {
            if (out.size >= limit) break
            if (card.deck !in scope || card.id in memories) continue
            if (card is Card.Cloze && !takenNotes.add(card.noteId)) continue
            out += card
        }
        return out
    }

    /** 到期复习：逾期越久越靠前。 */
    fun reviewQueue(day: Long = today()): List<Card> = store.memories(subject).values
        .filter { it.dueDay <= day && it.lastDay < day }
        .sortedWith(compareBy({ it.dueDay }, { it.stability }))
        .mapNotNull { content.byId[it.cardId] }
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

    fun know() { pendingGrade = 3; stage = Stage.BACK }
    fun hint() { stage = Stage.HINT }
    fun knowAfterHint() { pendingGrade = 2; stage = Stage.BACK }
    fun dontKnow() { pendingGrade = 1; stage = Stage.BACK }

    /** 下一次见面还要几天；同一天再练的卡片返回 0。 */
    fun nextInterval(grade: Int): Int = current?.let { store.previewInterval(subject, memories[it.id], grade) } ?: 0

    fun memoryOf(card: Card) = memories[card.id]

    fun commit(grade: Int = pendingGrade) {
        val card = queue.removeFirstOrNull() ?: return
        store.answer(subject, card.id, grade)
        memories[card.id] = store.memories(subject)[card.id]!!
        if (grade == 1) {
            forgotten++
            queue.add(minOf(3, queue.size), card)
        } else {
            finished++
        }
        stage = Stage.FRONT
        pendingGrade = 3
    }
}
