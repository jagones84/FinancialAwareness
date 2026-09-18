# FinancialAwareness

> Simulate your whole financial life, month by month — then let an optimizer find the plan.

Android app (Kotlin + Jetpack Compose) for life-long financial planning.

Most planners only *simulate*: you tweak inputs by hand and look at the curve. This one
**optimizes** the plan against an explicit utility (happiness) function, and it answers the
*inverse* question — **how much capital do I need today to stop working at age X?**

Everything runs **on-device**. No cloud, no account, no bank linking, no telemetry.

---

## Why this is different

- **It optimizes, not just simulates.** Four plan parameters are searched with a **genetic
  algorithm** plus coordinate-search refinement — modes: *True-Scalar*, *Pareto-Knee*,
  *Pareto-Front*.
- **The objective has two terms.** Not just average happiness, but its *stability*:
  `StabilityScore = Avg / (Avg + StdDev)`, combined as
  `fScalar = AvgUtility × ((1 − w) + w × Stability)` — or weight-free in the Pareto modes.
- **It solves the inverse problem.** The *Goal Solver* returns the **locus** of minimum
  initial capital versus saving ratio — a boundary curve, not a single guess.
- **The AI agent is on a leash.** The LLM never invents numbers: it calls the *same engine
  the GUI calls*, and parity is locked by tests.
- **The math is checkable.** The engine has a cross-model regression against an
  independent Python reference implementation, tolerance **1e-9**. See [`METHOD.md`](METHOD.md).

## What it does

### 1. Model your life
Current / pension / death ages · initial capital · inheritance · **severance pay (TFR)** ·
capital to keep at death · interest and debt rates · the **utility (happiness) curve** and
the **age-degradation curve**, both editable by dragging points · a **happiness
threshold**: the engine always spends at least what is needed to stay above it, and any
shortfall becomes **debt**.

### 2. Surplus calculator
Detailed monthly income and outgoings for both the working and the pension phase: 13th/14th
salaries, bonuses, rent/mortgage until a chosen age, category-based spending.

### 3. Simulation engine
Month-by-month: monthly utility samples, capital path, debt handling, bequest check,
reserve-first capital rule, one-time expenses with a cumulative utility offset from the
event age onward.

### 4. Optimization (genetic algorithm)
Free optimization of P1–P4 — *saving ratio, saving end age, annual capital draw %,
early draw start* — maximizing

```
Fobj = AvgUtility × ((1 − w) + w × Stability)
```

with modes `TRUE_SCALAR`, `PARETO_KNEE`, `PARETO_FRONT` and configurable
population / generations / crossover / mutation. Results are applied back to the live plan
and re-simulated.

### 5. Sensitivity analysis
Ranked impact of every parameter on average utility, per unit step (percentage points,
years, 10 k€, +100 €/month of extra earnings).

### 6. Goal Solver — the inverse question
*"How much capital do I need **today** to quit at age X and never drop below my happiness
threshold T?"*

The answer is a **locus** `Λ = { (P1, C*(P1)) }` — the exact feasibility boundary, computed
by bisection **on the official engine itself** (no closed-form approximation). Shown as a
table plus a 2D chart with your current position marked, tap-to-probe exact values, and
one-tap **Apply**.

Test-proven properties: *grazing* (the history touches the threshold at the boundary, to
machine precision), *monotonicity* in P1, *plateau*, and *capital-neutrality* above the
boundary.

### 7. Charts and report
Interactive Plotly charts (utility history, capital path, objective surface / heatmap over
P1–P2, Pareto front scatter), a native Canvas study chart, and **PDF export**.

### 8. AI agent (OpenRouter — your own key)
Multi-agent analysis and report generation **grounded in the app's real engine results**.
Tools: `RUN_SIMULATION`, `RUN_RETIREMENT_SOLVER`, `RUN_SENSITIVITY`, optimization, `FETCH_PAGE`.

### 9. Profiles
Save / load / delete complete parameter profiles, **compare them side by side**, and a
first-launch quick-start wizard.

## Verify the math yourself

This is the part that matters for a tool that touches your money:

- **Cross-model regression** against an independent Python reference implementation,
  tolerance **1e-9**: [`tools/cross_model_regression.py`](tools/cross_model_regression.py)
- **GUI/agent parity**: `AgentToolParityTest` requires the agent's simulation output to
  equal the direct engine computation, with and without overrides
- **Goal Solver cross-validation**: `GoalSolverCrossValidationTest`,
  `GoalSolverRandomCrossValidationTest`
- **Frozen domain specs** (what is implemented, and what is *not*) in [`.agent/`](.agent/)

The formal model — engine, objective, feasibility predicate, bisection, and the known
ceiling constraint — is written down in [`METHOD.md`](METHOD.md).

## Tech stack

Kotlin 2.2.10 · Jetpack Compose (Material 3) · Coroutines · Gson · Plotly 2.30.0 (bundled,
rendered in a WebView) · SharedPreferences persistence (no cloud, no telemetry) ·
OpenRouter REST API (optional, user-provided key) · JUnit

## Requirements

- Android Studio (recent version)
- JDK 17
- Android SDK: compile/target SDK 35, minSdk 24

## Build and test

```bash
git clone https://github.com/jagones84/FinantialAwareness.git
cd FinantialAwareness

./gradlew testDebugUnitTest    # unit tests
./gradlew assembleDebug        # build the APK
```

On Windows use `gradlew.bat` instead. The APK lands in
`app/build/outputs/apk/debug/app-debug.apk`.

## AI setup

Open the **AI Agent** screen in the app, insert your **OpenRouter API key** (stored on the
device only) and pick a model — default: `qwen/qwen3.7-plus`.

## Status and honest limits

- All P0/P1 items of the last engineering pass are **done**: agent parity, Goal Solver,
  GUI flow rework.
- **Known model constraint:** with the *default* curves the utility ceiling is ≈ 0.9347, so
  maximum achievable happiness at age 82 is ≈ 0.295. A 0.3 threshold is therefore
  **unreachable at 80+ regardless of capital** unless you edit the degradation curve. The
  app validates and warns — see [`METHOD.md` §5](METHOD.md).
- **Still open:** age validation in the surplus form, threshold/ceiling visibility in the
  main results card, agent access to PDF export / charts / profile management, and the
  optional "apply optimization results" write-back tool.
- **Android-only** for now.
- **Not financial advice.** This is an optimization tool over a model *you* define.

## License

Licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later).

If you run a modified version as a network service, the AGPL requires you to offer users
the complete corresponding source code under the same license. See [`LICENSE`](LICENSE).
