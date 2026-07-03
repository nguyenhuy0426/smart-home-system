package com.android.smarthome.firebase

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.android.smarthome.security.OAuthBackendHandler
import com.android.smarthome.security.FirebaseAuthSession
import com.android.smarthome.security.FirebaseSessionStore
import java.io.OutputStreamWriter
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FirebaseSyncService : Service(), FirebaseSyncQueue.RealtimeDatabaseWriter {

    private val binder = LocalBinder()
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var syncQueue: FirebaseSyncQueue? = null

    private var databaseUrl = ""
    private var apiKey = ""
    @Volatile
    private var lastTokenRefreshAtEpochMs = 0L
    private lateinit var sessionStore: FirebaseSessionStore
    @Volatile
    private var authSession: FirebaseAuthSession? = null

    companion object {
        private const val TAG = "FirebaseSyncService"
    }

    inner class LocalBinder : Binder() {
        fun getService(): FirebaseSyncService = this@FirebaseSyncService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Firebase Sync Service starting...")
        sessionStore = FirebaseSessionStore(this)
        authSession = sessionStore.load()

        // Load project parameters from secrets
        val secrets = OAuthBackendHandler.loadSecrets()
        if (secrets != null) {
            databaseUrl = secrets.optString("firebase_database_url", "").trimEnd('/')
            apiKey = secrets.optString("firebase_api_key", "")
            Log.i(TAG, "Loaded Firebase Database URL: $databaseUrl")
        } else {
            Log.e(TAG, "Could not load secrets. Sync Service running in offline mode.")
        }

        // Initialize local queue
        try {
            val queueFilePath = Paths.get(filesDir.absolutePath, "sync_queue.txt")
            syncQueue = FirebaseSyncQueue(queueFilePath, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Sync Queue", e)
        }

        // Start periodic sync replay loop (every 10 seconds)
        executor.scheduleWithFixedDelay({
            triggerSync()
        }, 5, 10, TimeUnit.SECONDS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun updateAuthSession(session: FirebaseAuthSession) {
        authSession = session
        Log.i(TAG, "Firebase auth session updated. Replaying queue...")
        executor.submit { triggerSync() }
    }

    fun clearAuthSession() {
        authSession = null
        if (::sessionStore.isInitialized) sessionStore.clear()
    }

    fun enqueueRecord(homeId: String, collection: String, nodeId: String, sequence: Long, jsonPayload: String) {
        require(sequence >= 0L) { "sequence must be non-negative" }
        enqueueRecord(homeId, collection, nodeId, "${nodeId}_$sequence", jsonPayload)
    }

    fun enqueueRecord(
        homeId: String,
        collection: String,
        nodeId: String,
        recordId: String,
        jsonPayload: String
    ) {
        executor.submit {
            try {
                syncQueue?.enqueue(homeId, collection, nodeId, recordId, jsonPayload)
                Log.d(TAG, "Enqueued record: nodeId=$nodeId, recordId=$recordId")
                triggerSync() // Try uploading immediately
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue record", e)
            }
        }
    }

    private fun triggerSync() {
        if (syncQueue == null || databaseUrl.isEmpty()) return
        if (authSession == null && sessionStore.load() == null) return
        try {
            ensureFreshSession()
            val count = syncQueue?.replay() ?: 0
            if (count > 0) {
                Log.i(TAG, "Successfully synced $count pending records to Firebase")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync attempt failed: ${e.message}")
        }
    }

    override fun write(documentPath: String, idempotencyKey: String, jsonPayload: String) {
        if (databaseUrl.isEmpty() || !databaseUrl.startsWith("https://")) {
            throw IllegalStateException("A secure Firebase Database URL is required")
        }
        val idToken = ensureFreshSession().idToken

        // Format URL: https://<project-id>-default-rtdb.firebaseio.com/homes/<homeId>/<collection>/<idempotencyKey>.json?auth=<idToken>
        val urlString = StringBuilder(databaseUrl)
            .append("/")
            .append(documentPath)
            .append(".json")
        
        urlString.append("?auth=")
            .append(URLEncoder.encode(idToken, StandardCharsets.UTF_8.name()))

        val url = URL(urlString.toString())
        var conn: HttpURLConnection? = null
        try {
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("if-match", "null_etag")

            val writer = OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8)
            writer.write(jsonPayload)
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK ||
                responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                Log.d(TAG, "Firebase sync success: $documentPath")
            } else if (responseCode == HttpURLConnection.HTTP_PRECON_FAILED) {
                val existingPayload = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    .orEmpty()
                val isSameLogicalRecord = JsonPayloadComparator.equivalentIgnoringRootFields(
                    jsonPayload,
                    existingPayload,
                    setOf("gatewayReceivedAtEpochMs")
                )
                if (!isSameLogicalRecord) {
                    throw IOException("Conflicting RTDB record already exists at $documentPath")
                }
                Log.d(TAG, "Firebase record already exists: $idempotencyKey")
            } else {
                if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED &&
                    System.currentTimeMillis() - lastTokenRefreshAtEpochMs > 60_000L) {
                    authSession = authSession?.copy(expiresAtEpochMs = 0L)
                }
                throw IOException("Firebase write returned HTTP $responseCode")
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun ensureFreshSession(): FirebaseAuthSession {
        val current = authSession ?: sessionStore.load()
            ?: throw IllegalStateException("Firebase authentication is required")
        if (!current.needsRefresh()) {
            authSession = current
            return current
        }
        if (apiKey.isBlank()) throw IllegalStateException("Firebase API key is not configured")

        val endpoint = "https://securetoken.googleapis.com/v1/token?key=" +
            URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
        val body = "grant_type=refresh_token&refresh_token=" +
            URLEncoder.encode(current.refreshToken, StandardCharsets.UTF_8.name())
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                if (responseCode == 400 || responseCode == 401 || responseCode == 403) {
                    clearAuthSession()
                }
                throw IllegalStateException("Firebase token refresh returned HTTP $responseCode")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val refreshed = FirebaseAuthSession.fromResponse(org.json.JSONObject(response))
            authSession = refreshed
            lastTokenRefreshAtEpochMs = System.currentTimeMillis()
            sessionStore.save(refreshed)
            return refreshed
        } finally {
            connection.disconnect()
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
