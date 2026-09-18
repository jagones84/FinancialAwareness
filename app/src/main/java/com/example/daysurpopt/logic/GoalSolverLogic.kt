// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Goal Solver: answers the inverse question
 * "how much capital do I need to accumulate so that I can stop working at age X
 * while keeping my happiness (utility) at or above threshold T?".
 *
 * Semantics (mapped onto existing engine primitives, no new persistent inputs):
 *  - "Stop working at X" -> etaPensione = X: the work-income bucket stops at X and
 *    the pension income of the given [SurplusInput] applies afterwards. Pass a
 *    zero-income SurplusInput for a pure capital-based plan.
 *  - "Happiness >= T" -> sogliaMinimaFunzioneUtilita = T. The engine already forces
 *    the minimum monthly spend that achieves T, drawing capital (and creating debt
 *    once capital is exhausted).
 *  - The what-if plan forces p3 = 0 (spend exactly the utility minimum, no extra
 *    percentage draw) and p2 = p4 = stopWorkAge.
 *  - "Feasible" -> no debt in any year, no legacy violation, every utility sample >= T.
 *
 * Feasibility is monotone in initial capital (more capital can only relax the
 * constraints under the same spending rule), so a bisection on capitaleIniziale
 * converges to the exact minimum.
 */
object GoalSolverLogic {

    const val DEFAULT_CAPITAL_TOLERANCE = 1000.0
    const val DEFAULT_CAPITAL_UPPER_BOUND = 3_000_000.0

    private const val UTILITY_EPSILON = 1e-6
    private const val DEBT_EPSILON = 1e-6

    /**
     * Builds the goal what-if inputs used by the solver: stops work at [stopWorkAge],
     * applies the happiness [threshold] and places [initialCapital] as starting capital.
     * All other assumptions (rates, curves, expenses, surplus, legacy) come from the base.
     */
    fun buildGoalWhatIfInputs(
        baseInputs: FinancialInput,
        threshold: Double,
        stopWorkAge: Int,
        initialCapital: Double,
        p1SavingRatio: Double? = null
    ): FinancialInput {
        return baseInputs
            .withDefaultAssumptionCurves()
            .copy(
                p1SavingRatioSurplus = p1SavingRatio ?: baseInputs.p1SavingRatioSurplus,
                etaPensione = stopWorkAge,
                p2EtaFineRisparmioNoCapitale = stopWorkAge,
                p4EtaAnticipataInizioSpesaCapitale = stopWorkAge,
                p3PercentualeCapitaleDaSpendereAnnualmente = 0.0,
                sogliaMinimaFunzioneUtilita = threshold,
                capitaleIniziale = initialCapital
            )
    }

    /**
     * Builds the inputs to install when the user APPLIES the solver result:
     * the full goal plan (stop work at the solved age, save until it, spend
     * exactly the utility minimum) with the required initial capital — not
     * just the capital. Applying the capital alone would run the user's own
     * plan shape and contradict the solver's promise.
     */
    fun buildGoalApplyInputs(
        baseInputs: FinancialInput,
        result: GoalSolverResult
    ): FinancialInput {
        val capital = result.requiredCapital
            ?: throw IllegalArgumentException("Cannot apply an infeasible goal result: ${result.reason}")
        return buildGoalWhatIfInputs(baseInputs, result.threshold, result.stopWorkAge, capital)
    }

    /**
     * Applies one row of the P1 sweep: installs that row's saving ratio together
     * with its required capital and the whole goal plan shape.
     */
    fun buildGoalApplyInputs(
        baseInputs: FinancialInput,
        threshold: Double,
        stopWorkAge: Int,
        row: GoalSweepRow
    ): FinancialInput {
        val capital = row.requiredCapital
            ?: throw IllegalArgumentException("Cannot apply an infeasible sweep row (P1 = ${row.p1}).")
        return buildGoalWhatIfInputs(baseInputs, threshold, stopWorkAge, capital, row.p1)
    }

    /**
     * The goal answer is a LOCUS, not a number: for every saving ratio P1 used
     * while still working there is a different minimum initial capital that lets
     * the user quit at [stopWorkAge] and never drop below [threshold] (and still
     * respect the bequest target soldiDaConservare). Higher P1 -> more saving
     * before quitting -> less capital needed today, so the locus is
     * non-increasing. The user's current P1 always gets an exact row
     * ([GoalSweepRow.isCurrentPlan]), even when it falls between grid points.
     */
    fun solveCapitalVsSavingRatio(
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        stopWorkAge: Int,
        threshold: Double,
        capitalTolerance: Double = DEFAULT_CAPITAL_TOLERANCE,
        capitalUpperBound: Double = DEFAULT_CAPITAL_UPPER_BOUND,
        p1Step: Double = 0.1
    ): GoalSweepResult {
        val validation = validateThreshold(baseInputs, threshold)
        val currentP1 = baseInputs.p1SavingRatioSurplus.coerceIn(0.0, 1.0)
        val steps = round(1.0 / p1Step).toInt().coerceIn(1, 100)
        val gridStep = 1.0 / steps
        val grid = (0..steps).map { it.toDouble() / steps }
        val nearCurrent: (Double) -> Boolean = { abs(it - currentP1) < gridStep / 4.0 }
        val p1Values = if (grid.any(nearCurrent)) grid else grid + currentP1

        val rows = p1Values.sorted().map { p1 ->
            if (!validation.isAchievable) {
                GoalSweepRow(p1, null, false, nearCurrent(p1))
            } else {
                val result = solveMinimumInitialCapital(
                    baseInputs = baseInputs.copy(p1SavingRatioSurplus = p1),
                    specificExpenses = specificExpenses,
                    surplusData = surplusData,
                    stopWorkAge = stopWorkAge,
                    threshold = threshold,
                    capitalTolerance = capitalTolerance,
                    capitalUpperBound = capitalUpperBound
                )
                GoalSweepRow(p1, result.requiredCapital, result.isFeasible, nearCurrent(p1))
            }
        }

        return GoalSweepResult(threshold, stopWorkAge, validation.maxAchievableUtility, rows)
    }

    /**
     * Validates that [threshold] is achievable at all: the maximum achievable utility is
     * the utility-curve ceiling times the minimum degradation over the simulated ages.
     * With default curves the ceiling is ~0.9347 (the baseline logistic at
     * BASELINE_MAX_SPESA never reaches 1.0), so thresholds above ~0.295 at age 82 are
     * unreachable regardless of capital.
     */
    fun validateThreshold(baseInputs: FinancialInput, threshold: Double): GoalValidation {
        val normalized = baseInputs.withDefaultAssumptionCurves()
        val ceiling = normalized.utilityCurvePoints
            ?.filter { it.x.isFinite() && it.y.isFinite() }
            ?.maxOfOrNull { it.y }
            ?.coerceIn(0.0, 1.0)
            ?: 1.0
        val minDegradation = (normalized.etaAttuale..normalized.etaMorte)
            .minOfOrNull { funzioneDegradoPerEta(it, normalized) }
            ?.coerceIn(0.0, 1.0)
            ?: 1.0
        val maxAchievable = (ceiling * minDegradation).coerceIn(0.0, 1.0)
        return GoalValidation(
            isAchievable = threshold <= maxAchievable,
            maxAchievableUtility = maxAchievable,
            utilityCurveCeiling = ceiling,
            minDegradation = minDegradation
        )
    }

    /**
     * Bisection on the initial capital. Returns a [GoalSolverResult] whose
     * [GoalSolverResult.requiredCapital] is the minimum feasible capital (never worse
     * than [capitalTolerance] from the true minimum), or null with a human-readable
     * [GoalSolverResult.reason] when the goal cannot be reached.
     */
    fun solveMinimumInitialCapital(
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        stopWorkAge: Int,
        threshold: Double,
        capitalTolerance: Double = DEFAULT_CAPITAL_TOLERANCE,
        capitalUpperBound: Double = DEFAULT_CAPITAL_UPPER_BOUND
    ): GoalSolverResult {
        val validation = validateThreshold(baseInputs, threshold)

        fun infeasible(reason: String): GoalSolverResult =
            GoalSolverResult(
                requiredCapital = null,
                isFeasible = false,
                reason = reason,
                threshold = threshold,
                stopWorkAge = stopWorkAge,
                maxAchievableUtility = validation.maxAchievableUtility
            )

        if (stopWorkAge < baseInputs.etaAttuale || stopWorkAge >= baseInputs.etaMorte) {
            return infeasible(
                "Invalid stop-work age $stopWorkAge: it must be between the current age " +
                    "${baseInputs.etaAttuale} and the death age ${baseInputs.etaMorte}."
            )
        }

        if (threshold <= 0.0) {
            return GoalSolverResult(
                requiredCapital = 0.0,
                isFeasible = true,
                reason = null,
                threshold = threshold,
                stopWorkAge = stopWorkAge,
                maxAchievableUtility = validation.maxAchievableUtility
            )
        }

        if (!validation.isAchievable) {
            return infeasible(
                "Happiness threshold " + "%.4f".format(Locale.US, threshold) +
                    " is above the maximum achievable utility " +
                    "%.4f".format(Locale.US, validation.maxAchievableUtility) +
                    " (utility curve ceiling " + "%.4f".format(Locale.US, validation.utilityCurveCeiling) +
                    " x minimum degradation " + "%.4f".format(Locale.US, validation.minDegradation) +
                    "). Lower the threshold or edit the utility/degradation curves."
            )
        }

        fun simulate(capital: Double): List<SimulationYear> =
            calculateSimulation(
                buildGoalWhatIfInputs(baseInputs, threshold, stopWorkAge, capital),
                specificExpenses,
                surplusData
            )

        fun isFeasible(years: List<SimulationYear>): Boolean {
            if (years.isEmpty()) return false
            return years.all { year ->
                year.debtAmount <= DEBT_EPSILON &&
                    !year.violazioneLascito &&
                    year.funzioneUtilita >= threshold - UTILITY_EPSILON &&
                    year.monthlyUtilitySamples.all { it >= threshold - UTILITY_EPSILON }
            }
        }

        val low = 0.0
        val high = capitalUpperBound
        if (!isFeasible(simulate(high))) {
            return infeasible(
                "Even a capital of " + "%.0f".format(Locale.US, high) +
                    " EUR cannot sustain the goal with the current plan. " +
                    "Reduce the threshold, increase income, or extend the savings phase."
            )
        }
        if (isFeasible(simulate(low))) {
            return GoalSolverResult(0.0, true, null, threshold, stopWorkAge, validation.maxAchievableUtility)
        }

        var lo = low
        var hi = high
        while (hi - lo > capitalTolerance) {
            val mid = (lo + hi) / 2.0
            if (isFeasible(simulate(mid))) hi = mid else lo = mid
        }

        return GoalSolverResult(hi, true, null, threshold, stopWorkAge, validation.maxAchievableUtility)
    }
}

data class GoalValidation(
    val isAchievable: Boolean,
    val maxAchievableUtility: Double,
    val utilityCurveCeiling: Double,
    val minDegradation: Double
)

data class GoalSolverResult(
    val requiredCapital: Double?,
    val isFeasible: Boolean,
    val reason: String?,
    val threshold: Double,
    val stopWorkAge: Int,
    val maxAchievableUtility: Double
)

/**
 * One point of the (P1, capital_i) locus: [requiredCapital] is the minimum
 * initial capital today that allows quitting at the stop-work age with saving
 * ratio [p1] while never dropping below the happiness threshold. Null when
 * even the upper-bound capital cannot sustain that row.
 */
data class GoalSweepRow(
    val p1: Double,
    val requiredCapital: Double?,
    val isFeasible: Boolean,
    val isCurrentPlan: Boolean
)

data class GoalSweepResult(
    val threshold: Double,
    val stopWorkAge: Int,
    val maxAchievableUtility: Double,
    val rows: List<GoalSweepRow>
)
