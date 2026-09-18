# FinancialAwareness — HANDOFF (2026-08-05)

Complete audit + handoff for the next agent. The audit pass added tests only
(`AgentToolParityTest`, `PromptDocumentationTest`, `RetirementCapitalSolverTest`); the follow-up
TDD pass applied the fixes — see section 0. Full unit suite (122 tests) and `assembleDebug` are green.

**Domain manuals (read BEFORE working on these domains — frozen, tested procedures):**
[.agent/README-goal-solver.md](.agent/README-goal-solver.md) ·
[.agent/README-sensitivity.md](.agent/README-sensitivity.md) ·
[.agent/README-gui-state-wiring.md](.agent/README-gui-state-wiring.md)

***

## 0h. License change (2026-09-05): MIT → AGPL-3.0-or-later

- **User request:** prevent anyone from copying the app and republishing/selling it on the
  Play Store.
- `LICENSE` replaced with the official GNU AGPL-3.0 text (gnu.org `agpl-3.0.txt`, 34,523
  bytes / 661 lines / LF, verified). Rollback: `git checkout -- LICENSE`.
- All **100 `.kt` files** under `app/src` now start with the header
  `// SPDX-License-Identifier: AGPL-3.0-or-later` + `// Copyright (c) 2026 jagones84`
  (per-file BOM/EOL style preserved; verified programmatically — 100/100 carry the header).
- `README.md` gained a License section stating the AGPL-3.0-or-later terms.
- **Effect:** any redistribution (including publishing a derivative app on a store, with or
  without modifications) must release the complete corresponding source under the same
  license; network-service use also triggers the source obligation (AGPL §13).
- Post-change verification: full unit suite + `assembleDebug` green (see Verification).

## 0. Fix status after the TDD application pass (2026-08-05)

All fixes were applied **red-first** (failing test → implementation → green → full suite + build).

- **F4 FIXED** — `AgentPrompts.getRiskPrompt` now teaches `StabilityScore = Avg / (Avg + StdDev)` and
  `fScalar = Avg * ((1 - w) + w * StabilityScore)`; stale texts are forbidden by `AgentPromptsTest`.

- **F5 FIXED** — system prompt documents `FETCH_PAGE` and the `sogliaMinimaFunzioneUtilita` override
  (`PromptDocumentationTest`).

- **F1a FIXED** — Surplus removed from the bottom bar, re-added as the first button of Section 1
  (Data Input & Setup) on the home screen.

- **F1b FIXED** — `QuickStartDialog` wired: auto-shown after privacy consent on first launch
  (`QuickStartRepository` flag), reopenable via a Star button in the home top bar.

- **Goal Solver (flagship) DONE** — `logic/GoalSolverLogic.kt` (bisection, ceiling validation F7,
  monotonicty tests, reference 275,390.63 € @ threshold 0.3), GUI "Goal Solver (Capital Needed)"
  button + dialog in Section 3 with Apply (writes capital through the standard `updateInputs`
  path: persist + re-simulate + clear stale analysis), localized en/it/es. `RetirementCapitalSolverTest`
  superseded and deleted by `GoalSolverLogicTest`.

- **Agent tool** **`RUN_RETIREMENT_SOLVER`** **DONE** — same `GoalSolverLogic`, same JSON override semantics
  as `RUN_SIMULATION`, ceiling-aware infeasibility reason, documented in the prompt.

- **F6 FIXED (agent↔GUI optimization parity)** — `RUN_OPTIMIZATION` now uses the user's `GAConfigUI`
  (via `buildAgentGaConfig` → `OptimizationLogic.parseGaConfig`) with optional JSON overrides
  `popSize/generations/pc/pm`; new `mode` parameter supports `TRUE_SCALAR` / `PARETO_KNEE` /
  `PARETO_FRONT` (same Pareto logic as the GUI); comparison context (`COMPARISON MODE ACTIVE`) is
  built by `FinancialViewModel.buildComparisonContextForAgent` in compare mode and injected into the
  multi-agent sustainability/risk prompts (`isComparing`).

- **Agent sensitivity access DONE** — new tool `RUN_SENSITIVITY` wraps the GUI's
  `OptimizationLogic.runSensitivityAnalysis` with the same override semantics, readable ranked
  output (`AgentSensitivityToolTest`), documented in the prompt.

- **Multi-agent grounding DONE (2026-08-05, part 4)** — the Sustainability/Risk agents previously
  received only raw inputs and hallucinated monetary figures (contradictory legacy math, "eliminate
  debt" on a debt-free plan, P1 benchmarked against income-based stats). `AgentToolExecutor.buildMultiAgentFinancialContext`
  now injects REAL engine results (objective, avg utility, std dev, stability, final capital, monthly
  surplus/saving breakdown, actual debt years) plus P1 surplus semantics into the shared context;
  Master prompt forbids invented numbers. Double execution of `RUN_MULTI_AGENT_ANALYSIS` in one turn
  blocked by `alreadyExecutedCommands` guard in `checkForToolUse` + `extractCommandName` tracking in
  `AgentViewModel` + "at most once per user request" prompt instruction (`AgentMultiAgentContextTest`).

- **Still open (by design, low priority):** F2 (in-context age validation in the Surplus form — the
  Goal Solver dialog validates its own ages), F3 partial (threshold/ceiling now surfaced via the
  Goal Solver dialog, not in the main results card), optional "apply optimization results" agent
  write-back tool, PDF/charts/profile-management agent tools.

- Verification: `testDebugUnitTest` 116 tests / 0 failures / 0 errors (1 opt-in skip); `assembleDebug` green.

## 0b. TO VALIDATE — assumption curves access & web research (user request, 2026-08-05)

Status of the agent's access to the curves edited in the **Setup (Assumptions) tab** — the utility
curve (utility vs extra daily spending, x = EUR/day) and the degradation curve (decay with age,
x = age) — and of its web-research capability:

- **READ — implemented & validated.** `GET_FINANCIAL_CONTEXT` returns `effectiveCurves` with the
  engine-effective points (defaults materialized when the user has no custom curve). Unit-tested
  (`AgentCurveAccessTest`) and validated end-to-end with a real LLM: the agent quoted y = 0.3037 at
  age 90, engine-exact.

- **EDIT — what-if only; persistence NOT available (to validate with the user).** The agent can test
  curve modifications via `RUN_SIMULATION` overrides (`utilityCurvePoints`, `degradationCurvePoints`)
  — parity locked by tests and exercised end-to-end (Final Capital 75,711 € with a flatter
  degradation curve). There is **no write-back tool**: curve changes are NOT persisted to the Setup
  tab; the agent is instructed to tell the user to apply them manually. If persistence is desired,
  a new tool (e.g. `SET_ASSUMPTIONS`) writing through the ViewModel/Repository path is needed.

- **WEB RESEARCH — implemented, e2e still to validate.** `WEB_SEARCH` (DuckDuckGo HTML via jsoup,
  no API key) and `FETCH_PAGE` are available and documented for curve-shaping research. Not yet
  exercised in a real-harness run: DuckDuckGo HTML can be rate-limited/blocked from mobile networks
  — validate on-device and consider a fallback engine if unreliable.

- **OpenRouter config (user question: "è settato in env?").** NO key exists in env vars or
  `local.properties` (only `sdk.dir`). `OPENROUTER_BASE_URL`/`HTTP_REFERER`/`TITLE` come from
  `local.properties` with defaults; the **API key and model live only in the app's SharedPreferences**
  (`AgentPrefs`, entered via the in-app settings dialog; default model `qwen/qwen3.7-plus`). The
  "Invalid API key." message in the chat is the 401 path when the dialog key is wrong/empty.

- **Can the agent be interrogated from outside with its own tool harness? YES.**
  `AgentOpenRouterHarnessTest` runs the agent exactly like the chat (safety prompt + system prompt +
  `AgentToolExecutor` loop) against any OpenAI-compatible provider: `OPENROUTER_API_KEY` preferred,
  else `OPENAI_API_KEY` / `DEEPSEEK_API_KEY` / `GEMINI_API_KEY` (failover on 401/402/429). It is
  **opt-in** (`AGENT_HARNESS_E2E=1` + key env) because real LLMs are non-deterministic. Validated
  with DeepSeek. Run: `$env:AGENT_HARNESS_E2E="1"; .\gradlew.bat testDebugUnitTest --tests "...AgentOpenRouterHarnessTest"`.

- **Real defects found by that e2e (fixed):** (1) multiple tool commands in one LLM response
  executed only the first → `checkForToolUse` now executes ALL of them; (2) models sometimes
  announce a tool without emitting the command token → strict "Tool emission" rule added to the
  system prompt.

## 0c. Scheduled-expenses live wiring + erase confirmation (user request, 2026-08-05)

- **Scheduled Expenses BUG FIXED (was: "hardcoded, not sensible to changes in the input form").**
  `SpecificExpensesScreen` kept its own local list, persisted straight to SharedPreferences via a
  `LaunchedEffect`, and **never** called `FinancialViewModel.updateSpecificExpenses` (dead code) —
  the simulation used a stale expense list until app restart / reset / profile load. Now the screen
  reads from `viewModel.specificExpenses` (single source of truth) and every add/edit/sort/delete
  goes through `updateSpecificExpenses`, gated by the new pure helper `expensesListsDiffer`
  (no-op skip when equal). On real change it clears optimization artifacts + `goalSolverResult`,
  persists via `SpecificExpensesRepository.saveExpenses` and triggers recalculation — so the form
  is alive for the simulation, GA, agent context, profiles and save/load exactly like every other
  input. TDD: `ScheduledExpensesFlowTest` (red first: unresolved `expensesListsDiffer`).

- **RENAME "One-Time Expenses" → "Scheduled Expenses"** ("Spese Pianificate" / "Gastos Programados"):
  keys `add_edit_scheduled_expenses`, `scheduled_expenses_title`, `scheduled_expenses_description`
  in values/, values-it/, values-es/ (ES also received all previously-missing screen translations);
  Kotlin references updated in `SpecificExpensesScreen` and `FinancialCalculatorScreen`. Only stale
  `lint-baseline.xml` mentions remain (harmless — unmatched baseline entries are ignored).

- **ERASE (user question "come cancellare i dati salvati le analisi fatte i punti sui grafici?").**
  The button already existed — "Erase Analysis Results" (`clear_analysis_state`) →
  `FinancialViewModel.clearAnalysisState()` resets ALL in-memory analysis state including the three
  chart-marker snapshots (`lastTrueScalar`/`lastParetoCompromise`/`lastParetoReference`), results,
  counts, goal-solver result and profile-2/delta comparison data; semantics locked by
  `OptimizationModeFlowTest`. Analysis state is **never persisted** (markers are in-memory only), so
  that button IS the complete erase. NEW: it now opens an **AlertDialog confirmation** (destructive
  "Erase" in error color + Cancel; message states saved inputs/profiles/preferences are untouched)
  via new strings `erase`, `erase_analysis_confirm_title/message` (en/it/es).

- Persisted-data map: inputs/surplus/curves/expenses/profiles = `Repositories.kt` prefs; agent chats
  \= `ChatHistoryPrefs` (per-session delete only, no bulk clear); agent API settings = `AgentPrefs`;
  language & compare state in their own prefs.

- Verification: `testDebugUnitTest` 111 tests / 0 failures / 0 errors (1 opt-in skip);
  `assembleDebug` green.

## 0d. Sensitivity metric + Goal Solver apply (user request, 2026-08-05)

- **Sensitivity now measures the AVERAGE UTILITY (happiness)**, not the scalarized objective
  `fObjW = Avg*((1-w)+w*Stability)` (user: "sensitivity of average utility, not fobj").
  New `SimulationLogic.calculateAverageUtilityFromYears` (monthly samples, else yearly aggregate —
  same sampling as the objective) is the metric; unit steps unchanged (P1 per 10pp, P2/P4 per year,
  monetary per 10k€, rates per 1pp, Daily Surplus per +100 €/month of extra earnings). The
  "Bonus Weight (w)" row was REMOVED (w defines the objective; it never moves the average utility).
  Agent header: "**Sensitivity Analysis (impact on average utility):**"; system prompt documents the
  metric; `sensitivity_calculation_failed` reworded (en/it/es). Rate rows verified: they report
  utility points per +1 percentage point (implementation perturbs +0.1pp and divides by 0.1 →
  `dU/d(rate)·0.01` — correct).

- **Goal Solver contradiction root-caused**: the bisection always used the official engine — the
  defect was the APPLY path, which installed ONLY `capitaleIniziale`, so the official simulation
  kept the user's own plan shape (etaPensione/p2/p3/p4) and contradicted the solver's promise.
  New `GoalSolverLogic.buildGoalApplyInputs(baseInputs, result)` installs the FULL goal plan
  (etaPensione = p2 = p4 = stopWorkAge, p3 = 0, sogliaMinima = T, required capital, user curves
  preserved); `applyGoalSolverCapital` uses it. Button "Apply Goal Plan" / "Applica Piano
  Obiettivo" / "Aplicar Plan Objetivo"; dialog description (en/it/es) explains that with the same
  capital the OPTIMIZER may reach a higher score via a less conservative plan (no per-sample floor,
  may spend above the utility minimum) — a different question, not an error.

- Tests (red-first): `SensitivityAvgUtilityTest` (3), `GoalSolverApplyTest` (2 — apply = full plan +
  the applied plan satisfies the goal in the official simulation); `AgentSensitivityToolTest`
  header updated.

- Verification: `testDebugUnitTest` 116 tests / 0 failures / 0 errors (1 opt-in skip); `assembleDebug` green.

## 0e. Goal Solver redesign: P1 sweep locus (user request, 2026-08-05)

- **The answer is a TABLE, not a number** (user spec: "solution is a chart, a table P1 / capital\_i
  couples"): `GoalSolverLogic.solveCapitalVsSavingRatio` sweeps P1 over 0%..100% step 10% (+ an exact
  flagged row for the user's current P1 when off-grid) and bisection-solves the minimum initial
  capital per row with the official engine. The locus is non-increasing in P1 (more saving while
  working → less capital needed today; once the utility floor binds, net monthly accumulation is
  `surplus − minSpend`, independent of P1). Tests: `GoalSolverSweepTest` (5: monotone locus, row ==
  single solve, off-grid current row, all-infeasible when threshold unreachable, apply-row plan).

- **Semantics (user questions on P3/P4)**: in the goal plan `P4 = P2 = stopWorkAge` by design.
  `P3 = 0` does NOT mean "never spend capital": after the stop the engine funds the utility minimum
  FROM CAPITAL via the `max(baseSpend, minimumSpend)` floor. The spent amount — the user's "unknown
  P3" — is solved month-by-month by the floor, and for the minimum-capital question that is the
  optimal path; a single percentage cannot express an age-varying amount, so `(P1, capital_i)` is
  the complete locus. Characterization lock:
  `applied_goal_plan_spends_capital_exactly_at_the_threshold_after_stop` (every post-stop monthly
  utility == threshold within 1e-6; final capital < half the required one).

- **UI**: `GoalSolverDialog` shows the selectable table (radio rows, "(current)" marker, infeasible
  rows "not reachable"); **Apply Goal Plan** installs the selected row's whole plan (P1 + capital +
  stop age + floor rule + threshold). Strings `goal_solver_table_p1/table_capital/current_row/
  row_infeasible` + rewritten `goal_solver_description` (en/it/es). ViewModel state renamed
  `goalSolverResult` → `goalSweepResult: GoalSweepResult`; `applyGoalSolverCapital` →
  `applyGoalSolverPlan(row)`. Agent tool `RUN_RETIREMENT_SOLVER` answers for the current P1 only
  (doc updated; the GUI table covers the whole locus).

- Cross-validation, fixed scenarios (user request): `GoalSolverCrossValidationTest` - 4 arbitrary
  scenarios (S1 no-income 40/40/82 T0.30; S2 mid-career w/ expenses+inheritance+TFR 42/58/82
  T0.25; S3 i=5% 35/50/90; S4 low-threshold 50/60/85), P1 ∈ {0, 0.3, 0.7, 1.0}: at C\* the engine
  satisfies the goal, at C\*−2×tol the engine violates, at C\*+50k the utility history is
  identical (maxDiff < 1e-9) with higher bequest; entire S2 locus (11 rows) re-validated.
  C\* table in `.agent/README-goal-solver.md` (S1 flat 269,531 € ∀P1 — floor binds; S4
  78,369 → 31,494 → 0 as P1 rises).

- Random cross-validation (user request): `GoalSolverRandomCrossValidationTest` - seeded-random
  input sets (SEED 20260901, 12 draws) -> sweep -> random P1 row applied -> engine time-history.
  Whenever C\* > 0 the plan must hit a binding constraint: 4x UTILITY graze (min sample - T =
  -5.55e-17, machine-exact), 1x DEBT graze (min year-end net worth 533 EUR within compounded
  slack), 7x trivially feasible (C\* = 0, income covers). ENGINE FACTS discovered: utility can
  NEVER fall below T (floor -> shortfall becomes debt, so the solver's utility check is a
  mirror; real constraints = year-end debt + death bequest); violazioneLascito is death-only;
  legacy guarded in-plan by the reserve-gated p3 draw (discounted legacy + PV of expenses).
  Golden rules in `.agent/README-goal-solver.md`.

- Locus chart (user request): `GoalSolverDialog` now renders the (P1, C\*) locus as a 2D Plotly
  chart (same stack as the Pareto chart) with the user's CURRENT simulation position (own P1 +
  actual initial capital) as a red marker. Pure model `logic/GoalLocusChartModel.kt`
  (`GoalLocusChartModelBuilder`, red-first tests: feasible rows only + sorted + percent,
  marker P1 coerced to \[0,1]); `LineTraceSpec.pointSize` added (default 4, marker 12);
  dialog body scrollable; strings en/it/es (chart\_title/axis\_p1/axis\_capital/trace\_locus/
  trace\_current). Chart hidden when no feasible row.

- Chart layout fix (user review: "grafico schiacciato per la legenda"): compact layout via
  `buildMultiLineJson(layoutOverrides, xTickAngle)` — legend INSIDE (h, top-right), margins
  44/8/8/30, x fixed 0..100, tickangle 0, `staticPlot: true` (kills stuck hover tooltip +
  gesture conflicts); Pareto default layout UNCHANGED, both locked by
  `PlotlySpecBuilderLayoutTest` (`testOptions.unitTests.isReturnDefaultValues = true` added
  to app/build.gradle.kts to unit-test Log-calling builders).

- Chart v3 — NATIVE Compose (user review 3: WebView still clipped in dialog): the locus chart
  is drawn natively now (`GoalLocusChart` composable, Canvas 200dp, Compose legend; x ticks
  0/25/50/75/100%, y nice ticks, red marker) — no WebView in the dialog, no sizing race.
  Geometry `logic/GoalLocusChartGeometry.yAxisTicks` unit-tested (TDD red-first). Plotly
  stack kept for full-screen charts; PlotlyHtmlProvider sizing hardening kept.

- Fobj > 1 regression fixed (user report: the fobj chart shows values above 1, stability function
  ill-conditioned, "fixed long ago but reintroduced"): the stability formula itself is NOT the
  culprit — `Avg/(Avg+Std)` is clamped \[0,1] and `fObjW ≤ avg` by construction since the
  2026-07-30 rewrite (commit 2d49502). The real hole: the monthly utility assembly
  `utilitaDaSpesa(...) + cumulativeUtilityOffset` adds the per-expense utility offset
  (user-editable in Specific Expenses) AFTER the \[0,1] clamp -> samples > 1 -> avg > 1 ->
  fObjW > 1 in the Charts heatmap. Reproduced red-first
  (`SimulationLogicTest.utilityWithOffset_stays_bounded_and_fobj_never_exceeds_one`,
  max sample 1.1585 with offset 0.9); fix = top-only clamp `.coerceAtMost(1.0)` at the
  simulation source (negative samples keep the existing infeasible→0 semantics; solver
  threshold-graze unaffected — clamp only touches the top). The pre-2d49502 formula
  `(Avg + w·(Avg/Std))/(1+w)` with 100.0 fallback was genuinely explosive; that historical
  fix is intact.

- Verification: `testDebugUnitTest` 138 tests / 0 failures / 0 errors (1 opt-in skip);
  main sources compile (test build).

## 0g. Graded cost function + max-utility spend cap (user request, 2026-09-02)

- **Problem**: the fobj landscape over P1-P4 was flat with cliffs ("piatta a scalino"). Not the
  stability formula (clamped, bounded since 2d49502) but the SIGNALING: (a) floor-funded threshold
  utility pinned most defensive plans to the same score (debt hid failure); (b) since 2d49502 any
  legacy violation or negative finite sample zeroed the WHOLE objective -> flat plain at 0 with no
  gradient; (c) spend above the utility-curve knee was never capped -> wasted capital fed cliff (b).
  The ORIGINAL engine (dc1e7a0) was smooth because of a graded death-year utility penalty (-100),
  a spend cap at max-utility spend, and no objective zeroing.

- **Fix 1 — graded objective** (`calculateObjectivesFromYears`): binary zero-outs removed; formula
  unchanged (`Avg*((1-w)+w*Stability)`); legacy violation now subtracts a graded penalty
  `100.0/planYears` from BOTH fObjW and fObj0 (separation: penalty > 1 >= base for planYears < 100
  -> violators always negative, with a continuous slope inside the violating region); finite
  negative samples (disutility offsets) flow into the average; the ONLY zero-out left is the
  math-error guard (non-finite samples or the -1e9 exception dummy, `UTILITY_SENTINEL_ABS = 1e6`).
  Tests: graded penalty value + separation, gradient between violating plans, negative-flow, sentinel.

- **Fix 2 — spend cap** (`computeMaxUtilityMonthlySpend` + monthly loop): voluntary spend capped at
  the utility-curve plateau start (smallest curve-point x with y >= curve max, x DAYS\_PER\_MONTH;
  default sigmoid -> `valoreSpesaGiornalieraMaxUtilita * DAYS_PER_MONTH` = old
  `valoreSpesaMensileMaxUtilita`); `finalSpend = max(min(baseSpend, cap), minimumSpend)` — floor
  UNCONDITIONAL (deliberate deviation from the old engine, keeps goal-solver semantics exact).
  No waste above saturation -> capital preserved -> fewer violating plans; solver C\* can only DECREASE.
  Tests: helper unit tests (default/curve-plateau/single-point) + engine cap-bind + floor-wins.
  Plan flaw caught during execution: within-year utility is NOT constant (fdeg uses continuous age,
  drift \~0.002/yr) — plateau assertion uses spread < 0.01.

- **Verification**: `testDebugUnitTest` 147 tests / 0 failures / 1 opt-in skip; `assembleDebug`
  green. All goal-solver cross-validation + characterization tests pass unchanged (dynamic C\*).

- **REVISION 2 (user rejected revision 1: "sempre quadrata... un gran quadratone piatto")** —
  evidence-driven correction with the real-data landscape diagnostic
  (`UserRealDataCheckTest.user_real_data_fobj_landscape_diagnostic`, prints the P1xP2 grid with
  floor%/sat%/avg/std/fobj per cell):

  - Diagnosis 1: the feasible plateau was FLOOR-PINNING — the reserve-gated p3 draw quarantined
    legacy + PV-of-expenses, leaving \~0 excess -> the draw was dead and P1 >= 0.4 produced
    BYTE-IDENTICAL results (feasible spread 0.017 total).

  - Fix: old three-branch spend rule restored — pre-pension p3 quota on (netWorth - legacy);
    retirement = p3-SCALED sustainable annuity PMT(netWorth, yearsLeft, legacy) gated on p3 > 0
    (the goal-solver contract "p3 = 0 -> no capital draw" MUST hold: ungated annuity broke 19
    tests); forecast brake `forecastFinalWithMinimumSpend(month) < legacy + 1 -> draw 0`
    (monthly port of the old forecastFinalWithMin). Reserve machinery removed.

  - Diagnosis 2: a marginal legacy landing (49,206 vs 50,000) hit the FLAT penalty -> whole grid
    at -2.3. Fix: SHORTFALL-PROPORTIONAL penalty `2.5 x (legacy - finalNetWorth)/legacy`
    (DEATH\_LEGACY\_PENALTY = 2.5 = June magnitude for a 100% breach; 1 EUR-scale breaches now cost
    \~0.04, not 2.5). Scale bug (100 vs 2.5) caught by the diagnostic itself (-64 cells).

  - RESULT (real data, P1xP2 grid): spread 0.017 -> 1.61; P2 gradient 0.147 -> 0.189 (longer
    saving -> richer annuity); P1 = 0 -> -1.42 (die \~18k in debt — honest deep negative);
    current plan 0.1526. The landscape finally discriminates.

- **Visible consequences**: study-table C\* values decrease vs pre-2026-09-02; fobj charts can show
  NEGATIVE values for legacy-violating plans (the graded failure signal); applied plans spend less
  above the saturation knee (higher final net worth). `tools/cross_model_regression.py` is STALE
  (encodes the 2026-07-30 policy) — regenerate before trusting it.

- **REVISION 3 (user: "sempre piatta... anche w=0 assurdo!!") — A/B June-vs-current + display fix**:

  - Experiment: the June engine (dc1e7a0) was ported VERBATIM into a test fixture
    (`JuneEngineLandscapeABTest`, annual loop + June objective `(Avg + w'·Avg/Std)/(1+w')`) and run
    side by side with the current engine on the user's real data (P1xP2 grid, w in {user, 0, 1}).

  - DECISIVE RESULT: June is EQUALLY FLAT on today's data (feasible avg spread \~0.013 vs current
    0.042 — current varies MORE; June cap%=0 everywhere, avg \~= T=0.2 -> floor-pinned by the \~141k
    scheduled expenses + 50k legacy vs 100k capital). The "come prima" difference is NOT the engine.

  - ROOT CAUSE of the visual flatness: the heatmap maps colors linearly over the RAW fObjW min/max
    (`PlotlySpecBuilder.getZMinMax`). With violators at -2.39 and feasible 0.204..0.246, the
    feasible band spans \~1.6% of the color scale -> all feasible cells render as ONE color. In
    June violators were exactly 0 -> scale \[0, 0.29] -> feasible band had full color resolution.

  - FIX (display-only, optimizer untouched): `SurfaceGrid.anchorColorScaleOnFeasible` (true on the
    normal landscape grids in ChartLogic, false on delta grids); `PlotlySpecBuilder`
    `getFeasibleAnchoredRange` anchors the 2D color range (contour start/end, heatmap zmin/zmax)
    on cells >= 0 -> violators clamp to the bottom color. 3D surface and delta grids keep the raw
    scale. Contract tests: `PlotlySpecBuilderColorScaleTest` (6). Suite 157/0/1 skip,
    `assembleDebug` green. Docs: README-goal-solver golden rules 3/5 refreshed + new rule 6.

- **DATA AUDIT (user: "i dati li hai controllati sono sensati?") + engine-exoneration proof**:
  new opt-in `UserRealDataCheckTest.user_real_data_sensibleness_audit` + synthetic
  `SimulationLogicTest.richSurplusData_produces_nonFlat_p1Landscape`. FINDINGS (all numbers
  cross-checked, no engine bug):

  - Real data: surplus 1,258 EUR/mo working / 18 EUR/mo pension; minimum spend for T=0.2 is
    853 (42yo) -> 1,745 (80yo); expenses 141.5k (40k\@50, 26k\@60, 46k\@70, 20k\@80); inheritance
    169k\@57; TFR 100k\@65; legacy 50k; curve responsive band 700-2,000 EUR/mo; cap 3,044 EUR/mo.

  - PLATEAU IS PHYSICS: floor bites when surplus x (1-P1) < minimum -> P1 > 1 - 853/1258 = 0.322.
    Measured transition: P1=0.300 (spend 880 > 853, avg 0.2135) vs P1=0.325 (spend 849 < 853 ->
    floor, avg 0.2134) -> byte-identical above. The current plan spends 29 of 41 years AT the
    floor (u=0.200 exactly).

  - CHASM AT THE BOUNDARY IS THE SEPARATION FLOOR (REVISION 2b, user-requested): fine sweep
    P1=0/0.025/0.05/0.075 -> fobj0 -2.34/-1.84/-1.33/-0.83 (graded, finalNW 18.3k/28.5k/38.7k/
    48.8k), then P1=0.100 -> +0.229 (feasible). A 1,161 EUR shortfall (2.3% of legacy) costs
    1.06 because penalty = 1.0 (floor) + 2.5 x shortfallRatio and the floor MUST exceed the best
    possible feasible score (\~0.25) or the optimizer would again pick marginal violators. The
    graded transition exists; only the last step into feasibility is discontinuous BY DESIGN.

  - ENGINE EXONERATED: with synthetic rich data (surplus 3,000 EUR/mo >> minimum \~450, capital
    300k, no expenses) the P1 sweep is SMOOTH and RICH: fobj0 0.4657 -> 0.2136, spread 0.252,
    monotonic over the whole range (test locks spread > 0.10). The engine produces rich
    landscapes whenever the data allows; the user's flatness is the tightness of their own
    numbers (surplus barely 1.47x the minimum consumption).

  - Git archaeology: ChartLogic/ChartsViewModel/heatmap code has exactly 2 commits, both
    2026-06-26 (dc1e7a0 initial + 776f773 pareto) -> the chart and its w-sourcing are UNCHANGED
    since June; "rich June" cannot come from chart code. Suite 159/0/1 skip, assembleDebug green.

- **THE ARCANUM (found by the USER, 2026-09-02): the flat "yellow square" was the THRESHOLD T.**
  The user's `sogliaMinimaFunzioneUtilita` is 0.2 NOW; it was 0.1 in June. T sets the minimum
  spend, and the minimum spend sets WHERE the utility floor bites: **P1\* = 1 - minSpend(T)/surplus**.
  Measured degradation curve on real data (`ARCANUM CHECK` in
  `user_real_data_fobj_landscape_diagnostic`, T sweep 0.2/0.16/0.12/0.1):

  - T=0.2 -> minSpend(42)=853 EUR/mo -> P1\*=0.32 -> feasible 30/36 cells, feasible-avg spread
    0.0236 (the yellow plateau) + 6 chasm cells at the border;

  - T=0.16 -> 731 EUR/mo -> P1\*=0.42 -> 36/36 feasible, spread 0.0675 (already half-flat);

  - T=0.12 -> 608 EUR/mo -> P1\*=0.52 -> spread 0.1038;

  - T=0.1 -> 538 EUR/mo -> P1\*=0.57 -> feasible 36/36 (ZERO violators, NO chasm anywhere),
    spread 0.1229 (5.2x richer) -> the June landscape reappears exactly.
    WHY T flattens (worst feasible avg RISES toward the best as T grows: min 0.1087 -> 0.1658 ->
    0.2039 while max stays \~0.23): (1) floor-years contribute EXACTLY T for every (P1,P2) — a
    growing constant diluting the average (current plan at T=0.2: 29/41 years at floor);
    (2) the responsive spend band \[minSpend(T), surplus] narrows (721 -> 527 -> 405 EUR/mo) and
    the utility curve saturates at the top; (3) the byte-identical plateau boundary P1\* falls
    with T. No w can recover the contrast: fObj is a monotone reshape of the same compressed avg.
    NO CODE CHANGE was needed: the engine was exonerated; T is a user setting. RULE FOR FUTURE
    SESSIONS: when the fobj heatmap "goes flat", check `sogliaMinimaFunzioneUtilita` FIRST.

## 0f. Failed attempts & dead ends (do NOT repeat)

Consolidated from the campaign's session logs (details also in `.agent/memory/activeContext.md`
"Gotchas"):

1. **Dead-code trap (the original scheduled-expenses bug)**: `updateSpecificExpenses` existed,
   tested and correct — but NO screen ever called it. "A function exists" ≠ "a function is
   wired". Audit wiring, not just presence.
2. **Parallel Search/Replace on the SAME file clobbers edits** (the RUN\_SENSITIVITY dispatch
   case was silently lost). Edits to one file: strictly sequential.
3. **`"...pt / 10%".format(x)`** **crashes** (`Conversion = '%'`) on literal `%` in the template —
   format the number separately with `Locale.US`.
4. **Rate-row sensitivity test bug (not a code bug)**: the first expectation divided by the
   absolute step (0.001) instead of deriving the per-1pp impact (×10) — mismatch was exactly
   ×100. The implementation's `check()` deltas are in percentage-point units.
5. **Android** **`strings.xml`** **escaping**: a doubled `\\'` lands LITERALLY in the resource (hit
   twice, IT strings); raw inner double quotes break AAPT — use typographic “ ” or `\"`.
   Always re-read the file after string edits.
6. **Real-LLM e2e is non-deterministic** (OpenAI 429 no-credits; flaky tool-loop completion in
   the full suite) — kept opt-in via `AGENT_HARNESS_E2E=1` with provider failover.
7. **Built-in md editing can markdown-escape underscores** in `.md` files — verify after
   editing, rewrite with Write if artifacts appear. MCP `edit_file` requires BOTH oldText and
   newText (no pure insertions).
8. **LLM harness defects found only by the real e2e** (see 0b): repeated tool calls, announce
   without emitting the command token, multi-command responses executing only the first.
   Unit tests alone did not surface them.

***

## 1. What the app is

Android app (Kotlin 2.2.10, Compose BOM 2024.09.00, Material3, minSdk 24 / target 35) for
life-financial simulation and behavioral parameter optimization:

- Monthly-step simulation engine ([SimulationLogic.kt](app/src/main/java/com/example/daysurpopt/logic/SimulationLogic.kt)):
  `rm = (1+r)^(1/12)-1`, reserve-first capital rule, debt bucket, one-time expenses with
  cumulative utility offset from event age onward, forced minimum spend to guarantee
  `sogliaMinimaFunzioneUtilita` (happiness threshold) by drawing capital.

- Objective: `StabilityScore = Avg/(Avg+StdDev)`; **True Scalar** `fScalar = Avg*((1-w)+w*Stability)`
  with direct `w ∈ [0,1]`. Pareto modes are weight-free (knee selection via normalized chord distance).

- Three optimization modes: TRUE\_SCALAR (GA + `refineScalarCandidate` coordinate search), PARETO\_KNEE,
  PARETO\_FRONT. Optimization results are applied back to live inputs (P1..P4) and re-simulated.

- Persistence: SharedPreferences (profiles via `ProfileStateMapper`, chats via `ChatRepository`,
  agent settings via `AgentSettingsRepository`).

- Charts: Plotly 2.30.0 in WebView, black paper/plot background, white fonts.

- Cross-model parity: `tools/cross_model_regression.py` vs Python reference (`C:\Users\giova\OneDrive\Documents\DOCUMENTS\Scripts\Finantial Awareness_v2`), tolerance 1e-9.

Theme (changed 2026-08-05): dark is **forced app-wide** ([Theme.kt](app/src/main/java/com/example/daysurpopt/ui/theme/Theme.kt)
uses `dynamicDarkColorScheme` on API 31+, static dark scheme otherwise; [themes.xml](app/src/main/res/values/themes.xml)
parent is `android:Theme.Material.NoActionBar`). There is no light variant anymore.

## 2. Navigation & GUI workflow audit (the "buttons vs tabs" problem)

### Current map

- Bottom bar (MainActivity): `Simulation` (financialCalculator) · `Surplus` · `Charts` · `Setup` (assumptions) · `AI Agent` · `Debug Log`.

- Simulation screen is sectioned by **buttons** that navigate to full screens:

  - Section "1. Data Input & Setup" → buttons: `userData` (UserInputsScreen: ages, capital, rates, threshold, P1..P4), `specificExpenses` (one-time expenses).

  - Section "2. Optimization Configuration" → button: `gaConfig` (GA params) + inline optimization-mode radios (True Scalar / Pareto Knee / Pareto Front).

  - Section "3. Analysis & Actions" → `Optimize` button, button to `optimizationParams`, `Run Simulation`, `Sensitivity`, `Erase Analysis Results`, `Reset Inputs`; results cards below.

  - "Manage Profiles" card at top; "About" card at bottom.

- Surplus screen: standalone bottom-bar destination with its own long input form (income green / expenses red,
  work & pension columns) and a back-arrow "save and return" that pushes computed daily-surplus values
  into `previousBackStackEntry.savedStateHandle`.

- Weight `w`: lives in the Charts screen slider (shared state in FinancialViewModel; slider release reruns active mode).

- Assumptions screen: utility & degradation curves editing + reset to defaults.

### Findings

- **F1 — Mixed input paradigm (UX blocker).** The workflow is non-linear and self-describing only partially:
  the user must *know* that income/expenses live in the bottom-bar `Surplus` tab while ages/capital/rates/P1..P4
  live behind the `User Data` button on the home screen, and that `w` lives in `Charts`. Nothing guides the
  sequence Surplus → User Data → (opt.) GA Config → Optimize → Charts. The intended sequence exists only as text:
  `quick_start_step1..6` strings (en/it/es) describe a 6-step guided path — **but** **`QuickStartDialog`** **is dead code,
  never instantiated anywhere**.
  *Recommendation:* reintroduce the flow explicitly. Options (in order of effort):

  1. Minimum: wire `QuickStartDialog` on first launch (privacy-consent accepted && no profile yet) + a "?" action.
  2. Better: replace the bottom-bar `Surplus` tab with a home-screen button inside "1. Data Input & Setup"
     (matching the old "buttons in the right workflow position" the user preferred), keeping bottom bar for
     Simulation / Charts / Agent / Setup only.
  3. Best: a 3-step onboarding wizard (Surplus → User Data → Optimize) with a visible stepper and a
     completion state that unlocks "Run Optimization".

- **F2 — Surplus data entry has no in-context validation of ages.** `mutuoAffittoFinoEta`, bonus "until age"
  etc. are free ints; the simulation silently clamps (e.g. `p4Age = max(p4, p2)`), the user never sees it.

- **F3 —** **`sogliaMinimaFunzioneUtilita`** **(happiness threshold) is buried** in User Data and never surfaced in
  results. Given finding F7 (ceiling), the app should show "your settings can reach at most X happiness at age Y".

## 3. AI Agent audit (tools vs GUI parity)

### Architecture

- [AgentViewModel.kt](app/src/main/java/com/example/daysurpopt/ui/AgentViewModel.kt): OpenRouter chat,
  system prompt = `Defaults.OPENROUTER_SAFETY_SYSTEM_PROMPT` + `PromptConstructor.constructSystemPrompt(...)`,
  last-10 visible messages as context, tool loop with **max depth 5** per user message; tool outputs appended
  as `system` messages; fresh GUI state (`inputs`, `specificExpenses`, `surplusData`) is passed on every user message.

- [AgentToolExecutor.kt](app/src/main/java/com/example/daysurpopt/agent/AgentToolExecutor.kt) commands:
  `WEB_SEARCH`, `FETCH_PAGE`, `GET_TIME`, `GET_FINANCIAL_CONTEXT`, `RUN_SIMULATION`, `RUN_OPTIMIZATION`,
  `RUN_MULTI_AGENT_ANALYSIS` (regex-detected + brace-counted JSON args; overrides mapped in
  `applyFinancialOverrides` / `applySurplusOverrides` / `applySpecificExpenseOverrides`).

### Parity verdict (the user's question: "same functions as GUI, in batch, exactly identical?")

- **Simulation: YES, exact.** `RUN_SIMULATION` calls `calculateSimulationWithWeight(modified, expenses, surplus)`
  — the same engine entry point the GUI uses. Locked by `AgentToolParityTest` (objective / final capital / avg
  utility strings must equal direct engine computation, with and without overrides, including
  `sogliaMinimaFunzioneUtilita` override).

- **Optimization: PARTIAL.**

  - Same core (`OptimizationLogic.optimizeParameters` + coordinate search = `refineScalarCandidate`), and the
    agent seeds the GA with the current parameters, so result ≥ current (locked by test). BUT:

  - **GA config mismatch:** agent hardcodes `popSize=100, generations=50, pc=0.7, pm=0.08, P1[0,1],
    P2[etaAttuale,etaPensione], P3[0,1], P4[etaAttuale,etaMorte]`; the GUI uses the user's `GAConfigUI` via
    `OptimizationLogic.parseGaConfig` (defaults pop 350 / gen 100, user-editable min/max ranges, and
    `maxP4 = max(maxP4raw, maxP2)`). Different budgets → agent optima can differ from GUI optima.

  - **Modes missing:** GUI has Pareto Knee / Pareto Front (`ParetoOptimizationLogic` +
    `ParetoKneeSelectionLogic`); the agent can only run True-Scalar-style GA. No mode parameter exists.

  - **No state write-back:** GUI optimization applies P1..P4 to live inputs and re-publishes results; the agent
    only prints a report. There is no "apply these parameters" tool.

  - **GUI features with NO agent access:** sensitivity analysis (`OptimizationLogic.runSensitivityAnalysis`),
    profile comparison / compare-state, PDF export, charts/marker snapshots, GA-config editing, profile
    create/load/delete, erase-analysis / reset-inputs, weight slider rerun semantics.

- **Other findings:**

  - **F4 — Stale formulas in** **`AgentPrompts.getRiskPrompt`** (lines 52–53): it still tells the LLM
    `Stability Index definition: StdDev / (Weight/100)` and `Objective stability reward term: AvgUtility / StdDev`.
    Both are pre-2026-07-30 semantics; the real ones are `Avg/(Avg+StdDev)` and
    `fScalar = Avg*((1-w)+w*Stability)`. The LLM is being fed wrong math for every risk/multi-agent report.

  - **F5 —** **`FETCH_PAGE`** **is implemented in the executor but NOT documented** in the system prompt's tool list
    (the LLM can hardly discover it). Also `sogliaMinimaFunzioneUtilita` is overridable in the executor but
    missing from the documented RUN\_SIMULATION parameter list.

  - **F6 — Multi-agent report hardcodes** **`isComparing = false`** in `executeMultiAgentWorkflow`; in compare mode
    the agent still analyzes profile 1 only.

  - Note: `AgentReportFormatter.computeStabilityIndex` correctly delegates to the current `computeStabilityScore`
    — the *computation* is right; only the prompt *text* (F4) is stale.

  - PromptConstructorTest exists and passes; it does not cover F4/F5 (see `PromptDocumentationTest` note).

## 4. The inverse problem: "how much must I accumulate to stop working with happiness ≥ 0.3?"

**Answer: NOT possible today via GUI or agent tools — but provably implementable with existing primitives.**

- There is **no root-finding/solver** anywhere in the app. The only "numeric tools" are the GA (maximizes
  fScalar over P1..P4) and one-sided finite-difference sensitivity. Neither answers "minimum capital such that
  plan X is feasible". An LLM would have to manually iterate `RUN_SIMULATION` guesses — unreliable, and the
  tool loop is capped at 5 turns.

- Semantics map cleanly onto existing engine fields (this is the key insight for implementation):

  - "Stop working" → `etaPensione = stop-work age` (work-income bucket never applies) + zeroed pension income
    in `SurplusInput` (agent can already override every income/expense field).

  - "Resulting happiness ≥ 0.3" → `sogliaMinimaFunzioneUtilita = 0.3`; the engine *already forces* the minimum
    monthly spend achieving it, drawing capital (going into debt when capital runs out).

  - "Feasible" → no `debtAmount` in any year, no `violazioneLascito`, all utility samples ≥ threshold.

  - Then `min capitaleIniziale` is found by simple bisection (monotone: more capital only relaxes constraints).

- **Proven by** **`RetirementCapitalSolverTest`** (test-only solver, app untouched):

  - Stop work at 40, zero income, happiness ≥ 0.3 (custom degradation floor 0.5): **≈ 275,391 €** needed.

  - Threshold 0.25 → ≈ 222,656 €; 0.30 → ≈ 275,391 € (monotonic ✓). Solver verified: −2,000 € ⇒ infeasible.

- **F7 — Important discovered constraint (locked by test):** with the **default** curves, the utility curve
  ceiling is ≈ 0.9347 (the baseline logistic evaluated at `BASELINE_MAX_SPESA` never reaches 1.0), so max
  achievable happiness at age 82 is ≈ 0.9347 × fdeg(82) ≈ **0.295**. A 0.3 threshold is unreachable at 80+
  *regardless of capital* with default curves; a custom degradation curve (floor 0.5) restores feasibility.
  Any future "Goal Solver" must validate `soglia ≤ 0.9347 × min(fdeg)` and warn the user.

### Recommended implementation (next session, TDD-ready)

1. Add `logic/GoalSolverLogic.kt`: `solveMinimumInitialCapital(inputs, expenses, surplus, threshold, tolerance)` —
   bisection exactly as in `RetirementCapitalSolverTest` (feasibility = no debt + no lascito violation +
   utilities ≥ threshold). \~80 lines, pure, unit-testable.
2. Expose GUI card "Goal Solver" (Simulation screen, section 3): inputs = stop-work age + happiness threshold;
   output = required capital + achieved plan; "Apply" writes `capitaleIniziale` (and optionally the zero-income
   surplus variant as a what-if).
3. Expose agent tool `RUN_RETIREMENT_SOLVER {"stopWorkAge": 45, "happinessThreshold": 0.3}` reusing the same
   logic; document it in `PromptConstructor` (avoid a new F5).
4. Add ceiling validation + warning (F7) before solving.
5. Update `cross_model_regression.py` if Python parity for the solver is desired (mirrored bisection).

## 5. Tests added in this pass (all green, no app changes)

| File                                                                                                            | What it locks                                                                                                                                                                                                                                                                                    |
| --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| [AgentToolParityTest.kt](app/src/test/java/com/example/daysurpopt/agent/AgentToolParityTest.kt)                 | Agent `RUN_SIMULATION` output == direct `calculateSimulationWithWeight` (no overrides, with financial/surplus overrides, with threshold override); `GET_FINANCIAL_CONTEXT` returns full GUI state JSON; agent `RUN_OPTIMIZATION` never reports less than the current objective and lists P1..P4. |
| [PromptDocumentationTest.kt](app/src/test/java/com/example/daysurpopt/agent/PromptDocumentationTest.kt)         | System prompt documents the 6 core tools, direct-weight semantics ("do not use P3 as proxy"), all documented override fields; risk prompt mentions Stability Index / Weight (w). Comment marks F4/F5 as open.                                                                                    |
| [RetirementCapitalSolverTest.kt](app/src/test/java/com/example/daysurpopt/logic/RetirementCapitalSolverTest.kt) | Reference bisection solver for the inverse problem + the default-curves utility ceiling (0.299 unreachable, 0.29 reachable at 3M). Reuse this algorithm for the future tool.                                                                                                                     |

## 6. Priorities for the next agent

1. **~~P0~~** ~~Fix F4~~ **DONE** (see section 0).
2. **~~P0~~** ~~Fix F5~~ **DONE** (see section 0).
3. **~~P1~~** ~~Goal Solver~~ **DONE** — GUI dialog + `GoalSolverLogic` + `RUN_RETIREMENT_SOLVER`.
4. **~~P1~~** ~~GUI flow rework per F1~~ **DONE** (QuickStartDialog wired + Surplus moved to home button).
5. **~~P2~~** ~~Agent optimization parity~~ **DONE** — user GA config, Pareto modes, comparison context,
   `RUN_SENSITIVITY`. Optional remaining: "apply optimization results" write-back tool.
6. **P2/P3 remaining:** F2 (age validation in Surplus form), F3 full (threshold/ceiling visibility in
   the main results card), agent access to PDF export / charts / profile management.
7. **P3** `NEXT_AGENT_HANDOFF.md` from 2026-07-30 no longer exists at repo root — this file supersedes it.

## 7. Conventions & commands

- Build: `.\gradlew.bat assembleDebug` · Tests: `.\gradlew.bat testDebugUnitTest` (JUnit4, `app/src/test`).

- TDD mandatory; simulation math must stay finite; Python/Android parity tolerance \~1e-15 on grid points
  (`tools/cross_model_regression.py --python-root <path>`).

- LLM default model `qwen/qwen3.7-plus`; OpenRouter keys via `local.properties` (`OPENROUTER_BASE_URL`,
  `OPENROUTER_HTTP_REFERER`, `OPENROUTER_TITLE`); no secrets in repo.

- Dark theme is forced (2026-08-05): do not reintroduce `isSystemInDarkTheme` / light schemes.

- Realtime debug log: `AppDebugLog` (viewable in-app in `debugLog` screen).

- Workflow doc: `F:\MCP\TRADING\WORKFLOW.md` must be updated after each completed subtask.

