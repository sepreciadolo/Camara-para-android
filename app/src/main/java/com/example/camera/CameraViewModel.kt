package com.example.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.TextureView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class CameraViewModel : ViewModel() {

    private val tag = "CameraViewModel"

    private var cameraService: CameraService? = null

    // UI State structures
    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted = _isPermissionGranted.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<CameraOption>>(emptyList())
    val discoveredCameras = _discoveredCameras.asStateFlow()

    private val _selectedCamera = MutableStateFlow<CameraOption?>(null)
    val selectedCamera = _selectedCamera.asStateFlow()

    private val _currentFilter = MutableStateFlow(FilterPresets.NORMAL)
    val currentFilter = _currentFilter.asStateFlow()

    private val _flashMode = MutableStateFlow("off") // off, on, auto, torch
    val flashMode = _flashMode.asStateFlow()

    private val _zoomFactor = MutableStateFlow(1.0f)
    val zoomFactor = _zoomFactor.asStateFlow()

    private val _shootMode = MutableStateFlow(ShootMode.AUTO) // AUTO, BURST, TIMER
    val shootMode = _shootMode.asStateFlow()

    // Timer Mode countdown state
    private val _timerDuration = MutableStateFlow(0) // 0 (off), 3, 5, 10 seconds
    val timerDuration = _timerDuration.asStateFlow()

    private val _countdownTicks = MutableStateFlow(-1) // active ticks shown during countdown
    val countdownTicks = _countdownTicks.asStateFlow()

    // Burst mode states
    private val _isCapturingBurst = MutableStateFlow(false)
    val isCapturingBurst = _isCapturingBurst.asStateFlow()

    private val _burstIndex = MutableStateFlow(0)
    val burstIndex = _burstIndex.asStateFlow()

    // Video recording states
    private val _isRecordingVideo = MutableStateFlow(false)
    val isRecordingVideo = _isRecordingVideo.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds = _recordingDurationSeconds.asStateFlow()

    // Manual Pro Control drawer toggle
    private val _isProControlsOpen = MutableStateFlow(false)
    val isProControlsOpen = _isProControlsOpen.asStateFlow()

    // Pro Parameters values
    private val _manualIso = MutableStateFlow<Int?>(null) // null = Auto
    val manualIso = _manualIso.asStateFlow()

    private val _manualExposureTimeNs = MutableStateFlow<Long?>(null) // null = Auto
    val manualExposureTimeNs = _manualExposureTimeNs.asStateFlow()

    private val _manualFocusDistance = MutableStateFlow<Float?>(null) // null = Auto focus
    val manualFocusDistance = _manualFocusDistance.asStateFlow()

    // Captured outputs preview
    private val _lastSavedPhotoUri = MutableStateFlow<Uri?>(null)
    val lastSavedPhotoUri = _lastSavedPhotoUri.asStateFlow()

    private val _statusBarMessage = MutableStateFlow<String?>(null)
    val statusBarMessage = _statusBarMessage.asStateFlow()

    private var recordingJob: kotlinx.coroutines.Job? = null
    private var statusClearJob: kotlinx.coroutines.Job? = null

    // New states for the requested premium functions
    private val _videoQuality = MutableStateFlow("1080p")
    val videoQuality = _videoQuality.asStateFlow()

    private val _showGrid = MutableStateFlow(true)
    val showGrid = _showGrid.asStateFlow()

    private val _manualContrast = MutableStateFlow(1.0f)
    val manualContrast = _manualContrast.asStateFlow()

    private fun showTempStatusMessage(message: String, delayMs: Long = 2500) {
        statusClearJob?.cancel()
        _statusBarMessage.value = message
        statusClearJob = viewModelScope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (_statusBarMessage.value == message) {
                _statusBarMessage.value = null
            }
        }
    }

    enum class ShootMode {
        AUTO, BURST, TIMER, VIDEO, PORTRAIT, DOCUMENTS, TIMELAPSE, ULTRA_HD, NIGHT, DUAL_VIDEO, PANORAMA
    }

    /**
     * Set up permissions state.
     */
    fun setPermissionsGranted(granted: Boolean) {
        _isPermissionGranted.value = granted
    }

    /**
     * Closes the active camera stream safely.
     */
    fun closeCamera() {
        cameraService?.closeCamera()
    }

    /**
     * Initializes CameraService on first bound view.
     */
    fun initService(context: Context) {
        if (cameraService == null) {
            cameraService = CameraService(context)
            loadDiscoveredCameras()
            queryLastMediaStorePhoto(context)
        }
    }

    private fun loadDiscoveredCameras() {
        val service = cameraService ?: return
        viewModelScope.launch {
            val list = service.discoverCameras()
            _discoveredCameras.value = list
            if (list.isNotEmpty() && _selectedCamera.value == null) {
                _selectedCamera.value = list.firstOrNull { it.id == "0" } ?: list.first()
            }
        }
    }

    /**
     * Binds camera texture stream directly using CameraService.
     */
    fun bindCameraStream(textureView: TextureView, onError: (String) -> Unit) {
        val service = cameraService ?: return
        val currentId = _selectedCamera.value?.id ?: "0"
        
        service.openCamera(currentId, textureView,
            onOpened = {
                // Read restored capabilities
                _zoomFactor.value = service.currentZoomFactor
                _statusBarMessage.value = "Cámara Pro vinculada con éxito"
                viewModelScope.launch {
                    delay(3000)
                    if (_statusBarMessage.value == "Cámara Pro vinculada con éxito") {
                        _statusBarMessage.value = null
                    }
                }
            },
            onError = { err ->
                _statusBarMessage.value = "Falla de cámara: $err"
                onError(err)
            }
        )
    }

    /**
     * Sets active sensor ID and re-routes streams.
     */
    fun selectCamera(option: CameraOption, textureView: TextureView, onError: (String) -> Unit) {
        _selectedCamera.value = option
        bindCameraStream(textureView, onError)
    }

    /**
     * Adjusts active camera zoom factor.
     */
    fun setZoom(factor: Float) {
        val service = cameraService ?: return
        val clampedFactor = factor.coerceIn(1.0f, service.maxZoomFactor)
        _zoomFactor.value = clampedFactor
        service.setZoom(clampedFactor)
    }

    /**
     * Adjusts Flash Mode.
     */
    fun toggleFlashMode() {
        val modes = listOf("off", "on", "auto", "torch")
        val nextIndex = (modes.indexOf(_flashMode.value) + 1) % modes.size
        val nextMode = modes[nextIndex]
        _flashMode.value = nextMode
        cameraService?.setFlashMode(nextMode)
        showTempStatusMessage("Flash: ${nextMode.uppercase()}")
    }

    /**
     * Standard color filters switcher.
     */
    fun setFilter(filter: CameraFilter) {
        _currentFilter.value = filter
        showTempStatusMessage("Filtro: ${filter.name}")
    }

    /**
     * Switching shoot mode.
     */
    fun setShootMode(mode: ShootMode) {
        _shootMode.value = mode
        cameraService?.let { service ->
            service.isTimelapseMode = (mode == ShootMode.TIMELAPSE)
        }
        showTempStatusMessage("Modo: ${mode.name}")
    }

    fun setVideoQuality(quality: String) {
        _videoQuality.value = quality
        cameraService?.let { service ->
            service.activeVideoQuality = quality
        }
        showTempStatusMessage("Calidad de Vídeo: $quality")
    }

    fun toggleGrid() {
        _showGrid.value = !_showGrid.value
        val msg = if (_showGrid.value) "Rejilla de captura: Activada" else "Rejilla de captura: Desactivada"
        showTempStatusMessage(msg)
    }

    fun setManualContrast(contrast: Float) {
        _manualContrast.value = contrast.coerceIn(0.4f, 2.2f)
        cameraService?.let { service ->
            service.contrastFactor = contrast.coerceIn(0.4f, 2.2f)
        }
    }

    fun setTempStatusMessage(message: String) {
        showTempStatusMessage(message)
    }

    fun triggerAutoFocus(x: Float, y: Float) {
        cameraService?.triggerAutoFocus(x, y)
    }

    fun setTimerDuration(duration: Int) {
        _timerDuration.value = duration
        val msg = if (duration == 0) "Temporizador Desactivado" else "Temporizador: ${duration}s"
        showTempStatusMessage(msg)
    }

    fun toggleProControls() {
        _isProControlsOpen.value = !_isProControlsOpen.value
        val msg = if (_isProControlsOpen.value) "Knobs Manuales PRO Abiertos" else "Controles PRO Ocultos"
        showTempStatusMessage(msg)
    }

    /**
     * Adjust Pro Manual Settings values.
     */
    fun setManualExposure(iso: Int?, exposureNs: Long?) {
        _manualIso.value = iso
        _manualExposureTimeNs.value = exposureNs
        cameraService?.setManualControls(iso, exposureNs, _manualFocusDistance.value)
        val msg = if (iso != null) "Manual: ISO $iso | Exp ${formatExposureLabel(exposureNs)}" else "Exposición: Automática"
        showTempStatusMessage(msg)
    }

    /**
     * Adjust Manual focus distance.
     */
    fun setManualFocus(distance: Float?) {
        _manualFocusDistance.value = distance
        cameraService?.setManualControls(_manualIso.value, _manualExposureTimeNs.value, distance)
        val msg = if (distance != null) {
            val label = if (distance == 0f) "Infinito (∞)" else String.format(Locale.getDefault(), "%.1f dpf", distance)
            "Enfoque Manual: $label"
        } else {
            "Enfoque: Automático Continuo"
        }
        showTempStatusMessage(msg)
    }

    /**
     * Triggers photo shooting sequences based on selected modes.
     */
    fun triggerPhotoCapture(context: Context, onError: (String) -> Unit) {
        when (_shootMode.value) {
            ShootMode.AUTO -> executeSingleCapture(context, onError)
            ShootMode.BURST -> executeBurstCapture(context, onError)
            ShootMode.TIMER -> executeTimedCapture(context, onError)
            ShootMode.VIDEO -> triggerVideoToggle(context, onError)
            ShootMode.PORTRAIT -> {
                showTempStatusMessage("Foco Bokeh Retrato Optimizado")
                executeSingleCapture(context, onError)
            }
            ShootMode.DOCUMENTS -> {
                showTempStatusMessage("Digitalizando Documento...")
                executeSingleCapture(context, onError)
            }
            ShootMode.TIMELAPSE -> triggerVideoToggle(context, onError)
            ShootMode.ULTRA_HD -> {
                showTempStatusMessage("Optimizando Texturas Detalladas 108MP HD...")
                executeSingleCapture(context, onError)
            }
            ShootMode.NIGHT -> {
                viewModelScope.launch {
                    showTempStatusMessage("Captura Nocturna: Estabilizando exposición (1.5s)...")
                    delay(1000)
                    executeSingleCapture(context, onError)
                }
            }
            ShootMode.DUAL_VIDEO -> triggerVideoToggle(context, onError)
            ShootMode.PANORAMA -> {
                viewModelScope.launch {
                    showTempStatusMessage("Procesando Panorámica Multi-Ángulo...")
                    delay(800)
                    executeSingleCapture(context, onError)
                }
            }
        }
    }

    private fun executeSingleCapture(context: Context, onError: (String) -> Unit) {
        val service = cameraService ?: return
        service.capturePhoto { bytes ->
            processAndSavePhotoBytes(context, bytes)
        }
    }

    private fun executeBurstCapture(context: Context, onError: (String) -> Unit) {
        val service = cameraService ?: return
        if (_isCapturingBurst.value) return
        
        viewModelScope.launch {
            _isCapturingBurst.value = true
            _burstIndex.value = 0
            
            val totalBurstCount = 5
            for (i in 1..totalBurstCount) {
                _burstIndex.value = i
                _statusBarMessage.value = "Ráfaga: Captura $i de $totalBurstCount"
                service.capturePhoto { bytes ->
                    processAndSavePhotoBytes(context, bytes)
                }
                delay(300) // Fast burst cadence
            }
            
            _statusBarMessage.value = "Ráfaga completada con éxito ($totalBurstCount fotos)"
            _isCapturingBurst.value = false
        }
    }

    private fun executeTimedCapture(context: Context, onError: (String) -> Unit) {
        val duration = _timerDuration.value
        if (duration <= 0) {
            executeSingleCapture(context, onError)
            return
        }

        viewModelScope.launch {
            _countdownTicks.value = duration
            while (_countdownTicks.value > 0) {
                _statusBarMessage.value = "Capturando en ${_countdownTicks.value}s..."
                delay(1000)
                _countdownTicks.value -= 1
            }
            _countdownTicks.value = -1
            executeSingleCapture(context, onError)
        }
    }

    /**
     * Handles Video Recording Action toggles.
     */
    private fun triggerVideoToggle(context: Context, onError: (String) -> Unit) {
        val service = cameraService ?: return
        if (_isRecordingVideo.value) {
            // Stop Video Recording
            recordingJob?.cancel()
            _isRecordingVideo.value = false
            _statusBarMessage.value = "Almacenando vídeo en galería..."
            service.stopVideoRecording(
                onStopped = { videoFile ->
                    saveVideoToMediaStore(context, videoFile)
                    _recordingDurationSeconds.value = 0
                },
                onError = { err ->
                    _statusBarMessage.value = "Falla al guardar vídeo: $err"
                }
            )
        } else {
            // Start recording
            val outputVideoFile = createTempVideoFile(context)
            service.startVideoRecording(outputVideoFile,
                onStarted = {
                    _isRecordingVideo.value = true
                    _statusBarMessage.value = "Grabando vídeo..."
                    _recordingDurationSeconds.value = 0
                    
                    // Increment video recording clock
                    recordingJob = viewModelScope.launch {
                        while (true) {
                            delay(1000)
                            _recordingDurationSeconds.value += 1
                        }
                    }
                },
                onError = { err ->
                    _statusBarMessage.value = "Falla de vídeo: $err"
                    onError(err)
                }
            )
        }
    }

    /**
     * Process high-res captured JPG, apply color matrix filters asynchronously, and persist to MediaStore.
     */
    private fun processAndSavePhotoBytes(context: Context, bytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                // Handle front camera mirroring adjustments if required
                val isFront = _selectedCamera.value?.facing == CameraCharacteristics.LENS_FACING_FRONT
                if (isFront) {
                    val matrix = Matrix().apply {
                        postScale(-1f, 1f) // Mirror horizontal
                    }
                    val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    bitmap.recycle()
                    bitmap = flipped
                }

                // Apply real-time creative filters to saved photo
                val filter = _currentFilter.value
                val filteredBitmap = FilterPresets.applyFilterToBitmap(bitmap, filter)
                if (filteredBitmap != bitmap) {
                    bitmap.recycle()
                }

                // Apply custom contrast adjustments
                val contrastVal = _manualContrast.value
                var processedBitmap = FilterPresets.adjustContrast(filteredBitmap, contrastVal)
                if (processedBitmap != filteredBitmap) {
                    filteredBitmap.recycle()
                }

                // Apply specialty mode-based post processing
                if (_shootMode.value == ShootMode.DOCUMENTS) {
                    val docBoostColor = FilterPresets.adjustContrast(processedBitmap, 1.4f)
                    if (docBoostColor != processedBitmap) {
                        processedBitmap.recycle()
                    }
                    processedBitmap = docBoostColor
                }

                // Write into MediaStore
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val imageFileName = "CAM_PRO_$timeStamp"
                val resolver = context.contentResolver
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$imageFileName.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CamaraPro")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    val outStream: OutputStream? = resolver.openOutputStream(imageUri)
                    if (outStream != null) {
                        processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
                        outStream.close()
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)
                    }

                    _lastSavedPhotoUri.value = imageUri
                    withContext(Dispatchers.Main) {
                        showTempStatusMessage("Foto guardada en Galería")
                    }
                }

                processedBitmap.recycle()

            } catch (e: Exception) {
                Log.e(tag, "Falla procesando y guardando captura", e)
            }
        }
    }

    /**
     * Persists recorded video file into MediaStore movies space.
     */
    private fun saveVideoToMediaStore(context: Context, videoFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val videoFileName = "VID_PRO_$timeStamp.mp4"
                
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CamaraPro")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (videoUri != null) {
                    resolver.openOutputStream(videoUri)?.use { outStream ->
                        val inStream = FileOutputStream(videoFile)
                        // Fast transfer file content
                        videoFile.inputStream().copyTo(outStream)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(videoUri, contentValues, null, null)
                    }

                    _lastSavedPhotoUri.value = videoUri
                    withContext(Dispatchers.Main) {
                        showTempStatusMessage("Vídeo guardado en Galería")
                    }
                }
                
                // Cleanup temp video file
                videoFile.delete()

            } catch (e: Exception) {
                Log.e(tag, "Error saving video file to media store", e)
            }
        }
    }

    private fun createTempVideoFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(context.cacheDir, "VID_TEMP_$timeStamp.mp4")
    }

    /**
     * Query the last photo in MediaStore for the preview circle thumbnail icon.
     */
    private fun queryLastMediaStorePhoto(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val id = it.getLong(idColumn)
                        val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        _lastSavedPhotoUri.value = contentUri
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Error querying last media asset", e)
            }
        }
    }

    /**
     * Formats nano shutter speeds into user readable labels.
     */
    private fun formatExposureLabel(ns: Long?): String {
        if (ns == null) return "Auto"
        val seconds = ns / 1_000_000_000.0
        return if (seconds >= 1.0) {
            String.format(Locale.getDefault(), "%.1fs", seconds)
        } else {
            val inverted = (1.0 / seconds).roundToInt()
            "1/${inverted}s"
        }
    }

    override fun onCleared() {
        cameraService?.destroy()
        super.onCleared()
    }
}
