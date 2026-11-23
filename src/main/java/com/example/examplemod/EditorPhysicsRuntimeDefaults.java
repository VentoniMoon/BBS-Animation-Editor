package com.example.examplemod;

import java.io.IOException;
import java.io.InputStream;

/**
 * Release-only runtime activation for physics bundled and validated by CI.
 *
 * Legacy/unbundled editor builds keep their opt-in behavior. The marker is
 * inserted into the final JAR ONLY after a full Forge + native Jolt build,
 * compatibility checks and native-library inspection have succeeded.
 * Explicit -D...=true/false always wins over the packaged default.
 *
 * This class never links JNI or starts physics itself.
 */
public final class EditorPhysicsRuntimeDefaults
{
    private static final String VERIFIED_BUNDLE=
            "/META-INF/bbsanimationeditor-verified-jolt6-java8.marker";
    private static final boolean BUNDLED=checkVerifiedBundle();

    private EditorPhysicsRuntimeDefaults(){}

    public static boolean isVerifiedBundle(){return BUNDLED;}

    public static boolean enabled(String property)
    {
        return parse(property,System.getProperty(property),BUNDLED);
    }

    static boolean parse(String property,String explicit,boolean packaged)
    {
        if(property==null||property.length()==0)return false;
        return explicit==null?packaged:Boolean.parseBoolean(explicit);
    }

    private static boolean checkVerifiedBundle()
    {
        try(InputStream marker=EditorPhysicsRuntimeDefaults.class
                .getResourceAsStream(VERIFIED_BUNDLE))
        {
            return marker!=null&&marker.read()=='J';
        }
        catch(IOException|SecurityException unreadable)
        {
            return false;
        }
    }
}
