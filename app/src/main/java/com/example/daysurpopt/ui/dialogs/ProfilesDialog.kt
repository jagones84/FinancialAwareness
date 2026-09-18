// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.daysurpopt.R
import com.example.daysurpopt.ui.theme.BadgeP1
import com.example.daysurpopt.ui.theme.BadgeP2
import com.example.daysurpopt.ui.theme.ExpenseRed

@Composable
fun ProfilesDialog(
    onDismiss: () -> Unit,
    onLoadProfile: (String) -> Unit,
    onSaveProfile: (String) -> Unit,
    existingProfiles: List<String>,
    onDeleteProfile: (String) -> Unit,
    onCompareProfiles: ((String, String) -> Unit)? = null,
    isComparing: Boolean = false,
    onExitCompare: (() -> Unit)? = null
) {
    var newProfileName by remember { mutableStateOf("") }
    var pendingOverwriteName by remember { mutableStateOf<String?>(null) }
    var compareMode by remember { mutableStateOf(false) }
    var selectedProfiles by remember { mutableStateOf(setOf<String>()) }

    if (pendingOverwriteName != null) {
        AlertDialog(
            onDismissRequest = { pendingOverwriteName = null },
            title = { Text(stringResource(R.string.confirm_overwrite_title)) },
            text = { Text(stringResource(R.string.confirm_overwrite_message, pendingOverwriteName!!)) },
            confirmButton = {
                TextButton(onClick = {
                    val name = pendingOverwriteName
                    pendingOverwriteName = null
                    if (name != null) onSaveProfile(name)
                    newProfileName = ""
                }) { Text(stringResource(R.string.overwrite)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwriteName = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_profiles_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                
                // Compare mode toggle (only show if callback provided and not already comparing)
                if (onCompareProfiles != null) {
                    if (isComparing) {
                        // Currently comparing - show exit button
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.compare_mode_active),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                TextButton(onClick = { onExitCompare?.invoke() }) {
                                    Text(stringResource(R.string.exit_compare_mode))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        // Toggle to enter compare selection mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.compare_profiles),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Switch(
                                checked = compareMode,
                                onCheckedChange = { 
                                    compareMode = it
                                    if (!it) selectedProfiles = emptySet()
                                }
                            )
                        }
                        
                        if (compareMode) {
                            Text(
                                stringResource(R.string.select_profiles_to_compare),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (selectedProfiles.size == 2) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        val list = selectedProfiles.toList()
                                        onCompareProfiles(list[0], list[1])
                                        compareMode = false
                                        selectedProfiles = emptySet()
                                        onDismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.compare_button))
                                }
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                
                // Save profile section (hide in compare mode)
                if (!compareMode) {
                    Text(stringResource(R.string.save_current_profile), style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newProfileName,
                            onValueChange = { newProfileName = it },
                            label = { Text(stringResource(R.string.profile_name)) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (newProfileName.isNotBlank()) {
                                if (existingProfiles.any { it == newProfileName }) {
                                    pendingOverwriteName = newProfileName
                                } else {
                                    onSaveProfile(newProfileName)
                                    newProfileName = ""
                                }
                            }
                        }, enabled = newProfileName.isNotBlank()) {
                            Text(stringResource(R.string.save))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                Text(
                    if (compareMode) stringResource(R.string.select_profiles_to_compare) 
                    else stringResource(R.string.load_existing_profile), 
                    style = MaterialTheme.typography.titleSmall
                )
                
                LazyColumn {
                    items(existingProfiles) { name ->
                        val isSelected = name in selectedProfiles
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (compareMode) {
                                        Modifier
                                            .clickable {
                                                selectedProfiles = if (isSelected) {
                                                    selectedProfiles - name
                                                } else if (selectedProfiles.size < 2) {
                                                    selectedProfiles + name
                                                } else {
                                                    selectedProfiles
                                                }
                                            }
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                else Color.Transparent,
                                                RoundedCornerShape(4.dp)
                                            )
                                    } else {
                                        Modifier
                                            .clickable {
                                                newProfileName = name
                                            }
                                    }
                                )
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (compareMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedProfiles = if (checked && selectedProfiles.size < 2) {
                                            selectedProfiles + name
                                        } else {
                                            selectedProfiles - name
                                        }
                                    },
                                    enabled = isSelected || selectedProfiles.size < 2
                                )
                                Text(
                                    name, 
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                // Show Profile 1/2 badge
                                val selList = selectedProfiles.toList()
                                if (isSelected) {
                                    val badge = if (selList.indexOf(name) == 0) "P1" else "P2"
                                    Surface(
                                        color = if (badge == "P1") BadgeP1 else BadgeP2,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            badge,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else {
                                Text(name, modifier = Modifier.weight(1f))
                                Row {
                                    TextButton(onClick = { onLoadProfile(name) }) { 
                                        Text(stringResource(R.string.load)) 
                                    }
                                    IconButton(onClick = { onDeleteProfile(name) }) {
                                        Text("X", color = ExpenseRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
