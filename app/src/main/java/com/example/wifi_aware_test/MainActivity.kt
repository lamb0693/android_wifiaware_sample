package com.example.wifi_aware_test

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkInfo
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.wifi_aware_test.databinding.ActivityMainBinding
import com.example.wifi_aware_test.thread.ClientSocketThread
import com.example.wifi_aware_test.thread.ServerSocketThread
import java.net.InetSocketAddress
import java.net.ServerSocket

class MainActivity : AppCompatActivity(), ThreadMessageCallback {

    lateinit var bindMain : ActivityMainBinding

    private var wifiAwareReceiver: WifiAwareBroadcastReceiver? = null
    private lateinit var intentFilter : IntentFilter
    private lateinit var wifiAwareManager : WifiAwareManager

    private val customAttachCallback=  CustomAttachCallback(this)

    lateinit var viewModel : MainViewModel

    private var asServer : Boolean? = null

    private lateinit var connectivityManager : ConnectivityManager
    private var serverSocketThread : ServerSocketThread? =  null
    private var clientSocketThread : ClientSocketThread? =  null

    private fun initViewModel(){
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        viewModel.wifiAwareSession.observe(this){
            var strDisplay = "wifiAwareSession : "
            strDisplay += it?.toString() ?: "null"
                bindMain.tvWifiAwareSession.text = strDisplay
            // session이 있으면 연결 버튼 disable
            bindMain.btnConnect.isEnabled = (it==null)
            bindMain.btnDisconnect.isEnabled = (it!=null)
        }

        viewModel.peerHandle.observe(this) {
            var strDisplay = "peerHandle : "
            strDisplay += it?.toString() ?: "null"
            bindMain.tvPeerHandle.text = strDisplay
        }

        viewModel.publishDiscoverySession.observe(this){
            var strDisplay = "publishDiscoverySession : "
            strDisplay += it?.toString() ?: "null"
            bindMain.tvPublishDiscoverySession.text = strDisplay
            bindMain.btnConnect.isEnabled = (
                viewModel.publishDiscoverySession.value == null
                    && viewModel.subscribeDiscoverySession.value == null)
            bindMain.btnSendViaSession.isEnabled = (
                    viewModel.publishDiscoverySession.value != null
                            || viewModel.subscribeDiscoverySession.value != null)
        }

        viewModel.subscribeDiscoverySession.observe(this){
            var strDisplay = "subscribeDiscoverySession : "
            strDisplay += it?.toString() ?: "null"
            bindMain.tvSubscribeDiscoverySession.text = strDisplay
            bindMain.btnConnect.isEnabled = (
                    viewModel.publishDiscoverySession.value == null
                            && viewModel.subscribeDiscoverySession.value == null)
            bindMain.btnSendViaSession.isEnabled = (
                    viewModel.publishDiscoverySession.value != null
                            || viewModel.subscribeDiscoverySession.value != null)
        }

    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun initUIListener() {
        bindMain.btnConnect.setOnClickListener{
            if(asServer == null) {
                Toast.makeText(this, "server or client를 선택하세요", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if(bindMain.editServiceName.text.isEmpty()) {
                Toast.makeText(this, "service 이름을 입력하세요", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            attach()
        }
        bindMain.btnDisconnect.setOnClickListener{
            removeCurrentSocketConnection()
            removeCurrentWifiAwareSession()
        }
        bindMain.rbServer.setOnClickListener{
            asServer = true
        }
        bindMain.rbClient.setOnClickListener{
            asServer = false
        }
        bindMain.btnSendViaSession.setOnClickListener{
            if(bindMain.editMessage.text.isEmpty()) return@setOnClickListener
            sendMessageViaSession(bindMain.editMessage.text.toString())
            bindMain.editMessage.text.clear()
        }
        bindMain.btnSendViaSocket.setOnClickListener{
            if(bindMain.editMessage.text.isEmpty()) return@setOnClickListener
            sendMessageViaSocket(bindMain.editMessage.text.toString())
            bindMain.editMessage.text.clear()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bindMain = ActivityMainBinding.inflate(layoutInflater)

        setContentView(bindMain.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        connectivityManager= getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


        initViewModel()

        initUIListener()

        initWifiAwareManager()

        checkPermission()
        bindMain.btnSendViaSocket.isEnabled = false
    }

    @SuppressLint("MissingPermission")
    fun setWifiAwareSession(wifiAwareSession: WifiAwareSession?){

        Log.i(">>>>", "setting wifiAwareSession")
        if(wifiAwareSession == null) Log.i(">>>>", "wifiAwareSession null")
        removeCurrentWifiAwareSession()
        viewModel.setWifiAwareSession(wifiAwareSession)

        if(asServer == null || bindMain.editServiceName.text.isEmpty()) return

        if(asServer!!) {
            val config: PublishConfig = PublishConfig.Builder()
                .setServiceName(bindMain.editServiceName.text!!.toString())
                .setTtlSec(0)
                .build()
            viewModel.wifiAwareSession.value?.publish(config, object : DiscoverySessionCallback() {
                override fun onPublishStarted(session: PublishDiscoverySession) {
                    Log.i(">>>>", "onPublishStarted... $session")
                    viewModel.setPublishDiscoverySession(session)
                }
                @RequiresApi(Build.VERSION_CODES.Q)
                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    viewModel.setPeerHandle(peerHandle)
                    val receivedMessage = String(message, Charsets.UTF_8)
                    Log.i(">>>>", "onMessageReceived...$peerHandle, $receivedMessage")
                    val strDispay = bindMain.tvChattingArea.text.toString() + "\n" + receivedMessage
                    bindMain.tvChattingArea.text = strDispay

                    if(serverSocketThread == null) initServerSocket()
                    // client는 아래 message를 받으면 ClientSocket을 만든다
                    sendMessageViaSession("hello from server")
                }
                override fun onSessionTerminated() {
                    Log.i(">>>>", "onSessionTerminated")
                    removeCurrentWifiAwareSession()
                    Toast.makeText(this@MainActivity, "fail to connect to server", Toast.LENGTH_SHORT).show()
                    super.onSessionTerminated()
                }
                override fun onServiceLost(peerHandle: PeerHandle, reason: Int) {
                    super.onServiceLost(peerHandle, reason)
                    Log.i(">>>>", "onServiceLost $peerHandle, $reason")
                }
            }, null)
        } else {
            val config: SubscribeConfig = SubscribeConfig.Builder()
                .setServiceName(bindMain.editServiceName.text!!.toString())
                .setTtlSec(0)
                .build()
            viewModel.wifiAwareSession.value?.subscribe(config, object : DiscoverySessionCallback() {
                override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                    Log.i(">>>>", "onSubscribeStarted... $session")
                    viewModel.setSubscribeDiscoverySession(session)
                }
                override fun onServiceDiscovered(
                    peerHandle: PeerHandle,
                    serviceSpecificInfo: ByteArray,
                    matchFilter: List<ByteArray>
                ) {
                    Log.i(">>>>", "onServiceDiscovered... $peerHandle, $serviceSpecificInfo")
                    val messageToSend = "hello...connected"
                    viewModel.setPeerHandle(peerHandle)
                    sendMessageViaSession(messageToSend)
                    Toast.makeText(this@MainActivity, "Connected to server", Toast.LENGTH_SHORT).show()
                }
                @RequiresApi(Build.VERSION_CODES.Q)
                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    viewModel.setPeerHandle(peerHandle)
                    val receivedMessage = String(message, Charsets.UTF_8)
                    Log.i(">>>>", "onMessageReceived...$peerHandle, $receivedMessage")
                    val strDisplay = bindMain.tvChattingArea.text.toString() + "\n" + receivedMessage
                    bindMain.tvChattingArea.text = strDisplay
                    if(clientSocketThread ==null) connectToServerSocket()
                }
                override fun onSessionTerminated() {
                    removeCurrentWifiAwareSession()
                    Toast.makeText(this@MainActivity, "fail to connect to server", Toast.LENGTH_SHORT).show()
                    super.onSessionTerminated()
                }
                override fun onServiceLost(peerHandle: PeerHandle, reason: Int) {
                    super.onServiceLost(peerHandle, reason)
                    Log.i(">>>>", "onServiceLost $peerHandle, $reason")
                }
            }, null)
        }//        }
    }

    fun sendMessageViaSession(message : String){
        if(asServer!!) {
            val strToSend = "server : $message"
            viewModel.publishDiscoverySession.value?.sendMessage(
                viewModel.peerHandle.value!!,101, strToSend.toByteArray(Charsets.UTF_8))
        } else {
            val strToSend = "client : $message"
            viewModel.subscribeDiscoverySession.value?.sendMessage(
                viewModel.peerHandle.value!!,101, strToSend.toByteArray(Charsets.UTF_8))
        }
        val strDisplay = bindMain.tvChattingArea.text.toString() + "\n" + message
        bindMain.tvChattingArea.text = strDisplay
    }

    private fun sendMessageViaSocket(message : String){
        if(asServer!!) {
            val strToSend = "server : $message"
            serverSocketThread?.sendMessageFromMainThread(message)
        } else {
            val strToSend = "client : $message"
            clientSocketThread?.sendMessageFromMainThread(message)
        }
        val strDisplay = bindMain.tvChattingArea.text.toString() + "\n" + message
        bindMain.tvChattingArea.text = strDisplay
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun attach(){
        wifiAwareManager.attach(customAttachCallback, null)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun initServerSocket(){
        if(asServer == null || asServer==false) return
        Log.i(">>>>", "init serversocket")

        if(viewModel.publishDiscoverySession.value == null
            || viewModel.peerHandle.value == null) return

        // WifiAwareNetworkSpecifier 생성
        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(
            viewModel.publishDiscoverySession.value!!,
            viewModel.peerHandle.value!!)
            .setPskPassphrase("12340987")
            .build()
        Log.i(">>>>", "init serversocket $networkSpecifier")

        // WifiAware 를 이용 하는 NetworkRequest 생성
        val myNetworkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()
        Log.i(">>>>", "init serversocket $myNetworkRequest")

        // 콜백 만들고 등록
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i(">>>>", "NetworkCallback onAvailable")
                Toast.makeText(this@MainActivity, "Socket network availabe", Toast.LENGTH_LONG).show()

                // ServerSocketThread가 만들어 져 있지 않으면
                // ServerSocketThread를 만들고 시작시킴
                try{
                    if(serverSocketThread == null) {
                        serverSocketThread = ServerSocketThread(this@MainActivity, this@MainActivity)
                        serverSocketThread?.also{
                            it.start()
                            runOnUiThread{
                                bindMain.btnSendViaSocket.isEnabled = true
                            }
                        }
                    }
                } catch ( e : Exception){
                    Log.e(">>>>", "starting socket thread exception : ${e.message}")
                }
            }
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                Log.i(">>>>", "NetworkCallback onCapabilitiesChanged network : $network")
                Log.i(">>>>", "NetworkCapabilities : $networkCapabilities")
            }
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.i(">>>>", "NetworkCallback onLost")
            }
        }

        connectivityManager.requestNetwork(myNetworkRequest, networkCallback)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectToServerSocket(){
        if(asServer == null || asServer==true) return
        Log.i(">>>>", "init client socket")

        if(viewModel.subscribeDiscoverySession.value == null
            || viewModel.peerHandle.value == null) return

        val networkSpecifier = WifiAwareNetworkSpecifier.Builder(
            viewModel.subscribeDiscoverySession.value!!,
            viewModel.peerHandle.value!!
        )
        .setPskPassphrase("12340987")
        .build()

        Log.i(">>>>", "connecting to server socket $networkSpecifier")

        val myNetworkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
            .setNetworkSpecifier(networkSpecifier)
            .build()
        Log.i(">>>>", "connecting to server socket $myNetworkRequest")
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i(">>>>", "NetworkCallback onAvailable")
                Toast.makeText(this@MainActivity, "Socket network availabe", Toast.LENGTH_LONG)
                    .show()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                Log.i(">>>>", "NetworkCallback onCapabilitiesChanged network : $network")
                Log.i(">>>>", "NetworkCapabilities : $networkCapabilities")

                val peerAwareInfo = networkCapabilities.transportInfo as WifiAwareNetworkInfo
                val peerIpv6 = peerAwareInfo.peerIpv6Addr

                if(clientSocketThread == null){
                    try{
                        clientSocketThread = ClientSocketThread(this@MainActivity,
                            InetSocketAddress(peerIpv6, 8888), this@MainActivity)
                        clientSocketThread?.also{
                            it.start()
                            runOnUiThread{
                                bindMain.btnSendViaSocket.isEnabled = true
                            }
                        }
                    } catch(e : Exception){
                        Log.e(">>>>", "clientSocket : ${e.message}")
                    }
                }

//                networkCallback?.let{
//                    connectivityManager.unregisterNetworkCallback(it)
//                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.i(">>>>", "NetworkCallback onLost")
            }
        }
        connectivityManager.requestNetwork(myNetworkRequest,
            networkCallback as ConnectivityManager.NetworkCallback
        )
    }

    fun removeCurrentWifiAwareSession(){
        try{
            viewModel.publishDiscoverySession.value?.close()
            viewModel.setPublishDiscoverySession(null)
            viewModel.subscribeDiscoverySession.value?.close()
            viewModel.setSubscribeDiscoverySession(null)
            viewModel.wifiAwareSession.value?.close()
            viewModel.setWifiAwareSession(null)
            viewModel.setPeerHandle(null)
        } catch (e: Exception) {
            Log.e(">>>>", "removeWifiAwareSession : ${e.message}")
        }
    }

    private fun removeCurrentSocketConnection(){
        clientSocketThread = null
        serverSocketThread = null
    }


    private fun initWifiAwareManager(){
        wifiAwareManager = getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager
        Log.i(">>>>", "wifiAwareManager : $wifiAwareManager")
        intentFilter = IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED)
        wifiAwareReceiver = WifiAwareBroadcastReceiver(this, wifiAwareManager, viewModel.wifiAwareSession.value )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        registerReceiver(wifiAwareReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        super.onResume()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissions -> val granted = permissions.entries.all {
        it.value
    }
        if(granted) Log.i(">>>>", "all permission granted in permission Launcher")
        else {
            Log.e(">>>>", "not all of permission granted in permission Launcher ")
        }
    }

    private fun checkPermission(){
        val statusCoarseLocation = ContextCompat.checkSelfPermission(this,
            "android.permission.ACCESS_COARSE_LOCATION")
        val statusFineLocation = ContextCompat.checkSelfPermission(this,
            "android.permission.ACCESS_FINE_LOCATION")

        val shouldRequestPermission = statusCoarseLocation != PackageManager.PERMISSION_GRANTED
                || statusFineLocation != PackageManager.PERMISSION_GRANTED

        if (shouldRequestPermission) {
            Log.d(">>>>", "One or more Permission Denied, Starting permission Launcher")
            permissionLauncher.launch(
                arrayOf(
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_FINE_LOCATION",
                )
            )
        } else {
            Log.i(">>>>", "All Permission Permitted, No need to start permission Launcher")
        }
    }

    override fun onMessageReceivedFromThread(message: String) {
        Log.i(">>>>", "from thread : $message")
        val strDisplay = bindMain.tvChattingArea.text.toString() + "\n" + message
        bindMain.tvChattingArea.text = strDisplay
    }

    override fun onBackPressed() {
        // Create a confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Exit")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                // Call finish() to exit the app
                super.onBackPressed()
            }
            .setNegativeButton("No", null)
            .show()
    }
}

