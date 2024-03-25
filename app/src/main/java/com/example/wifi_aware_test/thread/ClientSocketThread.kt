package com.example.wifi_aware_test.thread

import android.content.Context
import android.util.Log
import com.example.wifi_aware_test.ThreadMessageCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class ClientSocketThread(val context: Context,
             private val host : InetSocketAddress,
             private val messageCallback: ThreadMessageCallback
) : Thread() {
    private lateinit var socket : Socket
    private var outputStream: OutputStream? = null
    private var inputStream : InputStream? = null
    private var isRunning = true
    override fun run() {
        Log.i(">>>>", "Client Thread Started")

        try {
            socket = Socket()
            socket.connect(host, 10000)
            Log.i(">>>>" , "client socket ; connected to server = $socket")

            outputStream = socket.getOutputStream()
            inputStream = socket.getInputStream()
            // Send message to the server (group owner)
            sendMessage("hello from client through socket")

            while(isRunning){
                val buffer = ByteArray(1024)
                val bytesRead = inputStream?.read(buffer)
                if (bytesRead != null && bytesRead > 0) {
                    val receivedMessage = String(buffer, 0, bytesRead)
                    // Handle the received message
                    Log.i(">>>>",  "ReceivedMessage : $receivedMessage")
                    messageCallback.onMessageReceivedFromThread(receivedMessage)
                    if(receivedMessage == "quit") isRunning = false
                }
            }
        } catch (e: SocketTimeoutException) {
            // Handle timeout exception
            e.printStackTrace()
        } catch (e: Exception) {
            // Handle other exceptions
            e.printStackTrace()
        }finally {
            outputStream?.close()
            inputStream?.close()
            socket.close()
        }

        Log.i(">>>>", "Client Thread terminating...")
    }

    private fun sendMessage(message: String): Unit {
        try{
            val strMessage = "client : $message << via socket"
            outputStream?.write(strMessage.toByteArray())
        } catch(e:Exception) {
            Log.e(">>>>","sendMessage in socket thread : ${e.message}")
        }

    }

    fun sendMessageFromMainThread(message : String) {
        CoroutineScope(Dispatchers.IO).launch {
            sendMessage(message)
        }
    }
}