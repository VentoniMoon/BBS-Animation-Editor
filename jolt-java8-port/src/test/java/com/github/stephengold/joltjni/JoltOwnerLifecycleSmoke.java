package com.github.stephengold.joltjni;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/** Ensure the Java 8 cleanup shim preserves Jolt's once-only release semantics. */
public final class JoltOwnerLifecycleSmoke {
    private JoltOwnerLifecycleSmoke() { }

    private static final class FakeOwner extends JoltPhysicsObject {
        FakeOwner(final AtomicInteger calls) {
            setVirtualAddress(12345L, new Runnable() {
                @Override public void run() { calls.incrementAndGet(); }
            });
        }
    }

    private static WeakReference<FakeOwner> createAndClose(AtomicInteger calls) {
        FakeOwner value = new FakeOwner(calls);
        if (!value.hasAssignedNativeObject() || !value.ownsNativeObject())
            throw new AssertionError("Expected native resource ownership");
        value.close();
        value.close();
        if (value.hasAssignedNativeObject() || value.ownsNativeObject())
            throw new AssertionError("Close did not clear ownership");
        return new WeakReference<FakeOwner>(value);
    }

    private static WeakReference<FakeOwner> createWithoutClose(AtomicInteger calls) {
        return new WeakReference<FakeOwner>(new FakeOwner(calls));
    }

    public static void main(String[] args) throws Exception {
        if (!JoltPhysicsObject.isCleanerStarted())
            JoltPhysicsObject.startCleaner();
        AtomicInteger manual = new AtomicInteger();
        AtomicInteger garbage = new AtomicInteger();

        WeakReference<FakeOwner> closed = createAndClose(manual);
        if (manual.get() != 1)
            throw new AssertionError("Explicit close called more than once: " + manual.get());

        WeakReference<FakeOwner> pending = createWithoutClose(garbage);
        long deadline = System.nanoTime() + 8_000_000_000L;
        while ((pending.get() != null || garbage.get() != 1 || closed.get() != null)
                && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(25L);
        }
        if (garbage.get() != 1 || manual.get() != 1)
            throw new AssertionError("Expected one cleanup per owned object: manual="
                    + manual.get() + " gc=" + garbage.get());
        if (pending.get() != null || closed.get() != null)
            throw new AssertionError("Native-object wrappers were retained");
        System.out.println("Jolt owner explicit close + GC cleanup: PASS");
    }
}
