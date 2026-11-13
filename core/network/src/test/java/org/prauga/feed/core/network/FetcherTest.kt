// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FetcherTest {
    @Test
    fun conditionalGet() = runTest {
        val engine = MockEngine { request ->
            if (request.headers[HttpHeaders.IfNoneMatch] == "\"v1\"")
                respond("", HttpStatusCode.NotModified)
            else respond("<rss/>", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"v1\""))
        }
        val fetcher = Fetcher(HttpClient(engine) { expectSuccess = false })
        val first = fetcher.get("https://a.org/feed")
        assertTrue(first is FetchResult.Body)
        assertEquals("\"v1\"", (first as FetchResult.Body).etag)
        assertEquals("<rss/>", first.bytes.decodeToString())
        assertEquals(FetchResult.NotModified, fetcher.get("https://a.org/feed", etag = "\"v1\""))
    }

    @Test
    fun failures() = runTest {
        val engine = MockEngine { respond("no", HttpStatusCode.Forbidden) }
        val fetcher = Fetcher(HttpClient(engine) { expectSuccess = false })
        assertEquals(FetchResult.Failed(403, "HTTP 403"), fetcher.get("https://a.org/feed"))
        assertEquals("Not a link", (fetcher.get("ht tp://bad") as FetchResult.Failed).reason)
    }
}
