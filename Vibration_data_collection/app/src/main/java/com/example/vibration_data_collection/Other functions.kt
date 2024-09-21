package com.example.vibration_data_collection

import android.app.NotificationManager
import android.content.Context
import android.media.MediaPlayer
import androidx.core.app.NotificationCompat
import com.github.mikephil.charting.data.Entry
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.log10
import kotlin.math.sqrt
//used for connection issues, send notification and play audio.
fun sendNotificationAndPlayAudio(context: Context, title: String, content: String, audioFileName: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notification = NotificationCompat.Builder(context, "Notification channel")
        .setContentTitle(title)
        .setContentText(content)
        .setSmallIcon(R.drawable.offline_icon)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    notificationManager.notify(1, notification)
    // Play audio
    val mediaPlayer = MediaPlayer.create(context, context.resources.getIdentifier(audioFileName, "raw", context.packageName))
    mediaPlayer.start()
    mediaPlayer.setOnCompletionListener {
        it.release() // release resource
    }
}
//perform FFT to the time domain signal entries, a hamming window is used.
fun performFFT(entries: List<Entry>): List<Entry> {

    val n = entries.size

    val fft = DoubleFFT_1D(n.toLong())

    // Prepare the input array for FFT with Hamming window
    val input = DoubleArray(n * 2) // Real and imaginary parts

    for (i in entries.indices) {
        val hammingWindow = 0.54 - 0.46 * kotlin.math.cos(2.0 * Math.PI * i / (n - 1))
        input[2 * i] = entries[i].y.toDouble() * hammingWindow // Real part with Hamming window
        input[2 * i + 1] = 0.0 // Imaginary part
    }


    // Perform FFT

    fft.complexForward(input)

    // Convert the FFT result to a list of entries in dB scale

    val fftEntries = mutableListOf<Entry>()
    for (i in 0 until n / 2) {

        val real = input[2 * i]
        val imaginary = input[2 * i + 1]

        val magnitude = sqrt(real * real + imaginary * imaginary)

        val dbValue = 20 * log10(magnitude)

        fftEntries.add(Entry(i.toFloat(), dbValue.toFloat()))

    }


    return fftEntries
}