package com.example.smart_home_mobile_app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.smart_home_mobile_app.ui.screens.MobileAppNavigation

class MobileDashboardActivity : ComponentActivity() {
    private lateinit var controller: SmartHomeAppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = SmartHomeAppController(applicationContext)
        setContent { MobileAppNavigation(controller) }
    }

    override fun onDestroy() {
        controller.close()
        super.onDestroy()
    }
}

