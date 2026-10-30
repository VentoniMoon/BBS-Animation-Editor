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
    private static final String RUNTIME="com/example/examplemod/CameraShaderCurves";
    private static boolean warned;
    private static volatile boolean installed;

    public static boolean installed(){return installed;}

    @Override public byte[] transform(String name,String transformedName,
            byte[] bytes)
    {
        if(bytes==null)return null;
        if(!SHADERS.equals(name)&&!SHADERS.equals(transformedName))
            return bytes;
        try
        {
            ClassNode node=new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(node,0);
            int readerHooks=0,programHooks=0;
            for(Object o:node.methods)
            {
                MethodNode method=(MethodNode)o;
                if(method.name.matches("create(?:Vert|Geom|Frag|Comp)Shader"))
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
                else if("useProgram".equals(method.name))
                {
                    for(AbstractInsnNode instruction:method.instructions.toArray())
                        if(instruction.getOpcode()==Opcodes.RETURN)
                        {
                            method.instructions.insertBefore(instruction,call(
                                    "applyUniforms","()V"));
                            programHooks++;
                        }
                }
                else if("loadShaderPack".equals(method.name)||
                        "init".equals(method.name))
                {
                    method.instructions.insert(call("reset","()V"));
                }
                else if("uninit".equals(method.name))
                {
                    method.instructions.insert(call("clearPrograms","()V"));
                }
            }
            if(readerHooks<3||programHooks<1)
            {
                warn("OptiFine shader layout not recognized ("+
                        readerHooks+" source, "+programHooks+" program); "+
                        "shader Curve disabled");
                return bytes;
            }
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            installed=true;
            System.out.println("[BBS Editor][Curve] Native OptiFine "+
                    "shader source/program hooks installed ("+readerHooks+
                    "/"+programHooks+")");
            return writer.toByteArray();
        }
        catch(Throwable incompatible)
        {
            warn("OptiFine shader hook failed: "+incompatible);
            return bytes;
        }
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
