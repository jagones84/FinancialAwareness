// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

import kotlin.math.exp
import java.util.Locale

data class CurvePoint(
    val x: Double,
    val y: Double
)

data class FinancialInput(
    val eredita: Double = Defaults.DEFAULT_EREDITA,
    val soldiDaConservare: Double = Defaults.DEFAULT_SOLDI_DA_CONSERVARE,
    val tfrNetto: Double = Defaults.DEFAULT_TFR_NETTO,
    val tassoGuadagnoInteresse: Double = Defaults.DEFAULT_TASSO_INTERESSE,
    val tassoInteresseDebito: Double = Defaults.DEFAULT_TASSO_INTERESSE_DEBITO,
    val sogliaMinimaFunzioneUtilita: Double = Defaults.DEFAULT_SOGLIA_UTILITA,
    val capitaleIniziale: Double = Defaults.DEFAULT_CAPITALE_INIZIALE,
    val valoreSpesaGiornalieraMaxUtilita: Double = Defaults.DEFAULT_MAX_SPESA_GIORNALIERA_UTILITA,
    val utilityCurvePoints: List<CurvePoint>? = null,
    val degradationCurvePoints: List<CurvePoint>? = null,
    val etaAttuale: Int = Defaults.DEFAULT_ETA_ATTUALE,
    val etaPensione: Int = Defaults.DEFAULT_ETA_PENSIONE,
    val etaRicevimentoEredita: Int = Defaults.DEFAULT_ETA_EREDITA,
    val etaMorte: Int = Defaults.DEFAULT_ETA_MORTE,
    val p1SavingRatioSurplus: Double = Defaults.DEFAULT_P1_SAVING_RATIO_SURPLUS,
    val p2EtaFineRisparmioNoCapitale: Int = Defaults.DEFAULT_P2_ETA_FINE_RISPARMIO_NO_CAPITALE,
    val p3PercentualeCapitaleDaSpendereAnnualmente: Double = Defaults.DEFAULT_P3_PERC_CAPITALE_SPESA_ANNUALE,
    val p4EtaAnticipataInizioSpesaCapitale: Int = Defaults.DEFAULT_P4_ETA_ANTICIPATA_INIZIO_SPESA_CAPITALE,
    val bonusStdWeight: Double = Defaults.DEFAULT_BONUS_STD_WEIGHT
) {
    fun withDefaultAssumptionCurves(): FinancialInput {
        val hasUtility = (utilityCurvePoints?.size ?: 0) >= 2
        val hasDegradation = (degradationCurvePoints?.size ?: 0) >= 2
        if (hasUtility && hasDegradation) return this

        val utility = if (hasUtility) utilityCurvePoints else defaultUtilityCurvePoints(valoreSpesaGiornalieraMaxUtilita)
        val degradation = if (hasDegradation) degradationCurvePoints else defaultDegradationCurvePoints()

        return copy(
            utilityCurvePoints = utility,
            degradationCurvePoints = degradation
        )
    }

    private fun defaultUtilityCurvePoints(maxDaily: Double): List<CurvePoint> {
        val maxSafe = maxDaily.coerceAtLeast(1.0)
        val xs = listOf(0.0, 0.1, 0.2, 0.35, 0.5, 0.7, 0.85, 1.0)
            .map { (it * maxSafe).coerceIn(0.0, maxSafe) }
            .distinct()
            .sorted()

        val baselineMax = Defaults.BASELINE_MAX_SPESA
        val baselineCenter = Defaults.BASELINE_CENTER
        val baselineK = Defaults.BASELINE_K

        val targetMonthly = maxSafe * (365.0 / 12.0)
        val scale = if (targetMonthly > 0) targetMonthly / baselineMax else 1.0
        val x0 = baselineCenter * scale
        val k = baselineK / scale

        fun rawUtilityFromMonthlySpending(monthly: Double): Double {
            val u = 1.0 / (1.0 + exp(-k * (monthly - x0)))
            return u.coerceIn(0.0, 1.0)
        }

        return xs.map { daily ->
            val y = if (daily <= 0.0) 0.0 else rawUtilityFromMonthlySpending(daily * (365.0 / 12.0))
            CurvePoint(x = daily, y = y)
        }
    }

    private fun defaultDegradationCurvePoints(): List<CurvePoint> {
        fun baseDegradation(eta: Int): Double {
            return 0.3 + (1 - 0.3) / (1 + exp(0.15 * (eta - 55)))
        }

        return (30..90 step 10)
            .map { age -> CurvePoint(x = age.toDouble(), y = baseDegradation(age).coerceIn(0.0, 1.0)) }
    }
}

data class FinancialInputUI(
    val eredita: String = "0.00",
    val soldiDaConservare: String = "0.00",
    val tfrNetto: String = "0.00",
    val tassoGuadagnoInteresse: String = "0.0000",
    val tassoInteresseDebito: String = "0.0000",
    val sogliaMinimaFunzioneUtilita: String = "0.0000",
    val capitaleIniziale: String = "0.00",
    val valoreSpesaGiornalieraMaxUtilita: String = "0.00",
    val etaAttuale: String = "30",
    val etaPensione: String = "67",
    val etaRicevimentoEredita: String = "50",
    val etaMorte: String = "90",
    val p1SavingRatioSurplus: String = "0.5000",
    val p2EtaFineRisparmioNoCapitale: String = "67",
    val p3PercentualeCapitaleDaSpendereAnnualmente: String = "0.0400",
    val p4EtaAnticipataInizioSpesaCapitale: String = "67",
    val bonusStdWeight: String = "0.15"
) {
    companion object {
        fun from(inputs: FinancialInput) = FinancialInputUI(
            eredita = String.format(Locale.US, "%.2f", inputs.eredita),
            soldiDaConservare = String.format(Locale.US, "%.2f", inputs.soldiDaConservare),
            tfrNetto = String.format(Locale.US, "%.2f", inputs.tfrNetto),
            tassoGuadagnoInteresse = String.format(Locale.US, "%.4f", inputs.tassoGuadagnoInteresse),
            tassoInteresseDebito = String.format(Locale.US, "%.4f", inputs.tassoInteresseDebito),
            sogliaMinimaFunzioneUtilita = String.format(Locale.US, "%.4f", inputs.sogliaMinimaFunzioneUtilita),
            capitaleIniziale = String.format(Locale.US, "%.2f", inputs.capitaleIniziale),
            valoreSpesaGiornalieraMaxUtilita = String.format(Locale.US, "%.2f", inputs.valoreSpesaGiornalieraMaxUtilita),
            etaAttuale = inputs.etaAttuale.toString(),
            etaPensione = inputs.etaPensione.toString(),
            etaRicevimentoEredita = inputs.etaRicevimentoEredita.toString(),
            etaMorte = inputs.etaMorte.toString(),
            p1SavingRatioSurplus = String.format(Locale.US, "%.4f", inputs.p1SavingRatioSurplus),
            p2EtaFineRisparmioNoCapitale = inputs.p2EtaFineRisparmioNoCapitale.toString(),
            p3PercentualeCapitaleDaSpendereAnnualmente = String.format(Locale.US, "%.4f", inputs.p3PercentualeCapitaleDaSpendereAnnualmente),
            p4EtaAnticipataInizioSpesaCapitale = inputs.p4EtaAnticipataInizioSpesaCapitale.toString(),
            bonusStdWeight = String.format(Locale.US, "%.2f", inputs.bonusStdWeight)
        )
    }
}
