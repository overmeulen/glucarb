package com.glucarb.domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Finding an item's picture on the web without the app ever touching the network.
 *
 * Glucarb declares no permissions, INTERNET included, and the privacy policy rests on
 * that. So the search runs in the user's own browser, and the chosen picture comes back
 * through the system share sheet, where Glucarb is registered as a target for images.
 */
object WebImageSearch {

    /** An image search for [name]; a blank name opens an empty image search. */
    fun urlFor(name: String): String {
        val query = name.trim()
        if (query.isEmpty()) return "https://www.google.com/imghp"
        return "https://www.google.com/search?tbm=isch&q=" +
            URLEncoder.encode(query, StandardCharsets.UTF_8.name())
    }
}
