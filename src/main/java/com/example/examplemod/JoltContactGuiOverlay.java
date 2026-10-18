package com.example.examplemod;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * The reliable GUI-pass alternative to glBegin-based Jolt wireframes.
 *
 * The Preview has ALREADY been copied from the Preview FBO to Minecraft's
 * ordinary GUI framebuffer. Project native-world Jolt capsules with EXACTLY
 * the orbit-camera mathematics used by EditorGizmoController.project() and
 * draw thick, coloured 2D line quads via Minecraft's normal Tessellator.
 *
 * The previous path logged "10 hair/3 torso" but could disappear in the
 * Preview FBO/OptiFine framebuffer pipeline. This draws after the copy,
 * like EditorGizmoController's working GUI operations; no shader, actor,
 * animation, camera or collision state is modified.
 */
public final class JoltContactGuiOverlay
{
    private static final int SIDES=14;
    private static final int CAP_STEPS=4;
    private static String lastReport="";
    /** Optional visual-only filter. Never removes a Jolt collider.
     *  Example: -Dbbs.editor.physics.jolt.overlayBoxModel=boobs
     */
    private static final String BOX_MODEL_FILTER=
            System.getProperty("bbs.editor.physics.jolt.overlayBoxModel","")
                    .trim().toLowerCase(java.util.Locale.ROOT);
    /** Example: -Dbbs.editor.physics.jolt.overlayChain=left_strand_1 */
    private static final String CHAIN_FILTER=
            System.getProperty("bbs.editor.physics.jolt.overlayChain","")
                    .trim().toLowerCase(java.util.Locale.ROOT);

    private JoltContactGuiOverlay() {}

    /** Exact perspective math shared with the editor gizmo (Java 8 tested). */
    public static float[] project(double x,double y,double z,
            EditorCamera camera,int viewportX,int viewportY,
            int viewportWidth,int viewportHeight)
    {
        return JoltContactScreenProjection.project(x,y,z,camera,
                viewportX,viewportY,viewportWidth,viewportHeight);
    }

    public static void draw(Minecraft mc,EditorCamera camera,
            int x,int y,int width,int height,JoltContactOverlaySnapshot snapshot)
    {
        draw(mc,camera,x,y,width,height,snapshot,0D,0D,0D);
    }

    /**
     * Native contact scenes are actor-origin-relative. Only translate their
     * read-only debug drawing into the preview's actual world coordinates.
     * Never move physical Jolt bodies or alter actor transforms here.
     */
    public static void draw(Minecraft mc,EditorCamera camera,
            int x,int y,int width,int height,JoltContactOverlaySnapshot snapshot,
            double actorX,double actorY,double actorZ)
    {
        if(mc==null || camera==null || snapshot==null ||
                width<=0 || height<=0)return;
        ScaledResolution res=new ScaledResolution(mc);
        int scale=Math.max(1,res.getScaleFactor());
        int viewportX=Math.max(0,x*scale);
        int viewportY=Math.max(0,mc.displayHeight-(y+height)*scale);
        int viewportW=Math.max(0,Math.min(width*scale,mc.displayWidth-viewportX));
        int viewportH=Math.max(0,Math.min(height*scale,mc.displayHeight-viewportY));
        if(viewportW==0 || viewportH==0)return;

        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        int segmentCount=0,visibleBodies=0,drawnBoxes=0;
        try
        {
            GL20.glUseProgram(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glViewport(0,0,mc.displayWidth,mc.displayHeight);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(viewportX,viewportY,viewportW,viewportH);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            try
            {
                GL11.glLoadIdentity();
                GL11.glOrtho(0,res.getScaledWidth(),res.getScaledHeight(),
                        0,-1,1);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                try
                {
                    GL11.glLoadIdentity();
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    GL11.glDepthMask(false);
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glDisable(GL11.GL_LIGHTING);
                    GL11.glDisable(GL11.GL_CULL_FACE);
                    GL11.glDisable(GL11.GL_ALPHA_TEST);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
                            GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glShadeModel(GL11.GL_SMOOTH);

                    Tessellator tess=Tessellator.getInstance();
                    BufferBuilder buf=tess.getBuffer();
                    buf.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
                    List<JoltContactOverlaySnapshot.Shape> shapes=snapshot.shapes;
                    for(int i=0;i<shapes.size();i++)
                    {
                        if(!chainShown(shapes.get(i)))continue;
                        int before=segmentCount;
                        segmentCount+=drawCapsule(buf,shapes.get(i),camera,
                                x,y,width,height,actorX,actorY,actorZ);
                        if(segmentCount>before)visibleBodies++;
                    }
                    // Native kinematic boxes of additional BodyParts, including
                    // independently rotated cubes within one Chameleon bone.
                    for(JoltContactOverlaySnapshot.Box box:snapshot.boxes)
                    {
                        if(!boxModelShown(box))continue;
                        int before=segmentCount;
                        segmentCount+=drawAddedCollider(buf,box,camera,
                                x,y,width,height,actorX,actorY,actorZ);
                        if(segmentCount>before)
                        {
                            visibleBodies++;
                            drawnBoxes++;
                        }
                    }
                    tess.draw();

                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GlStateManager.enableTexture2D();
                    // Text is both a colour legend and an always-visible
                    // diagnostic: counts on screen but zero projected shapes
                    // indicate a camera/coordinate-space issue, NOT no Jolt.
                    int labelX=x+9,labelY=y+12;
                    String title="JOLT  torso "+snapshot.torsoCount+
                            "   hair "+snapshot.hairCount+
                            "   extras "+drawnBoxes+"/"+snapshot.boxCount+
                            "   visible "+visibleBodies;
                    if(!BOX_MODEL_FILTER.isEmpty())
                        title+=" [model:"+BOX_MODEL_FILTER+"]";
                    if(!CHAIN_FILTER.isEmpty())
                        title+=" [chain:"+CHAIN_FILTER+"]";
                    if(!shapes.isEmpty() && visibleBodies==0)
                        title+="  [outside camera]";
                    Gui.drawRect(labelX-4,labelY-4,
                            labelX+mc.fontRenderer.getStringWidth(title)+5,
                            labelY+mc.fontRenderer.FONT_HEIGHT+4,0xA9001016);
                    mc.fontRenderer.drawStringWithShadow(title,
                            labelX,labelY,0xFFBDF8C6);
                    // In filtered mode put provenance directly onto the
                    // cube. The boxes are GUI-projected; depth is deliberately
                    // disabled, so this is not an occlusion test.
                    if(!BOX_MODEL_FILTER.isEmpty())
                    {
                        int number=0;
                        for(JoltContactOverlaySnapshot.Box box:snapshot.boxes)
                        {
                            if(!boxModelShown(box))continue;
                            number++;
                            float[] center=project(box.pose[0]+actorX,
                                    box.pose[1]+actorY,box.pose[2]+actorZ,
                                    camera,x,y,width,height);
                            if(center!=null && center[0]>=x &&
                                    center[0]<=x+width &&
                                    center[1]>=y+30 && center[1]<=y+height)
                                mc.fontRenderer.drawStringWithShadow(
                                        box.source+" #"+number,
                                        (int)center[0]+3,(int)center[1]-8,
                                        0xFFFFD66B);
                        }
                    }
                }
                finally
                {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                }
            }
            finally
            {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
        }
        finally
        {
            GL20.glUseProgram(program);
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
            GL11.glMatrixMode(mode);
        }

        String report="frame="+snapshot.frame+" torso="+snapshot.torsoCount+
                " hair="+snapshot.hairCount+" boxes="+snapshot.boxCount+
                " boxesShown="+drawnBoxes+" filter="+BOX_MODEL_FILTER+
                " chain="+CHAIN_FILTER+
                " projectedShapes="+visibleBodies+
                " projectedLines="+segmentCount+" camera=("+camera.getCameraX()+
                ","+camera.getCameraY()+","+camera.getCameraZ()+")"+
                " actorOrigin=("+actorX+","+actorY+","+actorZ+")";
        // Avoid one-line-per-render logs; report changes to topology or
        // visibility category, not exact subpixel/camera differences.
        String status=snapshot.torsoCount+":"+snapshot.hairCount+
                ":"+snapshot.boxCount+":"+drawnBoxes+":"+BOX_MODEL_FILTER+
                ":"+CHAIN_FILTER+
                ":"+(visibleBodies>0?"on-screen":"off-screen")+
                ":"+(segmentCount>0?"drawn":"no-lines");
        if(!status.equals(lastReport))
        {
            lastReport=status;
            System.out.println("[BBS Animation Editor][Jolt][GUIOverlay] "+report);
            for(JoltContactOverlaySnapshot.Box box:snapshot.boxes)
                System.out.println("[BBS Animation Editor][Jolt][ExtraBox] "+
                        "source="+box.source+" center=("+
                        box.pose[0]+","+box.pose[1]+","+box.pose[2]+
                        ") quaternion=("+
                        box.pose[3]+","+box.pose[4]+","+
                        box.pose[5]+","+box.pose[6]+") half=("+
                        box.pose[7]+","+box.pose[8]+","+box.pose[9]+")");
            if(visibleBodies==0 && !snapshot.shapes.isEmpty())
            {
                JoltContactOverlaySnapshot.Shape first=snapshot.shapes.get(0);
                System.out.println("[BBS Animation Editor][Jolt][GUIOverlay] "+
                        "First shape world endpoints: "+
                        java.util.Arrays.toString(first.a)+" -> "+
                        java.util.Arrays.toString(first.b)+
                        ", radius="+first.radius+
                        " (check native Jolt vs actor-world origin)");
            }
        }
    }

    private static boolean chainShown(JoltContactOverlaySnapshot.Shape shape)
    {
        // The selected hair plus its passive collider environment, without
        // masking a Jolt body in the simulation itself.
        return CHAIN_FILTER.isEmpty() ||
                shape.kind==JoltContactOverlaySnapshot.Kind.TORSO ||
                shape.source.toLowerCase(java.util.Locale.ROOT)
                        .contains(CHAIN_FILTER);
    }

    private static boolean boxModelShown(JoltContactOverlaySnapshot.Box box)
    {
        return BOX_MODEL_FILTER.isEmpty() ||
                box.source.toLowerCase(java.util.Locale.ROOT)
                        .contains(BOX_MODEL_FILTER);
    }

    /** Emit actual capsule rings and longitudinal arcs as thick GUI quads. */
    private static int drawCapsule(BufferBuilder buf,
            JoltContactOverlaySnapshot.Shape shape,EditorCamera camera,
            int x,int y,int width,int height,
            double actorX,double actorY,double actorZ)
    {
        float[] a=shape.a,b=shape.b;
        double dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(!(length>1E-6D && length<5D))return 0;
        double nx=dx/length,ny=dy/length,nz=dz/length;
        double refx=Math.abs(ny)<0.85D?0D:1D;
        double refy=Math.abs(ny)<0.85D?1D:0D;
        double ux=-nz*refy,uy=nz*refx,uz=nx*refy-ny*refx;
        double un=Math.sqrt(ux*ux+uy*uy+uz*uz);
        if(un<1E-8D)return 0;
        ux/=un;uy/=un;uz/=un;
        double vx=ny*uz-nz*uy,vy=nz*ux-nx*uz,
                vz=nx*uy-ny*ux;
        double cx=(a[0]+b[0])*0.5D+actorX;
        double cy=(a[1]+b[1])*0.5D+actorY;
        double cz=(a[2]+b[2])*0.5D+actorZ;
        double half=Math.max(0D,length*0.5D-shape.radius);

        float[][][] ring=new float[2*(CAP_STEPS+1)][SIDES][];
        for(int side=0;side<2;side++)
        {
            for(int step=0;step<=CAP_STEPS;step++)
            {
                double angle=(Math.PI/2D)*
                        (side==0 ? 1D-step/(double)CAP_STEPS
                                 : step/(double)CAP_STEPS);
                double along=(side==0?-1D:1D)*
                        (half+shape.radius*Math.sin(angle));
                double radius=shape.radius*Math.cos(angle);
                int ringIndex=side*(CAP_STEPS+1)+step;
                for(int i=0;i<SIDES;i++)
                {
                    double phi=2D*Math.PI*i/SIDES;
                    double cos=radius*Math.cos(phi),sin=radius*Math.sin(phi);
                    ring[ringIndex][i]=project(
                            cx+nx*along+ux*cos+vx*sin,
                            cy+ny*along+uy*cos+vy*sin,
                            cz+nz*along+uz*cos+vz*sin,
                            camera,x,y,width,height);
                }
            }
        }
        int rgb=shape.kind==JoltContactOverlaySnapshot.Kind.HAIR
                ?0xFF72C1:shape.kind==JoltContactOverlaySnapshot.Kind.ADDED
                ?0xFFD66B:0x55FFA1;
        int lines=0;
        for(int step=0;step<ring.length;step++)
        {
            for(int i=0;i<SIDES;i++)
            {
                float[] current=ring[step][i];
                float[] around=ring[step][(i+1)%SIDES];
                if(emitLine(buf,current,around,rgb,220,1.7F,
                        x,y,width,height))
                    lines++;
                if(step+1<ring.length &&
                        emitLine(buf,current,ring[step+1][i],rgb,180,1.25F,
                                x,y,width,height))
                    lines++;
            }
        }
        // Three short torso contacts overlap like spheres. Draw the exact
        // fitted centerline and both landmarks so the axis is distinguishable
        // from the spherical surface. These are the same A/B values passed to
        // native Jolt: this does NOT alter the underlying physical collider.
        if(shape.kind==JoltContactOverlaySnapshot.Kind.TORSO)
        {
            float[] endA=project(a[0]+actorX,a[1]+actorY,a[2]+actorZ,
                    camera,x,y,width,height);
            float[] endB=project(b[0]+actorX,b[1]+actorY,b[2]+actorZ,
                    camera,x,y,width,height);
            if(emitLine(buf,endA,endB,0xFFE25D,255,3.2F,
                    x,y,width,height))lines++;
            lines+=drawPivotCross(buf,endA,0xFFF9BA,x,y,width,height);
            lines+=drawPivotCross(buf,endB,0xFFF9BA,x,y,width,height);
        }
        return lines;
    }

    /** Draw the actual primitive type installed into the native Jolt
     * physics system, not a rotated AABB substitute. Box=12 edges,
     * Sphere=closed rounded profile, Capsule=shaft and hemispherical caps.
     */
    private static int drawAddedCollider(BufferBuilder buf,
            JoltContactOverlaySnapshot.Box box,EditorCamera camera,
            int x,int y,int width,int height,
            double actorX,double actorY,double actorZ)
    {
        if(box.primitive==JoltColliderPrimitives.BOX)
            return drawBox(buf,box,camera,x,y,width,height,
                    actorX,actorY,actorZ);
        float[] desc=new float[11];
        System.arraycopy(box.pose,0,desc,0,10);
        desc[10]=box.primitive;
        try
        {
            float[][] ends=JoltColliderPrimitives.capsulePoles(desc);
            JoltContactOverlaySnapshot.Shape primitive=
                    new JoltContactOverlaySnapshot.Shape(
                            JoltContactOverlaySnapshot.Kind.ADDED,
                            ends[0],ends[1],box.pose[7],box.source);
            return drawCapsule(buf,primitive,camera,x,y,width,height,
                    actorX,actorY,actorZ);
        }
        catch(IllegalArgumentException bad)
        {
            return 0;
        }
    }

    /**
     * Draw the eight world corners and 12 edges of the actual oriented box
     * sent to Jolt. Same actor-local scene translation as hair/torso shapes.
     * Amber lines distinguish passive attached cubes from green body capsules.
     */
    private static int drawBox(BufferBuilder buf,JoltContactOverlaySnapshot.Box box,
            EditorCamera camera,int x,int y,int width,int height,
            double actorX,double actorY,double actorZ)
    {
        float[] p=box.pose;
        float qx=p[3],qy=p[4],qz=p[5],qw=p[6];
        float xx=qx*qx,yy=qy*qy,zz=qz*qz;
        float xy=qx*qy,xz=qx*qz,yz=qy*qz;
        float wx=qw*qx,wy=qw*qy,wz=qw*qz;
        double[] rx={1D-2D*(yy+zz),2D*(xy+wz),2D*(xz-wy)};
        double[] ry={2D*(xy-wz),1D-2D*(xx+zz),2D*(yz+wx)};
        double[] rz={2D*(xz+wy),2D*(yz-wx),1D-2D*(xx+yy)};
        float[][] corners=new float[8][];
        for(int i=0;i<8;i++)
        {
            double a=((i&1)==0?-1D:1D)*p[7];
            double b=((i&2)==0?-1D:1D)*p[8];
            double c=((i&4)==0?-1D:1D)*p[9];
            corners[i]=project(
                    p[0]+actorX+rx[0]*a+ry[0]*b+rz[0]*c,
                    p[1]+actorY+rx[1]*a+ry[1]*b+rz[1]*c,
                    p[2]+actorZ+rx[2]*a+ry[2]*b+rz[2]*c,
                    camera,x,y,width,height);
        }
        int lines=0;
        for(int i=0;i<8;i++)
            for(int bit=1;bit<=4;bit*=2)
                if((i&bit)==0 && emitLine(buf,corners[i],corners[i|bit],
                        0xFFD66B,225,1.6F,x,y,width,height))lines++;
        return lines;
    }

    /** Marks one fitted collider endpoint in the post-FBO GUI pass. */
    private static int drawPivotCross(BufferBuilder buf,float[] point,
            int rgb,int x,int y,int width,int height)
    {
        if(point==null)return 0;
        float radius=3.5F;
        float[] left={point[0]-radius,point[1]};
        float[] right={point[0]+radius,point[1]};
        float[] top={point[0],point[1]-radius};
        float[] bottom={point[0],point[1]+radius};
        int count=0;
        if(emitLine(buf,left,right,rgb,255,2F,x,y,width,height))count++;
        if(emitLine(buf,top,bottom,rgb,255,2F,x,y,width,height))count++;
        return count;
    }

    private static boolean emitLine(BufferBuilder buf,float[] a,float[] b,
            int rgb,int alpha,float thickness,
            int x,int y,int width,int height)
    {
        if(a==null||b==null)return false;
        // A physical shape outside the camera MUST NOT be counted as
        // actually visible just because 2D vertices were submitted.
        if(Math.max(a[0],b[0])<x || Math.min(a[0],b[0])>x+width ||
                Math.max(a[1],b[1])<y || Math.min(a[1],b[1])>y+height)
            return false;
        float dx=b[0]-a[0],dy=b[1]-a[1];
        float len=(float)Math.sqrt(dx*dx+dy*dy);
        if(len<0.1F || len>2000F)return false;
        float sx=-dy/len*thickness*0.5F;
        float sy=dx/len*thickness*0.5F;
        int r=(rgb>>16)&255,g=(rgb>>8)&255,bl=rgb&255;
        buf.pos(a[0]+sx,a[1]+sy,0D).color(r,g,bl,alpha).endVertex();
        buf.pos(a[0]-sx,a[1]-sy,0D).color(r,g,bl,alpha).endVertex();
        buf.pos(b[0]-sx,b[1]-sy,0D).color(r,g,bl,alpha).endVertex();
        buf.pos(b[0]+sx,b[1]+sy,0D).color(r,g,bl,alpha).endVertex();
        return true;
    }
}
