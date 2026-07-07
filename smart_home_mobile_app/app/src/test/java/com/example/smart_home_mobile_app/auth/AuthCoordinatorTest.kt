package com.example.smart_home_mobile_app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCoordinatorTest {
    @Test
    fun signInSuccessPersistsSessionAndLogoutClearsIt() {
        val adapter = FakeAuthAdapter(AuthResult.Success(AuthUser("user_1", "a@example.com")))
        val store = FakeSessionStore()
        val coordinator = AuthCoordinator(adapter, store)
        var result: AuthResult? = null

        coordinator.signIn("a@example.com", "secret1") { result = it }

        assertTrue(result is AuthResult.Success)
        assertEquals("user_1", store.user?.uid)
        coordinator.logout()
        assertTrue(adapter.signedOut)
        assertNull(store.user)
    }

    @Test
    fun providerSignInSuccessPersistsSession() {
        val adapter = FakeAuthAdapter(AuthResult.Failure("unused"))
        val store = FakeSessionStore()
        val coordinator = AuthCoordinator(adapter, store)
        val providerUser = AuthUser("uid_google", "g@example.com")
        var result: AuthResult? = null

        coordinator.signInWithProvider(
            { onResult -> onResult(AuthResult.Success(providerUser)) },
        ) { result = it }

        assertTrue(result is AuthResult.Success)
        assertEquals("uid_google", store.user?.uid)
        assertEquals("g@example.com", store.user?.email)
    }

    @Test
    fun providerSignInFailureDoesNotPersistSession() {
        val adapter = FakeAuthAdapter(AuthResult.Failure("unused"))
        val store = FakeSessionStore()
        val coordinator = AuthCoordinator(adapter, store)
        var result: AuthResult? = null

        coordinator.signInWithProvider(
            { onResult -> onResult(AuthResult.Failure("Google sign-in was cancelled")) },
        ) { result = it }

        assertEquals("Google sign-in was cancelled", (result as AuthResult.Failure).message)
        assertNull(store.user)
    }

    @Test
    fun registrationAndAuthenticationFailuresAreNotReportedAsSuccess() {
        val adapter = FakeAuthAdapter(AuthResult.Failure("invalid credentials"))
        val store = FakeSessionStore()
        val coordinator = AuthCoordinator(adapter, store)
        var result: AuthResult? = null

        coordinator.register("invalid", "short") { result = it }
        assertTrue(result is AuthResult.Failure)
        assertEquals(0, adapter.calls)

        coordinator.signIn("a@example.com", "secret1") { result = it }
        assertTrue(result is AuthResult.Failure)
        assertNull(store.user)
    }

    private class FakeAuthAdapter(private val result: AuthResult) : AuthAdapter {
        var calls = 0
        var signedOut = false
        override fun currentUser(): AuthUser? = null
        override fun signIn(email: String, password: String, callback: (AuthResult) -> Unit) {
            calls++
            callback(result)
        }
        override fun register(email: String, password: String, callback: (AuthResult) -> Unit) {
            calls++
            callback(result)
        }
        override fun signOut() { signedOut = true }
    }

    private class FakeSessionStore : SessionStore {
        var user: AuthUser? = null
        override fun save(user: AuthUser) { this.user = user }
        override fun load(): AuthUser? = user
        override fun clear() { user = null }
    }
}

