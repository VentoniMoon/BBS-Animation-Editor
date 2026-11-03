package com.example.examplemod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Java 8 / ForgeGradle 3 developer-only compatibility guard for
 * MinecraftForge/ForgeGradle#748 (unexpected Side.BUKKIT).
 *
 * Forge 1.12.2 NetworkRegistry initializes channel maps for CLIENT and
 * SERVER only, but newChannel() iterates over Side.values(). A contaminated
 * development Side enum can include BUKKIT, so channels.get(BUKKIT) is null.
 *
 * Replace ONLY the Side.values() array used by newChannel() with exactly
 * the two legal network sides. Do not change the Side enum, Forge event bus,
 * other methods or other mods. Enabled only in opt-in ASM stress profile.
 */
public final class ForgeNetworkSideGuardTransformer implements IClassTransformer
{
    private static final String TARGET =
            "net.minecraftforge.fml.common.network.NetworkRegistry";
    private static final String SIDE =
            "net/minecraftforge/fml/relauncher/Side";
    private static final String SIDE_DESCRIPTOR = "L" + SIDE + ";";
    private static final String VALUES_DESCRIPTOR = "()[" + SIDE_DESCRIPTOR;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        if (bytes == null ||
                (!TARGET.equals(name) && !TARGET.equals(transformedName)))
        {
            return bytes;
        }

        try
        {
            ClassNode node = new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(node, 0);
            int replaced = 0;

            for (MethodNode method : node.methods)
            {
                if (!"newChannel".equals(method.name))
                {
                    continue;
                }

                for (AbstractInsnNode insn : method.instructions.toArray())
                {
                    if (!(insn instanceof MethodInsnNode))
                    {
                        continue;
                    }

                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.getOpcode() != Opcodes.INVOKESTATIC ||
                            !SIDE.equals(call.owner) ||
                            !"values".equals(call.name) ||
                            !VALUES_DESCRIPTOR.equals(call.desc))
                    {
                        continue;
                    }

                    InsnList safeSides = new InsnList();
                    safeSides.add(new InsnNode(Opcodes.ICONST_2));
                    safeSides.add(new TypeInsnNode(Opcodes.ANEWARRAY, SIDE));

                    safeSides.add(new InsnNode(Opcodes.DUP));
                    safeSides.add(new InsnNode(Opcodes.ICONST_0));
                    safeSides.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            SIDE, "CLIENT", SIDE_DESCRIPTOR));
                    safeSides.add(new InsnNode(Opcodes.AASTORE));

                    safeSides.add(new InsnNode(Opcodes.DUP));
                    safeSides.add(new InsnNode(Opcodes.ICONST_1));
                    safeSides.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            SIDE, "SERVER", SIDE_DESCRIPTOR));
                    safeSides.add(new InsnNode(Opcodes.AASTORE));

                    method.instructions.insertBefore(insn, safeSides);
                    method.instructions.remove(insn);
                    replaced++;
                }
            }

            if (replaced == 0)
            {
                System.err.println("[BBS Editor][ForgeSideGuard] Did not find "
                        + "NetworkRegistry.newChannel Side.values() site; "
                        + "no bytecode changed");
                return bytes;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            System.out.println("[BBS Editor][ForgeSideGuard] NetworkRegistry."
                    + "newChannel uses only CLIENT/SERVER; patched sites="
                    + replaced);
            return writer.toByteArray();
        }
        catch (Throwable failure)
        {
            System.err.println("[BBS Editor][ForgeSideGuard] Patch failed: "
                    + failure);
            return bytes;
        }
    }
}
