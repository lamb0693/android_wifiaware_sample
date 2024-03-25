package com.example.wifi_aware_test.thread

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.wifi_aware_test.ThreadMessageCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class ServerSocketThread(private val context: Context, private val messageCallback: ThreadMessageCallback) : Thread(){
    private var serverSocket: ServerSocket? = null
    private var clientSocket : Socket? = null

    private var outputStream: OutputStream? = null
    private var inputStream : InputStream? = null
    private var isRunning = true
    override fun run() {
        Log.i(">>>>", "ServerSocketTrhead Thread Started")

        try {
            serverSocket = ServerSocket(8888)

            serverSocket?.also { serverSocket1 ->
                clientSocket = serverSocket1.accept()
                Log.i(">>>>" , "server socket ; Accepted  clientSocket = $clientSocket")
                clientSocket?.also {
                    inputStream = it.getInputStream()
                    outputStream = it.getOutputStream()

                    sendMessage("hello from server through socket")

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
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            outputStream?.close()
            inputStream?.close()
            clientSocket?.close()
            serverSocket?.close()
        }
        Log.i(">>>>", "Server Thread terminating...")
    }

    private fun sendMessage(message: String): Unit {
        try{
            val strMessage = "server : $message << via socket"
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