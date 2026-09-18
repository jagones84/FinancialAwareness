// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import android.content.Context
import com.example.daysurpopt.R
import com.example.daysurpopt.data.SurplusDataRepository
import com.example.daysurpopt.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*
import kotlin.random.Random

object OptimizationLogic {

    /**
     * One-at-a-time sensitivity of the AVERAGE UTILITY (happiness) with respect to
     * each input, expressed per the unit stated in [SensitivityResult.unitResId]
     * (e.g. the daily-surplus row is the utility change per +100 EUR/month of
     * extra earnings). Not the scalarized objective: bonusStdWeight defines the
     * objective itself and does not move the average utility, so it has no row.
     */
    suspend fun runSensitivityAnalysis(
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput
    ): List<SensitivityResult> {
        val results = mutableListOf<SensitivityResult>()

        val baseUtility = withContext(Dispatchers.Default) {
            calculateAverageUtilityFromYears(calculateSimulation(baseInputs, specificExpenses, surplusData))
        }
        if (!baseUtility.isFinite() || baseUtility <= 0.0) {
            return emptyList()
        }

        suspend fun check(
            nameResId: Int,
            delta: Double,
            scale: Double,
            unitResId: Int,
            update: (FinancialInput, Double) -> FinancialInput
        ) {
            var effectiveDelta = delta
            var newUtility = withContext(Dispatchers.Default) {
                calculateAverageUtilityFromYears(
                    calculateSimulation(update(baseInputs, effectiveDelta), specificExpenses, surplusData)
                )
            }

            if (abs(newUtility - baseUtility) < 1e-9) {
                effectiveDelta = -delta
                newUtility = withContext(Dispatchers.Default) {
                    calculateAverageUtilityFromYears(
                        calculateSimulation(update(baseInputs, effectiveDelta), specificExpenses, surplusData)
                    )
                }
            }

            if (abs(effectiveDelta) < 1e-9) {
                results.add(SensitivityResult(nameResId, 0.0, unitResId))
                return
            }

            val sensitivity = (newUtility - baseUtility) / effectiveDelta
            results.add(SensitivityResult(nameResId, sensitivity * scale, unitResId))
        }

        val scale10k = 10000.0
        val scale1year = 1.0
        val scale1pp = 1.0
        val scale100eurMonth = 100.0 * 12.0 / 365.25

        check(R.string.sens_p1, 0.1, 0.1, R.string.unit_pt_10) { i, d -> i.copy(p1SavingRatioSurplus = (i.p1SavingRatioSurplus + d).coerceIn(0.0, 1.0)) }
        check(R.string.sens_p2, 1.0, scale1year, R.string.unit_pt_year) { i, d -> i.copy(p2EtaFineRisparmioNoCapitale = i.p2EtaFineRisparmioNoCapitale + d.roundToInt()) }
        check(R.string.sens_p3, 0.10, 0.10, R.string.unit_pt_10) { i, d -> i.copy(p3PercentualeCapitaleDaSpendereAnnualmente = i.p3PercentualeCapitaleDaSpendereAnnualmente + d) }
        check(R.string.sens_p4, 1.0, scale1year, R.string.unit_pt_year) { i, d -> i.copy(p4EtaAnticipataInizioSpesaCapitale = i.p4EtaAnticipataInizioSpesaCapitale + d.roundToInt()) }

        val monetaryDelta = (baseInputs.eredita * 0.01).takeIf { it > 1 } ?: 100.0
        check(R.string.sens_inheritance, monetaryDelta, scale10k, R.string.unit_pt_10k) { i, d -> i.copy(eredita = i.eredita + d) }
        check(R.string.sens_keep, -monetaryDelta, scale10k, R.string.unit_pt_10k) { i, d -> i.copy(soldiDaConservare = (i.soldiDaConservare + d).coerceAtLeast(0.0)) }
        check(R.string.sens_tfr, monetaryDelta, scale10k, R.string.unit_pt_10k) { i, d -> i.copy(tfrNetto = i.tfrNetto + d) }
        check(R.string.sens_initial_cap, monetaryDelta, scale10k, R.string.unit_pt_10k) { i, d -> i.copy(capitaleIniziale = i.capitaleIniziale + d) }

        check(R.string.sens_int_rate, 0.1, scale1pp, R.string.unit_pt_1pp) { i, d -> i.copy(tassoGuadagnoInteresse = i.tassoGuadagnoInteresse + d / 100.0) }
        check(R.string.sens_debt_rate, 0.1, scale1pp, R.string.unit_pt_1pp) { i, d -> i.copy(tassoInteresseDebito = i.tassoInteresseDebito + d / 100.0) }
        check(R.string.sens_utility_threshold, 0.01, 0.01, R.string.unit_pt_001) { i, d -> i.copy(sogliaMinimaFunzioneUtilita = i.sogliaMinimaFunzioneUtilita + d) }

        check(R.string.sens_max_spending, 1.0, scale100eurMonth, R.string.unit_pt_100eur) { i, d -> i.copy(valoreSpesaGiornalieraMaxUtilita = i.valoreSpesaGiornalieraMaxUtilita + d) }

        // Surplus Sensitivity (special case: +1 EUR/day of extra earnings)
        val surplusDelta = 1.0
        val surplusUtility = withContext(Dispatchers.Default) {
            calculateAverageUtilityFromYears(
                calculateSimulation(baseInputs, specificExpenses, surplusData, surplusOffset = surplusDelta)
            )
        }
        val surplusSensitivity = (surplusUtility - baseUtility) / surplusDelta
        results.add(SensitivityResult(R.string.sens_surplus, surplusSensitivity * scale100eurMonth, R.string.unit_pt_100eur))

        return results.sortedByDescending { abs(it.scaledImpact) }
    }

    fun tournamentSelect(fitness: DoubleArray, size: Int): Int {
        var bestIdx = Random.nextInt(fitness.size)
        repeat(size - 1) {
            val idx = Random.nextInt(fitness.size)
            if (fitness[idx] > fitness[bestIdx]) bestIdx = idx
        }
        return bestIdx
    }

    fun optimizeParameters(
        baseInputs: FinancialInput,
        config: GAConfig,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        initialGuess: ParamsCandidate? = null // New parameter to seed GA
    ): OptimizationResult {
        val min = config.min
        val max = config.max

        fun randomCandidate(): ParamsCandidate {
            val p1 = Random.nextDouble(min.p1, max.p1)
            val p2 = Random.nextInt(min.p2, max.p2 + 1)
            val p3 = Random.nextDouble(min.p3, max.p3)
            var p4 = Random.nextInt(min.p4, max.p4 + 1)
            p4 = max(p4, p2)
            return ParamsCandidate(p1, p2, p3, p4)
        }

        fun eval(c: ParamsCandidate): Double {
            val in2 = baseInputs.copy(
                p1SavingRatioSurplus = c.p1,
                p2EtaFineRisparmioNoCapitale = c.p2,
                p3PercentualeCapitaleDaSpendereAnnualmente = c.p3,
                p4EtaAnticipataInizioSpesaCapitale = c.p4
            )
            val (obj, _) = calculateSimulationWithWeight(in2, specificExpenses, surplusData)
            return if (config.maximize) obj else -obj
        }

        fun crossover(a: ParamsCandidate, b: ParamsCandidate, prob: Double): ParamsCandidate {
            fun pickD(x: Double, y: Double) = if (Random.nextDouble() < prob) x else y
            fun pickI(x: Int, y: Int) = if (Random.nextDouble() < prob) x else y
            val p1 = pickD(a.p1, b.p1)
            val p2 = pickI(a.p2, b.p2)
            val p3 = pickD(a.p3, b.p3)
            var p4 = pickI(a.p4, b.p4)
            p4 = max(p4, p2)
            return ParamsCandidate(p1, p2, p3, p4)
        }

        fun mutate(c: ParamsCandidate, pm: Double): ParamsCandidate {
            var p1 = c.p1
            var p2 = c.p2
            var p3 = c.p3
            var p4 = c.p4
            if (Random.nextDouble() < pm) p1 = Random.nextDouble(min.p1, max.p1)
            if (Random.nextDouble() < pm) p2 = Random.nextInt(min.p2, max.p2 + 1)
            if (Random.nextDouble() < pm) p3 = Random.nextDouble(min.p3, max.p3)
            if (Random.nextDouble() < pm) p4 = Random.nextInt(min.p4, max.p4 + 1)
            p4 = max(p4, p2)
            return ParamsCandidate(p1, p2, p3, p4)
        }

        val population = MutableList(config.popSize) { randomCandidate() }
        
        // Seed with initial guess if valid
        if (initialGuess != null) {
            // Ensure guess is within bounds (clamped)
            val seeded = ParamsCandidate(
                initialGuess.p1.coerceIn(min.p1, max.p1),
                initialGuess.p2.coerceIn(min.p2, max.p2),
                initialGuess.p3.coerceIn(min.p3, max.p3),
                max(initialGuess.p4.coerceIn(min.p4, max.p4), initialGuess.p2.coerceIn(min.p2, max.p2))
            )
            // Replace first N individuals with the seed to give it a strong start
            val seeds = min(5, config.popSize / 10).coerceAtLeast(1)
            repeat(seeds) { i -> population[i] = seeded }
        }

        val fitness = DoubleArray(config.popSize)
        var pm = config.pm
        var prevBest = Double.NEGATIVE_INFINITY
        var globalBestFitness = Double.NEGATIVE_INFINITY
        var globalBest = population[0]
        val history = mutableListOf<Pair<Int, Double>>()

        repeat(config.generations) { g ->
            for (i in 0 until config.popSize) fitness[i] = eval(population[i])
            var bestIndex = 0
            for (i in 1 until config.popSize) if (fitness[i] > fitness[bestIndex]) bestIndex = i
            val bestFitness = fitness[bestIndex]
            if (bestFitness > globalBestFitness) {
                globalBestFitness = bestFitness
                globalBest = population[bestIndex]
            }
            history.add(g + 1 to (if (config.maximize) bestFitness else -bestFitness))
            if (abs(bestFitness - prevBest) < 1e-4) pm = min(0.9, pm * 1.5)
            prevBest = bestFitness

            val newPop = MutableList(config.popSize) { globalBest }
            for (i in 1 until config.popSize) {
                val p1Idx = tournamentSelect(fitness, 3)
                val p2Idx = tournamentSelect(fitness, 3)
                var child = crossover(population[p1Idx], population[p2Idx], config.pc)
                child = mutate(child, pm)
                newPop[i] = child
            }
            for (i in 0 until config.popSize) population[i] = newPop[i]
        }

        val finalBestFitness = if (config.maximize) globalBestFitness else -globalBestFitness
        return OptimizationResult(globalBest, finalBestFitness, history)
    }

    fun parseGaConfig(ui: GAConfigUI, baseInputs: FinancialInput): GAConfig {
        val pop = ui.popSize.toIntOrNull() ?: 350
        val gen = ui.generations.toIntOrNull() ?: 100
        val pc = ui.pc.replace(',', '.').toDoubleOrNull() ?: 0.7
        val pm = ui.pm.replace(',', '.').toDoubleOrNull() ?: 0.08
        val minTokens = ui.minRange.split(';').map { it.trim() }
        val maxTokens = ui.maxRange.split(';').map { it.trim() }
        fun d(s: String?) = (s ?: "").replace(',', '.').toDoubleOrNull()
        fun i(s: String?) = (s ?: "").trim().toIntOrNull()
        val minP1 = d(minTokens.getOrNull(0)) ?: 0.0
        val minP2raw = i(minTokens.getOrNull(1)) ?: baseInputs.etaAttuale
        val minP3 = d(minTokens.getOrNull(2)) ?: 0.0
        val minP4raw = i(minTokens.getOrNull(3)) ?: baseInputs.etaAttuale
        val minP2 = max(minP2raw, baseInputs.etaAttuale)
        val minP4 = max(minP4raw, baseInputs.etaAttuale)
        val maxP1 = d(maxTokens.getOrNull(0)) ?: 1.0
        val maxP2raw = i(maxTokens.getOrNull(1)) ?: baseInputs.etaPensione
        val maxP3 = d(maxTokens.getOrNull(2)) ?: 1.0
        val maxP4raw = i(maxTokens.getOrNull(3)) ?: (baseInputs.etaMorte - 1)
        val maxP2 = max(maxP2raw, minP2)
        val maxP4 = max(maxP4raw, minP4)
        val maximize = ui.maximize.trim() == "1"
        return GAConfig(
            pop,
            gen,
            pc,
            pm,
            ParamsCandidate(minP1, minP2, minP3, max(minP4, minP2)),
            ParamsCandidate(maxP1, maxP2, maxP3, max(maxP4, maxP2)),
            maximize
        )
    }

    fun refineScalarCandidate(
        baseInputs: FinancialInput,
        start: ParamsCandidate,
        config: GAConfig,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput
    ): OptimizationResult {
        return coordinateSearch(
            baseInputs = baseInputs,
            start = start,
            config = config.copy(maximize = true),
            specificExpenses = specificExpenses,
            surplusData = surplusData
        )
    }

    fun coordinateSearch(
        baseInputs: FinancialInput,
        start: ParamsCandidate,
        config: GAConfig,
        maxIter: Int = 10,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput
    ): OptimizationResult {
        return refineScalarCandidateForTest(
            evaluator = { candidate ->
                val in2 = baseInputs.copy(
                    p1SavingRatioSurplus = candidate.p1,
                    p2EtaFineRisparmioNoCapitale = candidate.p2,
                    p3PercentualeCapitaleDaSpendereAnnualmente = candidate.p3,
                    p4EtaAnticipataInizioSpesaCapitale = candidate.p4
                )
                val (obj, _) = calculateSimulationWithWeight(in2, specificExpenses, surplusData)
                obj
            },
            start = start,
            config = config,
            maxIter = maxIter,
            maximize = config.maximize
        )
    }
}

internal fun refineScalarCandidateForTest(
    evaluator: (ParamsCandidate) -> Double,
    start: ParamsCandidate,
    config: GAConfig,
    maxIter: Int = 10,
    maximize: Boolean
): OptimizationResult {
    var currentBest = start
    var bestFitness = evaluator(currentBest)
    val history = mutableListOf(1 to bestFitness)

    val stepSizesP1 = (0 until maxIter).map { (config.max.p1 - config.min.p1) * 0.5.pow(it + 1) }
    val stepSizesP2 = (0 until maxIter).map { max(1, ((config.max.p2 - config.min.p2) * 0.5.pow(it + 1)).roundToInt()) }
    val stepSizesP3 = (0 until maxIter).map { (config.max.p3 - config.min.p3) * 0.5.pow(it + 1) }
    val stepSizesP4 = (0 until maxIter).map { max(1, ((config.max.p4 - config.min.p4) * 0.5.pow(it + 1)).roundToInt()) }

    fun isBetter(candidateFitness: Double, referenceFitness: Double): Boolean {
        return if (maximize) candidateFitness > referenceFitness else candidateFitness < referenceFitness
    }

    for (i in 0 until maxIter) {
        var improvedInCycle = true
        while (improvedInCycle) {
            improvedInCycle = false

            for (sign in listOf(-1.0, 1.0)) {
                val p1 = (currentBest.p1 + sign * stepSizesP1[i]).coerceIn(config.min.p1, config.max.p1)
                val candidate = currentBest.copy(p1 = p1)
                val fitness = evaluator(candidate)
                if (isBetter(fitness, bestFitness)) {
                    bestFitness = fitness
                    currentBest = candidate
                    improvedInCycle = true
                }
            }

            for (sign in listOf(-1, 1)) {
                val p2 = (currentBest.p2 + sign * stepSizesP2[i]).coerceIn(config.min.p2, config.max.p2)
                val candidate = currentBest.copy(p2 = p2, p4 = max(currentBest.p4, p2))
                val fitness = evaluator(candidate)
                if (isBetter(fitness, bestFitness)) {
                    bestFitness = fitness
                    currentBest = candidate
                    improvedInCycle = true
                }
            }

            for (sign in listOf(-1.0, 1.0)) {
                val p3 = (currentBest.p3 + sign * stepSizesP3[i]).coerceIn(config.min.p3, config.max.p3)
                val candidate = currentBest.copy(p3 = p3)
                val fitness = evaluator(candidate)
                if (isBetter(fitness, bestFitness)) {
                    bestFitness = fitness
                    currentBest = candidate
                    improvedInCycle = true
                }
            }

            for (sign in listOf(-1, 1)) {
                val p4 = (currentBest.p4 + sign * stepSizesP4[i]).coerceIn(config.min.p4, config.max.p4)
                val candidate = currentBest.copy(p4 = max(p4, currentBest.p2))
                val fitness = evaluator(candidate)
                if (isBetter(fitness, bestFitness)) {
                    bestFitness = fitness
                    currentBest = candidate
                    improvedInCycle = true
                }
            }
        }
        history.add(i + 2 to bestFitness)
    }

    return OptimizationResult(currentBest, bestFitness, history)
}
