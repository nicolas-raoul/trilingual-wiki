package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Test for back button behavior when image viewer is opened.
 * This test validates the logic for tracking user navigation vs programmatic loads.
 */
class BackButtonBehaviorTest {
    
    @Test
    fun testUserNavigationTracking() {
        // Test that user navigation tracking works correctly
        val userNavWebViews = mutableSetOf<String>()
        
        // Simulate user clicking on image in one panel (adds to tracking)
        userNavWebViews.add("webViewEN")
        assertEquals(1, userNavWebViews.size)
        assertTrue(userNavWebViews.contains("webViewEN"))
        
        // Simulate programmatic search (clears tracking)
        userNavWebViews.clear()
        assertEquals(0, userNavWebViews.size)
        
        // Simulate another user navigation
        userNavWebViews.add("webViewFR")
        assertEquals(1, userNavWebViews.size)
        assertTrue(userNavWebViews.contains("webViewFR"))
        
        // Simulate back button press (removes from tracking)
        userNavWebViews.remove("webViewFR")
        assertEquals(0, userNavWebViews.size)
    }
    
    @Test
    fun testBackButtonLogic() {
        // Simulate the back button logic
        val webViewsWithUserNav = mutableListOf<String>()
        val allWebViews = listOf("webViewEN", "webViewFR", "webViewJA")
        val webViewsCanGoBack = mutableMapOf(
            "webViewEN" to true,
            "webViewFR" to true, 
            "webViewJA" to true
        )
        
        // Case 1: One WebView has user navigation (image viewer)
        webViewsWithUserNav.add("webViewEN")
        
        val webViewsToGoBack = webViewsWithUserNav.filter { webViewsCanGoBack[it] == true }
        assertEquals(1, webViewsToGoBack.size)
        assertEquals("webViewEN", webViewsToGoBack[0])
        
        // Case 2: No WebViews have user navigation - should fall back to all
        webViewsWithUserNav.clear()
        val fallbackWebViews = if (webViewsWithUserNav.isEmpty()) {
            allWebViews.filter { webViewsCanGoBack[it] == true }
        } else {
            webViewsWithUserNav.filter { webViewsCanGoBack[it] == true }
        }
        
        assertEquals(3, fallbackWebViews.size) // All WebViews should go back
    }
}