package com.example.camera

data class CameraOption(
    val id: String,
    val facing: Int, // CameraMetadata.LENS_FACING_FRONT, etc.
    val name: String,
    val megapixels: Int,
    val maxResolution: String,
    val sensorSize: String?,
    val focalLengths: FloatArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CameraOption
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
