package com.example.examplemod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Development-only SRG -> MCP compatibility bridge for an invocation copied
 * from MalisisCore 1.12's production MixinMalisisRenderer$MixinBlockModelShapes.
 *
 * The Malisis Mixin's @Inject body invokes IBlockState.func_177230_c() (SRG).
 * ForgeGradle runClient uses an MCP-mapped IBlockState.getBlock() instead.
 * Mixin refmap remapping fixes injection TARGET selectors but does not
 * rewrite already compiled INVOKEINTERFACE instructions in injected bodies.
 * Therefore the first walking particle can throw NoSuchMethodError.
 *
 * Add the original SRG spelling as a Java 8 default interface method which
 * delegates directly to MCP getBlock(). This preserves the exact semantics,
 * applies independently of Mixin transformer order, and never modifies the
 * Malisis JAR or the release version of our editor. Enabled only in the
 * opt-in -PfixMixinAsm ForgeGradle development profile.
 */
public final class DevIBlockStateSrgBridgeTransformer implements IClassTransformer
{
    private static final String TARGET =
            "net.minecraft.block.state.IBlockState";
    private static final String OWNER =
            "net/minecraft/block/state/IBlockState";
    private static final String BLOCK_DESC =
            "()Lnet/minecraft/block/Block;";
    private static final String SRG_NAME = "func_177230_c";
    private static final String MCP_NAME = "getBlock";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        if (bytes == null || (!TARGET.equals(name)
                && !TARGET.equals(transformedName)))
        {
            return bytes;
        }

        try
        {
            ClassNode node = new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(node, 0);
            boolean hasMcp = false;
            for (MethodNode method : node.methods)
            {
                if (BLOCK_DESC.equals(method.desc)
                        && SRG_NAME.equals(method.name))
                {
                    // Production/SRG Minecraft or another transformer
                    // already has this method: don't change anything.
                    System.out.println("[BBS Editor][MCP Bridge] IBlockState "
                            + SRG_NAME + " already present, no patch");
                    return bytes;
                }
                if (BLOCK_DESC.equals(method.desc)
                        && MCP_NAME.equals(method.name))
                {
                    hasMcp = true;
                }
            }

            if (!hasMcp || (node.access & Opcodes.ACC_INTERFACE) == 0
                    || node.version < Opcodes.V1_8)
            {
                System.err.println("[BBS Editor][MCP Bridge] Unexpected "
                        + "IBlockState layout; did not add SRG alias");
                return bytes;
            }

            MethodNode alias = new MethodNode(
                    Opcodes.ACC_PUBLIC, SRG_NAME, BLOCK_DESC, null, null);
            alias.visitCode();
            alias.visitVarInsn(Opcodes.ALOAD, 0);
            alias.visitMethodInsn(Opcodes.INVOKEINTERFACE, OWNER,
                    MCP_NAME, BLOCK_DESC, true);
            alias.visitInsn(Opcodes.ARETURN);
            alias.visitMaxs(1, 1);
            alias.visitEnd();
            node.methods.add(alias);

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            System.out.println("[BBS Editor][MCP Bridge] Added IBlockState."
                    + SRG_NAME + "() -> getBlock() for MalisisCore Mixin");
            return writer.toByteArray();
        }
        catch (Throwable error)
        {
            System.err.println("[BBS Editor][MCP Bridge] Error: " + error);
            return bytes;
        }
    }
}
