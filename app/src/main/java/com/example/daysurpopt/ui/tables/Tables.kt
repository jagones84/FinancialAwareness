// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.daysurpopt.R
import com.example.daysurpopt.domain.DeltaSensitivityResult
import com.example.daysurpopt.domain.SensitivityResult
import com.example.daysurpopt.domain.SimulationYear
import java.util.Locale
import kotlin.math.abs

import com.example.daysurpopt.ui.theme.PositiveDelta
import com.example.daysurpopt.ui.theme.NegativeDelta
import com.example.daysurpopt.ui.theme.WarningAmber

// Colors for comparison
private val BetterColor = PositiveDelta // Green
private val WorseColor = NegativeDelta   // Red

/**
 * For simulation table: determine if P2 value is "better" than P1 value for each metric.
 * - Higher utility = better (green)
 * - Higher capital = better (green)
 * - Lower debt = better (green)
 * - Lower capital eroded = better (green)
 */
private fun getComparisonColor(p1Value: Double, p2Value: Double, higherIsBetter: Boolean): Color {
    val diff = p2Value - p1Value
    return when {
        abs(diff) < 0.001 -> Color.Unspecified // No meaningful difference
        higherIsBetter && diff > 0 -> BetterColor
        higherIsBetter && diff < 0 -> WorseColor
        !higherIsBetter && diff < 0 -> BetterColor
        !higherIsBetter && diff > 0 -> WorseColor
        else -> Color.Unspecified
    }
}

@Composable
fun SimulationResultTable(
    results: List<SimulationYear>,
    isCompareMode: Boolean = false,
    profile2Results: List<SimulationYear> = emptyList()
) {
    val p2Map = profile2Results.associateBy { it.eta }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val headerStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            
            Text(stringResource(R.string.table_age), modifier = Modifier.weight(0.6f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.table_extra_spending), modifier = Modifier.weight(0.9f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.table_capital_end_year), modifier = Modifier.weight(1.2f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.table_savings_ratio), modifier = Modifier.weight(0.8f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.table_utility), modifier = Modifier.weight(0.8f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.table_debt_repayment), modifier = Modifier.weight(0.9f), style = headerStyle, textAlign = TextAlign.Center)
            Text(stringResource(R.string.table_capital_gained), modifier = Modifier.weight(1.0f), style = headerStyle, textAlign = TextAlign.Center)
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            items(results) { year ->
                val p2Year = p2Map[year.eta]
                
                val deathAge = results.lastOrNull()?.eta ?: year.eta
                val yearsToDeath = (deathAge - year.eta)
                val capColor = when {
                    !year.violazioneLascito -> Color.Unspecified
                    yearsToDeath <= 5 -> NegativeDelta
                    else -> WarningAmber
                }
                val utilColor = if (year.utilityAtThreshold) WarningAmber else Color.Unspecified
                val debtColor = if (year.debtRepayment > 0) NegativeDelta else Color.Unspecified
                
                val gained = year.capitaleFineAnno - year.capitaleInizioAnno
                val gainedColor = if (gained >= 0) PositiveDelta else NegativeDelta

                val rowStyle = MaterialTheme.typography.bodySmall

                // Profile 1 Row
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(year.eta.toString(), modifier = Modifier.weight(0.6f), style = rowStyle, textAlign = TextAlign.Center)
                    Text(String.format(Locale.getDefault(), "€%,.0f", year.spesaMensileCorrettaFinale * 12.0 / 365.0), modifier = Modifier.weight(0.9f), style = rowStyle, textAlign = TextAlign.Center)
                    Text(String.format(Locale.getDefault(), "€%,.0f", year.capitaleFineAnno), modifier = Modifier.weight(1.2f), style = rowStyle, color = capColor, textAlign = TextAlign.Center)
                    Text(String.format(Locale.getDefault(), "%.2f", year.savingRatioEffettivo), modifier = Modifier.weight(0.8f), style = rowStyle, textAlign = TextAlign.Center)
                    Text(String.format(Locale.getDefault(), "%.2f", year.funzioneUtilita), modifier = Modifier.weight(0.8f), style = rowStyle, color = utilColor, textAlign = TextAlign.Center)
                    Text(String.format(Locale.getDefault(), "€%,.0f", year.debtRepayment), modifier = Modifier.weight(0.9f), style = rowStyle, color = debtColor, textAlign = TextAlign.Center)
                    Text(String.format(Locale.getDefault(), "%.1fk", gained / 1000), modifier = Modifier.weight(1.0f), style = rowStyle, color = gainedColor, textAlign = TextAlign.Center)
                }
                
                // Profile 2 Row (if in compare mode and data exists)
                if (isCompareMode && p2Year != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(vertical = 2.dp)
                    ) {
                        val p2Style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        
                        Text("P2", modifier = Modifier.weight(0.6f), style = p2Style, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                        
                        // Extra spending - higher is better (more spending power)
                        val p1Spending = year.spesaMensileCorrettaFinale * 12.0 / 365.0
                        val p2Spending = p2Year.spesaMensileCorrettaFinale * 12.0 / 365.0
                        Text(
                            String.format(Locale.getDefault(), "€%,.0f", p2Spending), 
                            modifier = Modifier.weight(0.9f), 
                            style = p2Style, 
                            textAlign = TextAlign.Center,
                            color = getComparisonColor(p1Spending, p2Spending, higherIsBetter = true)
                        )
                        
                        // Capital - higher is better
                        Text(
                            String.format(Locale.getDefault(), "€%,.0f", p2Year.capitaleFineAnno), 
                            modifier = Modifier.weight(1.2f), 
                            style = p2Style, 
                            textAlign = TextAlign.Center,
                            color = getComparisonColor(year.capitaleFineAnno, p2Year.capitaleFineAnno, higherIsBetter = true)
                        )
                        
                        // Saving ratio - neutral (depends on strategy)
                        Text(
                            String.format(Locale.getDefault(), "%.2f", p2Year.savingRatioEffettivo), 
                            modifier = Modifier.weight(0.8f), 
                            style = p2Style, 
                            textAlign = TextAlign.Center
                        )
                        
                        // Utility - higher is better
                        Text(
                            String.format(Locale.getDefault(), "%.2f", p2Year.funzioneUtilita), 
                            modifier = Modifier.weight(0.8f), 
                            style = p2Style, 
                            textAlign = TextAlign.Center,
                            color = getComparisonColor(year.funzioneUtilita, p2Year.funzioneUtilita, higherIsBetter = true)
                        )
                        
                        // Debt repayment - lower is better (less debt burden)
                        Text(
                            String.format(Locale.getDefault(), "€%,.0f", p2Year.debtRepayment), 
                            modifier = Modifier.weight(0.9f), 
                            style = p2Style, 
                            textAlign = TextAlign.Center,
                            color = getComparisonColor(year.debtRepayment, p2Year.debtRepayment, higherIsBetter = false)
                        )
                        
                        // Capital gained - higher is better
                        val p2Gained = p2Year.capitaleFineAnno - p2Year.capitaleInizioAnno
                        Text(
                            String.format(Locale.getDefault(), "%.1fk", p2Gained / 1000), 
                            modifier = Modifier.weight(1.0f), 
                            style = p2Style, 
                            textAlign = TextAlign.Center,
                            color = getComparisonColor(gained, p2Gained, higherIsBetter = true)
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SensitivityAnalysisTable(
    results: List<SensitivityResult>,
    isCompareMode: Boolean = false,
    deltaResults: List<DeltaSensitivityResult> = emptyList()
) {
    val deltaMap = deltaResults.associateBy { it.nameResId }
    
    Column {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.table_parameter),
                modifier = Modifier.weight(1.5f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.table_impact),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.End,
                fontWeight = FontWeight.Bold
            )
            if (isCompareMode) {
                Text(
                    "Δ",
                    modifier = Modifier.weight(0.6f),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        HorizontalDivider(thickness = 2.dp)

        // Data
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            items(results) { result ->
                val delta = deltaMap[result.nameResId]
                
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(result.nameResId), 
                        modifier = Modifier.weight(1.5f), 
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.4f", result.scaledImpact)} ${stringResource(result.unitResId)}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isCompareMode) {
                        val deltaVal = delta?.deltaScaledImpact ?: 0.0
                        val deltaStr = if (deltaVal >= 0) stringResource(R.string.delta_val_positive, String.format(Locale.US, "%.4f", deltaVal))
                                       else stringResource(R.string.delta_val_negative, String.format(Locale.US, "%.4f", deltaVal))
                        // Green if absolute value is bigger (more sensitive = more impact), Red if smaller
                        val p2AbsImpact = abs(result.scaledImpact + deltaVal)
                        val p1AbsImpact = abs(result.scaledImpact)
                        val deltaColor = if (p2AbsImpact > p1AbsImpact) BetterColor else if (p2AbsImpact < p1AbsImpact) WorseColor else Color.Unspecified
                        
                        Text(
                            text = deltaStr,
                            modifier = Modifier.weight(0.6f),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.bodyMedium,
                            color = deltaColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
