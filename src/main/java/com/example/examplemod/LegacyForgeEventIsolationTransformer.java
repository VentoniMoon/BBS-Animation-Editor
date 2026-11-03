package com.example.examplemod;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Development-only fallback for Forge 1.12.2 EventSubscriptionTransformer.
 *
 * The full BetterLights modpack needs ASM 9.x for its newer Mixin runtime.
 * In that launch configuration some Forge Event subclasses can reach the
 * event bus without their own listener lists. A RegistryEvent.NewRegistry
 * then receives unrelated MouseEvent/IContextSetter subscribers and crashes.
 *
 * Do not filter subscriptions or modify EventBus: repair the underlying
 * missing bytecode using Forge's own EventSubscriptionTransformer pattern.
 * Already transformed classes are left unchanged. Only enabled for the
 * explicit -PfixMixinAsm developer profile, never a normal published mod.
 */
public final class LegacyForgeEventIsolationTransformer implements IClassTransformer
{
    private static final String EVENT =
            "net/minecraftforge/fml/common/eventhandler/Event";
    private static final String LIST =
            "net/minecraftforge/fml/common/eventhandler/ListenerList";
    private static final String LIST_DESC = "L" + LIST + ";";
    private static final String GET_LIST_DESC = "()" + LIST_DESC;
    private static final String CANCELABLE =
            "Lnet/minecraftforge/fml/common/eventhandler/Cancelable;";
    private static final String HAS_RESULT =
            "Lnet/minecraftforge/fml/common/eventhandler/Event$HasResult;";

    private static boolean isEventSubclass(ClassNode node)
    {
        if (EVENT.equals(node.name) || node.superName == null)
        {
            return false;
        }
        if (EVENT.equals(node.superName))
        {
            return true;
        }
        try
        {
            // Forge 1.12.2's own EventSubscriptionTransformer uses the
            // transformer class loader to resolve an indirect event parent.
            Class<?> parent = LegacyForgeEventIsolationTransformer.class
                    .getClassLoader().loadClass(
                            node.superName.replace('/', '.'));
            return Event.class.isAssignableFrom(parent);
        }
        catch (ClassNotFoundException ignored)
        {
            // An unresolved parent must not be speculatively patched.
            return false;
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        if (bytes == null || name == null
                || name.startsWith("net.minecraft.")
                || name.equals("net.minecraftforge.fml.common.eventhandler.Event")
                || name.indexOf('.') < 0)
        {
            return bytes;
        }

        try
        {
            ClassNode node = new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(node, 0);
            if (!isEventSubclass(node))
            {
                return bytes;
            }

            boolean hasSetup = false;
            boolean hasGetter = false;
            boolean hasListField = false;
            boolean hasNoArgsConstructor = false;
            boolean hasCancelable = false;
            boolean hasResult = false;
            for (MethodNode method : node.methods)
            {
                if ("setup".equals(method.name) && "()V".equals(method.desc))
                {
                    hasSetup = true;
                }
                if ("getListenerList".equals(method.name)
                        && GET_LIST_DESC.equals(method.desc))
                {
                    hasGetter = true;
                }
                if ("<init>".equals(method.name) && "()V".equals(method.desc))
                {
                    hasNoArgsConstructor = true;
                }
                if ("isCancelable".equals(method.name)
                        && "()Z".equals(method.desc))
                {
                    hasCancelable = true;
                }
                if ("hasResult".equals(method.name) && "()Z".equals(method.desc))
                {
                    hasResult = true;
                }
            }
            for (FieldNode field : node.fields)
            {
                if ("LISTENER_LIST".equals(field.name))
                {
                    hasListField = true;
                }
            }

            // Never layer a second set of methods onto a partially
            // transformed class: preserve its bytes for diagnostics.
            if ((hasSetup || hasGetter || hasListField)
                    && !(hasSetup && hasGetter && hasListField))
            {
                System.err.println("[BBS Editor][ForgeEventIsolation] Partial "
                        + node.name + " setup=" + hasSetup
                        + " getter=" + hasGetter + " field=" + hasListField);
                return bytes;
            }

            boolean changed = false;
            if (node.visibleAnnotations != null)
            {
                for (AnnotationNode annotation : node.visibleAnnotations)
                {
                    if (!hasCancelable && CANCELABLE.equals(annotation.desc))
                    {
                        addTrueMethod(node, "isCancelable");
                        changed = true;
                    }
                    if (!hasResult && HAS_RESULT.equals(annotation.desc))
                    {
                        addTrueMethod(node, "hasResult");
                        changed = true;
                    }
                }
            }

            if (!hasSetup)
            {
                // Forge adds a public no-argument constructor to event
                // classes lacking one. EventBus.register() requires it.
                if (!hasNoArgsConstructor)
                {
                    MethodNode constructor = new MethodNode(
                            Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                    constructor.visitCode();
                    constructor.visitVarInsn(Opcodes.ALOAD, 0);
                    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                            node.superName, "<init>", "()V", false);
                    constructor.visitInsn(Opcodes.RETURN);
                    constructor.visitMaxs(1, 1);
                    constructor.visitEnd();
                    node.methods.add(constructor);
                }

                node.fields.add(new FieldNode(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "LISTENER_LIST", LIST_DESC, null, null));

                MethodNode setup = new MethodNode(
                        Opcodes.ACC_PROTECTED, "setup", "()V", null, null);
                setup.visitCode();
                setup.visitVarInsn(Opcodes.ALOAD, 0);
                setup.visitMethodInsn(Opcodes.INVOKESPECIAL, node.superName,
                        "setup", "()V", false);
                setup.visitFieldInsn(Opcodes.GETSTATIC, node.name,
                        "LISTENER_LIST", LIST_DESC);
                Label create = new Label();
                setup.visitJumpInsn(Opcodes.IFNULL, create);
                setup.visitInsn(Opcodes.RETURN);
                setup.visitLabel(create);
                setup.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                setup.visitTypeInsn(Opcodes.NEW, LIST);
                setup.visitInsn(Opcodes.DUP);
                setup.visitVarInsn(Opcodes.ALOAD, 0);
                setup.visitMethodInsn(Opcodes.INVOKESPECIAL, node.superName,
                        "getListenerList", GET_LIST_DESC, false);
                setup.visitMethodInsn(Opcodes.INVOKESPECIAL, LIST,
                        "<init>", "(" + LIST_DESC + ")V", false);
                setup.visitFieldInsn(Opcodes.PUTSTATIC, node.name,
                        "LISTENER_LIST", LIST_DESC);
                setup.visitInsn(Opcodes.RETURN);
                setup.visitMaxs(3, 1);
                setup.visitEnd();
                node.methods.add(setup);

                MethodNode getter = new MethodNode(
                        Opcodes.ACC_PUBLIC, "getListenerList",
                        GET_LIST_DESC, null, null);
                getter.visitCode();
                getter.visitFieldInsn(Opcodes.GETSTATIC, node.name,
                        "LISTENER_LIST", LIST_DESC);
                getter.visitInsn(Opcodes.ARETURN);
                getter.visitMaxs(1, 1);
                getter.visitEnd();
                node.methods.add(getter);
                changed = true;
            }

            if (!changed)
            {
                return bytes;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            System.out.println("[BBS Editor][ForgeEventIsolation] Restored "
                    + node.name + " event wiring");
            return writer.toByteArray();
        }
        catch (Throwable error)
        {
            System.err.println("[BBS Editor][ForgeEventIsolation] Failed "
                    + transformedName + ": " + error);
            // Never return half-transformed bytecode.
            return bytes;
        }
    }

    private static void addTrueMethod(ClassNode node, String name)
    {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC, name, "()Z", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        node.methods.add(method);
    }
}
