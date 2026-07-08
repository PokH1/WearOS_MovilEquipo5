package com.amendezm2009.wearappmobile

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.amendezm2009.wearappmobile.ui.theme.WearAppTheme

val HUD_Primary = Color(0xFF00FFD1) 
val HUD_Secondary = Color(0xFF0088FF)
val HUD_Accent = Color(0xFFFF2D55) 
val HUD_Warning = Color(0xFFFFE600) 
val HUD_Bg = Color(0xFF050505) 

class MainActivity : ComponentActivity(), SensorEventListener, MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {
    private lateinit var sensorManager: SensorManager
    private var steps by mutableIntStateOf(0)
    private var stepGoal = 10000
    private val scope = CoroutineScope(Dispatchers.Main)
    private val dataClient by lazy { Wearable.getDataClient(this) }

    private var pressure by mutableStateOf<Float?>(null)
    private var temperature by mutableStateOf<Float?>(null)
    private var heartRate by mutableStateOf<Float?>(null)
    private var heartBeat by mutableStateOf<Float?>(null)
    private var batteryLevel by mutableStateOf<Int?>(null)
    private var humidity by mutableStateOf<Float?>(null)
    private var proximity by mutableStateOf<Float?>(null)
    private var light by mutableStateOf<Float?>(null)
    private var magneticField by mutableStateOf<Float?>(null)

    private var stepSensor: Sensor? = null
    private var pressureSensor: Sensor? = null
    private var temperatureSensor: Sensor? = null
    private var heartRateSensor: Sensor? = null
    private var heartBeatSensor: Sensor? = null
    private var humiditySensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var magneticFieldSensor: Sensor? = null

    private var hasBodySensorsPermission = false
    private var cameraReady by mutableStateOf(false)
    private var isProcessing by mutableStateOf(false)
    private var photoUri by mutableStateOf<String?>(null)
    private var notificationMessage by mutableStateOf<String?>(null)
    private var notificationType by mutableStateOf(NotificationType.INFO)
    private var showNotification by mutableStateOf(false)

    enum class NotificationType { SUCCESS, ERROR, INFO }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) startStepCounting() else showNotification("ACCESO DENEGADO", NotificationType.ERROR)
    }

    private val requestBodySensorsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasBodySensorsPermission = isGranted
        if (isGranted) registerHealthSensors()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initSensors()
        
        setContent {
            WearAppTheme {
                TacticalAppContainer()
            }
        }

        checkAndRequestPermissions()
        getBatteryLevel()
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getDataClient(this).addListener(this)
    }

    private fun initSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        heartBeatSensor = sensorManager.getDefaultSensor(65572)
        humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    @Composable
    fun TacticalAppContainer() {
        val pagerState = rememberPagerState(pageCount = { 3 })
        
        Scaffold(
            modifier = Modifier.fillMaxSize().background(HUD_Bg),
            timeText = { } 
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ScanlineBackground()

                HorizontalPager(state = pagerState) { page ->
                    when (page) {
                        0 -> TacticalDashboard(steps, batteryLevel, stepGoal) { syncSteps() }
                        1 -> BiometricScanner()
                        2 -> OpticalLinkScreen()
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
                    CustomDigitalClock()
                }

                SidePageIndicator(pagerState.currentPage, 3)

                if (showNotification) {
                    TacticalAlert(notificationMessage ?: "", notificationType) { showNotification = false }
                }
            }
        }
    }

    @Composable
    fun ScanlineBackground() {
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.05f)) {
            val scanlineSpacing = 4.dp.toPx()
            for (y in 0..size.height.toInt() step scanlineSpacing.toInt()) {
                drawLine(
                    color = HUD_Primary,
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
        }
    }

    @Composable
    fun CustomDigitalClock() {
        val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while(true) {
                currentTime.longValue = System.currentTimeMillis()
                delay(1000)
            }
        }
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        Text(
            text = sdf.format(java.util.Date(currentTime.longValue)),
            style = MaterialTheme.typography.caption2,
            color = HUD_Primary.copy(alpha = 0.8f),
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp
        )
    }

    @Composable
    fun TacticalDashboard(steps: Int, battery: Int?, goal: Int, onSync: () -> Unit) {
        val stepProgress = (steps.toFloat() / goal).coerceIn(0f, 1f)
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.8f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = ""
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // HUD Crosshair
            Crosshair(pulse)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))
                
                // Medidor Vertical de Pasos
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PASOS", style = MaterialTheme.typography.caption2, color = HUD_Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.width(80.dp).height(6.dp).background(Color.DarkGray.copy(0.3f))) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(stepProgress).background(HUD_Primary))
                    }
                }
                
                Text(steps.toString(), fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("META: $goal", fontSize = 10.sp, color = HUD_Primary.copy(0.6f))

                Spacer(modifier = Modifier.height(15.dp))
                EnergyBar(battery ?: 0)

                Spacer(modifier = Modifier.height(10.dp))

                // Botón de Acción Táctica
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(HUD_Primary.copy(0.1f))
                        .border(1.dp, HUD_Primary.copy(0.5f), RoundedCornerShape(4.dp))
                        .clickable { onSync() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("SINCRONIZAR", fontSize = 10.sp, color = HUD_Primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun BiometricScanner() {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = androidx.wear.compose.foundation.lazy.AutoCenteringParams(itemIndex = 0)
        ) {
            item {
                Text(
                    "DATOS_BIOMÉTRICOS",
                    style = MaterialTheme.typography.caption2,
                    color = HUD_Accent,
                    modifier = Modifier.padding(bottom = 12.dp, top = 30.dp)
                )
            }
            
            item {
                TacticalVitalCard(
                    "RITMO_CARDÍACO",
                    if (heartRate != null) "${heartRate!!.toInt()} BPM" else "ESCANEO...",
                    Icons.Default.Favorite,
                    HUD_Accent,
                    heartRate != null
                )
            }

            item {
                TacticalVitalCard(
                    "TEMP_AMB",
                    "${temperature?.toInt() ?: "--"}°C",
                    Icons.Default.Thermostat,
                    HUD_Warning,
                    temperature != null
                )
            }

            item {
                TacticalVitalCard(
                    "PRESIÓN",
                    "${pressure?.toInt() ?: "--"} hPa",
                    Icons.Default.Cloud,
                    HUD_Secondary,
                    pressure != null
                )
            }

            item {
                TacticalVitalCard(
                    "NIVEL_LUZ",
                    "${light?.toInt() ?: "--"} LX",
                    Icons.Default.WbSunny,
                    Color.White,
                    light != null
                )
            }
            
            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }

    @Composable
    fun TacticalVitalCard(label: String, value: String, icon: ImageVector, color: Color, isActive: Boolean) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 10.dp)
                .drawBehind {
                    drawLine(color.copy(0.3f), Offset(0f, size.height), Offset(size.width, size.height), 2f)
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if(isActive) color else Color.Gray, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(label, fontSize = 8.sp, color = Color.Gray)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if(isActive) Color.White else Color.Gray)
            }
            if (isActive) {
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.size(4.dp).background(color, CircleShape))
            }
        }
    }

    @Composable
    fun OpticalLinkScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (photoUri == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        // Animación de radar
                        val infiniteTransition = rememberInfiniteTransition(label = "")
                        val radius by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = 100f,
                            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = ""
                        )
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.5f, targetValue = 0f,
                            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = ""
                        )
                        Canvas(modifier = Modifier.size(120.dp)) {
                            drawCircle(HUD_Primary, radius = radius, style = Stroke(2f), alpha = alpha)
                        }

                        Button(
                            onClick = { sendTakePhotoMessage() },
                            modifier = Modifier.size(70.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (cameraReady) HUD_Primary else Color.Transparent
                            )
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(indicatorColor = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.Camera, null, tint = if (cameraReady) HUD_Bg else HUD_Primary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        if (cameraReady) "EJECUTAR_CAPTURA" else "INICIAR_ENLACE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HUD_Primary,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).border(1.dp, HUD_Primary, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Button(
                        onClick = { photoUri = null },
                        modifier = Modifier.align(Alignment.BottomCenter).size(36.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = HUD_Accent.copy(0.8f))
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    @Composable
    fun Crosshair(pulse: Float) {
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.2f)) {
            val center = Offset(size.width / 2, size.height / 2)
            val length = 20.dp.toPx() * pulse
            val gap = 40.dp.toPx()

            drawLine(HUD_Primary, Offset(center.x, center.y - gap), Offset(center.x, center.y - gap - length), 3f)
            drawLine(HUD_Primary, Offset(center.x, center.y + gap), Offset(center.x, center.y + gap + length), 3f)
            drawLine(HUD_Primary, Offset(center.x - gap, center.y), Offset(center.x - gap - length, center.y), 3f)
            drawLine(HUD_Primary, Offset(center.x + gap, center.y), Offset(center.x + gap + length, center.y), 3f)
        }
    }

    @Composable
    fun EnergyBar(level: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, null, tint = if(level < 20) HUD_Accent else HUD_Primary, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 1..5) {
                    val active = level >= (i * 20)
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 4.dp)
                            .background(if (active) HUD_Primary else Color.DarkGray.copy(0.3f))
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text("$level%", fontSize = 10.sp, color = Color.White)
        }
    }

    @Composable
    fun SidePageIndicator(current: Int, count: Int) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(end = 4.dp).padding(vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            for (i in 0 until count) {
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = if (i == current) 15.dp else 6.dp)
                        .background(if (i == current) HUD_Primary else Color.Gray.copy(0.3f))
                        .padding(vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    @Composable
    fun TacticalAlert(message: String, type: NotificationType, onDismiss: () -> Unit) {
        val color = when (type) {
            NotificationType.SUCCESS -> HUD_Primary
            NotificationType.ERROR -> HUD_Accent
            NotificationType.INFO -> HUD_Secondary
        }
        LaunchedEffect(Unit) { delay(2000); onDismiss() }
        
        Box(modifier = Modifier.fillMaxSize().zIndex(100f), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(Color.Black.copy(0.9f))
                    .border(1.dp, color)
                    .padding(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MSG_SISTEMA", fontSize = 8.sp, color = color, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(message.uppercase(), color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val ar = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val bs = ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        hasBodySensorsPermission = bs
        if (!ar) requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) else startStepCounting()
        if (!bs) requestBodySensorsPermissionLauncher.launch(Manifest.permission.BODY_SENSORS) else registerHealthSensors()
        registerEnvSensors()
    }

    private fun startStepCounting() {
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun registerHealthSensors() {
        heartRateSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun registerEnvSensors() {
        listOf(pressureSensor, temperatureSensor, humiditySensor, proximitySensor, lightSensor, magneticFieldSensor).forEach {
            it?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> steps = event.values[0].toInt()
            Sensor.TYPE_PRESSURE -> pressure = event.values[0]
            Sensor.TYPE_AMBIENT_TEMPERATURE -> temperature = event.values[0]
            Sensor.TYPE_HEART_RATE -> heartRate = event.values[0]
            Sensor.TYPE_LIGHT -> light = event.values[0]
            Sensor.TYPE_MAGNETIC_FIELD -> magneticField = event.values[0]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun getBatteryLevel() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val status = registerReceiver(null, filter)
        batteryLevel = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    }

    private fun syncSteps() {
        scope.launch {
            try {
                val request = com.google.android.gms.wearable.PutDataMapRequest.create("/steps").apply {
                    dataMap.putInt("steps", steps)
                    pressure?.let { dataMap.putFloat("pressure", it) }
                    temperature?.let { dataMap.putFloat("temperature", it) }
                    heartRate?.let { dataMap.putFloat("heartRate", it) }
                    batteryLevel?.let { dataMap.putInt("battery", it) }
                }.asPutDataRequest()
                dataClient.putDataItem(request).await()
                showNotification("DATOS_ENVIADOS", NotificationType.SUCCESS)
            } catch (_: Exception) {
                showNotification("ERROR_SINCRONIZACIÓN", NotificationType.ERROR)
            }
        }
    }

    fun sendTakePhotoMessage() {
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)
        scope.launch(Dispatchers.IO) {
            try {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isNotEmpty()) {
                    val path = if (!cameraReady) "/take_photo" else "/capture_photo"
                    if (cameraReady) isProcessing = true
                    messageClient.sendMessage(nodes[0].id, path, null).await()
                } else {
                    showNotification("SIN_ENLACE", NotificationType.ERROR)
                }
            } catch (e: Exception) {
                runOnUiThread { showNotification("ERROR_COMUNICACIÓN", NotificationType.ERROR) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onMessageReceived(event: com.google.android.gms.wearable.MessageEvent) {
        when (event.path) {
            "/photo_taken" -> runOnUiThread { cameraReady = true; showNotification("ENLACE_ESTABLECIDO", NotificationType.SUCCESS) }
            "/photo_captured" -> runOnUiThread { isProcessing = false; cameraReady = false; showNotification("CAPTURA_OK", NotificationType.SUCCESS) }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == com.google.android.gms.wearable.DataEvent.TYPE_CHANGED) {
                when (event.dataItem.uri.path) {
                    "/photo_image" -> {
                        val asset = event.dataItem.assets["photo"]
                        asset?.let {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val inputStream = Wearable.getDataClient(this@MainActivity).getFdForAsset(it).await().inputStream
                                    val tempFile = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                                    tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                                    runOnUiThread { photoUri = tempFile.absolutePath }
                                } catch (e: Exception) {
                                    runOnUiThread { showNotification("IMG_CORRUPTA", NotificationType.ERROR) }
                                }
                            }
                        }
                    }
                    "/steps" -> {
                        val dataMap = com.google.android.gms.wearable.DataMapItem.fromDataItem(event.dataItem).dataMap
                        runOnUiThread {
                            if (dataMap.containsKey("steps")) steps = dataMap.getInt("steps")
                            if (dataMap.containsKey("goal")) stepGoal = dataMap.getInt("goal")
                        }
                    }
                }
            }
        }
    }

    private fun showNotification(message: String, type: NotificationType) {
        notificationMessage = message
        notificationType = type
        showNotification = true
    }
}
