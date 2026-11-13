// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import java.net.URI
import org.jsoup.Jsoup
import org.prauga.feed.core.model.SourceKind

/** Turns what a person pastes or shares into feed addresses to try, in order. */
object FeedLinks {

    data class Candidate(val url: String, val kind: SourceKind)

    /** Normalises typed text into a URL, or null when it can't be one. */
    fun normalise(input: String): String? {
        var text = input.trim()
        Regex("https?://\\S+").find(text)?.let { text = it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'', '"') }
        if (text.isEmpty() || ' ' in text) return null
        if (!text.startsWith("http://") && !text.startsWith("https://")) text = "https://$text"
        return try {
            val uri = URI(text)
            if (uri.host.isNullOrEmpty() || '.' !in uri.host) null else uri.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun kindOf(url: String): SourceKind {
        val host = host(url)
        val path = path(url)
        return when {
            host.endsWith("youtube.com") || host == "youtu.be" -> SourceKind.YOUTUBE
            host.endsWith("reddit.com") -> SourceKind.REDDIT
            host == "bsky.app" -> SourceKind.BLUESKY
            Regex("^/@[A-Za-z0-9_.]+").containsMatchIn(path) -> SourceKind.MASTODON
            else -> SourceKind.WEB
        }
    }

    /**
     * Feed URLs that a known site publishes for [url], without fetching anything. Empty when the
     * page itself has to be read to find the feed.
     */
    fun direct(url: String): List<Candidate> {
        val host = host(url)
        val path = path(url).trimEnd('/')
        val segments = path.split('/').filter { it.isNotEmpty() }
        return when (kindOf(url)) {
            SourceKind.YOUTUBE -> {
                val query = query(url)
                when {
                    path.startsWith("/feeds/videos.xml") -> listOf(Candidate(url, SourceKind.YOUTUBE))
                    segments.getOrNull(0) == "channel" && segments.size >= 2 ->
                        listOf(youtube("channel_id=${segments[1]}"))
                    segments.getOrNull(0) == "playlist" && query["list"] != null ->
                        listOf(youtube("playlist_id=${query["list"]}"))
                    else -> emptyList()
                }
            }
            SourceKind.REDDIT ->
                when {
                    path.endsWith(".rss") -> listOf(Candidate(url, SourceKind.REDDIT))
                    segments.getOrNull(0) in setOf("r", "user", "u") && segments.size >= 2 ->
                        listOf(
                            Candidate(
                                "https://www.reddit.com/${segments[0]}/${segments[1]}/.rss",
                                SourceKind.REDDIT,
                            )
                        )
                    else -> emptyList()
                }
            SourceKind.BLUESKY ->
                if (segments.getOrNull(0) == "profile" && segments.size >= 2)
                    listOf(
                        Candidate(
                            "https://bsky.app/profile/${segments[1]}/rss",
                            SourceKind.BLUESKY,
                        )
                    )
                else emptyList()
            SourceKind.MASTODON ->
                if (path.endsWith(".rss")) listOf(Candidate(url, SourceKind.MASTODON))
                else
                    listOf(Candidate("https://$host/${segments.first()}.rss", SourceKind.MASTODON))
            SourceKind.WEB -> emptyList()
        }
    }

    /** Common feed paths to try on a site that doesn't announce its feed. */
    fun guesses(url: String): List<Candidate> {
        val root = "https://${host(url)}"
        return listOf("/feed", "/rss", "/feed.xml", "/rss.xml", "/atom.xml", "/index.xml", "/feeds/posts/default")
            .map { Candidate(root + it, SourceKind.WEB) }
    }

    /** Feeds a web page announces with `<link rel="alternate">`, best first. */
    fun discover(html: String, pageUrl: String): List<Candidate> {
        val doc = Jsoup.parse(html, pageUrl)
        val kind = kindOf(pageUrl)
        return doc
            .select("link[rel~=(?i)alternate][href]")
            .filter { link ->
                val type = link.attr("type").lowercase()
                "rss" in type || "atom" in type || "feed+json" in type || type == "application/json"
            }
            .sortedBy { link ->
                val href = link.attr("href").lowercase()
                if ("comments" in href) 1 else 0
            }
            .map { Candidate(it.absUrl("href").ifEmpty { it.attr("href") }, kind) }
            .distinctBy { it.url }
    }

    /** The site's favicon as a fallback icon. */
    fun iconFor(siteUrl: String?): String? {
        val host = siteUrl?.let(::host)?.takeIf { it.isNotEmpty() } ?: return null
        return "https://$host/favicon.ico"
    }

    fun host(url: String): String =
        try {
            URI(url).host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: ""
        } catch (_: Exception) {
            ""
        }

    private fun path(url: String): String =
        try {
            URI(url).path ?: ""
        } catch (_: Exception) {
            ""
        }

    private fun query(url: String): Map<String, String> =
        try {
            URI(url).rawQuery.orEmpty().split('&').mapNotNull {
                val (k, v) = it.split('=', limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
                k to v
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }

    private fun youtube(param: String) =
        Candidate("https://www.youtube.com/feeds/videos.xml?$param", SourceKind.YOUTUBE)
}
