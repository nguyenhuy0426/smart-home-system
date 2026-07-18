package com.android.smarthome.video;

import static org.junit.Assert.fail;

final class TestAssertions {
    private TestAssertions() {}

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    static <T extends Throwable> void expectThrows(Class<T> expected, ThrowingRunnable runnable) {
        try {
            runnable.run();
            fail("Expected " + expected.getName());
        } catch (Throwable actual) {
            if (!expected.isInstance(actual)) {
                throw new AssertionError("Expected " + expected.getName() + " but got "
                        + actual.getClass().getName(), actual);
            }
        }
    }
}
