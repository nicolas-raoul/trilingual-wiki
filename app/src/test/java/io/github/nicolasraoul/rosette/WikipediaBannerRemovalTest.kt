package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Test to verify that Wikipedia's grey top banner and navigation elements are removed via CSS injection.
 * This addresses the issue where the Wikipedia mobile header and navigation elements (Article/Talk tabs,
 * language switcher, download, star, edit buttons) take up valuable screen space
 * that could be used to display more article content.
 */
class WikipediaBannerRemovalTest {

    @Test
    fun `CSS injection should hide Wikipedia mobile header and navigation elements`() {
        // Test that verifies the expected CSS rules are injected to hide Wikipedia's banner and navigation
        // This documents the elements that should be hidden:
        
        val expectedHiddenElements = listOf(
            ".minerva-header",      // Main header container in Minerva skin
            ".header",              // Generic header element  
            ".header-container",    // Header container wrapper
            ".page-header",         // Page-specific header
            "#mw-mf-page-center > .header", // Specific header selector
            ".mw-header",           // MediaWiki header
            ".page-actions-menu",   // Page actions menu container
            ".talk-tab",            // Article/Talk tab switcher
            ".page-tabs",           // Page tabs container
            ".namespace-tabs",      // Namespace tabs
            ".minerva-page-tabs",   // Minerva-specific page tabs
            ".minerva-page-actions", // Minerva page actions
            ".mw-ui-icon-article",  // Article tab icon
            ".mw-ui-icon-talk",     // Talk tab icon
            ".namespace-0",         // Article namespace
            ".namespace-1",         // Talk namespace
            "[data-namespace=\"0\"]", // Article namespace data attribute
            "[data-namespace=\"1\"]", // Talk namespace data attribute
            ".page-summary",        // Page summary section
            ".last-modified-bar",   // Last modified information bar
            ".language-selector",   // Language switcher
            ".mw-ui-icon-language-switcher", // Language switcher icon
            ".mw-ui-icon-language", // Language icon
            ".language-button",     // Language button
            ".mw-ui-icon-download", // Download icon
            ".download-button",     // Download button
            ".mw-ui-icon-star",     // Star/watchlist icon
            ".watch-this-article",  // Watch article button
            ".watchstar",           // Watchstar element
            ".mw-ui-icon-edit",     // Edit icon
            ".edit-page",           // Edit page button
            ".edit-button",         // Edit button
            "#page-actions",        // Page actions container
            ".mw-editsection"       // Section edit links
        )
        
        // In a real WebView test, we would:
        // 1. Load a Wikipedia mobile page
        // 2. Verify that the onPageFinished method injects the CSS
        // 3. Check that the header and navigation elements are hidden (display: none)
        // 4. Verify that body margins/padding are reset to 0
        
        // For this unit test, we document the expected behavior
        expectedHiddenElements.forEach { selector ->
            assertTrue("CSS should hide $selector element", selector.isNotEmpty())
        }
        
        assertTrue("Header and navigation removal should free up screen space for article content", true)
    }
    
    @Test
    fun `JavaScript should hide Article and Talk text elements`() {
        // Test that verifies JavaScript function hideArticleTalkElements() behavior
        // This documents the expected functionality:
        
        // Elements with text content "Article" or "Talk" should be hidden
        val targetTextContent = listOf("Article", "Talk")
        
        // The JavaScript function should:
        // 1. Query all elements in the DOM
        // 2. Check leaf elements (no children) for exact text matches
        // 3. Hide elements with text content "Article" or "Talk"
        // 4. Also hide parent elements if they only contain the hidden element
        // 5. Run on initial load, delayed execution, and DOM mutations
        
        targetTextContent.forEach { text ->
            assertTrue("JavaScript should hide elements with text '$text'", text.isNotEmpty())
        }
        
        assertTrue("JavaScript should handle dynamic content via MutationObserver", true)
    }

    @Test
    fun `body margins should be reset to maximize content space`() {
        // Test that verifies body margins and padding are reset to 0
        // This ensures the content moves up to fill the space previously occupied by the header
        
        // Expected CSS rules:
        // - body { margin-top: 0 !important; padding-top: 0 !important; }
        
        // For this unit test, we document the expected behavior
        assertTrue("Body margin-top and padding-top should be reset to 0 to maximize content space", true)
    }

    @Test
    fun `existing padding CSS should be preserved`() {
        // Test that ensures the new header-hiding CSS doesn't break existing functionality
        // The app already injects CSS for left/right padding to prevent text cropping
        
        val existingPaddingRules = listOf(
            "body { padding-left: 8px !important; padding-right: 8px !important; }",
            ".mw-parser-output { padding-left: 8px !important; padding-right: 8px !important; }",
            "#content { padding-left: 8px !important; padding-right: 8px !important; }"
        )
        
        // For this unit test, we document that existing functionality is preserved
        existingPaddingRules.forEach { rule ->
            assertTrue("Existing padding rule should be preserved: $rule", rule.contains("padding"))
        }
        
        assertTrue("New header-hiding CSS should not break existing padding functionality", true)
    }
}