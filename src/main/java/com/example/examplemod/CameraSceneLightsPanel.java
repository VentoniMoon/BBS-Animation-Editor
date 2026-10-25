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
        Gui.drawRect(0,25,166,54,0xFF1D303B);
        mc.fontRenderer.drawString("PRESETS",8,36,
                opened?0xFF899DAA:0xFF60C8FA);
        mc.fontRenderer.drawString("LIGHTS",92,36,
                opened?0xFF60C8FA:0xFF899DAA);
        if(!opened)return;
        CameraSceneLights.Light selected=sync(screen);
        Gui.drawRect(0,54,166,panelBottom,0xFF19232C);
        Gui.drawRect(6,61,80,82,0xFF31495D);
        Gui.drawRect(85,61,160,82,0xFF31495D);
        mc.fontRenderer.drawString("+ ADD",21,68,0xFFFFFFFF);
        mc.fontRenderer.drawString("REMOVE",96,68,0xFFFFFFFF);
        int index=0;
        for(CameraSceneLights.Light l:screen.getSceneState()
                .getCameraLights().all())
        {
            int row=92+index++*21;
            if(row+20>=panelBottom)break;
            if(l==selected)
                Gui.drawRect(4,row-2,162,row+18,0xFF34536A);
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
        if(my>=25&&my<54&&mx>=0&&mx<166&&button==0)
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
        if(mx<166&&my>=54&&my<bottom)
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
            boolean hit=nativeSettings.mouseClicked(screen,nativeActor,
                    mx,my,button);
            persistNative(screen);
            return hit||true;
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
            persistNative(screen);
            return true;
        }
        return false;
    }
    public boolean scroll(AnimationEditorScreen screen,int mx,int my,int d)
    {
        if(!opened)return false;
        if(mx>=leftRight&&my>=54&&my<bottom&&tab==2)
            return nativeSettings.scroll(mx,my,d);
        return mx<166&&my>=54&&my<bottom;
    }

    private static final class ScreenPoint
    {
        final double x,y;
        ScreenPoint(double x,double y){this.x=x;this.y=y;}
    }
    private static ScreenPoint project(PreviewCameraPose camera,
            double x,double y,double z,int left,int top,int w,int h)
    {
        double yaw=Math.toRadians(camera.yaw);
        double pitch=Math.toRadians(camera.pitch);
        double[] f={-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),
                Math.cos(yaw)*Math.cos(pitch)};
        double[] r={Math.cos(yaw),0,Math.sin(yaw)};
        double[] u={-Math.sin(yaw)*Math.sin(pitch),Math.cos(pitch),
                Math.cos(yaw)*Math.sin(pitch)};
        double vx=x-camera.x,vy=y-camera.y,vz=z-camera.z;
        double depth=vx*f[0]+vy*f[1]+vz*f[2];
        if(depth<=0.02D)return null;
        double halfHeight=depth*Math.tan(Math.toRadians(camera.fov)*0.5D);
        if(halfHeight<1E-5D)return null;
        double scale=(h*0.5D)/halfHeight;
        return new ScreenPoint(left+w*0.5D+
                (vx*r[0]+vy*r[1]+vz*r[2])*scale,
                top+h*0.5D-
                (vx*u[0]+vy*u[1]+vz*u[2])*scale);
    }

    public void drawGizmo(AnimationEditorScreen screen,int x,int y,int w,int h)
    {
        if(!opened||tab==2||sync(screen)==null)return;
        CameraSceneLights.Light l=chosen;
        PreviewCameraPose pose=screen.getCameraWorkspaceNativePose();
        ScreenPoint p=project(pose,
                component(l,tab,0),component(l,tab,1),
                component(l,tab,2),x,y,w,h);
        if(p==null)return;
        int px=(int)p.x,py=(int)p.y;
        Gui.drawRect(px-3,py-3,px+4,py+4,0xFFFFFFFF);
        for(int i=0;i<3;i++)
        {
            double ax=component(l,tab,0)+(i==0?1D:0D);
            double ay=component(l,tab,1)+(i==1?1D:0D);
            double az=component(l,tab,2)+(i==2?1D:0D);
            ScreenPoint next=project(pose,ax,ay,az,x,y,w,h);
            if(next==null)continue;
            int color=i==0?0xFFFF6060:i==1?0xFF63ED85:0xFF679DFF;
            int ex=(int)next.x,ey=(int)next.y;
            Gui.drawRect(ex-5,ey-5,ex+6,ey+6,color);
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
        for(int i=0;i<3;i++)
        {
            double ax=component(chosen,tab,0)+(i==0?1D:0D);
            double ay=component(chosen,tab,1)+(i==1?1D:0D);
            double az=component(chosen,tab,2)+(i==2?1D:0D);
            ScreenPoint end=project(pose,ax,ay,az,x,y,w,h);
            ScreenPoint start=project(pose,
                    component(chosen,tab,0),component(chosen,tab,1),
                    component(chosen,tab,2),x,y,w,h);
            if(end==null||start==null||
                    Math.abs(mx-end.x)>11||Math.abs(my-end.y)>11)continue;
            dragAxis=i;dragComponent=tab;
            startMouseX=mx;startMouseY=my;
            oldCoord=component(chosen,tab,i);
            dx=end.x-start.x;dy=end.y-start.y;
            dirty=false;return true;
        }
        return false;
    }
    public boolean drag(AnimationEditorScreen screen,int mx,int my)
    {
        if(dragAxis<0||chosen==null)return false;
        double length=dx*dx+dy*dy;
        double steps=length<3D?(startMouseY-my)*0.025D:
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
