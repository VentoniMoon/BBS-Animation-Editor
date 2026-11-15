package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Editor-owned, KEYLESS particle emitters in CAMERA.
 * Shares exactly the same ParticleSettingsPanel as keyed actor emitters.
 */
public final class CameraSceneParticlesPanel
{
    private boolean opened;
    private int tab,scroll,top,bottom,leftRight;
    private CameraSceneParticles.Source chosen;
    private final ParticleSettingsPanel properties=new ParticleSettingsPanel();
    private GuiTextField input;
    private int editing=-1;
    private final EditorGizmoController moveGizmo=new EditorGizmoController();
    private final EditorCamera gizmoCamera=new EditorCamera();
    private final ActorAnimationData temporaryRig=
            new ActorAnimationData("scene-particle-gizmo");
    private final AnimationBone moveBone=new AnimationBone("Origin");
    private final AnimationKeyframe moveKey=new AnimationKeyframe(0);
    private CameraSceneParticles.Source dragged;
    private double dragX,dragY,dragZ;
    private boolean moved;

    public CameraSceneParticlesPanel()
    {
        temporaryRig.getBones().add(moveBone);
        moveGizmo.setLightRigTarget(temporaryRig);
        moveGizmo.setMode(EditorGizmoController.Mode.POSITION);
    }
    public boolean opened(){return opened;}
    public void open(){opened=true;}
    public void close(AnimationEditorScreen screen)
    {
        commit(screen);opened=false;properties.reset();resetDrag();
    }
    public void reset()
    {
        opened=false;chosen=null;input=null;editing=-1;tab=0;
        scroll=0;properties.reset();resetDrag();
    }
    private CameraSceneParticles.Source sync(AnimationEditorScreen screen)
    {
        CameraSceneParticles.Source source=screen.getSceneState()
                .getCameraParticles().selected();
        if(source!=chosen)
        {
            chosen=source;input=null;editing=-1;resetDrag();
            properties.reset();
        }
        return chosen;
    }
    private static final int LEFT=CameraWorkspace.CAMERA_LEFT_WIDTH;
    private int visible(){return Math.max(1,(bottom-111)/21);}
    private void clampList(int count)
    {
        scroll=Math.max(0,Math.min(scroll,Math.max(0,count-visible())));
    }
    public void drawLeft(AnimationEditorScreen screen,int panelBottom)
    {
        if(!opened)return;
        Minecraft mc=Minecraft.getMinecraft();
        bottom=panelBottom;
        EditorVisualStyle.drawCameraTabs(mc,LEFT,3);
        Gui.drawRect(0,54,LEFT,bottom,
                EditorVisualStyle.themedSurface(.075F));
        Gui.drawRect(4,61,LEFT/2-2,82,
                EditorVisualStyle.themedSurface(.23F));
        Gui.drawRect(LEFT/2+2,61,LEFT-4,82,
                EditorVisualStyle.themedSurface(.23F));
        mc.fontRenderer.drawString("+ ADD",11,68,0xFFFFFFFF);
        mc.fontRenderer.drawString("REMOVE",LEFT/2+6,68,0xFFFFFFFF);
        Gui.drawRect(4,87,LEFT-4,bottom-7,
                EditorVisualStyle.themedSurface(.045F));
        mc.fontRenderer.drawString("SCENE PARTICLES",11,92,
                EditorThemeManager.get().getAccentBright());
        java.util.List<CameraSceneParticles.Source> list=
                screen.getSceneState().getCameraParticles().all();
        clampList(list.size());
        for(int i=scroll;i<list.size()&&i<scroll+visible();i++)
        {
            int py=106+(i-scroll)*21;
            if(py+16>=bottom-8)break;
            CameraSceneParticles.Source source=list.get(i);
            Gui.drawRect(8,py-2,LEFT-8,py+17,
                    EditorVisualStyle.themedSurface(
                            source==chosen?.28F:.12F));
            if(source==chosen)
                Gui.drawRect(8,py-2,10,py+17,
                        EditorThemeManager.get().getAccent());
            String label=mc.fontRenderer.trimStringToWidth(
                    source.name,LEFT-23);
            mc.fontRenderer.drawString(label,13,py+3,
                    source==chosen?0xFFE0FAEF:0xFFCCD4DB);
        }
        if(list.isEmpty())
            mc.fontRenderer.drawString("No particle sources",
                    12,112,0xFF9FA7AE);
    }
    public void drawRight(AnimationEditorScreen screen,int panelBottom)
    {
        if(!opened)return;
        CameraSceneParticles.Source source=sync(screen);
        Minecraft mc=Minecraft.getMinecraft();
        leftRight=screen.width-185;top=25;bottom=panelBottom;
        Gui.drawRect(leftRight,25,screen.width,bottom,
                EditorVisualStyle.themedSurface(.07F));
        mc.fontRenderer.drawString("PARTICLE SOURCE",leftRight+10,34,
                EditorThemeManager.get().getAccentBright());
        if(source==null)
        {
            mc.fontRenderer.drawString("Create a static source",
                    leftRight+10,55,0xFFB0BAC2);
            return;
        }
        Gui.drawRect(leftRight+7,47,leftRight+178,65,
                EditorVisualStyle.themedSurface(.17F));
        mc.fontRenderer.drawString(
                mc.fontRenderer.trimStringToWidth(source.name,155),
                leftRight+14,51,0xFFE4EDF1);
        String[] labels={"POSITION","SETTINGS"};
        for(int i=0;i<2;i++)
        {
            int tx=leftRight+7+i*86;
            Gui.drawRect(tx,71,tx+84,94,
                    EditorVisualStyle.themedSurface(
                            tab==i?.29F:.12F));
            mc.fontRenderer.drawString(labels[i],tx+8,78,
                    tab==i?0xFFE7EDF1:0xFFADB5BE);
        }
        if(tab==1)
        {
            properties.bind(source.morph,
                    () -> screen.getCameraWorkspace().saveScene());
            properties.draw(mc,leftRight,99,bottom);
            return;
        }
        mc.fontRenderer.drawString("WORLD POSITION",leftRight+10,111,
                0xFFB6C2CC);
        String[] labelsXY={"X","Y","Z","Yaw","Pitch","Roll"};
        for(int i=0;i<labelsXY.length;i++)
        {
            int py=130+i*40;
            if(py+39>=bottom)break;
            mc.fontRenderer.drawString(labelsXY[i],leftRight+12,py,
                    0xFFC9D5DD);
            Gui.drawRect(leftRight+8,py+15,leftRight+178,py+39,
                    EditorVisualStyle.themedSurface(.21F));
            mc.fontRenderer.drawString("<",leftRight+17,py+23,
                    0xFFE2EAF0);
            mc.fontRenderer.drawString(">",leftRight+163,py+23,
                    0xFFE2EAF0);
            double value=get(source,i);
            mc.fontRenderer.drawString(String.format(
                    java.util.Locale.ROOT,"%.3f",value),
                    leftRight+68,py+23,0xFFE1EAEE);
            if(input!=null&&editing==i)input.drawTextBox();
        }
        if(bottom>404)
            mc.fontRenderer.drawString("Gizmo: move XYZ in Preview",
                    leftRight+10,381,0xFF9CAFB8);
        if(bottom>433)
        {
            Gui.drawRect(leftRight+10,405,leftRight+175,430,
                    EditorVisualStyle.themedSurface(.18F));
            mc.fontRenderer.drawString(
                    "ENABLED: "+(source.enabled?"ON":"OFF"),
                    leftRight+18,414,source.enabled?0xFF98E9B6:
                            0xFFDDB2B0);
        }
    }
    private double get(CameraSceneParticles.Source s,int axis)
    {
        switch(axis)
        {
            case 0:return s.x;
            case 1:return s.y;
            case 2:return s.z;
            case 3:return s.yaw;
            case 4:return s.pitch;
            default:return s.roll;
        }
    }
    private void set(CameraSceneParticles.Source s,int axis,double v)
    {
        if(!Double.isFinite(v)||Math.abs(v)>29999999D)return;
        switch(axis)
        {
            case 0:s.x=v;break;
            case 1:s.y=v;break;
            case 2:s.z=v;break;
            case 3:s.yaw=v;break;
            case 4:s.pitch=v;break;
            default:s.roll=v;break;
        }
    }
    public boolean click(AnimationEditorScreen screen,int mx,int my,
            int button)
    {
        if(!opened)return false;
        if(mx<LEFT&&my>=54&&my<bottom)
        {
            if(button!=0)return true;
            commit(screen);
            CameraSceneParticles data=screen.getSceneState()
                    .getCameraParticles();
            if(my>=61&&my<84)
            {
                if(mx<LEFT/2)
                {
                    PreviewCameraPose pose=screen.getCameraWorkspaceNativePose();
                    if(pose!=null)
                    {
                        double[] direction=PreviewCameraProjection.ray(
                                pose,.5D,.5D,0,0,1,1);
                        CameraSceneParticles.Source s=data.add(
                                "Particle "+(data.all().size()+1),
                                pose.x+direction[0]*5D,
                                pose.y+direction[1]*5D,
                                pose.z+direction[2]*5D);
                        if(s!=null)scroll=Math.max(0,data.all().size()-visible());
                    }
                }
                else data.removeSelected();
                chosen=null;sync(screen);
                screen.getCameraWorkspace().saveScene();
                return true;
            }
            if(my>=106)
            {
                int index=scroll+(my-106)/21;
                if(index>=0&&index<data.all().size())
                {
                    data.select(data.all().get(index).id);
                    sync(screen);
                }
            }
            return true;
        }
        if(mx<leftRight||my<25||my>=bottom)return false;
        if(button!=0)return true;
        CameraSceneParticles.Source source=sync(screen);
        if(source==null)return true;
        if(my>=71&&my<94)
        {
            commit(screen);
            tab=mx<leftRight+92?0:1;
            return true;
        }
        if(tab==1)
        {
            return properties.click(mx,my,button);
        }
        if(bottom>433&&my>=405&&my<430)
        {
            source.enabled=!source.enabled;
            screen.getCameraWorkspace().saveScene();return true;
        }
        for(int axis=0;axis<6;axis++)
        {
            int py=130+axis*40;
            if(py+39>=bottom)break;
            if(my>=py+15&&my<py+39)
            {
                commit(screen);
                if(mx<leftRight+38)
                {
                    set(source,axis,get(source,axis)-.0625D);
                    screen.getCameraWorkspace().saveScene();
                }
                else if(mx>=leftRight+153)
                {
                    set(source,axis,get(source,axis)+.0625D);
                    screen.getCameraWorkspace().saveScene();
                }
                else
                {
                    editing=axis;
                    input=new GuiTextField(796,
                            Minecraft.getMinecraft().fontRenderer,
                            leftRight+54,py+19,91,17);
                    input.setMaxStringLength(30);
                    input.setText(Double.toString(get(source,axis)));
                    input.setFocused(true);
                }
                return true;
            }
        }
        return true;
    }
    private void commit(AnimationEditorScreen screen)
    {
        if(input==null||chosen==null)return;
        try
        {
            double value=Double.parseDouble(input.getText().trim());
            set(chosen,editing,value);
            screen.getCameraWorkspace().saveScene();
        }
        catch(NumberFormatException ignored){}
        input=null;editing=-1;
    }
    public boolean keyTyped(AnimationEditorScreen screen,char c,int code)
    {
        if(!opened)return false;
        if(tab==1&&properties.keyTyped(c,code))return true;
        if(input==null)return false;
        if(code==Keyboard.KEY_ESCAPE)
        {
            input=null;editing=-1;return true;
        }
        if(code==Keyboard.KEY_RETURN||
                code==Keyboard.KEY_NUMPADENTER)
        {
            commit(screen);return true;
        }
        input.textboxKeyTyped(c,code);
        return true;
    }
    public boolean scroll(AnimationEditorScreen screen,int mx,int my,
            int direction)
    {
        if(!opened||direction==0)return false;
        if(tab==1&&properties.scroll(mx,my,direction))return true;
        if(mx>=0&&mx<LEFT&&my>=85&&my<bottom)
        {
            int count=screen.getSceneState().getCameraParticles().all().size();
            scroll=Math.max(0,Math.min(Math.max(0,count-visible()),
                    scroll+(direction>0?-1:1)));
            return true;
        }
        return false;
    }
    private BlockbusterRecordFrame originFrame(CameraSceneParticles.Source s)
    {
        BlockbusterRecordFrame f=new BlockbusterRecordFrame();
        f.setX(s.x);f.setY(s.y);f.setZ(s.z);return f;
    }
    private EditorCamera view(AnimationEditorScreen screen)
    {
        PreviewCameraPose pose=screen.getCameraWorkspaceNativePose();
        if(pose==null)return null;
        gizmoCamera.apply(pose);return gizmoCamera;
    }
    public void drawNativeGizmo(AnimationEditorScreen screen,Minecraft mc,
            int x,int y,int w,int h)
    {
        if(!opened||tab!=0||sync(screen)==null||
                screen.getCameraVideoExport().isExporting())return;
        EditorCamera camera=view(screen);
        if(camera!=null)
            moveGizmo.draw3D(mc,x,y,w,h,
                    moveBone,moveKey,originFrame(chosen),camera,true);
    }
    private void resetDrag()
    {
        moveGizmo.mouseReleased();
        moveKey.getTransform().setPosition(0F,0F,0F);
        dragged=null;moved=false;
    }
    public boolean beginGizmo(AnimationEditorScreen screen,int mx,int my,
            int button,int x,int y,int w,int h)
    {
        if(!opened||tab!=0||button!=0||sync(screen)==null||
                mx<x||mx>=x+w||my<y||my>=y+h)return false;
        EditorCamera camera=view(screen);
        if(camera==null)return false;
        resetDrag();
        moveGizmo.setGizmoFrame(screen.getCurrentFrame());
        if(!moveGizmo.mouseClicked(mx,my,button,x,y,w,h,moveBone,moveKey,
                originFrame(chosen),camera,true))
            return false;
        if(!moveGizmo.isDragging())return true;
        dragged=chosen;dragX=chosen.x;dragY=chosen.y;dragZ=chosen.z;
        return true;
    }
    public boolean drag(AnimationEditorScreen screen,int mx,int my)
    {
        if(dragged==null||!moveGizmo.isDragging())return false;
        if(chosen!=dragged){resetDrag();return false;}
        if(!moveGizmo.mouseDragged(mx,my))return false;
        AnimationTransform delta=moveKey.getTransform();
        double x=dragX+delta.getPositionX(),
                y=dragY+delta.getPositionY(),
                z=dragZ+delta.getPositionZ();
        if(Math.abs(x)>29999999D||Math.abs(y)>29999999D||
                Math.abs(z)>29999999D)return true;
        moved|=Math.abs(x-dragged.x)>1E-7D||
                Math.abs(y-dragged.y)>1E-7D||
                Math.abs(z-dragged.z)>1E-7D;
        dragged.x=x;dragged.y=y;dragged.z=z;
        return true;
    }
    public void release(AnimationEditorScreen screen)
    {
        boolean save=moved;
        resetDrag();
        if(save)screen.getCameraWorkspace().saveScene();
    }
}
