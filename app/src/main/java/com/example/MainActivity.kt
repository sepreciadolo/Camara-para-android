package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.TextureView
import android.widget.Toast
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.camera.CameraFilter
import com.example.camera.CameraOption
import com.example.camera.CameraViewModel
import com.example.camera.FilterPresets
import com.example.ui.theme.MyApplicationTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        viewModel.initService(applicationContext)

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF000000) // Beautiful absolute pitch black background
                ) {
                    CameraAppScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun CameraAppScreen(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val permissionGranted by viewModel.isPermissionGranted.collectAsStateWithLifecycle()
    
    // Dynamically request storage permissions on older APIs for seamless MediaStore writing
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    // Permission launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val cameraOk = results[Manifest.permission.CAMERA] ?: false
        viewModel.setPermissionsGranted(cameraOk)
        if (!cameraOk) {
            Toast.makeText(context, "Se requiere permiso de cámara para utilizar la app", Toast.LENGTH_LONG).show()
        }
    }

    // Checking permissions on launch
    LaunchedEffect(Unit) {
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionsGranted(hasCamera)
        if (!hasCamera) {
            launcher.launch(permissionsToRequest)
        }
    }

    if (!permissionGranted) {
        PermissionRequestView {
            launcher.launch(permissionsToRequest)
        }
    } else {
        CameraWorkspace(viewModel)
    }
}

/**
 * Onboarding screen prompting users for Camera hardware permissions.
 */
@Composable
fun PermissionRequestView(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F1116), Color(0xFF040507))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFF1E212E), CircleShape)
                    .border(1.dp, Color(0xFF32394D), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Camera Permission",
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(44.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Permiso de Acceso a Cámaras",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Para exprimir al máximo el hardware de tu dispositivo (Sony IMX882 f/1.79 principal, gran angular y frontal) con latencias mínimas y controles PRO directos, habilitamos conexiones de bajo nivel con la API Camera2 nativa de Android.",
                fontSize = 14.sp,
                color = Color(0xFF8E95A5),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("grant_permission_button")
            ) {
                Text("Permitir Acceso", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Immersive camera control workspace.
 */
@Composable
fun CameraWorkspace(viewModel: CameraViewModel) {
    val context = LocalContext.current
    var activeTextureView by remember { mutableStateOf<TextureView?>(null) }
    
    // Bind states
    val cameras by viewModel.discoveredCameras.collectAsStateWithLifecycle()
    val selectedCamera by viewModel.selectedCamera.collectAsStateWithLifecycle()
    val activeFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()
    val zoomFactor by viewModel.zoomFactor.collectAsStateWithLifecycle()
    val shootMode by viewModel.shootMode.collectAsStateWithLifecycle()
    val lastPhotoUri by viewModel.lastSavedPhotoUri.collectAsStateWithLifecycle()
    val isRecordingVideo by viewModel.isRecordingVideo.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsStateWithLifecycle()
    val timerDuration by viewModel.timerDuration.collectAsStateWithLifecycle()
    val countdownTicks by viewModel.countdownTicks.collectAsStateWithLifecycle()

    val showGrid by viewModel.showGrid.collectAsStateWithLifecycle()
    val videoQuality by viewModel.videoQuality.collectAsStateWithLifecycle()
    val manualContrast by viewModel.manualContrast.collectAsStateWithLifecycle()

    var focusPoint by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Pro configurations
    val isProDrawerOpen by viewModel.isProControlsOpen.collectAsStateWithLifecycle()
    val manualIso by viewModel.manualIso.collectAsStateWithLifecycle()
    val manualExposureTime by viewModel.manualExposureTimeNs.collectAsStateWithLifecycle()
    val manualFocusDistance by viewModel.manualFocusDistance.collectAsStateWithLifecycle()
    
    // System status banner
    val statusBarMsg by viewModel.statusBarMessage.collectAsStateWithLifecycle()
    
    // Filter selecting dialog bottom sheet toggle
    var isFilterDialogShow by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCamera, activeTextureView) {
        val tv = activeTextureView
        val cam = selectedCamera
        if (tv != null && cam != null) {
            viewModel.bindCameraStream(tv, onError = { err ->
                Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
            })
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                viewModel.closeCamera()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val tv = activeTextureView
                val cam = selectedCamera
                if (tv != null && cam != null) {
                    viewModel.bindCameraStream(tv, onError = { err ->
                        Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        if (!isLandscape) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // 1. TOOLBAR / UPPER TECHNICAL METRICS
                CameraUpperBoundToolbar(
                    flashMode = flashMode,
                    timerDuration = timerDuration,
                    isProControlsOpen = isProDrawerOpen,
                    selectedCamera = selectedCamera,
                    showGrid = showGrid,
                    videoQuality = videoQuality,
                    onToggleFlash = { viewModel.toggleFlashMode() },
                    onToggleTimer = {
                        val next = when (timerDuration) {
                            0 -> 3
                            3 -> 5
                            5 -> 10
                            else -> 0
                        }
                        viewModel.setTimerDuration(next)
                    },
                    onTogglePro = { viewModel.toggleProControls() },
                    onToggleGrid = { viewModel.toggleGrid() },
                    onToggleVideoQuality = {
                        val nextQuality = when (videoQuality) {
                            "720p" -> "1080p"
                            "1080p" -> "4K"
                            "4K" -> "720p"
                            else -> "1080p"
                        }
                        viewModel.setVideoQuality(nextQuality)
                    }
                )

                // 2. LIVE VIEW VIEWFINDER FIELD
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(44.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(44.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    focusPoint = offset
                                    viewModel.triggerAutoFocus(offset.x, offset.y)
                                    viewModel.setTempStatusMessage("Enfoque: Punto de doble toque fijado")
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(1500)
                                        focusPoint = null
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    viewModel.setTempStatusMessage("Ajustando Contraste...")
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val delta = -dragAmount.y / 600f
                                    val newContrast = (manualContrast + delta).coerceIn(0.4f, 2.2f)
                                    viewModel.setManualContrast(newContrast)
                                    viewModel.setTempStatusMessage(
                                        String.format(Locale.getDefault(), "Contraste: %.1f× (Arrastra ↑↓)", newContrast)
                                    )
                                },
                                onDragEnd = {
                                    viewModel.setTempStatusMessage("Contraste establecido")
                                },
                                onDragCancel = {
                                    viewModel.setTempStatusMessage("Cambio de contraste cancelado")
                                }
                            )
                        }
                        .pointerInput(zoomFactor) {
                            detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, rotation ->
                                if (zoom != 1.0f) {
                                    viewModel.setZoom(zoomFactor * zoom)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                        activeTextureView = this@apply
                                        configureTextureViewTransform(this@apply, width, height, viewModel)
                                    }
                                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                                        configureTextureViewTransform(this@apply, width, height, viewModel)
                                    }
                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        if (activeTextureView == this@apply) {
                                            activeTextureView = null
                                            viewModel.closeCamera()
                                        }
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("camera_viewfinder")
                    )

                    // High frame rate alignment grids (DSLR rule of thirds)
                    if (showGrid) {
                        CameraRuleOfThirdsGrid()
                    }

                    // Real-time Visual Filter Overlays
                    FilterVisualOverlay(filter = activeFilter)

                    // High-quality specialty guidelines overlays for Portrait, Document, and Panorama
                    ModeSpecialtyOverlay(mode = shootMode)

                    // Aesthetic Focus Reticle aligned perfectly in the center OR at user tapped focus point
                    if (focusPoint != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .absoluteOffset {
                                    androidx.compose.ui.unit.IntOffset(
                                        focusPoint!!.x.toInt() - 48,
                                        focusPoint!!.y.toInt() - 48
                                    )
                                }
                                .size(96.dp)
                                .border(1.5.dp, Color(0xFFFBBF24), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFFBBF24), CircleShape)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }

                    // High-End Telemetry Overlays - only show in Pro Mode to keep the picture clean
                    if (isProDrawerOpen) {
                        // Top Left Meta Info
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(20.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Column {
                                Text(
                                    text = "OIS ACTIVE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFBBF24),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "SONY IMX882",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Top Right Exposure Info Metrics
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(20.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val currentIso = manualIso ?: 100
                                val exposureVal = manualExposureTime
                                val currentShutter = if (exposureVal != null) {
                                    "1/${1_000_000_000L / exposureVal}"
                                } else {
                                    "1/500"
                                }
                                listOf(
                                    "ISO $currentIso",
                                    "S $currentShutter",
                                    "EV -0.3"
                                ).forEach { metric ->
                                    Box(
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = metric,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Counting Tick Visual Overlay
                    if (countdownTicks >= 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = countdownTicks.toString(),
                                fontSize = 90.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFBBF24)
                            )
                        }
                    }

                    // Horizontal LENS SELECTOR Switcher Pill Floating Bottom Center of Viewfinder
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                            .padding(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            cameras.forEach { cam ->
                                val isSelected = cam.id == selectedCamera?.id
                                val label = when(cam.facing) {
                                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                                    else -> if (cam.id == "0") "1.0" else "0.6"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) Color.White else Color.Transparent)
                                        .clickable {
                                            val tv = activeTextureView
                                            if (tv != null) {
                                                viewModel.selectCamera(cam, tv, onError = { err ->
                                                    Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                                                })
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }

                    // Video capture counter
                    if (isRecordingVideo) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                                .background(Color(0xFFE53935), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(8.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "REC %02d:%02d", recordingDuration / 60, recordingDuration % 60),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Floating glass Filters button in the bottom-right corner of the viewfinder
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        IconButton(
                            onClick = { isFilterDialogShow = true },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Visual Color filters list sheet",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 3. COLLAPSIBLE PRO MANUAL KNOBS DRAWER
                AnimatedVisibility(
                    visible = isProDrawerOpen,
                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = tween(250)) + fadeOut()
                ) {
                    CameraProManualControlsDrawer(
                        manualIso = manualIso,
                        manualExposureTime = manualExposureTime,
                        manualFocusDistance = manualFocusDistance,
                        onSetExposure = { iso, exp -> viewModel.setManualExposure(iso, exp) },
                        onSetFocus = { viewModel.setManualFocus(it) }
                    )
                }

                // Status message toast banner
                StatusBarBanner(message = statusBarMsg)

                // 4. BOTTOM ACTION & TRIGGER ROW
                CameraBottomBar(
                    viewModel = viewModel,
                    shootMode = shootMode,
                    lastPhotoUri = lastPhotoUri,
                    isRecordingVideo = isRecordingVideo,
                    onModeChange = {
                        if (!isRecordingVideo) {
                            viewModel.setShootMode(it)
                        }
                    },
                    onTriggerCapture = {
                        viewModel.triggerPhotoCapture(context, onError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        })
                    },
                    onFlipCamera = {
                        val tv = activeTextureView
                        if (tv != null && cameras.isNotEmpty() && selectedCamera != null) {
                            val currentIndex = cameras.indexOfFirst { it.id == selectedCamera!!.id }
                            val nextIndex = (currentIndex + 1) % cameras.size
                            val nextCam = cameras[nextIndex]
                            viewModel.selectCamera(nextCam, tv, onError = { err ->
                                Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                            })
                        }
                    },
                    onLaunchGallery = {
                        if (lastPhotoUri != null) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(lastPhotoUri, "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se encontró aplicación de galería", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No hay fotografías almacenadas", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        } else {
            // Adaptive Landscape layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Left pane: Viewfinder + overlaid status grids/telemetries, and Pro Drawer overlay!
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(32.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    focusPoint = offset
                                    viewModel.triggerAutoFocus(offset.x, offset.y)
                                    viewModel.setTempStatusMessage("Enfoque: Punto de doble toque fijado")
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(1500)
                                        focusPoint = null
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    viewModel.setTempStatusMessage("Ajustando Contraste...")
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val delta = -dragAmount.y / 600f
                                    val newContrast = (manualContrast + delta).coerceIn(0.4f, 2.2f)
                                    viewModel.setManualContrast(newContrast)
                                    viewModel.setTempStatusMessage(
                                        String.format(Locale.getDefault(), "Contraste: %.1f× (Arrastra ↑↓)", newContrast)
                                    )
                                },
                                onDragEnd = {
                                    viewModel.setTempStatusMessage("Contraste establecido")
                                },
                                onDragCancel = {
                                    viewModel.setTempStatusMessage("Cambio de contraste cancelado")
                                }
                            )
                        }
                        .pointerInput(zoomFactor) {
                            detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, rotation ->
                                if (zoom != 1.0f) {
                                    viewModel.setZoom(zoomFactor * zoom)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                        activeTextureView = this@apply
                                        configureTextureViewTransform(this@apply, width, height, viewModel)
                                    }
                                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                                        configureTextureViewTransform(this@apply, width, height, viewModel)
                                    }
                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        if (activeTextureView == this@apply) {
                                            activeTextureView = null
                                            viewModel.closeCamera()
                                        }
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("camera_viewfinder")
                    )

                    // High frame rate grid
                    if (showGrid) {
                        CameraRuleOfThirdsGrid()
                    }

                    // Real-time Visual Filter Overlay
                    FilterVisualOverlay(filter = activeFilter)

                    // High-quality specialty guidelines overlays for Portrait, Document, and Panorama
                    ModeSpecialtyOverlay(mode = shootMode)

                    // Aesthetic Focus Reticle aligned perfectly in the center OR at user tapped focus point
                    if (focusPoint != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .absoluteOffset {
                                    androidx.compose.ui.unit.IntOffset(
                                        focusPoint!!.x.toInt() - 40,
                                        focusPoint!!.y.toInt() - 40
                                    )
                                }
                                .size(80.dp)
                                .border(1.5.dp, Color(0xFFFBBF24), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFFBBF24), CircleShape)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(4.dp).background(Color.White, CircleShape))
                        }
                    }

                    // OIS, telemetry, exposure metric overlays in viewfinder - only show in Pro Mode
                    if (isProDrawerOpen) {
                        // Top Left Overlay: OIS Active
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column {
                                Text("OIS ACTIVE", fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFBBF24), letterSpacing = 1.sp)
                                Text("SONY IMX882", fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        // Top Right Exposure metrics
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val currentIso = manualIso ?: 100
                                val currentShutter = if (manualExposureTime != null) "1/${1_000_000_000L / manualExposureTime!!}" else "1/500"
                                listOf("ISO $currentIso", "S $currentShutter", "EV -0.3").forEach { metric ->
                                    Box(
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(metric, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Lens floating selector
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                            .padding(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            cameras.forEach { cam ->
                                val isSelected = cam.id == selectedCamera?.id
                                val label = when(cam.facing) {
                                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                                    else -> if (cam.id == "0") "1.0" else "0.6"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) Color.White else Color.Transparent)
                                        .clickable {
                                            val tv = activeTextureView
                                            if (tv != null) {
                                                viewModel.selectCamera(cam, tv, onError = { err ->
                                                    Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                                                })
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    // Video capture counter overlay
                    if (isRecordingVideo) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .background(Color(0xFFE53935), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.padding(end = 6.dp).size(6.dp).background(Color.White, CircleShape))
                                Text(
                                    text = String.format(Locale.getDefault(), "REC %02d:%02d", recordingDuration / 60, recordingDuration % 60),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Floating Filters trigger
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    ) {
                        IconButton(
                            onClick = { isFilterDialogShow = true },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .size(38.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = "Filters", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Overlaid Pro Manual controls drawer inside the viewfinder
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isProDrawerOpen,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.85f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        ) {
                            CameraProManualControlsDrawer(
                                manualIso = manualIso,
                                manualExposureTime = manualExposureTime,
                                manualFocusDistance = manualFocusDistance,
                                onSetExposure = { iso, exp -> viewModel.setManualExposure(iso, exp) },
                                onSetFocus = { viewModel.setManualFocus(it) }
                            )
                        }
                    }

                    if (countdownTicks >= 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(countdownTicks.toString(), fontSize = 80.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFBBF24))
                        }
                    }
                }

                // Right Pane: Compact layout of side controls
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .fillMaxHeight()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top control group: Flash, Timer, Pro, Grid, and Video Quality toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flash Mode Toggle icon
                        val flashIcon = when (flashMode) {
                            "on" -> Icons.Default.FlashOn
                            "auto" -> Icons.Default.FlashAuto
                            "torch" -> Icons.Default.Highlight
                            else -> Icons.Default.FlashOff
                        }
                        IconButton(
                            onClick = { viewModel.toggleFlashMode() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(flashIcon, contentDescription = "Flash", tint = if (flashMode == "off") Color.White.copy(alpha = 0.5f) else Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                        }

                        // Timer Duration Selector
                        IconButton(
                            onClick = {
                                val next = when (timerDuration) {
                                    0 -> 3
                                    3 -> 5
                                    5 -> 10
                                    else -> 0
                                }
                                viewModel.setTimerDuration(next)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Timer, contentDescription = "Timer", tint = if (timerDuration == 0) Color.White.copy(alpha = 0.5f) else Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                                if (timerDuration > 0) {
                                    Text(
                                        text = "${timerDuration}s",
                                        fontSize = 6.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFBBF24),
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
                                }
                            }
                        }

                        // PRO Mode Toggle
                        IconButton(
                            onClick = { viewModel.toggleProControls() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.SettingsSuggest, contentDescription = "Pro Manual Settings", tint = if (isProDrawerOpen) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                        }

                        // GRID Toggle
                        IconButton(
                            onClick = { viewModel.toggleGrid() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .border(0.5.dp, if (showGrid) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 3.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "GRID",
                                    fontSize = 6.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showGrid) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // VIDEO QUALITY Toggle
                        IconButton(
                            onClick = {
                                val nextQuality = when (videoQuality) {
                                    "720p" -> "1080p"
                                    "1080p" -> "4K"
                                    "4K" -> "720p"
                                    else -> "1080p"
                                }
                                viewModel.setVideoQuality(nextQuality)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 3.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = videoQuality,
                                    fontSize = 6.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Status message inside side panel
                    StatusBarBanner(message = statusBarMsg)

                    // Middle: Mode Carousel Selector formatted vertically
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp)
                    ) {
                        CameraViewModel.ShootMode.values().forEach { mode ->
                            val isSelected = mode == shootMode
                            val label = when(mode) {
                                CameraViewModel.ShootMode.AUTO -> "AUTO"
                                CameraViewModel.ShootMode.BURST -> "RÁFAGA"
                                CameraViewModel.ShootMode.TIMER -> "TEMPO"
                                CameraViewModel.ShootMode.VIDEO -> "VÍDEO"
                                CameraViewModel.ShootMode.PORTRAIT -> "RETRATO"
                                CameraViewModel.ShootMode.DOCUMENTS -> "DOCS"
                                CameraViewModel.ShootMode.TIMELAPSE -> "TIMELAPSE"
                                CameraViewModel.ShootMode.ULTRA_HD -> "ULTRA HD"
                                CameraViewModel.ShootMode.NIGHT -> "NOCTURNO"
                                CameraViewModel.ShootMode.DUAL_VIDEO -> "VÍDEO DUAL"
                                CameraViewModel.ShootMode.PANORAMA -> "PANORÁMICA"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFFFBBF24).copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        if (!isRecordingVideo) {
                                            viewModel.setShootMode(mode)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = if (isSelected) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.4f),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    // Bottom: Action trigger block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gallery Shortcut Link
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    if (lastPhotoUri != null) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(lastPhotoUri, "image/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No se encontró aplicación de galería", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "No hay fotografías almacenadas", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (lastPhotoUri != null) {
                                Icon(Icons.Default.Collections, contentDescription = "Gallery", tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery link", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Large Shutter Button with unified intelligent behaviors
                        AestheticShutterButton(
                            viewModel = viewModel,
                            shootMode = shootMode,
                            isRecordingVideo = isRecordingVideo,
                            size = 56.dp,
                            borderWidth = 2.dp
                        )

                        // Flip Camera Icon Toggle
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    val tv = activeTextureView
                                    if (tv != null && cameras.isNotEmpty() && selectedCamera != null) {
                                        val currentIndex = cameras.indexOfFirst { it.id == selectedCamera!!.id }
                                        val nextIndex = (currentIndex + 1) % cameras.size
                                        val nextCam = cameras[nextIndex]
                                        viewModel.selectCamera(nextCam, tv, onError = { err ->
                                            Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                                        })
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip camera", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // 5. COLOR FILTERS BOTTOM PANEL DIALOG with an elegant modal scrim background
    if (isFilterDialogShow) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { isFilterDialogShow = false },
            contentAlignment = Alignment.BottomCenter
        ) {
            CameraFilterDialog(
                activeFilter = activeFilter,
                onSelectFilter = {
                    viewModel.setFilter(it)
                    isFilterDialogShow = false
                },
                onDismiss = { isFilterDialogShow = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {} // Prevent click propagation to background scrim
                    .padding(16.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

/**
 * Standard DSLR Grid overlay lines.
 */
@Composable
fun CameraRuleOfThirdsGrid() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Vertical lines
        Row(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(0.5.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(0.5.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        // Horizontal lines
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Generates custom color tones dynamically matching preset designs.
 */
@Composable
fun FilterVisualOverlay(filter: CameraFilter) {
    if (filter.id == FilterPresets.NORMAL.id) return
    
    val brush = when(filter.id) {
        FilterPresets.NOIR.id -> {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.05f),
                    Color.Black.copy(alpha = 0.15f)
                )
            )
        }
        FilterPresets.RETRO_VINTAGE.id -> {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFB74D).copy(alpha = 0.15f),
                    Color(0xFF8D6E63).copy(alpha = 0.2f)
                )
            )
        }
        FilterPresets.CYBERPUNK.id -> {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE040FB).copy(alpha = 0.1f),
                    Color(0xFF00E5FF).copy(alpha = 0.12f)
                )
            )
        }
        FilterPresets.EMERALD_COOL.id -> {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF00B0FF).copy(alpha = 0.08f),
                    Color(0xFF00BFA5).copy(alpha = 0.12f)
                )
            )
        }
        FilterPresets.GOLDEN_HOUR.id -> {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF8F00).copy(alpha = 0.18f),
                    Color(0xFFD84315).copy(alpha = 0.22f)
                )
            )
        }
        else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush)
    )
}

/**
 * Helper to dynamically scale the preview in a TextureView without distortion using center-crop logic.
 */
fun configureTextureViewTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int, viewModel: CameraViewModel) {
    if (viewWidth == 0 || viewHeight == 0) return
    val previewSize = viewModel.getPreviewSize()
    val matrix = android.graphics.Matrix()
    
    val centerX = viewWidth / 2f
    val centerY = viewHeight / 2f
    
    val isPortrait = viewHeight > viewWidth
    val previewW = if (isPortrait) previewSize.height else previewSize.width
    val previewH = if (isPortrait) previewSize.width else previewSize.height
    
    val scaleX = viewWidth.toFloat() / previewW.toFloat()
    val scaleY = viewHeight.toFloat() / previewH.toFloat()
    
    // Fill completely (center crop) to keep preview aspect ratio perfectly undistorted
    val scale = Math.max(scaleX, scaleY)
    
    val postScaleX = (previewW.toFloat() * scale) / viewWidth.toFloat()
    val postScaleY = (previewH.toFloat() * scale) / viewHeight.toFloat()
    
    matrix.setScale(postScaleX, postScaleY, centerX, centerY)
    textureView.setTransform(matrix)
}

/**
 * High-End Visual Guidelines Overlays for specialty modes: Retrato, Documentos, Panorámica.
 */
@Composable
fun ModeSpecialtyOverlay(mode: CameraViewModel.ShootMode) {
    when (mode) {
        CameraViewModel.ShootMode.PORTRAIT -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Soft dash circular bokeh depth indicator
                androidx.compose.foundation.Canvas(modifier = Modifier.size(240.dp)) {
                    drawCircle(
                        color = Color(0xFFFBBF24).copy(alpha = 0.4f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                        )
                    )
                }
                
                // Overlay text badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFFFBBF24), CircleShape)
                        )
                        Text(
                            text = "MODO RETRATO • ENFOQUE DE PROFUNDIDAD",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
        CameraViewModel.ShootMode.DOCUMENTS -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Scanning frame outline
                Box(
                    modifier = Modifier
                        .size(width = 240.dp, height = 320.dp)
                        .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(12.dp))
                        .background(Color(0xFF3B82F6).copy(alpha = 0.05f))
                )
                
                // Overlay text badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "MODO DOCUMENTO • ESCANER DE ALTA FIDELIDAD",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3B82F6),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
        CameraViewModel.ShootMode.PANORAMA -> {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Horizontal level reference lines
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(1.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF10B981), Color.Transparent)
                            )
                        )
                )
                
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 56.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(Color(0xFF10B981).copy(alpha = 0.7f), CircleShape)
                        )
                    }
                }
                
                // Overlay text badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "MODO PANORÁMICA • GIRE CONTINUAMENTE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
        else -> {}
    }
}

/**
 * Unified Intelligent Shutter Button that supports pressing-and-holding continuous burst mode capture,
 * QuickVideo conversion trigger, other specialty modes, and standard photo capture.
 */
@Composable
fun AestheticShutterButton(
    viewModel: CameraViewModel,
    shootMode: CameraViewModel.ShootMode,
    isRecordingVideo: Boolean,
    size: androidx.compose.ui.unit.Dp,
    borderWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isQuickVideoRecording by viewModel.isQuickVideoRecording.collectAsStateWithLifecycle()
    
    val scaleAnim by animateFloatAsState(
        targetValue = if (isRecordingVideo || isQuickVideoRecording) 0.85f else 1.0f,
        animationSpec = tween(200)
    )
    
    Box(
        modifier = modifier
            .scale(scaleAnim)
            .size(size)
            .border(BorderStroke(borderWidth, Color.White), CircleShape)
            .padding(borderWidth + 2.dp)
            .testTag("shutter_button")
            .pointerInput(shootMode, isRecordingVideo, isQuickVideoRecording) {
                detectTapGestures(
                    onPress = { offset ->
                        val pressTime = System.currentTimeMillis()
                        
                        if (shootMode == CameraViewModel.ShootMode.BURST) {
                            viewModel.startContinuousBurstCapture(context) { err ->
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                        val released = tryAwaitRelease()
                        val duration = System.currentTimeMillis() - pressTime
                        
                        if (shootMode == CameraViewModel.ShootMode.BURST) {
                            viewModel.stopContinuousBurstCapture()
                        } else if (shootMode == CameraViewModel.ShootMode.AUTO) {
                            if (duration >= 400) {
                                viewModel.stopQuickVideo(context)
                            } else {
                                viewModel.triggerPhotoCapture(context) { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            // Standard triggers
                            viewModel.triggerPhotoCapture(context) { err ->
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onLongPress = {
                        if (shootMode == CameraViewModel.ShootMode.AUTO) {
                            viewModel.startQuickVideo(context) { err ->
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val centerColor = if (shootMode == CameraViewModel.ShootMode.VIDEO || actsLikeVideo(shootMode, isRecordingVideo, isQuickVideoRecording)) Color(0xFFE53935) else Color.White
        val centerShape = if (isRecordingVideo || isQuickVideoRecording) RoundedCornerShape(8.dp) else CircleShape
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(centerColor, centerShape)
        ) {
            if (shootMode == CameraViewModel.ShootMode.TIMER && !isRecordingVideo) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer shoot trigger",
                        tint = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.size(size * 0.3f)
                    )
                }
            }
        }
    }
}

private fun actsLikeVideo(shootMode: CameraViewModel.ShootMode, isRecording: Boolean, isQuickRecording: Boolean): Boolean {
    return isRecording || isQuickRecording || shootMode == CameraViewModel.ShootMode.VIDEO
}

/**
 * Top control panel bar.
 */
@Composable
fun CameraUpperBoundToolbar(
    flashMode: String,
    timerDuration: Int,
    isProControlsOpen: Boolean,
    selectedCamera: CameraOption?,
    showGrid: Boolean,
    videoQuality: String,
    onToggleFlash: () -> Unit,
    onToggleTimer: () -> Unit,
    onTogglePro: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleVideoQuality: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Flash and Grid pills
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Flash Pill
            val flashIcon = when (flashMode) {
                "on" -> Icons.Default.FlashOn
                "auto" -> Icons.Default.FlashAuto
                "torch" -> Icons.Default.Highlight
                else -> Icons.Default.FlashOff
            }
            UpperToolbarPill(
                icon = flashIcon,
                isActive = flashMode != "off",
                onClick = onToggleFlash,
                badgeText = flashMode.uppercase(Locale.ROOT)
            )

            // Grid Pill
            UpperToolbarPill(
                icon = Icons.Default.GridView,
                isActive = showGrid,
                onClick = onToggleGrid,
                badgeText = "GRID"
            )
        }

        // Center: High-end compact capsule
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "50MP • HDR",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        // Right side: Video quality, Timer and PRO
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quality Pill
            UpperToolbarPill(
                icon = Icons.Default.VideoCameraBack,
                isActive = videoQuality == "4K",
                onClick = onToggleVideoQuality,
                badgeText = videoQuality
            )

            // Timer Pill
            UpperToolbarPill(
                icon = Icons.Default.Timer,
                isActive = timerDuration > 0,
                onClick = onToggleTimer,
                badgeText = if (timerDuration == 0) "OFF" else "${timerDuration}S"
            )

            // PRO Pill
            UpperToolbarPill(
                icon = Icons.Default.SettingsSuggest,
                isActive = isProControlsOpen,
                onClick = onTogglePro,
                badgeText = "PRO"
            )
        }
    }
}

@Composable
fun UpperToolbarPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    badgeText: String
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color(0xFFFBBF24).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (isActive) Color(0xFFFBBF24).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = badgeText,
                fontSize = 6.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isActive) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.5f),
                letterSpacing = 0.2.sp
            )
        }
    }
}

/**
 * Camera manual exposure parameters panel.
 */
@Composable
fun CameraProManualControlsDrawer(
    manualIso: Int?,
    manualExposureTime: Long?,
    manualFocusDistance: Float?,
    onSetExposure: (Int?, Long?) -> Unit,
    onSetFocus: (Float?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF08080A))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "CONTROLES MANUALES (PRO SONY IMX882)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFBBF24),
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 1. ISO Controls
        Text(
            text = "SENSIBILIDAD ELECTRÓNICA (ISO)",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF8E95A5),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ProParameterKnob(text = "AUTO", isSelected = manualIso == null) {
                    onSetExposure(null, null)
                }
            }
            items(listOf(50, 100, 200, 400, 800, 1600, 3200)) { isoValue ->
                val isSelected = manualIso == isoValue
                ProParameterKnob(text = "ISO $isoValue", isSelected = isSelected) {
                    // Match exposure speeds: 1/125s for manual shutter synchronizations
                    onSetExposure(isoValue, 8_000_000L) 
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Shutter Speed / Exposure controls
        Text(
            text = "TIEMPO DE EXPOCICIÓN (VELOCIDAD)",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF8E95A5),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ProParameterKnob(text = "AUTO", isSelected = manualExposureTime == null) {
                    onSetExposure(null, null)
                }
            }
            items(listOf(
                1_000_000L to "1/1000s",
                4_000_000L to "1/250s",
                16_000_000L to "1/60s",
                66_000_000L to "1/15s",
                250_000_000L to "1/4s",
                1_000_000_000L to "1.0s"
            )) { (ns, label) ->
                val isSelected = manualExposureTime == ns
                ProParameterKnob(text = label, isSelected = isSelected) {
                    onSetExposure(manualIso ?: 400, ns)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Focal Distance Manual Adjustment Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ENFOQUE MANUAL (FOCAL)",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8E95A5)
            )
            
            Text(
                text = if (manualFocusDistance == null) "AF CONTINUO" else String.format(Locale.getDefault(), "Manual: %.1f cm", (10.0f - manualFocusDistance) * 3),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFBBF24)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProParameterKnob(
                text = "AF AUTO",
                isSelected = manualFocusDistance == null,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                onSetFocus(null)
            }
            
            Slider(
                value = manualFocusDistance ?: 0.0f,
                onValueChange = { onSetFocus(it) },
                valueRange = 0.0f..10.0f, // 0 means infinity focus, higher is macros focusing limit
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFBBF24),
                    activeTrackColor = Color(0xFFFBBF24).copy(alpha = 0.7f),
                    inactiveTrackColor = Color(0xFF2C3242)
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Small design capsule for list params.
 */
@Composable
fun ProParameterKnob(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = if (isSelected) Color(0xFFFBBF24) else Color(0xFF1E2129),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else Color.White
        )
    }
}

/**
 * Top toast banner.
 */
@Composable
fun StatusBarBanner(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF11141A))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message ?: "",
                fontSize = 12.sp,
                color = Color(0xFF2196F3),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Shutter bar trigger configurations.
 */
@Composable
fun CameraBottomBar(
    viewModel: CameraViewModel,
    shootMode: CameraViewModel.ShootMode,
    lastPhotoUri: Uri?,
    isRecordingVideo: Boolean,
    onModeChange: (CameraViewModel.ShootMode) -> Unit,
    onTriggerCapture: () -> Unit,
    onFlipCamera: () -> Unit,
    onLaunchGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF000000))
            .padding(bottom = 32.dp, top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Carousel Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CameraViewModel.ShootMode.values().forEach { mode ->
                val isSelected = mode == shootMode
                val label = when(mode) {
                    CameraViewModel.ShootMode.AUTO -> "AUTO"
                    CameraViewModel.ShootMode.BURST -> "RÁFAGA"
                    CameraViewModel.ShootMode.TIMER -> "TEMPO"
                    CameraViewModel.ShootMode.VIDEO -> "VÍDEO"
                    CameraViewModel.ShootMode.PORTRAIT -> "RETRATO"
                    CameraViewModel.ShootMode.DOCUMENTS -> "DOCS"
                    CameraViewModel.ShootMode.TIMELAPSE -> "TIMELAPSE"
                    CameraViewModel.ShootMode.ULTRA_HD -> "ULTRA HD"
                    CameraViewModel.ShootMode.NIGHT -> "NOCTURNO"
                    CameraViewModel.ShootMode.DUAL_VIDEO -> "VÍDEO DUAL"
                    CameraViewModel.ShootMode.PANORAMA -> "PANORÁMICA"
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onModeChange(mode) }
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        color = if (isSelected) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.3f),
                        letterSpacing = 1.sp
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(Color(0xFFFBBF24), CircleShape)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Shutter Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action: GALLERY THUMBNAIL preview (with custom translucent halo)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clip(CircleShape)
                    .testTag("gallery_preview_button")
                    .clickable { onLaunchGallery() },
                contentAlignment = Alignment.Center
            ) {
                if (lastPhotoUri != null) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = "Photo Library Gallery link",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Library blank placeholder link",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center Action: EXCEL STUTTER BUTTON with full touch-and-hold capabilities
            AestheticShutterButton(
                viewModel = viewModel,
                shootMode = shootMode,
                isRecordingVideo = isRecordingVideo,
                size = 80.dp,
                borderWidth = 3.dp
            )

            // Right Action: FLIP CAMERA lens selector (redefined premium translucent circular glass)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clip(CircleShape)
                    .clickable { onFlipCamera() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip camera direction",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Filter select modal view.
 */
@Composable
fun CameraFilterDialog(
    activeFilter: CameraFilter,
    onSelectFilter: (CameraFilter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FILTROS CREATIVOS EN TIEMPO REAL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close filter list", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPresets.ALL_FILTERS.forEach { filter ->
                    val isSelected = filter.id == activeFilter.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) Color(0xFFFBBF24).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectFilter(filter) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = if (isSelected) Color(0xFFFBBF24) else Color(0xFF8E95A5),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Palette,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = filter.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = filter.description,
                                fontSize = 11.sp,
                                color = Color(0xFF8E95A5)
                            )
                        }
                    }
                }
            }
        }
    }
}
