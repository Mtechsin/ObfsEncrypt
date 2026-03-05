package com.obfs.encrypt.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obfs.encrypt.viewmodel.SortOption

@Composable
fun SortOptionsDropdown(
    currentSortOrder: SortOption,
    currentSortAscending: Boolean,
    onSortOrderChange: (SortOption) -> Unit,
    onSortAscendingToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    androidx.compose.material3.IconButton(
        onClick = { expanded = true }
    ) {
        Icon(
            imageVector = Icons.Default.Sort,
            contentDescription = "Sort options",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        // Sort order options
        SortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = option.name.replaceFirstChar { it.uppercase() })
                        if (option == currentSortOrder) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (currentSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = if (currentSortAscending) "Ascending" else "Descending",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                },
                onClick = {
                    if (option == currentSortOrder) {
                        onSortAscendingToggle()
                    } else {
                        onSortOrderChange(option)
                    }
                    expanded = false
                },
                trailingIcon = {
                    if (option == currentSortOrder) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    }
}
