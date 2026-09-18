// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.daysurpopt.ui.theme.NegativeDelta
import com.example.daysurpopt.ui.theme.OptGroupBackground
import com.example.daysurpopt.ui.theme.OptGroupBorder
import com.example.daysurpopt.ui.theme.OptGroupMutedBackground
import com.example.daysurpopt.ui.theme.OptGroupMutedBorder
import com.example.daysurpopt.ui.theme.OptGroupMutedText
import com.example.daysurpopt.ui.theme.OptGroupText
import com.example.daysurpopt.ui.theme.PositiveDelta

@Composable
fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    keyboardType: KeyboardType = KeyboardType.Decimal,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = colors,
        enabled = enabled
    )
}

/**
 * Input field with optional colored delta indicator for compare mode.
 * Shows the Profile 1 value in the input box, with a colored delta above/beside when different from Profile 2.
 *
 * @param deltaValue The delta value (Profile 2 - Profile 1), or null if no delta to show.
 * @param positiveIsGood If true, positive delta is Green, negative is Red. If false, vice versa.
 */
@Composable
fun DeltaInputField(
    label: String,
    value: String,
    deltaValue: Double?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    keyboardType: KeyboardType = KeyboardType.Decimal,
    enabled: Boolean = true,
    isCompareMode: Boolean = false,
    positiveIsGood: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Delta indicator row (only show if comparing and there IS a delta)
        if (isCompareMode && deltaValue != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                val isPositive = deltaValue >= 0
                val isGood = if (positiveIsGood) isPositive else !isPositive
                val deltaColor = if (isGood) PositiveDelta else NegativeDelta

                val deltaStr = if (isPositive) {
                    "(P2: +${String.format(java.util.Locale.US, "%.2f", deltaValue)})"
                } else {
                    "(P2: ${String.format(java.util.Locale.US, "%.2f", deltaValue)})"
                }
                Text(
                    text = deltaStr,
                    color = deltaColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = colors,
            enabled = enabled // Allow editing even in compare mode
        )
    }
}

/**
 * Int delta version for age fields.
 */
@Composable
fun DeltaInputFieldInt(
    label: String,
    value: String,
    deltaValue: Int?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    keyboardType: KeyboardType = KeyboardType.Number,
    enabled: Boolean = true,
    isCompareMode: Boolean = false,
    positiveIsGood: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Delta indicator row (only show if comparing and there IS a delta)
        if (isCompareMode && deltaValue != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                val isPositive = deltaValue >= 0
                val isGood = if (positiveIsGood) isPositive else !isPositive
                val deltaColor = if (isGood) PositiveDelta else NegativeDelta

                val deltaStr = if (isPositive) {
                    "(P2: +$deltaValue)"
                } else {
                    "(P2: $deltaValue)"
                }
                Text(
                    text = deltaStr,
                    color = deltaColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = colors,
            enabled = enabled // Allow editing even in compare mode
        )
    }
}

@Composable
fun OptimizationParameterGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = OptGroupBackground,
        border = BorderStroke(1.dp, OptGroupBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
                color = OptGroupText
            )
            content()
        }
    }
}

@Composable
fun OptimizationParameterGroupMuted(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = OptGroupMutedBackground,
        border = BorderStroke(1.dp, OptGroupMutedBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
                color = OptGroupMutedText
            )
            content()
        }
    }
}
