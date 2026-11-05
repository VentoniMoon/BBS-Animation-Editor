package com.example.examplemod;

import java.util.Arrays;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Java 8 standalone ASM smoke test for the MCP <-> SRG development bridges.
 * Synthetic classes intentionally mimic the two vanilla 1.12.2 mappings.
 */
public final class DevSrgBridgeSmokeTest
{
    private static final String IFACE = "net/minecraft/block/state/IBlockState";
    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String DIGGING =
            "net/minecraft/network/play/client/CPacketPlayerDigging";
    private static final String IFACE_DESC = "()Lnet/minecraft/block/Block;";
    private static final String CHUNK_DESC =
            "(Lnet/minecraft/util/math/BlockPos;)"
                    + "Lnet/minecraft/block/state/IBlockState;";
    private static final String DIGGING_DESC =
            "()Lnet/minecraft/util/math/BlockPos;";

    private DevSrgBridgeSmokeTest(){}

    public static void main(String[] args)
    {
        IClassTransformer transformer = new DevIBlockStateSrgBridgeTransformer();

        byte[] interfaceMcp = stub(IFACE,true,"getBlock",IFACE_DESC);
        byte[] interfacePatched = transformer.transform(
                "net.minecraft.block.state.IBlockState",null,interfaceMcp);
        assertAlias(interfacePatched,"func_177230_c",IFACE_DESC,
                Opcodes.INVOKEINTERFACE,IFACE,"getBlock");
        require(Arrays.equals(interfacePatched,transformer.transform(
                "net.minecraft.block.state.IBlockState",null,interfacePatched)),
                "IBlockState bridge must be idempotent");

        byte[] chunkMcp = stub(CHUNK,false,"getBlockState",CHUNK_DESC);
        byte[] chunkPatched = transformer.transform(null,
                "net.minecraft.world.chunk.Chunk",chunkMcp);
        assertAlias(chunkPatched,"func_177435_g",CHUNK_DESC,
                Opcodes.INVOKEVIRTUAL,CHUNK,"getBlockState");
        require(Arrays.equals(chunkPatched,transformer.transform(null,
                "net.minecraft.world.chunk.Chunk",chunkPatched)),
                "Chunk bridge must be idempotent");

        // MalisisCore's injected NetHandlerPlayServer body references the
        // SRG CPacketPlayerDigging.func_179715_a() accessor. Check that an
        // MCP getPosition() is bridged, with the exact no-argument BlockPos
        // descriptor and virtual invocation of the original implementation.
        byte[] diggingMcp = stub(DIGGING,false,"getPosition",DIGGING_DESC);
        byte[] diggingPatched = transformer.transform(
                "net.minecraft.network.play.client.CPacketPlayerDigging",
                null,diggingMcp);
        assertAlias(diggingPatched,"func_179715_a",DIGGING_DESC,
                Opcodes.INVOKEVIRTUAL,DIGGING,"getPosition");
        require(Arrays.equals(diggingPatched,transformer.transform(
                "net.minecraft.network.play.client.CPacketPlayerDigging",
                null,diggingPatched)),
                "Digging packet bridge must be idempotent");

        // SRG production Minecraft already has the target names. A developer
        // compatibility bridge must never change those runtime classes.
        byte[] production = stub(CHUNK,false,"func_177435_g",CHUNK_DESC);
        require(Arrays.equals(production,transformer.transform(
                "net.minecraft.world.chunk.Chunk",null,production)),
                "Production SRG Chunk should remain unchanged");
        byte[] diggingProduction=stub(
                DIGGING,false,"func_179715_a",DIGGING_DESC);
        require(Arrays.equals(diggingProduction,transformer.transform(
                "net.minecraft.network.play.client.CPacketPlayerDigging",
                null,diggingProduction)),
                "Production SRG digging packet should remain unchanged");

        byte[] unrelated = stub("example/Other",false,
                "getBlockState",CHUNK_DESC);
        require(Arrays.equals(unrelated,transformer.transform(
                "example.Other","example.Other",unrelated)),
                "Unrelated class must remain untouched");

        System.out.println(
                "MCP -> SRG bridges pass: IBlockState + Chunk + CPacketPlayerDigging");
    }

    private static byte[] stub(String name,boolean isInterface,
            String methodName,String descriptor)
    {
        ClassWriter writer = new ClassWriter(0);
        int flags = Opcodes.ACC_PUBLIC;
        if(isInterface)flags |= Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE;
        writer.visit(Opcodes.V1_8,flags,name,null,"java/lang/Object",null);
        if(!isInterface)
        {
            MethodVisitor constructor=writer.visitMethod(Opcodes.ACC_PUBLIC,
                    "<init>","()V",null,null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD,0);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "java/lang/Object","<init>","()V",false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(1,1);
            constructor.visitEnd();
        }
        MethodVisitor original=writer.visitMethod(
                Opcodes.ACC_PUBLIC | (isInterface ? Opcodes.ACC_ABSTRACT : 0),
                methodName,descriptor,null,null);
        if(!isInterface)
        {
            original.visitCode();
            original.visitInsn(Opcodes.ACONST_NULL);
            original.visitInsn(Opcodes.ARETURN);
            original.visitMaxs(1,2);
        }
        original.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void assertAlias(byte[] result,String name,String descriptor,
            int invokeOpcode,String owner,String mcp)
    {
        ClassNode node = new ClassNode();
        new ClassReader(result).accept(node,0);
        MethodNode alias = null;
        for(MethodNode method:node.methods)
            if(method.name.equals(name) && method.desc.equals(descriptor))
                alias=method;
        require(alias!=null,"Missing " + name + descriptor);
        require((alias.access & Opcodes.ACC_ABSTRACT)==0,
                "Alias must have a concrete body: " + name);
        boolean delegates=false;
        for(AbstractInsnNode insn=alias.instructions.getFirst();
                insn!=null;insn=insn.getNext())
        {
            if(insn instanceof MethodInsnNode)
            {
                MethodInsnNode call=(MethodInsnNode)insn;
                if(call.getOpcode()==invokeOpcode
                        && owner.equals(call.owner)
                        && mcp.equals(call.name)
                        && descriptor.equals(call.desc))
                    delegates=true;
            }
        }
        require(delegates,"Alias does not delegate to the original MCP method");
    }

    private static void require(boolean condition,String message)
    {
        if(!condition)throw new AssertionError(message);
    }
}
