package com.example.wifi_aware_test

import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareSession
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.wifi_aware_test.thread.ClientSocketThread
import com.example.wifi_aware_test.thread.ServerSocketThread

class MainViewModel : ViewModel() {

    private val _wifiAwareSession = MutableLiveData<WifiAwareSession>()
    val wifiAwareSession : LiveData<WifiAwareSession> get() = _wifiAwareSession

    private val _peerHandle = MutableLiveData<PeerHandle>()
    val peerHandle : LiveData<PeerHandle> get() = _peerHandle

    private val _publishDiscoverySession = MutableLiveData<PublishDiscoverySession>()
    val publishDiscoverySession : LiveData<PublishDiscoverySession> get()= _publishDiscoverySession

    private val _subscribeDiscoverySession = MutableLiveData<SubscribeDiscoverySession>()
    val subscribeDiscoverySession : LiveData<SubscribeDiscoverySession> get()= _subscribeDiscoverySession

    init {
        _wifiAwareSession.value = null
        _peerHandle.value = null
        _publishDiscoverySession.value = null
        _subscribeDiscoverySession.value = null
    }

    fun setWifiAwareSession(session: WifiAwareSession?){
        _wifiAwareSession.value = session
    }

    fun setPeerHandle(handle: PeerHandle?){
        _peerHandle.value = handle
    }

    fun setPublishDiscoverySession(session: PublishDiscoverySession?){
        _publishDiscoverySession.value = session
    }

    fun setSubscribeDiscoverySession(session: SubscribeDiscoverySession?){
        _subscribeDiscoverySession.value = session
    }

}