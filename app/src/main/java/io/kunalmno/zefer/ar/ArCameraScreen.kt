package io.kunalmno.zefer.ar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.widget.Toast
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.kunalmno.zefer.data.NailSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * "Test AR" screen. Stage 4: set switcher, photo capture (save + share) and a 3-second timer. Stage 2: live camera + on-device hand tracking + the product's nails drawn on the fingers.
 */
/** Auto-capture thresholds (tune here). */
internal object AutoCapture {
    /** The hand must stay steady this long before the photo is taken. */
    const val HOLD_MS = 800L
    /** Max distance between where a nail is drawn and where tracking says it belongs, as a fraction of the nail's height. */
    const val MAX_GAP = 0.05f
    /** Max nail movement, in nail-heights per second. */
    const val MAX_SPEED = 0.35f
}

@Composable
fun ArCameraScreen(
    product: NailSet,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** All sets, for the switcher strip. */
    products: List<NailSet> = listOf(product)
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        denied = !granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraContent(product = product, products = products, onBack = onBack)
        } else {
            PermissionPrompt(
                denied = denied,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
                onBack = onBack
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameraContent(product: NailSet, products: List<NailSet>, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var useFront by rememberSaveable { mutableStateOf(true) }
    var tracker by remember { mutableStateOf<HandTracker?>(null) }
    var trackerError by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Debug switches (shown as chips while ArConfig.SHOW_DEBUG_SKELETON is true)
    var preferGpu by rememberSaveable { mutableStateOf(true) }     // falls back to CPU by itself if the GPU delegate fails
    var lowRes by rememberSaveable { mutableStateOf(false) }       // 640x480 analysis frames (true = 320x240)
    var debugOn by rememberSaveable { mutableStateOf(false) }      // tap the title 5 times to show/hide the debug line and chips
    var titleTaps by remember { mutableStateOf(0) }
    var autoCapture by rememberSaveable { mutableStateOf(true) }   // takes the photo by itself when the hand is steady
    var showAlways by rememberSaveable { mutableStateOf(false) }   // draw nails even on the palm side
    var showSkeleton by rememberSaveable { mutableStateOf(false) }
    var baseFrac by rememberSaveable { mutableStateOf(0.75f) }     // nail cuticle position along the fingertip segment
    var sizeScale by rememberSaveable { mutableStateOf(1.0f) }
    var predict by rememberSaveable { mutableStateOf(true) }
    NailTuning.baseFrac = baseFrac
    NailTuning.sizeScale = sizeScale
    NailTuning.predict = predict

    // Which set is being tried on (switchable with the strip). Nails are loaded once per set.
    var currentId by rememberSaveable { mutableStateOf(product.id) }
    val current = products.firstOrNull { it.id == currentId } ?: product
    val nailCache = remember { mutableStateMapOf<Long, List<Bitmap?>>() }
    LaunchedEffect(current.id) {
        if (current.id !in nailCache) nailCache[current.id] = loadNailBitmaps(context, current.nailUrls)
    }
    val nails = nailCache[current.id] ?: emptyList()
    val smoother = remember { NailSmoother() }


    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Capture / timer
    val scope = rememberCoroutineScope()
    var useTimer by rememberSaveable { mutableStateOf(false) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<Shot?>(null) }
    val flashAlpha by animateFloatAsState(if (flash) 0.85f else 0f, label = "flash")

    fun takePhoto(skipTimer: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            if (useTimer && !skipTimer) {
                for (i in 3 downTo 1) { countdown = i; delay(1000) }
                countdown = null
            }
            val picture = captureComposite(previewView, smoother, nails)
            if (picture == null) {
                Toast.makeText(context, "Couldn't take the photo, try again", Toast.LENGTH_SHORT).show()
            } else {
                flash = true; delay(140); flash = false
                shot = Shot(picture, saveToGallery(context, picture))
            }
            busy = false
        }
    }

    // Load the MediaPipe model off the main thread.
    LaunchedEffect(preferGpu, useFront) {
        tracker?.let { old -> tracker = null; withContext(Dispatchers.Default) { old.close() } }
        withContext(Dispatchers.Default) { runCatching { HandTracker(context.applicationContext, preferGpu) } }
            .onSuccess { tracker = it; trackerError = null }
            .onFailure { trackerError = it.message ?: it.javaClass.simpleName }
    }

    // (Re)bind the camera whenever the model is ready or the camera is flipped.
    LaunchedEffect(tracker, useFront, lowRes) {
        val t = tracker ?: return@LaunchedEffect
        val provider = context.cameraProvider()
        cameraProvider = provider

        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val resolution = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    if (lowRes) Size(320, 240) else Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor) { proxy -> t.analyze(proxy, isFrontCamera = useFront) } }

        val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        provider.unbindAll()
        runCatching { provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis) }
            .onFailure { trackerError = "Couldn't open the camera: ${it.message}" }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            analysisExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)
            tracker?.close()
        }
    }

    val emptyFrames = remember { MutableStateFlow(HandFrame()) }
    val emptyFps = remember { MutableStateFlow(0) }
    val frameState = (tracker?.frame ?: emptyFrames).collectAsStateWithLifecycle()
    val frame by frameState
    val fps by (tracker?.fps ?: emptyFps).collectAsStateWithLifecycle()
    val latencyState = (tracker?.latencyMs ?: emptyFps).collectAsStateWithLifecycle()
    val latency by latencyState
    val status = assessHands(frame)

    // ---- Auto-capture: when the hand is in frame, tracked confidently and the nails have stayed still on it
    // for HOLD_MS, take the photo. Moving again re-arms it, so it never fires twice in a row on its own.
    var autoProgress by remember { mutableFloatStateOf(0f) }
    val latestStatus by rememberUpdatedState(status)
    val onAutoShot by rememberUpdatedState<() -> Unit>({ takePhoto(skipTimer = true) })
    LaunchedEffect(autoCapture) {
        var steadySince = 0L
        var armed = true
        while (autoCapture) {
            delay(40)
            val idle = !busy && shot == null && countdown == null
            val hands = frameState.value.hands
            val calm = smoother.steadiness()
            val steady = idle &&
                latestStatus == HandStatus.Ready &&
                hands.isNotEmpty() && hands.all { it.score >= 0.8f } &&
                calm.visible >= 4 &&
                calm.maxGap < AutoCapture.MAX_GAP &&
                calm.maxSpeed < AutoCapture.MAX_SPEED
            val now = SystemClock.uptimeMillis()
            if (!steady) {
                steadySince = 0L
                autoProgress = 0f
                if (idle) armed = true            // the hand moved (or left): ready for the next photo
            } else if (armed) {
                if (steadySince == 0L) steadySince = now
                autoProgress = ((now - steadySince) / AutoCapture.HOLD_MS.toFloat()).coerceIn(0f, 1f)
                if (autoProgress >= 1f) {
                    armed = false
                    steadySince = 0L
                    autoProgress = 0f
                    onAutoShot()
                }
            }
        }
        autoProgress = 0f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        NailOverlay(smoother = smoother, frameState = frameState, latencyState = latencyState, nails = nails, showAlways = showAlways)
        HandOverlay(frame = frame, status = status, showSkeleton = showSkeleton)

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = current.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    titleTaps++
                    if (titleTaps >= 5) { debugOn = !debugOn; titleTaps = 0 }
                }
            )
            RoundButton(onClick = { useFront = !useFront }) {
                Icon(Icons.Outlined.FlipCameraAndroid, contentDescription = "Flip camera", tint = Color.White)
            }
        }

        // Bottom: status pill + tiny debug line
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                trackerError != null -> Pill(
                    "AR couldn't start: $trackerError\n(Is hand_landmarker.task in app/src/main/assets?)",
                    Color(0xFFB3261E)
                )
                tracker == null -> Pill("Loading hand tracking…", Color.Black.copy(alpha = 0.6f))
                else -> Pill(
                    status.message,
                    if (status == HandStatus.Ready) Color(0xFF2E7D32) else Color.Black.copy(alpha = 0.6f)
                )
            }
            if (debugOn && ArConfig.SHOW_DEBUG_SKELETON && tracker != null) {
                val info = frame.hands.joinToString("  ") { "${it.label} ${(it.score * 100).toInt()}% ${if (it.isBackOfHand) "back" else "palm"}" }
                Text(
                    text = "${tracker?.delegateName} · $fps fps · ${latency} ms · ${frame.hands.size} hand(s) $info",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DebugChip(if (preferGpu) "GPU" else "CPU") { preferGpu = !preferGpu }
                    DebugChip(if (lowRes) "320x240" else "640x480") { lowRes = !lowRes }
                    DebugChip(if (showAlways) "Nails: always" else "Nails: back of hand") { showAlways = !showAlways }
                    DebugChip(if (showSkeleton) "Dots: on" else "Dots: off") { showSkeleton = !showSkeleton }
                    DebugChip("Base ${"%.2f".format(baseFrac)}") { baseFrac = nextOf(baseFrac, listOf(0.35f, 0.45f, 0.55f, 0.65f, 0.75f, 0.85f)) }
                    DebugChip("Size ${"%.2f".format(sizeScale)}") { sizeScale = nextOf(sizeScale, listOf(0.7f, 0.8f, 0.9f, 1.0f, 1.1f)) }
                    DebugChip(if (predict) "Predict: on" else "Predict: off") { predict = !predict }
                }
            }

            if (products.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(products, key = { it.id }) { p ->
                        val selected = p.id == current.id
                        AsyncImage(
                            model = p.previewUrl,
                            contentDescription = p.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(if (selected) 60.dp else 52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(p.accentColor.copy(alpha = 0.6f))
                                .border(if (selected) 3.dp else 1.dp, if (selected) p.accentColor else Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .clickable { currentId = p.id }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 3-second timer toggle (for both hands with the phone propped up)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (useTimer) Color.White else Color.Black.copy(alpha = 0.45f))
                        .clickable { useTimer = !useTimer }
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = "3 second timer",
                        tint = if (useTimer) Color.Black else Color.White
                    )
                }
                // Shutter (the green ring fills while auto-capture is waiting for a steady hand)
                Box(modifier = Modifier.size(76.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(4.dp, Color.White, CircleShape)
                            .padding(7.dp)
                            .clip(CircleShape)
                            .background(if (busy) Color.White.copy(alpha = 0.5f) else Color.White)
                            .clickable(enabled = !busy && tracker != null) { takePhoto() }
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val p = autoProgress
                        if (p > 0f) {
                            drawArc(
                                color = Color(0xFF7CFC8A),
                                startAngle = -90f,
                                sweepAngle = 360f * p,
                                useCenter = false,
                                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
                // Auto-capture on/off
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (autoCapture) Color.White else Color.Black.copy(alpha = 0.45f))
                        .clickable {
                            autoCapture = !autoCapture
                            Toast.makeText(
                                context,
                                if (autoCapture) "Auto capture on: hold still and it takes the photo" else "Auto capture off",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = "Auto capture",
                        tint = if (autoCapture) Color.Black else Color.White
                    )
                }
            }
        }

        // Countdown number
        countdown?.let {
            Text(
                text = it.toString(),
                color = Color.White,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 140.sp),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Shutter flash
        if (flashAlpha > 0.01f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))

        // Result: the saved photo with Share / Close
        shot?.let { result ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = result.bitmap.asImageBitmap(),
                    contentDescription = "Your photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                )
                Text(
                    text = if (result.uri != null) "Saved to Pictures/Zefer" else "Couldn't save to gallery",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ResultButton("Close", Icons.Outlined.Close, filled = false) { shot = null }
                    if (result.uri != null) ResultButton("Share", Icons.Outlined.Share, filled = true) { shareImage(context, result.uri) }
                }
            }
        }
    }
}

private class Shot(val bitmap: Bitmap, val uri: android.net.Uri?)

@Composable
private fun ResultButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.Black else Color.White)
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (filled) Color.Black else Color.White)
    }
}

/** Next value of [options] after [current] (wraps around). */
private fun nextOf(current: Float, options: List<Float>): Float {
    val i = options.indexOfFirst { kotlin.math.abs(it - current) < 0.001f }
    return options[(i + 1) % options.size]
}

@Composable
private fun DebugChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
    ) { content() }
}

@Composable
private fun Pill(text: String, background: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun PermissionPrompt(
    denied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Camera needed", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            text = if (denied) "Zefer needs the camera to show nails on your hand. You can turn it on in Settings."
            else "Zefer uses your camera to show nails on your hand. Nothing is uploaded: it all runs on your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 28.dp)
        )
        ActionButton(if (denied) "Open settings" else "Allow camera", if (denied) onOpenSettings else onRequest)
        Box(Modifier.padding(top = 12.dp)) { ActionButton("Not now", onBack, filled = false) }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, filled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 14.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
        )
    }
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }
