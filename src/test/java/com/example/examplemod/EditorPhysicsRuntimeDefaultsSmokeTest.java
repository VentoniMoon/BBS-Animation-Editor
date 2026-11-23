package com.example.examplemod;

/** Java 8 smoke: user -D switches always outrank verified-release defaults. */
public final class EditorPhysicsRuntimeDefaultsSmokeTest
{
    private EditorPhysicsRuntimeDefaultsSmokeTest(){}

    public static void main(String[] args)
    {
        String flag="bbs.editor.physics.jolt.preview";
        check(!EditorPhysicsRuntimeDefaults.parse(flag,null,false),
                "Normal development builds must remain opt-in");
        check(EditorPhysicsRuntimeDefaults.parse(flag,null,true),
                "Verified Jolt releases should be active out of the box");
        check(!EditorPhysicsRuntimeDefaults.parse(flag,"false",true),
                "Explicit -D...=false must disable a bundled release");
        check(EditorPhysicsRuntimeDefaults.parse(flag,"true",false),
                "Explicit -D...=true must enable legacy dev mode");
        check(!EditorPhysicsRuntimeDefaults.parse("",null,true),
                "Invalid flag must not activate physics");

        String original=System.getProperty(flag);
        try
        {
            System.setProperty(flag,"false");
            check(!EditorPhysicsRuntimeDefaults.enabled(flag),
                    "User opt-out of preview must always work");
            System.setProperty(flag,"true");
            check(EditorPhysicsRuntimeDefaults.enabled(flag),
                    "User opt-in of preview must always work");
        }
        finally
        {
            if(original==null)System.clearProperty(flag);
            else System.setProperty(flag,original);
        }
        System.out.println("Verified packaged physics default / override: PASS");
    }

    private static void check(boolean condition,String message)
    {
        if(!condition)throw new AssertionError(message);
    }
}
