package com.nursery.scanner.scanner

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * ML Kit analyzer restricted to Code 128 (the nursery label symbology). Restricting formats speeds
 * detection and avoids false reads from stray 2D codes. Emits one decoded value per scan session:
 * after a hit it [disarm]s itself (so one scan == one line item) and stays quiet until the host
 * [arm]s it again — e.g. when the user taps "Retry" on a not-found code.
 */
class BarcodeAnalyzer(private val onBarcode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_CODE_128)
            .build(),
    )

    // Gates emission: true while waiting for a scan, flipped false on a hit (and while a result is
    // shown). Re-armed by the host to scan again, which is what makes "Retry" work.
    @Volatile
    private var armed = true

    fun arm() { armed = true }

    fun disarm() { armed = false }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !armed) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val code = barcodes.firstOrNull()?.rawValue
                if (!code.isNullOrBlank() && armed) {
                    armed = false
                    onBarcode(code)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
