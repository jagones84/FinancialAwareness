// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.GAConfig
import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.ParetoFrontResult
import com.example.daysurpopt.domain.ParetoPoint
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import kotlin.math.max
import kotlin.random.Random

object ParetoOptimizationLogic {

    fun extractNonDominatedFront(points: List<ParetoPoint>): List<ParetoPoint> {
        return points.filter { candidate ->
            points.none { other ->
                other !== candidate && (other.constraintDominates(candidate) || other.dominates(candidate))
            }
        }
    }

    fun optimizeParetoParameters(
        baseInputs: FinancialInput,
        config: GAConfig,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        random: Random = Random.Default
    ): ParetoFrontResult {
        fun randomCandidate(): ParamsCandidate {
            val p1 = if (config.min.p1 == config.max.p1) config.min.p1 else random.nextDouble(config.min.p1, config.max.p1)
            val p2 = if (config.min.p2 == config.max.p2) config.min.p2 else random.nextInt(config.min.p2, config.max.p2 + 1)
            val p3 = if (config.min.p3 == config.max.p3) config.min.p3 else random.nextDouble(config.min.p3, config.max.p3)
            var p4 = if (config.min.p4 == config.max.p4) config.min.p4 else random.nextInt(config.min.p4, config.max.p4 + 1)
            p4 = max(p4, p2)
            return ParamsCandidate(p1, p2, p3, p4)
        }

        fun evaluate(candidate: ParamsCandidate): ParetoPoint {
            val in2 = baseInputs.copy(
                p1SavingRatioSurplus = candidate.p1,
                p2EtaFineRisparmioNoCapitale = candidate.p2,
                p3PercentualeCapitaleDaSpendereAnnualmente = candidate.p3,
                p4EtaAnticipataInizioSpesaCapitale = candidate.p4
            )
            val years = calculateSimulation(in2, specificExpenses, surplusData)
            val metrics = calculateObjectivesFromYears(
                years = years,
                bonusStdWeight = 0.0,
                legacyTarget = in2.soldiDaConservare
            )

            return ParetoPoint(
                params = candidate,
                avgUtility = metrics.avgUtilita,
                stdDevUtility = metrics.stdDev,
                isFeasible = metrics.isFeasible,
                finalCapital = metrics.finalCapital,
                legacyGap = metrics.legacyGap
            )
        }

        val evaluated = buildList {
            repeat(config.popSize * max(1, config.generations)) {
                add(evaluate(randomCandidate()))
            }
        }

        val front = extractNonDominatedFront(evaluated)
            .filter { it.isFeasible }
            .distinctBy { it.params }
        val idealAvg = front.maxOfOrNull { it.avgUtility } ?: 0.0
        val idealStd = front.minOfOrNull { it.stdDevUtility } ?: 0.0

        return ParetoFrontResult(
            points = front,
            referencePoint = null,
            idealAvgUtility = idealAvg,
            idealStdDevUtility = idealStd
        )
    }
}
