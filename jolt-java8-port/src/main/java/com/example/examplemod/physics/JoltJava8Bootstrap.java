package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Optional Java 8 bootstrap for native Jolt 6.0.0 inside legacy Minecraft.
 *
 * Fail closed: the editor can keep using its previous animation engine if
 * Jolt is unavailable. Call available(gameDir) once before any Jolt native API.
 * An explicit -Dbbs.editor.jolt.native=/absolute/path can be used for testing.
 */
public final class JoltJava8Bootstrap {
    private static boolean attempted;
    private static boolean ready;
    private static Throwable failure;

    private JoltJava8Bootstrap() { }

    public static synchronized boolean available(File minecraftDirectory) {
        if (attempted) return ready;
        attempted = true;
        try {
            File nativeFile = resolveNative(minecraftDirectory);
            System.load(nativeFile.getAbsolutePath());

            // Exactly one process-level startup; JNI class signatures must be
            // from the rebuilt 6.0.0 Java 8 jar, never from an old Minecraft mod.
            Jolt.registerDefaultAllocator();
            Jolt.installDefaultAssertCallback();
            Jolt.installDefaultTraceCallback();
            if (!Jolt.newFactory())
                throw new IllegalStateException("Jolt refused to create a native factory");
            Jolt.registerTypes();
            if (!"6.0.0".equals(Jolt.versionString()))
                throw new IllegalStateException(
                        "Unexpected Jolt ABI version: " + Jolt.versionString());
            ready = true;
            System.out.println("[BBS Editor] Jolt Physics " + Jolt.versionString()
                    + " loaded from " + nativeFile.getAbsolutePath());
        } catch (Throwable err) {
            failure = err;
            ready = false;
            System.err.println("[BBS Editor] Optional Jolt Physics unavailable: " + err);
        }
        return ready;
    }

    public static synchronized Throwable failure() { return failure; }

    private static File resolveNative(File gameDir) throws Exception {
        String override = System.getProperty("bbs.editor.jolt.native");
        if (override != null && override.trim().length() != 0) {
            File chosen = new File(override.trim()).getAbsoluteFile();
            if (!chosen.isFile())
                throw new IOException("Configured Jolt native file missing: " + chosen);
            return chosen;
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!("amd64".equals(arch) || "x86_64".equals(arch)))
            throw new UnsupportedOperationException("Jolt x64 native not available for " + arch);
        String resource;
        String extension;
        if (os.contains("win")) {
            resource = "windows/x86-64/com/github/stephengold/joltjni.dll";
            extension = ".dll";
        } else if (os.contains("linux")) {
            resource = "linux/x86-64/com/github/stephengold/libjoltjni.so";
            extension = ".so";
        } else {
            throw new UnsupportedOperationException("Jolt platform unavailable: " + os);
        }

        ClassLoader loader = Jolt.class.getClassLoader();
        InputStream input = loader.getResourceAsStream(resource);
        if (input == null)
            throw new IOException("Jolt native not found on classpath: " + resource);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 * 1024 * 1024);
        try {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = input.read(chunk)) != -1) {
                if (bytes.size() + n > 64 * 1024 * 1024)
                    throw new IOException("Native library exceeds expected size");
                bytes.write(chunk, 0, n);
            }
        } finally {
            input.close();
        }

        byte[] data = bytes.toByteArray();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder name = new StringBuilder("joltjni-6.0.0-");
        for (int i = 0; i < 16; i++)
            name.append(String.format(Locale.ROOT, "%02x", hash[i] & 0xff));
        name.append(extension);
        File parent = gameDir == null ? new File(".") : gameDir;
        File cache = new File(parent, "config/bbs-editor/jolt-natives");
        Files.createDirectories(cache.toPath());
        File dest = new File(cache, name.toString());
        if (dest.isFile()) {
            byte[] existing = Files.readAllBytes(dest.toPath());
            if (MessageDigest.isEqual(
                    MessageDigest.getInstance("SHA-256").digest(existing), hash))
                return dest;
        }
        File temp = File.createTempFile("jolt-", extension, cache);
        try {
            Files.write(temp.toPath(), data);
            Files.move(temp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
        return dest;
    }
}
