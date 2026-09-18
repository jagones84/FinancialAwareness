// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.domain.Defaults
import com.google.gson.Gson
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A/B diagnostic: faithful port of the June engine (commit dc1e7a0, annual
 * age-based loop) run side by side with the current monthly engine on the
 * user's real data, to locate where the fobj landscape flattens.
 * Opt-in: runs only when the extracted prefs folder exists.
 */
class JuneEngineLandscapeABTest {

    private val prefsDir: File =
        File(System.getenv("FA_PREFS_DIR") ?: "C:\\WINDOWS\\TEMP\\fa_prefs")

    private val gson = Gson()

    private fun readPayload(prefsFile: String, key: String): String? {
        val file = File(prefsDir, prefsFile)
        if (!file.isFile) return null
        val xml = file.readText()
        val regex = Regex("<string name=\"$key\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        val raw = regex.find(xml)?.groupValues?.get(1) ?: return null
        return raw
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&#10;", "\n")
            .replace("&#39;", "'")
    }

    private fun loadUserScenario(): Triple<FinancialInput, SurplusInput, List<SpecificExpense>>? {
        val inputsJson = readPayload("FinancialPrefs.xml", "FinancialInputs") ?: return null
        val surplusJson = readPayload("SurplusPrefs.xml", "SurplusInputs") ?: return null
        val expensesJson = readPayload("SpecificExpensesPrefs.xml", "SpecificExpenses")
        val inputs = gson.fromJson(inputsJson, FinancialInput::class.java)
        val surplus = gson.fromJson(surplusJson, SurplusInput::class.java)
        val expenses: List<SpecificExpense> = expensesJson?.let {
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java, SpecificExpense::class.java
            ).type
            gson.fromJson<List<SpecificExpense>>(it, type)
        } ?: emptyList()
        return Triple(inputs, surplus, expenses)
    }

    // ---------- June engine fixture (dc1e7a0) ----------

    private fun juneSortedFiniteCurve(points: List<CurvePoint>): List<CurvePoint> =
        points.filter { it.x.isFinite() && it.y.isFinite() }.sortedBy { it.x }

    private fun juneInterpolateCurveY(points: List<CurvePoint>, x: Double): Double {
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

    private fun juneInvertCurveX(points: List<CurvePoint>, yTarget: Double): Double {
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
            val x = if (dy == 0.0) p0.x else {
                val t = (yTarget - y0) / dy
                p0.x + t * (p1.x - p0.x)
            }
            bestX = if (bestX == null) x else min(bestX!!, x)
        }
        return bestX ?: points[points.lastIndex].x
    }

    private fun juneDegradoPerEta(eta: Int, inputs: FinancialInput): Double {
        val curve = inputs.degradationCurvePoints?.let(::juneSortedFiniteCurve)?.takeIf { it.size >= 2 }
        return if (curve != null) {
            juneInterpolateCurveY(curve, eta.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.3 + (1 - 0.3) / (1 + exp(0.15 * (eta - 55)))
        }
    }

    private fun juneUtilitaDaSpesa(eta: Int, spesaMensile: Double, inputs: FinancialInput): Double {
        val daysPerMonth = 365.0 / 12.0
        val curve = inputs.utilityCurvePoints?.let(::juneSortedFiniteCurve)?.takeIf { it.size >= 2 }
        val uRaw = if (curve != null) {
            val daily = (spesaMensile / daysPerMonth).coerceAtLeast(0.0)
            juneInterpolateCurveY(curve, daily).coerceIn(0.0, 1.0)
        } else {
            val target = inputs.valoreSpesaGiornalieraMaxUtilita * daysPerMonth
            val scale = if (target > 0) target / Defaults.BASELINE_MAX_SPESA else 1.0
            val x0 = Defaults.BASELINE_CENTER * scale
            val k = Defaults.BASELINE_K / scale
            (1.0 / (1.0 + exp(-k * (spesaMensile - x0)))).coerceIn(0.0, 1.0)
        }
        val fdeg = juneDegradoPerEta(eta, inputs)
        return (fdeg * uRaw).coerceIn(0.0, 1.0)
    }

    private fun juneCalculateSimulation(
        inputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        surplusOffset: Double = 0.0
    ): List<SimulationYear> {
        return try {
            val simulationYears = mutableListOf<SimulationYear>()
            var capitaleAnnoPrecedente = inputs.capitaleIniziale
            var currentDebt = 0.0
            val debtInterestRate = inputs.tassoInteresseDebito
            val daysPerMonth = 365.0 / 12.0
            val valoreSpesaMensileMaxUtilita = inputs.valoreSpesaGiornalieraMaxUtilita * daysPerMonth

            val mutuoFinoEta = surplusData.mutuoAffittoFinoEta
            val surplusLavorativaConMutuo = surplusData.calculateSurplusGiornalieroLavorativa(true)
            val surplusLavorativaSenzaMutuo = surplusData.calculateSurplusGiornalieroLavorativa(false)
            val surplusPensioneConMutuo = surplusData.calculateSurplusGiornalieroPensione(true)
            val surplusPensioneSenzaMutuo = surplusData.calculateSurplusGiornalieroPensione(false)
            val bonusLavoroGiornaliero = surplusData.bonusEventualiPersonaliMensile * 12 / 365.0
            val bonusPensioneGiornaliero = surplusData.bonusEventualiPersonaliPensioneMensile * 12 / 365.0
            var cumulativeUtilityOffset = 0.0

            val maxAge = inputs.etaMorte
            val expenseAmountByAge = DoubleArray(maxAge + 1)
            val expenseUtilityOffsetByAge = DoubleArray(maxAge + 1)
            for (expense in specificExpenses) {
                if (expense.age <= maxAge) {
                    expenseAmountByAge[expense.age] += expense.amount
                    expenseUtilityOffsetByAge[expense.age] += expense.utilityOffset
                }
            }

            val surplusGiornalieroByAge = DoubleArray(maxAge + 1)
            val ereditaTfrByAge = DoubleArray(maxAge + 1)

            for (eta in inputs.etaAttuale..maxAge) {
                var s = (when {
                    eta < inputs.etaPensione -> if (eta < mutuoFinoEta) surplusLavorativaConMutuo else surplusLavorativaSenzaMutuo
                    else -> if (eta < mutuoFinoEta) surplusPensioneConMutuo else surplusPensioneSenzaMutuo
                }) + surplusOffset

                if (eta < inputs.etaPensione) {
                    if (eta > surplusData.bonusEventualiPersonaliMensileFinoEta) {
                        s -= bonusLavoroGiornaliero
                    }
                } else {
                    if (eta > surplusData.bonusEventualiPersonaliPensioneMensileFinoEta) {
                        s -= bonusPensioneGiornaliero
                    }
                }
                surplusGiornalieroByAge[eta] = s

                val ereditaTfr = (if (eta == inputs.etaPensione) inputs.tfrNetto else 0.0) +
                    (if (eta == inputs.etaRicevimentoEredita) inputs.eredita else 0.0) -
                    expenseAmountByAge[eta]
                ereditaTfrByAge[eta] = ereditaTfr
            }

            fun spesaMinimaPerEta(eta: Int, utilityOffset: Double): Double {
                val fdeg = juneDegradoPerEta(eta, inputs).coerceAtLeast(1e-9)
                val requiredUtility = (inputs.sogliaMinimaFunzioneUtilita - utilityOffset).coerceAtLeast(0.0)
                if (requiredUtility <= 0.0) return 0.0
                val requiredRaw = (requiredUtility / fdeg).coerceIn(0.0, 1.0)
                val curve = inputs.utilityCurvePoints?.let(::juneSortedFiniteCurve)?.takeIf { it.size >= 2 }
                return if (curve != null) {
                    val dailyNeeded = juneInvertCurveX(curve, requiredRaw).coerceAtLeast(0.0)
                    val res = dailyNeeded * daysPerMonth
                    if (res.isFinite()) res else 0.0
                } else {
                    val q = requiredRaw.coerceIn(1e-6, 1.0 - 1e-6)
                    val scale = if (valoreSpesaMensileMaxUtilita > 0) valoreSpesaMensileMaxUtilita / Defaults.BASELINE_MAX_SPESA else 1.0
                    val x0 = Defaults.BASELINE_CENTER * scale
                    val k = Defaults.BASELINE_K / scale
                    val res = x0 + (1.0 / k) * ln(q / (1.0 - q))
                    if (res.isFinite()) res else 0.0
                }
            }

            fun forecastFinalWithMin(startCapital: Double, startAge: Int, startDebt: Double, startUtilityOffset: Double): Double {
                var cap = startCapital
                var debt = startDebt
                var utilityOffset = startUtilityOffset
                for (eta in startAge..inputs.etaMorte) {
                    utilityOffset += expenseUtilityOffsetByAge[eta]
                    val surplusM = surplusGiornalieroByAge[eta] * daysPerMonth
                    val smin = spesaMinimaPerEta(eta, utilityOffset)
                    var cashPool = cap + ereditaTfrByAge[eta] + (surplusM - smin) * 12
                    if (debt > 0) {
                        val debtInterest = debt * debtInterestRate
                        debt += debtInterest
                        val repayable = cashPool.coerceAtLeast(0.0)
                        val paid = min(repayable, debt)
                        cashPool -= paid
                        debt -= paid
                    }
                    if (cashPool < 0) {
                        debt += -cashPool
                        cashPool = 0.0
                    }
                    cap = cashPool * (1 + inputs.tassoGuadagnoInteresse)
                    if (!cap.isFinite()) return -1e15
                }
                return cap - debt
            }

            for (eta in inputs.etaAttuale..inputs.etaMorte) {
                val capitaleInizioAnno = capitaleAnnoPrecedente
                var debtRepayment = 0.0

                cumulativeUtilityOffset += expenseUtilityOffsetByAge[eta]

                val surplusGiornaliero = surplusGiornalieroByAge[eta]
                val surplusMensile = surplusGiornaliero * daysPerMonth
                val ereditaTfrAnno = ereditaTfrByAge[eta]

                var spesaMensileCorrettaFinale: Double

                val p2 = inputs.p2EtaFineRisparmioNoCapitale
                val p4 = max(inputs.p4EtaAnticipataInizioSpesaCapitale, p2)

                val quotaCapitaleSpesaAnnuale = if (eta == inputs.etaMorte) {
                    max(0.0, capitaleInizioAnno - inputs.soldiDaConservare)
                } else if (eta >= inputs.etaPensione) {
                    (capitaleInizioAnno - inputs.soldiDaConservare) / (inputs.etaMorte - eta)
                } else if (eta >= p4 && eta >= p2) {
                    inputs.p3PercentualeCapitaleDaSpendereAnnualmente * (capitaleInizioAnno - inputs.soldiDaConservare) / (inputs.etaMorte - eta)
                } else {
                    0.0
                }

                val spesaMensileSurplusNoCapitale =
                    if (eta < p2) surplusMensile * (1 - inputs.p1SavingRatioSurplus) else surplusMensile
                val spesaMensileSurplusCorretta = spesaMensileSurplusNoCapitale + quotaCapitaleSpesaAnnuale / 12

                val spesaMensileMinima = spesaMinimaPerEta(eta, cumulativeUtilityOffset)

                spesaMensileCorrettaFinale = min(
                    max(spesaMensileSurplusCorretta, spesaMensileMinima),
                    valoreSpesaMensileMaxUtilita
                )

                fun nextCapitalAndDebtFrom(spesaMensile: Double, capStart: Double, debtStart: Double): Pair<Double, Double> {
                    var cashPool = capStart + ereditaTfrAnno + (surplusMensile - spesaMensile) * 12
                    var debt = debtStart
                    if (debt > 0) {
                        val debtInterest = debt * debtInterestRate
                        debt += debtInterest
                        val repayable = cashPool.coerceAtLeast(0.0)
                        val paid = min(repayable, debt)
                        cashPool -= paid
                        debt -= paid
                    }
                    if (cashPool < 0) {
                        debt += -cashPool
                        cashPool = 0.0
                    }
                    val capNext = cashPool * (1 + inputs.tassoGuadagnoInteresse)
                    return capNext to debt
                }

                if (eta >= inputs.etaPensione && eta < inputs.etaMorte) {
                    val remainingIncludingThis = (inputs.etaMorte - eta + 1).toDouble()
                    if (remainingIncludingThis > 1.0) {
                        val i = inputs.tassoGuadagnoInteresse
                        val ceff = (capitaleInizioAnno + ereditaTfrAnno - currentDebt).coerceAtLeast(0.0)
                        val wReq = if (i > 0.0) {
                            ((ceff * (1.0 + i).pow(remainingIncludingThis) - inputs.soldiDaConservare) * i) /
                                ((1.0 + i).pow(remainingIncludingThis) - 1.0)
                        } else {
                            (ceff - inputs.soldiDaConservare) / remainingIncludingThis
                        }
                        val debtInterestAnnual = currentDebt * debtInterestRate
                        val spendReq = surplusMensile + (wReq - debtInterestAnnual) / 12.0
                        spesaMensileCorrettaFinale = min(
                            max(spendReq, spesaMensileMinima),
                            valoreSpesaMensileMaxUtilita
                        )
                        val (capNext, debtNext) = nextCapitalAndDebtFrom(spesaMensileCorrettaFinale, capitaleInizioAnno, currentDebt)
                        val finalIfMin = forecastFinalWithMin(capNext, eta + 1, debtNext, cumulativeUtilityOffset)
                        if (finalIfMin < inputs.soldiDaConservare + 1.0) {
                            spesaMensileCorrettaFinale = min(
                                spesaMensileMinima,
                                valoreSpesaMensileMaxUtilita
                            )
                        }
                    }
                }

                if (eta == inputs.etaMorte) {
                    val i = inputs.tassoGuadagnoInteresse
                    val ceff = (capitaleInizioAnno + ereditaTfrAnno - currentDebt).coerceAtLeast(0.0)
                    val wReq = (ceff - inputs.soldiDaConservare / (1.0 + i))
                    val debtInterestAnnual = currentDebt * debtInterestRate
                    val spendReq = surplusMensile + (wReq - debtInterestAnnual) / 12.0
                    spesaMensileCorrettaFinale = min(
                        max(spendReq, spesaMensileMinima),
                        valoreSpesaMensileMaxUtilita
                    )
                }

                val spesaAnnuale = spesaMensileCorrettaFinale * 12
                val netCashFlow = (surplusMensile * 12) - spesaAnnuale

                var cashPool = capitaleInizioAnno + ereditaTfrAnno + netCashFlow

                if (currentDebt > 0) {
                    val debtInterest = currentDebt * debtInterestRate
                    currentDebt += debtInterest
                    val repayable = cashPool.coerceAtLeast(0.0)
                    val totalPaid = min(repayable, currentDebt)
                    cashPool -= totalPaid
                    val interestPaid = min(totalPaid, debtInterest)
                    val principalPaid = totalPaid - interestPaid
                    debtRepayment = totalPaid
                    currentDebt -= totalPaid
                }

                if (cashPool < 0) {
                    currentDebt += -cashPool
                    cashPool = 0.0
                }

                val capitaleFineAnno = cashPool * (1 + inputs.tassoGuadagnoInteresse)
                val patrimonioNettoFineAnno = capitaleFineAnno - currentDebt

                val funzioneUtilitaRaw =
                    if (eta == inputs.etaMorte && patrimonioNettoFineAnno < 0.9 * inputs.soldiDaConservare) {
                        -100.0
                    } else {
                        juneUtilitaDaSpesa(eta, spesaMensileCorrettaFinale, inputs) + cumulativeUtilityOffset
                    }

                val funzioneUtilita = if (funzioneUtilitaRaw.isFinite()) funzioneUtilitaRaw else -1.0

                val savingRatioEffettivo = if (surplusMensile > 0.0) ((surplusMensile - spesaMensileSurplusNoCapitale) / surplusMensile) else 0.0

                val isLegacyFailure = (eta == inputs.etaMorte && patrimonioNettoFineAnno < (inputs.soldiDaConservare - 1.0))
                val violazioneLascito = isLegacyFailure

                val utilityAtThreshold = abs(spesaMensileCorrettaFinale - spesaMensileMinima) <= 1e-6

                simulationYears.add(
                    SimulationYear(
                        eta = eta,
                        capitaleInizioAnno = capitaleInizioAnno,
                        spesaMensileCorrettaFinale = spesaMensileCorrettaFinale,
                        funzioneUtilita = funzioneUtilita,
                        savingRatioEffettivo = savingRatioEffettivo,
                        violazioneLascito = violazioneLascito,
                        capitaleFineAnno = capitaleFineAnno,
                        utilityAtThreshold = utilityAtThreshold,
                        debtAmount = currentDebt,
                        debtRepayment = debtRepayment,
                        capitaleEroso = capitaleInizioAnno - capitaleFineAnno
                    )
                )

                capitaleAnnoPrecedente = capitaleFineAnno
                if (!capitaleAnnoPrecedente.isFinite()) throw ArithmeticException("Capitale non finito")
            }

            simulationYears
        } catch (e: Exception) {
            listOf(SimulationYear(eta = inputs.etaAttuale, funzioneUtilita = -1e9))
        }
    }

    private fun juneStdDev(numbers: List<Double>): Double {
        val finite = numbers.filter { it.isFinite() }
        if (finite.size < 2) return 0.0
        val mean = finite.average()
        val variance = finite.sumOf { (it - mean).pow(2) } / finite.size
        return sqrt(variance)
    }

    private data class JuneObjective(
        val fObjW: Double,
        val fObj0: Double,
        val rawAvg: Double,
        val rawStd: Double,
        val zeroed: Boolean
    )

    private fun juneObjectiveFromYears(years: List<SimulationYear>, bonusStdWeight: Double): JuneObjective {
        if (years.isEmpty()) return JuneObjective(0.0, 0.0, 0.0, 0.0, true)
        val utilities = years.map { it.funzioneUtilita }
        val avg = utilities.average()
        val std = juneStdDev(utilities)
        val zeroed = years.any { it.violazioneLascito } ||
            years.any { !it.funzioneUtilita.isFinite() } ||
            years.any { it.funzioneUtilita < 0 }
        if (zeroed || !avg.isFinite() || avg <= 0.0) {
            return JuneObjective(0.0, 0.0, avg, std, true)
        }
        val weight = bonusStdWeight / 100.0
        val stabilityTerm = if (std > 1e-9) avg / std else 100.0
        val fObjW = (avg + weight * stabilityTerm) / (1.0 + weight)
        return JuneObjective(fObjW, avg, avg, std, false)
    }

    // ---------- A/B test ----------

    @Test
    fun june_vs_current_landscape_ab_on_real_data() {
        assumeTrue("User prefs folder not available", prefsDir.isDirectory)
        val (inputs0, surplus, expenses) = loadUserScenario() ?: return
        val inputs = inputs0.withDefaultAssumptionCurves()
        val legacy = inputs.soldiDaConservare
        val p1Values = listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)
        val p2Values = listOf(50, 56, 62, 68, 74, 80)
        val weights = linkedSetOf(inputs.bonusStdWeight, 0.0, 1.0)
        val juneCapMonthly = inputs.valoreSpesaGiornalieraMaxUtilita * 365.0 / 12.0

        println("=== JUNE vs CURRENT A/B: ages ${inputs.etaAttuale}..${inputs.etaMorte}, pension ${inputs.etaPensione}, " +
            "capital ${"%.0f".format(inputs.capitaleIniziale)}, keep=${"%.0f".format(legacy)}, " +
            "P3=${"%.4f".format(inputs.p3PercentualeCapitaleDaSpendereAnnualmente)}, P4=${inputs.p4EtaAnticipataInizioSpesaCapitale}, " +
            "T=${inputs.sogliaMinimaFunzioneUtilita}, juneCap=${"%.1f".format(juneCapMonthly)} EUR/m ===")

        for (w in weights) {
            println("=== w=$w ===")
            val curFobj = mutableListOf<Double>()
            val jnFobj = mutableListOf<Double>()
            val curAvg = mutableListOf<Double>()
            val jnAvg = mutableListOf<Double>()
            for (p2 in p2Values) {
                for (p1 in p1Values) {
                    val cell = inputs.copy(
                        p1SavingRatioSurplus = p1,
                        p2EtaFineRisparmioNoCapitale = p2
                    )
                    val curYears = calculateSimulation(cell, expenses, surplus)
                    val curObj = calculateObjectivesFromYears(curYears, bonusStdWeight = w, legacyTarget = legacy)
                    val jnYears = juneCalculateSimulation(cell, expenses, surplus)
                    val jnObj = juneObjectiveFromYears(jnYears, w)
                    curFobj.add(curObj.fObjW)
                    jnFobj.add(jnObj.fObjW)
                    curAvg.add(curObj.avgUtilita)
                    jnAvg.add(jnObj.rawAvg)
                    val jnCapFrac = jnYears.count { it.spesaMensileCorrettaFinale >= juneCapMonthly - 1e-6 }
                        .toDouble() / jnYears.size.coerceAtLeast(1)
                    println(
                        "P1=${"%.1f".format(p1)} P2=$p2 | cur fobj=${"%.4f".format(curObj.fObjW)} avg=${"%.4f".format(curObj.avgUtilita)} " +
                            "| jn fobj=${"%.4f".format(jnObj.fObjW)} avg=${"%.4f".format(jnObj.rawAvg)} " +
                            "cap%=${"%.2f".format(jnCapFrac)} z=${jnObj.zeroed}"
                    )
                }
            }
            fun spread(v: List<Double>) = if (v.isEmpty()) 0.0 else v.max() - v.min()
            println(
                "--- w=$w SUMMARY: spread cur-fobj=${"%.4f".format(spread(curFobj))} jn-fobj=${"%.4f".format(spread(jnFobj))} " +
                    "cur-avg=${"%.4f".format(spread(curAvg))} jn-avg=${"%.4f".format(spread(jnAvg))} ==="
            )
        }

        val probe = juneCalculateSimulation(inputs, expenses, surplus)
        assertTrue(probe.isNotEmpty())
        assertTrue(calculateSimulation(inputs, expenses, surplus).isNotEmpty())
    }
}
