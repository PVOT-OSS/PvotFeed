// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.stories

import java.text.Normalizer

/** Splits headlines into comparable words: lower case, no accents, no filler, rough singulars. */
internal object Words {
    private val split = Regex("[^\\p{L}\\p{N}]+")
    private val marks = Regex("\\p{M}+")

    fun of(text: String, limit: Int = Int.MAX_VALUE): List<String> {
        val plain = marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
        return plain
            .lowercase()
            .replace("’", "'")
            .replace("'s ", " ")
            .split(split)
            .asSequence()
            .filter { it.length >= 2 && it !in STOP }
            .map(::stem)
            .filter { it !in STOP }
            .take(limit)
            .toList()
    }

    /**
     * Capitalised words in a headline: likely names of people, places and things. In a Title Case
     * headline every word looks like a name, so all of them count.
     */
    fun names(title: String): Set<String> {
        val raw = title.split(split).filter { it.isNotEmpty() }
        val capitalised = raw.filter { it.first().isUpperCase() }
        return of(capitalised.joinToString(" ")).toSet()
    }

    private fun stem(word: String): String =
        when {
            word.any { it.isDigit() } -> word
            word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") ->
                word.dropLast(1)
            else -> word
        }

    val STOP =
        """
        a about above after again against all also am an and any are as at be because been before
        being below between both but by can could did do does doing down during each few for from
        further had has have having he her here hers herself him himself his how i if in into is it
        its itself just me more most my myself no nor not now of off on once only or other our ours
        out over own same she should so some such than that the their theirs them then there these
        they this those through to too under until up very was we were what when where which while
        who whom why will with would you your yours amid says said say new news latest update updates
        live today day week year yr after over vs v via get gets got make makes made take takes set
        sets one two first last report reports reportedly know need here's what's how's watch video
        photos pics explained exclusive opinion analysis full top big check out why may might could
        amp quot nbsp per cent percent mr ms dr st
        """
            .trim()
            .split(Regex("\\s+"))
            .toSet()
}
