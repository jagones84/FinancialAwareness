// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

object Defaults {
    const val DEFAULT_EREDITA = 80000.0
    const val DEFAULT_SOLDI_DA_CONSERVARE = 50000.0
    const val DEFAULT_TFR_NETTO = 65000.0
    const val DEFAULT_TASSO_INTERESSE = 0.02
    const val DEFAULT_TASSO_INTERESSE_DEBITO = 0.07
    const val DEFAULT_SOGLIA_UTILITA = 0.1
    const val DEFAULT_CAPITALE_INIZIALE = 20000.0
    const val DEFAULT_MAX_SPESA_GIORNALIERA_UTILITA = 82.0
    const val DEFAULT_ETA_ATTUALE = 30
    const val DEFAULT_ETA_PENSIONE = 65
    const val DEFAULT_ETA_EREDITA = 55
    const val DEFAULT_ETA_MORTE = 82
    const val DEFAULT_P1_SAVING_RATIO_SURPLUS = 0.40
    const val DEFAULT_P2_ETA_FINE_RISPARMIO_NO_CAPITALE = 51
    const val DEFAULT_P3_PERC_CAPITALE_SPESA_ANNUALE = 0.40
    const val DEFAULT_P4_ETA_ANTICIPATA_INIZIO_SPESA_CAPITALE = 57
    const val DEFAULT_BONUS_STD_WEIGHT = 0.15

    const val BASELINE_MAX_SPESA = 2500.0
    const val BASELINE_CENTER = 1065.0
    const val BASELINE_K = 0.001856

    const val OPENROUTER_SAFETY_SYSTEM_PROMPT = """
You must follow these safety rules:
- Refuse requests that involve illegal wrongdoing, explicit sexual content (especially involving minors), self-harm instructions, hate/harassment, or dangerous instructions.
- If the user requests disallowed content, refuse briefly and offer a safer alternative.
- For financial topics, provide general educational information and encourage consulting a professional when appropriate.
"""
}
