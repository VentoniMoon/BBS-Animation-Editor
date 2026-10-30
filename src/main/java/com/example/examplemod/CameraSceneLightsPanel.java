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
        mc.fontRenderer.drawString("CLIPS",5,36,
                opened?0xFF899DAA:0xFF60C8FA);
        mc.fontRenderer.drawString("LIGHTS",47,36,
                opened?0xFF60C8FA:0xFF899DAA);
        mc.fontRenderer.drawString("MIRRORS",88,36,0xFF899DAA);
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
    // CameraClipInspector's visual metrics. Only this editor's appearance
    // changes; stored light values, gizmos and scene persistence do not.
    private static final int RIGHT_WIDTH=185;
    private static final int HEADER_BOTTOM=68;
    private static final int TAB_Y=71;
    private static final int TAB_HEIGHT=19;
    private static final int FIRST_FIELD_Y=123;
    private static final int FIELD_SPACING=23;
    private static final int TOGGLE_Y=246;
    private static final int INSPECTOR_BG=0xFF191D21;
    private static final int SECTION_BG=0xFF2B2D30;
    private static final int FIELD_BG=0xFF10151A;

    private static int tabX(int x,int index)
    {
        return x+7+index*58;
    }
    private void section(Minecraft mc,int x,int y,String title)
    {
        int accent=EditorThemeManager.get().getAccent();
        Gui.drawRect(x+7,y,x+178,y+16,SECTION_BG);
        Gui.drawRect(x+7,y,x+10,y+16,accent);
        mc.fontRenderer.drawString(title,x+15,y+4,0xFFE2E5E6);
    }

    public void drawRight(AnimationEditorScreen screen,int panelBottom)
    {
        if(!opened)return;
        CameraSceneLights.Light light=sync(screen);
        int x=screen.width-RIGHT_WIDTH;
        top=25;bottom=panelBottom;leftRight=x;
        Minecraft mc=Minecraft.getMinecraft();
        int accent=EditorThemeManager.get().getAccent();
        int bright=EditorThemeManager.get().getAccentBright();
        Gui.drawRect(x,25,screen.width,panelBottom,INSPECTOR_BG);
        Gui.drawRect(x,25,x+2,panelBottom,0xFF30383E);
        mc.fontRenderer.drawString("LIGHT PROPERTIES",x+10,34,bright);

        if(light==null)
        {
            mc.fontRenderer.drawString("Create a static light",x+10,53,
                    0xFF8C999F);
            return;
        }
        Gui.drawRect(x+7,46,x+178,63,0xFF272E34);
        Gui.drawRect(x+7,46,x+10,63,accent);
        String name=mc.fontRenderer.trimStringToWidth(light.name,119);
        mc.fontRenderer.drawString(name,x+15,50,0xFFE8F1F3);
        String status=light.enabled?"ON":"OFF";
        mc.fontRenderer.drawString(status,
                x+171-mc.fontRenderer.getStringWidth(status),
                50,light.enabled?bright:0xFF969EA4);

        String[] tabs={"ORIGIN","TARGET","LIGHT"};
        for(int i=0;i<3;i++)
        {
            int tx=tabX(x,i);
            Gui.drawRect(tx,TAB_Y,tx+55,TAB_Y+TAB_HEIGHT,
                    tab==i?SECTION_BG:0xFF21262B);
            Gui.drawRect(tx,TAB_Y+TAB_HEIGHT-2,tx+55,
                    TAB_Y+TAB_HEIGHT,tab==i?accent:0xFF343C43);
            int textX=tx+(55-mc.fontRenderer.getStringWidth(tabs[i]))/2;
            mc.fontRenderer.drawString(tabs[i],textX,TAB_Y+5,
                    tab==i?0xFFE9F5F8:0xFF9CAAB0);
        }
        if(tab==2)
        {
            nativeSettings.drawCamera(mc,x,96,panelBottom,nativeActor);
            return;
        }

        section(mc,x,98,tab==0?"WORLD ORIGIN":"WORLD TARGET");
        String[] axes={"X","Y","Z"};
        for(int i=0;i<3;i++)
        {
            int yy=FIRST_FIELD_Y+i*FIELD_SPACING;
            mc.fontRenderer.drawString(axes[i],x+13,yy+4,
                    0xFFADBBC1);
            Gui.drawRect(x+48,yy,x+175,yy+17,FIELD_BG);
            Gui.drawRect(x+48,yy+16,x+175,yy+17,0xFF384148);
            if(field!=null&&fieldAxis==i)
                field.drawTextBox();
            else
                mc.fontRenderer.drawString(
                        mc.fontRenderer.trimStringToWidth(
                            String.format(Locale.ROOT,"%.3f",
                                component(light,tab,i)),116),
                        x+53,yy+4,0xFFE6F0F3);
        }

        mc.fontRenderer.drawString("WORLD COORDINATES / BLOCKS",
                x+11,201,0xFF8C999F);
        mc.fontRenderer.drawString("Drag 3D gizmo to reposition",
                x+11,216,0xFFAABAC2);

        section(mc,x,229,"SOURCE");
        Gui.drawRect(x+11,TOGGLE_Y,x+175,TOGGLE_Y+17,FIELD_BG);
        Gui.drawRect(x+11,TOGGLE_Y+16,x+175,TOGGLE_Y+17,
                0xFF384148);
        mc.fontRenderer.drawString("Enabled",x+17,TOGGLE_Y+4,
                0xFFCFD9DC);
        mc.fontRenderer.drawString(light.enabled?"On":"Off",
                x+113,TOGGLE_Y+4,0xFFE6F0F3);
        Gui.drawRect(x+156,TOGGLE_Y+3,x+169,TOGGLE_Y+14,
                light.enabled?accent:0xFF58626A);
        mc.fontRenderer.drawString("No keys - saved with the scene",
                x+11,279,0xFF879AA4);
        if(warning!=null)
            mc.fontRenderer.drawString(
                    mc.fontRenderer.trimStringToWidth(warning,163),
                    x+11,299,0xFFFF9999);
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
            if(mx>=87)return false; // Dedicated MIRRORS tab
            commit(screen);
            if(mx>=43)
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
        if(my>=TAB_Y&&my<TAB_Y+TAB_HEIGHT)
        {
            for(int i=0;i<3;i++)
            {
                int tx=tabX(leftRight,i);
                if(mx>=tx&&mx<tx+55)
                {
                    commit(screen);
                    persistNative(screen);
                    tab=i;
                    return true;
                }
            }
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
        if(mx>=leftRight+11&&mx<leftRight+175&&
                my>=TOGGLE_Y&&my<TOGGLE_Y+17)
        {
            light.enabled=!light.enabled;
            screen.getCameraWorkspace().saveScene();
            return true;
        }
        commit(screen);
        for(int i=0;i<3;i++)
        {
            int yy=FIRST_FIELD_Y+i*FIELD_SPACING;
            if(mx>=leftRight+48&&mx<leftRight+175&&
                    my>=yy&&my<yy+17)
            {
                fieldAxis=i;
                field=new GuiTextField(18618,Minecraft.getMinecraft().fontRenderer,
                        leftRight+52,yy+1,117,15);
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

    /**
     * GUI guides are deliberately limited to the beam and the two point
     * selectors. The MOVE gizmo is 3D geometry, drawn separately in the
     * same FBO/pass as the already working POSE actor light gizmo.
     */
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
            BetterLightsWireOverlay.draw(camera,light.morph,
                    world.x,world.y,world.z,
                    world.targetX,world.targetY,world.targetZ,x,y,w,h);
        float[] source=PreviewCameraProjection.project(
                light.x,light.y,light.z,camera,x,y,w,h);
        float[] target=PreviewCameraProjection.project(
                light.targetX,light.targetY,light.targetZ,camera,x,y,w,h);
        if(source!=null&&target!=null)
            BetterLightsWireOverlay.line(source[0],source[1],
                    target[0],target[1],0x9983DBF3,x,y,w,h);
        // Keep the opposite anchor selectable even while dragging the
        // 3D gizmo. Do not paint 2D axis labels over the native 3D arrows.
        if(tab!=0)marker(camera,light.x,light.y,light.z,"O",
                0xFFFFD16F,false,x,y,w,h);
        if(tab!=1)marker(camera,light.targetX,light.targetY,light.targetZ,
                "T",0xFF70D9FF,false,x,y,w,h);
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

        // Origin/Target are independently editable world anchors.
        int opposite=tab==0?1:0;
        if(near(camera,component(light,opposite,0),
                component(light,opposite,1),component(light,opposite,2),
                mx,my,x,y,w,h))
        {
            commit(screen);
            persistNative(screen);
            resetMove();
            tab=opposite;
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

        EditorCamera view=cameraForGizmo(screen);
        if(view==null)return false;
        commit(screen);
        // The native gizmo owns the same ray/plane hit test as POSE.
        // A transient Bone/Key is just a writable move delta, never saved.
        resetMove();
        if(!moveGizmo.mouseClicked(mx,my,button,x,y,w,h,
                moveBone,moveKey,gizmoFrame(light),view,true))
            return near(camera,component(light,tab,0),
                    component(light,tab,1),component(light,tab,2),
                    mx,my,x,y,w,h);
        if(!moveGizmo.isDragging())return true;
        draggedLight=light;
        dragComponent=tab;
        dragStartX=component(light,tab,0);
        dragStartY=component(light,tab,1);
        dragStartZ=component(light,tab,2);
        dirty=false;
        return true;
    }

    /** Native BetterLights values remain scene-owned even while dragging. */
    public boolean dragNativeSetting(AnimationEditorScreen screen,int mx,int my)
    {
        if(!opened||tab!=2||nativeActor==null)return false;
        if(!nativeSettings.mouseDragged(screen,mx,my))return false;
        syncNative(screen);
        return true;
    }

    public void releaseNativeSetting(AnimationEditorScreen screen)
    {
        nativeSettings.mouseReleased();
        syncNative(screen);
    }

    public boolean drag(AnimationEditorScreen screen,int mx,int my)
    {
        if(draggedLight==null||!moveGizmo.isDragging())return false;
        if(chosen!=draggedLight){resetMove();return false;}
        if(!moveGizmo.mouseDragged(mx,my))return false;

        AnimationTransform change=moveKey.getTransform();
        double x=dragStartX+change.getPositionX();
        double y=dragStartY+change.getPositionY();
        double z=dragStartZ+change.getPositionZ();
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                ||Math.max(Math.max(Math.abs(x),Math.abs(y)),Math.abs(z))
                    >29999999D)return true;

        int selected=dragComponent;
        double oldX=component(draggedLight,selected,0);
        double oldY=component(draggedLight,selected,1);
        double oldZ=component(draggedLight,selected,2);
        set(draggedLight,selected,0,x);
        set(draggedLight,selected,1,y);
        set(draggedLight,selected,2,z);

        if(BetterLightsWorldState.points(
                draggedLight.x,draggedLight.y,draggedLight.z,
                draggedLight.targetX,draggedLight.targetY,
                draggedLight.targetZ)==null)
        {
            set(draggedLight,selected,0,oldX);
            set(draggedLight,selected,1,oldY);
            set(draggedLight,selected,2,oldZ);
            warning="Origin and Target cannot coincide";
            return true;
        }
        warning=null;
        dirty|=Math.abs(oldX-x)>1E-7D||
                Math.abs(oldY-y)>1E-7D||Math.abs(oldZ-z)>1E-7D;
        return true;
    }

    public boolean release(AnimationEditorScreen screen)
    {
        if(!moveGizmo.isDragging())return false;
        boolean changed=dirty;
        resetMove();
        if(changed)screen.getCameraWorkspace().saveScene();
        return true;
    }

}
