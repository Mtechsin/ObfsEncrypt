# Search History Chip Component

## Purpose
Provide a reusable chip component for displaying search history items in the FileSearchBar dropdown.

## Design
- Material3 chip with query text and delete icon
- Clickable to select the query
- Long-press or secondary action to delete from history
- Visual feedback for pressed states
- Consistent with app's Material3 theme

## File Location
`app/src/main/java/com/obfs/encrypt/ui/components/SearchHistoryChip.kt`

## Component Interface
```kotlin
@Composable
fun SearchHistoryChip(
    query: String,
    onSelected: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
)
```

## Visual Design
- Chip container with background color
- Query text left-aligned
- Delete icon (X) right-aligned
- Ripple effect on click
- Proper spacing and typography

## Implementation Details
1. Uses Material3 Chip or custom Row with clickable behavior
2. Shows query text with ellipsis for long queries
3. Delete icon only shows on hover/long-press or always visible
4. Separate callbacks for selection vs deletion
5. Accessible content descriptions
6. Theme-aware colors

## Usage in FileSearchBar
- Display history items in dropdown below search field
- Handle selection to populate search field
- Handle deletion to remove from history
- Show appropriate empty state when no history

## Interaction Patterns
1. **Tap query**: Selects query, closes dropdown, populates search field
2. **Tap delete icon**: Removes query from history, updates chip list
3. **Long-press**: Optional alternative for delete action
4. **Swipe to dismiss**: Optional enhancement for touch devices

## Accessibility
- Proper content descriptions for both query and delete action
- Sufficient touch targets (minimum 48dp)
- Color contrast compliant
- Screen reader friendly

## Styling Guidelines
- Use Material3 chip colors and typography
- Height: 32dp or 36dp
- Horizontal padding: 12dp
- Vertical padding: 6dp
- Corner radius: 16dp (pill shape) or 8dp
- Font size: bodySmall or labelLarge

## Integration with SearchHistoryRepository
- FileSearchBar collects searchHistory flow
- Maps history items to SearchHistoryChip components
- Handles onSelected by setting searchQuery and triggering search
- Handles onDeleted by calling repository.removeSearchQuery

## Performance Considerations
- Lightweight composable with minimal recompositions
- Stable keys for history items if needed
- Efficient deletion without full list rebuild when possible
- Proper disposal of any animations or effects

## Error Handling
- Gracefully handles empty or null queries
- Prevents duplicate chips for same query (repository ensures uniqueness)
- Safe callback invocation (null checks if needed)

## Testing Scenarios
1. Chip displays query text correctly
2. Selection callback fires on tap
3. Deletion callback fires on delete icon tap
4. Long query text truncates with ellipsis
5. Empty state when no history items
6. Theme adaptation (light/dark)
7. Pressed state visual feedback
8. Accessibility properties correct

## Dependencies
- Material3 components
- App theme colors and typography
- Optional: Icon vectors for delete action