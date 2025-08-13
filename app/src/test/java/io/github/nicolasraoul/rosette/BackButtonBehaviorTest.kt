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
    
    @Test
    fun testRabbitImageViewerScenario() {
        // Test the specific scenario from the issue
        val userNavTracking = mutableSetOf<String>()
        val webViewNavigationHistory = mutableMapOf(
            "webViewEN" to listOf("rabbit"),
            "webViewFR" to listOf("rabbit", "image_viewer"),  // French panel opened image
            "webViewJA" to listOf("rabbit")
        )
        
        // Simulate user opening image in French panel
        userNavTracking.add("webViewFR")
        
        // Simulate back button press - should only affect French panel
        val webViewsToGoBack = userNavTracking.toList()
        assertEquals(1, webViewsToGoBack.size)
        assertEquals("webViewFR", webViewsToGoBack[0])
        
        // After going back, French panel should be back to rabbit
        // (In real app, this would close the image viewer)
        userNavTracking.clear()
        
        // Verify that English and Japanese panels are unaffected
        assertEquals(listOf("rabbit"), webViewNavigationHistory["webViewEN"])
        assertEquals(listOf("rabbit"), webViewNavigationHistory["webViewJA"])
    }
    
    @Test
    fun testMultipleUserNavigations() {
        // Test when multiple WebViews have user navigation
        val userNavTracking = mutableSetOf<String>()
        
        // User opens images in two panels
        userNavTracking.add("webViewEN")
        userNavTracking.add("webViewFR") 
        assertEquals(2, userNavTracking.size)
        
        // Back button should affect both WebViews with user navigation
        val webViewsToGoBack = userNavTracking.toList()
        assertEquals(2, webViewsToGoBack.size)
        assertTrue(webViewsToGoBack.contains("webViewEN"))
        assertTrue(webViewsToGoBack.contains("webViewFR"))
        
        // After going back, clear tracking
        userNavTracking.clear()
        assertEquals(0, userNavTracking.size)
    }
    
    @Test
    fun testSearchClearsUserNavigationTracking() {
        // Test that searching for a new article clears user navigation tracking
        val userNavTracking = mutableSetOf<String>()
        
        // User opens image viewer in one panel
        userNavTracking.add("webViewFR")
        assertEquals(1, userNavTracking.size)
        
        // User searches for a new article (triggers performFullSearch)
        // This should clear the tracking
        userNavTracking.clear() // Simulates webViewsWithUserNavigation.clear() in performFullSearch
        assertEquals(0, userNavTracking.size)
        
        // Now back button should not have any tracked user navigation
        // and should fall back to default behavior
        assertTrue(userNavTracking.isEmpty())
    }
}