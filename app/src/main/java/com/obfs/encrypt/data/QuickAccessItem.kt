package com.obfs.encrypt.data

import android.os.Environment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File

data class QuickAccessItem(
    val id: String,
    val title: String,
    val path: String,
    val icon: ImageVector,
    val isFavorite: Boolean = false
)

object QuickAccessData {
    
    fun getDefaultQuickAccessItems(favorites: Set<String> = emptySet()): List<QuickAccessItem> {
        val externalStorage = Environment.getExternalStorageDirectory()
        
        return listOf(
            QuickAccessItem(
                id = "internal",
                title = "Internal Storage",
                path = externalStorage.absolutePath,
                icon = Icons.Outlined.Folder,
                isFavorite = favorites.contains(externalStorage.absolutePath)
            ),
            QuickAccessItem(
                id = "downloads",
                title = "Downloads",
                path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                icon = Icons.Outlined.CloudDownload,
                isFavorite = favorites.contains(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
            ),
            QuickAccessItem(
                id = "documents",
                title = "Documents",
                path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath,
                icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                isFavorite = favorites.contains(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath)
            ),
            QuickAccessItem(
                id = "pictures",
                title = "Pictures",
                path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath,
                icon = Icons.Outlined.Image,
                isFavorite = favorites.contains(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath)
            ),
            QuickAccessItem(
                id = "dcim",
                title = "Camera",
                path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
                icon = Icons.Outlined.PhotoCamera,
                isFavorite = favorites.contains(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath)
            ),
            QuickAccessItem(
                id = "videos",
                title = "Videos",
                path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath,
                icon = Icons.Outlined.VideoLibrary,
                isFavorite = favorites.contains(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath)
            )
        )
    }
    
    fun getFavoritesFromPaths(paths: Set<String>): List<QuickAccessItem> {
        val allItems = getDefaultQuickAccessItems()
        return allItems.filter { it.path in paths }
    }
}
