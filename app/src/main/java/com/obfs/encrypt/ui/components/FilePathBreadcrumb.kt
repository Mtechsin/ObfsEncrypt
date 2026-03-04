package com.obfs.encrypt.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import android.os.Environment

@Composable
fun FilePathBreadcrumb(
    currentPath: String,
    onPathClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pathParts = remember(currentPath) {
        generatePathParts(currentPath)
    }
    
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onHomeClick,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Go to root",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(2.dp)
            )
        }
        
        pathParts.forEachIndexed { index, part ->
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            
            if (index == pathParts.size - 1) {
                Text(
                    text = part,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onPathClick(part) }
                )
            } else {
                Text(
                    text = part,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { 
                        val targetPath = pathParts.take(index + 1).joinToString("/")
                        onPathClick(generateFullPath(pathParts.take(index + 1)))
                    }
                )
            }
        }
    }
}

private fun generatePathParts(path: String): List<String> {
    val storagePath = Environment.getExternalStorageDirectory().absolutePath
    return if (path.startsWith(storagePath)) {
        val relativePath = path.removePrefix(storagePath)
        listOf("Storage") + relativePath.split("/").filter { it.isNotEmpty() }
    } else {
        path.split("/").filter { it.isNotEmpty() }
    }
}

private fun generateFullPath(parts: List<String>): String {
    val storagePath = Environment.getExternalStorageDirectory().absolutePath
    return if (parts.first() == "Storage") {
        storagePath + "/" + parts.drop(1).joinToString("/")
    } else {
        parts.joinToString("/")
    }
}
