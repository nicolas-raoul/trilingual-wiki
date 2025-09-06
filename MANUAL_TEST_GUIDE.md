# Manual Testing Guide for FAB Menu Behavior Fix

## Test Scenario: FAB should not show menu items when article is missing

### Setup
1. Build and install the app on an Android device/emulator
2. Ensure the app has default languages: Spanish (es) and English (en)

### Test Steps

#### Test Case 1: Missing Article Scenario
1. Search for an article that exists in English but NOT in Spanish
   - Example: Search for "Silicon Valley" (likely to exist in English but may not exist in Spanish)
   - Or search for any very recent English topic that hasn't been translated yet

2. Expected behavior:
   - English webview should show the actual Wikipedia article
   - Spanish webview should show: "The es edition is waiting for someone to write an article on that topic."
   - English FAB should show menu items when clicked (Open in Wikipedia app, Open in web browser, Edit)
   - **Spanish FAB should NOT show any menu items when clicked** ✓ (This is the fix)

#### Test Case 2: Both Articles Exist
1. Search for a common article that exists in both languages
   - Example: Search for "Paris" or "Albert Einstein"

2. Expected behavior:
   - Both webviews should show actual Wikipedia articles
   - Both FABs should show menu items when clicked ✓

#### Test Case 3: Article Transition
1. Search for an article missing in one language (verify FAB doesn't show menu)
2. Search for an article that exists in both languages 
3. Verify that the previously missing language's FAB now shows menu items ✓

### Technical Verification Points
- Check logs for "showMissingArticleMessage" calls
- Verify `webViewsWithMissingArticles` set is populated correctly
- Confirm `onPageStarted` removes webviews from the set when loading real URLs
- Ensure FAB click listener checks the set before showing popup menu

### Code Changes Summary
- Added `webViewsWithMissingArticles` Set to track webviews with missing articles
- Modified `showMissingArticleMessage()` to add webview to tracking set
- Updated FAB click listener to check tracking set before showing menu
- Modified `onPageStarted()` to remove webviews when loading real URLs

### Expected Result
When a webview displays "The [lang] edition is waiting for someone to write an article on that topic.", the FAB for that language should remain visible but clicking it should do nothing (no popup menu shown).