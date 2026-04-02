# Search History Repository Implementation

## Purpose
Provide persistent storage for user search queries to enable quick reuse of recent searches.

## Design
- Uses Android DataStore with Preferences API for lightweight persistence
- Stores search history as a set of strings (queries)
- Maintains order with most recent first
- Limits history to 10 items to prevent excessive storage
- Thread-safe with coroutine support

## File Location
`app/src/main/java/com/obfs/encrypt/data/SearchHistoryRepository.kt`

## Key Features
1. **addSearchQuery(query)**: Adds query to history, moving existing ones to top
2. **removeSearchQuery(query)**: Removes specific query from history
3. **clearSearchHistory()**: Clears all search history
4. **isInSearchHistory(query)**: Checks if query exists in history
5. **searchHistory Flow**: Provides reactive access to history list

## Implementation Details
- Uses Hilt for dependency injection
- Leverages existing pattern from RecentFoldersRepository
- Handles empty/blank queries by ignoring them
- Maintains proper ordering (most recent first)
- Automatic cleanup of old entries when limit exceeded

## Usage in FileBrowserScreen
1. Inject SearchHistoryRepository via hiltViewModel()
2. Collect searchHistory flow to update UI
3. Call addSearchQuery when user submits a search
4. Display history in search bar dropdown
5. Allow selecting history items to reuse queries

## Integration Points
- FileBrowserScreen: Show history dropdown, add queries on search
- FileSearchBar: Display history items, handle selection
- MainViewModel: Optional integration for global search history

## Testing Considerations
1. Verify history persistence across app restarts
2. Test order maintenance (most recent first)
3. Verify limit enforcement (10 items max)
4. Test empty query handling
5. Test concurrent access safety
6. Verify DataStore initialization doesn't block UI

## Dependencies
- Android Datastore Preferences
- Hilt dependency injection
- Kotlin coroutines for Flow

## Security Notes
- Stores only search queries, no sensitive data
- Data stored in app-private storage
- No network transmission of history