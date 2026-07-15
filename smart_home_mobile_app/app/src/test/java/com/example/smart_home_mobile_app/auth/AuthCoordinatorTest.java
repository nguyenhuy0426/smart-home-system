package com.example.smart_home_mobile_app.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AuthCoordinatorTest {

    @Test
    public void signInSuccessPersistsSessionAndLogoutClearsIt() {
        FakeAuthAdapter adapter = new FakeAuthAdapter(new AuthResult.Success(new AuthUser("user_1", "a@example.com")));
        FakeSessionStore store = new FakeSessionStore();
        AuthCoordinator coordinator = new AuthCoordinator(adapter, store);
        AuthResult[] result = new AuthResult[1];

        coordinator.signIn("a@example.com", "secret1", r -> result[0] = r);

        assertTrue(result[0] instanceof AuthResult.Success);
        assertEquals("user_1", store.user.uid);
        coordinator.logout();
        assertTrue(adapter.signedOut);
        assertNull(store.user);
    }

    @Test
    public void providerSignInSuccessPersistsSession() {
        FakeAuthAdapter adapter = new FakeAuthAdapter(new AuthResult.Failure("unused"));
        FakeSessionStore store = new FakeSessionStore();
        AuthCoordinator coordinator = new AuthCoordinator(adapter, store);
        AuthUser providerUser = new AuthUser("uid_google", "g@example.com");
        AuthResult[] result = new AuthResult[1];

        coordinator.signInWithProvider(
                onResult -> onResult.onResult(new AuthResult.Success(providerUser)),
                r -> result[0] = r);

        assertTrue(result[0] instanceof AuthResult.Success);
        assertEquals("uid_google", store.user.uid);
        assertEquals("g@example.com", store.user.email);
    }

    @Test
    public void providerSignInFailureDoesNotPersistSession() {
        FakeAuthAdapter adapter = new FakeAuthAdapter(new AuthResult.Failure("unused"));
        FakeSessionStore store = new FakeSessionStore();
        AuthCoordinator coordinator = new AuthCoordinator(adapter, store);
        AuthResult[] result = new AuthResult[1];

        coordinator.signInWithProvider(
                onResult -> onResult.onResult(new AuthResult.Failure("Google sign-in was cancelled")),
                r -> result[0] = r);

        assertEquals("Google sign-in was cancelled", ((AuthResult.Failure) result[0]).message);
        assertNull(store.user);
    }

    @Test
    public void registrationAndAuthenticationFailuresAreNotReportedAsSuccess() {
        FakeAuthAdapter adapter = new FakeAuthAdapter(new AuthResult.Failure("invalid credentials"));
        FakeSessionStore store = new FakeSessionStore();
        AuthCoordinator coordinator = new AuthCoordinator(adapter, store);
        AuthResult[] result = new AuthResult[1];

        coordinator.register("invalid", "short", r -> result[0] = r);
        assertTrue(result[0] instanceof AuthResult.Failure);
        assertEquals(0, adapter.calls);

        coordinator.signIn("a@example.com", "secret1", r -> result[0] = r);
        assertTrue(result[0] instanceof AuthResult.Failure);
        assertNull(store.user);
    }

    private static final class FakeAuthAdapter implements AuthAdapter {
        private final AuthResult result;
        int calls;
        boolean signedOut;

        FakeAuthAdapter(AuthResult result) {
            this.result = result;
        }

        @Override
        public AuthUser currentUser() {
            return null;
        }

        @Override
        public void signIn(String email, String password, AuthCallback callback) {
            calls++;
            callback.onResult(result);
        }

        @Override
        public void register(String email, String password, AuthCallback callback) {
            calls++;
            callback.onResult(result);
        }

        @Override
        public void signOut() {
            signedOut = true;
        }
    }

    private static final class FakeSessionStore implements SessionStore {
        AuthUser user;

        @Override
        public void save(AuthUser user) {
            this.user = user;
        }

        @Override
        public AuthUser load() {
            return user;
        }

        @Override
        public void clear() {
            user = null;
        }
    }
}
