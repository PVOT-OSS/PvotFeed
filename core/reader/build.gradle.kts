// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.feed.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "org.prauga.feed.core.reader" }

dependencies {
    implementation(project(":core:feeds"))
    implementation(libs.jsoup)
    compileOnly(libs.jspecify)
    implementation(libs.kotlinx.serialization.json)
}
