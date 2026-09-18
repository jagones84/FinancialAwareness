// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.daysurpopt.R
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.domain.SurplusInputUI
import com.example.daysurpopt.ui.common.DeltaInputField
import com.example.daysurpopt.ui.common.DeltaInputFieldInt
import com.example.daysurpopt.ui.theme.ExpenseRed
import com.example.daysurpopt.ui.theme.ExpenseRedFocused
import com.example.daysurpopt.ui.theme.ExpenseRedUnfocused
import com.example.daysurpopt.ui.theme.IncomeGreen
import com.example.daysurpopt.ui.theme.IncomeGreenFocused
import com.example.daysurpopt.ui.theme.IncomeGreenUnfocused
import com.example.daysurpopt.ui.theme.NegativeDelta
import com.example.daysurpopt.ui.theme.PositiveDelta
import java.util.Locale

@Composable
fun SurplusCalculatorScreen(
    navController: NavController,
    viewModel: FinancialViewModel
) {
    val surplusInput = viewModel.surplusData
    var uiSurplusInput by remember(surplusInput) { mutableStateOf(SurplusInputUI.from(surplusInput)) }

    // Comparison data
    val isComparing = viewModel.compareState.isComparing
    val p2Surplus = if (isComparing) viewModel.profile2SurplusData else null

    SurplusCalculatorContent(
        surplusInput = surplusInput,
        uiSurplusInput = uiSurplusInput,
        isComparing = isComparing,
        p2Surplus = p2Surplus,
        onUpdateUi = { ui -> uiSurplusInput = ui },
        onUpdateInput = { input -> viewModel.updateSurplusData(input) },
        onBack = {
            val surplusLavorativaMedia = surplusInput.calculateSurplusGiornalieroMedioLavorativa()
            val surplusPensioneMedia = surplusInput.calculateSurplusGiornalieroMedioPensione()
            navController.previousBackStackEntry?.savedStateHandle?.set("surplusLavorativaMedia", surplusLavorativaMedia)
            navController.previousBackStackEntry?.savedStateHandle?.set("surplusPensioneMedia", surplusPensioneMedia)
            navController.previousBackStackEntry?.savedStateHandle?.set("mutuoFinoEta", surplusInput.mutuoAffittoFinoEta)
            navController.popBackStack()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurplusCalculatorContent(
    surplusInput: SurplusInput,
    uiSurplusInput: SurplusInputUI,
    isComparing: Boolean,
    p2Surplus: SurplusInput?,
    onUpdateUi: (SurplusInputUI) -> Unit,
    onUpdateInput: (SurplusInput) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    val surplusLavorativaMedia = surplusInput.calculateSurplusGiornalieroMedioLavorativa()
    val surplusPensioneMedia = surplusInput.calculateSurplusGiornalieroMedioPensione()

    val greenTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = IncomeGreenFocused,
        unfocusedBorderColor = IncomeGreenUnfocused,
        focusedLabelColor = IncomeGreenFocused,
        unfocusedLabelColor = IncomeGreenUnfocused,
        cursorColor = IncomeGreenFocused
    )

    val redTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ExpenseRedFocused,
        unfocusedBorderColor = ExpenseRedUnfocused,
        focusedLabelColor = ExpenseRedFocused,
        unfocusedLabelColor = ExpenseRedUnfocused,
        cursorColor = ExpenseRedFocused
    )

    // Helper functions for Deltas
    @Composable
    fun SurplusDeltaField(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        currentValueChecker: () -> Double,
        p2ValueSelector: (SurplusInput) -> Double,
        colors: TextFieldColors,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        isHigherBetter: Boolean = true
    ) {
        val p1Val = currentValueChecker()
        val delta = if (isComparing && p2Surplus != null) {
            val p2Val = p2ValueSelector(p2Surplus)
            p2Val - p1Val
        } else null

        DeltaInputField(
            label = label,
            value = value,
            deltaValue = delta,
            onValueChange = onValueChange,
            modifier = modifier,
            colors = colors,
            enabled = enabled,
            isCompareMode = isComparing,
            positiveIsGood = isHigherBetter
        )
    }

    @Composable
    fun SurplusDeltaFieldInt(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        currentValueChecker: () -> Int,
        p2ValueSelector: (SurplusInput) -> Int,
        colors: TextFieldColors,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        isHigherBetter: Boolean? = null // Null = Neutral
    ) {
        val p1Val = currentValueChecker()
        val delta = if (isComparing && p2Surplus != null) {
            val p2Val = p2ValueSelector(p2Surplus)
            p2Val - p1Val
        } else null

        DeltaInputFieldInt(
            label = label,
            value = value,
            deltaValue = delta,
            onValueChange = onValueChange,
            modifier = modifier,
            colors = colors,
            enabled = enabled,
            isCompareMode = isComparing,
            positiveIsGood = isHigherBetter ?: true
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.daily_surplus_calculator_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.save_and_return))
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

            // Entrate (Higher is Better -> Default)
            Text(stringResource(R.string.income), style = MaterialTheme.typography.titleLarge, color = IncomeGreen)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.working_age), style = MaterialTheme.typography.titleMedium)
                    SurplusDeltaField(
                        label = stringResource(R.string.monthly_net_salary),
                        value = uiSurplusInput.stipendioMensile,
                        onValueChange = {
                            onUpdateUi(uiSurplusInput.copy(stipendioMensile = it))
                            it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(stipendioMensile = v)) }
                        },
                        currentValueChecker = { surplusInput.stipendioMensile },
                        p2ValueSelector = { it.stipendioMensile },
                        colors = greenTextFieldColors
                    )
                    SurplusDeltaField(
                        label = stringResource(R.string.annual_net_bonus),
                        value = uiSurplusInput.premioRisultatoNettoAnnuale,
                        onValueChange = {
                            onUpdateUi(uiSurplusInput.copy(premioRisultatoNettoAnnuale = it))
                            it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(premioRisultatoNettoAnnuale = v)) }
                        },
                        currentValueChecker = { surplusInput.premioRisultatoNettoAnnuale },
                        p2ValueSelector = { it.premioRisultatoNettoAnnuale },
                        colors = greenTextFieldColors
                    )
                    SurplusDeltaField(
                        label = stringResource(R.string.annual_13_14),
                        value = uiSurplusInput.tredicesimaQuattordicesimaNetto,
                        onValueChange = {
                            onUpdateUi(uiSurplusInput.copy(tredicesimaQuattordicesimaNetto = it))
                            it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(tredicesimaQuattordicesimaNetto = v)) }
                        },
                        currentValueChecker = { surplusInput.tredicesimaQuattordicesimaNetto },
                        p2ValueSelector = { it.tredicesimaQuattordicesimaNetto },
                        colors = greenTextFieldColors
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SurplusDeltaField(
                            label = stringResource(R.string.monthly_personal_bonus),
                            value = uiSurplusInput.bonusEventualiPersonaliMensile,
                            onValueChange = {
                                onUpdateUi(uiSurplusInput.copy(bonusEventualiPersonaliMensile = it))
                                it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(bonusEventualiPersonaliMensile = v)) }
                            },
                            currentValueChecker = { surplusInput.bonusEventualiPersonaliMensile },
                            p2ValueSelector = { it.bonusEventualiPersonaliMensile },
                            colors = greenTextFieldColors,
                            modifier = Modifier.weight(1f)
                        )
                        SurplusDeltaFieldInt(
                            label = stringResource(R.string.until_age),
                            value = uiSurplusInput.bonusEventualiPersonaliMensileFinoEta,
                            onValueChange = {
                                onUpdateUi(uiSurplusInput.copy(bonusEventualiPersonaliMensileFinoEta = it))
                                it.toIntOrNull()?.let { v -> onUpdateInput(surplusInput.copy(bonusEventualiPersonaliMensileFinoEta = v)) }
                            },
                            currentValueChecker = { surplusInput.bonusEventualiPersonaliMensileFinoEta },
                            p2ValueSelector = { it.bonusEventualiPersonaliMensileFinoEta },
                            colors = greenTextFieldColors,
                            modifier = Modifier.weight(0.5f)
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pension), style = MaterialTheme.typography.titleMedium)
                    SurplusDeltaField(
                        label = stringResource(R.string.monthly_net_pension),
                        value = uiSurplusInput.pensioneMensileNetta,
                        onValueChange = {
                            onUpdateUi(uiSurplusInput.copy(pensioneMensileNetta = it))
                            it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(pensioneMensileNetta = v)) }
                        },
                        currentValueChecker = { surplusInput.pensioneMensileNetta },
                        p2ValueSelector = { it.pensioneMensileNetta },
                        colors = greenTextFieldColors
                    )
                    SurplusDeltaField(
                        label = stringResource(R.string.other_monthly_income),
                        value = uiSurplusInput.altreEntrateMensiliPensione,
                        onValueChange = {
                            onUpdateUi(uiSurplusInput.copy(altreEntrateMensiliPensione = it))
                            it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(altreEntrateMensiliPensione = v)) }
                        },
                        currentValueChecker = { surplusInput.altreEntrateMensiliPensione },
                        p2ValueSelector = { it.altreEntrateMensiliPensione },
                        colors = greenTextFieldColors
                    )
                    SurplusDeltaField(
                        label = stringResource(R.string.annual_13_14),
                        value = uiSurplusInput.tredicesimaQuattordicesimaNettoPensione,
                        onValueChange = {
                            onUpdateUi(uiSurplusInput.copy(tredicesimaQuattordicesimaNettoPensione = it))
                            it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(tredicesimaQuattordicesimaNettoPensione = v)) }
                        },
                        currentValueChecker = { surplusInput.tredicesimaQuattordicesimaNettoPensione },
                        p2ValueSelector = { it.tredicesimaQuattordicesimaNettoPensione },
                        colors = greenTextFieldColors
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SurplusDeltaField(
                            label = stringResource(R.string.monthly_personal_bonus),
                            value = uiSurplusInput.bonusEventualiPersonaliPensioneMensile,
                            onValueChange = {
                                onUpdateUi(uiSurplusInput.copy(bonusEventualiPersonaliPensioneMensile = it))
                                it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(bonusEventualiPersonaliPensioneMensile = v)) }
                            },
                            currentValueChecker = { surplusInput.bonusEventualiPersonaliPensioneMensile },
                            p2ValueSelector = { it.bonusEventualiPersonaliPensioneMensile },
                            colors = greenTextFieldColors,
                            modifier = Modifier.weight(1f)
                        )
                        SurplusDeltaFieldInt(
                            label = stringResource(R.string.until_age),
                            value = uiSurplusInput.bonusEventualiPersonaliPensioneMensileFinoEta,
                            onValueChange = {
                                onUpdateUi(uiSurplusInput.copy(bonusEventualiPersonaliPensioneMensileFinoEta = it))
                                it.toIntOrNull()?.let { v -> onUpdateInput(surplusInput.copy(bonusEventualiPersonaliPensioneMensileFinoEta = v)) }
                            },
                            currentValueChecker = { surplusInput.bonusEventualiPersonaliPensioneMensileFinoEta },
                            p2ValueSelector = { it.bonusEventualiPersonaliPensioneMensileFinoEta },
                            colors = greenTextFieldColors,
                            modifier = Modifier.weight(0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Spese (Lower is Better -> isHigherBetter = false)
            Text(stringResource(R.string.fixed_expenses_monthly), style = MaterialTheme.typography.titleLarge, color = ExpenseRed)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SurplusDeltaField(
                    label = stringResource(R.string.mortgage_rent),
                    value = uiSurplusInput.mutuoAffitto,
                    onValueChange = {
                        onUpdateUi(uiSurplusInput.copy(mutuoAffitto = it))
                        it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(mutuoAffitto = v)) }
                    },
                    currentValueChecker = { surplusInput.mutuoAffitto },
                    p2ValueSelector = { it.mutuoAffitto },
                    colors = redTextFieldColors,
                    modifier = Modifier.weight(1f),
                    isHigherBetter = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                SurplusDeltaFieldInt(
                    label = stringResource(R.string.until_age),
                    value = uiSurplusInput.mutuoAffittoFinoEta,
                    onValueChange = {
                        onUpdateUi(uiSurplusInput.copy(mutuoAffittoFinoEta = it))
                        it.toIntOrNull()?.let { v -> onUpdateInput(surplusInput.copy(mutuoAffittoFinoEta = v)) }
                    },
                    currentValueChecker = { surplusInput.mutuoAffittoFinoEta },
                    p2ValueSelector = { it.mutuoAffittoFinoEta },
                    colors = redTextFieldColors,
                    modifier = Modifier.weight(0.6f)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.working_age), style = MaterialTheme.typography.titleMedium)
                    SurplusDeltaField(stringResource(R.string.condo_fees), uiSurplusInput.condominioLavorativa, { onUpdateUi(uiSurplusInput.copy(condominioLavorativa = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(condominioLavorativa = v)) } }, { surplusInput.condominioLavorativa }, { it.condominioLavorativa }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.utilities_bills), uiSurplusInput.bolletteLavorativa, { onUpdateUi(uiSurplusInput.copy(bolletteLavorativa = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(bolletteLavorativa = v)) } }, { surplusInput.bolletteLavorativa }, { it.bolletteLavorativa }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.food), uiSurplusInput.ciboLavorativa, { onUpdateUi(uiSurplusInput.copy(ciboLavorativa = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(ciboLavorativa = v)) } }, { surplusInput.ciboLavorativa }, { it.ciboLavorativa }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.vehicles), uiSurplusInput.veicoliLavorativa, { onUpdateUi(uiSurplusInput.copy(veicoliLavorativa = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(veicoliLavorativa = v)) } }, { surplusInput.veicoliLavorativa }, { it.veicoliLavorativa }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.gym), uiSurplusInput.palestraLavorativa, { onUpdateUi(uiSurplusInput.copy(palestraLavorativa = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(palestraLavorativa = v)) } }, { surplusInput.palestraLavorativa }, { it.palestraLavorativa }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.transport_travel), uiSurplusInput.trasportiViaggiLavorativa, { onUpdateUi(uiSurplusInput.copy(trasportiViaggiLavorativa = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(trasportiViaggiLavorativa = v)) } }, { surplusInput.trasportiViaggiLavorativa }, { it.trasportiViaggiLavorativa }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.health), uiSurplusInput.saluteLavorativa, { onUpdateUi(uiSurplusInput.copy(saluteLavorativa = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(saluteLavorativa = v)) } }, { surplusInput.saluteLavorativa }, { it.saluteLavorativa }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.vacations), uiSurplusInput.vacanzeLavorativa, { onUpdateUi(uiSurplusInput.copy(vacanzeLavorativa = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(vacanzeLavorativa = v)) } }, { surplusInput.vacanzeLavorativa }, { it.vacanzeLavorativa }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.shopping), uiSurplusInput.shoppingLavorativa, { onUpdateUi(uiSurplusInput.copy(shoppingLavorativa = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(shoppingLavorativa = v)) } }, { surplusInput.shoppingLavorativa }, { it.shoppingLavorativa }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.other), uiSurplusInput.altroLavorativa, { onUpdateUi(uiSurplusInput.copy(altroLavorativa = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(altroLavorativa = v)) } }, { surplusInput.altroLavorativa }, { it.altroLavorativa }, redTextFieldColors, isHigherBetter = false)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pension), style = MaterialTheme.typography.titleMedium)
                    SurplusDeltaField(stringResource(R.string.condo_fees), uiSurplusInput.condominioPensione, { onUpdateUi(uiSurplusInput.copy(condominioPensione = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(condominioPensione = v)) } }, { surplusInput.condominioPensione }, { it.condominioPensione }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.utilities_bills), uiSurplusInput.bollettePensione, { onUpdateUi(uiSurplusInput.copy(bollettePensione = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(bollettePensione = v)) } }, { surplusInput.bollettePensione }, { it.bollettePensione }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.food), uiSurplusInput.ciboPensione, { onUpdateUi(uiSurplusInput.copy(ciboPensione = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(ciboPensione = v)) } }, { surplusInput.ciboPensione }, { it.ciboPensione }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.vehicles), uiSurplusInput.veicoliPensione, { onUpdateUi(uiSurplusInput.copy(veicoliPensione = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(veicoliPensione = v)) } }, { surplusInput.veicoliPensione }, { it.veicoliPensione }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.gym), uiSurplusInput.palestraPensione, { onUpdateUi(uiSurplusInput.copy(palestraPensione = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(palestraPensione = v)) } }, { surplusInput.palestraPensione }, { it.palestraPensione }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.transport_travel), uiSurplusInput.trasportiViaggiPensione, { onUpdateUi(uiSurplusInput.copy(trasportiViaggiPensione = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(trasportiViaggiPensione = v)) } }, { surplusInput.trasportiViaggiPensione }, { it.trasportiViaggiPensione }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.health), uiSurplusInput.salutePensione, { onUpdateUi(uiSurplusInput.copy(salutePensione = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(salutePensione = v)) } }, { surplusInput.salutePensione }, { it.salutePensione }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.vacations), uiSurplusInput.vacanzePensione, { onUpdateUi(uiSurplusInput.copy(vacanzePensione = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(vacanzePensione = v)) } }, { surplusInput.vacanzePensione }, { it.vacanzePensione }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.shopping), uiSurplusInput.shoppingPensione, { onUpdateUi(uiSurplusInput.copy(shoppingPensione = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(shoppingPensione = v)) } }, { surplusInput.shoppingPensione }, { it.shoppingPensione }, redTextFieldColors, isHigherBetter = false)
                    SurplusDeltaField(stringResource(R.string.other), uiSurplusInput.altroPensione, { onUpdateUi(uiSurplusInput.copy(altroPensione = it)); it.replace(',', '.').toDoubleOrNull()?.let { v -> onUpdateInput(surplusInput.copy(altroPensione = v)) } }, { surplusInput.altroPensione }, { it.altroPensione }, redTextFieldColors, isHigherBetter = false)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Risultati
            Text(stringResource(R.string.results), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            val entrateLavorativa = surplusInput.getEntrateMensiliLavorativa()
            val usciteLavorativaSenzaMutuo = surplusInput.getUsciteMensiliLavorativa(false)
            val usciteLavorativaConMutuo = surplusInput.getUsciteMensiliLavorativa(true)

            val entratePensione = surplusInput.getEntrateMensiliPensione()
            val uscitePensioneSenzaMutuo = surplusInput.getUsciteMensiliPensione(false)
            val uscitePensioneConMutuo = surplusInput.getUsciteMensiliPensione(true)

            Text(stringResource(R.string.monthly_totals), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(
                    R.string.work_totals_format,
                    String.format(Locale.getDefault(), "€%,.2f", entrateLavorativa),
                    String.format(Locale.getDefault(), "€%,.2f", usciteLavorativaSenzaMutuo),
                    String.format(Locale.getDefault(), "€%,.2f", usciteLavorativaConMutuo)
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                stringResource(
                    R.string.pension_totals_format,
                    String.format(Locale.getDefault(), "€%,.2f", entratePensione),
                    String.format(Locale.getDefault(), "€%,.2f", uscitePensioneSenzaMutuo),
                    String.format(Locale.getDefault(), "€%,.2f", uscitePensioneConMutuo)
                ),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.calculated_daily_surplus_title), style = MaterialTheme.typography.headlineSmall)
            
            // Work Surplus Results + Delta
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.work_surplus_average, String.format(Locale.getDefault(), "€%,.2f", surplusLavorativaMedia)), style = MaterialTheme.typography.bodyLarge)
                if (isComparing && p2Surplus != null) {
                    val p2Val = p2Surplus.calculateSurplusGiornalieroMedioLavorativa()
                    val delta = p2Val - surplusLavorativaMedia
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (delta >= 0) stringResource(R.string.delta_surplus_positive, String.format(Locale.US, "%.2f", delta)) else stringResource(R.string.delta_surplus_negative, String.format(Locale.US, "%.2f", delta)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (delta >= 0) PositiveDelta else NegativeDelta
                    )
                }
            }
            
            // Pension Surplus Results + Delta
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pension_surplus_average, String.format(Locale.getDefault(), "€%,.2f", surplusPensioneMedia)), style = MaterialTheme.typography.bodyLarge)
                if (isComparing && p2Surplus != null) {
                    val p2Val = p2Surplus.calculateSurplusGiornalieroMedioPensione()
                    val delta = p2Val - surplusPensioneMedia
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (delta >= 0) stringResource(R.string.delta_surplus_positive, String.format(Locale.US, "%.2f", delta)) else stringResource(R.string.delta_surplus_negative, String.format(Locale.US, "%.2f", delta)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (delta >= 0) PositiveDelta else NegativeDelta
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview
@Composable
fun SurplusCalculatorPreview() {
    SurplusCalculatorContent(
        surplusInput = SurplusInput(),
        uiSurplusInput = SurplusInputUI.from(SurplusInput()),
        isComparing = false,
        p2Surplus = null,
        onUpdateUi = {},
        onUpdateInput = {},
        onBack = {}
    )
}
