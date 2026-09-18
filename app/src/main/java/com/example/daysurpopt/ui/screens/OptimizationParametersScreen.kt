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
import androidx.compose.ui.graphics.Color
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
import com.example.daysurpopt.ui.common.OptimizationParameterGroup

@Composable
fun OptimizationParametersScreen(
    navController: NavController,
    viewModel: FinancialViewModel
) {
    val isComparing = viewModel.compareState.isComparing
    val p1 = viewModel.compareState.profile1?.financialInput
    val p2 = viewModel.compareState.profile2?.financialInput
    
    OptimizationParametersContent(
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
fun OptimizationParametersContent(
    uiInputs: FinancialInputUI,
    isComparing: Boolean,
    p1: FinancialInput?,
    p2: FinancialInput?,
    isOptimizing: Boolean,
    onUpdateUiInputs: (FinancialInputUI) -> Unit,
    onUpdateParsedInput: ((FinancialInput) -> FinancialInput) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val optimizationTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.optimization_parameters_title)) },
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
            Text(
                text = stringResource(R.string.optimization_parameters_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OptimizationParameterGroup(title = "") {
                DeltaInputField(
                    label = stringResource(R.string.p1_surplus_fraction_saved),
                    value = uiInputs.p1SavingRatioSurplus,
                    deltaValue = if (isComparing && p1 != null && p2 != null) 
                        DeltaCalculator.deltaOrNull(p1.p1SavingRatioSurplus, p2.p1SavingRatioSurplus) else null,
                    onValueChange = {
                        onUpdateUiInputs(uiInputs.copy(p1SavingRatioSurplus = it))
                        val normalized = it.replace(',', '.')
                        normalized.toDoubleOrNull()
                            ?.let { v -> onUpdateParsedInput { i -> i.copy(p1SavingRatioSurplus = v) } }
                    },
                    colors = optimizationTextFieldColors,
                    enabled = !isOptimizing,
                    isCompareMode = isComparing
                )
                DeltaInputFieldInt(
                    label = stringResource(R.string.p2_end_of_savings_age),
                    value = uiInputs.p2EtaFineRisparmioNoCapitale,
                    deltaValue = if (isComparing && p1 != null && p2 != null) 
                        DeltaCalculator.deltaOrNull(p1.p2EtaFineRisparmioNoCapitale, p2.p2EtaFineRisparmioNoCapitale) else null,
                    onValueChange = {
                        onUpdateUiInputs(uiInputs.copy(p2EtaFineRisparmioNoCapitale = it))
                        it.toIntOrNull()?.let { v -> onUpdateParsedInput { i -> i.copy(p2EtaFineRisparmioNoCapitale = v) } }
                    },
                    colors = optimizationTextFieldColors,
                    keyboardType = KeyboardType.Number,
                    enabled = !isOptimizing,
                    isCompareMode = isComparing
                )
                DeltaInputField(
                    label = stringResource(R.string.p3_annual_capital_spending_share),
                    value = uiInputs.p3PercentualeCapitaleDaSpendereAnnualmente,
                    deltaValue = if (isComparing && p1 != null && p2 != null) 
                        DeltaCalculator.deltaOrNull(p1.p3PercentualeCapitaleDaSpendereAnnualmente, p2.p3PercentualeCapitaleDaSpendereAnnualmente) else null,
                    onValueChange = {
                        onUpdateUiInputs(uiInputs.copy(p3PercentualeCapitaleDaSpendereAnnualmente = it))
                        val normalized = it.replace(',', '.')
                        normalized.toDoubleOrNull()
                            ?.let { v -> onUpdateParsedInput { i -> i.copy(p3PercentualeCapitaleDaSpendereAnnualmente = v) } }
                    },
                    colors = optimizationTextFieldColors,
                    enabled = !isOptimizing,
                    isCompareMode = isComparing
                )
                DeltaInputFieldInt(
                    label = stringResource(R.string.p4_start_of_capital_spending_age),
                    value = uiInputs.p4EtaAnticipataInizioSpesaCapitale,
                    deltaValue = if (isComparing && p1 != null && p2 != null) 
                        DeltaCalculator.deltaOrNull(p1.p4EtaAnticipataInizioSpesaCapitale, p2.p4EtaAnticipataInizioSpesaCapitale) else null,
                    onValueChange = {
                        onUpdateUiInputs(uiInputs.copy(p4EtaAnticipataInizioSpesaCapitale = it))
                        it.toIntOrNull()
                            ?.let { v -> onUpdateParsedInput { i -> i.copy(p4EtaAnticipataInizioSpesaCapitale = v) } }
                    },
                    colors = optimizationTextFieldColors,
                    keyboardType = KeyboardType.Number,
                    enabled = !isOptimizing,
                    isCompareMode = isComparing
                )
            }
        }
    }
}

@Preview
@Composable
fun OptimizationParametersPreview() {
    OptimizationParametersContent(
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
