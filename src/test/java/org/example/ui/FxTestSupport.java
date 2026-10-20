package org.example.ui;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;

/** Starts JavaFX once for UI tests and runs code on its thread; skips the tests without a display. */
final class FxTestSupport {
    private static Boolean available;

    private FxTestSupport() {
    }

    static synchronized void startToolkit() {
        if (available == null) {
            try {
                CompletableFuture<Void> started = new CompletableFuture<>();
                Platform.startup(() -> started.complete(null));
                started.get(30, TimeUnit.SECONDS);
                Platform.setImplicitExit(false);
                available = true;
            } catch (IllegalStateException alreadyRunning) {
                available = true;
            } catch (Exception | Error unavailable) {
                available = false;
            }
        }
        assumeTrue(available, "JavaFX needs a display");
    }

    static <T> T onFx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    static void runOnFx(ThrowingRunnable action) throws Exception {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    /** Polls the condition on the FX thread until it holds; background tasks finish meanwhile. */
    static void waitUntil(BooleanSupplier condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!onFx(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met within " + timeout);
            }
            Thread.sleep(50);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    static Object call(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
                method.setAccessible(true);
                return method.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(name);
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
