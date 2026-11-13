// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import java.net.URI
import java.net.URISyntaxException

/** Removes tracking parameters from links before they are stored, shown or opened. */
object LinkCleaner {
    private val exact =
        setOf(
            "fbclid",
            "gclid",
            "dclid",
            "msclkid",
            "yclid",
            "mc_cid",
            "mc_eid",
            "igshid",
            "ref_src",
            "ref_url",
            "cmpid",
            "ito",
            "_ga",
            "_gl",
            "ncid",
            "ocid",
            "sr_share",
            "guccounter",
            "taid",
            "at_medium",
            "at_campaign",
            "spm",
            "share_id",
        )
    private val prefixes = listOf("utm_", "pk_", "mtm_", "hsa_", "__s", "oly_")

    fun clean(url: String): String {
        val uri =
            try {
                URI(url.trim())
            } catch (_: URISyntaxException) {
                return url.trim()
            }
        val query = uri.rawQuery ?: return uri.toString()
        val kept =
            query.split('&').filter { part ->
                val name = part.substringBefore('=').lowercase()
                part.isNotEmpty() && name !in exact && prefixes.none { name.startsWith(it) }
            }
        val base = url.trim().substringBefore('?').substringBefore('#')
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return if (kept.isEmpty()) base + fragment else "$base?${kept.joinToString("&")}$fragment"
    }
}
