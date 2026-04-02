# Search Result Highlighting Implementation

## Current State
The OptimizedFileList displays filenames using a simple Text composable:
```kotlin
Text(
    text = item.name,
    style = MaterialTheme.typography.bodyLarge,
    // ... other properties
)
```

## Enhancement Goal
Highlight matching portions of filenames when displaying search results to improve scanability and user experience.

## Implementation Approach
1. Modify OptimizedFileList to accept an optional search query parameter
2. Create a helper function to generate highlighted text spans
3. Replace the simple Text with AnnotatedString that highlights matches
4. Handle case-insensitive matching and multiple occurrences

## Files to Modify
- `app/src/main/java/com/obfs/encrypt/ui/components/optimized/OptimizedFileList.kt`

## Detailed Implementation

### 1. Add Search Query Parameter
Modify the OptimizedFileList function signature to accept an optional search query:

```kotlin
@Composable
fun OptimizedFileList(
    // ... existing parameters ...
    searchQuery: String = "", // NEW PARAMETER
    // ... existing parameters ...
)
```

### 2. Create Highlighting Helper Function
Add this function to the file:

```kotlin
@Composable
private fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    highlightColor: Color = MaterialTheme.colorScheme.primary
) {
    if (query.isEmpty() || text.isEmpty()) {
        Text(
            text = text,
            style = style
        )
        return
    }

    val lowerCaseText = text.lowercase()
    val lowerCaseQuery = query.lowercase()
    val annotatedString = buildAnnotatedString {
        var start = 0
        while (true) {
            val index = lowerCaseText.indexOf(lowerCaseQuery, start)
            if (index == -1) {
                // No more matches, append remaining text
                append(text.substring(start))
                break
            }
            
            // Append text before match
            append(text.substring(start, index))
            
            // Append highlighted match
            val matchEnd = index + query.length
            val matchText = text.substring(index, matchEnd)
            pushSpan(
                style = SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(matchText)
            }
            popSpan()
            
            start = matchEnd
        }
    }
    
    Text(
        text = annotatedString,
        style = style
    )
}
```

### 3. Modify File Item Text Display
Replace the existing filename Text composable (around line 638) with:

```kotlin
// File name with optional highlighting
if (searchQuery.isNotEmpty()) {
    HighlightedText(
        text = item.name,
        query = searchQuery,
        style = MaterialTheme.typography.bodyLarge,
        highlightColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    )
} else {
    Text(
        text = item.name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isSelected) FontWeight.SemiBold else if (item.isDirectory) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    )
}
```

### 4. Update Function Calls
Update all calls to OptimizedFileList to pass the search query parameter:

In FileBrowserScreen.kt, modify the OptimizedFileList call (around line 604):

```kotlin
OptimizedFileList(
    filesAndFolders = displayFiles,
    selectedItems = selectedItems,
    isLoading = isLoading,
    favoritePaths = favoritePaths,
    // ... existing parameters ...
    searchQuery = if (showSearch) debouncedSearchQuery else "", // Pass debounced query when searching
    // ... existing parameters ...
)
```

## Visual Design
- Matching text appears in primary color with bold weight
- Non-matching text uses normal styling
- Selected items maintain their selected appearance with appropriate color adaptation
- Works correctly in both light and dark themes

## Performance Considerations
- The highlighting function is lightweight and called only for visible items
- Uses efficient string operations (indexOf, substring)
- No allocations during scrolling for non-searching state
- Leverages Compose's smart recomposition

## Edge Cases Handled
- Empty search query (falls back to normal text)
- Empty filename
- Multiple occurrences of query in filename
- Query longer than filename
- Case insensitive matching
- Special characters in query or filename

## Testing Scenarios
1. Normal search with matches
2. Search with no matches (should show normal text)
3. Empty search query
4. Multiple matches in same filename
5. Matches at beginning, middle, end of filename
6. Selected vs non-selected item highlighting
7. Light/dark theme adaptation
8. Very long filenames with matches

## Accessibility
- Maintains proper text scaling
- Preserves semantic meaning (doesn't alter actual text)
- Works with screen readers (annotated string is still readable)
- Sufficient color contrast (uses theme colors)

## Future Enhancements
- Support for highlighting multiple different queries
- Configurable highlight colors
- Fuzzy matching highlighting
- Highlighting in other text fields (size, date, etc.)