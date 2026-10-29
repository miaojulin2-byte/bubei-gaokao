package cn.bubei.gaokao

import cn.bubei.gaokao.data.Fsrs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** 数值对照来自固定版本的上游 ts-fsrs 实际运行结果，不是本移植自己生成的。 */
class FsrsTest {
    private fun close(actual: Double, expected: Double, label: String) {
        if (!actual.isFinite() || abs(actual - expected) > max(1e-7, abs(expected) * 1e-11)) {
            throw AssertionError("$label: $actual != $expected")
        }
    }

    @Test
    fun matchesUpstreamVectors() {
        val rows = javaClass.classLoader!!.getResource("fsrs6-golden.tsv")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        rows.forEachIndexed { n, row ->
            val v = row.split('\t')
            val s = v[0].toDouble()
            val d = v[1].toDouble()
            val elapsed = v[2].toDouble()
            val grade = v[3].toInt()
            val retention = v[4].toDouble()
            val old = if (s == 0.0) null else Fsrs.Memory(s, d)
            val next = Fsrs.next(old, elapsed, grade)
            close(next.stability, v[5].toDouble(), "stability $n")
            close(next.difficulty, v[6].toDouble(), "difficulty $n")
            close(if (old == null) 1.0 else Fsrs.retrievability(old, elapsed), v[7].toDouble(), "retrievability $n")
            assertEquals("interval $n", v[8].toInt(), Fsrs.interval(next, retention))
        }
        assertEquals(4592, rows.size)
    }

    @Test
    fun rejectsInvalidInput() {
        for (grade in listOf(0, 5)) assertThrows(IllegalArgumentException::class.java) { Fsrs.next(null, 0.0, grade) }
        for (r in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1.1)) {
            assertThrows(IllegalArgumentException::class.java) { Fsrs.interval(Fsrs.Memory(2.0, 5.0), r) }
        }
    }
}
