package com.example.examplemod;

/** Verify production weld preview scope compiles on Java 8 with Chameleon
 * optional; runtime morph integration is verified inside Forge separately.
 */
public final class ChameleonWeldPreviewCompileSmoke {
    public static void main(String[] args) {
        try (ChameleonWeldPreviewPass pass=ChameleonWeldPreviewPass.begin(null,0F)) {
            if(pass==null)throw new AssertionError("Missing Weld preview scope");
        }
        System.out.println("Chameleon Weld preview Java8 compilation: PASS");
    }
}
