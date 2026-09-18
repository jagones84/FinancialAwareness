# README — Goal Solver (capital needed)

Frozen, tested architecture of the Goal Solver domain. Procedures here are verified by the
current test suite (see "Diagnosis commands" — all green as of 2026-08-05).

## What it answers

"How much capital do I need TODAY to quit work at age X and never let happiness (utility) drop
below threshold T?" The answer is a **LOCUS / TABLE**, not a single number: for every saving
ratio P1 used while still working there is a different minimum initial capital.

## Formal problem statement (fixed vs free parameters)

User-fixed constants θ: current age a0, death age ad, stop-work age X, income streams and
their end dates, scheduled expenses / inheritance / TFR schedules, interest rates, utility
and degradation curves, bequest target L, happiness threshold T (the user sets T and X).

Goal-plan shape (fixed by the solver's semantics, NOT free): etaPensione = P2 = P4 = X,
P3 = 0 (the floor solves the per-month capital draw).

Free unknowns (2): initial capital C0 and saving ratio P1.

The solver has NO model of its own: the OFFICIAL simulation S is its only oracle (zero
simplifications). S(P1, C0) returns the monthly utility history {u\_m}, the year-end debts
{d\_y} and the death net worth NW. Feasibility:

```
Feas(P1, C0)  <=>  min_m u_m >= T - 1e-6  AND  max_y d_y <= 1e-6  AND  NW >= L - 1
```

(the utility clause is a defensive mirror: the engine floor keeps u >= T at ANY capital,
the shortfall becoming debt — the BINDING clauses are solvency and bequest).

For each P1 the solver computes the exact feasibility boundary:

```
C*(P1) = inf { C0 >= 0 : Feas(P1, C0) }
```

by bisection on the oracle (2 boundary checks + ceil(log2(3e6/1000)) = 12 steps = 14
official-simulation launches per row; capital tolerance 1000 EUR). The answer is the LOCUS

```
Λ = { (P1, C*(P1)) : P1 ∈ [0,1] }
```

— the boundary curve of the feasible region: "P1 as the limit value beyond which the
simulation breaks", evaluated for EVERY P1 (strictly more information than one limit pair).

Test-proven properties:

1. **Grazing**: at (P1, C\*(P1)) the history touches the threshold, min\_m u\_m = T up to
   machine precision (−5.55e-17); below C\* − 2×tolerance the plan is infeasible.
2. **Monotonicity**: C\*(·) is non-increasing in P1 (more saving → less capital needed).
3. **Plateau**: where the floor binds over the whole post-stop horizon, net accumulation
   is surplus − minSpend (P1-independent) ⇒ C\* flat (user's real data: plateau from 30%).
4. **Capital-neutrality above the boundary**: for C0 ≥ C\*(P1) the utility history is
   IDENTICAL; only the bequest grows (P3 = 0 floor never spends the surplus).

Deliberately NOT inside the solver: max Fobj over P1. That is a DIFFERENT question — the
Optimization tab answers it with the free GA over P1..P4 (no threshold goal, no min-capital).
Mixing them needs a lexicographic formulation: stage 1 = the locus (this solver), stage 2 =
choose the row maximizing Fobj. The selectable table IS stage 1 and leaves stage 2 to the
user; a per-row Fobj column would automate stage 2 (not implemented yet).

## Locus chart (C\_I vs P1)

`GoalSolverDialog` renders the solver's answer also as a 2D Plotly chart (same stack as the
Pareto chart): x = P1 (%), y = minimum capital C\*(P1) as a cyan line from the feasible rows;
the user's CURRENT simulation position (its own P1 + actual initial capital) is a RED
marker — above the curve = the current plan already satisfies the goal, below = capital
missing. Pure model `logic/GoalLocusChartModel.kt` (GoalLocusChartModelBuilder, unit-tested:
feasible rows only, sorted by P1, P1 in percent, marker P1 coerced to \[0,1], marker kept
even when every row is infeasible); spec via `PlotlySpecBuilder.buildMultiLineJson`
(`LineTraceSpec` gained a `pointSize` param, default 4 — the red marker uses 12). The chart
is hidden when no feasible row exists; the dialog body became scrollable (chart + table
exceed small screens). Strings: goal\_solver\_chart\_title / chart\_axis\_p1 / chart\_axis\_capital /
trace\_locus / trace\_current (en/it/es).

COMPACT LAYOUT (fix after user review "grafico schiacciato per la legenda"): the locus spec
uses `buildMultiLineJson(layoutOverrides = ..., xTickAngle = 0)` — legend INSIDE the plot
(horizontal, top-right — the locus descends to the right so that corner is always free),
margins 44/8/8/30, x range fixed 0..100, straight ticks, `meta staticPlot: true` (no stuck
hover tooltip, no gesture conflicts with the scrollable dialog). The default builder layout
is UNCHANGED (Pareto full-screen style). Both behaviors locked by
`PlotlySpecBuilderLayoutTest` (enabled by `testOptions.unitTests.isReturnDefaultValues = true`
in app/build.gradle.kts, so builders calling android.util.Log run in JVM tests).

WEBVIEW SIZING FIX (second user review: "non vedo l'asse delle ascisse"): the bottom of the
chart was CLIPPED — Plotly rendered before the WebView reached its final size inside the
dialog (onPageFinished can fire pre-layout; `lastRenderedSpec` then blocked any re-render).
Fix in `PlotlyHtmlProvider` (benefits ALL charts): (1) each render pins
`layout.width/height` to the CURRENT div `clientWidth/clientHeight` — the SVG can never
overflow its container; (2) a `ResizeObserver` re-renders when the container settles/changes
(>2px delta, guarded by `__renderDone`); (3) staticPlot charts always use `Plotly.newPlot`
(full redraw). JS is inside the HTML template — not unit-testable in the JVM suite; verified
by build + device visual check.

FINAL DECISION — NATIVE CHART (third user review, WebView still clipped): the Plotly/WebView
stack inside a dialog proved too fragile (3 failed rounds), so the locus chart is now drawn
NATIVELY in Compose: `GoalLocusChart` (private composable in FinancialCalculatorScreen.kt,
Canvas 200dp + Compose legend Row) — axes, grid, ticks (x 0/25/50/75/100%, y nice steps),
locus polyline, dots and red marker, no WebView involved, no sizing race possible. Geometry:
`logic/GoalLocusChartGeometry.yAxisTicks` (unit-tested: starts at 0, nice 1/2/2.5/5 x 10^n
steps \~5 divisions, headroom above max). The Plotly stack remains for the full-screen charts
(Pareto/surfaces/heatmaps); the PlotlyHtmlProvider sizing fixes from the previous round stay
(they harden those charts too). Strings unchanged.

PROBE (user request: "il mouse che prende X Y data sopra la curva"): hovering with a mouse
or touching/dragging on the canvas snaps a probe to the nearest plotted point (locus rows +
current marker, nearest by P1): dashed vertical guideline, white ring on the dot, tooltip
"P1 <n>% · <capital> €" clamped inside the plot. Touch drags are consumed so the scrollable
dialog does not fight the probe. Pure selection: `GoalLocusChartGeometry.nearestProbePoint`
(unit-tested).

## Architecture (all engine-faithful — no closed-form simplifications)

- `GoalSolverLogic.solveMinimumInitialCapital(...)` — bisection on `capitaleIniziale` over
  \[0, 3,000,000] with tolerance 1,000 €. Feasibility = official engine
  (`calculateSimulation`): no debt in any month, no legacy violation
  (`soldiDaConservare` respected), every monthly utility sample >= T.

- `GoalSolverLogic.solveCapitalVsSavingRatio(...)` — the locus: sweeps P1 over
  0%..100% step 10% (+ exact extra row for the user's current P1 when off-grid,
  `GoalSweepRow.isCurrentPlan`), solving one bisection per row.
  The locus is NON-INCREASING in P1: while the utility floor does not bind, more saving =
  same spending = more capital at the stop age; once the floor binds, net monthly
  accumulation is `surplus − minSpend`, independent of P1.

- `buildGoalWhatIfInputs(base, T, X, capital, p1?)` — the goal plan shape:
  `etaPensione = p2 = p4 = X`, `p3 = 0`, `sogliaMinimaFunzioneUtilita = T`, curves preserved.

- `buildGoalApplyInputs(...)` — what Apply installs: the WHOLE plan (two overloads:
  from a single `GoalSolverResult`, or from a `GoalSweepRow` = P1 + threshold + stop age).

- ViewModel: `goalSweepResult: GoalSweepResult?` state, `runGoalSolver(X, T)`,
  `applyGoalSolverPlan(row)`. Dialog: selectable radio table (P1 % | capital today |
  "(current)" | "not reachable"), Apply enabled only on feasible rows.

- Agent tool `RUN_RETIREMENT_SOLVER` answers for the user's CURRENT P1 only; the GUI table
  covers the whole locus.

## Semantics golden rules

1. `p3 = 0` does NOT mean "never spend capital". After the stop age the engine funds the
   utility minimum FROM CAPITAL via `finalSpend = max(baseSpend, minimumSpend)` eroding it.
2. The yearly spent amount is an unknown solved MONTH BY MONTH by the floor
   (`minSpend(age) = utility-curve inverse of threshold / degradation(age)`). A single
   percentage P3 cannot express it (age-varying, circular in remaining capital), and for the
   minimum-capital question spending exactly the floor is optimal. So `(P1, capital_i)` is
   the complete locus; yearly amounts are derived quantities.
3. `P2 = P4 = stopWorkAge` by design in the goal plan (saving stops when work stops; draws
   may start then). In the user's own plans P2/P4 stay free (GA-optimized).
4. The optimizer CAN reach a higher score with the same capital via a less conservative plan
   (no per-sample floor, may spend above the minimum) — a different question, not an error.
5. The threshold must be <= `utilityCurveCeiling × minDegradation` (validateThreshold);
   otherwise all rows are infeasible and `maxAchievableUtility` explains the ceiling.
6. Any input change clears `goalSweepResult` (stale sweeps are never shown).

## Diagnosis commands

```powershell
.\gradlew testDebugUnitTest --tests "com.example.daysurpopt.logic.GoalSolverLogicTest" --tests "com.example.daysurpopt.logic.GoalSolverApplyTest" --tests "com.example.daysurpopt.logic.GoalSolverSweepTest"
```

Key locks: `solveMinimumCapital_matchesReferenceBisectionScenario` (\~275,390 € @ stop 40 /
T 0.3 / degradation floor 0.5); `applied_goal_plan_satisfies_goal_in_official_simulation`
(applied plan = goal reproduced by the engine); `applied_goal_plan_spends_capital_exactly_at_
the_threshold_after_stop` (post-stop monthly utility == T within 1e-6, capital actually spent);
`sweep_returns_monotone_locus_over_p1_grid`; `sweep_row_matches_single_solve_for_same_p1`.

### Real-data sanity check (opt-in, no personal data in the repo)

`UserRealDataCheckTest` reads the user's actual SharedPreferences (extracted from the device
into an external folder — default `C:\WINDOWS\TEMP\fa_prefs`, override with `FA_PREFS_DIR`;
skips via assumeTrue when absent) and runs the current-plan simulation plus the locus with and
without scheduled expenses. Validated 2026-09-01: locus monotone with the expected P1 plateau
(floor binding), scheduled expenses proved to count (best row 167,725 € with vs 70,313 €
without, delta +97,412 €), current plan feasible (avg utility 0.2192, final capital 50,024 €
≈ legacy 50,000 €).

### Cross-validation harness (`GoalSolverCrossValidationTest`)

Independent proof that the solver and the OFFICIAL engine agree on ARBITRARY parameter sets
(user request 2026-09-01, after self-verifying on his own data). 4 scenarios, P1 ∈ {0, 0.3, 0.7, 1.0}:

| Scenario                                    | C\*(P1=0) | C\*(0.3) | C\*(0.7) | C\*(1.0)             |
| ------------------------------------------- | --------- | -------- | -------- | -------------------- |
| S1 early-stop-no-income 40/40/82 T0.30      | 269,531   | 269,531  | 269,531  | 269,531 (flat locus) |
| S2 mid-career-rent-pension 42/58/82 T0.25   | 7,324     | 0        | 0        | 0                    |
| S3 high-rates-late-death 35/50/90 T0.28 i5% | 13,184    | 0        | 0        | 0                    |
| S4 late-start-low-threshold 50/60/85 T0.15  | 78,369    | 31,494   | 0        | 0                    |

Per row the test asserts: (1) at C\* the engine satisfies the goal (all monthly samples ≥ T,
no debt, no legacy violation); (2) at C\*−2×tolerance the engine violates; (3) at C\*+50k the
utility time-history is IDENTICAL (maxDiff < 1e-9 — P3=0 floor never spends surplus) and the
final bequest is higher. Test 2 re-validates the ENTIRE feasible S2 locus (11 rows). All green.

### Random cross-validation (`GoalSolverRandomCrossValidationTest`)

User-requested harness: seeded-random input sets (SEED 20260901, 12 draws) -> full GUI
pipeline (sweep -> random P1 row applied -> official engine time-history). Whenever C\* > 0
the applied plan MUST hit a binding constraint; with C\* = 0 income covers the goal and
nothing grazes. Observed on seed 20260901: 4x UTILITY graze with min(sample) - T =
-5.55e-17 (machine-exact touch), 1x DEBT graze (min year-end net worth 533 EUR vs
compounded slack 2,296 EUR), 7x trivially feasible.

ENGINE FACTS discovered while building it (golden rules):

1. **Utility NEVER falls below T at any capital.** `finalSpend = max(baseSpend, minimumSpend)`
   (SimulationLogic) - the floor always spends at least the threshold amount; the shortfall
   becomes DEBT (10%/y). So the solver's per-sample utility check is a defensive mirror;
   the REAL feasibility constraints are year-end debt and the death bequest.
2. **violazioneLascito is death-only**: `month == monthCount - 1` is the last month of the
   WHOLE plan (not of each year). `(capital - debt) < soldiDaConservare - 1.0`.
3. **During the plan the legacy is guarded by the three-branch draw** (2026-09-02, REVISION 2):
   pre-pension (from max(P4, P2)) the p3 quota applies to (netWorth - legacy); in retirement the
   draw is a p3-SCALED sustainable annuity PMT(netWorth, yearsLeft, legacy) with the POST-DRAW
   forecast brake (`forecastFinalWithMinimumSpend < legacy + 1 -> draw 0`). Contract: p3 = 0
   -> NO capital draw (buildGoalWhatIfInputs relies on it — the ungated annuity broke 19 tests).
4. Three binding modes at C\* (graze = the constrained quantity touches its boundary within
   the bisection slack compounded at the plan rate): UTILITY (floor binds, min sample == T),
   LEGACY (final net worth grazes legacy), DEBT (min year-end net worth grazes 0).
   With P1 = 100% the floor binds PRE-stop (saving the whole salary means living on capital
   while working) - correct engine behavior, seen in random draw R7.
5. **Spend is capped at the utility-curve plateau start** (2026-09-02,
   `computeMaxUtilityMonthlySpend`): voluntary spend cannot exceed the smallest curve x whose
   y reaches the curve max — beyond it extra spending buys zero utility and only wastes capital
   (`finalSpend = max(min(baseSpend, cap), minimumSpend)`; the threshold floor stays
   unconditional). Consequence: **C\* values computed before 2026-09-02 are stale — they can
   only decrease** (less waste -> less capital required). The fobj is graded: legacy violations
   score `base - (1.0 + 2.5 x shortfallRatio)` (separation: every violator < 0 <= every feasible).
6. **Landscape heatmap colors anchor on the feasible band** (2026-09-02, REVISION 3):
   `SurfaceGrid.anchorColorScaleOnFeasible` makes the 2D heatmap/contour zmin/zmax start at the
   lowest feasible cell (z >= 0); legacy violators (negative fobj) clamp to the bottom color
   instead of stretching the color scale over the penalty range (which rendered the whole
   feasible band as ONE color — the "gran quadratone piatto"). Delta grids and the 3D surface
   keep the raw scale. A/B evidence (`JuneEngineLandscapeABTest`): the June engine (dc1e7a0) is
   EQUALLY floor-pinned on today's real data (feasible avg spread ~0.013 vs current 0.042) —
   the landscape's physical contrast is limited by the data (scheduled expenses + legacy vs
   capital), not by the engine.
7. **The threshold T controls the landscape richness, not just the floor** (2026-09-02, THE
   ARCANUM, found by the user): `minimumSpend(T)` sets where the floor bites —
   **P1\* = 1 - minSpend(T)/surplus**. Measured degradation curve on real data (42yo, surplus
   1,258 EUR/mo): T=0.2 -> minSpend 853, P1*=0.32, spread 0.024, 6 violators; T=0.16 -> 731,
   P1*=0.42, spread 0.068; T=0.12 -> 608, P1*=0.52, spread 0.104; T=0.1 -> 538, P1*=0.57,
   spread 0.123, ALL plans feasible. Mechanism: floor-years contribute EXACTLY T for every
   (P1,P2) (a growing constant that dilutes the average), the responsive spend band
   [minSpend(T), surplus] narrows, and the byte-identical plateau boundary falls. A higher T
   both raises the guaranteed quality of life AND flattens the fobj landscape; no w can recover
   the contrast (fObj is a monotone reshape of the same compressed avg). When the heatmap "goes
   flat", check `sogliaMinimaFunzioneUtilita` FIRST. Regression guards:
   `richSurplusData_produces_nonFlat_p1Landscape` (engine can be rich) + `ARCANUM CHECK` block
   in `user_real_data_fobj_landscape_diagnostic` (T sweep 0.2/0.16/0.12/0.1).

## Rollback

The locus UI is additive over the single-solve core: `solveMinimumInitialCapital` and
`buildGoalWhatIfInputs` are unchanged public API. To revert the sweep dialog, restore the
single `GoalSolverResult` state in `FinancialViewModel` + the previous `GoalSolverDialog`
body (git history, part 7/8 of WORKFLOW\.md) — the logic layer keeps working either way.
