package com.athletebridge.android.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.athletebridge.android.ui.theme.LogoGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipMultiSelect(
    items: List<String>,
    selection: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val isSelected = selection.contains(item)
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newSelection = selection.toMutableSet()
                    if (isSelected) newSelection.remove(item) else newSelection.add(item)
                    onSelectionChange(newSelection)
                },
                label = { Text(item) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LogoGreen,
                    selectedLabelColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
