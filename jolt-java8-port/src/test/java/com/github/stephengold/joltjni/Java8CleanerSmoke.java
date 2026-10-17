package com.github.stephengold.joltjni;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/** Smoke test: GC-triggered cleanup without external JNI dependency. */
public final class Java8CleanerSmoke {
    private Java8CleanerSmoke() { }

    public static void main(String[] args) throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        Java8Cleaner cleaner = Java8Cleaner.create();
        WeakReference<Object> weak;
        {
            Object target = new Object();
            weak = new WeakReference<Object>(target);
            cleaner.register(target, new Runnable() {
                @Override public void run() { calls.incrementAndGet(); }
            });
            target = null;
        }
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (calls.get() == 0 && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(20L);
        }
        if (weak.get() != null || calls.get() != 1)
            throw new AssertionError("Expected one cleanup after collection, got " + calls.get());
        System.out.println("Java8Cleaner smoke: PASS");
    }
}
