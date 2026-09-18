// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.daysurpopt.R
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SpecificExpenseUI
import com.example.daysurpopt.ui.common.DeltaInputField
import com.example.daysurpopt.ui.common.DeltaInputFieldInt
import java.util.Locale

@Composable
fun SpecificExpensesScreen(
    navController: NavController,
    viewModel: FinancialViewModel
) {
    var expenses by remember { mutableStateOf(viewModel.specificExpenses) }
    var uiExpenses by remember {
        mutableStateOf(expenses.map { SpecificExpenseUI(it.age.toString(), String.format(Locale.US, "%.2f", it.amount), String.format(Locale.US, "%.4f", it.utilityOffset)) })
    }

    val isComparing = viewModel.compareState.isComparing
    val p2Expenses = if (isComparing) viewModel.profile2Expenses else emptyList()

    SpecificExpensesContent(
        expenses = expenses,
        uiExpenses = uiExpenses,
        isComparing = isComparing,
        p2Expenses = p2Expenses,
        onUpdateExpenses = { newExpenses, newUiExpenses ->
            expenses = newExpenses
            uiExpenses = newUiExpenses
            viewModel.updateSpecificExpenses(newExpenses)
        },
        onBack = { navController.popBackStack() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecificExpensesContent(
    expenses: List<SpecificExpense>,
    uiExpenses: List<SpecificExpenseUI>,
    isComparing: Boolean,
    p2Expenses: List<SpecificExpense>,
    onUpdateExpenses: (List<SpecificExpense>, List<SpecificExpenseUI>) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.scheduled_expenses_title)) },
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringResource(R.string.scheduled_expenses_description),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            var sortMenuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopStart)) {
                OutlinedButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sort_expenses))
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Sort"
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_age)) },
                        onClick = {
                            val sorted = expenses.sortedBy { it.age }
                            val sortedUi = sorted.map { SpecificExpenseUI(it.age.toString(), String.format(Locale.US, "%.2f", it.amount), String.format(Locale.US, "%.4f", it.utilityOffset)) }
                            onUpdateExpenses(sorted, sortedUi)
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_amount)) },
                        onClick = {
                            val sorted = expenses.sortedByDescending { it.amount }
                            val sortedUi = sorted.map { SpecificExpenseUI(it.age.toString(), String.format(Locale.US, "%.2f", it.amount), String.format(Locale.US, "%.4f", it.utilityOffset)) }
                            onUpdateExpenses(sorted, sortedUi)
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_utility)) },
                        onClick = {
                            val sorted = expenses.sortedByDescending { it.utilityOffset }
                            val sortedUi = sorted.map { SpecificExpenseUI(it.age.toString(), String.format(Locale.US, "%.2f", it.amount), String.format(Locale.US, "%.4f", it.utilityOffset)) }
                            onUpdateExpenses(sorted, sortedUi)
                            sortMenuExpanded = false
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            uiExpenses.forEachIndexed { index, uiExpense ->
                // Compare with P2 at same index
                val p2Expense = p2Expenses.getOrNull(index)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Age Field (Neutral Delta = Grey)
                    val deltaAge = if (isComparing && p2Expense != null) p2Expense.age - expenses[index].age else null
                    
                    DeltaInputFieldInt(
                        label = stringResource(R.string.age_label_format, index + 1),
                        value = uiExpense.age,
                        deltaValue = deltaAge,
                        onValueChange = { newValue ->
                            val newUiExpenses = uiExpenses.toMutableList()
                            newUiExpenses[index] = uiExpenses[index].copy(age = newValue)
                            
                            val newExpenses = expenses.toMutableList()
                            newValue.toIntOrNull()?.let { v ->
                                if(index < newExpenses.size) {
                                    newExpenses[index] = expenses[index].copy(age = v)
                                }
                            }
                            onUpdateExpenses(newExpenses, newUiExpenses)
                        },
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                        isCompareMode = isComparing
                        // Age is neutral, no semantic meaning to delta
                    )
                    
                    // Amount Field (Lower is Better -> Green if delta < 0)
                    val deltaAmount = if (isComparing && p2Expense != null) p2Expense.amount - expenses[index].amount else null

                    DeltaInputField(
                        label = stringResource(R.string.expense_amount_format, index + 1),
                        value = uiExpense.amount,
                        deltaValue = deltaAmount,
                        onValueChange = { newValue ->
                            val newUiExpenses = uiExpenses.toMutableList()
                            newUiExpenses[index] = uiExpenses[index].copy(amount = newValue)
                            
                            val newExpenses = expenses.toMutableList()
                            newValue.replace(',', '.').toDoubleOrNull()?.let { v ->
                                if(index < newExpenses.size) {
                                    newExpenses[index] = expenses[index].copy(amount = v)
                                }
                            }
                            onUpdateExpenses(newExpenses, newUiExpenses)
                        },
                        modifier = Modifier.weight(2f),
                        isCompareMode = isComparing,
                        positiveIsGood = false // Lower expenses are better
                    )
                    
                    // Utility Offset Field (Higher is Better -> Green if delta > 0)
                    val deltaOffset = if (isComparing && p2Expense != null) p2Expense.utilityOffset - expenses[index].utilityOffset else null

                    DeltaInputField(
                        label = stringResource(R.string.utility_offset_label, index + 1),
                        value = uiExpense.utilityOffset,
                        deltaValue = deltaOffset,
                        onValueChange = { newValue ->
                            val newUiExpenses = uiExpenses.toMutableList()
                            newUiExpenses[index] = uiExpenses[index].copy(utilityOffset = newValue)
                            
                            val newExpenses = expenses.toMutableList()
                            newValue.replace(',', '.').toDoubleOrNull()?.let { v ->
                                if(index < newExpenses.size) {
                                    newExpenses[index] = expenses[index].copy(utilityOffset = v)
                                }
                            }
                            onUpdateExpenses(newExpenses, newUiExpenses)
                        },
                        modifier = Modifier.weight(1.5f),
                        isCompareMode = isComparing
                        // Higher utility offset is better
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val defaultExpenses = List(10) { SpecificExpense() }
                    val defaultUiExpenses = defaultExpenses.map { SpecificExpenseUI(it.age.toString(), String.format(Locale.US, "%.2f", it.amount), String.format(Locale.US, "%.4f", it.utilityOffset)) }
                    onUpdateExpenses(defaultExpenses, defaultUiExpenses)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.reset_expenses))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview
@Composable
fun SpecificExpensesPreview() {
    SpecificExpensesContent(
        expenses = List(3) { SpecificExpense() },
        uiExpenses = List(3) { SpecificExpenseUI("0", "0.00", "0.0000") },
        isComparing = false,
        p2Expenses = emptyList(),
        onUpdateExpenses = { _, _ -> },
        onBack = {}
    )
}
