package com.example.examplemod;

import java.util.List;
import net.minecraft.client.gui.Gui;

/**
 * Non-destructive viewport bone pick. BBS uses a per-form stencil index;
 * Forge 1.12 preview is shared by actors, mirrors and FBO passes, so the
 * initial editor implementation uses the gizmo's EXACT projected native
 * joint positions instead of reading a possibly stale OpenGL pick buffer.
 *
 * A projected limb segment selects its CHILD bone. No raw skin triangle
 * or model scale is modified. A later native mesh-ID pass can replace
 * this strategy without changing keyframe/drag ownership.
 */
public final class EditorBoneViewportPicker
{
    private static final float PIVOT_RADIUS=13F;
    private static final float LIMB_RADIUS=9F;
    private static final int HOVER_GREEN=0xFF59F179;

    private static final class Point
    {
        final float x,y;
        Point(float x,float y){this.x=x;this.y=y;}
    }

    private AnimationBone hover;
    private Point hoverPoint,hoverParent;

    public AnimationBone getHoveredBone(){return hover;}

    public void clear()
    {
        hover=null;hoverPoint=null;hoverParent=null;
    }

    public AnimationBone update(int mx,int my,
            List<AnimationBone> bones,EditorGizmoController gizmo,
            BlockbusterRecordFrame recordFrame,EditorCamera camera,
            int vx,int vy,int vw,int vh)
    {
        clear();
        if(bones==null||gizmo==null||recordFrame==null||
                camera==null||vw<=0||vh<=0||
                mx<vx||mx>=vx+vw||my<vy||my>=vy+vh)
            return null;

        float best=Float.POSITIVE_INFINITY;
        for(AnimationBone bone:bones)
        {
            if(bone==null)continue;
            float[] p=gizmo.projectBonePivot(
                    bone,recordFrame,camera,vx,vy,vw,vh);
            if(!inside(p,vx,vy,vw,vh))continue;
            Point point=new Point(p[0],p[1]);
            Point parent=null;
            if(bone.getParent()!=null)
            {
                float[] a=gizmo.projectBonePivot(
                        bone.getParent(),recordFrame,camera,vx,vy,vw,vh);
                if(inside(a,vx,vy,vw,vh))parent=new Point(a[0],a[1]);
            }
            float pivot2=distanceSquared(mx,my,point.x,point.y);
            float score=Float.POSITIVE_INFINITY;
            if(pivot2<=PIVOT_RADIUS*PIVOT_RADIUS)
                score=pivot2;
            if(parent!=null)
            {
                // Bone segment is useful for arms/legs when its pivot is
                // occluded by a big model cube. Favor a joint directly
                // under the cursor over a distant point on the skeleton.
                float segment2=segmentDistanceSquared(
                        mx,my,parent.x,parent.y,point.x,point.y);
                float length2=distanceSquared(
                        parent.x,parent.y,point.x,point.y);
                if(length2>16F&&segment2<=LIMB_RADIUS*LIMB_RADIUS)
                    score=Math.min(score,segment2+10F);
            }
            if(score<best)
            {
                best=score;hover=bone;hoverPoint=point;hoverParent=parent;
            }
        }
        return hover;
    }

    /** Green wire highlight is GUI-only. It cannot contaminate video/FBO
     * or change the material tint of a real character model. */
    public void drawHover()
    {
        if(hover==null||hoverPoint==null)return;
        if(hoverParent!=null)
        {
            float dx=hoverPoint.x-hoverParent.x;
            float dy=hoverPoint.y-hoverParent.y;
            int steps=(int)Math.min(128,Math.max(1,
                    Math.ceil(Math.sqrt(dx*dx+dy*dy)/3F)));
            for(int i=0;i<=steps;i++)
            {
                float t=i/(float)steps;
                int x=Math.round(hoverParent.x+t*dx);
                int y=Math.round(hoverParent.y+t*dy);
                Gui.drawRect(x-1,y-1,x+2,y+2,HOVER_GREEN);
            }
        }
        int x=Math.round(hoverPoint.x),y=Math.round(hoverPoint.y);
        Gui.drawRect(x-6,y-6,x+7,y-4,HOVER_GREEN);
        Gui.drawRect(x-6,y+4,x+7,y+6,HOVER_GREEN);
        Gui.drawRect(x-6,y-6,x-4,y+6,HOVER_GREEN);
        Gui.drawRect(x+4,y-6,x+7,y+6,HOVER_GREEN);
    }

    static boolean inside(float[] p,int x,int y,int w,int h)
    {
        return p!=null&&p.length>=2&&Float.isFinite(p[0])&&
                Float.isFinite(p[1])&&p[0]>=x&&p[0]<x+w&&
                p[1]>=y&&p[1]<y+h;
    }

    static float distanceSquared(float x,float y,float bx,float by)
    {
        float dx=x-bx,dy=y-by;return dx*dx+dy*dy;
    }

    static float segmentDistanceSquared(float x,float y,
            float ax,float ay,float bx,float by)
    {
        float dx=bx-ax,dy=by-ay;
        float length=dx*dx+dy*dy;
        if(length<1E-8F)return distanceSquared(x,y,ax,ay);
        float t=Math.max(0F,Math.min(1F,
                ((x-ax)*dx+(y-ay)*dy)/length));
        return distanceSquared(x,y,ax+dx*t,ay+dy*t);
    }
}
