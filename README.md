# Developing environment

This project is written with Google’s official IDE “**Android studio**”, with a developing framework called “**Jetpack-Compose**”. 

1. Install IDE: https://developer.android.google.cn/codelabs/basic-android-kotlin-compose-install-android-studio
2. Open the project: Click ‘open’ and open the folder “vibration_data_sensing”

![image.png](https://prod-files-secure.s3.us-west-2.amazonaws.com/868033a6-c973-4cbb-9288-88cf1f5f959c/df898fe5-f804-444c-89b6-0644ae191ece/image.png)

1. Build and run the app on an Android phone: https://developer.android.google.cn/studio/run/device
2. Read the official starter documents for jetpack-compose framework.https://developer.android.google.cn/courses/android-basics-compose/course You don’t need to read all of them, as long as you can understand the file “MainActicity.kt” - what are @Composable functions? Where is the UI defined? Which code runs when the app starts? 
3. For sake of time, I don’t recommend learning a new language “Kotlin” and all of its grammar from zero. Do the preparations below, and let ChatGPT do the rest for you. Here is a very helpful AI coding tool: [Cursor](https://www.cursor.com/)

# Preparations

To start modifying the vibration data collection app, you’ll need these basic knowledge.

- Basic knowledge of **object oriented programming**.
- The “**declaration programming**” concept in Jetpack-Compose developing framework.
    - Basically, you’ll find many functions annotated “@Composable” in the code. Such functions describes a mechanism that the app would keep track of some variables. Whenever they changes, the app run some code written in the function and update the changes in the UI component.
    - You don’t have to manually consider every detail of the state shifting. Android automatically monitor and decide when to update the UI. So it is called “declaration”.
- Learn that the **MQTT protocol** requires a router, a broker, and several client devices. Learn about the publish-subscribe relation between clients.
- Learn about **multi-thread** operations in mobile apps.
    - Network operations must be put in back-stage threads.
    - To make back-stage threads not mixed with each other (e.g. prevent sending get-gain commands to a sensor while another thread is sending get-rate commands to it), **lock** is used in the code.

# Structure of the code

## 1. Three salient instances in the code

The app monitors and uses 3 salient instances - broker, devices, files. They are digital twins of devices in the real-world or data in the disk. Let’s look at the definition of their class first.

- **Class: Broker**

```kotlin
class Broker {
    var reachable by mutableStateOf(false)
    var name by mutableStateOf("Not found")
    var ip by mutableStateOf("Not found")
    var status by mutableStateOf("Please start a broker on port 1883!")
}
```

There will always be **one** broker instance in the app, describing the broker’s name, IP, status.

- **Class: Device**

```jsx
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
    var entryListUpdated by mutableStateOf(false) 

    fun fetchADCGain(context: Context): Double { //get gain configured in the sensor
        
    }

    fun fetchADCRate(): Long {//get sample rate configured in the sensor
        
    }

    fun fetchBrokerIP(): String? {//get broker IP configured in the sensor
        
    }

    fun setADCGain(setGain: Double) {//set new gain
       
    }

    fun setADCRate(setRate: Long) {//set new sample rate
        
    }

    fun sendBrokerIP(setBrokerIP: String?) {//send the right broker IP to sensors
        
    }

    fun synchronize() {//synchronize the pace of data collection of all sensors
       
    }
    fun fetchDeviceParam(context: Context) {
        
    }
    fun addDataFrame(dataFrame: DataFrame) {
        
    }

    fun getDataFrames(): List<DataFrame> {
        
    }
    //copy the ring buffer to the static buffer
    fun backupBuffer() {
       
    }
    //extract entry list from ring buffer, so that the dataset of chart is updated, the chart displays newest data
    fun extractEntries(frameCount: Int, distance: Int, useStatic: Boolean) { // useStatic means extract entry from the static copy of ring buffer
    
    }
}
```

There is always a mutable list of Device instances called “**devices**” in the app, corresponding to each connected sensor.

A “device” instance:

1. serves as a digital twin of a physical sensor, provides API to fetch and send configurations to sensors via Telnet. Telnet is a remote console that transmits AT commands, related functions are defined in the file “Telnet.kt”.
2. Store, parse and reformat the vibration data. When the sensor is running (the procedure that this app prepare the sensors will be introduced later), MQTT message in the format of json is parsed into instance **“dataFrame”** and added  to the device instance’s ring buffer “**dataFrames**”. To plot the data real-time, certain frames in the ring buffer will be reformatted into “**entryList**”. To  plot previous data, the “dataFrames” is copied to “**staticDataFrame**” and stop updating, then reformatted into “entryList”.
- **Class: CsvFile**

```kotlin
data class CsvFile(
    var file: File, //This class is an extension of the class 'File'

) {
    //The file generated by the app has three layers: file - segment - sensor. A file can include multiple segments, a segment can include multiple sensors.
    //A CsvFile instance record the files' metadata, segment list along with the segment's metadata and sensor list, along with the sensor's metadata
    var segmentList: MutableList<Segment> = mutableListOf()
    var comment: String = ""
    var isRecording = mutableStateOf(false)
    var isExpanded = mutableStateOf(false)
    fun parseCsvFile() {
        
    }

//When start recording, write a header that describes the segment's metadata into the file.
    fun writeHeader(segment: Segment) {
      
    }
}
data class Segment(
    var name: String = "",
    var startTime: String = "",
    var comment: String = "",
    val sensorList: MutableList<SensorData> = mutableListOf()
)

data class SensorData(
    var name: String = "",
    var gain: Double = 0.0,
    var rate: Int = 0,
)
```

There is always a mutable list of csvFile  instances called “**files**”. It correspond to the csv files in the disk.

A csvFile instance:

1. point to a file in the disk, since it is an extension of the system class “File”.
2. contains several “**Segment”** instances, each corresponds to a recording attempt. A Segment instance contains several **“SensorData”** instances, each describes a sensor’s parameters while recording.
3. can parse the file with function parseCavFile(). It reads all the segments in the file along with their metadata, and sensor information.
4. can write metadata into the file with function “writeHeader” when a recording attempt starts.
5. can write vibration data into the file with function **“writeFrame”** during recording.

Below is an example csv file generated by the app. There are 3 layers: file - segment - sensor, indicated by retraction, which aligns with the structure of the class CsvFile.

![image.png](https://prod-files-secure.s3.us-west-2.amazonaws.com/868033a6-c973-4cbb-9288-88cf1f5f959c/592dc911-506f-418d-9f5f-9aa596d1cf18/image.png)

## 2. Synchronizing instances with physical devices

After learning about the most important instances in the app, we can now understand how the app is connected to the external environment:

![image.png](https://prod-files-secure.s3.us-west-2.amazonaws.com/868033a6-c973-4cbb-9288-88cf1f5f959c/9a9d8870-f452-4787-a57a-69919f8f8444/image.png)

Next we’ll see the code that ensures the alignment between these instances and physical devices.

- **Periodically scan net devices and update the instances**

In the **onCreate** function in mainActivity, a timer is started. The app start to scan all devices under local WiFi. The broker instance and device list instance are updated in the process.

```kotlin
handler = Handler(Looper.getMainLooper())
        runnable = Runnable {
            scanDevices(this@MainActivity, scannedDevices, broker)
            updateDeviceInstances(this@MainActivity, devices, scannedDevices, broker)
            handler.postDelayed(runnable, 5000)
        }
        handler.post(runnable)
```

Two relevant functions are below. If a device is named with a prefix “GEOSCOPE-”, then it is a **sensor**. If a device can be successfully connected at the 1883 port, then there is a **broker** running on that device.

```kotlin
//In device.kt
//Use multiple threads to scan the devices under the local WiFi
//If the device is reachable. (a)check its name, if starts with 'GEOSCOPE-', it is a sensor. (b) if it can be connected on port 1883, it is a broker
fun scanDevices(context: Context, scannedDevices: MutableList<Pair<String, String>>, broker: Broker) {

}
//According to the scanned results, update instances in the device list
fun updateDeviceInstances(
    context: Context,
    currentInstances: MutableList<Device>,
    scannedDevices: List<Pair<String, String>>,
    broker: Broker
) {
   
}
```

- **Two monitors declared by @Composable functions**

To ensure receiving data correctly from sensors, there are three things to check:

1. Connect to the MQTT broker as an MQTT client.
2. The “broker IP” configured in the sensor matches the actual broker IP.
3. The app subscribe to the topic published by the sensor.

In practice, broker has different states (not found, reachable but not connected, connected, lost connection), and each sensor has different states (connected but not configured, publishing topic, lost connection). So it is troublesome to enumerate all states and events. So we use two @Composable functions to declare monitoring on the event that a broker becomes reachable or not reachable, a sensor becomes ready or disconnected, ensuring the final state always matches the three conditions above.

 

```kotlin
//In MainActivity.kt
MonitorBroker(broker)
MonitorDevicesReady(devices)
```

## 3. Handling MQTT message arrivals

Now the MQTT message should be dumping in! The handler function is defined in dataProcessing.kt. It does three things:

1. Parse the message into “dataFrame” instance
2. Add the dataFrame to the correlating “**device”** instance
3. Reformat the frame into an “**EntryList**” and store it in “**device**” instance, for plotting
4. If the app is recording, write the frame into the file

```kotlin
data class DataFrame(
    val uuid: String,
    val data: List<Int>,
    val gain: Double,
    val sendTime: Long
)
fun mqttMessageHandler(
    topic: String?,
    message: MqttMessage?,
    devices: MutableList<Device>,
    mainActivity: MainActivity
) {
    // The message is in json format, parse them into a data structure called DataFrame defined above
    val jsonString = message?.toString() ?: return
    try {
        val dataFrame = DataFrame.fromJson(jsonString)
        val device = devices.find { it.topic == topic }

        device?.addDataFrame(dataFrame)

        if (mainActivity.isPlaying) {
            device?.extractEntries(mainActivity.plotLength, 0, false) // update the entry list if plotting real-time
            mainActivity.sliderValue = 199
        }
        //if a file is recording, fine the file and stream the data into it
        if (mainActivity.recording) {
            mainActivity.files.forEach { csvFile ->
                if (csvFile.isRecording.value) {
                    csvFile.writeFrame(dataFrame, mainActivity.recordingSegment, device?.name ?: "")
                }
            }
        }
    } catch (e: Exception) {
        println("Error parsing message: ${e.message}")
    }
}
```

## 4. Visualization

To plot the signal and spectrum, third party library “MPAndoidCharts” is used. To plot a line chart, you need to:

1. **declare one or multiple entry lists as dataset**. An entry is a pair of x-y coordinates.
2. Whenever you want to update the chart, **update the dataset and mark a flag variable**, so that the @Composable function monitoring the flag will be triggered and run the code inside to update the chart.

In this app, the entryList is stored as property of the device instance, and it is extracted from the dataFrames (ring buffer) in the device instance.

```kotlin
//In MainActivity.kt
@Composable
    fun LineChart(devices: List<Device>) {
    }
```

## 5. Synchronizing CsvFile instance with files in the disk

- Whenever a file is added or removed from disk, use the function **updateFileList()**, then the file list UI will be updated.
- Whenever a file’s content is modified, use the function **parseCsvFile()**, then the app will read the file content and the CsvFile instance will be updated with respect to file content.

## 6. The procedure to start recording

To start recording with current device list, 3 things need to be done.

- **Add a new segment in the current file**

Generate a segment instance with current device list, then write a template into the csv file in disk.

```kotlin
fun generateSegment(segName: String, comment: String, devices: List<Device>, useAllSeg: Boolean = false): Segment {

}
fun writeHeader(segment: Segment) {

}
```

- **Modify the recording flag of mainActivity and the device instance, so that the MQTT Handler will start stream data into the file with function writeFrame at the arrival of messages.**

Below is the current code to start recording:

```kotlin
//mainAvtivity.kt, when clicking the confirm button of starting recording
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
```

## 7. UI components & quick locating where to modify

Below is a diagram of the UI components along with other. When trying to modify some logic or adding new functions, you can refer to this diagram to locate correlating code.

![image.png](https://prod-files-secure.s3.us-west-2.amazonaws.com/868033a6-c973-4cbb-9288-88cf1f5f959c/2e77251a-8432-4007-b938-b088ce979e8d/image.png)

# Contact me

If you have idea on what to modify or add to the app, a simple way to know where to start is contacting me. My email is yunkaiy@zju.edu.cn.
