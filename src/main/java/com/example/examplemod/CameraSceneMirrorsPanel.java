package com.example.examplemod;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Scene MIRRORS tab: independent of CAMERA clips, records and actor physics.
 * A rectangular world-space hit area can be selected and slid across its
 * plane directly in the film viewport; numbers handle precise placement.
 */
public final class CameraSceneMirrorsPanel
{
    private boolean opened;
    private int bottom,inspectorLeft;
    private int listScroll;
    private GuiTextField field;
    private int edit=-1;
    private CameraSceneMirrors.Mirror dragging;
    private double startX,startY,startZ,hitX,hitY,hitZ;
    private boolean dirty;
    /**
     * A transient editor-only rig reuses the verified 3D POSITION handles
     * from the static light editor. The mirror itself has no keyframes.
     */
    private final EditorGizmoController moveGizmo=new EditorGizmoController();
    private final EditorCamera moveCamera=new EditorCamera();
    private final ActorAnimationData moveRig=
            new ActorAnimationData("camera-static-mirror-gizmo");
    private final AnimationBone moveBone=new AnimationBone(BetterLightsRig.ORIGIN);
    private final AnimationKeyframe moveKey=new AnimationKeyframe(0);
    private CameraSceneMirrors.Mirror gizmoMirror;
    private double gizmoStartX,gizmoStartY,gizmoStartZ;
    private boolean yawMode;
    private CameraSceneMirrors.Mirror yawDragging;
    private double yawStart,yawLastAngle,yawAccumulated;
    private int yawStartMouseX;
    private boolean yawRayValid;

    public CameraSceneMirrorsPanel()
    {
        moveRig.getBones().add(moveBone);
        moveGizmo.setLightRigTarget(moveRig);
        moveGizmo.setMode(EditorGizmoController.Mode.POSITION);
    }

    private void stopGizmo()
    {
        moveGizmo.mouseReleased();
        moveKey.getTransform().setPosition(0F,0F,0F);
        gizmoMirror=null;
        yawDragging=null;
        yawRayValid=false;
    }

    private EditorCamera cameraForGizmo(AnimationEditorScreen screen)
    {
        PreviewCameraPose pose=screen.getCameraWorkspaceNativePose();
        if(pose==null)return null;
        moveCamera.apply(pose);
        return moveCamera;
    }

    private static BlockbusterRecordFrame pointFrame(CameraSceneMirrors.Mirror m)
    {
        BlockbusterRecordFrame frame=new BlockbusterRecordFrame();
        frame.setX(m.x);frame.setY(m.y);frame.setZ(m.z);
        return frame;
    }

    /** Same 3D move arrows and planes as POSE/LIGHTS, in the Preview FBO. */
    public void drawNativeGizmo(AnimationEditorScreen screen,Minecraft mc,
            int x,int y,int w,int h)
    {
        if(!opened||yawMode||screen.getCameraVideoExport().isExporting())
            return;
        CameraSceneMirrors.Mirror selected=list(screen).selected();
        if(selected==null)return;
        EditorCamera view=cameraForGizmo(screen);
        if(view!=null)
            moveGizmo.draw3D(mc,x,y,w,h,moveBone,moveKey,
                    pointFrame(selected),view,true);
    }

    public boolean opened(){return opened;}
    public void open(){opened=true;}
    public void close(AnimationEditorScreen screen)
    {
        commit(screen);
        opened=false;
        dragging=null;
        stopGizmo();
    }
    public void reset()
    {
        opened=false;field=null;edit=-1;
        dragging=null;dirty=false;listScroll=0;
        yawMode=false;stopGizmo();
    }
    private static CameraSceneMirrors list(AnimationEditorScreen screen)
    {
        return screen.getSceneState().getCameraMirrors();
    }
    private int visible(){return Math.max(1,(bottom-112)/21);}
    public void drawLeft(AnimationEditorScreen screen,int panelBottom)
    {
        if(!opened)return;
        bottom=panelBottom;
        Minecraft mc=Minecraft.getMinecraft();
        EditorVisualStyle.drawCameraTabs(mc,CameraWorkspace.CAMERA_LEFT_WIDTH,2);
        Gui.drawRect(0,54,CameraWorkspace.CAMERA_LEFT_WIDTH,bottom,EditorVisualStyle.themedSurface(0.075F));
        Gui.drawRect(4,61,64,82,EditorVisualStyle.themedSurface(0.23F));
        Gui.drawRect(67,61,126,82,EditorVisualStyle.themedSurface(0.23F));
        mc.fontRenderer.drawString("+ ADD",10,68,0xFFFFFFFF);
        mc.fontRenderer.drawString("DELETE",73,68,0xFFFFFFFF);
        Gui.drawRect(4,87,CameraWorkspace.CAMERA_LEFT_WIDTH-4,bottom-7,
                EditorVisualStyle.themedSurface(0.045F));
        Gui.drawRect(4,87,CameraWorkspace.CAMERA_LEFT_WIDTH-4,88,
                EditorVisualStyle.themedSurface(0.30F));
        Gui.drawRect(6,90,9,101,EditorThemeManager.get().getAccent());
        mc.fontRenderer.drawString("SCENE MIRRORS",13,92,
                EditorThemeManager.get().getAccentBright());
        List<CameraSceneMirrors.Mirror> all=list(screen).all();
        listScroll=Math.max(0,Math.min(listScroll,
                Math.max(0,all.size()-visible())));
        for(int i=listScroll;i<all.size()&&i<listScroll+visible();i++)
        {
            int yy=106+(i-listScroll)*21;
            if(yy+18>bottom)break;
            CameraSceneMirrors.Mirror m=all.get(i);
            Gui.drawRect(8,yy-2,CameraWorkspace.CAMERA_LEFT_WIDTH-8,yy+17,
                    EditorVisualStyle.themedSurface(m==list(screen).selected()?
                            0.28F:(i%2==0?0.14F:0.10F)));
            if(m==list(screen).selected())
                Gui.drawRect(8,yy-2,10,yy+17,
                        EditorThemeManager.get().getAccent());
            mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                    (m.enabled?"":"[ ] ")+m.name,114),
                    8,yy+2,m.enabled?0xFFE0F2FE:0xFF82919C);
        }
        if(all.isEmpty())
            mc.fontRenderer.drawString("No mirrors yet",12,111,0xFF929CA8);
    }
    private static final String[] LABELS={
            "X","Y","Z","YAW (DEG)","WIDTH","HEIGHT"};
    private static double get(CameraSceneMirrors.Mirror m,int i)
    {
        switch(i)
        {
            case 0:return m.x;
            case 1:return m.y;
            case 2:return m.z;
            case 3:return m.yaw;
            case 4:return m.width;
            default:return m.height;
        }
    }
    private static void set(CameraSceneMirrors.Mirror m,int i,double v)
    {
        switch(i)
        {
            case 0:m.x=v;break;
            case 1:m.y=v;break;
            case 2:m.z=v;break;
            case 3:m.yaw=v;break;
            case 4:m.width=Math.max(0.25D,Math.min(64D,v));break;
            case 5:m.height=Math.max(0.25D,Math.min(64D,v));break;
        }
    }
    public void drawRight(AnimationEditorScreen screen,int panelBottom)
    {
        if(!opened)return;
        Minecraft mc=Minecraft.getMinecraft();
        inspectorLeft=screen.width-185;
        Gui.drawRect(inspectorLeft,25,screen.width,panelBottom,EditorVisualStyle.themedSurface(0.070F));
        Gui.drawRect(inspectorLeft,25,inspectorLeft+2,panelBottom,EditorVisualStyle.themedSurface(0.220F));
        mc.fontRenderer.drawString("MIRROR PROPERTIES",inspectorLeft+9,35,
                EditorThemeManager.get().getAccentBright());
        CameraSceneMirrors.Mirror m=list(screen).selected();
        if(m==null)
        {
            mc.fontRenderer.drawString("Add a mirror to the scene",
                    inspectorLeft+9,57,0xFFB3BDC8);
            return;
        }
        Gui.drawRect(inspectorLeft+7,48,inspectorLeft+177,67,EditorVisualStyle.themedSurface(0.150F));
        mc.fontRenderer.drawString(m.name,inspectorLeft+12,53,0xFFE8F1F3);
        mc.fontRenderer.drawString("WORLD RECTANGLE",inspectorLeft+10,82,
                0xFF9EB4C3);
        for(int i=0;i<LABELS.length;i++)
        {
            int yy=102+i*25;
            mc.fontRenderer.drawString(LABELS[i],inspectorLeft+12,yy,
                    0xFFAAB8C1);
            Gui.drawRect(inspectorLeft+78,yy-4,inspectorLeft+175,yy+15,
                    EditorVisualStyle.themedSurface(0.045F));
            Gui.drawRect(inspectorLeft+78,yy-4,inspectorLeft+92,yy+15,
                    EditorVisualStyle.themedSurface(0.29F));
            Gui.drawRect(inspectorLeft+161,yy-4,inspectorLeft+175,yy+15,
                    EditorVisualStyle.themedSurface(0.29F));
            mc.fontRenderer.drawString("<",inspectorLeft+82,yy,0xFFE3EFF4);
            mc.fontRenderer.drawString(">",inspectorLeft+165,yy,0xFFE3EFF4);
            if(field!=null&&edit==i)field.drawTextBox();
            else mc.fontRenderer.drawString(String.format(Locale.ROOT,
                    "%.4f",get(m,i)),inspectorLeft+95,yy,0xFFE3EFF4);
        }
        Gui.drawRect(inspectorLeft+10,257,inspectorLeft+175,275,
                EditorVisualStyle.themedSurface(0.160F));
        mc.fontRenderer.drawString("Enabled",inspectorLeft+16,262,
                0xFFE0EAF0);
        mc.fontRenderer.drawString(m.enabled?"ON":"OFF",
                inspectorLeft+145,262,m.enabled?0xFF8CF0AB:0xFFDD9C9C);
        Gui.drawRect(inspectorLeft+10,283,inspectorLeft+90,303,
                EditorVisualStyle.themedSurface(yawMode?0.10F:0.34F));
        Gui.drawRect(inspectorLeft+94,283,inspectorLeft+175,303,
                EditorVisualStyle.themedSurface(yawMode?0.34F:0.10F));
        mc.fontRenderer.drawString("MOVE XYZ",inspectorLeft+20,289,
                yawMode?0xFF9FB4C1:0xFF9BE6FF);
        mc.fontRenderer.drawString("ROTATE Y",inspectorLeft+104,289,
                yawMode?0xFF9BE6FF:0xFF9FB4C1);
        mc.fontRenderer.drawString(yawMode
                ?"Drag the Yaw ring in Preview"
                :"Drag XYZ handles (or mirror face)",
                inspectorLeft+10,314,0xFF8C9CA8);
        mc.fontRenderer.drawString("Static scene object; no keys",
                inspectorLeft+10,327,0xFF8C9CA8);
        mc.fontRenderer.drawString("Max 32 / reflection view: 1",
                inspectorLeft+10,340,0xFF8C9CA8);
        // A single clipped line used to hide the important shaderpack
        // diagnostics (buffer count, deferred and composite stages). Wrap
        // them in the inspector so tests can report the actual blocker.
        String status=CameraSceneMirrorRenderer.status();
        int lineY=357;
        for(int line=0;line<5&&status.length()>0&&
                lineY+9<panelBottom;line++,lineY+=11)
        {
            String portion=mc.fontRenderer.trimStringToWidth(status,164);
            if(portion.length()==0)break;
            mc.fontRenderer.drawString(portion,inspectorLeft+10,lineY,
                    0xFF9AD5E5);
            status=status.substring(portion.length()).trim();
        }
    }
    public boolean click(AnimationEditorScreen screen,int mx,int my,int button)
    {
        int left=CameraWorkspace.CAMERA_LEFT_WIDTH;
        if(my>=25&&my<54&&mx>=0&&mx<left)
        {
            if(mx>=87&&button==0)
            {
                opened=true;
                return true;
            }
            if(opened)close(screen);
            return false; // CLIPS/LIGHTS handled by the existing panel
        }
        if(!opened)return false;
        if(my<54||my>=bottom)return false;
        if(mx<left)
        {
            if(button!=0)return true;
            commit(screen);
            CameraSceneMirrors mirrors=list(screen);
            if(my>=61&&my<83)
            {
                if(mx<65)
                {
                    PreviewCameraPose cam=screen.getCameraWorkspaceNativePose();
                    double[] forward=PreviewCameraProjection.ray(cam,
                            0.5D,0.5D,0,0,1,1);
                    if(forward!=null)
                    {
                        double facing=Math.atan2(-forward[0],-forward[2]);
                        CameraSceneMirrors.Mirror m=mirrors.add(
                                cam.x+forward[0]*5D,
                                cam.y+forward[1]*5D,
                                cam.z+forward[2]*5D,
                                Math.toDegrees(facing));
                        if(m!=null)listScroll=Math.max(0,
                                mirrors.all().size()-visible());
                    }
                }
                else mirrors.removeSelected();
                stopGizmo();dragging=null;dirty=false;
                screen.getCameraWorkspace().saveScene();
                return true;
            }
            if(my>=106)
            {
                int index=listScroll+(my-106)/21;
                if(index>=0&&index<mirrors.all().size())
                {
                    if(mirrors.selected()!=mirrors.all().get(index))
                    {
                        stopGizmo();dragging=null;dirty=false;
                    }
                    mirrors.select(mirrors.all().get(index).id);
                }
            }
            return true;
        }
        if(mx<screen.width-185)return false;
        if(button!=0)return true;
        CameraSceneMirrors.Mirror m=list(screen).selected();
        if(m==null)return true;
        commit(screen);
        if(my>=283&&my<303&&mx>=inspectorLeft+10&&
                mx<inspectorLeft+175)
        {
            boolean rotate=mx>=inspectorLeft+94;
            if(yawMode!=rotate)
            {
                stopGizmo();dragging=null;dirty=false;
                yawMode=rotate;
            }
            return true;
        }
        if(my>=257&&my<275)
        {
            m.enabled=!m.enabled;
            screen.getCameraWorkspace().saveScene();
            return true;
        }
        for(int i=0;i<LABELS.length;i++)
        {
            int yy=102+i*25;
            if(mx>=screen.width-185+78&&mx<screen.width-10&&
                    my>=yy-4&&my<yy+15)
            {
                if(mx<screen.width-185+92 || mx>=screen.width-185+161)
                {
                    stepValue(screen,m,i,
                            mx<screen.width-185+92?-1:1,0.0625D);
                    return true;
                }
                edit=i;
                field=new GuiTextField(19251,mc().fontRenderer,
                        screen.width-185+94,yy-2,66,16);
                field.setText(String.format(Locale.ROOT,"%.4f",get(m,i)));
                field.setFocused(true);
                return true;
            }
        }
        return true;
    }
    private void stepValue(AnimationEditorScreen screen,
            CameraSceneMirrors.Mirror mirror,int index,int direction,double step)
    {
        double before=get(mirror,index);
        if(!Double.isFinite(before))return;
        set(mirror,index,before+direction*step);
        if(get(mirror,index)!=before)screen.getCameraWorkspace().saveScene();
    }

    private static Minecraft mc(){return Minecraft.getMinecraft();}
    private void commit(AnimationEditorScreen screen)
    {
        if(field==null||edit<0)return;
        CameraSceneMirrors.Mirror m=list(screen).selected();
        if(m!=null)try
        {
            double v=Double.parseDouble(field.getText().trim());
            if(Double.isFinite(v))
            {
                set(m,edit,v);
                screen.getCameraWorkspace().saveScene();
            }
        }
        catch(NumberFormatException ignored){}
        field=null;edit=-1;
    }
    public boolean keyTyped(AnimationEditorScreen screen,char typed,int key)
    {
        if(!opened||field==null)return false;
        if(key==Keyboard.KEY_ESCAPE)
        {field=null;edit=-1;return true;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||
                key==Keyboard.KEY_TAB)
        {commit(screen);return true;}
        if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN)
        {
            CameraSceneMirrors.Mirror m=list(screen).selected();
            if(m!=null&&edit>=0)
            {
                stepValue(screen,m,edit,key==Keyboard.KEY_UP?1:-1,0.03125D);
                field.setText(String.format(Locale.ROOT,"%.5f",get(m,edit)));
            }
            return true;
        }
        field.textboxKeyTyped(typed,key);
        return true;
    }
    public boolean scroll(int mx,int my,int direction)
    {
        if(!opened||mx<0||mx>=CameraWorkspace.CAMERA_LEFT_WIDTH||
                my<90||my>=bottom)return false;
        listScroll=Math.max(0,Math.min(
                Math.max(0,32-visible()),
                listScroll+(direction>0?-1:1)));
        return true;
    }
    /** World radius for the editor-only rotation ring, in blocks. */
    private static double yawRadius(CameraSceneMirrors.Mirror mirror)
    {
        return Math.max(0.7D,Math.min(3.0D,mirror.width*0.48D));
    }

    private static float[] projectedYaw(CameraSceneMirrors.Mirror mirror,
            PreviewCameraPose camera,double angle,double radius,
            int px,int py,int pw,int ph)
    {
        double a=Math.toRadians(angle);
        return PreviewCameraProjection.project(
                mirror.x+Math.sin(a)*radius,
                mirror.y,
                mirror.z+Math.cos(a)*radius,
                camera,px,py,pw,ph);
    }

    private static double segmentDistanceSq(double x,double y,
            float[] a,float[] b)
    {
        double dx=b[0]-a[0],dy=b[1]-a[1];
        double length=dx*dx+dy*dy;
        if(length<1E-6D)return (x-a[0])*(x-a[0])+
                (y-a[1])*(y-a[1]);
        double t=Math.max(0D,Math.min(1D,
                ((x-a[0])*dx+(y-a[1])*dy)/length));
        double sx=a[0]+t*dx,sy=a[1]+t*dy;
        return (x-sx)*(x-sx)+(y-sy)*(y-sy);
    }

    /** Projected circle hit test, including edge-on camera views. */
    private static boolean hitYawRing(CameraSceneMirrors.Mirror m,
            PreviewCameraPose camera,int mx,int my,
            int px,int py,int pw,int ph)
    {
        double radius=yawRadius(m);
        float[] previous=null;
        for(int i=0;i<=72;i++)
        {
            float[] next=projectedYaw(m,camera,i*5D,radius,
                    px,py,pw,ph);
            if(previous!=null&&next!=null&&
                    segmentDistanceSq(mx,my,previous,next)<=110D)
                return true;
            previous=next;
        }
        // The front-normal pointer is another grab handle, useful when
        // the horizontal circle projects to a narrow line.
        float[] origin=PreviewCameraProjection.project(
                m.x,m.y,m.z,camera,px,py,pw,ph);
        float[] tip=projectedYaw(m,camera,m.yaw,radius*1.2D,
                px,py,pw,ph);
        return origin!=null&&tip!=null&&
                segmentDistanceSq(mx,my,origin,tip)<=110D;
    }

    /** Yaw = atan2(world X, world Z), matching CameraMirrorMath.normal. */
    private static double rayYaw(CameraSceneMirrors.Mirror mirror,
            PreviewCameraPose camera,int mx,int my,
            int px,int py,int pw,int ph)
    {
        double[] ray=PreviewCameraProjection.ray(camera,mx,my,
                px,py,pw,ph);
        if(ray==null||Math.abs(ray[1])<1E-5D)
            return Double.NaN;
        double t=(mirror.y-camera.y)/ray[1];
        if(t<=0D||t>2048D)return Double.NaN;
        double dx=camera.x+ray[0]*t-mirror.x;
        double dz=camera.z+ray[2]*t-mirror.z;
        if(dx*dx+dz*dz<1E-6D)return Double.NaN;
        return Math.toDegrees(Math.atan2(dx,dz));
    }

    /** Gizmo handles take precedence, but clicking a rectangle still selects. */
    public boolean beginDrag(AnimationEditorScreen screen,int mx,int my,
            int button,int px,int py,int pw,int ph)
    {
        if(!opened||button!=0||field!=null||
                mx<px||my<py||mx>=px+pw||my>=py+ph)return false;
        PreviewCameraPose cam=screen.getCameraWorkspaceNativePose();
        if(cam==null)return false;

        CameraSceneMirrors mirrors=list(screen);
        CameraSceneMirrors.Mirror selected=mirrors.selected();
        if(selected!=null)
        {
            if(yawMode)
            {
                if(hitYawRing(selected,cam,mx,my,px,py,pw,ph))
                {
                    stopGizmo();
                    dragging=null;
                    yawDragging=selected;
                    yawStart=selected.yaw;
                    yawStartMouseX=mx;
                    yawAccumulated=0D;
                    yawLastAngle=rayYaw(selected,cam,mx,my,
                            px,py,pw,ph);
                    yawRayValid=Double.isFinite(yawLastAngle);
                    dirty=false;
                    return true;
                }
            }
            else
            {
                EditorCamera view=cameraForGizmo(screen);
                if(view!=null)
                {
                    stopGizmo();
                    if(moveGizmo.mouseClicked(mx,my,button,px,py,pw,ph,
                            moveBone,moveKey,pointFrame(selected),view,true))
                    {
                        if(moveGizmo.isDragging())
                        {
                            gizmoMirror=selected;
                            gizmoStartX=selected.x;
                            gizmoStartY=selected.y;
                            gizmoStartZ=selected.z;
                            dragging=null;
                            dirty=false;
                        }
                        return true;
                    }
                }
            }
        }

        double[] ray=PreviewCameraProjection.ray(cam,mx,my,
                px,py,pw,ph);
        if(ray==null)return false;
        List<CameraSceneMirrors.Mirror> all=mirrors.all();
        for(int i=all.size()-1;i>=0;i--)
        {
            CameraSceneMirrors.Mirror m=all.get(i);
            double[] hit=CameraMirrorMath.hit(m.x,m.y,m.z,m.yaw,
                    m.width,m.height,cam.x,cam.y,cam.z,
                    ray[0],ray[1],ray[2]);
            if(hit==null)continue;
            if(m!=selected)stopGizmo();
            mirrors.select(m.id);
            // In ROTATE mode a face click selects; only the yaw ring rotates.
            if(yawMode)
            {
                dragging=null;
                return true;
            }
            dragging=m;
            startX=m.x;startY=m.y;startZ=m.z;
            hitX=hit[0];hitY=hit[1];hitZ=hit[2];
            dirty=false;
            return true;
        }
        return false;
    }

    public boolean drag(AnimationEditorScreen screen,int mx,int my,
            int px,int py,int pw,int ph)
    {
        if(gizmoMirror!=null)
        {
            if(gizmoMirror!=list(screen).selected())
            {
                stopGizmo();
                return true;
            }
            if(!moveGizmo.mouseDragged(mx,my))return true;
            AnimationTransform t=moveKey.getTransform();
            double x=gizmoStartX+t.getPositionX();
            double y=gizmoStartY+t.getPositionY();
            double z=gizmoStartZ+t.getPositionZ();
            if(!Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(z)||
                    Math.abs(x)>29999999D||Math.abs(y)>29999999D||
                    Math.abs(z)>29999999D)
                return true;
            dirty|=Math.abs(gizmoMirror.x-x)>1E-7D||
                    Math.abs(gizmoMirror.y-y)>1E-7D||
                    Math.abs(gizmoMirror.z-z)>1E-7D;
            gizmoMirror.x=x;gizmoMirror.y=y;gizmoMirror.z=z;
            return true;
        }
        if(yawDragging!=null)
        {
            if(yawDragging!=list(screen).selected())
            {
                stopGizmo();
                return true;
            }
            PreviewCameraPose cam=screen.getCameraWorkspaceNativePose();
            if(cam==null)return true;
            double nextYaw;
            if(yawRayValid)
            {
                double angle=rayYaw(yawDragging,cam,mx,my,
                        px,py,pw,ph);
                if(!Double.isFinite(angle))return true;
                double step=angle-yawLastAngle;
                while(step>180D)step-=360D;
                while(step< -180D)step+=360D;
                yawAccumulated+=step;
                yawLastAngle=angle;
                nextYaw=yawStart+yawAccumulated;
            }
            else
            {
                // At eye-level the horizontal ray is parallel to the yaw
                // ring. Screen-horizontal motion remains a reliable control.
                nextYaw=yawStart+(mx-yawStartMouseX)*0.55D;
            }
            if(Double.isFinite(nextYaw))
            {
                dirty|=Math.abs(yawDragging.yaw-nextYaw)>1E-7D;
                yawDragging.yaw=nextYaw;
            }
            return true;
        }
        if(dragging==null)return false;
        PreviewCameraPose cam=screen.getCameraWorkspaceNativePose();
        if(cam==null)return true;
        double[] ray=PreviewCameraProjection.ray(cam,mx,my,
                px,py,pw,ph);
        if(ray==null)return true;
        double[] n=CameraMirrorMath.normal(dragging.yaw);
        double denom=ray[0]*n[0]+ray[2]*n[2];
        if(Math.abs(denom)<1E-7D)return true;
        double t=((startX-cam.x)*n[0]+(startZ-cam.z)*n[2])/denom;
        if(t<=0D||t>2048D)return true;
        dragging.x=startX+cam.x+ray[0]*t-hitX;
        dragging.y=startY+cam.y+ray[1]*t-hitY;
        dragging.z=startZ+cam.z+ray[2]*t-hitZ;
        dirty=true;
        return true;
    }

    public void release(AnimationEditorScreen screen)
    {
        if(dirty)screen.getCameraWorkspace().saveScene();
        stopGizmo();
        dragging=null;dirty=false;
    }

    /** Editor-only outline: not part of native world capture / MP4 output. */
    public void drawGuide(AnimationEditorScreen screen,
            int px,int py,int pw,int ph)
    {
        if(!opened)return;
        CameraSceneMirrors.Mirror m=list(screen).selected();
        if(m==null)return;
        PreviewCameraPose cam=screen.getCameraWorkspaceNativePose();
        double[][] corners=CameraMirrorMath.corners(m.x,m.y,m.z,
                m.yaw,m.width,m.height);
        float[][] pts=new float[4][];
        for(int i=0;i<4;i++)
        {
            pts[i]=PreviewCameraProjection.project(corners[i][0],
                    corners[i][1],corners[i][2],cam,px,py,pw,ph);
            if(pts[i]==null)return;
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2F);
        GL11.glColor4f(0.3F,0.85F,1F,1F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for(int i=0;i<4;i++)GL11.glVertex2f(pts[i][0],pts[i][1]);
        GL11.glEnd();
        GL11.glPopAttrib();

        // Explain the tinted surface in the actual viewport, not only in
        // the right inspector (which may be hidden or cropped). This overlay
        // is editor chrome and never enters the native world frame/export.
        String reason=CameraSceneMirrorRenderer.surfaceHint();
        if(reason.length()>0&&pw>30&&ph>20)
        {
            Minecraft mc=Minecraft.getMinecraft();
            int labelWidth=Math.max(1,Math.min(300,pw-12));
            String shown=mc.fontRenderer.trimStringToWidth(reason,
                    labelWidth-8);
            int textWidth=mc.fontRenderer.getStringWidth(shown);
            float minX=pts[0][0],minY=pts[0][1];
            for(int i=1;i<4;i++)
            {
                minX=Math.min(minX,pts[i][0]);
                minY=Math.min(minY,pts[i][1]);
            }
            int x=(int)Math.max(px+6,Math.min(px+pw-textWidth-8,
                    Math.round(minX+4)));
            int y=(int)Math.max(py+6,Math.min(py+ph-15,
                    Math.round(minY+5)));
            Gui.drawRect(x-3,y-3,x+textWidth+4,y+12,0xD9152533);
            mc.fontRenderer.drawStringWithShadow(shown,x,y,
                    0xFFFFD087);
        }
    }
}
