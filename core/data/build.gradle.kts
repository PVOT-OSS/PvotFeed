// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.feed.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android { namespace = "org.prauga.feed.core.data" }

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    api(project(":core:model"))
    api(project(":core:reader"))
    api(project(":core:network"))
    implementation(project(":core:feeds"))
    implementation(project(":core:stories"))
    api(libs.room.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.room.compiler)
    testImplementation(libs.kotlinx.coroutines.test)
}
