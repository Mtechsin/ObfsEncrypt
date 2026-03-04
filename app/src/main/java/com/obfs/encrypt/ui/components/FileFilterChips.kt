package com.obfs.encrypt.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.obfs.encrypt.crypto.EncryptionHelper

enum class FileFilter(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Outlined.FilterList),
    IMAGES("Images", Icons.Outlined.Image),
    VIDEOS("Videos", Icons.Outlined.VideoFile),
    DOCUMENTS("Docs", Icons.Outlined.Description),
    ENCRYPTED(".obfs", Icons.Outlined.Lock)
}

@Composable
fun FileFilterChips(
    selectedFilter: FileFilter,
    onFilterSelected: (FileFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FileFilter.entries.forEach { filter ->
            FilterChip(
                filter = filter,
                isSelected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    filter: FileFilter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "chipBackground"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "chipContent"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = filter.icon,
                contentDescription = filter.label,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Text(
                text = filter.label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
        }
    }
}

fun shouldIncludeFile(fileName: String, isDirectory: Boolean, filter: FileFilter): Boolean {
    if (isDirectory) return true // Always show directories
    
    val extension = fileName.substringAfterLast('.', "").lowercase()
    
    return when (filter) {
        FileFilter.ALL -> true
        FileFilter.IMAGES -> extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "raw")
        FileFilter.VIDEOS -> extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v")
        FileFilter.DOCUMENTS -> extension in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv", "json", "xml", "html", "htm")
        FileFilter.ENCRYPTED -> extension == "obfs"
    }
}
