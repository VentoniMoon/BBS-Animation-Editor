package com.example.examplemod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Development-only SRG -> MCP compatibility bridge for production Mixins
 * from MalisisCore and other 1.12.2 mods.
 *
 * Injected Mixin method bodies may retain SRG invocation names even when
 * Mixin remaps their injection selectors through ForgeGradle's refmap.
 * ForgeGradle runClient uses MCP names in the target Minecraft classes.
 *
 * Two independently observed missing links in the full dev modpack:
 *   IBlockState.func_177230_c() -> IBlockState.getBlock()
 *   Chunk.func_177435_g(BlockPos) -> Chunk.getBlockState(BlockPos)
 *   CPacketPlayerDigging.func_179715_a() -> CPacketPlayerDigging.getPosition()
 *
 * Alias only when the MCP target exists and the SRG alias is absent.
 * This never changes vanilla semantics or edits any mod JAR, and is
 * registered only by the opt-in -PfixMixinAsm development profile.
 */
public final class DevIBlockStateSrgBridgeTransformer implements IClassTransformer
{
    private static final String BLOCK_STATE_TARGET =
            "net.minecraft.block.state.IBlockState";
    private static final String CHUNK_TARGET =
            "net.minecraft.world.chunk.Chunk";
    private static final String DIGGING_TARGET =
            "net.minecraft.network.play.client.CPacketPlayerDigging";

    private static final String BLOCK_STATE_OWNER =
            "net/minecraft/block/state/IBlockState";
    private static final String CHUNK_OWNER =
            "net/minecraft/world/chunk/Chunk";
    private static final String DIGGING_OWNER =
            "net/minecraft/network/play/client/CPacketPlayerDigging";

    private static final String GET_BLOCK_DESC =
            "()Lnet/minecraft/block/Block;";
    private static final String GET_BLOCK_STATE_DESC =
            "(Lnet/minecraft/util/math/BlockPos;)"
            + "Lnet/minecraft/block/state/IBlockState;";
    private static final String GET_DIGGING_POSITION_DESC =
            "()Lnet/minecraft/util/math/BlockPos;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        if(bytes == null)return null;

        boolean blockState = BLOCK_STATE_TARGET.equals(name)
                || BLOCK_STATE_TARGET.equals(transformedName);
        boolean chunk = CHUNK_TARGET.equals(name)
                || CHUNK_TARGET.equals(transformedName);
        boolean digging = DIGGING_TARGET.equals(name)
                || DIGGING_TARGET.equals(transformedName);
        if(!blockState && !chunk && !digging)return bytes;

        final String owner = blockState ? BLOCK_STATE_OWNER
                : chunk ? CHUNK_OWNER : DIGGING_OWNER;
        final String descriptor = blockState ? GET_BLOCK_DESC
                : chunk ? GET_BLOCK_STATE_DESC : GET_DIGGING_POSITION_DESC;
        final String srgName = blockState ? "func_177230_c"
                : chunk ? "func_177435_g" : "func_179715_a";
        final String mcpName = blockState ? "getBlock"
                : chunk ? "getBlockState" : "getPosition";

        try
        {
            ClassNode node = new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(node, 0);

            // Never add an alias to a non-matching class, or patch an
            // already SRG-mapped production class.
            if(!owner.equals(node.name))return bytes;

            boolean hasMcp = false;
            for(MethodNode method : node.methods)
            {
                if(descriptor.equals(method.desc)
                        && srgName.equals(method.name))
                {
                    System.out.println("[BBS Editor][MCP Bridge] "
                            + node.name + "." + srgName
                            + " already present, no patch");
                    return bytes;
                }
                if(descriptor.equals(method.desc)
                        && mcpName.equals(method.name))
                    hasMcp = true;
            }

            boolean isInterface =
                    (node.access & Opcodes.ACC_INTERFACE) != 0;
            if(!hasMcp || isInterface != blockState
                    || (blockState && node.version < Opcodes.V1_8))
            {
                System.err.println("[BBS Editor][MCP Bridge] Unexpected "
                        + node.name + " layout; did not add "
                        + srgName + " alias");
                return bytes;
            }

            // For IBlockState, Java 8 allows a concrete default interface
            // method. For Chunk, this is an ordinary public instance method.
            // All aliases invoke the ORIGINAL MCP method, preserving
            // virtual dispatch (including overridden methods in subclasses).
            // In particular, the digging packet's SRG getPosition bridge
            // is needed by MalisisCore's injected server-side handler; the
            // original bytecode otherwise disconnects the player on mining.
            MethodNode alias = new MethodNode(Opcodes.ACC_PUBLIC,
                    srgName, descriptor, null, null);
            alias.visitCode();
            alias.visitVarInsn(Opcodes.ALOAD, 0);
            if(chunk)alias.visitVarInsn(Opcodes.ALOAD, 1);
            alias.visitMethodInsn(blockState
                            ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
                    owner, mcpName, descriptor, blockState);
            alias.visitInsn(Opcodes.ARETURN);
            alias.visitMaxs(chunk ? 2 : 1, chunk ? 2 : 1);
            alias.visitEnd();
            node.methods.add(alias);

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            System.out.println("[BBS Editor][MCP Bridge] Added "
                    + node.name + "." + srgName + descriptor
                    + " -> " + mcpName + " for dev Mixin compatibility");
            return writer.toByteArray();
        }
        catch(Throwable error)
        {
            System.err.println("[BBS Editor][MCP Bridge] Error patching "
                    + transformedName + ": " + error);
            return bytes;
        }
    }
}
