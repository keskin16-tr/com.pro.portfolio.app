package com.example.portfolio.analytics

import com.example.portfolio.model.PricePoint

data class PerformancePoint(
    val timestamp: Long,
    val portfolioReturn: Double,
    val benchmarkReturn: Double
)

data class PerformanceResult(
    val portfolioPercent: Double,
    val benchmarkPercent: Double,
    val difference: Double,
    val points: List<PerformancePoint>
)

object BenchmarkAnalyzer {

    fun compare(
        portfolio: List<Pair<Long, Double>>,
        benchmark: List<PricePoint>
    ): PerformanceResult? {

        if (portfolio.size < 2 || benchmark.size < 2) {
            return null
        }

        val sortedPortfolio = portfolio.sortedBy { pair: Pair<Long, Double> -> pair.first }
        val sortedBenchmark = benchmark.sortedBy { point: PricePoint -> point.timestamp }

        val firstPortfolio = sortedPortfolio.first()
        val lastPortfolio = sortedPortfolio.last()

        val firstBenchmark = sortedBenchmark.minByOrNull { point: PricePoint ->
            kotlin.math.abs(point.timestamp - firstPortfolio.first)
        } ?: return null

        val lastBenchmark = sortedBenchmark.minByOrNull { point: PricePoint ->
            kotlin.math.abs(point.timestamp - lastPortfolio.first)
        } ?: return null

        if (firstPortfolio.second <= 0 ||
            firstBenchmark.price <= 0 ||
            lastPortfolio.second < 0 ||
            lastBenchmark.price <= 0
        ) return null

        val benchmarkPercent =
            (lastBenchmark.price / firstBenchmark.price - 1) * 100

        val portfolioPercent =
            (lastPortfolio.second / firstPortfolio.second - 1) * 100

        val points = sortedPortfolio.mapNotNull { item: Pair<Long, Double> ->
            val matching = sortedBenchmark.minByOrNull { point: PricePoint ->
                kotlin.math.abs(point.timestamp - item.first)
            } ?: return@mapNotNull null

            if (matching.price <= 0 || firstBenchmark.price <= 0) {
                return@mapNotNull null
            }

            PerformancePoint(
                timestamp = item.first,
                portfolioReturn =
                    (item.second / firstPortfolio.second - 1) * 100,
                benchmarkReturn =
                    (matching.price / firstBenchmark.price - 1) * 100
            )
        }

        return PerformanceResult(
            portfolioPercent = portfolioPercent,
            benchmarkPercent = benchmarkPercent,
            difference = portfolioPercent - benchmarkPercent,
            points = points
        )
    }
}
