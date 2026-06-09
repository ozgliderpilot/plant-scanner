package com.nursery.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nursery.scanner.ui.NurseryRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NurseryApplication).container
        setContent { NurseryRoot(container) }
    }
}
