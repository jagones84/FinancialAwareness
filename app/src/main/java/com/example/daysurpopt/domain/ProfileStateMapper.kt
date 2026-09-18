// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

data class LoadedProfileState(
    val financialInput: FinancialInput,
    val uiInputs: FinancialInputUI,
    val surplusInput: SurplusInput,
    val specificExpenses: List<SpecificExpense>,
    val gaConfig: GAConfigUI
)

object ProfileStateMapper {
    fun createFullProfile(
        financialInput: FinancialInput,
        surplusInput: SurplusInput,
        specificExpenses: List<SpecificExpense>,
        gaConfig: GAConfigUI
    ): FullProfile {
        return FullProfile(
            financialInput = financialInput,
            surplusInput = surplusInput,
            specificExpenses = specificExpenses,
            gaConfig = gaConfig
        )
    }

    fun restoreLoadedProfile(profile: FullProfile): LoadedProfileState {
        val normalizedFinancialInput = profile.financialInput.withDefaultAssumptionCurves()
        return LoadedProfileState(
            financialInput = normalizedFinancialInput,
            uiInputs = FinancialInputUI.from(normalizedFinancialInput),
            surplusInput = profile.surplusInput,
            specificExpenses = profile.specificExpenses,
            gaConfig = profile.gaConfig
        )
    }
}
