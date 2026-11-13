// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface FetchResult {
    class Body(
        val bytes: ByteArray,
        val finalUrl: String,
        val contentType: String?,
        val etag: String?,
        val lastModified: String?,
    ) : FetchResult

    data object NotModified : FetchResult

    data class Failed(val status: Int?, val reason: String) : FetchResult
}

/**
 * Plain GETs with conditional headers, a size limit and politeness: at most [perHost] requests
 * to one host at a time, and one to hosts that throttle unknown readers (Reddit).
 */
class Fetcher(
    private val client: HttpClient,
    private val perHost: Int = 2,
    private val maxBytes: Int = 6 * 1024 * 1024,
) {
    private val hosts = ConcurrentHashMap<String, Semaphore>()

    suspend fun get(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
        accept: String = FEED_ACCEPT,
    ): FetchResult {
        val host =
            try {
                URI(url).host?.lowercase().orEmpty()
            } catch (_: Exception) {
                return FetchResult.Failed(null, "Not a link")
            }
        val gate =
            hosts.getOrPut(host) { Semaphore(if (host.endsWith("reddit.com")) 1 else perHost) }
        return gate.withPermit {
            try {
                client
                    .prepareGet(url) {
                        header(HttpHeaders.Accept, accept)
                        header(HttpHeaders.AcceptLanguage, "en-IN,en;q=0.9")
                        etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                        lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
                    }
                    .execute { response ->
                        when {
                            response.status == HttpStatusCode.NotModified -> FetchResult.NotModified
                            response.status.value !in 200..299 ->
                                FetchResult.Failed(
                                    response.status.value,
                                    "HTTP ${response.status.value}",
                                )
                            else -> {
                                val channel = response.bodyAsChannel()
                                val out = ByteArrayOutputStream()
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    val n = channel.readAvailable(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    if (out.size() > maxBytes) break
                                }
                                FetchResult.Body(
                                    bytes = out.toByteArray(),
                                    finalUrl = response.request.url.toString(),
                                    contentType = response.headers[HttpHeaders.ContentType],
                                    etag = response.headers[HttpHeaders.ETag],
                                    lastModified = response.headers[HttpHeaders.LastModified],
                                )
                            }
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                FetchResult.Failed(null, e.message ?: e.javaClass.simpleName)
            } catch (e: Exception) {
                FetchResult.Failed(null, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    companion object {
        const val FEED_ACCEPT =
            "application/rss+xml, application/atom+xml, application/feed+json, " +
                "application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.7, */*;q=0.5"
        const val PAGE_ACCEPT = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5"
    }
}
