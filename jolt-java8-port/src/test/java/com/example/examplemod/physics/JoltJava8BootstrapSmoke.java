package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;
import java.io.File;

/** Java 8 process-level test of the optional native startup for Forge 1.12. */
public final class JoltJava8BootstrapSmoke {
    private JoltJava8BootstrapSmoke() { }

    public static void main(String[] args) {
        if (!JoltJava8Bootstrap.available(new File(".")))
            throw new AssertionError("Jolt bootstrap failed: " + JoltJava8Bootstrap.failure());
        if (!JoltJava8Bootstrap.available(new File(".")))
            throw new AssertionError("Jolt should stay available after first init");
        if (!"6.0.0".equals(Jolt.versionString()))
            throw new AssertionError("Wrong native version");
        System.out.println("Jolt Java8 optional Forge bootstrap: PASS");
    }
}
