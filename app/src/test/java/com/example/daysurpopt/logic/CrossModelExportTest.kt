// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.floor
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossModelExportTest {

    @Test
    fun exportSharedScenarioGrid() {
        val scenarioPathProp = System.getProperty("crossModelScenarioPath")
            ?: System.getenv("CROSS_MODEL_SCENARIO_PATH")
            ?: return
        val outputDirProp = System.getProperty("crossModelOutputDir")
            ?: System.getenv("CROSS_MODEL_OUTPUT_DIR")
            ?: return

        val scenarioPath = Path.of(scenarioPathProp)
        val outputDir = Path.of(outputDirProp)

        require(Files.exists(scenarioPath)) { "Scenario file not found: $scenarioPath" }
        Files.createDirectories(outputDir)

        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create()
        val scenario: CrossModelScenario = gson.fromJson(
            String(Files.readAllBytes(scenarioPath)),
            CrossModelScenario::class.java
        )

        val (inputs, surplusInput, expenses) = scenario.toAndroidModel()
        val rows = mutableListOf<GridRow>()
        val summaries = mutableListOf<WeightSummary>()

        scenario.grid.weights.forEach { weight ->
            var best: GridRow? = null
            for (p2Age in scenario.grid.p2Ages) {
                val p4Age = maxOf(scenario.policy.p4BaseAge.toInt(), p2Age)
                for (p1 in scenario.grid.p1Values) {
                    val candidate = inputs.copy(
                        p1SavingRatioSurplus = p1,
                        p2EtaFineRisparmioNoCapitale = p2Age,
                        p3PercentualeCapitaleDaSpendereAnnualmente = scenario.policy.p3,
                        p4EtaAnticipataInizioSpesaCapitale = p4Age,
                        bonusStdWeight = weight
                    )
                    val years = calculateSimulation(candidate, expenses, surplusInput)
                    val objectives = calculateObjectivesFromYears(years, weight, candidate.soldiDaConservare)
                    val row = GridRow(
                        weight = weight,
                        p1 = p1,
                        p2Age = p2Age,
                        p4Age = p4Age,
                        objective = objectives.fObjW,
                        avgUtility = objectives.avgUtilita,
                        stdDev = objectives.stdDev,
                        stabilityScore = objectives.stabilityIndex
                    )
                    rows += row
                    if (best == null || row.objective > best!!.objective) {
                        best = row
                    }
                }
            }
            summaries += WeightSummary(
                weight = weight,
                bestP1 = best!!.p1,
                bestP2Age = best!!.p2Age,
                bestP4Age = best!!.p4Age,
                bestObjective = best!!.objective,
                bestAvgUtility = best!!.avgUtility,
                bestStdDev = best!!.stdDev,
                bestStabilityScore = best!!.stabilityScore
            )
        }

        val csvPath = outputDir.resolve("android_cross_model_grid.csv")
        val summaryPath = outputDir.resolve("android_cross_model_summary.json")
        Files.write(csvPath, buildCsv(rows).toByteArray())
        Files.write(summaryPath, gson.toJson(summaries).toByteArray())

        assertTrue(rows.isNotEmpty())
        assertTrue(summaries.all { it.bestObjective.isFinite() })
    }

    private fun buildCsv(rows: List<GridRow>): String {
        val header = "weight,p1,p2_age,p4_age,objective,avg_utility,std_dev,stability_score"
        val body = rows.joinToString("\n") { row ->
            listOf(
                row.weight,
                row.p1,
                row.p2Age,
                row.p4Age,
                row.objective,
                row.avgUtility,
                row.stdDev,
                row.stabilityScore
            ).joinToString(",")
        }
        return "$header\n$body\n"
    }
}

private data class CrossModelScenario(
    val scenarioName: String,
    val model: ScenarioModel,
    val surplus: List<ScenarioSurplusBand>,
    val utilityCurve: List<ScenarioPoint>,
    val ageCurve: List<ScenarioPoint>,
    val oneTimeExpenses: List<ScenarioExpense>,
    val policy: ScenarioPolicy,
    val grid: ScenarioGrid,
    val numerics: ScenarioNumerics
) {
    fun toAndroidModel(): Triple<FinancialInput, SurplusInput, List<SpecificExpense>> {
        val workBands = surplus.filter { it.endAge <= model.retirementAge }
        val retirementBands = surplus.filter { it.startAge >= model.retirementAge }

        require(workBands.size in 1..2) { "Expected 1 or 2 work surplus bands" }
        require(retirementBands.size == 1) { "Expected exactly 1 retirement surplus band" }

        val workBase = workBands.last().monthlyEur
        val workBonus = if (workBands.size == 2) workBands.first().monthlyEur - workBase else 0.0
        require(workBonus >= -1e-9) { "Work bonus must be non-negative" }

        val bonusUntilAge = if (workBands.size == 2) {
            floor(workBands.first().endAge).toInt() - 1
        } else {
            0
        }

        val inputs = FinancialInput(
            eredita = model.inheritanceEur,
            soldiDaConservare = model.terminalBequestEur,
            tfrNetto = model.tfrEur,
            tassoGuadagnoInteresse = model.realAnnualReturn,
            tassoInteresseDebito = model.realAnnualDebtInterest,
            sogliaMinimaFunzioneUtilita = model.minimumUtilityThreshold,
            capitaleIniziale = model.initialCapitalEur,
            valoreSpesaGiornalieraMaxUtilita = utilityCurve.maxOf { it.x },
            utilityCurvePoints = utilityCurve.map { CurvePoint(it.x, it.y) },
            degradationCurvePoints = ageCurve.map { CurvePoint(it.x, it.y) },
            etaAttuale = model.startAge,
            etaPensione = model.retirementAge,
            etaRicevimentoEredita = model.inheritanceAge,
            etaMorte = model.deathAge,
            p1SavingRatioSurplus = grid.p1Values.first(),
            p2EtaFineRisparmioNoCapitale = grid.p2Ages.first(),
            p3PercentualeCapitaleDaSpendereAnnualmente = policy.p3,
            p4EtaAnticipataInizioSpesaCapitale = policy.p4BaseAge.toInt(),
            bonusStdWeight = grid.weights.first()
        )

        val surplusInput = SurplusInput(
            stipendioMensile = workBase,
            premioRisultatoNettoAnnuale = 0.0,
            tredicesimaQuattordicesimaNetto = 0.0,
            bonusEventualiPersonaliMensile = workBonus,
            bonusEventualiPersonaliMensileFinoEta = bonusUntilAge,
            pensioneMensileNetta = retirementBands.single().monthlyEur,
            tredicesimaQuattordicesimaNettoPensione = 0.0,
            bonusEventualiPersonaliPensioneMensile = 0.0,
            bonusEventualiPersonaliPensioneMensileFinoEta = 0,
            altreEntrateMensiliPensione = 0.0,
            mutuoAffitto = 0.0,
            mutuoAffittoFinoEta = 0,
            condominioLavorativa = 0.0,
            bolletteLavorativa = 0.0,
            ciboLavorativa = 0.0,
            veicoliLavorativa = 0.0,
            palestraLavorativa = 0.0,
            trasportiViaggiLavorativa = 0.0,
            saluteLavorativa = 0.0,
            vacanzeLavorativa = 0.0,
            shoppingLavorativa = 0.0,
            altroLavorativa = 0.0,
            condominioPensione = 0.0,
            bollettePensione = 0.0,
            ciboPensione = 0.0,
            veicoliPensione = 0.0,
            palestraPensione = 0.0,
            trasportiViaggiPensione = 0.0,
            salutePensione = 0.0,
            vacanzePensione = 0.0,
            shoppingPensione = 0.0,
            altroPensione = 0.0
        )

        val expenses = oneTimeExpenses.map { expense ->
            require(expense.age == expense.age.toInt().toDouble()) { "Expense age must be integral for Android mapping" }
            SpecificExpense(
                age = expense.age.toInt(),
                amount = expense.amount,
                utilityOffset = expense.utilityOffset
            )
        }

        return Triple(inputs, surplusInput, expenses)
    }
}

private data class ScenarioModel(
    val startAge: Int,
    val deathAge: Int,
    val retirementAge: Int,
    val initialCapitalEur: Double,
    val inheritanceEur: Double,
    val inheritanceAge: Int,
    val tfrEur: Double,
    val tfrAge: Int,
    val terminalBequestEur: Double,
    val realAnnualReturn: Double,
    val realAnnualDebtInterest: Double,
    val minimumUtilityThreshold: Double,
    val daysPerMonth: Double
)

private data class ScenarioSurplusBand(
    val startAge: Double,
    val endAge: Double,
    val monthlyEur: Double
)

private data class ScenarioPoint(
    val x: Double,
    val y: Double
)

private data class ScenarioExpense(
    val age: Double,
    val amount: Double,
    val utilityOffset: Double
)

private data class ScenarioPolicy(
    val p3: Double,
    val p4BaseAge: Double
)

private data class ScenarioGrid(
    val weights: List<Double>,
    val p1Values: List<Double>,
    val p2Ages: List<Int>
)

private data class ScenarioNumerics(
    val stdEpsilon: Double
)

private data class GridRow(
    val weight: Double,
    val p1: Double,
    val p2Age: Int,
    val p4Age: Int,
    val objective: Double,
    val avgUtility: Double,
    val stdDev: Double,
    val stabilityScore: Double
)

private data class WeightSummary(
    val weight: Double,
    val bestP1: Double,
    val bestP2Age: Int,
    val bestP4Age: Int,
    val bestObjective: Double,
    val bestAvgUtility: Double,
    val bestStdDev: Double,
    val bestStabilityScore: Double
)
