// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.daysurpopt.R
import com.example.daysurpopt.domain.DeltaCalculator
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.FinancialInputUI
import com.example.daysurpopt.ui.common.DeltaInputField
import com.example.daysurpopt.ui.common.DeltaInputFieldInt

@Composable
fun UserInputsScreen(
    navController: NavController,
    viewModel: FinancialViewModel
) {
    val isComparing = viewModel.compareState.isComparing
    val p1 = viewModel.compareState.profile1?.financialInput
    val p2 = viewModel.compareState.profile2?.financialInput

    UserInputsContent(
        uiInputs = viewModel.uiInputs,
        isComparing = isComparing,
        p1 = p1,
        p2 = p2,
        isOptimizing = viewModel.optimizing,
        onUpdateUiInputs = { viewModel.updateUiInputs(it) },
        onUpdateParsedInput = { update -> viewModel.updateParsedInput(update) },
        onBack = { navController.popBackStack() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInputsContent(
    uiInputs: FinancialInputUI,
    isComparing: Boolean,
    p1: FinancialInput?,
    p2: FinancialInput?,
    isOptimizing: Boolean,
    onUpdateUiInputs: (FinancialInputUI) -> Unit,
    onUpdateParsedInput: ((com.example.daysurpopt.domain.FinancialInput) -> com.example.daysurpopt.domain.FinancialInput) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Personal Ages
            Text(stringResource(R.string.section_personal_data), style = MaterialTheme.typography.titleMedium)
            
            DeltaInputFieldInt(
                label = stringResource(R.string.current_age),
                value = uiInputs.etaAttuale,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.etaAttuale, p2.etaAttuale) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(etaAttuale = it))
                    it.toIntOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(etaAttuale = v) } }
                },
                keyboardType = KeyboardType.Number,
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )
            DeltaInputFieldInt(
                label = stringResource(R.string.retirement_age),
                value = uiInputs.etaPensione,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.etaPensione, p2.etaPensione) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(etaPensione = it))
                    it.toIntOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(etaPensione = v) } }
                },
                keyboardType = KeyboardType.Number,
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )
            DeltaInputFieldInt(
                label = stringResource(R.string.end_of_horizon_age),
                value = uiInputs.etaMorte,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.etaMorte, p2.etaMorte) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(etaMorte = it))
                    it.toIntOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(etaMorte = v) } }
                },
                keyboardType = KeyboardType.Number,
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )

            // Financial Data
            Text(stringResource(R.string.section_financial_data), style = MaterialTheme.typography.titleMedium)

            DeltaInputField(
                label = stringResource(R.string.initial_capital_at_current_age),
                value = uiInputs.capitaleIniziale,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.capitaleIniziale, p2.capitaleIniziale) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(capitaleIniziale = it))
                    val normalized = it.replace(',', '.')
                    normalized.toDoubleOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(capitaleIniziale = v) } }
                },
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )
            DeltaInputField(
                label = stringResource(R.string.net_tfr_at_retirement),
                value = uiInputs.tfrNetto,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.tfrNetto, p2.tfrNetto) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(tfrNetto = it))
                    val normalized = it.replace(',', '.')
                    normalized.toDoubleOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(tfrNetto = v) } }
                },
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )
            DeltaInputField(
                label = stringResource(R.string.capital_to_leave),
                value = uiInputs.soldiDaConservare,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.soldiDaConservare, p2.soldiDaConservare) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(soldiDaConservare = it))
                    val normalized = it.replace(',', '.')
                    normalized.toDoubleOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(soldiDaConservare = v) } }
                },
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )
            DeltaInputField(
                label = stringResource(R.string.expected_inheritance),
                value = uiInputs.eredita,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.eredita, p2.eredita) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(eredita = it))
                    val normalized = it.replace(',', '.')
                    normalized.toDoubleOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(eredita = v) } }
                },
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )
            DeltaInputFieldInt(
                label = stringResource(R.string.inheritance_reception_age),
                value = uiInputs.etaRicevimentoEredita,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.etaRicevimentoEredita, p2.etaRicevimentoEredita) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(etaRicevimentoEredita = it))
                    it.toIntOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(etaRicevimentoEredita = v) } }
                },
                keyboardType = KeyboardType.Number,
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )

            // Rates
            Text(stringResource(R.string.section_rates), style = MaterialTheme.typography.titleMedium)

            DeltaInputField(
                label = stringResource(R.string.annual_interest_rate_on_capital),
                value = uiInputs.tassoGuadagnoInteresse,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.tassoGuadagnoInteresse, p2.tassoGuadagnoInteresse) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(tassoGuadagnoInteresse = it))
                    val normalized = it.replace(',', '.')
                    normalized.toDoubleOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(tassoGuadagnoInteresse = v) } }
                },
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )
            DeltaInputField(
                label = stringResource(R.string.annual_interest_rate_on_debt),
                value = uiInputs.tassoInteresseDebito,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.tassoInteresseDebito, p2.tassoInteresseDebito) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(tassoInteresseDebito = it))
                    val normalized = it.replace(',', '.')
                    normalized.toDoubleOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(tassoInteresseDebito = v) } }
                },
                enabled = !isOptimizing,
                isCompareMode = isComparing,
                positiveIsGood = false
            )

            // Utility Parameters
            Text(stringResource(R.string.section_utility_params), style = MaterialTheme.typography.titleMedium)

            DeltaInputField(
                label = stringResource(R.string.extra_daily_spending_for_utility),
                value = uiInputs.valoreSpesaGiornalieraMaxUtilita,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.valoreSpesaGiornalieraMaxUtilita, p2.valoreSpesaGiornalieraMaxUtilita) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(valoreSpesaGiornalieraMaxUtilita = it))
                    val normalized = it.replace(',', '.')
                    normalized.toDoubleOrNull()
                        ?.let { v -> onUpdateParsedInput { i -> i.copy(valoreSpesaGiornalieraMaxUtilita = v) } }
                },
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )
            DeltaInputField(
                label = stringResource(R.string.minimum_utility_threshold),
                value = uiInputs.sogliaMinimaFunzioneUtilita,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.sogliaMinimaFunzioneUtilita, p2.sogliaMinimaFunzioneUtilita) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(sogliaMinimaFunzioneUtilita = it))
                    val normalized = it.replace(',', '.')
                    normalized.toDoubleOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(sogliaMinimaFunzioneUtilita = v) } }
                },
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )
            DeltaInputField(
                label = stringResource(R.string.constant_utility_weight),
                value = uiInputs.bonusStdWeight,
                deltaValue = if (isComparing && p1 != null && p2 != null) DeltaCalculator.deltaOrNull(p1.bonusStdWeight, p2.bonusStdWeight) else null,
                onValueChange = {
                    onUpdateUiInputs(uiInputs.copy(bonusStdWeight = it))
                    val normalized = it.replace(',', '.')
                    normalized.toDoubleOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(bonusStdWeight = v) } }
                },
                enabled = !isOptimizing,
                isCompareMode = isComparing
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UserInputsPreview() {
    UserInputsContent(
        uiInputs = FinancialInputUI(),
        isComparing = false,
        p1 = null,
        p2 = null,
        isOptimizing = false,
        onUpdateUiInputs = {},
        onUpdateParsedInput = {},
        onBack = {}
    )
}
