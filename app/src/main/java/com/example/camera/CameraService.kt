package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class CameraService(private val context: Context) {

    private val tag = "CameraService-Pro"

    // Camera services
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewRequest: CaptureRequest? = null

    // Background Threads for Camera callbacks
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Sized surfaces
    private var previewSize: Size = Size(1920, 1080)
    private var imageReader: ImageReader? = null
    private var activeTextureView: TextureView? = null

    // Video Recording assets
    private var mediaRecorder: MediaRecorder? = null
    private var isRecordingVideo = false
    private var videoFile: File? = null

    // Camera hardware constraints & stats
    var currentCameraId: String = "0"
        private set
    var maxZoomFactor: Float = 1.0f
        private set
    var currentZoomFactor: Float = 1.0f
        private set
    var minFocusDistance: Float = 0f // 0 means autofocus only, higher numbers mean focus limit
        private set

    // Active manual settings status
    private var activeIso: Int? = null
    private var activeExposureTimeNs: Long? = null
    private var activeFocusDistance: Float? = null
    private var activeFlashMode: String = "off" // off, on, auto, torch

    // Adaptive Premium additions
    var activeVideoQuality: String = "1080p" // 720p, 1080p, 4K
    var isTimelapseMode: Boolean = false
    var contrastFactor: Float = 1.0f

    // Lock for threading
    private val cameraOpenCloseLock = Semaphore(1)

    // Callbacks for capture notifications
    private var onPhotoTakenCallback: ((ByteArray) -> Unit)? = null

    init {
        startBackgroundThread()
    }

    /**
     * Iterates over physical and logical Camera IDs, returning a highly informative list of options.
     */
    fun discoverCameras(): List<CameraOption> {
        val options = mutableListOf<CameraOption>()
        try {
            val idList = cameraManager.cameraIdList
            for (id in idList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
                
                // Fetch Megapixels
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG)
                val maxPixels = jpegSizes?.maxByOrNull { it.width * it.height }?.let { it.width.toLong() * it.height } ?: 0L
                val megapixels = (maxPixels / 1000000.0).roundToInt()
                
                val maxRes = jpegSizes?.maxByOrNull { it.width * it.height }?.let { "${it.width}x${it.height}" } ?: "Desconocida"
                
                // Sensor size description
                val physicalSizeStr = when (facing) {
                    CameraCharacteristics.LENS_FACING_BACK -> {
                        if (id == "0") "Principal 50 MP (Sony IMX882 f/1.79)"
                        else if (megapixels in 7..9) "Ultra Gran Angular 8 MP"
                        else "Cámara Auxiliar ($megapixels MP)"
                    }
                    CameraCharacteristics.LENS_FACING_FRONT -> "Cámara Frontal 20 MP"
                    else -> "Sensor Externo ($megapixels MP)"
                }
                
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                
                options.add(
                    CameraOption(
                        id = id,
                        facing = facing,
                        name = physicalSizeStr,
                        megapixels = megapixels,
                        maxResolution = maxRes,
                        sensorSize = if (facing == CameraCharacteristics.LENS_FACING_BACK) "IMX882 / Wide" else "Front",
                        focalLengths = focalLengths
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Falla al descubrir cámaras", e)
        }
        return options
    }

    /**
     * Starts the camera background handler thread to offload heavy processing from UI thread.
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackgroundPro").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    /**
     * Safely stops the camera background handler thread.
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(tag, "Error stopping background thread", e)
        }
    }

    /**
     * Opens a camera connection asynchronously and binds it to a TextureView.
     */
    fun openCamera(cameraId: String, textureView: TextureView, onOpened: () -> Unit, onError: (String) -> Unit) {
        if (backgroundHandler == null) startBackgroundThread()
        
        backgroundHandler?.post {
            try {
                if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    android.os.Handler(Looper.getMainLooper()).post { onError("Cámara ocupada por otro proceso") }
                    return@post
                }

                // Close current camera if open
                closeCurrentCameraInternal()

                currentCameraId = cameraId
                activeTextureView = textureView

                val chars = cameraManager.getCameraCharacteristics(cameraId)
                
                // Fetch max digital zoom capability
                maxZoomFactor = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
                currentZoomFactor = 1.0f

                // Fetch physical lens properties
                minFocusDistance = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

                // Select optimal sizes
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: throw RuntimeException("No configuration map found")
                previewSize = selectOptimalPreviewSize(map, textureView)

                // Configure ImageReader for high-res photo capture with up to 3 buffers to allow zero shutter lag
                val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)
                val captureSize = jpegSizes?.maxByOrNull { it.width * it.height } ?: Size(1920, 1080)
                imageReader = ImageReader.newInstance(captureSize.width, captureSize.height, ImageFormat.JPEG, 3).apply {
                    setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        image.close()
                        
                        onPhotoTakenCallback?.let { callback ->
                            triggerVibration(50L) // Tactile feedback on capture
                            callback(bytes)
                        }
                    }, backgroundHandler)
                }

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraOpenCloseLock.release()
                        cameraDevice = camera
                        startPreviewSession(onOpened, onError)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        cameraOpenCloseLock.release()
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, errorType: Int) {
                        cameraOpenCloseLock.release()
                        camera.close()
                        cameraDevice = null
                        val errText = when (errorType) {
                            ERROR_CAMERA_IN_USE -> "La cámara ya está siendo usada"
                            ERROR_MAX_CAMERAS_IN_USE -> "Límite de cámaras concurrentes alcanzado"
                            ERROR_CAMERA_DISABLED -> "Cámara deshabilitada por políticas del sistema"
                            ERROR_CAMERA_DEVICE -> "Falla crítica en el dispositivo de cámara"
                            ERROR_CAMERA_SERVICE -> "Falla en el servicio del sistema de cámara"
                            else -> "Falla desconocida ($errorType)"
                        }
                        android.os.Handler(Looper.getMainLooper()).post { onError(errText) }
                    }
                }, backgroundHandler)

            } catch (e: Exception) {
                cameraOpenCloseLock.release()
                Log.e(tag, "Error abriendo la cámara $cameraId", e)
                android.os.Handler(Looper.getMainLooper()).post { onError("Imposible abrir cámara: ${e.localizedMessage}") }
            }
        }
    }

    /**
     * Sets up the CameraCaptureSession with optimized render pipelines.
     */
    private fun startPreviewSession(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val camera = cameraDevice ?: return
        val textureView = activeTextureView ?: return
        val readerRef = imageReader ?: return

        try {
            val texture = textureView.surfaceTexture ?: return
            
            // Set TextureView buffer size to match ideal target aspect ratio
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)

            val previewSurface = Surface(texture)
            val captureSurface = readerRef.surface

            val targets = mutableListOf(previewSurface, captureSurface)

            previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                
                // Configure 120Hz or maximum fluid refresh rates
                setOptimalFpsRange(this, cameraManager.getCameraCharacteristics(camera.id))

                // Configure standard Auto-focus and Auto-exposure parameters
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                
                // Apply OIS (Optical Image Stabilization) if available on the sensor!
                // Sony IMX882 on back has premium physical metal coil OIS.
                set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
            }

            // Clean previous capture sessions
            captureSession?.close()

            camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        applyActiveParameters() // Ensure flash, zoom, manual configs propagate immediately
                        previewRequest = previewRequestBuilder?.build()
                        previewRequest?.let {
                            session.setRepeatingRequest(it, null, backgroundHandler)
                        }
                        android.os.Handler(Looper.getMainLooper()).post { onSuccess() }
                    } catch (e: Exception) {
                        Log.e(tag, "Falla al repetir request de preview", e)
                        android.os.Handler(Looper.getMainLooper()).post { onError("Sesión configurada pero falló inicio lógico: ${e.localizedMessage}") }
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    android.os.Handler(Looper.getMainLooper()).post { onError("Configuración del flujo de preview fallida") }
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(tag, "Error creando sesión de preview", e)
            onError("Falla al iniciar la sesión: ${e.localizedMessage}")
        }
    }

    /**
     * Capture high-res photo with optimal parameters.
     */
    fun capturePhoto(onPhotoCaptured: (ByteArray) -> Unit) {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val readerRef = imageReader ?: return

        backgroundHandler?.post {
            try {
                onPhotoTakenCallback = onPhotoCaptured

                val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(readerRef.surface)
                    
                    // Maintain active zoom and manual settings in the saved photo request
                    val activeCrop = previewRequestBuilder?.get(CaptureRequest.SCALER_CROP_REGION)
                    if (activeCrop != null) {
                        set(CaptureRequest.SCALER_CROP_REGION, activeCrop)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val activeZoomRatio = previewRequestBuilder?.get(CaptureRequest.CONTROL_ZOOM_RATIO)
                        if (activeZoomRatio != null) {
                            set(CaptureRequest.CONTROL_ZOOM_RATIO, activeZoomRatio)
                        }
                    }

                    // Propagate manual exposures & sensitivity
                    if (activeIso != null && activeExposureTimeNs != null) {
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                        set(CaptureRequest.SENSOR_SENSITIVITY, activeIso)
                        set(CaptureRequest.SENSOR_EXPOSURE_TIME, activeExposureTimeNs)
                    } else {
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    }

                    // Propagate manual focus
                    if (activeFocusDistance != null) {
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                        set(CaptureRequest.LENS_FOCUS_DISTANCE, activeFocusDistance)
                    } else {
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }

                    // Propagate flash mode
                    set(CaptureRequest.FLASH_MODE, previewRequestBuilder?.get(CaptureRequest.FLASH_MODE) ?: CaptureRequest.FLASH_MODE_OFF)

                    // Enable high quality processes
                    set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
                    set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                    set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)

                    // Handle standard mirror flip for Front Camera selfie modes
                    val chars = cameraManager.getCameraCharacteristics(camera.id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                    
                    set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
                }

                // Zero Shutter Lag (ZSL) flow: Keep the preview repeating request active
                // to maintain continuous 120Hz/60Hz viewport stream without visual freezes (jank/stutter).
                session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        super.onCaptureCompleted(session, request, result)
                        // Trigger rapid focus confirmation vibration
                        triggerVibration(100L)
                    }
                }, backgroundHandler)

            } catch (e: Exception) {
                Log.e(tag, "Falla capturando fotografía de alta calidad", e)
            }
        }
    }

    /**
     * Resumes preview stream repeating queries.
     */
    private fun resumePreviewStream() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        try {
            previewRequest = builder.build()
            session.setRepeatingRequest(previewRequest!!, null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Falla al reanudar stream de previsualización", e)
        }
    }

    /**
     * Sets digital/optical zoom.
     */
    fun setZoom(zoomFactor: Float) {
        if (zoomFactor < 1.0f || zoomFactor > maxZoomFactor) return
        currentZoomFactor = zoomFactor
        
        previewRequestBuilder?.let { builder ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Modern Zoom Ratio API
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomFactor)
            } else {
                // Classic Crop Region Fallback
                try {
                    val chars = cameraManager.getCameraCharacteristics(currentCameraId)
                    val sensorRect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    if (sensorRect != null) {
                        val centerX = sensorRect.centerX()
                        val centerY = sensorRect.centerY()
                        val deltaX = (sensorRect.width() / (2.0f * zoomFactor)).toInt()
                        val deltaY = (sensorRect.height() / (2.0f * zoomFactor)).toInt()

                        val cropRect = Rect(
                            centerX - deltaX,
                            centerY - deltaY,
                            centerX + deltaX,
                            centerY + deltaY
                        )
                        builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error calculating zoom crop region", e)
                }
            }
            updateSessionRepeatingRequest()
        }
    }

    /**
     * Configures the Flash mode.
     */
    fun setFlashMode(flashMode: String) {
        activeFlashMode = flashMode
        applyFlashSettings()
    }

    private fun applyFlashSettings() {
        val builder = previewRequestBuilder ?: return
        try {
            when (activeFlashMode) {
                "off" -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                "on" -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                }
                "auto" -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                "torch" -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }
            updateSessionRepeatingRequest()
        } catch (e: Exception) {
            Log.e(tag, "Falla aplicando flash", e)
        }
    }

    /**
     * Triggers active autofocus scan around a touch target point.
     */
    fun triggerAutoFocus(x: Float, y: Float) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        backgroundHandler?.post {
            try {
                // Cancel any existing AF triggers
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
                session.capture(builder.build(), null, backgroundHandler)

                // Start AF trigger
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                session.capture(builder.build(), null, backgroundHandler)

                // Vibration feedback for tactile focus affirmation
                triggerVibration(50L)

                // Reset to idle trigger state
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                updateSessionRepeatingRequest()
            } catch (e: Exception) {
                Log.e(tag, "Failed to run tap to autofocus trigger", e)
            }
        }
    }

    /**
     * Updates ISO, Exposure, and Focus (Manual Controls).
     */
    fun setManualControls(iso: Int?, exposureTimeNs: Long?, focusDistance: Float?) {
        activeIso = iso
        activeExposureTimeNs = exposureTimeNs
        activeFocusDistance = focusDistance
        applyManualControls()
    }

    private fun applyManualControls() {
        val builder = previewRequestBuilder ?: return
        try {
            // Apply Manual ISO and Shutter Speed Exposure
            if (activeIso != null && activeExposureTimeNs != null) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, activeIso)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, activeExposureTimeNs)
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                // Resume flash logic since auto exposure took back control
                applyFlashSettings()
            }

            // Apply Manual Focus Distance
            if (activeFocusDistance != null && minFocusDistance > 0) {
                // Focus: 0.0f is infinity, up to minFocusDistance (usually around 10.0f for macro objects)
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, activeFocusDistance)
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }

            updateSessionRepeatingRequest()
        } catch (e: Exception) {
            Log.e(tag, "Falla al aplicar controles manuales", e)
        }
    }

    /**
     * Mass apply parameters upon open preview.
     */
    private fun applyActiveParameters() {
        setZoom(currentZoomFactor)
        applyFlashSettings()
        applyManualControls()
    }

    private fun updateSessionRepeatingRequest() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        try {
            previewRequest = builder.build()
            session.setRepeatingRequest(previewRequest!!, null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(tag, "Error actualizandoRepeatingRequest", e)
        }
    }

    /**
     * Start Video Recording using a secure pipeline.
     */
    fun startVideoRecording(outputFile: File, onStarted: () -> Unit, onError: (String) -> Unit) {
        if (isRecordingVideo) return
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val textureView = activeTextureView ?: return

        backgroundHandler?.post {
            try {
                videoFile = outputFile
                
                // Initialize MediaRecorder (Modern API compatible)
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                val texture = textureView.surfaceTexture ?: throw IllegalStateException("TextureView is empty")
                texture.setDefaultBufferSize(previewSize.width, previewSize.height)
                val previewSurface = Surface(texture)

                // 2. Set source files
                mediaRecorder?.apply {
                    // SECURE MIC fallback! In emulators or sandboxed background tasks, mic might be blocked.
                    var hasAudio = !isTimelapseMode
                    if (hasAudio) {
                        try {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                        } catch (e: Exception) {
                            Log.w(tag, "Falla al registrar micrófono, grabando SÓLO de vídeo", e)
                            hasAudio = false
                        }
                    }

                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(outputFile.absolutePath)

                    // Video quality configuration based on dynamic toggle and timelapse
                    val size = when (activeVideoQuality) {
                        "720p" -> Pair(1280, 720)
                        "4K" -> Pair(3840, 2160)
                        else -> Pair(1920, 1080) // default 1080p
                    }

                    val bitrateValue = when (activeVideoQuality) {
                        "720p" -> 10000000
                        "4K" -> 55000000
                        else -> 30000000
                    }

                    val frameRateValue = if (activeVideoQuality == "4K" || isTimelapseMode) 30 else 60

                    try {
                        setVideoSize(size.first, size.second)
                    } catch (e: Exception) {
                        Log.w(tag, "Size ${size.first}x${size.second} not fully compatible, falling back to 1080p", e)
                        setVideoSize(1920, 1080)
                    }
                    setVideoEncodingBitRate(bitrateValue)
                    setVideoFrameRate(frameRateValue)
                    
                    if (isTimelapseMode) {
                        // Native Timelapse configuration: capture frames slower, play back at standard target speed
                        setCaptureRate(2.0) // 2 FPS recorded, which plays back at standard video speed (15x timelapse!)
                    }
                    
                    // Prioritize modern high-efficiency HEVC (H.265) encoding
                    try {
                        setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
                    } catch (e: Exception) {
                        Log.w(tag, "HEVC codec is not supported on this platform, falling back to legacy H264", e)
                        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    }
                    
                    if (hasAudio) {
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(44100)
                        setAudioEncodingBitRate(128000)
                    }

                    prepare()
                }

                val recordSurface = mediaRecorder?.surface ?: throw IllegalStateException("Record Surface is null")
                val targets = listOf(previewSurface, recordSurface)

                session.close() // Reconfigure session for recording

                camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(newSession: CameraCaptureSession) {
                        captureSession = newSession
                        try {
                            previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                addTarget(previewSurface)
                                addTarget(recordSurface)
                                setOptimalFpsRange(this, cameraManager.getCameraCharacteristics(camera.id))
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
                            }

                            // Propagate active parameters inside recording
                            applyActiveParameters()

                            newSession.setRepeatingRequest(previewRequestBuilder!!.build(), null, backgroundHandler)

                            mediaRecorder?.start()
                            isRecordingVideo = true

                            // Trigger strong vibration when starting recording
                            triggerVibration(150L)

                            android.os.Handler(Looper.getMainLooper()).post { onStarted() }

                        } catch (e: Exception) {
                            Log.e(tag, "Falla al repetir request de grabación", e)
                            android.os.Handler(Looper.getMainLooper()).post { onError("Sesión de grabación fallida: ${e.localizedMessage}") }
                        }
                    }

                    override fun onConfigureFailed(newSession: CameraCaptureSession) {
                        android.os.Handler(Looper.getMainLooper()).post { onError("Imposible iniciar sesión de grabación") }
                    }
                }, backgroundHandler)

            } catch (e: Exception) {
                Log.e(tag, "Falla iniciando captura de video", e)
                android.os.Handler(Looper.getMainLooper()).post { onError("Error al preparar vídeo: ${e.localizedMessage}") }
            }
        }
    }

    /**
     * Stop video recording safely.
     */
    fun stopVideoRecording(onStopped: (File) -> Unit, onError: (String) -> Unit) {
        if (!isRecordingVideo) return

        backgroundHandler?.post {
            try {
                isRecordingVideo = false
                mediaRecorder?.apply {
                    try {
                        stop()
                    } catch (e: Exception) {
                        Log.w(tag, "Stop recording trace exception", e)
                    }
                    reset()
                    release()
                }
                mediaRecorder = null

                triggerVibration(80L)

                // Reconfigure to standard preview session
                val file = videoFile
                if (file != null) {
                    android.os.Handler(Looper.getMainLooper()).post { onStopped(file) }
                }

                // Restore Standard Camera preview flow
                val textureView = activeTextureView
                if (textureView != null) {
                    openCamera(currentCameraId, textureView, {}, {})
                }

            } catch (e: Exception) {
                Log.e(tag, "Falla al detener grabación de vídeo", e)
                android.os.Handler(Looper.getMainLooper()).post { onError("Error al guardar vídeo: ${e.localizedMessage}") }
            }
        }
    }

    /**
     * Helper to select best preview dimensions, fitting typical portrait screens.
     */
    private fun selectOptimalPreviewSize(map: StreamConfigurationMap, textureView: TextureView): Size {
        val sizes = map.getOutputSizes(SurfaceTexture::class.java) ?: return Size(1920, 1080)
        
        // Find 16:9 or 4:3 high-res sizes
        val targetRatio = 16.0 / 9.0
        val matches = sizes.filter {
            val ratio = it.width.toDouble() / it.height.toDouble()
            Math.abs(ratio - targetRatio) < 0.1 || Math.abs((it.height.toDouble() / it.width.toDouble()) - targetRatio) < 0.1
        }
        
        // Choose close to 1080p (for rendering preview fluidity)
        val best = matches.minByOrNull { Math.abs(it.height - 1080) } ?: sizes.maxByOrNull { it.width * it.height }
        return best ?: Size(1920, 1080)
    }

    /**
     * Binds maximum fluidity frame range to request (e.g. 120Hz viewfinders).
     */
    private fun setOptimalFpsRange(builder: CaptureRequest.Builder, characteristics: CameraCharacteristics) {
        try {
            val ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            if (ranges != null && ranges.isNotEmpty()) {
                // Find high frame rate ranges: [30, 60], [60, 60] or similar
                val bestRange = ranges.maxByOrNull { it.upper }
                if (bestRange != null) {
                    builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, bestRange)
                    Log.d(tag, "Aplicada tasa de refresco fluida de previsualización: $bestRange")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Falla configurando FPS fluida", e)
        }
    }

    /**
     * Closes current camera properly.
     */
    fun closeCamera() {
        backgroundHandler?.post {
            closeCurrentCameraInternal()
        }
    }

    /**
     * Completely destroys the camera service and releases background thread.
     */
    fun destroy() {
        backgroundHandler?.post {
            closeCurrentCameraInternal()
            stopBackgroundThread()
        }
    }

    private fun closeCurrentCameraInternal() {
        try {
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader?.close()
            imageReader = null

            mediaRecorder?.release()
            mediaRecorder = null
            
            isRecordingVideo = false
        } catch (e: Exception) {
            Log.e(tag, "Error al cerrar la cámara", e)
        }
    }

    /**
     * Triggers active vibration for accurate tactile confirmation (X-axis physical feedback mimic).
     */
    private fun triggerVibration(durationMs: Long) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "No se pudo vibrar el dispositivo (sin hardware o sin permisos)", e)
        }
    }
}
