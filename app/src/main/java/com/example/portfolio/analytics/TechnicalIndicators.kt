
fun ema20(values: List<Double>) = ema(values, 20)

fun ema50(values: List<Double>) = ema(values, 50)

fun ema200(values: List<Double>) = ema(values, 200)

data class StochasticResult(
    val k: List<Double?>,
    val d: List<Double?>
)

fun stochastic(
    highs: List<Double>,
    lows: List<Double>,
    closes: List<Double>,
    period: Int = 14,
    smooth: Int = 3
): StochasticResult {

    if (highs.size != lows.size ||
        highs.size != closes.size ||
        period <= 0 || smooth <= 0
    ) {
        return StochasticResult(emptyList(), emptyList())
    }

    val k = MutableList<Double?>(closes.size) { null }

    for (i in period - 1 until closes.size) {
        val highest = highs
            .subList(i - period + 1, i + 1).maxOrNull() ?: continue

        val lowest = lows
            .subList(i - period + 1, i + 1).minOrNull() ?: continue

        k[i] = if (highest == lowest) 50.0
        else (closes[i] - lowest) / (highest - lowest) * 100
    }

    val d = MutableList<Double?>(closes.size) { null }

    for (i in k.indices) {
        if (i + 1 < period - 1 + smooth) continue

        val window = k.subList(i - smooth + 1, i + 1)
        if (window.all { it != null }) {
            d[i] = window.filterNotNull().average()
        }
    }

    return StochasticResult(k, d)
}
