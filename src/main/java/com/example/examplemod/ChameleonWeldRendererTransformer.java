package com.example.examplemod;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Forge 1.12.2 LaunchClassLoader transformer registered at mod preInit.
 *
 * Wraps ChameleonRenderer.render(Model), rather than trying to modify the
 * Blockbuster record or its Jolt physics. The original renderer receives
 * unmodified semantics and draw order; the helper adjusts only the model's
 * visible vertices for the duration of this single draw.
 *
 * A wrapper with a bytecode try/finally is intentional: shared ModelVertex
 * objects must ALWAYS be restored, including when another rendering mod
 * throws during ChameleonRenderer.render.
 */
public final class ChameleonWeldRendererTransformer implements IClassTransformer,
        Opcodes
{
    private static final String RENDERER=
            "mchorse/chameleon/lib/render/ChameleonRenderer";
    private static final String MODEL=
            "Lmchorse/chameleon/lib/data/model/Model;";
    private static final String DESC="("+MODEL+")V";
    private static final String RUNTIME=
            "com/example/examplemod/ChameleonWeldRuntime";

    private static volatile boolean patched;
    private static volatile boolean warned;

    public static boolean isPatched()
    {
        return patched;
    }

    @Override
    public byte[] transform(String name,String transformedName,byte[] bytes)
    {
        if(bytes==null)return null;
        String actual=transformedName==null?name:transformedName;
        if(!RENDERER.equals(actual.replace('.','/')) &&
                (name==null || !RENDERER.equals(name.replace('.','/'))))
            return bytes;

        try
        {
            ClassNode node=new ClassNode(ASM5);
            new ClassReader(bytes).accept(node,ClassReader.EXPAND_FRAMES);
            MethodNode original=null;
            for(MethodNode m:node.methods)
            {
                if("render".equals(m.name) && DESC.equals(m.desc) &&
                        (m.access&ACC_STATIC)!=0)
                    original=m;
                if("bbs$renderUnwelded".equals(m.name))
                {
                    patched=true;
                    return bytes;
                }
            }
            if(original==null)
            {
                System.err.println("[BBS Editor][Weld] No native render(Model) target");
                return bytes;
            }

            // Move Chameleon's unchanged implementation behind our wrapper.
            original.name="bbs$renderUnwelded";
            original.access=(original.access & ~(ACC_PUBLIC|ACC_PROTECTED)) |
                    ACC_PRIVATE | ACC_SYNTHETIC;

            MethodNode wrapper=new MethodNode(ASM5,ACC_PUBLIC|ACC_STATIC,
                    "render",DESC,null,null);
            InsnList il=wrapper.instructions;
            LabelNode begin=new LabelNode();
            LabelNode done=new LabelNode();
            LabelNode handler=new LabelNode();

            wrapper.tryCatchBlocks.add(
                    new TryCatchBlockNode(begin,done,handler,null));
            il.add(begin);
            il.add(new VarInsnNode(ALOAD,0));
            il.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,
                    "begin","(Ljava/lang/Object;)V",false));
            il.add(new VarInsnNode(ALOAD,0));
            il.add(new MethodInsnNode(INVOKESTATIC,RENDERER,
                    "bbs$renderUnwelded",DESC,false));
            il.add(done);
            il.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,
                    "end","()V",false));
            il.add(new InsnNode(RETURN));
            il.add(handler);
            il.add(new VarInsnNode(ASTORE,1));
            il.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,
                    "end","()V",false));
            il.add(new VarInsnNode(ALOAD,1));
            il.add(new InsnNode(ATHROW));
            node.methods.add(wrapper);

            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES |
                    ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            patched=true;
            System.out.println("[BBS Editor][Weld] Native ChameleonRenderer.render(Model) hooked");
            return writer.toByteArray();
        }
        catch(Throwable error)
        {
            if(!warned)
            {
                warned=true;
                System.err.println("[BBS Editor][Weld] Native renderer hook failed: "+
                        error.getClass().getSimpleName()+": "+error.getMessage());
            }
            return bytes;
        }
    }
}
