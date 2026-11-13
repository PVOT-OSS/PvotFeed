// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorTest {
    private val page =
        """
        <html><head>
          <title>Rain arrives | Example</title>
          <meta property="og:image" content="/img/hero.jpg">
          <meta property="og:site_name" content="Example">
          <meta name="author" content="Asha Rao">
          <link rel="apple-touch-icon" href="/apple.png">
        </head><body>
          <nav><a href="/">Home</a><a href="/india">India</a></nav>
          <div class="share-buttons"><p>Share this story on every network you like, please.</p></div>
          <article>
            <h1>Rain arrives</h1>
            <p>The monsoon reached Kerala on Friday, three days earlier than usual, the weather office said.</p>
            <figure><img src="/img/clouds.jpg" width="800"><figcaption>Clouds over Kochi</figcaption></figure>
            <p>Farmers, who had waited for weeks, welcomed the rain, while cities braced for flooding.</p>
            <p>Also read: Ten things about the monsoon</p>
            <h2>What happens next</h2>
            <p>Forecasters expect the rain to cover the south of the country within a week, they said.</p>
          </article>
          <div class="related-stories"><p>Another story that is not this one, with plenty of words.</p></div>
          <footer><p>Copyright Example Media, all rights reserved, forever and ever.</p></footer>
        </body></html>
        """

    @Test
    fun article() {
        val result = Extractor.extract(page, "https://example.com/india/rain")
        assertEquals("https://example.com/img/hero.jpg", result.imageUrl)
        assertEquals("Asha Rao", result.byline)
        val texts = result.blocks.filterIsInstance<Block.Text>().map { it.text }
        assertTrue(texts.first().startsWith("The monsoon reached Kerala"))
        assertTrue("What happens next" in texts)
        assertFalse(texts.any { "Also read" in it })
        assertFalse(texts.any { "Share this story" in it })
        assertFalse(texts.any { "Copyright" in it })
        assertFalse(texts.any { "Another story" in it })
        assertEquals(
            Block.Image("https://example.com/img/clouds.jpg", "Clouds over Kochi"),
            result.blocks.filterIsInstance<Block.Image>().single(),
        )
    }

    @Test
    fun meta() {
        val meta = Extractor.meta(page, "https://example.com/india/rain")
        assertEquals("https://example.com/apple.png", meta.iconUrl)
        assertEquals("Example", meta.siteName)
    }

    @Test
    fun json() {
        val result = Extractor.extract(page, "https://example.com/india/rain")
        assertEquals(result, Extracted.fromJson(result.toJson()))
    }
}
