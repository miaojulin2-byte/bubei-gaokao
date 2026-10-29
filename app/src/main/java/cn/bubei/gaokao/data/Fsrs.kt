package cn.bubei.gaokao.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * FSRS-6 记忆模型，移植自 ts-fsrs（MIT，提交 28410b2757ca）。
 * 关闭随机扰动；按天调度。数值用上游生成的 4592 组向量校验（见 FsrsTest）。
 */
object Fsrs {
    const val MIN_S = 0.001
    const val MAX_S = 36500.0

    private val w = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
        1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    )
    private val decay = -w[20]
    private val factor = round8(exp(ln(0.9) / decay) - 1)

    data class Memory(val stability: Double, val difficulty: Double) {
        init {
            require(stability.isFinite() && difficulty.isFinite() && stability in MIN_S..MAX_S && difficulty in 1.0..10.0) {
                "Invalid FSRS memory"
            }
        }
    }

    private fun round8(x: Double) = (x * 1e8).roundToLong() / 1e8
    private fun initialDifficulty(grade: Int) = round8(w[4] - exp((grade - 1) * w[5]) + 1)

    fun retrievability(m: Memory, elapsed: Double): Double {
        require(elapsed.isFinite() && elapsed >= 0) { "Invalid FSRS elapsed days" }
        return round8((1 + factor * elapsed / m.stability).pow(decay))
    }

    /** grade：1 忘记，2 困难，3 良好，4 简单。old 为 null 表示第一次作答。 */
    fun next(old: Memory?, elapsed: Double, grade: Int): Memory {
        require(grade in 1..4 && elapsed.isFinite() && elapsed >= 0) { "Invalid FSRS input" }
        if (old == null) {
            return Memory(w[grade - 1].coerceIn(MIN_S, MAX_S), initialDifficulty(grade).coerceIn(1.0, 10.0))
        }
        val s = old.stability
        val d = old.difficulty
        val r = retrievability(old, elapsed)
        val nextS = when {
            elapsed == 0.0 -> {
                var gain = s.pow(-w[19]) * exp(w[17] * (grade - 3 + w[18]))
                if (grade >= 2) gain = maxOf(gain, 1.0)
                round8((s * gain).coerceIn(MIN_S, MAX_S))
            }
            grade == 1 -> {
                val failed = round8(
                    (w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14])).coerceIn(MIN_S, MAX_S)
                )
                round8(s / exp(w[17] * w[18])).coerceIn(MIN_S, failed)
            }
            else -> {
                val hard = if (grade == 2) w[15] else 1.0
                val easy = if (grade == 4) w[16] else 1.0
                round8(
                    (s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp((1 - r) * w[10]) - 1) * hard * easy))
                        .coerceIn(MIN_S, MAX_S)
                )
            }
        }
        val damp = round8((-w[6] * (grade - 3)) * (10 - d) / 9)
        val nextD = round8(w[7] * initialDifficulty(4) + (1 - w[7]) * (d + damp)).coerceIn(1.0, 10.0)
        return Memory(nextS, nextD)
    }

    /** 达到目标保持率所需的间隔天数，至少 1 天。 */
    fun interval(m: Memory, retention: Double): Int {
        require(retention.isFinite() && retention > 0 && retention <= 1) { "Invalid FSRS retention" }
        return (m.stability / factor * (retention.pow(1 / decay) - 1)).roundToLong().toDouble().coerceIn(1.0, MAX_S).toInt()
    }
}
