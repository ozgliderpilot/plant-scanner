package com.nursery.scanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nursery.scanner.ci.CiMode
import com.nursery.scanner.setup.MagicLinkApplicator
import com.nursery.scanner.ui.NurseryRoot
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NurseryApplication).container
        // CI mode (#72): seed + flags before first frame when the launch extra is present.
        // runBlocking keeps Maestro from racing an empty plant list on cold start.
        runBlocking { CiMode.onColdStart(container, intent) }
        if (!CiMode.active) {
            container.autoExportTicker.start()
        }
        container.offerMagicLink(MagicLinkApplicator.uriFromIntent(intent))
        setContent { NurseryRoot(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val container = (application as NurseryApplication).container
        container.offerMagicLink(MagicLinkApplicator.uriFromIntent(intent))
    }
}
