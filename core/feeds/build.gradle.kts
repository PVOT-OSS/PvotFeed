// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

plugins { alias(libs.plugins.feed.android.library) }

android { namespace = "org.prauga.feed.core.feeds" }

dependencies {
    api(project(":core:model"))
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kxml2)
}
