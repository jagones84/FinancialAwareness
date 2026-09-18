# Method

The formal model behind FinancialAwareness. Everything here is implemented in the app and
covered by tests; nothing is a simplification of the real engine.

> This document exists because a tool that touches your money must be checkable. See
> [Correctness evidence](#6-correctness-evidence) for how the numbers are verified.

---

## 1. The simulation — the only oracle

The engine is a **deterministic, monthly-step** simulation. There is no closed-form
shortcut anywhere in the app: the Goal Solver, the optimizer and the AI agent all call
this same function.

- monthly rate from the annual rate `r`: `rm = (1 + r)^(1/12) − 1`
- **reserve-first** capital rule
- explicit **debt bucket** — a shortfall is not clipped, it becomes debt
- **one-time expenses** at chosen ages, with a cumulative utility offset applying from the
  event age onward
- **forced minimum spend**: the engine always spends at least what is required to keep
  utility at or above the happiness threshold, drawing capital to do it

Outputs per run: the monthly utility history `{u_m}`, the year-end debts `{d_y}`, and the
death net worth `NW`.

## 2. Utility (happiness)

Utility is an explicit, user-editable function — not a hidden heuristics:

- `u(spend)` — baseline logistic over spending
- `fdeg(age)` — age-degradation curve (editable points)
- monthly sample: `u_m = u(spend_m) × fdeg(age_m)`

Both curves are editable in the app by dragging points, because the whole point is that
*your* happiness function is not my default.

## 3. The objective

```
StabilityScore = Avg / (Avg + StdDev)

fScalar = AvgUtility × ((1 − w) + w × StabilityScore)      w ∈ [0, 1]
```

`w` is a single direct weight: `w = 0` optimizes raw average happiness, `w = 1` optimizes
a plan that is happy *and* steady.

Pareto modes are **weight-free**: the knee is selected by normalized chord distance, so
the user never has to invent a `w`.

## 4. The inverse problem — Goal Solver

**Question:** *how much capital do I need TODAY to quit work at age X and never let
happiness drop below threshold T?*

**User-fixed constants θ:** current age `a0`, death age `ad`, stop-work age `X`, income
streams and their end dates, scheduled expenses / inheritance / TFR schedules, interest
rates, utility and degradation curves, bequest target `L`, threshold `T`.

**Plan shape (fixed by the solver's semantics, not free):**
`etaPensione = P2 = P4 = X`, `P3 = 0` — the utility floor solves the monthly draw.

**Free unknowns (2):** initial capital `C0` and saving ratio `P1`.

**Oracle:** the official simulation `S(P1, C0)` — zero simplifications.

**Feasibility:**

```
Feas(P1, C0)  ⟺  min_m u_m ≥ T − 1e-6  ∧  max_y d_y ≤ 1e-6  ∧  NW ≥ L − 1
```

The utility clause is a defensive mirror: the engine floor keeps `u ≥ T` at *any* capital
(the shortfall becoming debt), so the **binding** clauses are solvency and bequest.

**Boundary, for each P1:**

```
C*(P1) = inf { C0 ≥ 0 : Feas(P1, C0) }
```

found by **bisection on the oracle**: 2 boundary checks + `ceil(log2(3e6/1000)) = 12`
steps = **14 official-simulation launches per row**, capital tolerance **1000 €**.

**Answer — the LOCUS:**

```
Λ = { (P1, C*(P1)) : P1 ∈ [0, 1] }
```

That is the boundary curve of the feasible region: for *every* saving ratio, the minimum
capital below which the plan breaks. Strictly more information than one limit pair.

### Test-proven properties

1. **Grazing** — at `(P1, C*(P1))` the history touches the threshold: `min_m u_m = T` to
   machine precision (`−5.55e-17`); below `C* − 2×tolerance` the plan is infeasible.
2. **Monotonicity** — `C*(·)` is non-increasing in `P1`: more saving needs less capital.
3. **Plateau** — where the floor binds over the whole post-stop horizon, net accumulation
   is `surplus − minSpend`, which is `P1`-independent ⇒ `C*` is flat (on real data:
   plateau from 30 %).
4. **Capital-neutrality above the boundary** — for `C0 ≥ C*(P1)` the utility history is
   **identical**; only the bequest grows.

### Worked example (real data, in-repo test)

| Stop work at | Threshold T | Minimum capital needed |
|---|---|---|
| 40 | 0.30 | ≈ 275 391 € |
| 40 | 0.25 | ≈ 222 656 € |

Verified: `−2 000 €` ⇒ infeasible. Monotonic in `T` ✓.

## 5. Known ceiling constraint

With the **default** curves the utility ceiling is `≈ 0.9347` (the baseline logistic
evaluated at `BASELINE_MAX_SPESA` never reaches 1.0). Therefore

```
max achievable happiness ≈ 0.9347 × fdeg(age)
```

At age 82 with default degradation that is `≈ 0.295`. So **a 0.3 threshold is unreachable
at 80+ regardless of capital** unless you edit the degradation curve (e.g. a floor of 0.5
restores feasibility). The app must validate `T ≤ 0.9347 × min(fdeg)` and warn — this is a
model property, not a bug, and hiding it would be the actual bug.

## 6. Correctness evidence

- **Cross-model regression**: the engine is compared against an independent Python
  reference implementation with tolerance **1e-9** (`tools/cross_model_regression.py`).
- **GUI/agent parity**: `AgentToolParityTest` requires the agent's `RUN_SIMULATION` output
  (objective, final capital, average utility) to equal the direct engine computation,
  with and without overrides — including the happiness-threshold override.
- **Goal Solver cross-validation**: `GoalSolverCrossValidationTest` and
  `GoalSolverRandomCrossValidationTest`.
- **Engine unit tests**: `SimulationLogicTest`.

## 7. Deliberately NOT in the model

Stated plainly, so nobody assumes more than the app delivers:

- **No stochastic / Monte Carlo layer.** The engine is deterministic with explicit curves.
  "Probability of success" is a *different* question and belongs to a different tool.
- **No tax engine and no country pension rules.** You encode them as income streams.
- **Stage 2 of the inverse problem** (automatically pick the locus row maximizing `Fobj` —
  a lexicographic formulation) is **left to the user**: the locus *is* stage 1. A per-row
  `Fobj` column would automate stage 2; it is not implemented yet.
- **Android-only** at present.

## 8. Not financial advice

This is an optimization tool over a model *you* define. It does not predict markets, and
its answers are only as meaningful as your inputs and curves.
