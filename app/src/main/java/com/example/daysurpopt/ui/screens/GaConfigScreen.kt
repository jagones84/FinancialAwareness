// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.daysurpopt.R
import com.example.daysurpopt.data.GaConfigRepository
import com.example.daysurpopt.domain.GAConfigUI
import com.example.daysurpopt.ui.common.InputField
import com.example.daysurpopt.ui.common.OptimizationParameterGroupMuted
import com.example.daysurpopt.ui.theme.InputBorderFocused
import com.example.daysurpopt.ui.theme.InputBorderUnfocused
import com.example.daysurpopt.ui.theme.InputCursor
import com.example.daysurpopt.ui.theme.InputLabelFocused
import com.example.daysurpopt.ui.theme.InputLabelUnfocused
import com.example.daysurpopt.ui.theme.InputText

@Composable
fun GaConfigScreen(
    navController: NavController,
    viewModel: FinancialViewModel
) {
    GaConfigContent(
        gaUI = viewModel.gaUI,
        onUpdateGaUI = { viewModel.updateGaConfig(it) },
        onBack = { navController.popBackStack() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaConfigContent(
    gaUI: GAConfigUI,
    onUpdateGaUI: (GAConfigUI) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val gaTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = InputBorderFocused,
        unfocusedBorderColor = InputBorderUnfocused,
        focusedLabelColor = InputLabelFocused,
        unfocusedLabelColor = InputLabelUnfocused,
        focusedTextColor = InputText,
        unfocusedTextColor = InputText,
        cursorColor = InputCursor
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.ga_params_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.save_and_return))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OptimizationParameterGroupMuted(title = stringResource(R.string.ga_params_group_title)) {
                InputField(
                    label = stringResource(R.string.initial_population),
                    value = gaUI.popSize,
                    onValueChange = { onUpdateGaUI(gaUI.copy(popSize = it)) },
                    colors = gaTextFieldColors,
                    keyboardType = KeyboardType.Number
                )
                InputField(
                    label = stringResource(R.string.num_generations),
                    value = gaUI.generations,
                    onValueChange = { onUpdateGaUI(gaUI.copy(generations = it)) },
                    colors = gaTextFieldColors,
                    keyboardType = KeyboardType.Number
                )
                InputField(
                    label = stringResource(R.string.crossover_probability),
                    value = gaUI.pc,
                    onValueChange = { onUpdateGaUI(gaUI.copy(pc = it)) },
                    colors = gaTextFieldColors
                )
                InputField(
                    label = stringResource(R.string.mutation_probability),
                    value = gaUI.pm,
                    onValueChange = { onUpdateGaUI(gaUI.copy(pm = it)) },
                    colors = gaTextFieldColors
                )
                InputField(
                    label = stringResource(R.string.min_range_p),
                    value = gaUI.minRange,
                    onValueChange = { onUpdateGaUI(gaUI.copy(minRange = it)) },
                    colors = gaTextFieldColors
                )
                InputField(
                    label = stringResource(R.string.max_range_p),
                    value = gaUI.maxRange,
                    onValueChange = { onUpdateGaUI(gaUI.copy(maxRange = it)) },
                    colors = gaTextFieldColors
                )
                InputField(
                    label = stringResource(R.string.optimization_goal),
                    value = gaUI.maximize,
                    onValueChange = { onUpdateGaUI(gaUI.copy(maximize = it)) },
                    colors = gaTextFieldColors,
                    keyboardType = KeyboardType.Number
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GaConfigPreview() {
    GaConfigContent(
        gaUI = GAConfigUI(),
        onUpdateGaUI = {},
        onBack = {}
    )
}
