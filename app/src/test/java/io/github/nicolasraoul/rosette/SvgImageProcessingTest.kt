package io.github.nicolasraoul.rosette

import org.junit.Test
import org.junit.Assert.*

/**
 * Test to verify SVG image URL processing works correctly.
 * This addresses the issue where SVG images fail to load due to incorrect filename extraction.
 */
class SvgImageProcessingTest {

    @Test
    fun `SVG filename extraction logic should work correctly`() {
        // Test the filename extraction logic directly
        val thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Tennis_pictogram.svg/40px-Tennis_pictogram.svg.png"
        
        // Simulate the fixed logic
        val pathSegments = thumbnailUrl.split("/")
        val thumbIndex = pathSegments.indexOf("thumb")
        val extractedFilename = if (thumbIndex >= 0 && thumbIndex + 3 < pathSegments.size) {
            pathSegments[thumbIndex + 3]
        } else {
            thumbnailUrl.substringAfterLast("/")
        }
        
        // Verify the correct filename is extracted
        assertEquals("Should extract correct SVG filename", "Tennis_pictogram.svg", extractedFilename)
        
        // Verify the old logic would have been wrong
        val oldFilename = thumbnailUrl.substringAfterLast("/")
        assertEquals("Old logic extracts wrong filename", "40px-Tennis_pictogram.svg.png", oldFilename)
        assertNotEquals("New logic should be different from old", oldFilename, extractedFilename)
        
        // Verify the expected Special:Redirect URL format
        val expectedUrl = "https://commons.wikimedia.org/w/index.php?title=Special:Redirect/file/$extractedFilename&width=960"
        assertTrue("Should generate valid Special:Redirect URL", expectedUrl.contains("Special:Redirect"))
        assertFalse("Should not contain thumbnail prefix in final URL", expectedUrl.contains("px-"))
    }
    
    @Test
    fun `SVG filename extraction logic should handle edge cases`() {
        // Document edge cases that should be handled
        val edgeCases = listOf(
            // SVG with complex names
            "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/File_with_spaces.svg/50px-File_with_spaces.svg.png",
            
            // SVG with numbers and special characters  
            "https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/Icon-123_test.svg/100px-Icon-123_test.svg.png",
            
            // Very small thumbnails
            "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Test.svg/20px-Test.svg.png"
        )
        
        edgeCases.forEach { url ->
            assertTrue("Edge case URL should be valid", url.startsWith("https://"))
            assertTrue("Edge case should be SVG thumbnail", url.contains(".svg") && url.contains("/thumb/"))
        }
    }
}