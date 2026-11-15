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
    private int positionScroll;
    private static final int INSPECTOR_WIDTH=185;
    private static final int POSITION_TOP=102;
    private static final int POSITION_ROW=24;
    private static final String[] POSITION_ITEMS={
            "#WORLD POSITION","X","Y","Z","#ROTATION",
            "Yaw","Pitch","Roll","#SOURCE","Enabled"};
    private static final int FIELD_LEFT=87;
    private static final int FIELD_RIGHT=177;
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
        scroll=0;positionScroll=0;properties.reset();resetDrag();
    }
    private CameraSceneParticles.Source sync(AnimationEditorScreen screen)
    {
        CameraSceneParticles.Source source=screen.getSceneState()
                .getCameraParticles().selected();
        if(source!=chosen)
        {
            chosen=source;input=null;editing=-1;positionScroll=0;
            resetDrag();properties.reset();
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
        String add=mc.fontRenderer.trimStringToWidth("+ ADD",
                LEFT/2-13);
        String remove=mc.fontRenderer.trimStringToWidth("REMOVE",
                LEFT/2-13);
        mc.fontRenderer.drawString(add,
                4+Math.max(1,(LEFT/2-6-
                        mc.fontRenderer.getStringWidth(add))/2),
                68,0xFFEAE5F0);
        mc.fontRenderer.drawString(remove,LEFT/2+2+
                Math.max(1,(LEFT/2-6-
                        mc.fontRenderer.getStringWidth(remove))/2),
                68,0xFFEAE5F0);
        Gui.drawRect(4,87,LEFT-4,bottom-7,
                EditorVisualStyle.themedSurface(.045F));
        mc.fontRenderer.drawString(
                mc.fontRenderer.trimStringToWidth("SCENE PARTICLES",
                        LEFT-25),11,92,
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
            mc.fontRenderer.drawString(
                    mc.fontRenderer.trimStringToWidth(
                            "No particle sources",LEFT-24),
                    12,112,0xFF9FA7AE);
    }
    private int visiblePositionRows()
    {
        return Math.max(0,(bottom-POSITION_TOP-5)/POSITION_ROW);
    }
    private void clampPosition()
    {
        positionScroll=Math.max(0,Math.min(positionScroll,
                Math.max(0,POSITION_ITEMS.length-visiblePositionRows())));
    }
    private static int axisForItem(int index)
    {
        if(index>=1&&index<=3)return index-1;
        if(index>=5&&index<=7)return index-2;
        return -1;
    }
    private static void section(Minecraft mc,int x,int y,String title)
    {
        int accent=EditorThemeManager.get().getAccent();
        Gui.drawRect(x+7,y,x+178,y+19,
                EditorVisualStyle.themedSurface(.18F));
        Gui.drawRect(x+7,y,x+10,y+19,accent);
        mc.fontRenderer.drawString(
                mc.fontRenderer.trimStringToWidth(title,152),
                x+15,y+5,0xFFE2E5EB);
    }

    public void drawRight(AnimationEditorScreen screen,int panelBottom)
    {
        if(!opened)return;
        CameraSceneParticles.Source source=sync(screen);
        Minecraft mc=Minecraft.getMinecraft();
        leftRight=screen.width-INSPECTOR_WIDTH;top=25;bottom=panelBottom;
        int x=leftRight;
        int accent=EditorThemeManager.get().getAccent();
        int bright=EditorThemeManager.get().getAccentBright();

        Gui.drawRect(x,25,screen.width,bottom,
                EditorVisualStyle.themedSurface(.07F));
        Gui.drawRect(x,25,x+2,bottom,
                EditorVisualStyle.themedSurface(.20F));
        mc.fontRenderer.drawString(
                mc.fontRenderer.trimStringToWidth("PARTICLE SOURCE",165),
                x+10,34,bright);
        if(source==null)
        {
            mc.fontRenderer.drawString(
                    mc.fontRenderer.trimStringToWidth(
                            "Create a static source",164),
                    x+10,55,0xFF9CA6B0);
            return;
        }
        Gui.drawRect(x+7,46,x+178,64,
                EditorVisualStyle.themedSurface(.15F));
        Gui.drawRect(x+7,46,x+10,64,accent);
        mc.fontRenderer.drawString(
                mc.fontRenderer.trimStringToWidth(source.name,156),
                x+15,51,0xFFE7EDF2);

        final String[] labels={"POSITION","SETTINGS"};
        for(int i=0;i<labels.length;i++)
        {
            int tx=x+7+i*86;
            boolean selected=tab==i;
            Gui.drawRect(tx,71,tx+84,94,
                    selected?EditorVisualStyle.themedSurface(.29F):
                            EditorVisualStyle.themedSurface(.11F));
            Gui.drawRect(tx,92,tx+84,94,
                    selected?accent:0xFF45404B);
            String label=mc.fontRenderer.trimStringToWidth(labels[i],76);
            mc.fontRenderer.drawString(label,
                    tx+(84-mc.fontRenderer.getStringWidth(label))/2,
                    79,selected?0xFFE9E5F0:0xFFB0A9B8);
        }

        if(tab==1)
        {
            properties.bind(source.morph,
                    () -> screen.getCameraWorkspace().saveScene());
            properties.draw(mc,x,100,bottom);
            return;
        }

        clampPosition();
        int visible=visiblePositionRows();
        for(int row=0;row<visible&&row+positionScroll<
                POSITION_ITEMS.length;row++)
        {
            int index=row+positionScroll;
            int yy=POSITION_TOP+row*POSITION_ROW;
            if(yy+POSITION_ROW>bottom)break;
            String item=POSITION_ITEMS[index];
            if(item.startsWith("#"))
            {
                section(mc,x,yy+2,item.substring(1));
                continue;
            }

            Gui.drawRect(x+7,yy+1,x+178,yy+POSITION_ROW-1,
                    EditorVisualStyle.themedSurface(.045F));
            Gui.drawRect(x+FIELD_LEFT,yy+3,x+FIELD_RIGHT,
                    yy+POSITION_ROW-3,
                    EditorVisualStyle.themedSurface(.07F));
            Gui.drawRect(x+FIELD_LEFT,yy+POSITION_ROW-4,
                    x+FIELD_RIGHT,yy+POSITION_ROW-3,
                    EditorVisualStyle.themedSurface(.25F));
            mc.fontRenderer.drawString(
                    mc.fontRenderer.trimStringToWidth(item,
                            FIELD_LEFT-17),
                    x+11,yy+8,0xFFCAD1D9);

            if("Enabled".equals(item))
            {
                mc.fontRenderer.drawString(source.enabled?"ON":"OFF",
                        x+105,yy+8,0xFFE9EEF2);
                Gui.drawRect(x+162,yy+6,x+174,yy+18,
                        source.enabled?accent:0xFF54535A);
                continue;
            }

            Gui.drawRect(x+FIELD_LEFT,yy+3,x+101,
                    yy+POSITION_ROW-3,
                    EditorVisualStyle.themedSurface(.29F));
            Gui.drawRect(x+163,yy+3,x+FIELD_RIGHT,
                    yy+POSITION_ROW-3,
                    EditorVisualStyle.themedSurface(.29F));
            mc.fontRenderer.drawString("<",x+91,yy+8,0xFFEAE5EF);
            mc.fontRenderer.drawString(">",x+168,yy+8,0xFFEAE5EF);
            int axis=axisForItem(index);
            if(input!=null&&editing==axis)
            {
                input.x=x+102;input.y=yy+5;
                input.width=59;input.height=15;
                input.drawTextBox();
            }
            else
            {
                String value=String.format(java.util.Locale.ROOT,
                        "%.3f",get(source,axis));
                mc.fontRenderer.drawString(
                        mc.fontRenderer.trimStringToWidth(value,56),
                        x+104,yy+8,0xFFE7EBF1);
            }
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
