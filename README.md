# FinancialAwareness

Android app for personal financial awareness: simulate your financial life month-by-month, optimize your saving and spending parameters, measure parameter sensitivity, plan an anticipated retirement, and get AI-powered analysis grounded in your own simulation results. All data stays on your device.

## Features

- **Financial setup** — current / pension / death ages, initial capital, inheritance, severance pay (TFR), capital to keep at death, interest and debt rates, happiness threshold, customizable utility and age-degradation curves (editable, draggable points).
- **Surplus calculator** — detailed monthly income and outgoings for the working and pension phases (13th/14th salaries, bonuses, rent/mortgage until a chosen age, category-based spending).
- **Scheduled expenses** — one-off expenses at chosen ages with an optional utility offset (e.g. a trip that also buys happiness).
- **Simulation engine** — the official month-by-month engine: monthly utility samples, capital path, debt handling and bequest check. The utility floor always spends at least what is needed to keep happiness at the threshold; any shortfall becomes debt.
- **Optimization (genetic algorithm)** — free optimization of the plan parameters P1–P4 (saving ratio, saving end age, annual capital draw percentage, early capital draw start) maximizing `Fobj = AvgUtility × ((1−w) + w × Stability)`, with modes `TRUE_SCALAR`, `PARETO_KNEE` and `PARETO_FRONT`, and configurable population / generations / crossover / mutation.
- **Sensitivity analysis** — ranked impact of every parameter on the average utility (per unit step: percentage points, years, 10k €, +100 €/month of extra earnings).
- **Anticipated Retirement Study** — the inverse question: *how much capital do I need today to quit work at age X and never drop below my happiness threshold?* The answer is a **table (locus)** of saving-ratio P1 vs minimum initial capital, a **2D chart** with your current position marked in red, hover/tap probe with exact values, and one-tap **Apply** that installs the whole plan into the simulation.
- **Charts** — interactive Plotly charts (utility history, capital path, objective surface / heatmap over P1–P2, Pareto front scatter) plus the native study chart.
- **PDF report** — export of the current analysis.
- **AI agent (OpenRouter)** — multi-agent analysis and report generation grounded in the app's real engine results (no hallucinated numbers); tools include simulation, optimization, sensitivity and the anticipated retirement study. Uses your own OpenRouter API key.
- **Profiles & Quick Start** — save/load complete parameter profiles, side-by-side comparison, first-launch quick-start wizard.
- **Languages** — English, Italiano, Español.

## Tech Stack

- Kotlin, Jetpack Compose (Material 3)
- Coroutines
- Gson
- Plotly (bundled, rendered in a WebView) for full-screen charts; native Compose Canvas for the study chart
- SharedPreferences persistence (no cloud, no telemetry)
- OpenRouter REST API (optional, user-provided key)
- JUnit unit tests

## Requirements

- Android Studio (recent version)
- JDK 17
- Android SDK (compile/target SDK 35, minSdk 24)

## Setup

```bash
git clone https://github.com/jagones84/FinantialAwareness.git
cd FinantialAwareness
./gradlew.bat assembleDebug
```

Install the generated APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Build And Test

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

## AI Setup

- Open the AI Agent screen in the app
- Insert your OpenRouter API key (stored on the device only)
- Select the desired model — default: `qwen/qwen3.7-plus`

## Project Structure

```
FinancialAwareness/
├── app/
│   └── src/
│       ├── main/                            # The Android application
│       │   └── java/com/example/daysurpopt/
│       │       ├── ui/                      # Compose screens, dialogs, charts
│       │       │                            #   (Plotly WebView for full-screen charts,
│       │       │                            #    native Canvas for the study chart)
│       │       ├── logic/                   # Simulation engine, genetic optimizer,
│       │       │                            #   sensitivity, retirement study, PDF export
│       │       ├── domain/                  # Data models (inputs, curves, results)
│       │       ├── agent/                   # AI agent tooling (prompts, tool executor,
│       │       │                            #   OpenRouter client)
│       │       └── data/                    # Persistence helpers
│       └── test/                            # 137 JVM unit tests (JUnit) — engine,
│                                            #   retirement study, sensitivity, charts,
│                                            #   agent tools
├── docs/                                    # Project documentation
├── tools/                                   # Regression tooling (cross-model scenario runner)
├── .agent/                                  # Engineering domain manuals + agent memory
├── HANDOFF.md                               # Current fix-campaign status and history
├── build.gradle.kts / settings.gradle.kts
└── gradle/                                  # Wrapper and dependency catalog
```

## Notes

- All user data stays on-device (SharedPreferences); nothing is uploaded
- Keep `local.properties`, `local.properties`-derived secrets and signing material out of the repository
- No secrets are committed: the OpenRouter key is entered in-app and stored locally

## License

FinancialAwareness — Copyright (c) 2026 jagones84

Licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later).

Anyone who redistributes this program — modified or not, in source or compiled form,
including publishing a derivative app on Google Play or any other store — must release
the complete corresponding source code under the same license. See [LICENSE](LICENSE).
