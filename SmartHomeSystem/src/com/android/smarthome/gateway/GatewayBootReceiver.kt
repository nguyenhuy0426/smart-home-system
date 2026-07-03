package com.android.smarthome.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the always-on gateway after Android has completed normal user boot. */
class GatewayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, SmartHomeGatewayService::class.java))
        }
    }
}
