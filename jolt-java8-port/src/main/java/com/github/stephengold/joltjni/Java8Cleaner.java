package com.github.stephengold.joltjni;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java 8 replacement for the subset of java.lang.ref.Cleaner used in Jolt-JNI
 * 6.0.0. Does NOT capture the referent. Ownership and exactly-once native
 * cleanup remain the responsibility of JoltPhysicsObject.executeCleanup().
 *
 * Experimental compatibility component; not an upstream Jolt-JNI source file.
 */
public final class Java8Cleaner {
    private final ReferenceQueue<Object> queue = new ReferenceQueue<Object>();
    private final Set<CleanupReference> live = Collections.newSetFromMap(
            new ConcurrentHashMap<CleanupReference, Boolean>());

    private Java8Cleaner() {
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                for (;;) {
                    try {
                        Reference<?> ref = queue.remove();
                        if (ref instanceof CleanupReference) {
                            CleanupReference cleanup = (CleanupReference) ref;
                            if (live.remove(cleanup)) {
                                try { cleanup.action.run(); }
                                catch (Throwable failure) {
                                    System.err.println("[Jolt Java8 Cleaner] Cleanup failed:");
                                    failure.printStackTrace(System.err);
                                }
                            }
                            cleanup.clear();
                        }
                    } catch (InterruptedException interrupted) {
                        // Daemon should continue processing owned native objects.
                    }
                }
            }
        }, "jolt-java8-cleaner");
        worker.setDaemon(true);
        worker.start();
    }

    public static Java8Cleaner create() { return new Java8Cleaner(); }

    public void register(Object referent, Runnable action) {
        if (referent == null || action == null)
            throw new NullPointerException("referent/action");
        CleanupReference ref = new CleanupReference(referent, queue, action);
        live.add(ref);
        // No strong reference to referent must be retained by action.
        // The returned registration is intentionally owned by the live set.
    }

    private static final class CleanupReference extends PhantomReference<Object> {
        final Runnable action;
        CleanupReference(Object referent, ReferenceQueue<? super Object> queue,
                Runnable action) {
            super(referent, queue);
            this.action = action;
        }
    }
}
