# Active Context - Audit fixes applied (TDD pass)

## Current Task

All audit fixes from HANDOFF.md applied step-by-step with TDD (red-first), session of 2026-08-05.

## Recently Completed (this pass)

1. **F4/F5**: Risk-prompt formulas corrected (`Avg/(Avg+StdDev)`, `fScalar = Avg*((1-w)+w*Stability)`);
   system prompt documents FETCH\_PAGE + sogliaMinimaFunzioneUtilita (AgentPromptsTest, PromptDocumentationTest).
2. **Goal Solver**: logic/GoalSolverLogic.kt (bisection min initial capital for "stop work at age X with
   happiness >= T", ceiling validation F7) + GUI button/dialog in Section 3 with Apply (standard
   updateInputs path) + agent tool RUN\_RETIREMENT\_SOLVER. Strings en/it/es. RetirementCapitalSolverTest
   deleted (superseded by GoalSolverLogicTest).
3. **F1a/F1b**: Surplus moved from bottom bar to home Section 1 button; QuickStartDialog wired on first
   launch (QuickStartRepository) + Star button to reopen.
4. **F6 agent parity**: RUN\_OPTIMIZATION uses user's GAConfigUI (buildAgentGaConfig) + JSON overrides
   popSize/generations/pc/pm + modes TRUE\_SCALAR/PARETO\_KNEE/PARETO\_FRONT; comparison context
   (buildComparisonContextForAgent in FinancialViewModel) injected into multi-agent prompts;
   threaded through AgentViewModel/AgentScreen/ChatView/MainActivity.
5. **RUN\_SENSITIVITY agent tool**: wraps OptimizationLogic.runSensitivityAnalysis, same JSON overrides
   as RUN\_SIMULATION, readable ranked output (AgentSensitivityToolTest).
6. **Multi-agent grounding (part 4)**: buildMultiAgentFinancialContext injects REAL engine results
   (objective/avg/std/stability/final capital/monthly surplus/saving/debt years) + P1 surplus semantics
   into Sustainability & Risk prompts (previously raw inputs only -> hallucinated legacy math, wrong
   debt advice, P1 misread as % of income). Master prompt forbids invented numbers. Repeated tool call
   in one turn blocked via alreadyExecutedCommands guard + extractCommandName tracking.
7. **Curve access + real harness e2e (part 5)**: GET\_FINANCIAL\_CONTEXT now returns effectiveCurves
   (utility vs extra spending + age degradation, defaults materialized); curve EDIT = what-if only
   via RUN\_SIMULATION overrides (no persistence to Setup tab - TO VALIDATE if write-back tool needed);
   system prompt documents curve workflow + strict tool-emission rule (never announce without emitting
   command token). checkForToolUse now executes ALL commands in one response (was: only first).
   AgentOpenRouterHarnessTest (opt-in AGENT\_HARNESS\_E2E=1) validates the real agent against any
   OpenAI-compatible provider; validated with DeepSeek (OpenRouter key NOT in env/local.properties -
   lives only in app SharedPreferences via settings dialog; "Invalid API key" = 401 path).
8. **Scheduled expenses live (part 6)**: SpecificExpensesScreen was saving straight to prefs and
   NEVER calling updateSpecificExpenses (dead code -> stale simulation). Now state starts from
   viewModel.specificExpenses and every edit goes through updateSpecificExpenses, gated by pure
   helper expensesListsDiffer (no-op skip; on change clears optimization artifacts + goalSweepResult,
   persists, triggers recalculation). TDD: ScheduledExpensesFlowTest.
9. **Rename One-Time -> Scheduled Expenses**: keys add\_edit\_scheduled\_expenses / scheduled\_expenses\_title
   / scheduled\_expenses\_description in en/it/es ("Spese Pianificate" / "Gastos Programados"; ES also
   got all previously-missing screen strings); Kotlin refs updated (SpecificExpensesScreen,
   FinancialCalculatorScreen). Stale lint-baseline.xml mentions are harmless.
10. **Erase confirmation**: "Erase Analysis Results" already cleared ALL in-memory analysis incl.
    chart-marker snapshots (locked by OptimizationModeFlowTest) and was never persisted; added
    AlertDialog confirm (erase / erase\_analysis\_confirm\_title+message in en/it/es) before it runs.
11. **Sensitivity metric = average utility (part 7)**: runSensitivityAnalysis measured fObjW
    (scalarized objective); now measures AvgUtility via new SimulationLogic.calculateAverageUtilityFromYears.
    Bonus Weight (w) row removed (defines the objective, never moves avg utility). Unit steps unchanged
    (Daily Surplus per +100 EUR/month; rates per +1pp - verified the /0.1 vs 0.001-absolute scale).
    Agent header + system prompt updated; sensitivity\_calculation\_failed reworded (en/it/es).
    TDD: SensitivityAvgUtilityTest (3 tests).
12. **Goal Solver apply = full goal plan (part 7)**: applyGoalSolverCapital installed ONLY the capital
    (root cause of "contradicts the optimal simulation" - the bisection always used the official
    engine). buildGoalApplyInputs installs etaPensione=p2=p4=stopWorkAge, p3=0, soglia=T, required
    capital, curves preserved. TDD: GoalSolverApplyTest (2 tests).
13. **Goal Solver = P1 sweep locus (part 8, user spec)**: answer is a TABLE (P1, capital\_i), not a
    number. solveCapitalVsSavingRatio sweeps P1 0..100% step 10% + exact off-grid current-P1 row
    (isCurrentPlan); per-row bisection with the official engine. Locus non-increasing in P1 (floor
    binding => net accumulation surplus-minSpend independent of P1). ViewModel state renamed
    goalSolverResult -> goalSweepResult (AnalysisUiState + all clears + runGoalSolver);
    applyGoalSolverCapital -> applyGoalSolverPlan(row). Dialog = selectable radio table
    (P1 % | capital today | (current) | "not reachable"), Apply installs the SELECTED row's whole
    plan. New strings + rewritten goal\_solver\_description (en/it/es). RUN\_RETIREMENT\_SOLVER doc
    updated (answers current P1 only). TDD: GoalSolverSweepTest (5 tests).
14. **P3/P4 semantics clarified (part 8)**: P4 = P2 = stopWorkAge by design in the goal plan.
    P3 = 0 does NOT mean "never spend capital": after stop the engine funds the utility minimum
    FROM CAPITAL via max(baseSpend, minimumSpend). The spent amount (user's "unknown P3") is solved
    month-by-month by the floor; a single percentage cannot express an age-varying need (circular in
    remaining capital), so (P1, capital_i) is the complete locus. Characterization lock:
    applied_goal_plan_spends_capital_exactly_at_the_threshold_after_stop (post-stop monthly utility
    == threshold within 1e-6; final capital < half of required).
15. **Real-data sanity check**: UserRealDataCheckTest (opt-in, reads device SharedPreferences
    extracted to external folder, default C:\WINDOWS\TEMP\fa_prefs via FA_PREFS_DIR, assumeTrue
    skip) - runs current-plan sim + goal locus with/without scheduled expenses on the user's real
    data. adb at %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe; pull prefs with
    `adb exec-out run-as com.example.daysurpopt cat shared_prefs/<Name>.xml`. Validated: scheduled
    expenses DO count in the solver (167,725 vs 70,313 required, delta +97,412), current plan
    feasible (avgUtil 0.2192 >= 0.2, final 50,024 ~= legacy 50,000), locus monotone with P1 plateau
    from 30% (floor binding). Prefs names: FinancialPrefs/SurplusPrefs/SpecificExpensesPrefs/
    GaConfigPrefs/AppProfiles/AgentPrefs/ChatHistoryPrefs.
16. **Cross-validation on arbitrary params (part 8b)**: GoalSolverCrossValidationTest - 4 scenarios
    chosen by the agent (S1 no-income 40/40/82 T0.30; S2 mid-career w/ expenses+inheritance+TFR
    42/58/82 T0.25; S3 i=5% 35/50/90 T0.28; S4 low-threshold 50/60/85 T0.15), P1 in {0,0.3,0.7,1.0}.
    Per row: engine satisfies at C*, violates at C*-2x tol, at C*+50k utility history IDENTICAL
    (maxDiff < 1e-9) + higher bequest; entire S2 locus (11 rows) re-validated. C* table in
    README-goal-solver (S1 flat 269,531; S4 78,369 -> 31,494 -> 0). 126 tests green.
17. **Random cross-validation (part 8c)**: GoalSolverRandomCrossValidationTest - seeded random
    inputs (SEED 20260901, 12 draws), full GUI pipeline (sweep -> random P1 row applied ->
    engine time-history). Whenever C* > 0 the plan must hit a binding constraint: 4x UTILITY
    graze (min sample - T = -5.55e-17 machine-exact), 1x DEBT graze (min year-end net worth
    533 within compounded slack), 7x trivially feasible (C* = 0). ENGINE FACTS discovered:
    (a) utility NEVER falls below T at any capital - floor spends >= minimumSpend, shortfall
    becomes DEBT 10%/y, so the solver's per-sample utility check is a defensive mirror and
    the real constraints are year-end debt + death bequest; (b) violazioneLascito is
    death-only (month == monthCount-1 is the plan's last month, not each year's);
    (c) in-plan legacy is guarded by the reserve-gated p3 draw (only excess over
    reserveAt = discounted legacy + PV of future expenses is p3-spendable; the floor can
    still eat the reserve); (d) with P1 = 100% the floor binds PRE-stop (living on capital
    while working). Three binding modes = UTILITY / LEGACY / DEBT graze; slack = capital
    tolerance compounded at plan rate. 127 tests green.
18. **Locus chart (part 8d)**: GoalSolverDialog renders the (P1, C*) locus as 2D Plotly chart
    (Pareto stack: PlotlySpecBuilder.buildMultiLineJson + PlotlyWebView) - cyan line of feasible
    rows, RED marker (pointSize 12) at the user's CURRENT simulation position (own P1 + actual
    capitaleIniziale): above curve = current plan safe, below = capital missing. Pure model
    logic/GoalLocusChartModel.kt (GoalLocusChartModelBuilder, TDD red-first: feasible-only +
    sorted + P1 percent, marker P1 coerced). LineTraceSpec gained pointSize (default 4).
    Dialog body scrollable. Strings en/it/es. 130 tests green.
19. **Chart layout fix (user review)**: locus chart crushed sideways by the vertical legend
    outside + fullscreen margins + stuck hover tooltip. Fix: buildMultiLineJson gained
    layoutOverrides + xTickAngle (defaults = old layout, Pareto unchanged); locus uses legend
    INSIDE (h, top-right), margins 44/8/8/30, x fixed 0..100, tickangle 0, staticPlot true.
    Locked by PlotlySpecBuilderLayoutTest; testOptions.unitTests.isReturnDefaultValues = true
    added in app/build.gradle.kts (unlocks JVM tests for Log-calling builders). 132 green.
20. **WebView bottom-clip fix (user review 2)**: x axis + labels invisible = chart rendered
    TALLER than the WebView (onPageFinished fired pre-layout in the dialog; lastRenderedSpec
    guard blocked re-render). Fix in PlotlyHtmlProvider for ALL charts: layout.width/height
    pinned to div clientWidth/Height at each render; ResizeObserver re-renders on container
    settle (>2px, guarded by __renderDone); staticPlot always Plotly.newPlot. JS in HTML
    template = not JVM-testable, verified by build + visual. 132 green.
21. **Chart v3 NATIVE (user review 3)**: WebView still clipped in dialog after sizing fixes
    -> replaced with native Compose Canvas chart (GoalLocusChart in FinancialCalculatorScreen,
    legend Row + Canvas 200dp; x ticks 0/25/50/75/100, y nice ticks via
    logic/GoalLocusChartGeometry.yAxisTicks, TDD red-first; red marker 5.5dp). No WebView in
    dialog = no sizing race. Plotly stack kept for full-screen charts. Gotchas hit:
    Canvas.nativeCanvas needs import androidx.compose.ui.graphics.nativeCanvas;
    DrawTransform rotate pivot = Offset; android.graphics.Canvas.rotate(deg,px,py) simpler.
22. **Chart probe (user request)**: hover/tap/drag on the native locus chart snaps to the
    nearest plotted point (locus + current marker, nearest-by-P1 via
    GoalLocusChartGeometry.nearestProbePoint, TDD red-first): dashed guideline, white ring,
    tooltip "P1 n% · capital EUR" clamped in plot; touch drags consumed vs dialog scroll.
    pointerInput(model, yTicks) with awaitPointerEventScope loop (hover = PointerType.Mouse
    unpressed events). 137 tests green.
23. **Naming + full README (user request)**: feature renamed in UI - EN "Anticipated
    Retirement Study" / IT "Studio di Pensionamento Anticipato" / ES "Estudio de Jubilación
    Anticipada" (goal_solver_button/title/solve/solving/chart_title + erase message, en/it/es;
    agent messages "Retirement study ..." in AgentToolExecutor). Internal ids (GoalSolver*,
    goal_solver_* keys, RUN_RETIREMENT_SOLVER) unchanged by design. README.md rewritten to
    cover the WHOLE app (features/stack/setup/tests/structure/privacy).
24. **gitignore + README structure (user request)**: .gitignore rewritten (organized sections:
    Gradle, IDE, Kotlin, secrets, OS noise, local analysis artifacts); .agent/ UN-ignored (now
    visible on GitHub, per GLOBAL RULES repo architecture); .trae/ kept ignored. README
    Project Structure now shows the REAL full tree (app/src/main + app/src/test with tests,
    docs/, tools/, .agent/, HANDOFF.md, gradle). git status verified: only tracked-modified +
    ?? .agent/; check-ignore confirms build/.gradle/.idea/.kotlin/local.properties.

25. **Fobj > 1 regression fixed (user report, systematic-debugging)**: NOT the stability formula
    (Avg/(Avg+Std) clamped [0,1] since commit 2d49502) but `utilitaDaSpesa + cumulativeUtilityOffset`
    at SimulationLogic.kt:367 — offset added AFTER the [0,1] clamp; positive offsets (UI-editable
    per expense) push samples > 1 → avg > 1 → fObjW > 1 in the fobj heatmap. Reproduced red-first
    (max sample 1.1585, offset 0.9) → fix `.coerceAtMost(1.0)` top-only (negative samples keep
    infeasible→0 semantics; solver graze unaffected). Test
    `utilityWithOffset_stays_bounded_and_fobj_never_exceeds_one`; suite 138/0/1 skip.
    NOTE: stored prefs (2026-09-01) have all 50 offsets = 0 — symptom requires offsets > 0.

26. **Graded cost function + spend cap (user request, brainstorm→spec→plan→TDD)**: fobj landscape
    was flat-with-cliffs — floor pinned defensive plans to the same score, 2d49502's binary zero-out
    made every violation a flat 0, no spend cap wasted capital. Fix (spec
    `docs/superpowers/specs/2026-09-02-graded-cost-function-design.md`, plan
    `docs/superpowers/plans/2026-09-02-graded-cost-function.md`): (1) graded penalty
    `100/planYears` subtracted from fObjW AND fObj0 on legacy violation (violators always negative,
    smooth slope); finite negative samples flow into avg; only math-error sentinel still zeroes.
    (2) `computeMaxUtilityMonthlySpend` (curve max-y plateau x; default = daily-max × DAYS_PER_MONTH)
    caps voluntary spend, floor unconditional → solver semantics exact, C* can only decrease.
    Suite 147/0/1 skip, assembleDebug green. fdeg uses CONTINUOUS age (within-year utility drifts
    ~0.002) — plateau assertions must not assume constant samples.

27. **REVISION 2 — spend rule + proportional penalty (user: "sempre quadrata, quadratone piatto")**:
    real-data landscape diagnostic proved the feasible plateau was FLOOR-PINNING (reserve-gated
    draw dead: P1>=0.4 byte-identical, spread 0.017). Restored the old three-branch spend rule:
    pre-pension p3 quota on (netWorth - legacy); retirement = p3-SCALED PMT annuity gated on p3>0
    (goal-solver contract p3=0 -> no draw — ungated version broke 19 tests); forecast brake
    (forecastFinalWithMinimumSpend < legacy+1 -> draw 0). Penalty now SHORTFALL-PROPORTIONAL:
    2.5 x (legacy - finalNetWorth)/legacy (marginal breaches ~-0.04, full breach -2.5; scale bug
    100-vs-2.5 caught by the diagnostic). RESULT: spread 1.61, P2 gradient 0.147->0.189,
    P1=0 -> -1.42. Suite 149/0/1 skip, assembleDebug green. cross_model_regression.py STALE.

28. **REVISION 2b — separation + post-draw brake (user caught the optimizer bug)**: with pure
    proportional penalty, marginal breachers outscored feasible plans -> the GA picked
    legacy-violating plans. Fix: penalty = 1.0 (floor) + 2.5 x shortfallRatio -> every violator
    < 0 <= every feasible plan (test `legacyViolation_penalty_guarantees_separation_from_feasible_plans`);
    forecast brake now POST-DRAW (forecast from month+1 at capital-annuity, June semantics) ->
    the real-data marginal breach vanished (finalCapital 50,006, viol 0, current plan 0.1922;
    P1=0 -> -2.39; P1>=0.2 band feasible with P2 gradient 0.188->0.196). Suite 150/0/1 skip,
    assembleDebug green.

29. **REVISION 3 — June-vs-current A/B + heatmap color anchoring (user: "sempre piatta, anche
    w=0")**: June engine (dc1e7a0) ported verbatim into `JuneEngineLandscapeABTest` and A/B'd on
    the real P1xP2 grid at w in {user, 0, 1}: June is EQUALLY floor-pinned on today's data
    (feasible avg spread ~0.013 vs current 0.042, cap%=0, avg ~= T) -> the engine matches June;
    flatness is data-inherent (141k expenses + 50k legacy vs 100k capital). The VISUAL flatness
    was the color scale: raw fObjW min/max stretched over the penalty range (-2.39) compressed
    the feasible band (0.204..0.246) to ~1.6% -> one color. Fix display-only:
    `SurfaceGrid.anchorColorScaleOnFeasible` + `getFeasibleAnchoredRange` (2D zmin/zmax anchored
    on cells >= 0, violators clamp dark; 3D + delta grids raw). New `PlotlySpecBuilderColorScaleTest`
    (6 contract tests). Suite 157/0/1 skip, assembleDebug green.

30. **DATA AUDIT + engine exoneration (user: "i dati sono sensati? c'e' qualche errore?")**: new
    opt-in `user_real_data_sensibleness_audit` + synthetic `richSurplusData_produces_nonFlat_p1Landscape`.
    Data SENSIBLE (surplus 1,258/mo work vs 853/mo minimum at T=0.2; 141.5k expenses; 169k@57
    inheritance; 100k TFR). Plateau P1>0.322 is FLOOR PHYSICS (1-853/1258=0.322, measured 0.325);
    chasm at the boundary is the separation floor (graded below: -0.83/-1.33/-1.84); engine PROVEN
    rich on synthetic data (spread 0.252, smooth); heatmap code unchanged since June (2 commits
    2026-06-26). Suite 159/0/1 skip, assembleDebug green.

31. **THE ARCANUM (found by the USER): the flat landscape was the THRESHOLD T.** June used
    sogliaMinimaFunzioneUtilita=0.1, now 0.2. T sets minSpend -> floor-bite boundary
    P1* = 1 - minSpend(T)/surplus. ARCANUM CHECK on real data: T=0.2 -> 30/36 feasible,
    spread 0.0236 (yellow plateau) + 6 chasm cells; T=0.1 -> 36/36 feasible (no chasm),
    spread 0.1229 (5.2x) = June landscape back. NO code change needed; engine was exonerated.
    At T=0.1/w=1 the heatmap is smooth single-peaked (~0.19 @ P1~0.15/P2~52; current 0.16).
    Manual: README-goal-solver rule 7, HANDOFF 0g ARCANUM.

32. **LICENSE change: MIT → AGPL-3.0-or-later** (user request: block copy-and-resell on
    Play Store). LICENSE = official gnu.org text (34,523 bytes, verified); SPDX+copyright
    header added to all 100 .kt files under app/src (BOM/EOL preserved, verified 100/100);
    README License section added; HANDOFF 0h. Post-change: full suite + build green.

## Verification

- testDebugUnitTest: 159 tests / 0 failures / 0 errors (1 opt-in skip).

- assembleDebug: green.

- WORKFLOW\.md (F:\MCP\TRADING\WORKFLOW\.md) parts 1-8 written; HANDOFF.md sections 0/0b/0c/0d/0e.

## Remaining (low priority)

- F2: in-context age validation in Surplus form.

- F3 full: threshold/ceiling visibility in main results card (partial via Goal Solver dialog).

- Optional agent write-back tool ("apply optimization results"), PDF/charts/profile agent tools.

## Gotchas learned

- Multiple parallel SearchReplace edits to the SAME file can clobber each other - apply sequentially.

- The built-in edit tool can markdown-escape underscores when editing .md files - verify the file
  after editing and rewrite with Write if artifacts appear. Also escaping apostrophes in Android
  strings.xml: use ' (single backslash), never \\'; inner double quotes must be typographic or
  escaped (AAPT fails on raw ").

- `"template %s and 10%".format(x)` breaks on literal '%': format numbers separately (Locale.US).

- Unit tests DO see real R.string values (AGP 9 app module); R resId -> name mapping in executor works.

- Sensitivity rate rows: delta passed to check() is in "percentage point" units (update divides by 100),
  so dividing by delta gives per-1pp impact - do not re-derive with the absolute step.

- MCP filesystem edit\_file requires BOTH oldText and newText (no pure insertions).

