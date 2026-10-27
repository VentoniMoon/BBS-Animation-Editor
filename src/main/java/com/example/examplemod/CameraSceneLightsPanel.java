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
    /**
     * The same 3D arrow-and-plane controller used by POSE. This control rig
     * never joins scene data; no static keyframes or actor morphs are added.
     */
    private final EditorGizmoController moveGizmo=new EditorGizmoController();
    private final EditorCamera moveCamera=new EditorCamera();
    private final ActorAnimationData moveRig=
            new ActorAnimationData("camera-static-light-gizmo");
    private final AnimationBone moveBone=new AnimationBone(BetterLightsRig.ORIGIN);
    private final AnimationKeyframe moveKey=new AnimationKeyframe(0);
    private CameraSceneLights.Light draggedLight;
    private int dragComponent=-1;
    private double dragStartX,dragStartY,dragStartZ;
    private boolean dirty;
    private int listScroll;
    private String warning;

    public CameraSceneLightsPanel()
    {
        moveRig.getBones().add(moveBone);
        moveGizmo.setLightRigTarget(moveRig);
        moveGizmo.setMode(EditorGizmoController.Mode.POSITION);
    }

    private BlockbusterRecordFrame gizmoFrame(CameraSceneLights.Light light)
    {
        if(light==null)return null;
        BlockbusterRecordFrame frame=new BlockbusterRecordFrame();
        int point=tab==1?1:0;
        frame.setX(component(light,point,0));
        frame.setY(component(light,point,1));
        frame.setZ(component(light,point,2));
        return frame;
    }

    private EditorCamera cameraForGizmo(AnimationEditorScreen screen)
    {
        PreviewCameraPose pose=screen.getCameraWorkspaceNativePose();
        if(pose==null)return null;
        moveCamera.apply(pose);
        return moveCamera;
    }

    /** Draw 3D arrows and gray move planes into the actual Preview FBO. */
    public void drawNativeGizmo(AnimationEditorScreen screen,Minecraft mc,
            int x,int y,int w,int h)
    {
        if(!opened||tab==2||sync(screen)==null||
                screen.getCameraVideoExport().isExporting())return;
        EditorCamera camera=cameraForGizmo(screen);
        if(camera==null)return;
        moveGizmo.draw3D(mc,x,y,w,h,
                moveBone,moveKey,gizmoFrame(chosen),camera,true);
    }

    private void resetMove()
    {
        moveGizmo.mouseReleased();
        moveKey.getTransform().setPosition(0F,0F,0F);
        draggedLight=null;
        dirty=false;
    }

    public boolean opened(){return opened;}
    public void reset()
    {
        opened=false;chosen=null;nativeActor=null;
        field=null;fieldAxis=-1;resetMove();
        listScroll=0;tab=0;warning=null;
    }
    public void close(AnimationEditorScreen editor)
    {
        commit(editor);
        persistNative(editor);
        opened=false;field=null;resetMove();
    }
    private void choose(AnimationEditorScreen screen,
            CameraSceneLights.Light next)
    {
        persistNative(screen);
        chosen=next;
        nativeActor=null;
        field=null;fieldAxis=-1;
        resetMove();warning=null;
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
    // The CAMERA column is narrower than Character's actor sidebar.
    private static final int LEFT=CameraWorkspace.CAMERA_LEFT_WIDTH;
    private int visibleLights()
    {
        // Leave room for the scroll-count footer at the bottom.
        return Math.max(0,(bottom-92-19)/21);
    }
    private void clampList(int count)
    {
        listScroll=Math.max(0,Math.min(listScroll,
                Math.max(0,count-visibleLights())));
    }
    public void drawLeft(AnimationEditorScreen screen,int panelBottom)
    {
        leftRight=screen.width-185;
        top=25;bottom=panelBottom;
        Minecraft mc=Minecraft.getMinecraft();
        Gui.drawRect(0,25,LEFT,54,0xFF1D303B);
        mc.fontRenderer.drawString("PRESETS",7,36,
                opened?0xFF899DAA:0xFF60C8FA);
        mc.fontRenderer.drawString("LIGHTS",LEFT/2+7,36,
                opened?0xFF60C8FA:0xFF899DAA);
        if(!opened)return;
        CameraSceneLights.Light selected=sync(screen);
        Gui.drawRect(0,54,LEFT,panelBottom,0xFF19232C);
        Gui.drawRect(4,61,LEFT/2-2,82,0xFF31495D);
        Gui.drawRect(LEFT/2+2,61,LEFT-4,82,0xFF31495D);
        mc.fontRenderer.drawString("+ ADD",11,68,0xFFFFFFFF);
        mc.fontRenderer.drawString("REMOVE",LEFT/2+5,68,0xFFFFFFFF);
        java.util.List<CameraSceneLights.Light> lights=
                screen.getSceneState().getCameraLights().all();
        clampList(lights.size());
        for(int i=listScroll;i<lights.size();i++)
        {
            int row=92+(i-listScroll)*21;
            if(row+20>=panelBottom)break;
            CameraSceneLights.Light light=lights.get(i);
            if(light==selected)
                Gui.drawRect(4,row-2,LEFT-4,row+18,0xFF34536A);
            String label=mc.fontRenderer.trimStringToWidth(
                    (light.enabled?"":"[ ] ")+light.name,LEFT-16);
            mc.fontRenderer.drawString(label,9,row+3,
                    light.enabled?0xFFE0F2FE:0xFF82919C);
        }
        if(lights.size()>visibleLights())
            mc.fontRenderer.drawString(
                    (listScroll+1)+"-"+Math.min(lights.size(),
                        listScroll+visibleLights())+"/"+lights.size(),
                    7,bottom-12,0xFF7A93A3);
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
        mc.fontRenderer.drawString("Drag arrows; click O/T to switch",
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
        if(warning!=null)
            mc.fontRenderer.drawString(
                    mc.fontRenderer.trimStringToWidth(warning,167),
                    x+10,366,0xFFFF9999);
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
                double previous=component(l,tab,fieldAxis);
                set(l,tab,fieldAxis,value);
                if(BetterLightsWorldState.points(
                        l.x,l.y,l.z,l.targetX,l.targetY,l.targetZ)==null)
                {
                    set(l,tab,fieldAxis,previous);
                    warning="Origin and Target cannot coincide";
                }
                else
                {
                    warning=null;
                    if(Math.abs(previous-value)>1E-7D)
                        screen.getCameraWorkspace().saveScene();
                }
            }
        }
        catch(NumberFormatException ignored){}
        field=null;fieldAxis=-1;
    }

    public boolean click(AnimationEditorScreen screen,int mx,int my,
            int button)
    {
        if(my>=25&&my<54&&mx>=0&&mx<LEFT&&button==0)
        {
            commit(screen);
            if(mx>=LEFT/2)
            {
                opened=true;
                sync(screen);
            }
            else close(screen);
            return true;
        }
        if(!opened)return false;
        if(mx<LEFT&&my>=54&&my<bottom)
        {
            if(button!=0)return true;
            commit(screen);
            CameraSceneLights list=screen.getSceneState().getCameraLights();
            if(my>=61&&my<84)
            {
                if(mx<LEFT/2)
                {
                    PreviewCameraPose pose=screen.getCameraWorkspaceNativePose();
                    // A source must spawn IN FRONT of the real film camera,
                    // not on the old +Z side (behind a default -Z view).
                    double[] forward=PreviewCameraProjection.ray(
                            pose,0.5D,0.5D,0,0,1,1);
                    CameraSceneLights.Light light=list.add(
                            "Light "+(list.all().size()+1),
                            pose.x+forward[0]*6D,
                            pose.y+forward[1]*6D,
                            pose.z+forward[2]*6D);
                    if(light!=null)
                    {
                        // Keep the newly selected source in the visible list.
                        listScroll=Math.max(0,list.all().size()-visibleLights());
                        light.targetX=light.x+forward[0]*4D;
                        light.targetY=light.y+forward[1]*4D;
                        light.targetZ=light.z+forward[2]*4D;
                        choose(screen,light);
                        tab=0;
                    }
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
            int index=listScroll+(my-92)/21;
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
        if(mx>=0&&mx<LEFT&&my>=54&&my<bottom)
        {
            int count=screen.getSceneState().getCameraLights().all().size();
            listScroll+=d>0?-1:1;
            clampList(count);
            return true;
        }
        return false;
    }

    /** The source and aiming point remain two independent world positions. */
    private static boolean inside(double x,double y,int left,int top,int w,int h)
    {
        return x>=left+7&&x<left+w-7&&y>=top+7&&y<top+h-7;
    }

    private static void marker(PreviewCameraPose camera,
            double wx,double wy,double wz,
            String label,int color,boolean selected,
            int x,int y,int w,int h)
    {
        float[] p=PreviewCameraProjection.project(wx,wy,wz,camera,
                x,y,w,h);
        if(p==null||!inside(p[0],p[1],x,y,w,h))return;
        int px=(int)p[0],py=(int)p[1];
        Gui.drawRect(px-5,py-5,px+6,py+6,selected?0xFFFFFFFF:color);
        Gui.drawRect(px-3,py-3,px+4,py+4,selected?color:0xFF12202B);
        Minecraft.getMinecraft().fontRenderer.drawString(
                label,px+9,py-4,color);
    }

    public void drawGizmo(AnimationEditorScreen screen,int x,int y,int w,int h)
    {
        if(!opened||sync(screen)==null||w<1||h<1)return;
        CameraSceneLights.Light light=chosen;
        PreviewCameraPose camera=screen.getCameraWorkspaceNativePose();
        if(camera==null)return;
        BetterLightsWorldState world=BetterLightsWorldState.points(
                light.x,light.y,light.z,
                light.targetX,light.targetY,light.targetZ);
        if(world!=null)
        {
            BetterLightsWireOverlay.draw(camera,light.morph,
                    world.x,world.y,world.z,
                    world.targetX,world.targetY,world.targetZ,
                    x,y,w,h);
        }
        float[] source=PreviewCameraProjection.project(
                light.x,light.y,light.z,camera,x,y,w,h);
        float[] target=PreviewCameraProjection.project(
                light.targetX,light.targetY,light.targetZ,camera,x,y,w,h);
        if(source!=null&&target!=null)
            BetterLightsWireOverlay.line(source[0],source[1],
                    target[0],target[1],0x9983DBF3,x,y,w,h);

        // The two anchors stay visible even when the LIGHT tab is active.
        // Select either O/T marker directly in the preview to edit it.
        marker(camera,light.x,light.y,light.z,"O",
                0xFFFFD16F,tab==0,x,y,w,h);
        marker(camera,light.targetX,light.targetY,light.targetZ,"T",
                0xFF70D9FF,tab==1,x,y,w,h);
        if(tab==2)return;

        double wx=component(light,tab,0),wy=component(light,tab,1),
                wz=component(light,tab,2);
        CameraLightWorldGizmo.Handle handle=CameraLightWorldGizmo.project(
                camera,wx,wy,wz,x,y,w,h);
        if(handle==null)return;
        int[] colors={0xFFFF6565,0xFF69E994,0xFF66A7FF};
        for(int axis=0;axis<3;axis++)
        {
            double[] p=handle.tips[axis];
            if(p==null)continue;
            int color=colors[axis];
            BetterLightsWireOverlay.line(handle.x,handle.y,
                    p[0],p[1],color,x,y,w,h);
            double vx=p[0]-handle.x,vy=p[1]-handle.y;
            double distance=Math.sqrt(vx*vx+vy*vy);
            if(distance<8D)continue;
            double ux=vx/distance,uy=vy/distance;
            // POSE-style directional arrow rather than screen-space boxes.
            BetterLightsWireOverlay.line(p[0],p[1],
                    p[0]-ux*11D-uy*5D,p[1]-uy*11D+ux*5D,
                    color,x,y,w,h);
            BetterLightsWireOverlay.line(p[0],p[1],
                    p[0]-ux*11D+uy*5D,p[1]-uy*11D-ux*5D,
                    color,x,y,w,h);
            if(inside(p[0],p[1],x,y,w,h))
                Minecraft.getMinecraft().fontRenderer.drawString(
                        axis==0?"X":axis==1?"Y":"Z",
                        (int)p[0]+5,(int)p[1]-8,color);
        }
    }

    private static boolean near(PreviewCameraPose camera,
            double wx,double wy,double wz,int mx,int my,
            int x,int y,int w,int h)
    {
        float[] p=PreviewCameraProjection.project(wx,wy,wz,camera,
                x,y,w,h);
        if(p==null||!inside(p[0],p[1],x,y,w,h))return false;
        double dx=p[0]-mx,dy=p[1]-my;
        return dx*dx+dy*dy<=121D;
    }

    public boolean beginGizmo(AnimationEditorScreen screen,
            int mx,int my,int button,int x,int y,int w,int h)
    {
        if(button!=0||!opened||sync(screen)==null||
                mx<x||mx>=x+w||my<y||my>=y+h)return false;
        PreviewCameraPose camera=screen.getCameraWorkspaceNativePose();
        if(camera==null)return false;
        CameraSceneLights.Light light=chosen;

        // Clicking the other anchor swaps editor control, like choosing a
        // different light rig bone in POSE, but does NOT create a keyframe.
        int opposite=tab==0?1:0;
        if(near(camera,component(light,opposite,0),
                component(light,opposite,1),component(light,opposite,2),
                mx,my,x,y,w,h))
        {
            commit(screen);
            persistNative(screen);
            tab=opposite;
            drag=null;draggedLight=null;
            return true;
        }
        if(tab==2)
        {
            if(near(camera,light.x,light.y,light.z,mx,my,x,y,w,h))
            {tab=0;return true;}
            if(near(camera,light.targetX,light.targetY,light.targetZ,
                    mx,my,x,y,w,h))
            {tab=1;return true;}
            return false;
        }
        double wx=component(light,tab,0),wy=component(light,tab,1),
                wz=component(light,tab,2);
        CameraLightWorldGizmo.Handle handle=CameraLightWorldGizmo.project(
                camera,wx,wy,wz,x,y,w,h);
        int axis=CameraLightWorldGizmo.hitAxis(handle,mx,my);
        if(axis<0)
            return near(camera,wx,wy,wz,mx,my,x,y,w,h);
        commit(screen);
        drag=new CameraLightWorldGizmo.Drag(axis,camera,
                wx,wy,wz,x,y,w,h,mx,my);
        draggedLight=light;
        dragStart=component(light,tab,axis);
        dragComponent=tab;
        dirty=false;
        return true;
    }

    public boolean drag(AnimationEditorScreen screen,int mx,int my)
    {
        if(drag==null||draggedLight==null)return false;
        if(chosen!=draggedLight){drag=null;return false;}
        double next=dragStart+drag.delta(mx,my);
        if(!Double.isFinite(next)||Math.abs(next)>29999999D)return true;
        int axis=drag.axis;
        double previous=component(draggedLight,dragComponent,axis);
        set(draggedLight,dragComponent,axis,next);
        if(BetterLightsWorldState.points(
                draggedLight.x,draggedLight.y,draggedLight.z,
                draggedLight.targetX,draggedLight.targetY,
                draggedLight.targetZ)==null)
        {
            set(draggedLight,dragComponent,axis,previous);
            return true;
        }
        dirty|=Math.abs(previous-next)>1E-7D;
        return true;
    }

    public boolean release(AnimationEditorScreen screen)
    {
        if(drag==null)return false;
        drag=null;draggedLight=null;
        if(dirty)screen.getCameraWorkspace().saveScene();
        dirty=false;return true;
    }

}
