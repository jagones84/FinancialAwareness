# Graded Cost Function (old-style smooth landscape) — Design

Date: 2026-09-02
Status: Approved (user, 2026-09-02)
Scope: objective shaping only + one engine guard (spend cap). Goal-solver semantics untouched.

## Problem

The fobj landscape over P1-P4 (Charts screen heatmaps/surfaces) is flat with cliffs ("piatta a
scalino"):

1. **Floor plateau**: `finalSpend = max(baseSpend, minimumSpend)` funds the utility threshold from
   capital/debt, so most defensive plans have identical utility (= threshold) → fobj nearly constant.
   Debt hides failure: a sustainable plan and a debt-sustained plan score the same.
2. **Zero cliff**: since commit 2d49502, `calculateObjectivesFromYears` forces the WHOLE objective to
   0.0 on any legacy violation or negative finite utility sample → the infeasible region is a flat
   plain at exactly 0 with no gradient.
3. **Saturation plateau**: above the utility-curve knee extra spending buys no utility; the current
   engine does NOT cap spend there, so capital is wasted, feeding cliff (2).

The ORIGINAL engine (commit dc1e7a0) produced a smooth landscape because it (a) capped spend at the
max-utility spend, (b) used a graded death-year utility penalty (−100 in the death year, impact
−100/N on the average) instead of zeroing the objective, and (c) never zeroed the objective on
violations.

## Decisions (user-approved)

- **Formula kept**: `fObj = Avg * ((1-w) + w * Stability)`, bounded in \[0,1] for feasible plans
  (choice A — do NOT restore the old `(Avg + w'*(Avg/Std))/(1+w')` whose Avg/Std term is
  ill-conditioned; that regression was fixed on 2026-09-02, see HANDOFF 0e).

- **Graded penalties** replace the binary zero-outs (old style).

- **Spend cap at max-utility spend** restored (old engine mechanic), with floor priority preserved.

## Spec

### 1. Graded objective — `calculateObjectivesFromYears` (SimulationLogic.kt)

- REMOVE the `return ObjectiveResults(0, ...)` zero-outs for:

  - `years.any { it.violazioneLascito }`

  - `utilitySamples.any { it < 0.0 }` (finite negative samples = disutility offsets; they now flow
    into the average, graded — old behavior)

- KEEP the zero-out guard for math errors: any non-finite sample or any sample with |u| >= 1e6
  (the `-1e9` exception dummy from `calculateSimulation`'s catch block) → objective 0.0.

- Graded legacy penalty:

  ```
  base      = avg * ((1-w) + w * stability)          // unchanged formula, real samples
  planYears = years.size
  fObjW     = base - DEATH_LEGACY_PENALTY / planYears   // when violazioneLascito, else base
  ```

  with `DEATH_LEGACY_PENALTY = 100.0`.

  - Separation: while `planYears < 100` (any horizon expressible with death age <= current age +
    99 years), `100/planYears > 1 >= base` → violating plans are always strictly negative, feasible
    ones in \[0, 1]. Horizons >= 100 years would weaken full separation but still grade.

  - Grading: within violating plans `base` still varies continuously → smooth slope, no flat cliff.

  - `avgUtilita`, `stdDev`, `stabilityIndex`, `isFeasible`, `finalCapital`, `legacyGap` are computed
    from the REAL samples and are NOT affected by the penalty.

- Known accepted edge: plans with finite negative samples at w = 1 → stability = 0 → base = 0.
  Rare (requires negative utility offsets); documented, not mitigated (formula is user-locked).

### 2. Spend cap — monthly simulation loop (SimulationLogic.kt)

- Compute `maxUtilSpendMonthly` (curve-aware, unified rule):

  - usable curve (>= 2 finite points): `curveMax` = max point y; `xSat` = smallest curve-point x
    with `y >= curveMax` (beyond `xSat` the clamped interpolation is constant at `curveMax` — extra
    spend buys no utility); cap = `xSat * DAYS_PER_MONTH`.
    For the DEFAULT generated curve (ceiling \~0.9347 at its last point
    `valoreSpesaGiornalieraMaxUtilita`) this yields exactly the old `valoreSpesaMensileMaxUtilita`.

  - no usable curve (raw default sigmoid): `valoreSpesaGiornalieraMaxUtilita * DAYS_PER_MONTH`
    (the old `valoreSpesaMensileMaxUtilita`).
    (Amendment during planning: the original "no cap when the curve never reaches 1.0" clause is
    replaced by this unified max-y rule — the default curve never reaches 1.0 and must still be
    capped at its plateau start.)

- Spend rule becomes:

  ```
  finalSpend = max(min(baseSpend, maxUtilSpendMonthly), minimumSpend)
  ```

  The cap trims the VOLUNTARY spend only; the floor is unconditional (deliberate deviation from the
  old `min(max(desired, minima), maxUtil)`, which capped the floor too). This preserves the current
  goal-solver semantics exactly: floor-funded threshold utility, shortfall becomes debt.

- Effects: no waste above saturation → capital preserved → less debt, fewer violating plans
  (the zero/penalty region shrinks), solver C\* can only decrease, locus stays non-increasing in P1.

### 3. Unchanged (explicit non-goals)

- Goal solver: feasibility predicate reads engine outputs directly (debt, legacy, utility samples);
  binding modes (UTILITY/LEGACY/DEBT graze), bisection, locus monotonicity — all intact.

- Floor→debt semantics, death-only legacy check, reserve-gated P3 draw.

- Sensitivity analysis (uses raw average utility), UI strings, PDF, agent tools.

- fobj formula and w semantics.

## Testing (TDD)

New RED-first tests:

1. Legacy-violating plan → `fObjW == base - 100.0/planYears` (< 0), feasible plan → `fObjW == base`.
2. Two violating plans with different sample sets → different fobj (gradient exists, no flat cliff).
3. Spend cap: baseSpend above maxUtil → finalSpend == maxUtil; final capital higher than the same
   run without the cap; utility unchanged (saturated).
4. Conflict `minimumSpend > maxUtilSpend` → floor wins (finalSpend == minimumSpend, utility ==
   threshold — solver semantics preserved).
5. Exception dummy (`-1e9`) still forces objective 0.0.

Updates: tests that lock the binary zero-out semantics move to the graded expectations.
Full suite re-run (138+ tests) + `assembleDebug`.

## Visible consequences (expected, user-approved)

- Solver C\* values shown in the study table/locus decrease (less waste → less capital required).

- Applied plans spend less above the saturation knee → higher final net worth.

- fobj charts can show NEGATIVE values for violating plans (colorbar extends below 0) — this is the
  graded failure signal.

- The fobj formula caption (`charts_fobj_formula`) stays accurate; no string changes.

## Files

- `app/src/main/java/com/example/daysurpopt/logic/SimulationLogic.kt` — objective + spend cap.

- `app/src/test/java/com/example/daysurpopt/logic/SimulationLogicTest.kt` (+ possibly
  `GoalSolverCrossValidationTest` expectations if any locked values shift) — tests.

- Docs: HANDOFF.md (0g), `.agent/memory/activeContext.md`, `.agent/README-goal-solver.md` (C\* note).

## Out of scope

- Old annuity spend rule (wReq/PMT) replacing the P3 draw — rejected: would remove P3 semantics.

- Debt-burden terms in the objective (YAGNI; the cap already limits structural debt).

- Restoring the Avg/Std objective formula (ill-conditioned; already rejected by user choice A).

