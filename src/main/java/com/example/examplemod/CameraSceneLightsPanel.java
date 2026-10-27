package com.example.examplemod;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * CAMERA scene-light authoring: an actor-independent list, Origin/Target
 * world-coordinate controls, the original BetterLights property inspector,
 * and three projected Move handles per controller.
 *
 * No light clip/keyframe is created. The rendered source is the scene's
 * persistent CameraSceneLights.Light, not a Minecraft world entity.
 */
public final class CameraSceneLightsPanel
{
    private boolean opened;
    private int tab; // 0 Origin, 1 Target, 2 BetterLights properties
    private final BetterLightsActorInspector nativeSettings=
            new BetterLightsActorInspector();
    private CameraSceneLights.Light chosen;
    private BlockbusterSceneActorData nativeActor;
    private GuiTextField field;
    private int fieldAxis=-1;
    private int leftRight,top,bottom;
    private int dragAxis=-1;
    private int dragComponent=-1;
    private int startMouseX,startMouseY;
    private double oldCoord,dx,dy;
    private boolean dirty;

    public boolean opened(){return opened;}
    public void reset()
    {
        opened=false;chosen=null;nativeActor=null;
        field=null;fieldAxis=-1;dragAxis=-1;dirty=false;
        tab=0;
    }
    public void close(AnimationEditorScreen editor)
    {
        persistNative(editor);
        opened=false;field=null;dragAxis=-1;
    }
    private void choose(AnimationEditorScreen screen,
            CameraSceneLights.Light next)
    {
        persistNative(screen);
        chosen=next;
        nativeActor=null;
        field=null;
        if(next!=null)
        {
            BlockbusterSceneActor actor=new BlockbusterSceneActor();
            actor.setId("scene-light-"+next.id);
            actor.setName(next.name);
            actor.setMorph(next.morph);
            nativeActor=new BlockbusterSceneActorData(actor);
        }
    }
    private CameraSceneLights.Light sync(AnimationEditorScreen screen)
    {
        CameraSceneLights.Light current=screen.getSceneState()
                .getCameraLights().selected();
        if(current!=chosen)choose(screen,current);
        return current;
    }
    private void persistNative(AnimationEditorScreen screen)
    {
        if(chosen==null||nativeActor==null)return;
        nativeSettings.flush(screen);
        syncNative(screen);
    }

    /** Pull only committed NBT; don't close a newly opened text field. */
    private void syncNative(AnimationEditorScreen screen)
    {
        if(chosen==null||nativeActor==null)return;
        if(nativeActor.getActor().getMorph()!=null)
        {
            String before=chosen.morph.toString();
            chosen.morph=nativeActor.getActor().getMorph().copy();
            if(!before.equals(chosen.morph.toString()))
                screen.getCameraWorkspace().saveScene();
        }
    }
    public void drawLeft(AnimationEditorScreen screen,int panelBottom)
    {
        leftRight=screen.width-185;
        top=25;bottom=panelBottom;
        Minecraft mc=Minecraft.getMinecraft();
        Gui.drawRect(0,25,163,54,0xFF1D303B);
        mc.fontRenderer.drawString("PRESETS",8,36,
                opened?0xFF899DAA:0xFF60C8FA);
        mc.fontRenderer.drawString("LIGHTS",92,36,
                opened?0xFF60C8FA:0xFF899DAA);
        if(!opened)return;
        CameraSceneLights.Light selected=sync(screen);
        Gui.drawRect(0,54,163,panelBottom,0xFF19232C);
        Gui.drawRect(6,61,80,82,0xFF31495D);
        Gui.drawRect(83,61,158,82,0xFF31495D);
        mc.fontRenderer.drawString("+ ADD",21,68,0xFFFFFFFF);
        mc.fontRenderer.drawString("REMOVE",96,68,0xFFFFFFFF);
        int index=0;
        for(CameraSceneLights.Light l:screen.getSceneState()
                .getCameraLights().all())
        {
            int row=92+index++*21;
            if(row+20>=panelBottom)break;
            if(l==selected)
                Gui.drawRect(4,row-2,159,row+18,0xFF34536A);
            mc.fontRenderer.drawString(l.name,12,row+3,
                    l.enabled?0xFFE0F2FE:0xFF82919C);
        }
    }
    public void drawRight(AnimationEditorScreen screen,int panelBottom)
    {
        if(!opened)return;
        CameraSceneLights.Light light=sync(screen);
        int x=screen.width-185;
        top=25;bottom=panelBottom;leftRight=x;
        Gui.drawRect(x,25,screen.width,panelBottom,0xFF1C252E);
        Minecraft mc=Minecraft.getMinecraft();
        String[] captions={"ORIGIN","TARGET","LIGHT"};
        for(int i=0;i<3;i++)
        {
            int xx=x+5+i*60;
            Gui.drawRect(xx,28,xx+57,51,
                    tab==i?0xFF3D7391:0xFF273943);
            mc.fontRenderer.drawString(captions[i],xx+4,36,
                    tab==i?0xFFFFFFFF:0xFFB3C3CC);
        }
        if(light==null)
        {
            mc.fontRenderer.drawString("Create a light with +",x+9,67,
                    0xFFE0E0E0);
            return;
        }
        if(tab==2)
        {
            nativeSettings.draw(mc,x,54,panelBottom,nativeActor,
                    screen.getCurrentFrame());
            return;
        }
        mc.fontRenderer.drawString("WORLD "+captions[tab],
                x+12,68,0xFF70CFFF);
        mc.fontRenderer.drawString("Move: axis gizmo or XYZ values",
                x+10,89,0xFFB7C2C9);
        String[] axis={"X","Y","Z"};
        for(int i=0;i<3;i++)
        {
            int yy=112+i*41;
            mc.fontRenderer.drawString(axis[i],x+11,yy+6,
                    i==0?0xFFFF7777:i==1?0xFF80E68E:0xFF8FB9FF);
            Gui.drawRect(x+29,yy,x+176,yy+22,0xFF101A22);
            if(field!=null&&fieldAxis==i)
                field.drawTextBox();
            else
                mc.fontRenderer.drawString(
                        String.format(Locale.ROOT,"%.3f",
                            component(light,tab,i)),x+37,yy+7,
                        0xFFE7EFF4);
        }
        mc.fontRenderer.drawString("WORLD POINT (blocks)",x+10,251,
                0xFF8094A3);
        mc.fontRenderer.drawString("Light enabled: "+(light.enabled?"YES":"NO"),
                x+11,279,light.enabled?0xFF80EEAA:0xFFF0A0A0);
        Gui.drawRect(x+10,303,x+175,327,0xFF324D64);
        mc.fontRenderer.drawString("TOGGLE LIGHT",x+38,311,0xFFFFFFFF);
        mc.fontRenderer.drawString(
                "Ctrl+S saves; no keys created",x+10,346,0xFFB0BDC6);
    }

    private static double component(CameraSceneLights.Light l,int part,int axis)
    {
        if(part==0)return axis==0?l.x:axis==1?l.y:l.z;
        return axis==0?l.targetX:axis==1?l.targetY:l.targetZ;
    }
    private static void set(CameraSceneLights.Light l,int part,
            int axis,double value)
    {
        if(part==0)
        {
            if(axis==0)l.x=value;
            else if(axis==1)l.y=value;
            else l.z=value;
        }
        else
        {
            if(axis==0)l.targetX=value;
            else if(axis==1)l.targetY=value;
            else l.targetZ=value;
        }
    }
    private void commit(AnimationEditorScreen screen)
    {
        if(field==null||fieldAxis<0)return;
        try
        {
            double value=Double.parseDouble(field.getText().trim());
            if(!Double.isFinite(value)||Math.abs(value)>30000000D)
                throw new NumberFormatException("Invalid world coordinate");
            CameraSceneLights.Light l=sync(screen);
            if(l!=null)
            {
                set(l,tab,fieldAxis,value);
                screen.getCameraWorkspace().saveScene();
            }
        }
        catch(NumberFormatException ignored){}
        field=null;fieldAxis=-1;
    }

    public boolean click(AnimationEditorScreen screen,int mx,int my,
            int button)
    {
        if(my>=25&&my<54&&mx>=0&&mx<163&&button==0)
        {
            if(mx>=83)
            {
                opened=true;
                sync(screen);
            }
            else close(screen);
            return true;
        }
        if(!opened)return false;
        if(mx<163&&my>=54&&my<bottom)
        {
            if(button!=0)return true;
            CameraSceneLights list=screen.getSceneState().getCameraLights();
            if(my>=61&&my<84)
            {
                if(mx<83)
                {
                    PreviewCameraPose pose=screen.getCameraWorkspaceNativePose();
                    double yaw=Math.toRadians(pose.yaw);
                    double pitch=Math.toRadians(pose.pitch);
                    CameraSceneLights.Light l=list.add("Light "+(list.all().size()+1),
                            pose.x-Math.sin(yaw)*Math.cos(pitch)*4D,
                            pose.y-Math.sin(pitch)*4D,
                            pose.z+Math.cos(yaw)*Math.cos(pitch)*4D);
                    if(l!=null)choose(screen,l);
                }
                else
                {
                    choose(screen,null);
                    list.removeSelected();
                    choose(screen,list.selected());
                }
                screen.getCameraWorkspace().saveScene();
                return true;
            }
            int index=(my-92)/21;
            if(my>=92&&index>=0&&index<list.all().size())
            {
                list.select(list.all().get(index).id);
                choose(screen,list.selected());
            }
            return true;
        }
        if(mx<leftRight||my<25||my>=bottom)return false;
        if(button!=0)return true;
        if(my>=28&&my<53)
        {
            commit(screen);persistNative(screen);
            tab=Math.max(0,Math.min(2,(mx-leftRight-5)/60));
            return true;
        }
        CameraSceneLights.Light light=sync(screen);
        if(light==null)return true;
        if(tab==2)
        {
            nativeSettings.mouseClicked(screen,nativeActor,mx,my,button);
            syncNative(screen);
            return true;
        }
        if(my>=303&&my<328)
        {
            light.enabled=!light.enabled;
            screen.getCameraWorkspace().saveScene();
            return true;
        }
        commit(screen);
        for(int i=0;i<3;i++)
        {
            int yy=112+i*41;
            if(my>=yy&&my<yy+22)
            {
                fieldAxis=i;
                field=new GuiTextField(18618,Minecraft.getMinecraft().fontRenderer,
                        leftRight+35,yy+2,130,17);
                field.setText(String.format(Locale.ROOT,"%.5f",
                        component(light,tab,i)));
                field.setFocused(true);
                return true;
            }
        }
        return true;
    }
    public boolean keyTyped(AnimationEditorScreen screen,char c,int key)
    {
        if(!opened)return false;
        if(field!=null)
        {
            if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||
                    key==Keyboard.KEY_TAB){commit(screen);return true;}
            if(key==Keyboard.KEY_ESCAPE){field=null;fieldAxis=-1;return true;}
            field.textboxKeyTyped(c,key);return true;
        }
        if(tab==2&&nativeSettings.keyTyped(screen,c,key))
        {
            syncNative(screen);
            return true;
        }
        return false;
    }
    public boolean scroll(AnimationEditorScreen screen,int mx,int my,int d)
    {
        if(!opened)return false;
        if(mx>=leftRight&&my>=54&&my<bottom&&tab==2)
            return nativeSettings.scroll(mx,my,d);
        return mx<163&&my>=54&&my<bottom;
    }

    private static final class ScreenPoint
    {
        final double x,y;
        ScreenPoint(double x,double y){this.x=x;this.y=y;}
    }
    /**
     * Project world XYZ through the exact native preview camera convention.
     * The old independent +Z-forward projection was inverted relative to
     * PreviewWorldRenderer (-Z view space), causing screen-locked handles.
     */
    private static ScreenPoint project(PreviewCameraPose camera,
            double x,double y,double z,int left,int top,int w,int h)
    {
        float[] p=PreviewCameraProjection.project(x,y,z,camera,
                left,top,w,h);
        return p==null?null:new ScreenPoint(p[0],p[1]);
    }

    /** Size a world-space axis so it stays usable across zoom distances. */
    private static double axisLength(PreviewCameraPose camera,
            double x,double y,double z)
    {
        double a=x-camera.x,b=y-camera.y,c=z-camera.z;
        return Math.max(0.4D,Math.min(12D,
                Math.sqrt(a*a+b*b+c*c)*0.075D));
    }

    public void drawGizmo(AnimationEditorScreen screen,int x,int y,int w,int h)
    {
        if(!opened||sync(screen)==null)return;
        CameraSceneLights.Light l=chosen;
        PreviewCameraPose pose=screen.getCameraWorkspaceNativePose();
        // Preview-only outline; the native film framebuffer is already
        // composited before this GUI overlay is drawn.
        BetterLightsWorldState world=BetterLightsWorldState.points(
                l.x,l.y,l.z,l.targetX,l.targetY,l.targetZ);
        if(world!=null)
            BetterLightsWireOverlay.draw(pose,l.morph,
                    world.x,world.y,world.z,
                    world.targetX,world.targetY,world.targetZ,
                    x,y,w,h);
        if(tab==2)return;
        double ox=component(l,tab,0),oy=component(l,tab,1),
                oz=component(l,tab,2);
        ScreenPoint p=project(pose,ox,oy,oz,x,y,w,h);
        if(p==null)return;
        double length=axisLength(pose,ox,oy,oz);
        Gui.drawRect((int)p.x-4,(int)p.y-4,
                (int)p.x+5,(int)p.y+5,0xFFFFFFFF);
        for(int i=0;i<3;i++)
        {
            ScreenPoint tip=project(pose,
                    ox+(i==0?length:0D),
                    oy+(i==1?length:0D),
                    oz+(i==2?length:0D),x,y,w,h);
            if(tip==null)continue;
            int color=i==0?0xFFFF6060:i==1?0xFF63ED85:0xFF679DFF;
            BetterLightsWireOverlay.line(p.x,p.y,tip.x,tip.y,
                    color,x,y,w,h);
            int ex=(int)tip.x,ey=(int)tip.y;
            if(ex<x+7||ex>=x+w-7||ey<y+7||ey>=y+h-7)continue;
            Gui.drawRect(ex-6,ey-6,ex+7,ey+7,color);
            Minecraft.getMinecraft().fontRenderer.drawString(
                    i==0?"X":i==1?"Y":"Z",ex-2,ey-4,0xFF0B1220);
        }
    }

    public boolean beginGizmo(AnimationEditorScreen screen,
            int mx,int my,int button,int x,int y,int w,int h)
    {
        if(button!=0||!opened||tab==2||sync(screen)==null||
                mx<x||mx>=x+w||my<y||my>=y+h)return false;
        PreviewCameraPose pose=screen.getCameraWorkspaceNativePose();
        double ox=component(chosen,tab,0),oy=component(chosen,tab,1),
                oz=component(chosen,tab,2);
        double length=axisLength(pose,ox,oy,oz);
        ScreenPoint start=project(pose,ox,oy,oz,x,y,w,h);
        if(start==null)return false;
        for(int i=0;i<3;i++)
        {
            ScreenPoint end=project(pose,
                    ox+(i==0?length:0D),
                    oy+(i==1?length:0D),
                    oz+(i==2?length:0D),x,y,w,h);
            if(end==null||
                    Math.abs(mx-end.x)>12||Math.abs(my-end.y)>12)continue;
            dragAxis=i;dragComponent=tab;
            startMouseX=mx;startMouseY=my;
            oldCoord=component(chosen,tab,i);
            dx=(end.x-start.x)/length;
            dy=(end.y-start.y)/length;
            dirty=false;return true;
        }
        return false;
    }

    public boolean drag(AnimationEditorScreen screen,int mx,int my)
    {
        if(dragAxis<0||chosen==null)return false;
        double length=dx*dx+dy*dy;
        double steps=length<1E-4D?(startMouseY-my)*0.025D:
                ((mx-startMouseX)*dx+(my-startMouseY)*dy)/length;
        set(chosen,dragComponent,dragAxis,oldCoord+steps);
        dirty=true;return true;
    }
    public boolean release(AnimationEditorScreen screen)
    {
        if(dragAxis<0)return false;
        dragAxis=-1;
        if(dirty)screen.getCameraWorkspace().saveScene();
        dirty=false;return true;
    }
}
