package com.github.stephengold.joltjni;

/** Prove rebuilt Java-8 bindings call the published Jolt 6.0.0 native ABI. */
public final class JoltNativeSmoke {
    private JoltNativeSmoke() { }

    public static void main(String[] args) {
        String lib = System.getProperty("jolt.native.path");
        if (lib == null || lib.length() == 0)
            throw new IllegalArgumentException("Missing -Djolt.native.path=<absolute-so>");
        System.load(lib);
        System.out.println("Loaded native library: " + lib);

        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        if (!Jolt.newFactory())
            throw new IllegalStateException("Jolt native factory initialization failed");
        Jolt.registerTypes();
        String version = Jolt.versionString();
        if (version == null || version.length() == 0)
            throw new AssertionError("Empty Jolt native version");
        System.out.println("Java 8 -> native Jolt: " + version
                + " (" + Jolt.buildType() + ")");
        // Factory cleanup happens at process exit, matching Wemppy's JoltEngine.
    }
}
