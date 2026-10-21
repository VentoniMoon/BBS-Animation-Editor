package com.example.examplemod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Java8 integration test for injected morph NBT, copy, merge and render context. */
public final class ChameleonWeldMorphHookSmoke implements Opcodes
{
    public static final class Delegate
    {
        static boolean throwOnDraw;
        static boolean expected;

        public static void draw()
        {
            try
            {
                Method m=ChameleonWeldRuntime.class.getDeclaredMethod("currentMorphWeld");
                m.setAccessible(true);
                boolean active=(Boolean)m.invoke(null);
                if(active!=expected)
                    throw new AssertionError("Morph render scope wrong: "+active);
            }
            catch(ReflectiveOperationException error)
            {
                throw new AssertionError(error);
            }
            if(throwOnDraw)throw new IllegalStateException("render failure");
        }
    }

    private static final class Loader extends ClassLoader
    {
        Loader(){super(ChameleonWeldMorphHookSmoke.class.getClassLoader());}
        Class<?> define(byte[] b)
        {
            return defineClass("mchorse.chameleon.metamorph.ChameleonMorph",b,0,b.length);
        }
    }

    private static void method(ClassWriter cw,String name,String desc,int returned)
    {
        MethodVisitor mv=cw.visitMethod(ACC_PUBLIC,name,desc,null,null);
        mv.visitCode();
        mv.visitInsn(returned);
        mv.visitMaxs(1,2);
        mv.visitEnd();
    }

    private static byte[] rawMorph()
    {
        String cls="mchorse/chameleon/metamorph/ChameleonMorph";
        String parent="mchorse/metamorph/api/morphs/AbstractMorph";
        ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8,ACC_PUBLIC|ACC_SUPER,cls,null,parent,null);
        MethodVisitor ctor=cw.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD,0);
        ctor.visitMethodInsn(INVOKESPECIAL,parent,"<init>","()V",false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(1,1);
        ctor.visitEnd();

        method(cw,"toNBT","(Lnet/minecraft/nbt/NBTTagCompound;)V",RETURN);
        method(cw,"fromNBT","(Lnet/minecraft/nbt/NBTTagCompound;)V",RETURN);
        method(cw,"copy","(L"+parent+";)V",RETURN);
        MethodVisitor canMerge=cw.visitMethod(ACC_PUBLIC,"canMerge",
                "(L"+parent+";)Z",null,null);
        canMerge.visitCode();
        canMerge.visitInsn(ICONST_1);
        canMerge.visitInsn(IRETURN);
        canMerge.visitMaxs(1,2);
        canMerge.visitEnd();
        MethodVisitor rm=cw.visitMethod(ACC_PRIVATE,"renderModel",
                "(Lnet/minecraft/entity/EntityLivingBase;F)V",null,null);
        rm.visitCode();
        rm.visitMethodInsn(INVOKESTATIC,
                "com/example/examplemod/ChameleonWeldMorphHookSmoke$Delegate",
                "draw","()V",false);
        rm.visitInsn(RETURN);
        rm.visitMaxs(1,3);
        rm.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    public static void main(String[] args)throws Exception
    {
        byte[] original=rawMorph();
        ChameleonWeldMorphTransformer transformer=new ChameleonWeldMorphTransformer();
        byte[] injected=transformer.transform(
                "mchorse.chameleon.metamorph.ChameleonMorph",
                "mchorse.chameleon.metamorph.ChameleonMorph",original);
        if(!ChameleonWeldMorphTransformer.isPatched())
            throw new AssertionError("Morph transform missing");

        Class<?> cls=new Loader().define(injected);
        Object on=cls.newInstance();
        Object off=cls.newInstance();
        ChameleonWeldRuntime.setMorphWeld(on,true);
        if(!ChameleonWeldRuntime.isMorphWeldEnabled(on) ||
                ChameleonWeldRuntime.isMorphWeldEnabled(off))
            throw new AssertionError("Per-instance Weld field not independent");

        Method write=cls.getMethod("toNBT",NBTTagCompound.class);
        Method read=cls.getMethod("fromNBT",NBTTagCompound.class);
        Method copy=cls.getMethod("copy",AbstractMorph.class);
        Method merge=cls.getMethod("canMerge",AbstractMorph.class);
        NBTTagCompound tag=new NBTTagCompound();
        write.invoke(on,tag);
        if(!tag.getBoolean("BBSWeld"))
            throw new AssertionError("Weld was not exported into morph NBT");
        read.invoke(off,tag);
        if(!ChameleonWeldRuntime.isMorphWeldEnabled(off))
            throw new AssertionError("Weld was not restored from morph NBT");

        Object c=cls.newInstance();
        copy.invoke(c,on);
        if(!ChameleonWeldRuntime.isMorphWeldEnabled(c))
            throw new AssertionError("Morph copy lost Weld");
        Object no=cls.newInstance();
        merge.invoke(c,no);
        if(ChameleonWeldRuntime.isMorphWeldEnabled(c))
            throw new AssertionError("Morph merge kept obsolete Weld");

        Method render=cls.getDeclaredMethod("renderModel",EntityLivingBase.class,float.class);
        render.setAccessible(true);
        Delegate.expected=true;
        render.invoke(on,new EntityLivingBase(),0F);
        Delegate.expected=false;
        Delegate.throwOnDraw=true;
        try
        {
            render.invoke(off,new EntityLivingBase(),0F);
            throw new AssertionError("Expected render failure");
        }
        catch(InvocationTargetException expected)
        {
            if(!(expected.getCause() instanceof IllegalStateException))
                throw new AssertionError(expected.getCause());
        }
        Delegate.throwOnDraw=false;
        // No leaked context after exception.
        Delegate.draw();
        System.out.println("Chameleon per-morph Weld NBT/copy/merge/scope PASS");
    }
}
