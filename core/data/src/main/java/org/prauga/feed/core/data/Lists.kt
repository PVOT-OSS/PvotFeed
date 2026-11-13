// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import org.prauga.feed.core.feeds.ParsedFeed

/** "linux-arm-msm@vger.kernel.org" → "linux-arm-msm". */
internal fun listName(feed: ParsedFeed): String =
    feed.mailingList?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: feed.title.removeSuffix(" archive mirror")
