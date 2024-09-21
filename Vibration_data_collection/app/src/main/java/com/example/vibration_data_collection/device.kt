package com.example.vibration_data_collection

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.mikephil.charting.data.Entry
import java.net.InetAddress
import java.net.Socket
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.locks.ReentrantLock


class Broker {
    var reachable by mutableStateOf(false)
    var name by mutableStateOf("Not found")
    var ip by mutableStateOf("Not found")
    var status by mutableStateOf("Please start a broker on port 1883!")
}


@SuppressLint("MutableCollectionMutableState")
class Device(//construct instance with name and IP
    val name: String,
    private val deviceIP: String,
) {
    var brokerIP by mutableStateOf("Not fetched yet.")
    var gain by mutableDoubleStateOf(0.0)
    var rate by mutableLongStateOf(0)
    var process by mutableStateOf("Adding device")
    var ready by mutableStateOf(false)
    val topic: String = "geoscope/node1/${name.removePrefix("GEOSCOPE-")}"
    val lock = ReentrantLock(true) //each device instance contains a lock, making sure that different Telnet sending threads do not mix with each other, and run in the right sequence
    var plotSignal by mutableStateOf(true) // Plot signal or not
    var highlightSignal by mutableStateOf(false) // Highlight signal or not
    var plotSpectrum by mutableStateOf(true) // Plot spectrum or not
    var highlightSpectrum by mutableStateOf(false) // Highlight spectrum or not
    var record by mutableStateOf(false) //Record data from this device or not
    // Ring buffer for data frames, memorize 200 recent frames
    val bufferSize = 200
    val dataFrames = Array<DataFrame?>(bufferSize) { null }
    var currentIndex = 0
    //A copy of ring buffer that is not updating with MQTT messages, used to display previous signal when the app is not real-time plotting.
    private var staticCurrentIndex = 0
    private var staticDataFrames = Array<DataFrame?>(bufferSize) { null }

    //Current plotting dataset and its updating flag, extracted from the ring buffer with function extractEntryList
    var entryList by mutableStateOf(mutableListOf<Entry>())
    var entryListUpdated by mutableStateOf(false) // {{ edit_1 }} 添加布尔变量标记更新


    fun fetchADCGain(context: Context): Double { //get gain configured in the sensor
        val handler = Handler(Looper.getMainLooper())
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                handler.post {
                    Toast.makeText(
                        context,
                        "Configuring sensors under a new WiFi might take minutes.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, 5000)

        lock.lock()
        try {
            ready = false
            process = "Fetching gain configured in device ... "
            sendMultipleCommands(deviceIP, "exit", "adc")
            val response: String? = sendTelnetCommandWithLateResponse(deviceIP, "gain")
            return response?.let {
                it.substringAfter("Gain: ").trim().toDoubleOrNull() ?: 0.0
            } ?: 0.0
        } finally {
            lock.unlock()
            timer.cancel()
        }
    }

    fun fetchADCRate(): Long {//get sample rate configured in the sensor
        lock.lock()
        try {
            ready = false
            process = "Fetching rate configured in device ... "
            sendMultipleCommands(deviceIP, "exit", "adc")
            val response: String? = sendTelnetCommandWithLateResponse(deviceIP, "rate")
            return response?.let {
                it.substringAfter("Sample Rate: ").substringBefore(" Hz").toLongOrNull() ?: 0
            } ?: 0
        } finally {
            lock.unlock()
        }
    }

    fun fetchBrokerIP(): String? {//get broker IP configured in the sensor
        lock.lock()
        try {
            ready = false
            process = "Fetching broker IP configured in device ... "
            sendMultipleCommands(deviceIP, "exit", "mqtt")
            val response: String? = sendTelnetCommandWithLateResponse(deviceIP, "ip")
            return response?.substringAfter("Broker IP: ")?.trim()
        } finally {
            lock.unlock()
        }
    }

    fun setADCGain(setGain: Double) {//set new gain
        lock.lock()
        try {
            ready = false
            process = "Sending new gain ... "
            sendMultipleCommands(deviceIP, "exit", "adc", "gain $setGain")
        } finally {
            lock.unlock()
        }
    }

    fun setADCRate(setRate: Long) {//set new sample rate
        lock.lock()
        try {
            ready = false
            process = "Sending new rate ... "
            sendMultipleCommands(deviceIP, "exit", "adc", "rate $setRate")
        } finally {
            lock.unlock()
        }
    }

    fun sendBrokerIP(setBrokerIP: String?) {//send the right broker IP to sensors
        lock.lock()
        try {
            ready = false
            process = "Sending new broker IP ... "
            sendMultipleCommands(deviceIP, "exit", "mqtt", "ip $setBrokerIP", "commit", "save")
        } finally {
            lock.unlock()
        }
    }

    fun synchronize() {//synchronize the pace of data collection of all sensors
        lock.lock()
        try {
            process = "Clearing buffer and resetting timestamp ... "
            sendMultipleCommands(deviceIP, "exit", "synchronize")
        } finally {
            process = "Running ... "
            lock.unlock()
        }
    }
    fun fetchDeviceParam(context: Context) {
        gain = fetchADCGain(context)
        rate = fetchADCRate()
        brokerIP = fetchBrokerIP().toString()
    }
    fun addDataFrame(dataFrame: DataFrame) {
        dataFrames[currentIndex] = dataFrame
        currentIndex = (currentIndex + 1) % bufferSize // 环形缓冲区
    }

    fun getDataFrames(): List<DataFrame> {
        return dataFrames.filterNotNull() // 返回非空的 DataFrame 列表
    }
    //copy the ring buffer to the static buffer
    fun backupBuffer() {
        lock.lock()
        try {
            staticCurrentIndex = currentIndex
            for (i in dataFrames.indices) {
                staticDataFrames[i] = dataFrames[i]
            }
        } finally {
            lock.unlock()
        }
    }
    //extract entry list from ring buffer, so that the dataset of chart is updated, the chart displays newest data
    fun extractEntries(frameCount: Int, distance: Int, useStatic: Boolean) { // useStatic means extract entry from the static copy of ring buffer
        val frames = if (useStatic) staticDataFrames else dataFrames
        val startIndex =if (useStatic) (staticCurrentIndex - distance - frameCount + bufferSize) % bufferSize else (currentIndex - distance - frameCount + bufferSize) % bufferSize
        entryList.clear()
        for (i in 0 until frameCount) {
            val index = (startIndex + i) % bufferSize
            frames[index]?.let { frame ->
                frame.data.forEachIndexed { dataIndex, value ->
                    entryList.add(Entry(((i * frame.data.size) + dataIndex).toFloat(), value.toFloat()-2048))
                }
            }
        }
        entryListUpdated = true //set the flag, so the chart update will be triggered

    }
}
//Use multiple threads to scan the devices under the local WiFi
//If the device is reachable. (a)check its name, if starts with 'GEOSCOPE-', it is a sensor. (b) if it can be connected on port 1883, it is a broker
fun scanDevices(context: Context, scannedDevices: MutableList<Pair<String, String>>, broker: Broker) {
    scannedDevices.clear()
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val dhcpInfo = wifiManager.getDhcpInfo()

    val subnet = (dhcpInfo.ipAddress and 0xFF).toString() + "." +
            ((dhcpInfo.ipAddress shr 8) and 0xFF) + "." +
            ((dhcpInfo.ipAddress shr 16) and 0xFF) + "."
    var brokerFound = false
    val threads = (1..254).map { i ->
        Thread {
            val ip = subnet + i
            try {
                val inetAddress = InetAddress.getByName(ip)
                if (inetAddress.isReachable(100)) {
                    val deviceName = inetAddress.hostName
                    if (deviceName.startsWith("GEOSCOPE-")) {
                        synchronized(scannedDevices) {
                            scannedDevices.add(Pair(deviceName, ip)) // 修改为scannedDevices
                        }
                    }else{
                        Socket(ip, 1883).use {
                            broker.name = deviceName
                            broker.ip = ip
                            broker.status = "Broker Connected!"
                            brokerFound = true
                            broker.reachable = true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    threads.forEach { it.start() }
    // Wait for all the threads
    threads.forEach { it.join() }
    if (!brokerFound) {
        broker.reachable = false
        broker.name = "Not found"
        broker.ip = "Not found"
        broker.status = "Please start a broker on port 1883!"
    }
}
//According to the scanned results, update instances in the device list
fun updateDeviceInstances(
    context: Context,
    currentInstances: MutableList<Device>,
    scannedDevices: List<Pair<String, String>>,
    broker: Broker
) {
    val newDeviceNames = scannedDevices.map { it.first }.toSet()

    // Remove disconnected instances
    currentInstances.removeIf { currentDevice ->
        if (currentDevice.name !in newDeviceNames) {
            sendNotificationAndPlayAudio(context, "A device disconnected", "Sensor ${currentDevice.name} has disconnected.", "yao")
            true //remove
        } else {
            false
        }
    }

    // Add new instance according to name and ip
    scannedDevices.forEach { (deviceName, deviceIP) ->
        if (currentInstances.none { currentDevice -> currentDevice.name == deviceName }) {
            val newDevice = Device(deviceName, deviceIP)
            currentInstances.add(newDevice)
            Thread {
                newDevice.fetchDeviceParam(context)
                if (broker.reachable && newDevice.brokerIP != broker.ip) {
                    newDevice.sendBrokerIP(broker.ip)
                    newDevice.brokerIP = newDevice.fetchBrokerIP().toString()
                }
                newDevice.ready = true
                newDevice.process = "Running ..."
            }.start()
        }
    }
}

fun updateBrokerIPForAllDevices(devices: List<Device>, broker: Broker) {
    devices.forEach { device ->
            if (broker.reachable && device.brokerIP != broker.ip) {
                Thread {
                    device.sendBrokerIP(broker.ip)
                    device.brokerIP = device.fetchBrokerIP().toString()
                    device.ready = true
                    device.process = "Running ..."
                }.start()
            }
    }
}



fun synchronizeAllSensors(devices: List<Device>) {
    devices.forEach { device ->
        Thread {
            device.synchronize()
        }.start()
    }
}

