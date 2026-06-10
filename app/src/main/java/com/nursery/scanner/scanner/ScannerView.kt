package com.nursery.scanner.scanner

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * A steady CameraX viewfinder (no flashing/flicker — accessibility rule) that decodes Code 128 and
 * reports the first value via [onBarcode]. The host screen navigates onward on the first hit.
 *
 * [scanning] re-arms the one-shot analyzer: the preview keeps running, but a decoded value is only
 * emitted while it is true. Flip it back to true (e.g. after a not-found "Retry") to scan again.
 */
@Composable
fun ScannerView(
    modifier: Modifier = Modifier,
    scanning: Boolean = true,
    onBarcode: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode = rememberUpdatedState(onBarcode)
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { BarcodeAnalyzer { code -> currentOnBarcode.value(code) } }

    // The analyzer is created once (in the factory below) and outlives recompositions, so re-arming
    // here is what lets the camera detect again after a result was shown.
    LaunchedEffect(scanning) {
        if (scanning) analyzer.arm() else analyzer.disarm()
    }

    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analyzerExecutor, analyzer)
                    }
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
