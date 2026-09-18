// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProfileStateMapperTest {

    @Test
    fun createFullProfile_usesCurrentSurplusState() {
        val currentInputs = FinancialInput(eredita = 1234.0)
        val currentSurplus = SurplusInput(
            stipendioMensile = 4321.0,
            mutuoAffitto = 876.0,
            mutuoAffittoFinoEta = 59,
            altroPensione = 222.0
        )
        val currentExpenses = listOf(SpecificExpense(age = 55, amount = 1000.0, utilityOffset = -0.2))
        val currentGaConfig = GAConfigUI(popSize = "250")

        val profile = ProfileStateMapper.createFullProfile(
            financialInput = currentInputs,
            surplusInput = currentSurplus,
            specificExpenses = currentExpenses,
            gaConfig = currentGaConfig
        )

        assertEquals(currentInputs, profile.financialInput)
        assertEquals(currentSurplus, profile.surplusInput)
        assertEquals(currentExpenses, profile.specificExpenses)
        assertEquals(currentGaConfig, profile.gaConfig)
    }

    @Test
    fun restoreLoadedProfile_preservesSurplusAndBuildsUiInputsFromNormalizedFinancialInputs() {
        val storedProfile = FullProfile(
            financialInput = FinancialInput(
                capitaleIniziale = 9999.0,
                utilityCurvePoints = null,
                degradationCurvePoints = null
            ),
            surplusInput = SurplusInput(
                stipendioMensile = 3210.0,
                bonusEventualiPersonaliMensile = 400.0,
                bonusEventualiPersonaliMensileFinoEta = 54,
                mutuoAffitto = 650.0,
                mutuoAffittoFinoEta = 61,
                shoppingPensione = 77.0
            ),
            specificExpenses = listOf(SpecificExpense(age = 67, amount = 2500.0, utilityOffset = 0.1)),
            gaConfig = GAConfigUI(generations = "75")
        )

        val restored = ProfileStateMapper.restoreLoadedProfile(storedProfile)

        assertEquals(storedProfile.surplusInput, restored.surplusInput)
        assertEquals(storedProfile.specificExpenses, restored.specificExpenses)
        assertEquals(storedProfile.gaConfig, restored.gaConfig)
        assertNotNull(restored.financialInput.utilityCurvePoints)
        assertNotNull(restored.financialInput.degradationCurvePoints)
        assertEquals(FinancialInputUI.from(restored.financialInput), restored.uiInputs)
    }
}
