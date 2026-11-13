// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

/** Reads the date formats feeds use in practice: RFC 822 with its many variants, and ISO 8601. */
object FeedDates {
    private val zoneNames =
        mapOf(
            "UT" to "+0000",
            "UTC" to "+0000",
            "GMT" to "+0000",
            "Z" to "+0000",
            "EST" to "-0500",
            "EDT" to "-0400",
            "CST" to "-0600",
            "CDT" to "-0500",
            "MST" to "-0700",
            "MDT" to "-0600",
            "PST" to "-0800",
            "PDT" to "-0700",
            "IST" to "+0530",
            "BST" to "+0100",
            "CET" to "+0100",
            "CEST" to "+0200",
        )

    private val rfc822: List<DateTimeFormatter> =
        listOf("d MMM yyyy H:m:s Z", "d MMM yyyy H:m Z", "d MMM yy H:m:s Z", "d MMMM yyyy H:m:s Z")
            .map { pattern ->
                DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .appendPattern(pattern)
                    .toFormatter(Locale.ENGLISH)
            }

    private val isoLocal =
        DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .optionalStart()
            .appendLiteral('T')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .toFormatter(Locale.ROOT)

    fun parse(raw: String?): Instant? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (text.first().isDigit() && text.length >= 10 && text[4] == '-') iso(text)
        else rfc(text)
    }

    private fun iso(text: String): Instant? {
        val normal = text.replace(' ', 'T')
        return try {
            OffsetDateTime.parse(normal).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                ZonedDateTime.parse(normal).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(normal, isoLocal).toInstant(ZoneOffset.UTC)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    private fun rfc(text: String): Instant? {
        var s = text.substringAfter(',', text).trim().replace(Regex("\\s+"), " ")
        val parts = s.split(' ').toMutableList()
        val last = parts.lastOrNull() ?: return null
        val offset =
            zoneNames[last.uppercase(Locale.ROOT)]
                ?: if (Regex("[+-]\\d{2}:?\\d{2}").matches(last)) last.replace(":", "") else null
        if (offset != null) parts[parts.lastIndex] = offset else parts.add("+0000")
        s = parts.joinToString(" ")
        for (formatter in rfc822) {
            try {
                return ZonedDateTime.parse(s, formatter).toInstant()
            } catch (_: DateTimeParseException) {}
        }
        return null
    }
}
