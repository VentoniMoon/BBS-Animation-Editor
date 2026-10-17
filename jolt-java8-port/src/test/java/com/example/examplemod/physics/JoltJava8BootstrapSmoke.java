package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;
import java.io.File;

/** Java 8 process-level test of the optional native startup for Forge 1.12. */
public final class JoltJava8BootstrapSmoke {
    private JoltJava8BootstrapSmoke() { }

    public static void main(String[] args) {
        if (args.length > 0 && "missing".equals(args[0])) {
            System.setProperty("bbs.editor.jolt.native",
                    new File("intentionally-missing-jolt-library").getAbsolutePath());
            if (JoltJava8Bootstrap.available(new File(".")) ||
                    JoltJava8Bootstrap.failure() == null)
                throw new AssertionError("Unavailable native library must fail closed");
            System.out.println("Jolt Java8 unavailable-platform fallback: PASS");
            return;
        }
        if (!JoltJava8Bootstrap.available(new File(".")))
            throw new AssertionError("Jolt bootstrap failed: " + JoltJava8Bootstrap.failure());
        if (!JoltJava8Bootstrap.available(new File(".")))
            throw new AssertionError("Jolt should stay available after first init");
        if (!"6.0.0".equals(Jolt.versionString()))
            throw new AssertionError("Wrong native version");
        System.out.println("Jolt Java8 optional Forge bootstrap: PASS");
    }
}
