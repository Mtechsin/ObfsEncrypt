# Debounced Search Implementation

## Current Issues
1. Search triggers on every character change causing excessive API calls
2. No visual feedback during search operations
3. Poor user experience when typing quickly

## Solution Overview
Implement debounced search with visual feedback:
- Delay search execution until user pauses typing (300ms)
- Show loading indicator during search
- Clear previous results when starting new search
- Maintain search state properly

## File Modifications Needed

### 1. FileBrowserScreen.kt Changes

#### State Variables (around line 127)
```kotlin
// ADD THESE VARIABLES
var debouncedSearchQuery by rememberSaveable { mutableStateOf("") }
private var debounceJob: Job? = null
val isSearching by remember { mutableStateOf(false) }
// REPLACE searchResults initialization with:
val searchResults by remember { mutableStateOf<List<FileItem>?>(null) }
val searchError by remember { mutableStateOf<String?>(null) }
```

#### LaunchedEffect for Debouncing (add after line 134)
```kotlin
// Debounce search queries
LaunchedEffect(searchQuery) {
    // Cancel previous debounce job if exists
    debounceJob?.cancel()
    
    // If query is empty, clear results immediately
    if (searchQuery.isEmpty()) {
        searchResults.value = null
        searchError.value = null
        isSearching.value = false
        debouncedSearchQuery.value = ""
        return@LaunchedEffect
    }
    
    // Set searching state
    isSearching.value = true
    searchError.value = null
    
    // Create new debounce job
    debounceJob = launch {
        delay(300) // 300ms debounce
        // Only proceed if current query hasn't changed
        if (searchQuery.isNotEmpty()) {
            debouncedSearchQuery.value = searchQuery
            // Perform search
            fileManagerViewModel.searchFiles(
                query = searchQuery,
                searchSubfolders = searchSubfolders
            ) { results ->
                searchResults.value = results
                isSearching.value = false
            }
        } else {
            isSearching.value = false
        }
    }
}

// Cleanup on disposal
DisposableEffect(Unit) {
    onDispose {
        debounceJob?.cancel()
    }
}
```

#### Modified FileSearchBar Usage (around line 462)
```kotlin
FileSearchBar(
    query = searchQuery,
    debouncedQuery = debouncedSearchQuery,
    isSearching = isSearching,
    onQueryChange = { 
        searchQuery = it
        // Results will be cleared automatically by debounce effect
    },
    onSearch = { 
        // This is now handled by debounce effect, but keep for explicit search
        if (it.isNotBlank()) {
            fileManagerViewModel.searchFiles(
                query = it,
                searchSubfolders = searchSubfolders
            ) { results ->
                searchResults.value = results
                isSearching.value = false
            }
        }
    },
    searchSubfolders = searchSubfolders,
    onSearchSubfoldersChange = { 
        searchSubfolders = it
        if (searchQuery.isNotBlank()) {
            // Trigger immediate search when subfolder toggle changes
            fileManagerViewModel.searchFiles(
                query = searchQuery,
                searchSubfolders = it
            ) { results ->
                searchResults.value = results
                isSearching.value = false
            }
        }
    },
    modifier = Modifier.padding(vertical = 4.dp)
)
```

### 2. FileSearchBar.kt Changes

#### Add Parameters (around line 38)
```kotlin
@Composable
fun FileSearchBar(
    query: String,
    debouncedQuery: String = "",
    isSearching: Boolean = false,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search files...",
    searchSubfolders: Boolean = false,
    onSearchSubfoldersChange: (Boolean) -> Unit = {}
) {
```

#### Add Loading Indicator (around line 103, after OutlinedTextField)
```kotlin
// Search status indicator
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.End
) {
    if (isSearching) {
        // Show loading spinner
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    } else if (debouncedQuery.isNotEmpty()) {
        // Show search completed text
        Text(
            text = "Search completed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    // Search subfolders toggle (move this inside the row)
    AnimatedVisibility(
        visible = query.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        // ... existing checkbox code ...
    }
}
```

### 3. Visual Feedback in Main UI

#### Add Search Status Display (around line 460, inside the if (showSearch) block)
```kotlin
if (showSearch) {
    FileSearchBar(
        // ... existing parameters ...
    )
    
    // Search status message
    when {
        isSearching -> {
            Text(
                text = "Searching...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
        searchError != null -> {
            Text(
                text = searchError ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
        else -> {
            // Show result count when available
            val resultCount = searchResults?.size ?: 0
            if (resultCount > 0 || debouncedSearchQuery.isNotEmpty()) {
                Text(
                    text = if (debouncedSearchQuery.isNotEmpty()) 
                        "Found $resultCount results for \"$debouncedSearchQuery\"" 
                    else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
```

## Expected Benefits
1. Reduced CPU usage during rapid typing
2. Better responsiveness - UI doesn't freeze on each keystroke
3. Clear visual feedback about search state
4. Fewer unnecessary search operations
5. Improved battery life on mobile devices

## Testing Considerations
1. Test with various typing speeds
2. Verify debounce timing (300ms) feels natural
3. Ensure search results update correctly after debounce
4. Test edge cases like rapid clearing/typing
5. Verify loading states display correctly
6. Check memory leaks from coroutine jobs

## Implementation Notes
- Uses Kotlin coroutines for debouncing rather than Handler/postDelayed
- Properly cancels previous debounce jobs to prevent race conditions
- Maintains separation between raw query and debounced query
- Provides clear visual states: idle, searching, results, error
- Handles empty queries gracefully by clearing results immediately