package com.example.examplemod;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public final class CameraShaderCurveSourceSmokeTest
{
    private static void check(boolean yes,String message)
    {if(!yes)throw new AssertionError(message);}

    public static void main(String[] args)
    {
        String shader="#version 120\n"+
                "#define BLOOM_POWER 0.5 // [0.0 0.5 1.0 2.0]\n"+
                "#define SHADOW_SAMPLES 8 // [4 8 16]\n"+
                "#define DISABLED_OPT 3.5 // [1 2 3]\n"+
                "#define USE_ARRAY 6 // [3 6 9]\n"+
                "#if USE_ARRAY == 6\n#endif\n"+
                "const float amount = BLOOM_POWER * 2.0;\n"+
                "float color = BLOOM_POWER + amount;\n"+
                "float untouched = 0.8;\n"+
                "void main() { gl_FragColor = vec4(color); }\n";
        CameraShaderCurveSource.Result r=CameraShaderCurveSource.convert(
                shader,new HashSet<String>(Arrays.asList(
                        "BLOOM_POWER","SHADOW_SAMPLES","USE_ARRAY")));
        check(r.options.containsKey("BLOOM_POWER"),"bloom option");
        check(r.options.containsKey("SHADOW_SAMPLES"),"integer slider");
        check(!r.options.containsKey("DISABLED_OPT"),
                "do not animate disabled OptiFine settings");
        check(!r.options.containsKey("USE_ARRAY"),
                "preprocessor-dependent macros must stay constant");
        check(r.source.contains("uniform float bbs_curve_BLOOM_POWER;"),
                "float uniform declaration");
        check(r.source.contains("uniform int bbs_curve_SHADOW_SAMPLES;"),
                "int uniform declaration");
        check(r.source.contains("#define BLOOM_POWER 0.5"),
                "original macro must remain for preprocessor");
        check(r.source.contains("float amount = bbs_curve_BLOOM_POWER"),
                "dependent const variables must become mutable");
        check(!r.source.contains("const float amount"),
                "GLSL const cannot depend on a uniform");
        check(r.source.contains("float color = bbs_curve_BLOOM_POWER"),
                "shader code must reference injected uniforms");
        check(r.source.contains("float untouched = 0.8"),
                "unrelated shader source must not change");
        check(r.source.contains("#if USE_ARRAY == 6"),
                "preprocessor must remain original");
        check(r.source.contains("#define SHADOW_SAMPLES 8"),
                "literal macro must remain for compile-time code");

        CameraShaderCurveSource.Result none=CameraShaderCurveSource.convert(
                shader,Collections.<String>emptySet());
        check(none.options.isEmpty()&&none.source.equals(shader),
                "no options allowed means original shader unchanged");
        String old="#version 120\n#define EXPOSURE 1.3 // [0.2 1.3 4.0]\n"+
                "void main(){float x=EXPOSURE;}\n";
        CameraShaderCurveSource.Result fallback=
                CameraShaderCurveSource.convert(old,null);
        check(fallback.options.containsKey("EXPOSURE"),
                "annotation-only fallback when OptiFine options unavailable");
        check(Math.abs(fallback.options.get("EXPOSURE").fallback-1.3f)
                <0.0001f,"default pack parameter value");

        // Most real shaderpacks place a tooltip before the selectable
        // values: // Shadow darkness levels [0.05 0.10 0.20].
        String realPack="#version 120\n"+
                "#define SHADOW_DARKNESS 0.10 "+
                "// Shadow darkness levels [0.05 0.10 0.20]\n"+
                "void main(){float x=SHADOW_DARKNESS;}\n";
        CameraShaderCurveSource.Result annotated=
                CameraShaderCurveSource.convert(realPack,null);
        check(annotated.options.containsKey("SHADOW_DARKNESS"),
                "OptiFine tooltip before slider values must parse");

        String noAnnotation="#version 120\n"+
                "#define AUTO_EXPOSURE 1.25 // Exposure strength\n"+
                "void main(){float x=AUTO_EXPOSURE;}\n";
        CameraShaderCurveSource.Result fromApi=
                CameraShaderCurveSource.convert(noAnnotation,
                        Collections.singleton("AUTO_EXPOSURE"));
        check(fromApi.options.containsKey("AUTO_EXPOSURE"),
                "Active float OptiFine option must work without slider hint");

        String protectedShader="#version 120\n"+
                "#define STEPS 8 // Samples [4 8 16]\n"+
                "#if STEPS > 4\nfloat test = 1.;\n#endif\n";
        CameraShaderCurveSource.Result protectedResult=
                CameraShaderCurveSource.convert(protectedShader,
                        Collections.singleton("STEPS"));
        check(!protectedResult.options.containsKey("STEPS"),
                "Preprocessor constants must never become uniforms");

        System.out.println("CAMERA native OptiFine shader curve source PASS");
    }
}