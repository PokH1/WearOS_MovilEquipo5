package com.amendezm2009.wearappmobile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val DeepNavy = Color(0xFF0F172A)
val CardSurface = Color(0xFF1E293B)
val NeonBlue = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00FF88)
val SoftRed = Color(0xFFFF5252)
val GlassWhite = Color(0x1AFFFFFF)

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {
    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentSteps by mutableIntStateOf(0)
    private var stepGoal by mutableIntStateOf(10000)
    private var pressure by mutableStateOf<Float?>(null)
    private var temperature by mutableStateOf<Float?>(null)
    private var heartRate by mutableStateOf<Float?>(null)
    private var heartBeat by mutableStateOf<Float?>(null)
    private var battery by mutableStateOf<Int?>(null)
    private var humidity by mutableStateOf<Float?>(null)
    private var proximity by mutableStateOf<Float?>(null)
    private var light by mutableStateOf<Float?>(null)
    private var magneticField by mutableStateOf<Float?>(null)
    
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var photoFile: File? = null
    private var showCamera by mutableStateOf(false)
    private val CAMERA_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }

        setContent {
            AppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainDashboard(
                            steps = currentSteps,
                            onStepChange = { 
                                currentSteps = it
                                syncToWatch()
                            },
                            goal = stepGoal,
                            onGoalChange = { 
                                stepGoal = it
                                syncToWatch()
                            },
                            vitals = VitalsData(pressure, temperature, heartRate, heartBeat, battery, humidity, proximity, light, magneticField),
                            showCamera = showCamera,
                            onOpenGallery = { navController.navigate("gallery") },
                            activity = this@MainActivity
                        )
                    }
                    composable("gallery") {
                        ProfessionalGallery(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
        dataClient.addListener(this)
        Wearable.getMessageClient(this).addListener(this)
    }

    private fun syncToWatch() {
        scope.launch {
            try {
                val request = PutDataMapRequest.create("/steps").apply {
                    dataMap.putInt("steps", currentSteps)
                    dataMap.putInt("goal", stepGoal)
                }.asPutDataRequest()
                dataClient.putDataItem(request).await()
            } catch (e: Exception) {
                Log.e("Sync", "Error syncing to watch: ${e.message}")
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/steps") {
                DataMapItem.fromDataItem(event.dataItem).dataMap.apply {
                    runOnUiThread {
                        currentSteps = getInt("steps")
                        pressure = if (containsKey("pressure")) getFloat("pressure") else null
                        temperature = if (containsKey("temperature")) getFloat("temperature") else null
                        heartRate = if (containsKey("heartRate")) getFloat("heartRate") else null
                        heartBeat = if (containsKey("heartBeat")) getFloat("heartBeat") else null
                        battery = if (containsKey("battery")) getInt("battery") else null
                        humidity = if (containsKey("humidity")) getFloat("humidity") else null
                        proximity = if (containsKey("proximity")) getFloat("proximity") else null
                        light = if (containsKey("light")) getFloat("light") else null
                        magneticField = if (containsKey("magneticField")) getFloat("magneticField") else null
                    }
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/take_photo" -> runOnUiThread { 
                showCamera = true
                sendConfirmation("/photo_taken")
            }
            "/capture_photo" -> runOnUiThread { 
                takePhoto()
                showCamera = false
            }
        }
    }

    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                imageCapture = ImageCapture.Builder().build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this as LifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch (e: Exception) {
                Log.e("Camera", "Error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        photoFile = File(getExternalFilesDir(null), "photo_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile!!).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                sendPhotoToWatch()
                sendConfirmation("/photo_captured")
            }
            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(this@MainActivity, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun sendPhotoToWatch() {
        photoFile?.let { file ->
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val asset = Asset.createFromBytes(stream.toByteArray())
            val request = PutDataMapRequest.create("/photo_image").apply {
                dataMap.putAsset("photo", asset)
            }.asPutDataRequest()
            CoroutineScope(Dispatchers.IO).launch { try { dataClient.putDataItem(request).await() } catch (_: Exception) {} }
        }
    }

    private fun sendConfirmation(path: String) {
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { messageClient.sendMessage(it.id, path, null) }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dataClient.removeListener(this)
        Wearable.getMessageClient(this).removeListener(this)
    }
}

data class VitalsData(
    val pressure: Float?, val temperature: Float?, val heartRate: Float?,
    val heartBeat: Float?, val battery: Int?, val humidity: Float?,
    val proximity: Float?, val light: Float?, val magneticField: Float?
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonBlue,
            secondary = NeonGreen,
            surface = DeepNavy,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun MainDashboard(
    steps: Int, onStepChange: (Int) -> Unit,
    goal: Int, onGoalChange: (Int) -> Unit,
    vitals: VitalsData, showCamera: Boolean, 
    onOpenGallery: () -> Unit, activity: MainActivity
) {
    Box(modifier = Modifier.fillMaxSize().background(DeepNavy)) {
        if (showCamera) {
            CameraOverlay(activity)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding() 
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HeaderSection(onOpenGallery)
                Spacer(modifier = Modifier.height(24.dp))
                StepProgressCard(steps, goal, onStepChange)
                Spacer(modifier = Modifier.height(24.dp))
                VitalsGrid(vitals)
                Spacer(modifier = Modifier.height(24.dp))
                GoalSettingsCard(goal, onGoalChange)
            }
        }
    }
}

@Composable
fun HeaderSection(onOpenGallery: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Monitor de Signos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = NeonBlue)
            Text("Centro de Salud y Entorno", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        IconButton(
            onClick = onOpenGallery,
            modifier = Modifier.clip(CircleShape).background(GlassWhite)
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = "Galería", tint = Color.White)
        }
    }
}

@Composable
fun StepProgressCard(steps: Int, goal: Int, onStepChange: (Int) -> Unit) {
    val progress = (steps.toFloat() / goal).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(1000))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 10.dp,
                        color = Color.DarkGray.copy(alpha = 0.3f)
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 10.dp,
                        color = NeonGreen,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = NeonGreen)
                    }
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column {
                    Text("PASOS HOY", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(steps.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Meta: $goal", style = MaterialTheme.typography.bodyMedium, color = NeonBlue)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { onStepChange((steps - 10).coerceAtLeast(0)) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = GlassWhite)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "-10 pasos", tint = Color.White)
                }
                
                Text("Ajustar Pasos", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                
                FilledIconButton(
                    onClick = { onStepChange(steps + 10) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = NeonGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "+10 pasos", tint = Color.Black)
                }
            }
        }
    }
}

@Composable
fun VitalsGrid(vitals: VitalsData) {
    val items = listOf(
        VitalItem("Ritmo cardiaco", "${vitals.heartRate?.toInt() ?: "--"} bpm", Icons.Default.Favorite, SoftRed),
        VitalItem("Batería", "${vitals.battery ?: "--"}%", Icons.Default.BatteryChargingFull, NeonBlue),
        VitalItem("Temp", "${vitals.temperature?.toInt() ?: "--"}°C", Icons.Default.Thermostat, Color.Yellow),
        VitalItem("Presión", "${vitals.pressure?.toInt() ?: "--"} hPa", Icons.Default.Cloud, Color.Cyan),
        VitalItem("Humedad", "${vitals.humidity?.toInt() ?: "--"}%", Icons.Default.WaterDrop, Color(0xFF818CF8)),
        VitalItem("Luz", "${vitals.light?.toInt() ?: "--"} lx", Icons.Default.WbSunny, Color(0xFFFDE047)),
        VitalItem("Prox", "${vitals.proximity?.toInt() ?: "--"} cm", Icons.Default.Radar, Color.Magenta),
        VitalItem("Mag", "${vitals.magneticField?.toInt() ?: "--"} μT", Icons.Default.Explore, NeonGreen)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
    ) {
        items(items) { item ->
            VitalMiniCard(item)
        }
    }
}

data class VitalItem(val label: String, val value: String, val icon: ImageVector, val color: Color)

@Composable
fun VitalMiniCard(item: VitalItem) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(item.label, fontSize = 10.sp, color = Color.Gray)
                Text(item.value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun GoalSettingsCard(currentGoal: Int, onGoalChange: (Int) -> Unit) {
    var textValue by remember { mutableStateOf(currentGoal.toString()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GlassWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Ajustar Meta Diaria", fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { textValue.toIntOrNull()?.let { onGoalChange(it) } },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                ) {
                    Text("OK", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CameraOverlay(activity: MainActivity) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    activity.startCamera(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier.fillMaxSize().padding(40.dp).border(2.dp, NeonBlue.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        )
        Text(
            "MODO REMOTO ACTIVADO",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(8.dp),
            color = NeonBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalGallery(onBack: () -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf(listPhotoFiles(context)) }
    var selectedPhoto by remember { mutableStateOf<File?>(null) }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GALERÍA", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DeepNavy)
            )
        },
        containerColor = DeepNavy
    ) { padding ->
        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay capturas disponibles", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.padding(padding).padding(4.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(photos) { file ->
                    GalleryThumbnail(file) { selectedPhoto = file }
                }
            }
        }
    }

    if (selectedPhoto != null) {
        PhotoDetailDialog(
            file = selectedPhoto!!,
            onDismiss = { selectedPhoto = null },
            onDelete = {
                it.delete()
                photos = listPhotoFiles(context)
                selectedPhoto = null
            }
        )
    }
}

@Composable
fun GalleryThumbnail(file: File, onClick: () -> Unit) {
    val bitmap = remember(file) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailDialog(file: File, onDismiss: () -> Unit, onDelete: (File) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp).align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
                IconButton(onClick = { onDelete(file) }, modifier = Modifier.background(SoftRed.copy(0.8f), CircleShape)) {
                    Icon(Icons.Default.Delete, null, tint = Color.White)
                }
            }
        }
    }
}

fun listPhotoFiles(context: android.content.Context): List<File> {
    return context.getExternalFilesDir(null)?.listFiles { f -> f.name.startsWith("photo_") && f.name.endsWith(".jpg") }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()
}
