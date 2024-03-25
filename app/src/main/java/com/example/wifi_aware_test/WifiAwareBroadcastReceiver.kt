package com.example.wifi_aware_test

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class WifiAwareBroadcastReceiver(
    private val activity: MainActivity,
    private val wifiAwareManager: WifiAwareManager,
    private val wifiAwareSession: WifiAwareSession?,
) : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context?, intent: Intent?) {
        if(wifiAwareManager.isAvailable){
            Log.i(">>>>", "wifiAwareManager is available")
            activity.attach()
        } else {
            wifiAwareSession.let {
                activity.removeCurrentWifiAwareSession()
            }
            Log.e(">>>>", "wifiAwareManager is not available")
        }
    }
}