package com.example.smart_home_mobile_app.firebase

import android.content.Context
import com.example.smart_home_mobile_app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

data class FirebaseServices(
    val auth: FirebaseAuth,
    val database: FirebaseDatabase,
)

object FirebaseBootstrap {
    private const val APP_NAME = "smart-home-mobile"

    fun initialize(context: Context): FirebaseServices {
        val applicationId = BuildConfig.FIREBASE_APPLICATION_ID.trim()
        val apiKey = BuildConfig.FIREBASE_API_KEY.trim()
        val databaseUrl = BuildConfig.FIREBASE_DATABASE_URL.trim().trimEnd('/')
        if (applicationId.isEmpty() || apiKey.isEmpty() || databaseUrl.isEmpty()) {
            throw IllegalStateException(
                "Firebase is not configured. Set FIREBASE_APPLICATION_ID, " +
                    "FIREBASE_API_KEY, and FIREBASE_DATABASE_URL in ~/.gradle/gradle.properties.",
            )
        }
        if (!databaseUrl.startsWith("https://")) {
            throw IllegalStateException("FIREBASE_DATABASE_URL must use HTTPS")
        }
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(applicationId)
                    .setApiKey(apiKey)
                    .setDatabaseUrl(databaseUrl)
                    .build(),
                APP_NAME,
            )
        return FirebaseServices(
            auth = FirebaseAuth.getInstance(app),
            database = FirebaseDatabase.getInstance(app),
        )
    }
}

