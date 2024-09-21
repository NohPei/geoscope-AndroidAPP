package com.example.vibration_data_collection

import java.io.PrintWriter
import java.net.Socket

import kotlin.system.measureTimeMillis

fun sendTelnetCommand(ip: String, command: String): String? {
    var response: String?
    var elapsedTime: Long
    do {
        elapsedTime = measureTimeMillis {
            response = try {
                val socket = Socket(ip, 23)
                socket.soTimeout = 10000 // If command sending does not succeed within 10 seconds, break from the sending thread, and send the command again
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println("$command\n")
                val reader = socket.getInputStream().bufferedReader()
                var tempResponse: String?
                do {
                    tempResponse = reader.readLine() // keep reading next line, if the content equals what we have send, the sending is successful
                } while (tempResponse != command && tempResponse != null)
                writer.close()
                socket.close()
                tempResponse // return the response
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    } while (elapsedTime > 10000)
    return response
}
//similar to the function above, but read one more line below, to get the response from the device
fun sendTelnetCommandWithLateResponse(ip: String, command: String): String? {
    var response: String?
    var elapsedTime: Long
    do {
        elapsedTime = measureTimeMillis {
            response = try {
                val socket = Socket(ip, 23)
                socket.soTimeout = 10000
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println("$command\n")
                val reader = socket.getInputStream().bufferedReader()
                var tempResponse: String?
                do {
                    tempResponse = reader.readLine()
                } while (tempResponse != command && tempResponse != null)
                writer.close()
                socket.close()
                tempResponse = reader.readLine()
                tempResponse
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    } while (elapsedTime > 10000)
    return response
}


fun sendMultipleCommands(deviceIP: String, vararg commands: String) {
    commands.forEach { command ->
        sendTelnetCommand(deviceIP, command)
    }
}