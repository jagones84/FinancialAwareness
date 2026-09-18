// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.*
import com.example.daysurpopt.utils.AppDebugLog
import kotlin.math.*

private fun sortedFiniteCurve(points: List<CurvePoint>): List<CurvePoint> {
    return points
        .filter { it.x.isFinite() && it.y.isFinite() }
        .sortedBy { it.x }
}

private fun interpolateCurveY(points: List<CurvePoint>, x: Double): Double {
    if (points.isEmpty()) return 0.0
    if (points.size == 1) return points[0].y
    if (x <= points[0].x) return points[0].y
    if (x >= points[points.lastIndex].x) return points[points.lastIndex].y

    for (i in 0 until points.lastIndex) {
        val p0 = points[i]
        val p1 = points[i + 1]
        if (x >= p0.x && x <= p1.x) {
            val dx = p1.x - p0.x
            if (dx == 0.0) return p0.y
            val t = (x - p0.x) / dx
            return p0.y + t * (p1.y - p0.y)
        }
    }

    return points[points.lastIndex].y
}

private fun invertCurveX(points: List<CurvePoint>, yTarget: Double): Double {
    if (points.isEmpty()) return 0.0
    if (points.size == 1) return points[0].x

    val ys = points.map { it.y }
    val minY = ys.minOrNull() ?: 0.0
    val maxY = ys.maxOrNull() ?: 1.0

    if (yTarget <= minY) return points[0].x
    if (yTarget >= maxY) return points[points.lastIndex].x

    var bestX: Double? = null

    for (i in 0 until points.lastIndex) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val y0 = p0.y
        val y1 = p1.y
        val lo = min(y0, y1)
        val hi = max(y0, y1)
        if (yTarget < lo || yTarget > hi) continue

        val dy = y1 - y0
        val x = if (dy == 0.0) {
            p0.x
        } else {
            val t = (yTarget - y0) / dy
            p0.x + t * (p1.x - p0.x)
        }

        bestX = if (bestX == null) x else min(bestX!!, x)
    }

    return bestX ?: points[points.lastIndex].x
}

fun calculateStandardDeviation(numbers: List<Double>): Double {
    val finite = numbers.filter { it.isFinite() }
    if (finite.size < 2) return 0.0
    val mean = finite.average()
    val variance = finite.sumOf { (it - mean).pow(2) } / finite.size
    return sqrt(variance)
}

fun computeMaxUtilityMonthlySpend(inputs: FinancialInput): Double {
    val curve = inputs.utilityCurvePoints
        ?.filter { it.x.isFinite() && it.y.isFinite() }
        ?.takeIf { it.size >= 2 }
    if (curve != null) {
        val curveMax = curve.maxOf { it.y }
        val xSat = curve.filter { it.y >= curveMax }.minOf { it.x }
        return xSat * DAYS_PER_MONTH
    }
    return inputs.valoreSpesaGiornalieraMaxUtilita * DAYS_PER_MONTH
}

private const val STD_EPSILON = 1e-12
private const val DEATH_LEGACY_PENALTY = 2.5
private const val LEGACY_BREACH_FLOOR = 1.0
private const val UTILITY_SENTINEL_ABS = 1e6
private const val DAYS_PER_MONTH = 365.25 / 12.0

fun computeStabilityScore(avgUtilita: Double, stdDevUtilita: Double): Double {
    if (!avgUtilita.isFinite() || avgUtilita <= 0.0) return 0.0
    if (!stdDevUtilita.isFinite() || stdDevUtilita <= 0.0) return 1.0

    return (avgUtilita / (avgUtilita + stdDevUtilita)).coerceIn(0.0, 1.0)
}

fun funzioneDegradoPerEta(eta: Double): Double {
    return 0.3 + (1 - 0.3) / (1 + exp(0.15 * (eta - 55.0)))
}

fun funzioneDegradoPerEta(eta: Int, inputs: FinancialInput): Double {
    return funzioneDegradoPerEta(eta.toDouble(), inputs)
}

fun funzioneDegradoPerEta(eta: Double, inputs: FinancialInput): Double {
    val curve = inputs.degradationCurvePoints?.let(::sortedFiniteCurve)?.takeIf { it.size >= 2 }
    return if (curve != null) {
        interpolateCurveY(curve, eta).coerceIn(0.0, 1.0)
    } else {
        funzioneDegradoPerEta(eta)
    }
}

fun utilitaDaSpesa(eta: Int, spesaMensile: Double, inputs: FinancialInput): Double {
    return utilitaDaSpesa(eta.toDouble(), spesaMensile, inputs)
}

fun utilitaDaSpesa(eta: Double, spesaMensile: Double, inputs: FinancialInput): Double {
    val curve = inputs.utilityCurvePoints?.let(::sortedFiniteCurve)?.takeIf { it.size >= 2 }

    val uRaw = if (curve != null) {
        val daily = (spesaMensile / DAYS_PER_MONTH).coerceAtLeast(0.0)
        interpolateCurveY(curve, daily).coerceIn(0.0, 1.0)
    } else {
        val baselineMax = Defaults.BASELINE_MAX_SPESA
        val baselineCenter = Defaults.BASELINE_CENTER
        val baselineK = Defaults.BASELINE_K
        val target = inputs.valoreSpesaGiornalieraMaxUtilita * DAYS_PER_MONTH
        val scale = if (target > 0) target / baselineMax else 1.0
        val x0 = baselineCenter * scale
        val k = baselineK / scale

        val u = 1.0 / (1.0 + exp(-k * (spesaMensile - x0)))
        u.coerceIn(0.0, 1.0)
    }

    val fdeg = funzioneDegradoPerEta(eta, inputs)
    return (fdeg * uRaw).coerceIn(0.0, 1.0)
}

private data class MonthlySimulationPoint(
    val eta: Int,
    val capitaleInizioPeriodo: Double,
    val spesaMensile: Double,
    val utility: Double,
    val savingRatio: Double,
    val capitaleFinePeriodo: Double,
    val utilityAtThreshold: Boolean,
    val debtAmount: Double,
    val debtRepayment: Double,
    val violazioneLascito: Boolean
)

private fun monthlyRateFromAnnual(annualRate: Double): Double {
    return (1.0 + annualRate).pow(1.0 / 12.0) - 1.0
}

private fun spesaMinimaPerEta(eta: Double, utilityOffset: Double, inputs: FinancialInput): Double {
    val fdeg = funzioneDegradoPerEta(eta, inputs).coerceAtLeast(1e-9)
    val requiredUtility = (inputs.sogliaMinimaFunzioneUtilita - utilityOffset).coerceAtLeast(0.0)
    if (requiredUtility <= 0.0) return 0.0

    val requiredRaw = (requiredUtility / fdeg).coerceIn(0.0, 1.0)
    val curve = inputs.utilityCurvePoints?.let(::sortedFiniteCurve)?.takeIf { it.size >= 2 }

    return if (curve != null) {
        invertCurveX(curve, requiredRaw).coerceAtLeast(0.0) * DAYS_PER_MONTH
    } else {
        val q = requiredRaw.coerceIn(1e-6, 1.0 - 1e-6)
        val baselineMax = Defaults.BASELINE_MAX_SPESA
        val baselineCenter = Defaults.BASELINE_CENTER
        val baselineK = Defaults.BASELINE_K
        val targetMonthly = inputs.valoreSpesaGiornalieraMaxUtilita * DAYS_PER_MONTH
        val scale = if (targetMonthly > 0.0) targetMonthly / baselineMax else 1.0
        val x0 = baselineCenter * scale
        val k = baselineK / scale
        val res = x0 + (1.0 / k) * ln(q / (1.0 - q))
        if (res.isFinite()) res else 0.0
    }
}

private fun monthlySurplusForAge(
    age: Double,
    inputs: FinancialInput,
    surplusData: SurplusInput,
    surplusOffset: Double
): Double {
    val ageInt = floor(age).toInt()
    var monthly = when {
        ageInt < inputs.etaPensione -> {
            val income = surplusData.stipendioMensile +
                (surplusData.premioRisultatoNettoAnnuale / 12.0) +
                (surplusData.tredicesimaQuattordicesimaNetto / 12.0) +
                if (ageInt <= surplusData.bonusEventualiPersonaliMensileFinoEta) surplusData.bonusEventualiPersonaliMensile else 0.0
            val outgo = (if (ageInt < surplusData.mutuoAffittoFinoEta) surplusData.mutuoAffitto else 0.0) +
                surplusData.condominioLavorativa +
                surplusData.bolletteLavorativa +
                surplusData.ciboLavorativa +
                surplusData.veicoliLavorativa +
                surplusData.palestraLavorativa +
                surplusData.trasportiViaggiLavorativa +
                surplusData.saluteLavorativa +
                surplusData.vacanzeLavorativa +
                surplusData.shoppingLavorativa +
                surplusData.altroLavorativa
            income - outgo
        }
        else -> {
            val income = surplusData.pensioneMensileNetta +
                surplusData.altreEntrateMensiliPensione +
                (surplusData.tredicesimaQuattordicesimaNettoPensione / 12.0) +
                if (ageInt <= surplusData.bonusEventualiPersonaliPensioneMensileFinoEta) surplusData.bonusEventualiPersonaliPensioneMensile else 0.0
            val outgo = (if (ageInt < surplusData.mutuoAffittoFinoEta) surplusData.mutuoAffitto else 0.0) +
                surplusData.condominioPensione +
                surplusData.bollettePensione +
                surplusData.ciboPensione +
                surplusData.veicoliPensione +
                surplusData.palestraPensione +
                surplusData.trasportiViaggiPensione +
                surplusData.salutePensione +
                surplusData.vacanzePensione +
                surplusData.shoppingPensione +
                surplusData.altroPensione
            income - outgo
        }
    }

    monthly += surplusOffset * DAYS_PER_MONTH
    return monthly
}

private fun aggregateMonthlyPoints(monthly: List<MonthlySimulationPoint>): List<SimulationYear> {
    if (monthly.isEmpty()) return emptyList()

    return monthly
        .groupBy { it.eta }
        .toSortedMap()
        .map { (eta, points) ->
            val opening = points.first().capitaleInizioPeriodo
            val ending = points.last().capitaleFinePeriodo
            val utilitySamples = points.map { it.utility }
            SimulationYear(
                eta = eta,
                capitaleInizioAnno = opening,
                spesaMensileCorrettaFinale = points.map { it.spesaMensile }.average(),
                funzioneUtilita = utilitySamples.average(),
                savingRatioEffettivo = points.map { it.savingRatio }.average(),
                violazioneLascito = points.any { it.violazioneLascito },
                capitaleFineAnno = ending,
                utilityAtThreshold = points.any { it.utilityAtThreshold },
                debtAmount = points.last().debtAmount,
                debtRepayment = points.sumOf { it.debtRepayment },
                capitaleEroso = opening - ending,
                monthlyUtilitySamples = utilitySamples
            )
        }
}

fun calculateSimulation(
    inputs: FinancialInput,
    specificExpenses: List<SpecificExpense>,
    surplusData: SurplusInput,
    surplusOffset: Double = 0.0
): List<SimulationYear> {
    try {
        val startAge = inputs.etaAttuale.toDouble()
        val monthCount = max(0, (inputs.etaMorte - inputs.etaAttuale) * 12)
        if (monthCount == 0) return emptyList()

        val p2Month = max(0, (inputs.p2EtaFineRisparmioNoCapitale - inputs.etaAttuale) * 12)
        val p4Age = max(inputs.p4EtaAnticipataInizioSpesaCapitale, inputs.p2EtaFineRisparmioNoCapitale)
        val p4Month = max(0, (p4Age - inputs.etaAttuale) * 12)
        val capitalMonthlyRate = monthlyRateFromAnnual(inputs.tassoGuadagnoInteresse)
        val debtMonthlyRate = monthlyRateFromAnnual(inputs.tassoInteresseDebito)
        val maxUtilSpendMonthly = computeMaxUtilityMonthlySpend(inputs)

        val expenseAmountByMonth = DoubleArray(monthCount)
        val expenseUtilityOffsetByMonth = DoubleArray(monthCount)
        for (expense in specificExpenses) {
            val month = ((expense.age - startAge) * 12.0).roundToInt()
            if (month in 0 until monthCount) {
                expenseAmountByMonth[month] += expense.amount
                expenseUtilityOffsetByMonth[month] += expense.utilityOffset
            }
        }
        val minimumSpendByMonth = DoubleArray(monthCount)
        var offsetAccumulator = 0.0
        for (month in 0 until monthCount) {
            offsetAccumulator += expenseUtilityOffsetByMonth[month]
            minimumSpendByMonth[month] = spesaMinimaPerEta(startAge + month / 12.0, offsetAccumulator, inputs)
        }
        val pensionMonth = max(0, ((inputs.etaPensione - startAge) * 12).roundToInt())

        val inheritanceMonth = ((inputs.etaRicevimentoEredita - startAge) * 12.0).roundToInt()
        val tfrMonth = ((inputs.etaPensione - startAge) * 12.0).roundToInt()

        var capital = inputs.capitaleIniziale
        var debt = 0.0
        var cumulativeUtilityOffset = 0.0
        val monthlyPoints = mutableListOf<MonthlySimulationPoint>()

        fun normalizeBalances(): Double {
            var repaid = 0.0
            if (capital < 0.0) {
                debt += -capital
                capital = 0.0
            }
            if (capital > 0.0 && debt > 0.0) {
                repaid = min(capital, debt)
                capital -= repaid
                debt -= repaid
            }
            return repaid
        }

        fun forecastFinalWithMinimumSpend(fromMonth: Int, startCapital: Double, startDebt: Double): Double {
            var forecastCapital = startCapital
            var forecastDebt = startDebt
            for (m in fromMonth until monthCount) {
                val ageM = startAge + m / 12.0
                forecastCapital *= (1.0 + capitalMonthlyRate)
                if (forecastDebt > 0.0) forecastDebt *= (1.0 + debtMonthlyRate)
                if (m == inheritanceMonth) forecastCapital += inputs.eredita
                if (m == tfrMonth) forecastCapital += inputs.tfrNetto
                forecastCapital -= expenseAmountByMonth[m]
                forecastCapital += monthlySurplusForAge(ageM, inputs, surplusData, surplusOffset) -
                    minimumSpendByMonth[m]
                if (forecastCapital < 0.0) {
                    forecastDebt += -forecastCapital
                    forecastCapital = 0.0
                } else if (forecastCapital > 0.0 && forecastDebt > 0.0) {
                    val repaid = min(forecastCapital, forecastDebt)
                    forecastCapital -= repaid
                    forecastDebt -= repaid
                }
            }
            return forecastCapital - forecastDebt
        }

        for (month in 0 until monthCount) {
            val age = startAge + month / 12.0
            val ageBucket = floor(age).toInt()
            val openingCapital = capital

            capital *= (1.0 + capitalMonthlyRate)
            if (debt > 0.0) {
                debt *= (1.0 + debtMonthlyRate)
            }

            if (month == inheritanceMonth) capital += inputs.eredita
            if (month == tfrMonth) capital += inputs.tfrNetto

            capital -= expenseAmountByMonth[month]
            cumulativeUtilityOffset += expenseUtilityOffsetByMonth[month]

            var debtRepayment = normalizeBalances()

            val monthlySurplus = monthlySurplusForAge(age, inputs, surplusData, surplusOffset)
            val monthlySaving = if (month < p2Month) inputs.p1SavingRatioSurplus * monthlySurplus else 0.0
            val spendSurplus = monthlySurplus - monthlySaving

            capital += monthlySaving
            debtRepayment += normalizeBalances()

            val availableNetWorth = (capital - debt).coerceAtLeast(0.0)
            val remainingMonths = (monthCount - month).coerceAtLeast(1)
            val draw = when {
                month < p4Month -> 0.0
                month < pensionMonth -> {
                    val spendable = (availableNetWorth - inputs.soldiDaConservare).coerceAtLeast(0.0)
                    inputs.p3PercentualeCapitaleDaSpendereAnnualmente * spendable / remainingMonths.toDouble()
                }
                else -> {
                    val yearsLeft = remainingMonths / 12.0
                    val i = inputs.tassoGuadagnoInteresse
                    val annuityAnnual = if (i > 0.0) {
                        ((availableNetWorth * (1.0 + i).pow(yearsLeft) - inputs.soldiDaConservare) * i) /
                            ((1.0 + i).pow(yearsLeft) - 1.0)
                    } else {
                        (availableNetWorth - inputs.soldiDaConservare) / yearsLeft
                    }
                    val annuityMonthly = inputs.p3PercentualeCapitaleDaSpendereAnnualmente *
                        (annuityAnnual - debt * inputs.tassoInteresseDebito) / 12.0
                    val postDrawCapital = (capital - annuityMonthly).coerceAtLeast(0.0)
                    val postDrawForecast = forecastFinalWithMinimumSpend(month + 1, postDrawCapital, debt)
                    if (annuityMonthly <= 0.0 ||
                        postDrawForecast < inputs.soldiDaConservare + 1.0
                    ) {
                        0.0
                    } else {
                        annuityMonthly
                    }
                }
            }

            if (draw > 0.0) {
                capital -= draw
                normalizeBalances()
            }

            val baseSpend = (spendSurplus + draw).coerceAtLeast(0.0)
            val minimumSpend = minimumSpendByMonth[month]
            val finalSpend = max(min(baseSpend, maxUtilSpendMonthly), minimumSpend)
            val additionalSpendNeeded = (finalSpend - baseSpend).coerceAtLeast(0.0)

            if (additionalSpendNeeded > 0.0) {
                capital -= additionalSpendNeeded
                normalizeBalances()
            }

            val utility = (utilitaDaSpesa(age, finalSpend, inputs) + cumulativeUtilityOffset).coerceAtMost(1.0)
            val savingRatioEffettivo = if (monthlySurplus > 0.0) (monthlySaving / monthlySurplus) else 0.0
            val violazioneLascito = month == monthCount - 1 && (capital - debt) < (inputs.soldiDaConservare - 1.0)

            monthlyPoints.add(
                MonthlySimulationPoint(
                    eta = ageBucket,
                    capitaleInizioPeriodo = openingCapital,
                    spesaMensile = finalSpend,
                    utility = if (utility.isFinite()) utility else -1.0,
                    savingRatio = savingRatioEffettivo,
                    capitaleFinePeriodo = capital,
                    utilityAtThreshold = abs(finalSpend - minimumSpend) <= 1e-6 && minimumSpend > 0.0,
                    debtAmount = debt,
                    debtRepayment = debtRepayment,
                    violazioneLascito = violazioneLascito
                )
            )

            if (!capital.isFinite() || !debt.isFinite()) {
                throw ArithmeticException("Monthly capital or debt became non-finite")
            }
        }

        return aggregateMonthlyPoints(monthlyPoints)
    } catch (e: Exception) {
        // Return a special dummy year that will force fobj to 0 in upstream functions
        return listOf(SimulationYear(eta = inputs.etaAttuale, funzioneUtilita = -1e9))
    }
}


fun computeObjective(avgUtilita: Double, stdDevUtilita: Double, bonusStdWeight: Double): Double {
    val weight = bonusStdWeight.coerceIn(0.0, 1.0)
    val stabilityTerm = computeStabilityScore(avgUtilita, stdDevUtilita)
    val penaltyFactor = (1.0 - weight) + weight * stabilityTerm
    return avgUtilita * penaltyFactor
}

fun calculateObjectivesFromYears(
    years: List<SimulationYear>,
    bonusStdWeight: Double,
    legacyTarget: Double? = null
): ObjectiveResults {
    if (years.isEmpty()) return ObjectiveResults(0.0, 0.0, 0.0, 0.0, 0.0)

    val finalCapital = years.lastOrNull()?.capitaleFineAnno ?: 0.0
    val legacyGap = finalCapital - (legacyTarget ?: 0.0)
    val utilitySamples = years.flatMap { year ->
        if (year.monthlyUtilitySamples.isNotEmpty()) year.monthlyUtilitySamples else listOf(year.funzioneUtilita)
    }
    val isFeasible = !years.any { it.violazioneLascito } &&
        utilitySamples.none { !it.isFinite() || it < 0.0 }

    // Math-error guard only: non-finite samples or the -1e9 exception dummy force 0.
    // Finite negative samples (disutility offsets) flow into the average - graded, old-style.
    if (utilitySamples.any { !it.isFinite() || abs(it) >= UTILITY_SENTINEL_ABS }) {
        AppDebugLog.add("SimLogic", "Zero objective: non-finite or sentinel utility sample detected")
        return ObjectiveResults(0.0, 0.0, 0.0, 0.0, 0.0, false, finalCapital, legacyGap)
    }

    val avgUtilita = utilitySamples.average()
    if (!avgUtilita.isFinite()) {
        AppDebugLog.add("SimLogic", "Zero objective: non-finite avgUtilita: $avgUtilita")
        return ObjectiveResults(0.0, 0.0, 0.0, 0.0, 0.0, false, finalCapital, legacyGap)
    }

    val stdDevUtilita = calculateStandardDeviation(utilitySamples)
    val legacyViolated = years.any { it.violazioneLascito }
    val finalNetWorth = finalCapital - (years.lastOrNull()?.debtAmount ?: 0.0)
    val legacyPenalty = if (legacyViolated && legacyTarget != null && legacyTarget > 0.0) {
        val shortfallRatio = ((legacyTarget - finalNetWorth) / legacyTarget).coerceAtLeast(0.0)
        LEGACY_BREACH_FLOOR + DEATH_LEGACY_PENALTY * shortfallRatio
    } else if (legacyViolated) {
        DEATH_LEGACY_PENALTY
    } else {
        0.0
    }

    val fObjW = computeObjective(avgUtilita, stdDevUtilita, bonusStdWeight) - legacyPenalty
    if (legacyViolated) {
        AppDebugLog.add("SimLogic", "Graded legacy penalty: fObjW=$fObjW (penalty=$legacyPenalty, net=$finalNetWorth, target=$legacyTarget)")
    }
    if (!legacyViolated && fObjW < 0.0001) {
        AppDebugLog.add("SimLogic", "Low fObjW: $fObjW (avg: $avgUtilita, std: $stdDevUtilita)")
    }

    val fObj0 = computeObjective(avgUtilita, stdDevUtilita, 0.0) - legacyPenalty
    val stabilityIndex = computeStabilityScore(avgUtilita, stdDevUtilita)

    return ObjectiveResults(
        fObjW = fObjW,
        fObj0 = fObj0,
        stabilityIndex = stabilityIndex,
        stdDev = stdDevUtilita,
        avgUtilita = avgUtilita,
        isFeasible = isFeasible,
        finalCapital = finalCapital,
        legacyGap = legacyGap
    )
}

/**
 * Average utility across the whole plan ("happiness"), mirroring the aggregation
 * used for the objective: all monthly samples when available, otherwise the yearly
 * aggregate. This is the metric the sensitivity analysis refers to.
 */
fun calculateAverageUtilityFromYears(years: List<SimulationYear>): Double {
    if (years.isEmpty()) return 0.0
    val samples = years.flatMap { year ->
        if (year.monthlyUtilitySamples.isNotEmpty()) year.monthlyUtilitySamples else listOf(year.funzioneUtilita)
    }
    return samples.average()
}

fun calculateSimulationWithWeight(
    inputs: FinancialInput,
    specificExpenses: List<SpecificExpense>,
    surplusData: SurplusInput,
    surplusOffset: Double = 0.0
): Pair<Double, List<SimulationYear>> {
    val years = calculateSimulation(inputs, specificExpenses, surplusData, surplusOffset)
    val results = calculateObjectivesFromYears(
        years = years,
        bonusStdWeight = inputs.bonusStdWeight,
        legacyTarget = inputs.soldiDaConservare
    )
    return results.fObjW to years
}
