package com.example.smart_home_mobile_app.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.smart_home_mobile_app.BuildConfig
import com.example.smart_home_mobile_app.auth.AuthCoordinator
import com.example.smart_home_mobile_app.auth.AuthResult
import com.example.smart_home_mobile_app.auth.AuthUser
import com.example.smart_home_mobile_app.auth.FirebaseAuthAdapter
import com.example.smart_home_mobile_app.auth.GoogleCredentialSignIn
import com.example.smart_home_mobile_app.auth.SecureSessionStore
import com.example.smart_home_mobile_app.data.HomeUiState
import com.example.smart_home_mobile_app.data.LoadStatus
import com.example.smart_home_mobile_app.firebase.FirebaseBootstrap
import com.example.smart_home_mobile_app.firebase.FirebaseRealtimeGateway
import com.example.smart_home_mobile_app.firebase.Subscription
import com.example.smart_home_mobile_app.repository.CommandRepository
import com.example.smart_home_mobile_app.repository.HomeIdStore
import com.example.smart_home_mobile_app.repository.HomeRepository

enum class AuthStatus { CONFIG_REQUIRED, SIGNED_OUT, LOADING, SIGNED_IN, ERROR }

data class AuthUiState(
    val status: AuthStatus,
    val user: AuthUser? = null,
    val message: String? = null,
)

class SmartHomeAppController(context: Context, val isPreviewMode: Boolean = false) {
    private val homeIdStore = HomeIdStore(context)
    private var authCoordinator: AuthCoordinator? = null
    private var firebaseAuthAdapter: FirebaseAuthAdapter? = null
    private var homeRepository: HomeRepository? = null
    private var commandRepository: CommandRepository? = null
    private var homeSubscription: Subscription? = null

    var authState by mutableStateOf(AuthUiState(AuthStatus.LOADING))
        private set
    var homeState by mutableStateOf(HomeUiState())
        private set
    val homeIds = mutableStateListOf<String>()
    var selectedHomeId by mutableStateOf<String?>(null)
        private set
    var commandMessage by mutableStateOf<String?>(null)
        private set
    var previewMode by mutableStateOf(false)
        private set

    init {
        if (isPreviewMode) {
            previewMode = true
            homeIds.add("home_123")
            selectedHomeId = "home_123"
            homeState = com.example.smart_home_mobile_app.data.FakeData.sampleHomeUiState
            authState = AuthUiState(AuthStatus.SIGNED_IN, user = AuthUser("uid_preview", "preview@example.com"))
        } else {
            homeIds.addAll(homeIdStore.load())
            try {
                val services = FirebaseBootstrap.initialize(context)
                val adapter = FirebaseAuthAdapter(services.auth)
                firebaseAuthAdapter = adapter
                authCoordinator = AuthCoordinator(
                    adapter,
                    SecureSessionStore(context),
                )
                val gateway = FirebaseRealtimeGateway(services.database)
                homeRepository = HomeRepository(gateway)
                commandRepository = CommandRepository(gateway)
                val restored = authCoordinator?.restoredUser()
                if (restored == null) {
                    authState = AuthUiState(AuthStatus.SIGNED_OUT)
                } else {
                    onAuthenticated(restored)
                }
            } catch (error: Throwable) {
                authState = AuthUiState(AuthStatus.CONFIG_REQUIRED, message = error.message)
            }
        }
    }

    fun signIn(email: String, password: String) {
        val coordinator = authCoordinator ?: return
        authState = AuthUiState(AuthStatus.LOADING)
        coordinator.signIn(email, password, ::handleAuthResult)
    }

    fun register(email: String, password: String) {
        val coordinator = authCoordinator ?: return
        authState = AuthUiState(AuthStatus.LOADING)
        coordinator.register(email, password, ::handleAuthResult)
    }

    fun signInWithGoogle(activity: ComponentActivity) {
        val coordinator = authCoordinator ?: return
        val adapter = firebaseAuthAdapter ?: return
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (webClientId.isEmpty()) {
            authState = AuthUiState(
                AuthStatus.ERROR,
                message = "Google sign-in is not configured. Set GOOGLE_WEB_CLIENT_ID in " +
                    "gradle.properties (see CONFIG_REQUIRED.md).",
            )
            return
        }
        authState = AuthUiState(AuthStatus.LOADING)
        coordinator.signInWithProvider({ onResult ->
            GoogleCredentialSignIn(webClientId).start(
                activity,
                onToken = { idToken -> adapter.signInWithGoogleIdToken(idToken, onResult) },
                onError = { message -> onResult(AuthResult.Failure(message)) },
            )
        }, ::handleAuthResult)
    }

    fun signInWithApple(activity: ComponentActivity) {
        val coordinator = authCoordinator ?: return
        val adapter = firebaseAuthAdapter ?: return
        authState = AuthUiState(AuthStatus.LOADING)
        coordinator.signInWithProvider({ onResult ->
            adapter.signInWithApple(activity, onResult)
        }, ::handleAuthResult)
    }

    fun logout() {
        homeSubscription?.cancel()
        homeSubscription = null
        authCoordinator?.logout()
        authState = AuthUiState(AuthStatus.SIGNED_OUT)
        homeState = HomeUiState()
        selectedHomeId = null
        commandMessage = null
        previewMode = false
    }

    fun enterPreviewMode() {
        if (!BuildConfig.DEBUG) return
        homeSubscription?.cancel()
        homeSubscription = null
        previewMode = true
        selectedHomeId = null
        homeState = HomeUiState(
            status = LoadStatus.EMPTY,
            message = "Debug UI preview: no Firebase session, home data, or device commands are active",
        )
        commandMessage = null
    }

    fun exitPreviewMode() {
        previewMode = false
        homeState = HomeUiState()
    }

    fun addHome(homeId: String) {
        try {
            val ids = homeIdStore.add(homeId.trim())
            homeIds.clear()
            homeIds.addAll(ids)
            selectHome(homeId.trim())
        } catch (error: IllegalArgumentException) {
            homeState = HomeUiState(status = LoadStatus.ERROR, message = error.message)
        }
    }

    fun removeSelectedHome() {
        val selected = selectedHomeId ?: return
        homeSubscription?.cancel()
        homeSubscription = null
        val ids = homeIdStore.remove(selected)
        homeIds.clear()
        homeIds.addAll(ids)
        selectedHomeId = null
        homeState = HomeUiState(status = LoadStatus.EMPTY, message = "Add a home ID to begin")
        ids.firstOrNull()?.let(::selectHome)
    }

    fun selectHome(homeId: String) {
        val user = authState.user ?: return
        if (homeId !in homeIds) return
        homeSubscription?.cancel()
        selectedHomeId = homeId
        homeSubscription = homeRepository?.observe(homeId, user.uid) { state ->
            homeState = state
        }
    }

    fun sendCommand(nodeId: String, commandType: String) {
        if (previewMode) {
            commandMessage = "Commands are disabled in debug UI preview"
            return
        }
        val user = authState.user ?: return
        val homeId = selectedHomeId ?: return
        val role = homeState.snapshot?.home?.role.orEmpty()
        if (commandType in setOf("unlock", "open_door") && role != "access_admin") {
            commandMessage = "Access commands require the access_admin role"
            return
        }
        commandMessage = "Submitting request…"
        try {
            commandRepository?.create(user.uid, homeId, nodeId, commandType) { requestId, error ->
                commandMessage = if (error == null) {
                    "Command request ${requestId.orEmpty()} submitted for gateway authorization"
                } else {
                    error.message
                }
            }
        } catch (error: IllegalArgumentException) {
            commandMessage = error.message
        }
    }

    fun clearCommandMessage() {
        commandMessage = null
    }

    fun close() {
        homeSubscription?.cancel()
        homeSubscription = null
    }

    private fun handleAuthResult(result: AuthResult) {
        when (result) {
            is AuthResult.Success -> onAuthenticated(result.user)
            is AuthResult.Failure -> authState = AuthUiState(AuthStatus.ERROR, message = result.message)
        }
    }

    private fun onAuthenticated(user: AuthUser) {
        authState = AuthUiState(AuthStatus.SIGNED_IN, user = user)
        if (homeIds.isEmpty()) {
            homeState = HomeUiState(status = LoadStatus.EMPTY, message = "Add a home ID to begin")
        } else {
            selectHome(homeIds.first())
        }
    }
}
