package com.example.examplemod;

import java.nio.ByteBuffer;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Java 8 FBO readback state regression (without Minecraft or GPU). */
public final class CameraExportReadbackSmokeTest
{
    private static void check(boolean ok,String message)
    {
        if(!ok)throw new AssertionError(message);
    }

    public static void main(String[] args)
    {
        CameraExportPlan plan=new CameraExportPlan(128,128,20,10);
        CameraExportReadback reader=new CameraExportReadback(plan);
        Framebuffer exact=new Framebuffer(256,256,99);
        GL30.read=7;
        GL11.pack=8;
        GL30.oldReadBuffer=1029;
        GL30.sourceReadBuffer=GL30.GL_COLOR_ATTACHMENT0;
        ByteBuffer buffer=reader.capture(exact,64,10,128,128);
        check(buffer.isReadOnly(),"readback caller must not modify GPU frame");
        check(buffer.remaining()==plan.frameBytes,"exact RGBA bytes returned");
        check(buffer.get(0)==(byte)0&&buffer.get(250)==(byte)250,
                "raw RGBA bytes preserved");
        check(GL11.readCalls==1,"one GL pixel read per output frame");
        check(GL11.readX==64&&GL11.readY==10&&
                GL11.readW==128&&GL11.readH==128,
                "read exact native resolution without stretch");
        check(GL30.read==7,"original framebuffer rebound");
        check(GL11.pack==8,"pack alignment restored");
        check(GL30.oldReadBuffer==1029,"original read-buffer unchanged");
        check(GL30.sourceReadBuffer==GL30.GL_COLOR_ATTACHMENT0,
                "source attachment state restored");

        boolean rejected=false;
        try{reader.capture(exact,0,0,100,128);}
        catch(IllegalArgumentException error){rejected=true;}
        check(rejected,"smaller preview cannot masquerade as HD/4K");
        rejected=false;
        try{reader.capture(exact,200,0,128,128);}
        catch(IllegalArgumentException error){rejected=true;}
        check(rejected,"crop must stay inside source framebuffer");

        System.out.println("CAMERA offline native readback dimensions/GL cleanup PASS");
    }
}
