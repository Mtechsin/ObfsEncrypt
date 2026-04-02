package com.obfs.encrypt.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

@Composable
fun SearchHighlightText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        Text(
            text = text,
            modifier = modifier,
            maxLines = maxLines,
            overflow = overflow,
            style = MaterialTheme.typography.bodyLarge
        )
        return
    }

    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var index = lowerText.indexOf(lowerQuery, lastIndex)

        while (index >= 0) {
            append(text.substring(lastIndex, index))
            withStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(text.substring(index, index + query.length))
            }
            lastIndex = index + query.length
            index = lowerText.indexOf(lowerQuery, lastIndex)
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        style = MaterialTheme.typography.bodyLarge
    )
}
