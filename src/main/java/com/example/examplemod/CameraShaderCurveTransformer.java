package com.example.examplemod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Narrow, optional native OptiFine shader hooks. Never changes Blockbuster,
 * world rendering, camera projection, GL render order, or shader options
 * on disk. Unsupported OptiFine layouts are left completely unchanged.
 */
public final class CameraShaderCurveTransformer implements IClassTransformer
{
    private static final String SHADERS="net.optifine.shaders.Shaders";
    private static final String SHADERS_RENDER=
            "net.optifine.shaders.ShadersRender";
    private static final String RUNTIME="com/example/examplemod/CameraShaderCurves";
    private static final String SUN_SHADOW=
            "com/example/examplemod/BbsFsNativeActorSunShadowOverlay";
    private static final String NATIVE_ACTORS=
            "com/example/examplemod/BbsFsNativeActorPass";
    private static volatile boolean nativeShadowHookInstalled;
    private static volatile boolean nativeEntityHookInstalled;
    public static boolean nativeEntityHookInstalled()
    {
        return nativeEntityHookInstalled;
    }
    private static volatile String shadowHookDiagnostic="not-transformed";
    public static String nativeShadowHookDiagnostic()
    {
        return shadowHookDiagnostic;
    }

    /** Used for runtime diagnostics; the first frame may install lazily. */
    public static boolean nativeShadowHookInstalled()
    {
        return nativeShadowHookInstalled;
    }
    private static boolean warned;
    private static volatile boolean installed;

    public static boolean installed(){return installed;}

    @Override public byte[] transform(String name,String transformedName,
            byte[] bytes)
    {
        if(bytes==null)return null;
        final boolean curvesEnabled=!"false".equalsIgnoreCase(
                System.getProperty("bbs.editor.camera.shaderCurves","true"));
        final boolean shadowsEnabled=!"false".equalsIgnoreCase(
                System.getProperty(
                        "bbs.editor.camera.nativeSunShadowTransformer",
                        "true"));
        final boolean actorEntityEnabled=!"false".equalsIgnoreCase(
                System.getProperty("bbs.editor.preview.nativeEntityStage","true"));
        final boolean isShaders=SHADERS.equals(name)||
                SHADERS.equals(transformedName);
        final boolean isShadersRender=SHADERS_RENDER.equals(name)||
                SHADERS_RENDER.equals(transformedName);
        if(!isShaders&&!isShadersRender)return bytes;
        if(!curvesEnabled&&!shadowsEnabled&&!actorEntityEnabled)return bytes;
        try
        {
            ClassNode node=new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(node,0);
            int readerHooks=0,programHooks=0,shadowHooks=0,actorHooks=0;
            // Prefer instrumenting the actual shadow renderer, not
            // assuming which method in Shaders calls it. The method is
            // invoked before ordinary GBuffer terrain in OptiFine G5.
            // A fallback callsite inside Shaders.beginRender is kept
            // below for builds with unusual ShadersRender layouts.
            if(isShadersRender&&shadowsEnabled)
            {
                for(Object obj:node.methods)
                {
                    MethodNode method=(MethodNode)obj;
                    if(!"renderShadowMap".equals(method.name)||
                            !method.desc.endsWith(")V"))continue;
                    int exits=0;
                    for(AbstractInsnNode insn:
                            method.instructions.toArray())
                    {
                        if(insn.getOpcode()!=Opcodes.RETURN)continue;
                        method.instructions.insertBefore(insn,
                                shadowCallback());
                        exits++;
                    }
                    shadowHooks+=exits;
                    shadowHookDiagnostic="ShadersRender.renderShadowMap "+
                            method.desc+" returnHooks="+exits;
                }
                if(shadowHooks>0)
                {
                    ClassWriter out=new ClassWriter(
                            ClassWriter.COMPUTE_MAXS);
                    node.accept(out);
                    nativeShadowHookInstalled=true;
                    System.out.println("[BBS Editor][Actor Sun Shadow] "+
                            "Native hook installed in "+
                            shadowHookDiagnostic);
                    return out.toByteArray();
                }
                shadowHookDiagnostic=
                        "ShadersRender loaded but no compatible return hook";
                System.err.println("[BBS Editor][Actor Sun Shadow] "+
                        shadowHookDiagnostic);
                return bytes;
            }
            if(!isShaders)return bytes;
            for(Object o:node.methods)
            {
                MethodNode method=(MethodNode)o;
                if(curvesEnabled&&
                        method.name.matches(
                                "create(?:Vert|Geom|Frag|Comp)Shader"))
                {
                    for(AbstractInsnNode instruction:method.instructions.toArray())
                    {
                        if(!(instruction instanceof MethodInsnNode))continue;
                        MethodInsnNode invoke=(MethodInsnNode)instruction;
                        if("resolveIncludes".equals(invoke.name)&&
                                invoke.desc.endsWith(
                                        ")Ljava/io/BufferedReader;"))
                        {
                            method.instructions.insert(instruction,call(
                                    "processReader",
                                    "(Ljava/io/BufferedReader;)"+
                                    "Ljava/io/BufferedReader;"));
                            readerHooks++;
                        }
                    }
                }
                else if(shadowsEnabled&&
                        "beginRender".equals(method.name))
                {
                    // OptiFine 1.12.2 performs the full native sun
                    // shadow map BEFORE its regular world GBuffer.
                    // Only there can actor casters affect BSL terrain
                    // lighting. A post-WorldLast overlay was too late.
                    // Avoid modifying ShadersRender or native sfb.
                    for(AbstractInsnNode instruction:
                            method.instructions.toArray())
                    {
                        if(!(instruction instanceof MethodInsnNode))continue;
                        MethodInsnNode invoke=(MethodInsnNode)instruction;
                        if(!"renderShadowMap".equals(invoke.name)||
                                !"net/optifine/shaders/ShadersRender"
                                        .equals(invoke.owner))continue;
                        method.instructions.insert(instruction,
                                shadowCallback());
                        shadowHooks++;
                    }
                }
                else if(curvesEnabled&&"useProgram".equals(method.name))
                {
                    for(AbstractInsnNode instruction:method.instructions.toArray())
                        if(instruction.getOpcode()==Opcodes.RETURN)
                        {
                            method.instructions.insertBefore(instruction,call(
                                    "applyUniforms","()V"));
                            programHooks++;
                        }
                }
                else if(curvesEnabled&&
                        ("loadShaderPack".equals(method.name)||
                        "init".equals(method.name)))
                {
                    method.instructions.insert(call("reset","()V"));
                }
                else if(curvesEnabled&&"uninit".equals(method.name))
                {
                    method.instructions.insert(call("clearPrograms","()V"));
                }
            }
            boolean curveValid=curvesEnabled&&
                    readerHooks>=3&&programHooks>=1;
            if(curvesEnabled&&!curveValid)
                warn("OptiFine shader curve hooks incomplete ("+
                        readerHooks+" source, "+programHooks+
                        " program); preserving independent sun-shadow hook");
            if(shadowHooks<1&&!nativeShadowHookInstalled)
                shadowHookDiagnostic=
                        "Shaders.beginRender callsite not found; "+
                        "waiting for ShadersRender transform";
            if(shadowHooks>0)
                nativeShadowHookInstalled=true;
            if(!curveValid&&shadowHooks<1&&!actorEntityEnabled)return bytes;

            // If curves aren't recognized, strip any partial curve
            // insertions by re-reading the original bytecode and apply
            // only the shadow hook. Never leave a half-patched OptiFine.
            if(!curveValid)
            {
                node=new ClassNode(Opcodes.ASM5);
                new ClassReader(bytes).accept(node,0);
                for(Object obj:node.methods)
                {
                    MethodNode method=(MethodNode)obj;
                    if(!"beginRender".equals(method.name))continue;
                    for(AbstractInsnNode insn:
                            method.instructions.toArray())
                    {
                        if(!(insn instanceof MethodInsnNode))continue;
                        MethodInsnNode call=(MethodInsnNode)insn;
                        if("renderShadowMap".equals(call.name)&&
                                "net/optifine/shaders/ShadersRender"
                                        .equals(call.owner))
                            method.instructions.insert(insn,
                                    shadowCallback());
                    }
                }
            }
            // A conservative exact-method hook, placed at the ENTRY to
            // endEntities while gbuffers_entities and its draw buffers are
            // still active. Never call the hook inside Shaders.useProgram,
            // where late composite programs would corrupt the GBuffer.
            if(actorEntityEnabled)
            {
                for(Object obj:node.methods)
                {
                    MethodNode method=(MethodNode)obj;
                    if("endEntities".equals(method.name) &&
                            "()V".equals(method.desc))
                    {
                        method.instructions.insert(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,NATIVE_ACTORS,
                                "beforeOptiFineEndEntities","()V",false));
                        actorHooks++;
                    }
                }
                if(actorHooks>0)
                {
                    nativeEntityHookInstalled=true;
                    System.out.println("[BBS Editor][FS Preview] "+
                            "OptiFine native entity GBuffer hook installed.");
                }
            }
            if(!curveValid&&shadowHooks<1&&actorHooks<1)return bytes;
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            installed=curveValid;
            System.out.println("[BBS Editor][Curve] Native OptiFine "+
                    "shader source/program hooks installed ("+readerHooks+
                    "/"+programHooks+") nativeSunShadowHook="+shadowHooks+
                    " curves="+curveValid+" entityStageHooks="+actorHooks);
            return writer.toByteArray();
        }
        catch(Throwable incompatible)
        {
            warn("OptiFine shader hook failed: "+incompatible);
            return bytes;
        }
    }
    private static MethodInsnNode shadowCallback()
    {
        return new MethodInsnNode(Opcodes.INVOKESTATIC,SUN_SHADOW,
                "onNativeShadowMapCompleted","()V",false);
    }

    private static MethodInsnNode call(String name,String descriptor)
    {
        return new MethodInsnNode(Opcodes.INVOKESTATIC,RUNTIME,
                name,descriptor,false);
    }
    private static void warn(String message)
    {
        if(warned)return;
        warned=true;
        System.err.println("[BBS Editor][Curve] "+message);
    }
}
