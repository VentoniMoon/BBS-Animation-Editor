package com.example.examplemod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Narrow Forge 1.12.2 development compatibility fallback.
 *
 * In a ForgeGradle runClient with newer ASM for BetterLights Mixin, the
 * old EventSubscriptionTransformer can leave vanilla Forge event subclasses
 * without their generated ListenerList overrides. Such events then inherit
 * Event.getListenerList() and unrelated handlers are invoked (for example,
 * a Techguns MouseEvent subscriber while posting RegistryEvent.NewRegistry).
 *
 * This does NOT replace Forge's event bus or filter legitimate subscribers.
 * It applies Forge's own generated ListenerList pattern to the two affected
 * event types, but only if those methods have not already been generated.
 *
 * Enabled only with -Dbbs.editor.forgeEventIsolation=true via the
 * -PfixMixinAsm developer launch. Never enabled in packaged builds by default.
 */
public final class LegacyForgeEventIsolationTransformer implements IClassTransformer
{
    private static final String REGISTRY_EVENT =
            "net.minecraftforge.event.RegistryEvent$NewRegistry";
    private static final String MOUSE_EVENT =
            "net.minecraftforge.client.event.MouseEvent";
    private static final String LIST = "net/minecraftforge/fml/common/eventhandler/ListenerList";
    private static final String LIST_DESC = "L" + LIST + ";";
    private static final String GET_LIST_DESC = "()" + LIST_DESC;

    private static boolean matches(String name)
    {
        return REGISTRY_EVENT.equals(name) || MOUSE_EVENT.equals(name);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        if (bytes == null || (!matches(name) && !matches(transformedName)))
        {
            return bytes;
        }

        try
        {
            ClassNode node = new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(node, 0);

            boolean setup = false;
            boolean listGetter = false;
            boolean listField = false;
            for (MethodNode method : (List<MethodNode>) node.methods)
            {
                if ("setup".equals(method.name) && "()V".equals(method.desc))
                {
                    setup = true;
                }
                if ("getListenerList".equals(method.name)
                        && GET_LIST_DESC.equals(method.desc))
                {
                    listGetter = true;
                }
            }
            for (FieldNode field : (List<FieldNode>) node.fields)
            {
                if ("LISTENER_LIST".equals(field.name))
                {
                    listField = true;
                }
            }

            if (setup && listGetter)
            {
                // The original Forge event transformer already did its job.
                return bytes;
            }
            if (setup || listGetter || listField)
            {
                // Never produce duplicate or half-overlapping Forge event
                // methods if a different coremod has partially changed them.
                System.err.println("[BBS Editor][ForgeEventIsolation] Incomplete event "
                        + node.name + " setup=" + setup + " getter=" + listGetter
                        + " field=" + listField + "; skipping invasive patch");
                return bytes;
            }

            // Reproduce exactly the per-event listener-list semantics from
            // Forge 1.12.2 EventSubscriptionTransformer.buildEvents().
            node.fields.add(new FieldNode(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    "LISTENER_LIST", LIST_DESC, null, null));

            MethodNode init = new MethodNode(
                    Opcodes.ACC_PROTECTED, "setup", "()V", null, null);
            init.visitCode();
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, node.superName,
                    "setup", "()V", false);
            init.visitFieldInsn(Opcodes.GETSTATIC, node.name,
                    "LISTENER_LIST", LIST_DESC);
            org.objectweb.asm.Label create = new org.objectweb.asm.Label();
            init.visitJumpInsn(Opcodes.IFNULL, create);
            init.visitInsn(Opcodes.RETURN);
            init.visitLabel(create);
            init.visitTypeInsn(Opcodes.NEW, LIST);
            init.visitInsn(Opcodes.DUP);
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, node.superName,
                    "getListenerList", GET_LIST_DESC, false);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, LIST,
                    "<init>", "(" + LIST_DESC + ")V", false);
            init.visitFieldInsn(Opcodes.PUTSTATIC, node.name,
                    "LISTENER_LIST", LIST_DESC);
            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(3, 1);
            init.visitEnd();
            node.methods.add(init);

            MethodNode getList = new MethodNode(
                    Opcodes.ACC_PUBLIC, "getListenerList", GET_LIST_DESC,
                    null, null);
            getList.visitCode();
            getList.visitFieldInsn(Opcodes.GETSTATIC, node.name,
                    "LISTENER_LIST", LIST_DESC);
            getList.visitInsn(Opcodes.ARETURN);
            getList.visitMaxs(1, 1);
            getList.visitEnd();
            node.methods.add(getList);

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS |
                    ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            System.out.println("[BBS Editor][ForgeEventIsolation] Repaired "
                    + node.name + " separate ListenerList in Forge 1.12 dev runtime");
            return writer.toByteArray();
        }
        catch (Throwable error)
        {
            System.err.println("[BBS Editor][ForgeEventIsolation] Failed for "
                    + transformedName + ": " + error);
            // Fail closed: do not return potentially corrupted event bytecode.
            return bytes;
        }
    }
}
