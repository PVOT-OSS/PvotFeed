// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

plugins { alias(libs.plugins.feed.android.library) }

android { namespace = "org.prauga.feed.core.network" }

dependencies {
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
