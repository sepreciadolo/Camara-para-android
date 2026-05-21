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
}
