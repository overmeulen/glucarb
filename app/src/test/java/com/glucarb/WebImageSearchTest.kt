package com.glucarb

import com.glucarb.domain.WebImageSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class WebImageSearchTest {

    @Test
    fun `the item name becomes an encoded image query`() {
        assertEquals(
            "https://www.google.com/search?tbm=isch&q=Pain+au+chocolat+%26+caf%C3%A9",
            WebImageSearch.urlFor("  Pain au chocolat & caf\u00E9 "),
        )
    }

    @Test
    fun `a blank name still opens an image search`() {
        assertEquals("https://www.google.com/imghp", WebImageSearch.urlFor("   "))
    }
}
