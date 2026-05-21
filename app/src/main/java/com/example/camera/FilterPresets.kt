package com.example.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

data class CameraFilter(
    val id: String,
    val name: String,
    val description: String,
    val colorMatrix: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CameraFilter
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

object FilterPresets {
    val NORMAL = CameraFilter(
        id = "normal",
        name = "Estándar",
        description = "Colores reales puros de fábrica",
        colorMatrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    val NOIR = CameraFilter(
        id = "noir",
        name = "Contrast Noir",
        description = "Blanco y negro con alto contraste dramático",
        colorMatrix = floatArrayOf(
            0.35f, 0.60f, 0.12f, 0.0f, -25f,
            0.35f, 0.60f, 0.12f, 0.0f, -25f,
            0.35f, 0.60f, 0.12f, 0.0f, -25f,
            0.00f, 0.00f, 0.00f, 1.0f, 0f
        )
    )

    val RETRO_VINTAGE = CameraFilter(
        id = "retro",
        name = "Warm Vintage",
        description = "Estilo clásico analógico, tonos cálidos y nostálgicos",
        colorMatrix = floatArrayOf(
            0.98f, 0.08f, 0.00f, 0.0f, 12f,
            0.00f, 0.92f, 0.10f, 0.0f, 8f,
            0.00f, 0.08f, 0.78f, 0.0f, -15f,
            0.00f, 0.00f, 0.00f, 1.0f, 0f
        )
    )

    val CYBERPUNK = CameraFilter(
        id = "cyberpunk",
        name = "Cyberpunk",
        description = "Sombras verdes azuladas con destellos magenta y azul eléctrico",
        colorMatrix = floatArrayOf(
            0.80f, 0.30f, 0.10f, 0.0f, 50f,
            0.05f, 0.85f, 0.35f, 0.0f, -25f,
            0.20f, 0.15f, 1.25f, 0.0f, 35f,
            0.00f, 0.00f, 0.00f, 1.0f, 0f
        )
    )

    val EMERALD_COOL = CameraFilter(
        id = "cyan_cool",
        name = "Cyan Cool",
        description = "Tonos fríos de película cinematográfica de ciencia ficción",
        colorMatrix = floatArrayOf(
            0.65f, 0.15f, 0.15f, 0.0f, -15f,
            0.05f, 1.05f, 0.15f, 0.0f, 20f,
            0.05f, 0.25f, 1.30f, 0.0f, 25f,
            0.00f, 0.00f, 0.00f, 1.0f, 0f
        )
    )

    val GOLDEN_HOUR = CameraFilter(
        id = "golden_hour",
        name = "Golden Hour",
        description = "Infundido con el resplandor ámbar de la hora dorada",
        colorMatrix = floatArrayOf(
            1.20f, 0.00f, 0.00f, 0.0f, 20f,
            0.00f, 1.08f, 0.00f, 0.0f, 8f,
            0.00f, 0.00f, 0.82f, 0.0f, -20f,
            0.00f, 0.00f, 0.00f, 1.0f, 0f
        )
    )

    val ALL_FILTERS = listOf(NORMAL, NOIR, RETRO_VINTAGE, CYBERPUNK, EMERALD_COOL, GOLDEN_HOUR)

    /**
     * Applies the chosen filter matrix to a Bitmap dynamically on a background thread.
     */
    fun applyFilterToBitmap(source: Bitmap, filter: CameraFilter): Bitmap {
        if (filter.id == NORMAL.id) return source
        
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(filter.colorMatrix))
        }
        
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Dynamically adjusts the contrast of a Bitmap using a ColorMatrix.
     */
    fun adjustContrast(source: Bitmap, contrast: Float): Bitmap {
        if (source.isRecycled) return source
        if (contrast == 1.0f) return source
        
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(output)
        val scale = contrast
        val translate = 128f * (1f - scale)
        val matrix = floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(matrix))
        }
        
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Converts a bitmap into a high-contrast black & white scanned page style.
     */
    fun convertToDocumentScan(source: Bitmap): Bitmap {
        if (source.isRecycled) return source
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            // Grayscale ColorMatrix
            val colorMatrix = android.graphics.ColorMatrix().apply {
                setSaturation(0f)
            }
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(source, 0f, 0f, paint)
        
        // Boost contrast significantly to clear background shadow noise and pop dark document text
        val finalResult = adjustContrast(output, 1.8f)
        if (finalResult != output) {
            output.recycle()
        }
        return finalResult
    }

    /**
     * Implements a simulated Bokeh Depth Of Field using stack blur and radial vignette blend.
     */
    fun applyPortraitBokeh(source: Bitmap): Bitmap {
        if (source.isRecycled) return source
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // Draw primary clear content
        canvas.drawBitmap(source, 0f, 0f, null)
        
        // Draw blurred content over with central clear vignetting
        val blurred = createBlurredBitmap(source, 10)
        
        val maskPaint = Paint().apply {
            isAntiAlias = true
            shader = android.graphics.RadialGradient(
                width / 2f,
                height / 2.2f, // Anchor near standard human face position
                Math.min(width, height) * 0.35f, // Smooth transition bokeh radius
                intArrayOf(0x00000000, 0xFFFFFFFF.toInt()), // sharp center, fully blurred background
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        
        canvas.drawBitmap(blurred, 0f, 0f, maskPaint)
        blurred.recycle()
        
        return output
    }

    /**
     * Crops image into an wider-aspect Panoramic cinematic landscape dimension with popping colors.
     */
    fun applyPanoramaCrop(source: Bitmap): Bitmap {
        if (source.isRecycled) return source
        val width = source.width
        val height = source.height
        val targetAspect = 21.0f / 9.0f // ultra widescreen panoramic format
        
        var cropWidth = width
        var cropHeight = (width / targetAspect).toInt()
        if (cropHeight > height) {
            cropHeight = height
            cropWidth = (height * targetAspect).toInt()
        }
        
        val startX = (width - cropWidth) / 2
        val startY = (height - cropHeight) / 2
        
        val cropped = Bitmap.createBitmap(source, startX, startY, cropWidth, cropHeight)
        
        val popContrast = adjustContrast(cropped, 1.15f)
        if (popContrast != cropped) {
            cropped.recycle()
        }
        return popContrast
    }

    /**
     * Pure Kotlin execution of fast stack blur for background bokeh depth of field.
     */
    private fun createBlurredBitmap(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(Math.max(w, h))
        val dv = IntArray(256 * div)
        for (idx in 0 until 256 * div) {
            dv[idx] = idx / div
        }
        yw = 0
        yi = 0
        y = 0
        while (y < h) {
            rsum = 0
            gsum = 0
            bsum = 0
            for (offset in -radius..radius) {
                p = pix[yi + Math.min(wm, Math.max(offset, 0))]
                rsum += p shr 16 and 0xff
                gsum += p shr 8 and 0xff
                bsum += p and 0xff
            }
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                val p1 = pix[yw + vmin[x]]
                val p2 = pix[yw + Math.max(x - radius, 0)]
                rsum += (p1 shr 16 and 0xff) - (p2 shr 16 and 0xff)
                gsum += (p1 shr 8 and 0xff) - (p2 shr 8 and 0xff)
                bsum += (p1 and 0xff) - (p2 and 0xff)
                yi++
                x++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (offset in -radius..radius) {
                yi = Math.max(0, yp) + x
                rsum += r[yi]
                gsum += g[yi]
                bsum += b[yi]
                yp += w
            }
            yi = x
            y = 0
            while (y < h) {
                pix[yi] = (-0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum])
                if (x == 0) {
                    vmin[y] = Math.min(y + radius + 1, hm) * w
                }
                val p1 = x + vmin[y]
                val p2 = x + Math.max(y - radius, 0) * w
                rsum += r[p1] - r[p2]
                gsum += g[p1] - g[p2]
                bsum += b[p1] - b[p2]
                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}
