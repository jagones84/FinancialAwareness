# Graded Cost Function Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the binary zero-out objective with the old-style graded penalties and restore the max-utility spend cap, so the fobj landscape over P1-P4 is smooth and discriminative instead of flat-with-cliffs.

**Architecture:** Two independent changes in `SimulationLogic.kt`: (1) `calculateObjectivesFromYears` keeps the bounded formula `Avg*((1-w)+w*Stability)` and replaces the "any violation -> 0.0" rule with an additive graded penalty `100.0/planYears` for legacy violations, letting finite negative utility samples flow into the average; (2) the monthly loop caps voluntary spend at the utility-curve saturation point, with the threshold floor still unconditional. Goal-solver feasibility reads engine outputs directly and is untouched.

**Tech Stack:** Kotlin, JUnit4, Gradle (`:app:testDebugUnitTest`).

**Spec:** `docs/superpowers/specs/2026-09-02-graded-cost-function-design.md`

**NOTE on commits:** this project's rules forbid commits without an explicit user request. Steps below verify behavior instead of committing; at the end, ask the user whether to commit.

---

### Task 1: Graded objective (replace binary zero-outs)

**Files:**
- Modify: `app/src/main/java/com/example/daysurpopt/logic/SimulationLogic.kt` (constants near line 77; function `calculateObjectivesFromYears` lines 405-463)
- Test: `app/src/test/java/com/example/daysurpopt/logic/SimulationLogicTest.kt`

- [ ] **Step 1: Write the failing tests** — append inside `class SimulationLogicTest` (e.g. after `utilityWithOffset_stays_bounded_and_fobj_never_exceeds_one`):

```kotlin
    @Test
    fun legacyViolation_gets_graded_penalty_instead_of_zero() {
        val violatingYears = listOf(
            SimulationYear(
                eta = 40, funzioneUtilita = 0.5, capitaleFineAnno = 1000.0,
                monthlyUtilitySamples = List(12) { 0.5 }
            ),
            SimulationYear(
                eta = 41, funzioneUtilita = 0.5, capitaleFineAnno = 0.0,
                monthlyUtilitySamples = List(12) { 0.5 }, violazioneLascito = true
            )
        )
        val violating = calculateObjectivesFromYears(violatingYears, bonusStdWeight = 1.0, legacyTarget = 500.0)
        assertEquals(0.5 - 100.0 / 2, violating.fObjW, 1e-9)
        assertEquals(0.5 - 100.0 / 2, violating.fObj0, 1e-9)
        assertFalse(violating.isFeasible)

        val feasibleYears = violatingYears.map { it.copy(violazioneLascito = false) }
        val feasible = calculateObjectivesFromYears(feasibleYears, bonusStdWeight = 1.0, legacyTarget = 500.0)
        assertEquals(0.5, feasible.fObjW, 1e-9)
        assertTrue(violating.fObjW < feasible.fObjW)
    }

    @Test
    fun legacyViolation_penalty_preserves_gradient_between_plans() {
        fun yearsWith(sample: Double): List<SimulationYear> = listOf(
            SimulationYear(
                eta = 40, funzioneUtilita = sample, capitaleFineAnno = 0.0,
                monthlyUtilitySamples = List(12) { sample }, violazioneLascito = true
            )
        )
        val low = calculateObjectivesFromYears(yearsWith(0.3), bonusStdWeight = 1.0, legacyTarget = 0.0)
        val high = calculateObjectivesFromYears(yearsWith(0.6), bonusStdWeight = 1.0, legacyTarget = 0.0)
        assertEquals(0.3 - 100.0, low.fObjW, 1e-9)
        assertEquals(0.6 - 100.0, high.fObjW, 1e-9)
        assertTrue(high.fObjW > low.fObjW)
        assertTrue(low.fObjW < 0.0 && high.fObjW < 0.0)
    }

    @Test
    fun negativeFiniteSamples_flowIntoGradedAverage() {
        val years = listOf(
            SimulationYear(
                eta = 40, funzioneUtilita = 0.2, capitaleFineAnno = 0.0,
                monthlyUtilitySamples = listOf(0.4, -0.2)
            )
        )
        val result = calculateObjectivesFromYears(years, bonusStdWeight = 0.0, legacyTarget = 0.0)
        assertEquals(0.1, result.avgUtilita, 1e-9)
        assertEquals(0.1, result.fObjW, 1e-9)
        assertFalse(result.isFeasible)
    }

    @Test
    fun exceptionSentinel_stillForcesZeroObjective() {
        val years = listOf(
            SimulationYear(
                eta = 40, funzioneUtilita = -1e9, capitaleFineAnno = 0.0,
                monthlyUtilitySamples = listOf(-1e9)
            )
        )
        val result = calculateObjectivesFromYears(years, bonusStdWeight = 1.0, legacyTarget = 0.0)
        assertEquals(0.0, result.fObjW, 0.0)
        assertEquals(0.0, result.fObj0, 0.0)
    }
```

(`SimulationYear`, `calculateObjectivesFromYears`, `assertFalse`, `assertTrue`, `assertEquals` are already imported in this test file via `org.junit.Assert.*` and the logic package.)

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.example.daysurpopt.logic.SimulationLogicTest 2>&1 | Select-Object -Last 25`
Expected: FAIL — `legacyViolation_gets_graded_penalty_instead_of_zero`, `legacyViolation_penalty_preserves_gradient_between_plans` and `negativeFiniteSamples_flowIntoGradedAverage` fail on `expected X but was 0.0` (the current binary zero-out); `exceptionSentinel_stillForcesZeroObjective` passes already.

- [ ] **Step 3: Implement the graded objective** — in `SimulationLogic.kt`:

(a) Add the constants next to the existing `STD_EPSILON` block (line ~77):

```kotlin
private const val DEATH_LEGACY_PENALTY = 100.0
private const val UTILITY_SENTINEL_ABS = 1e6
```

(b) Replace the WHOLE `calculateObjectivesFromYears` function with:

```kotlin
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
    val planYears = years.size.coerceAtLeast(1)
    val legacyPenalty = if (legacyViolated) DEATH_LEGACY_PENALTY / planYears else 0.0

    val fObjW = computeObjective(avgUtilita, stdDevUtilita, bonusStdWeight) - legacyPenalty
    if (legacyViolated) {
        AppDebugLog.add("SimLogic", "Graded legacy penalty: fObjW=$fObjW (penalty=$legacyPenalty, years=$planYears)")
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
```

Notes: the old `avgUtilita <= 0.0 -> 0` guard is intentionally removed (graded negatives); `abs` is available via `import kotlin.math.*`; `computeObjective`, `computeStabilityScore`, `calculateStandardDeviation`, `AppDebugLog` already exist in this file. The penalty is subtracted from BOTH `fObjW` and `fObj0` so `deltaFObjW`/`deltaFObj0` comparisons stay a pure weight effect.

- [ ] **Step 4: Run the test class to verify green**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.example.daysurpopt.logic.SimulationLogicTest 2>&1 | Select-Object -Last 12`
Expected: BUILD SUCCESSFUL — 18 tests (14 previous + 4 new), 0 failures.

---

### Task 2: Max-utility spend cap (engine guard)

**Files:**
- Modify: `app/src/main/java/com/example/daysurpopt/logic/SimulationLogic.kt` (new helper near `calculateStandardDeviation`; monthly loop line ~357-359)
- Test: `app/src/test/java/com/example/daysurpopt/logic/SimulationLogicTest.kt`

- [ ] **Step 1: Write the failing tests** — append inside `class SimulationLogicTest`:

```kotlin
    @Test
    fun computeMaxUtilityMonthlySpend_defaultSigmoid_usesDailyMax() {
        val inputs = FinancialInput(valoreSpesaGiornalieraMaxUtilita = 150.0)
        assertEquals(150.0 * (365.25 / 12.0), computeMaxUtilityMonthlySpend(inputs), 1e-9)
    }

    @Test
    fun computeMaxUtilityMonthlySpend_curveCapsAtPlateauStart() {
        val inputs = FinancialInput(
            valoreSpesaGiornalieraMaxUtilita = 150.0,
            utilityCurvePoints = listOf(
                CurvePoint(x = 0.0, y = 0.2),
                CurvePoint(x = 100.0, y = 0.9),
                CurvePoint(x = 200.0, y = 0.9)
            )
        )
        assertEquals(100.0 * (365.25 / 12.0), computeMaxUtilityMonthlySpend(inputs), 1e-9)
    }

    @Test
    fun computeMaxUtilityMonthlySpend_singlePointCurve_fallsBackToDailyMax() {
        val inputs = FinancialInput(
            valoreSpesaGiornalieraMaxUtilita = 150.0,
            utilityCurvePoints = listOf(CurvePoint(x = 10.0, y = 0.9))
        )
        assertEquals(150.0 * (365.25 / 12.0), computeMaxUtilityMonthlySpend(inputs), 1e-9)
    }

    @Test
    fun spendCap_limitsVoluntarySpending_toMaxUtilitySpend() {
        val inputs = FinancialInput(
            p1SavingRatioSurplus = 0.0,
            p2EtaFineRisparmioNoCapitale = 30,
            p3PercentualeCapitaleDaSpendereAnnualmente = 1.0,
            p4EtaAnticipataInizioSpesaCapitale = 30,
            etaAttuale = 30,
            etaPensione = 67,
            etaMorte = 90,
            soldiDaConservare = 50000.0,
            capitaleIniziale = 10_000_000.0,
            valoreSpesaGiornalieraMaxUtilita = 10.0,
            sogliaMinimaFunzioneUtilita = 0.1
        )
        val years = calculateSimulation(inputs, emptyList(), SurplusInput())
        val cap = computeMaxUtilityMonthlySpend(inputs)
        assertTrue(years.isNotEmpty())
        assertEquals(cap, years.first().spesaMensileCorrettaFinale, 1e-6)
        val samples = years.first().monthlyUtilitySamples
        assertTrue(samples.isNotEmpty())
        assertEquals(0.0, samples.max() - samples.min(), 1e-9)
    }

    @Test
    fun spendCap_floorWins_whenMinimumSpendExceedsCap() {
        val inputs = FinancialInput(
            p1SavingRatioSurplus = 0.0,
            p2EtaFineRisparmioNoCapitale = 30,
            p3PercentualeCapitaleDaSpendereAnnualmente = 1.0,
            p4EtaAnticipataInizioSpesaCapitale = 30,
            etaAttuale = 30,
            etaPensione = 67,
            etaMorte = 90,
            soldiDaConservare = 50000.0,
            capitaleIniziale = 10_000_000.0,
            valoreSpesaGiornalieraMaxUtilita = 10.0,
            sogliaMinimaFunzioneUtilita = 0.95
        )
        val years = calculateSimulation(inputs, emptyList(), SurplusInput())
        val cap = computeMaxUtilityMonthlySpend(inputs)
        assertTrue(years.isNotEmpty())
        assertTrue(years.first().spesaMensileCorrettaFinale > cap)
        val samples = years.first().monthlyUtilitySamples
        assertTrue(samples.min() >= 0.95 - 1e-6)
    }
```

(`CurvePoint` comes from `com.example.daysurpopt.domain` — add the import `import com.example.daysurpopt.domain.CurvePoint` if not already present; `FinancialInput`, `SurplusInput`, `calculateSimulation` are already imported. Numeric grounding: default sigmoid at spend = daily-max x 30.4375 gives u = 0.9347 exactly (Defaults.BASELINE_*), threshold 0.95 needs requiredRaw = 0.95/0.984 = 0.966 > 0.9347 so the floor spend exceeds the cap; threshold 0.1 needs a negative raw spend so the floor is 0 and the cap binds.)

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.example.daysurpopt.logic.SimulationLogicTest 2>&1 | Select-Object -Last 25`
Expected: FAIL — unresolved reference `computeMaxUtilityMonthlySpend` (compile error counts as RED), and the two engine tests would fail on the cap assertions.

- [ ] **Step 3: Implement the helper and the cap** — in `SimulationLogic.kt`:

(a) Add the public helper right after `calculateStandardDeviation` (line ~75):

```kotlin
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
```

(b) Inside `calculateSimulation`, compute the cap once before the month loop (next to `val debtMonthlyRate = ...`, line ~264):

```kotlin
        val maxUtilSpendMonthly = computeMaxUtilityMonthlySpend(inputs)
```

(c) Change the spend rule (line ~357-359) from:

```kotlin
            val baseSpend = (spendSurplus + draw).coerceAtLeast(0.0)
            val minimumSpend = spesaMinimaPerEta(age, cumulativeUtilityOffset, inputs)
            val finalSpend = max(baseSpend, minimumSpend)
```

to:

```kotlin
            val baseSpend = (spendSurplus + draw).coerceAtLeast(0.0)
            val minimumSpend = spesaMinimaPerEta(age, cumulativeUtilityOffset, inputs)
            val finalSpend = max(min(baseSpend, maxUtilSpendMonthly), minimumSpend)
```

The cap trims the VOLUNTARY spend only; the floor stays unconditional (goal-solver semantics preserved: floor-funded threshold, shortfall becomes debt).

- [ ] **Step 4: Run the test class to verify green**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.example.daysurpopt.logic.SimulationLogicTest 2>&1 | Select-Object -Last 12`
Expected: BUILD SUCCESSFUL — 23 tests, 0 failures.

---

### Task 3: Full verification, ripple check and docs

**Files:**
- Verify: whole suite + APK build
- Modify: `HANDOFF.md`, `.agent/memory/activeContext.md`, `.agent/README-goal-solver.md`

- [ ] **Step 1: Run the FULL unit suite**

Run: `.\gradlew.bat :app:testDebugUnitTest 2>&1 | Select-Object -Last 15`
Expected: BUILD SUCCESSFUL — 143 tests / 0 failures / 1 opt-in skip (138 previous + 4 objective + 5 cap tests = 147 total? count from the actual XML summary; record the real numbers).

Count precisely from the XML results and record the real figures.

- [ ] **Step 2: Ripple checks on locked behavior**

If any of these fail, the expectation (not the engine) is wrong — update the test to the graded/capped semantics and note it in the HANDOFF entry:
- `GoalSolverCrossValidationTest` / `GoalSolverRandomCrossValidationTest`: solver coherence assertions are dynamic (C* recomputed per run) — must still pass; C* values may DROP (cap preserves capital). The three binding modes still hold (floor untouched).
- `applied_goal_plan_spends_capital_exactly_at_the_threshold_after_stop` characterization: goal plans spend at the floor (below the cap) — unchanged.
- `SensitivityAvgUtilityTest`, `CrossModelExportTest`, Pareto tests: dynamic assertions — must pass.
- `UserRealDataCheckTest` (opt-in, runs on this machine): structural assertions only — must pass with different C* numbers.

- [ ] **Step 3: Build the APK**

Run: `.\gradlew.bat :app:assembleDebug 2>&1 | Select-Object -Last 5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Update docs (same session, per project rules)**

- `HANDOFF.md`: new section `## 0g. Graded cost function + spend cap (user request, 2026-09-02)` — problem (flat/stepped landscape), the two changes, verification counts, visible consequences (C* down, negative fobj for violators).
- `.agent/memory/activeContext.md`: item 26 summarizing the change + updated Verification counts.
- `.agent/README-goal-solver.md`: add a note under the ENGINE FACTS golden rules: "spend is capped at the utility-curve plateau start (curve max-y x); C* values computed before 2026-09-02 are stale (they can only decrease)".

- [ ] **Step 5: Ask the user whether to commit**

Per project rules, commits happen only on explicit request. Present the changed-file list and ask.

---

## Self-Review (done at plan time)

- **Spec coverage:** graded penalty + guard (Task 1), cap + floor priority + curve-aware rule incl. the max-y amendment (Task 2), full suite + ripple + docs (Task 3). Formula unchanged, solver untouched (explicit non-goals — nothing to implement).
- **Placeholders:** none — all code complete; the only open figure is the exact final test count, which Step 3.1 records from the actual XML.
- **Type consistency:** `computeMaxUtilityMonthlySpend(inputs: FinancialInput): Double` used identically in Tasks 2 steps; `DEATH_LEGACY_PENALTY` / `UTILITY_SENTINEL_ABS` defined in Task 1 and used in the same task.
