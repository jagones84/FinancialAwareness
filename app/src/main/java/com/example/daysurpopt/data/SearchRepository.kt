// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository for performing web searches and retrieving system time.
 */
object SearchRepository {

    /**
     * Performs a web search using DuckDuckGo's HTML interface.
     * This is a "free" search that doesn't require an API key.
     * 
     * @param query The search query.
     * @return A list of result snippets or an error message.
     */
    suspend fun performWebSearch(query: String): String = withContext(Dispatchers.IO) {
        try {
            // DuckDuckGo HTML version is easier to parse and less likely to block simple requests
            val url = "https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .get()

            val results = doc.select(".result")
            if (results.isEmpty()) return@withContext "No results found for '$query'."

            val builder = StringBuilder()
            builder.append("Search results for '$query':\n\n")
            
            // Limit to top 5 results
            results.take(5).forEachIndexed { index, element ->
                val title = element.select(".result__title").text()
                val snippet = element.select(".result__snippet").text()
                val link = element.select(".result__url").text()
                
                builder.append("${index + 1}. $title\n")
                builder.append("   Snippet: $snippet\n")
                builder.append("   Link: $link\n\n")
            }
            
            builder.toString()
        } catch (e: Exception) {
            "Failed to perform search: ${e.message}"
        }
    }

    /**
     * Fetches the text content of a specific URL.
     * Useful for reading the full details of a search result.
     *
     * @param url The URL to fetch.
     * @return The text content of the page (truncated to ~5000 chars).
     */
    suspend fun fetchPageContent(url: String): String = withContext(Dispatchers.IO) {
        try {
            // Clean URL if it's a DuckDuckGo redirect (common in html interface)
            val cleanUrl = if (url.contains("duckduckgo.com/l/?uddg=")) {
                java.net.URLDecoder.decode(url.substringAfter("uddg=").substringBefore("&rut="), "UTF-8")
            } else {
                url.trim()
            }

            val doc = Jsoup.connect(cleanUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(10000)
                .get()

            // Extract text from paragraphs to avoid navigation/menu noise
            val paragraphs = doc.select("p")
            val sb = StringBuilder()
            sb.append("Content from $cleanUrl:\n\n")
            
            for (p in paragraphs) {
                val text = p.text()
                if (text.length > 50) { // Filter out short/irrelevant lines
                    sb.append(text).append("\n\n")
                }
                if (sb.length > 5000) break // Limit content size
            }
            
            if (sb.length <= 50) {
                 // Fallback to body text if paragraphs are empty
                 sb.append(doc.body().text().take(5000))
            }

            sb.toString()
        } catch (e: Exception) {
            "Failed to fetch content from $url: ${e.message}"
        }
    }

    /**
     * Returns the current system time in a readable format.
     */
    fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return "Current Time: ${sdf.format(Date())}"
    }
}
