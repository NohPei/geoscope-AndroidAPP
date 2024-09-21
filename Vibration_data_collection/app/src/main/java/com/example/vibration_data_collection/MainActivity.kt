package com.example.vibration_data_collection

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val devices = mutableStateListOf<Device>()
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private val scannedDevices = mutableListOf<Pair<String, String>>()
    private var mqttClient: MqttClient? = null
    private val subscribedTopics = mutableSetOf<String>()
    private val broker = Broker()
    var sliderValue: Int by mutableIntStateOf(199) // 提升 sliderValue 为类成员
    var isPlaying: Boolean by mutableStateOf(true) // 提升 isPlaying 为类成员
    private var showSlider by mutableStateOf(false)
    var plotLength by mutableIntStateOf(3)  // Plot length (Frame)
    val files = mutableStateListOf<CsvFile>() // 修改为 CsvFile
    var recordingFile: String = ""
    var expandedFile: String = ""
    var recordingSegment: String = ""
    var recording = false
    private var selectedTab by mutableIntStateOf(0) // 默认选中 "Connections"
    // ====================
    // Setup section
    // ====================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //App starting step 1: check WiFi permission
        if (checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
                ), 1
            )
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        //App starting step 2: create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "Notification channel"
            val channelName = "Notification channel"
            val channelDescription = "Notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        //App starting step 3: scan brokers and sensors under local WiFi
        handler = Handler(Looper.getMainLooper())
        runnable = Runnable {
            scanDevices(this@MainActivity, scannedDevices, broker)
            updateDeviceInstances(this@MainActivity, devices, scannedDevices, broker)
            handler.postDelayed(runnable, 5000)
        }
        handler.post(runnable)

        //Use composable functions to define monitors and UI
        setContent {
            MonitorBroker(broker)
            MonitorDevicesReady(devices)
            NavigationBar() //The top layer UI component
        }
    }
    // ====================
    // Top layer UI component section
    // ====================
    //Top layer UI component, including 2 pages: Net & View
    @Composable
    fun NavigationBar() {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .background(Color(0xFFA99FC1))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterVertically)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    NavigationItem(
                        icon = Icons.Filled.Home,
                        label = "Net",
                        isSelected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    Spacer(modifier = Modifier.weight(1f))
                    NavigationItem(
                        icon = Icons.Filled.Create,
                        label = "View",
                        isSelected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            // Update the entryList of each device instance, so that the chart will update once switching to the recording page
                            devices.forEach { it.entryListUpdated = true }
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))

                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> NetPage(devices, broker)
                1 -> ViewPage(devices)
            }
        }
    }

    //A secondary component used in the Top layer
    @Composable
    fun NavigationItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Icon(icon, contentDescription = label)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = if (isSelected) Color.Blue else Color.Black)
        }
    }

    // ====================
    // View page UI component section
    // ====================
    //net page's top layer component, including WiFiStatusCard, BrokerStatusCard, and device list
    @Composable
    fun NetPage(devices: List<Device>, broker: Broker) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(//Left half screen
                    modifier = Modifier
                        .weight(0.4f)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "  WiFi Status",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    WifiStatusCard(devices)
                    Text(
                        text = "  Broker Status",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    BrokerStatusCard(broker)
                }
                HorizontalDivider(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(),
                    color = Color.Gray.copy(alpha = 0.5f)
                )
                VerticalDivider()
                Column(//Right half screen
                    modifier = Modifier
                        .weight(0.6f)
                        .padding(16.dp)
                        .padding(end = 84.dp)
                ) {
                    Text(
                        text = "Connected Sensors",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(devices) { device ->
                            DeviceCard(device)
                        }
                    }
                }
            }
        }
    }
    //This card monitors and shows WiFi status
    @Composable
    fun WifiStatusCard(devices: List<Device>) {
        val context = LocalContext.current
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var ssid by remember { mutableStateOf(wifiManager.connectionInfo.ssid) }
        var ipAddress by remember { mutableStateOf(Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)) }
        val connectedDevicesCount by rememberUpdatedState(newValue = devices.size)
        var isConnected by remember { mutableStateOf(wifiManager.connectionInfo.networkId != -1) } // {{ edit_1 }}

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = remember {
            object : ConnectivityManager.NetworkCallback() {//Manage the states of WiFi
                override fun onAvailable(network: Network) {
                    // Update the state when network is available
                    ssid = wifiManager.connectionInfo.ssid
                    ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
                    isConnected = true
                }

                override fun onLost(network: Network) {
                    // Update the state when network is lost
                    ipAddress = "N/A"
                    isConnected = false
                    sendNotificationAndPlayAudio(
                        this@MainActivity,
                        "WiFi Disconnected",
                        "The WiFi has disconnected.",
                        "aaaaaa"
                    )
                }
            }
        }

        DisposableEffect(Unit) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            onDispose {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            }
        }

        Card(//Declare the UI of the card, with respect to the states of WiFi managed above
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = if (isConnected) "WiFi Connected!" else "Not Connected!")
                    Text(text = "Phone IP: $ipAddress")
                    Text(text = "Connected Sensors: $connectedDevicesCount")
                }
                Image(
                    painter = painterResource(id = R.drawable.router),
                    contentDescription = "Delete",
                    modifier = Modifier
                        .weight(0.25f) // 指定weight
                        .size(72.dp) // 修改为一半的大小
                        .padding(top = 16.dp,end=8.dp)
                )
            }
        }
    }
    //Shows broker state. The monitoring work is implemented in the function 'scanDevices', which is used every 5 seconds
    @Composable
    fun BrokerStatusCard(broker: Broker) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Broker: ${broker.name}")
                    Text(text = "Broker IP: ${broker.ip}")
                    Text(text = broker.status)
                }
                Image(
                    painter = painterResource(id = R.drawable.laptop),
                    contentDescription = "Delete",
                    modifier = Modifier
                        .weight(0.25f)
                        .size(72.dp)
                        .padding(top = 16.dp,end=8.dp)
                )
            }
        }
    }
    //For each instance in the list 'devices', generate a DeviceCard component
    //Contains status of the device, and a button to configure device parameters
    @Composable
    fun DeviceCard(device: Device) {
        var showDialog by remember { mutableStateOf(false) }
        var gainInput by remember { mutableStateOf(device.gain.toString()) }
        var rateInput by remember { mutableStateOf(device.rate.toString()) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                    Text(
                        text = device.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(
                    modifier = Modifier
                        .height(1.dp)
                        .fillMaxWidth(),
                    color = Color.Gray.copy(alpha = 0.5f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(0.5f)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Gain: ${device.gain}",
                                modifier = Modifier.weight(0.5f)
                            )
                            Text(
                                text = "Rate: ${device.rate}",
                                modifier = Modifier.weight(0.5f),
                                textAlign = TextAlign.End
                            )
                        }
                        Text(text = device.process)
                    }
                    Spacer(modifier = Modifier.weight(0.15f))
                    Button(
                        onClick = {
                            gainInput = device.gain.toString()
                            rateInput = device.rate.toString()
                            showDialog = true
                        },
                        modifier = Modifier
                            .weight(0.35f)
                            .padding(4.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        Text("Config")
                    }
                }
                //When the button is clicked, pop out the configuring dialogue
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("Configure Device") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = gainInput,
                                    onValueChange = { gainInput = it },
                                    label = { Text("Gain") }
                                )
                                OutlinedTextField(
                                    value = rateInput,
                                    onValueChange = { rateInput = it },
                                    label = { Text("Rate") }
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                showDialog = false
                                if (recording) {
                                    selectedTab = 1
                                    recording = false
                                    files.forEach { file ->
                                        file.isRecording.value = false
                                    }
                                    recordingFile = ""
                                    recordingSegment = ""
                                }
                                val newGain = gainInput.toDoubleOrNull() ?: device.gain
                                val newRate = rateInput.toLongOrNull() ?: device.rate
                                Thread {
                                    device.setADCGain(newGain)
                                    device.setADCRate(newRate)
                                    device.gain = device.fetchADCGain(this@MainActivity)
                                    device.rate = device.fetchADCRate()
                                    device.process = "Running ..."
                                    device.ready = true
                                }.start()
                            }) {
                                Text("Send")
                            }
                        },
                        dismissButton = {
                            Button(onClick = { showDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    // ====================
    // Monitors section in the view page
    // ====================
    private fun subscribeToTopic(topic: String) {
        mqttClient?.subscribe(topic)
        subscribedTopics.add(topic)
    }

    private fun unsubscribeAll() {
        subscribedTopics.forEach { topic ->
            mqttClient?.unsubscribe(topic)
        }
        subscribedTopics.clear()
    }
    //code within LaunchedEffect will automatically run whenever the broker.reachable changes, so the app reacts to broker change
    @Composable
    fun MonitorBroker(broker: Broker) {
        LaunchedEffect(broker.reachable) {
            if (mqttClient != null) {
                if (!broker.reachable) {
                    try {
                        unsubscribeAll()
                        mqttClient?.disconnect()
                    } catch (e: MqttException) {
                        e.printStackTrace()
                        sendNotificationAndPlayAudio(
                            this@MainActivity,
                            "MQTT Disconnected",
                            "The broker has disconnected.",
                            "aaahohoho"
                        )
                    }
                    mqttClient = null
                }
            } else {
                if (broker.reachable) {
                    val brokerUrl = "tcp://${broker.ip}:1883"
                    val persistence =
                        MqttDefaultFilePersistence(this@MainActivity.getExternalFilesDir(null)?.absolutePath)
                    mqttClient = MqttClient(brokerUrl, "geoscope-app", persistence)
                    try {
                        mqttClient?.connect(MqttConnectOptions().apply {

                        })
                        mqttClient?.setCallback(object : MqttCallback {
                            override fun connectionLost(cause: Throwable?) {
                                println("Connection lost: ${cause?.message}")
                            }
                            //Because of the code below, whenever an MQTT message arrives, the function  mqttMessageHandler will run
                            override fun messageArrived(topic: String?, message: MqttMessage?) {
                                Thread {
                                    mqttMessageHandler(topic, message, devices, this@MainActivity)
                                }.start()
                            }

                            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                                //The app doesn't really deliver message
                            }
                        })
                        println("MQTT Client connected to ${broker.ip}")
                        updateBrokerIPForAllDevices(devices, broker)
                        devices.forEach { device ->
                            subscribeToTopic(device.topic)
                        }
                    } catch (e: MqttException) {
                        e.printStackTrace()
                        mqttClient = null
                    }
                }
            }
        }
    }
    //code within LaunchedEffect will automatically run whenever any instance's 'ready' property in the device list changes, so the app reacts to the event that a new sensor is ready
    @Composable
    fun MonitorDevicesReady(devices: List<Device>) {
        val allReady by remember {
            derivedStateOf { devices.all { it.ready } }
        }

        LaunchedEffect(allReady) {
            if (mqttClient != null) {
                devices.forEach { device ->

                    if (device.ready && !subscribedTopics.contains(device.topic)) {
                        subscribeToTopic(device.topic)
                    }
                }
                val topicsToUnsubscribe = subscribedTopics.filter { topic ->
                    devices.none { it.topic == topic }
                }
                topicsToUnsubscribe.forEach { topic ->
                    mqttClient?.unsubscribe(topic)
                }
                subscribedTopics.removeAll(topicsToUnsubscribe.toSet())
            }
        }
    }
    //=================
    //=================
    //Record page section
    //=================
    //=================
    @Composable
    fun ViewPage(devices: List<Device>) {
        var showSettingsDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            updateFileList(this@MainActivity, files, this@MainActivity)
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(0.45f)) {
                Box(modifier = Modifier.weight(0.83f)) {
                    LineChart(devices)
                }
                if (showSlider) {//when paused real-time plot, show the slider
                    Box(
                        modifier = Modifier
                            .weight(0.06f)
                            .padding(1.dp)
                    ) {
                        Slider(
                            value = sliderValue.toFloat(),
                            onValueChange = {
                                sliderValue = it.toInt()
                                devices.forEach { device ->
                                    device.extractEntries(plotLength, (199 - sliderValue), true)
                                }
                            },
                            valueRange = 0f..199f,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(0.06f))
                }

                Box(
                    modifier = Modifier
                        .weight(0.12f)
                        .padding(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                isPlaying = !isPlaying
                                showSlider = !isPlaying
                                if (isPlaying) {
                                    sliderValue = 199
                                } else {
                                    devices.forEach { device ->
                                        device.backupBuffer()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(if (isPlaying) "Pause" else "Play")
                        }
                        Button(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text("Display settings")
                        }
                    }
                }
            }

            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 2.dp,
                color = Color.Gray
            )
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxSize()
                    .padding(end = 72.dp)
            ) {
                Column {
                    LazyColumn(modifier = Modifier.weight(0.88f)) {
                        item {
                            AddFile()
                        }
                        item {
                            HorizontalDivider(
                                thickness = 2.dp,
                                color = Color.Gray
                            )
                        }
                        item {
                            FileList(files)
                        }
                    }
                    HorizontalDivider()

                }
            }
        }

        if (showSettingsDialog) {
            DisplaySettingsDialog(devices, onDismiss = { showSettingsDialog = false })
        }
    }
    //Secondary component in the record page: LineChart
    //Contains a time domain chart and a spectrum
    //The entry list here is extracted from the device instance's dataFrames property, using the function extractEntries.
    // if the app is plotting real time, extract the newest frames to the dataset
    // otherwise, backup the current frame list, and refer to the slider value to determine which frames to extract
    @Composable
    fun LineChart(devices: List<Device>) {
        val context = LocalContext.current
        val lineChart = remember {
            com.github.mikephil.charting.charts.LineChart(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                isDragEnabled = true
                setPinchZoom(true)
                description.text = "Time Domain Signal"
            }
        }

        val fftChart = remember {
            com.github.mikephil.charting.charts.LineChart(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                isDragEnabled = true
                setPinchZoom(true)
                description.text = "Spectrum (dB)"
            }
        }

// Whenever the any instance's 'entryListUpdated' property in the device list changes, update the lineChart
        val anyEntryListUpdated by remember {
            derivedStateOf { devices.any { it.entryListUpdated } } // {{ edit_1 }}
        }
        val currentSliderValue by rememberUpdatedState(sliderValue)

        val colors = listOf(
            Color.Blue, Color.Red, Color.Green, Color.Cyan,
            Color.Magenta, Color.Yellow, Color.Gray, Color.Black,
            Color(0xFF8B4513), Color(0xFF00FF7F), Color(0xFFFFA500), Color(0xFF4682B4),
            Color(0xFF6A5ACD), Color(0xFF708090), Color(0xFFFF1493), Color(0xFF7FFF00)
        )

        // To update a line in the chart, just modify the dataset (which is a entry list)
        LaunchedEffect(anyEntryListUpdated, currentSliderValue) {
            if (anyEntryListUpdated || currentSliderValue != sliderValue) {
                // The time domain signal chart use hte dataset 'lineDataSets'
                val lineDataSets = devices.mapIndexedNotNull { index, device ->
                    if (device.entryList.isNotEmpty() && device.plotSignal) {
                        LineDataSet(device.entryList, device.name.removePrefix("GEOSCOPE-")).apply {
                            color = colors[index % colors.size].toArgb()
                            valueTextColor = Color.Black.toArgb()
                            lineWidth = if (device.highlightSignal) 2f else 1f
                            setDrawCircles(false)
                            if (!device.highlightSignal) {
                                setColor(colors[index % colors.size].toArgb(), 64) // 透明
                            } else {
                                setColor(colors[index % colors.size].toArgb(), 255) // 透明
                            }
                        }
                    } else {
                        null
                    }
                }

                val lineData = LineData(lineDataSets)
                lineChart.data = lineData
                lineChart.description.isEnabled = true
                lineChart.axisRight.isEnabled = false
                lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
                lineChart.axisLeft.axisMinimum = -2048f
                lineChart.axisLeft.axisMaximum = 2048f
                fftChart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()} Hz"
                    }
                }
                lineChart.invalidate()

                // The spectrum chart use the 'fftDataSets'
                val fftDataSets = devices.mapIndexedNotNull { index, device ->
                    if (device.entryList.isNotEmpty() && device.plotSpectrum) {
                        val fftEntries = performFFT(device.entryList)
                        val maxIndex = fftEntries.size - 1
                        val normalizedFftEntries = fftEntries.mapIndexed { index, entry ->
                            Entry(index.toFloat() / maxIndex * device.rate, entry.y)
                        }
                        LineDataSet(normalizedFftEntries, device.name.removePrefix("GEOSCOPE-")).apply {
                            color = colors[index % colors.size].toArgb()
                            valueTextColor = Color.Black.toArgb()
                            lineWidth = if (device.highlightSpectrum) 2f else 1f
                            setDrawCircles(false)
                            if (!device.highlightSpectrum) {
                                setColor(colors[index % colors.size].toArgb(), 64) // 透明
                            } else {
                                setColor(colors[index % colors.size].toArgb(), 255) // 透明
                            }
                        }
                    } else {
                        null
                    }
                }

                val fftData = LineData(fftDataSets)
                fftChart.data = fftData
                fftChart.description.isEnabled = true
                fftChart.axisRight.isEnabled = false
                fftChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
                fftChart.axisLeft.axisMinimum = -0f
                fftChart.axisLeft.axisMaximum = 100f

                fftChart.invalidate()

                // Reset the flag, so that next time it becomes true, the charts will update again
                devices.forEach { it.entryListUpdated = false }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { lineChart }, modifier = Modifier.weight(0.5f))
            AndroidView(factory = { fftChart }, modifier = Modifier.weight(0.5f))
        }
    }
    //Secondary component: DisplaySettingsDialog
    @Composable
    fun DisplaySettingsDialog(devices: List<Device>, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Display Settings") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f)
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth()
                        ) {
                            Text("Plot length", modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = plotLength.toFloat(),
                                onValueChange = { plotLength = it.toInt() },
                                valueRange = 1f..10f,
                                steps = 8, // 1 to 10, so 8 steps in between
                                modifier = Modifier.weight(2f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$plotLength Frame", modifier = Modifier.weight(1f)) // Add weight
                        }
                    }
                    item {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color.LightGray
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Sensor ID",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Plot Signal",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Highlight Signal",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Plot Spectrum",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Highlight Spectrum",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    item {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color.LightGray
                        )
                    }
                    //For each sensor, decide whether to play or highlight
                    items(devices) { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                device.name.removePrefix("GEOSCOPE-"),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Checkbox(
                                checked = device.plotSignal,
                                onCheckedChange = { isChecked ->
                                    device.plotSignal = isChecked
                                    device.entryListUpdated = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = device.highlightSignal,
                                onCheckedChange = { device.highlightSignal = it },
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = device.plotSpectrum,
                                onCheckedChange = { isChecked ->
                                    device.plotSpectrum = isChecked
                                    device.entryListUpdated = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = device.highlightSpectrum,
                                onCheckedChange = { device.highlightSpectrum = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color.LightGray
                        )

                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
            properties = DialogProperties(
                usePlatformDefaultWidth = false // Disable default width
            )
        )
    }

    //=================
    //=================
    //Record page section - files and recording
    //=================
    //=================
    @Composable
    fun AddFile() {//When AddFile is clicked, pop a dialogue to enter the new file's name and comment
        val context = LocalContext.current
        val fileName = remember { mutableStateOf("") }
        val comment = remember { mutableStateOf("") }
        var showDialog by remember { mutableStateOf(false) }

        // Check the list 'files', and display a row for each instance. Run 'updateFileList' to synchronize the UI with the files in external storage
        LaunchedEffect(Unit) {
            updateFileList(context, files, this@MainActivity)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add File")
            Spacer(modifier = Modifier.width(8.dp))
            Text("New File")
        }

        // 对话框
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Create New File") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = fileName.value,
                            onValueChange = { fileName.value = it },
                            label = { Text("File Name") }
                        )
                        OutlinedTextField(
                            value = comment.value,
                            onValueChange = { comment.value = it },
                            label = { Text("Comment") }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val finalFileName =
                            if (fileName.value.isEmpty()) "Untitled" else fileName.value.replace("\n", " ")
                        val dir = context.getExternalFilesDir(null)
                        var newFile = File(dir, "$finalFileName.csv")
                        var index = 1
                        while (newFile.exists()) {
                            newFile = File(dir, "$finalFileName - $index.csv")
                            index++
                        }

                        if (newFile.createNewFile()) {
                            println("File created at: ${newFile.absolutePath}")

                            // 使用用户输入的 Comment
                            val templateContent = """
                            File Property,File Value,Segment Property,Segment Value,Sensor Property,Sensor Value
                            Comment,${comment.value.replace("\n", " ")},,,,,
                            
                        """.trimIndent()

                            newFile.bufferedWriter().use { writer ->
                                writer.write(templateContent)
                            }

                            val csvFile = CsvFile(newFile)
                            files.add(csvFile)
                            updateFileList(context, files, this@MainActivity) // 更新文件列表
                            showDialog = false // 关闭对话框
                        } else {
                            Toast.makeText(
                                context,
                                "The file name already exists",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    //Display a row for each csvFile instance in list 'files
    //each row can be expanded to view the segment list and several operating buttons
    @Composable
    fun FileList(files: MutableList<CsvFile>) {
        var showDeleteDialog by remember { mutableStateOf(false) }
        var fileToDelete by remember { mutableStateOf<CsvFile?>(null) }
        var showRecordDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Column {
            files.forEach { file ->
                val fileName = file.file.nameWithoutExtension
                val fileDate = SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).format(Date(file.file.lastModified()))
                val fileSize = file.file.length()

                Column(modifier = Modifier.padding(8.dp)) {
                    Column(
                        modifier = Modifier
                            .background(if (file.isRecording.value) Color.Yellow else Color.Transparent)
                            .clickable {

                                if (!file.isExpanded.value) {
                                    files.forEach { file ->
                                        file.isExpanded.value = false
                                    }
                                    file.isExpanded.value = true
                                    expandedFile = file.file.name
                                } else {
                                    file.isExpanded.value = false
                                    expandedFile = ""
                                }
                            }
                    ) {
                        Text(fileName)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(fileDate)
                            Text("${fileSize / 1024} KB")
                        }
                    }

                    // 扩展的内容
                    if (file.isExpanded.value) {
                        file.parseCsvFile() // Parse the file, to synchronize properties of the csvFile instance with the file stored in the disk
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (file.isRecording.value) Color.Yellow else Color.Transparent)
                            ) {
                                Text(file.comment)
                            }
                            SegmentList(file)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // Delete button
                                Image(
                                    painter = painterResource(id = R.drawable.delete),
                                    contentDescription = "Delete",
                                    modifier = Modifier
                                        .weight(0.25f)
                                        .clickable {
                                            fileToDelete = file
                                            if (!file.isRecording.value) {
                                                showDeleteDialog = true
                                            }
                                        }
                                        .size(36.dp)
                                        .align(Alignment.Bottom)
                                )

                                // Delete dialog
                                if (showDeleteDialog && fileToDelete != null) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteDialog = false },
                                        title = { Text("Confirm Delete") },
                                        text = { Text("Are you sure you want to delete this file?") },

                                        dismissButton = {
                                            Button(onClick = { showDeleteDialog = false }) {
                                                Text("No")
                                            }
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                expandedFile = ""
                                                fileToDelete!!.file.delete()
                                                updateFileList(
                                                    this@MainActivity,
                                                    files,
                                                    this@MainActivity
                                                )
                                                showDeleteDialog = false
                                            }) {
                                                Text("Yes")
                                            }
                                        }
                                    )
                                }

                                //record button
                                //record starting procedure: pop a dialogue to decide file name, generate a segment header according to the device list,
                                //write the segment header into the file, enable the recording flag so the MQTT handler function will stream data into the file
                                Button(
                                    onClick = {
                                        if (file.isRecording.value || !files.any { it.isRecording.value }) {
                                            if (!file.isRecording.value) {
                                                synchronizeAllSensors(devices)
                                                showRecordDialog = true
                                            }
                                            else {
                                                file.isRecording.value = false
                                                recording = false
                                                recordingFile = ""
                                                recordingSegment = ""
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(2f)
                                        .align(Alignment.CenterVertically)
                                        .padding(horizontal = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        contentColor = when {
                                            file.isRecording.value -> Color.Yellow
                                            files.any { it.isRecording.value } -> Color.Gray.copy(
                                                alpha = 0.5f
                                            )
                                            else -> Color.White
                                        }
                                    )
                                )
                                {
                                    Text(if (file.isRecording.value) "Stop" else "Record")
                                }
                                if (showRecordDialog) {
                                    var segName by remember { mutableStateOf("") }
                                    var comment by remember { mutableStateOf("") }

                                    AlertDialog(
                                        onDismissRequest = { showRecordDialog = false },
                                        title = { Text("New Segment") },
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(0.35f)
                                                ) {
                                                    OutlinedTextField(
                                                        value = segName,
                                                        onValueChange = { segName = it },
                                                        label = { Text("Segment Name") }
                                                    )
                                                    OutlinedTextField(
                                                        value = comment,
                                                        onValueChange = { comment = it },
                                                        label = { Text("Comment") }
                                                    )
                                                }
                                                Spacer(modifier = Modifier.weight(0.025f))
                                                VerticalDivider()
                                                Spacer(modifier = Modifier.weight(0.025f))
                                                LazyColumn(
                                                    modifier = Modifier.weight(0.6f)
                                                ) {
                                                    item {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically // Center align vertically
                                                        ) {
                                                            Text(
                                                                "Sensor",
                                                                modifier = Modifier.weight(1f),
                                                                textAlign = TextAlign.Center
                                                            )
                                                            Text(
                                                                "Gain",
                                                                modifier = Modifier.weight(1f),
                                                                textAlign = TextAlign.Center
                                                            )
                                                            Text(
                                                                "Sample Rate",
                                                                modifier = Modifier.weight(1f),
                                                                textAlign = TextAlign.Center
                                                            )
                                                            Text(
                                                                "Record",
                                                                modifier = Modifier.weight(1f),
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                    devices.forEach { device ->
                                                        if (device.ready) {
                                                            item {
                                                                Column {
                                                                    HorizontalDivider()
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically // Center align vertically
                                                                    ) {
                                                                        Text(
                                                                            device.name.removePrefix("GEOSCOPE-"),
                                                                            modifier = Modifier.weight(1f),
                                                                            textAlign = TextAlign.Center
                                                                        )
                                                                        Text(
                                                                            device.gain.toString(),
                                                                            modifier = Modifier.weight(1f),
                                                                            textAlign = TextAlign.Center
                                                                        )
                                                                        Text(
                                                                            device.rate.toString(),
                                                                            modifier = Modifier.weight(1f),
                                                                            textAlign = TextAlign.Center
                                                                        )
                                                                        Checkbox(
                                                                            checked = device.record,
                                                                            onCheckedChange = { isChecked ->
                                                                                device.record = isChecked
                                                                            },
                                                                            modifier = Modifier.weight(1f)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        properties = DialogProperties(
                                            usePlatformDefaultWidth = false // Disable default width
                                        ),
                                        confirmButton = {
                                            Button(onClick = {
                                                if (devices.any { it.record }) {
                                                    var finalSegName = if (segName.isEmpty()) "Segment - 1" else segName
                                                    var index = 1
                                                    while (file.segmentList.any { it.name == finalSegName }) {
                                                        finalSegName = if (segName.isEmpty()) "Segment - $index" else "$segName - $index"
                                                        index++
                                                    }
                                                    recordingSegment = finalSegName
                                                    file.isRecording.value = true
                                                    recordingFile = file.file.name
                                                    val segment = generateSegment(
                                                        finalSegName.replace("\n", " "),
                                                        comment.replace("\n", " "),
                                                        devices
                                                    )
                                                    file.writeHeader(segment)
                                                    file.parseCsvFile()
                                                    recording = true

                                                    showRecordDialog = false
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "No sensor selected.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }) {
                                                Text("Confirm")
                                            }
                                        },
                                        dismissButton = {
                                            Button(onClick = { showRecordDialog = false }) {
                                                Text("Cancel")
                                            }
                                        }
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (devices.isNotEmpty()) {
                                            if (!files.any { it.isRecording.value }) {
                                                // Generate a unique segment name with an index
                                                var segmentName = "Snapshot"
                                                var index = 1
                                                while (file.segmentList.any { it.name == segmentName }) {
                                                    segmentName = "Snapshot - $index"
                                                    index++
                                                }

                                                val segment = generateSegment(
                                                    segmentName,
                                                    "200 recent frames saved.",
                                                    devices,
                                                    true
                                                )
                                                file.writeHeader(segment)
                                                //save the whole ring buffers of all sensors
                                                devices.forEach { device ->
                                                    for (i in 0 until device.bufferSize) {
                                                        val dataFrame = device.dataFrames[(device.currentIndex + i) % device.bufferSize]
                                                        if (dataFrame != null) {
                                                            file.writeFrame(dataFrame, segmentName, device.name)
                                                        }
                                                    }
                                                }
                                                //parse again to update UI
                                                file.parseCsvFile()
                                                updateFileList(this@MainActivity, files, this@MainActivity)
                                            } else{
                                                Toast.makeText(
                                                    context,
                                                    "Another file is recording.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "No sensor connected.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(2f)
                                        .align(Alignment.CenterVertically)
                                        .padding(horizontal = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        contentColor = when {
                                            file.isRecording.value -> Color.Gray.copy(alpha = 0.5f)
                                            files.any { it.isRecording.value } -> Color.Gray.copy(
                                                alpha = 0.5f
                                            )
                                            else -> Color.White
                                        }
                                    )
                                ) {
                                    Text("Q. Save")
                                }
                                //share button
                                Image(
                                    painter = painterResource(id = R.drawable.ios_share),
                                    contentDescription = "Share",
                                    modifier = Modifier
                                        .weight(0.25f)
                                        .clickable {
                                            val fileUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.provider",
                                                file.file
                                            )
                                            val shareIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                                type = "text/csv"
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(shareIntent, "Share CSV file")
                                            )
                                        }
                                        .size(36.dp)
                                        .align(Alignment.Bottom)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 2.dp,
                    color = Color.Gray
                )
            }
        }
    }

    //When a file row is expanded, it shows a SegmentList component
    //parse the file to update the instance's properties, then display them
    @Composable
    fun SegmentList(csvFile: CsvFile) {
        Column {
            HorizontalDivider()
            csvFile.segmentList.forEachIndexed { index, segment ->
                var isExpanded by remember { mutableStateOf(false) }
                val isLastSegment = index == csvFile.segmentList.size - 1 && csvFile.isRecording.value
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .background(if (isLastSegment) Color.Yellow else Color.Transparent)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(segment.name)
                        Text(segment.startTime)
                    }
                    Text(segment.comment)
                    if (isExpanded) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Sensor",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "Gain",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "Sample Rate",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                            segment.sensorList.forEach { sensorData ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        sensorData.name.removePrefix("GEOSCOPE-"),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        sensorData.gain.toString(),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        sensorData.rate.toString(),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}

