package com.example.examplemod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.vecmath.Vector3f;

import mchorse.chameleon.ClientProxy;
import mchorse.chameleon.lib.data.model.Model;
import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.data.model.ModelCube;
import mchorse.chameleon.lib.data.model.ModelQuad;
import mchorse.chameleon.lib.data.model.ModelVertex;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Exercises an ASM-transformed ChameleonRenderer equivalent with a real
 * rotating parent/child hair seam and checks cleanup on thrown render.
 * No Forge, game process, Jolt native library or Chameleon dependency needed.
 */
public final class ChameleonWeldNativeHookSmoke implements Opcodes
{
    public static final class Probe
    {
        static boolean throwOnDraw;
        static boolean expectWeld=true;
        static int draws;

        public static void view(Object raw)
        {
            Model model=(Model)raw;
            ModelBone parent=model.bones.get(0);
            ModelBone child=parent.children.get(0);
            Vector3f a=parent.cubes.get(0).quads.get(0).vertices.get(0).position;
            Vector3f b=child.cubes.get(0).quads.get(0).vertices.get(0).position;

            if(!expectWeld)
            {
                if(Math.abs(a.x-(-4.08F/16F))>1E-6F ||
                        Math.abs(b.x-(-4.08F/16F))>1E-6F)
                    throw new AssertionError("Non-hair model was unexpectedly welded");
                draws++;
                return;
            }

            float angle=(float)Math.toRadians(child.current.rotation.z);
            float c=(float)Math.cos(angle),s=(float)Math.sin(angle);
            float pivotY=9F/16F;
            float worldX=b.x*c-(b.y-pivotY)*s;
            float worldY=b.x*s+(b.y-pivotY)*c+pivotY;

            if(Math.abs(a.x-worldX)>1E-4F ||
                    Math.abs(a.y-worldY)>1E-4F ||
                    Math.abs(a.z-b.z)>1E-4F)
                throw new AssertionError("Native Chameleon Weld seam left a gap: "+
                        a.x+","+a.y+" vs "+worldX+","+worldY);
            if(Math.abs(a.x-(-4.08F/16F))<1E-3F)
                throw new AssertionError("Weld renderer never modified the parent seam");
            draws++;
            if(throwOnDraw)
                throw new IllegalStateException("deliberate renderer failure");
        }
    }

    private static final class TestLoader extends ClassLoader
    {
        TestLoader() {super(ChameleonWeldNativeHookSmoke.class.getClassLoader());}
        Class<?> define(byte[] bytes)
        {
            return defineClass("mchorse.chameleon.lib.render.ChameleonRenderer",
                    bytes,0,bytes.length);
        }
    }

    private static ModelVertex vertex(float x,float y,float z)
    {
        return new ModelVertex(x/16F,y/16F,z/16F);
    }

    private static ModelCube cube(float y,float sizeY)
    {
        ModelCube cube=new ModelCube();
        cube.origin.set(4F,y,1F);
        cube.size.set(8F,sizeY,1F);
        cube.inflate=0.08F;
        return cube;
    }

    private static void addFace(ModelCube cube,float y)
    {
        ModelQuad quad=new ModelQuad();
        quad.vertices.add(vertex(-4.08F,y,0.92F));
        quad.vertices.add(vertex(4.08F,y,0.92F));
        quad.vertices.add(vertex(4.08F,y,2.08F));
        quad.vertices.add(vertex(-4.08F,y,2.08F));
        cube.quads.add(quad);
    }

    private static byte[] originalRenderer()
    {
        String renderer="mchorse/chameleon/lib/render/ChameleonRenderer";
        ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8,ACC_PUBLIC|ACC_SUPER,renderer,null,
                "java/lang/Object",null);
        MethodVisitor m=cw.visitMethod(ACC_PUBLIC|ACC_STATIC,"render",
                "(Lmchorse/chameleon/lib/data/model/Model;)V",null,null);
        m.visitCode();
        m.visitVarInsn(ALOAD,0);
        m.visitMethodInsn(INVOKESTATIC,
                "com/example/examplemod/ChameleonWeldNativeHookSmoke$Probe",
                "view","(Ljava/lang/Object;)V",false);
        m.visitInsn(RETURN);
        m.visitMaxs(0,0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void near(float a,float b)
    {
        if(Math.abs(a-b)>1E-6F)throw new AssertionError(a+" != "+b);
    }

    public static void main(String[] args)throws Exception
    {
        System.setProperty("bbs.editor.weld.runtime","true");

        Model model=new Model();
        ModelBone parent=new ModelBone();
        ModelBone child=new ModelBone();
        parent.children.add(child);
        model.bones.add(parent);
        child.initial.translate.set(0,9,0);
        child.current.translate.set(0,9,0);
        child.current.rotation.set(0,0,45);

        ModelCube top=cube(9.08F,2.92F);
        ModelCube bottom=cube(6.08F,2.84F);
        addFace(top,9F);
        addFace(bottom,9F);
        parent.cubes.add(top);
        child.cubes.add(bottom);
        ClientProxy.chameleonModels.put("hair body",new ClientProxy.Wrapper(model));

        Vector3f oldParent=top.quads.get(0).vertices.get(0).position;
        Vector3f oldChild=bottom.quads.get(0).vertices.get(0).position;
        float origPX=oldParent.x,origPY=oldParent.y;
        float origCX=oldChild.x,origCY=oldChild.y;

        ChameleonWeldRendererTransformer transformer=
                new ChameleonWeldRendererTransformer();
        byte[] input=originalRenderer();
        byte[] patched=transformer.transform(
                "mchorse.chameleon.lib.render.ChameleonRenderer",
                "mchorse.chameleon.lib.render.ChameleonRenderer",input);
        if(!ChameleonWeldRendererTransformer.isPatched() ||
                patched.length==input.length)
            throw new AssertionError("Chameleon renderer was not instrumented");

        Class<?> renderer=new TestLoader().define(patched);
        Method render=renderer.getMethod("render",Model.class);
        render.invoke(null,model);
        near(oldParent.x,origPX);
        near(oldParent.y,origPY);
        near(oldChild.x,origCX);
        near(oldChild.y,origCY);

        Probe.throwOnDraw=true;
        try
        {
            render.invoke(null,model);
            throw new AssertionError("Expected simulated Chameleon draw failure");
        }
        catch(InvocationTargetException expected)
        {
            if(!(expected.getCause() instanceof IllegalStateException))
                throw new AssertionError(expected.getCause());
        }
        near(oldParent.x,origPX);
        near(oldParent.y,origPY);
        near(oldChild.x,origCX);
        near(oldChild.y,origCY);

        ClientProxy.chameleonModels.clear();
        ClientProxy.chameleonModels.put("another model",
                new ClientProxy.Wrapper(model));
        Probe.throwOnDraw=false;
        Probe.expectWeld=false;
        render.invoke(null,model);
        near(oldParent.x,origPX);
        near(oldChild.x,origCX);

        if(Probe.draws!=3)
            throw new AssertionError("Expected 2 hair Weld draws and 1 excluded draw");
        System.out.println("Native Chameleon Weld hook: seam, model filter and failure rollback PASS");
    }
}
