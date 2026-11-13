// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import com.prauga.pvot.designsystem.platform.PvotBaseActivity
import com.prauga.pvot.designsystem.theme.PvotAppTheme

class MainActivity : PvotBaseActivity() {
    private val sharedLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FeedApplication).container
        if (savedInstanceState == null) sharedLink.value = sharedText(intent)
        setPvotContent {
            PvotAppTheme {
                FeedNavigation(
                    container = container,
                    sharedLink = sharedLink.value,
                    onSharedLinkHandled = { sharedLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedText(intent)?.let { sharedLink.value = it }
    }

    private fun sharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
}
