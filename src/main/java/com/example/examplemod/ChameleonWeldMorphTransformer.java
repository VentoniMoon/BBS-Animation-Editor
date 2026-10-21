package com.example.examplemod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds the per-instance Weld flag to ChameleonMorph and preserves it across
 * Chameleon's own NBT serialization, copy, merge and render.
 *
 * No changes to Metamorph libraries, physics, keys or native Chameleon
 * animation. Bytecode uses ASM 5 / Java 8 compatible instructions.
 */
public final class ChameleonWeldMorphTransformer implements IClassTransformer,Opcodes
{
    private static final String MORPH="mchorse/chameleon/metamorph/ChameleonMorph";
    private static final String RUNTIME="com/example/examplemod/ChameleonWeldRuntime";
    private static final String NBT="Lnet/minecraft/nbt/NBTTagCompound;";
    private static final String BASE="Lmchorse/metamorph/api/morphs/AbstractMorph;";
    private static final String ENTITY="Lnet/minecraft/entity/EntityLivingBase;";

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
        String n=transformedName==null?name:transformedName;
        if(!MORPH.equals(n.replace('.','/')) &&
                (name==null || !MORPH.equals(name.replace('.','/'))))
            return bytes;
        try
        {
            ClassNode node=new ClassNode(ASM5);
            new ClassReader(bytes).accept(node,ClassReader.EXPAND_FRAMES);
            for(Object o:node.fields)
                if("bbs$weld".equals(((FieldNode)o).name))
                {
                    patched=true;
                    return bytes;
                }

            MethodNode render=null,read=null,write=null,copy=null,merge=null;
            for(Object o:node.methods)
            {
                MethodNode m=(MethodNode)o;
                if(m.name.equals("renderModel") &&
                        m.desc.equals("("+ENTITY+"F)V"))render=m;
                if(m.name.equals("fromNBT") &&
                        m.desc.equals("("+NBT+")V"))read=m;
                if(m.name.equals("toNBT") &&
                        m.desc.equals("("+NBT+")V"))write=m;
                if(m.name.equals("copy") &&
                        m.desc.equals("("+BASE+")V"))copy=m;
                if(m.name.equals("canMerge") &&
                        m.desc.equals("("+BASE+")Z"))merge=m;
            }
            if(render==null||read==null||write==null||copy==null||merge==null)
            {
                System.err.println("[BBS Editor][Weld] ChameleonMorph method "+
                        "layout not recognized, per-instance Weld disabled");
                return bytes;
            }

            node.fields.add(new FieldNode(ASM5,ACC_PUBLIC|ACC_SYNTHETIC,
                    "bbs$weld","Z",null,null));
            node.methods.add(wrapVoid(read,"bbs$originalFromNBT",
                    "readMorph","(Ljava/lang/Object;"+NBT+")V"));
            node.methods.add(wrapVoid(write,"bbs$originalToNBT",
                    "writeMorph","(Ljava/lang/Object;"+NBT+")V"));
            node.methods.add(wrapVoid(copy,"bbs$originalCopy",
                    "copyMorph","(Ljava/lang/Object;Ljava/lang/Object;)V"));
            node.methods.add(wrapRender(render));
            node.methods.add(wrapMerge(merge));

            ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_MAXS |
                    ClassWriter.COMPUTE_FRAMES);
            node.accept(cw);
            patched=true;
            System.out.println("[BBS Editor][Weld] ChameleonMorph per-part NBT "+
                    "and render context hooked");
            return cw.toByteArray();
        }
        catch(Throwable fail)
        {
            if(!warned)
            {
                warned=true;
                System.err.println("[BBS Editor][Weld] Morph hook failed: "+
                        fail.getClass().getSimpleName()+": "+fail.getMessage());
            }
            return bytes;
        }
    }

    private static MethodNode wrapVoid(MethodNode old,String hidden,
            String callback,String callbackDesc)
    {
        String desc=old.desc;
        int access=old.access;
        old.name=hidden;
        old.access=(old.access & ~(ACC_PUBLIC|ACC_PROTECTED)) |
                ACC_PRIVATE|ACC_SYNTHETIC;
        MethodNode w=new MethodNode(ASM5,access,"",desc,null,null);
        if("bbs$originalFromNBT".equals(hidden))w.name="fromNBT";
        else if("bbs$originalToNBT".equals(hidden))w.name="toNBT";
        else w.name="copy";
        InsnList il=w.instructions;
        il.add(new VarInsnNode(ALOAD,0));
        il.add(new VarInsnNode(ALOAD,1));
        il.add(new MethodInsnNode(INVOKESPECIAL,MORPH,hidden,desc,false));
        il.add(new VarInsnNode(ALOAD,0));
        il.add(new VarInsnNode(ALOAD,1));
        il.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,callback,callbackDesc,false));
        il.add(new InsnNode(RETURN));
        return w;
    }

    private static MethodNode wrapRender(MethodNode old)
    {
        String desc=old.desc;
        int access=old.access;
        old.name="bbs$originalRenderModel";
        old.access=(old.access & ~(ACC_PUBLIC|ACC_PROTECTED)) |
                ACC_PRIVATE|ACC_SYNTHETIC;
        MethodNode w=new MethodNode(ASM5,access,"renderModel",desc,null,null);
        InsnList il=w.instructions;
        LabelNode start=new LabelNode(),done=new LabelNode(),handler=new LabelNode();
        il.add(new VarInsnNode(ALOAD,0));
        il.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"enterMorph",
                "(Ljava/lang/Object;)V",false));
        il.add(start);
        il.add(new VarInsnNode(ALOAD,0));
        il.add(new VarInsnNode(ALOAD,1));
        il.add(new VarInsnNode(FLOAD,2));
        il.add(new MethodInsnNode(INVOKESPECIAL,MORPH,
                "bbs$originalRenderModel",desc,false));
        il.add(done);
        il.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"exitMorph","()V",false));
        il.add(new InsnNode(RETURN));
        il.add(handler);
        il.add(new VarInsnNode(ASTORE,3));
        il.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"exitMorph","()V",false));
        il.add(new VarInsnNode(ALOAD,3));
        il.add(new InsnNode(ATHROW));
        w.tryCatchBlocks.add(new TryCatchBlockNode(start,done,handler,null));
        return w;
    }

    private static MethodNode wrapMerge(MethodNode old)
    {
        String desc=old.desc;
        int access=old.access;
        old.name="bbs$originalCanMerge";
        old.access=(old.access & ~(ACC_PUBLIC|ACC_PROTECTED)) |
                ACC_PRIVATE|ACC_SYNTHETIC;
        MethodNode w=new MethodNode(ASM5,access,"canMerge",desc,null,null);
        InsnList il=w.instructions;
        il.add(new VarInsnNode(ALOAD,0));
        il.add(new VarInsnNode(ALOAD,1));
        il.add(new MethodInsnNode(INVOKESPECIAL,MORPH,
                "bbs$originalCanMerge",desc,false));
        il.add(new VarInsnNode(ISTORE,2));
        il.add(new VarInsnNode(ALOAD,0));
        il.add(new VarInsnNode(ALOAD,1));
        il.add(new VarInsnNode(ILOAD,2));
        il.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"mergeMorph",
                "(Ljava/lang/Object;Ljava/lang/Object;Z)V",false));
        il.add(new VarInsnNode(ILOAD,2));
        il.add(new InsnNode(IRETURN));
        return w;
    }
}
