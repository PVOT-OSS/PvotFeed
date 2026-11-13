// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent

object FeedHttp {
    fun userAgent(version: String) = "PvotFeed/$version (+https://github.com/PVOT-OSS/PvotFeed)"

    fun client(userAgent: String): HttpClient =
        HttpClient(Android) {
            expectSuccess = false
            followRedirects = true
            install(UserAgent) { agent = userAgent }
            install(HttpTimeout) {
                requestTimeoutMillis = 25_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
}
