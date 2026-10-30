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

    public boolean opened(){return opened;}
    public void open(){opened=true;}
    public void close(AnimationEditorScreen screen)
    {
        commit(screen);
        opened=false;
        dragging=null;
    }
    public void reset()
    {
        opened=false;field=null;edit=-1;
        dragging=null;dirty=false;listScroll=0;
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
        Gui.drawRect(0,25,CameraWorkspace.CAMERA_LEFT_WIDTH,54,0xFF1D303B);
        mc.fontRenderer.drawString("CLIPS",5,36,0xFF899DAA);
        mc.fontRenderer.drawString("LIGHTS",47,36,0xFF899DAA);
        mc.fontRenderer.drawString("MIRRORS",88,36,0xFF60C8FA);
        Gui.drawRect(0,54,CameraWorkspace.CAMERA_LEFT_WIDTH,bottom,0xFF19232C);
        Gui.drawRect(4,61,64,82,0xFF31495D);
        Gui.drawRect(67,61,126,82,0xFF31495D);
        mc.fontRenderer.drawString("+ ADD",10,68,0xFFFFFFFF);
        mc.fontRenderer.drawString("DELETE",73,68,0xFFFFFFFF);
        mc.fontRenderer.drawString("SCENE MIRRORS",7,88,0xFF8DAAB9);
        List<CameraSceneMirrors.Mirror> all=list(screen).all();
        listScroll=Math.max(0,Math.min(listScroll,
                Math.max(0,all.size()-visible())));
        for(int i=listScroll;i<all.size()&&i<listScroll+visible();i++)
        {
            int yy=106+(i-listScroll)*21;
            if(yy+18>bottom)break;
            CameraSceneMirrors.Mirror m=all.get(i);
            if(m==list(screen).selected())
                Gui.drawRect(4,yy-2,126,yy+17,0xFF34536A);
            mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                    (m.enabled?"":"[ ] ")+m.name,114),
                    8,yy+2,m.enabled?0xFFE0F2FE:0xFF82919C);
        }
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
        Gui.drawRect(inspectorLeft,25,screen.width,panelBottom,0xFF191D21);
        Gui.drawRect(inspectorLeft,25,inspectorLeft+2,panelBottom,0xFF30383E);
        mc.fontRenderer.drawString("MIRROR PROPERTIES",inspectorLeft+9,35,
                0xFF7DD4F8);
        CameraSceneMirrors.Mirror m=list(screen).selected();
        if(m==null)
        {
            mc.fontRenderer.drawString("Add a mirror to the scene",
                    inspectorLeft+9,57,0xFFB3BDC8);
            return;
        }
        Gui.drawRect(inspectorLeft+7,48,inspectorLeft+177,67,0xFF272E34);
        mc.fontRenderer.drawString(m.name,inspectorLeft+12,53,0xFFE8F1F3);
        mc.fontRenderer.drawString("WORLD RECTANGLE",inspectorLeft+10,82,
                0xFF9EB4C3);
        for(int i=0;i<LABELS.length;i++)
        {
            int yy=102+i*25;
            mc.fontRenderer.drawString(LABELS[i],inspectorLeft+12,yy,
                    0xFFAAB8C1);
            Gui.drawRect(inspectorLeft+78,yy-4,inspectorLeft+175,yy+15,
                    0xFF10151A);
            if(field!=null&&edit==i)field.drawTextBox();
            else mc.fontRenderer.drawString(String.format(Locale.ROOT,
                    "%.3f",get(m,i)),inspectorLeft+85,yy,0xFFE3EFF4);
        }
        Gui.drawRect(inspectorLeft+10,257,inspectorLeft+175,275,
                0xFF29323C);
        mc.fontRenderer.drawString("Enabled",inspectorLeft+16,262,
                0xFFE0EAF0);
        mc.fontRenderer.drawString(m.enabled?"ON":"OFF",
                inspectorLeft+145,262,m.enabled?0xFF8CF0AB:0xFFDD9C9C);
        mc.fontRenderer.drawString("Drag the rectangle in preview",
                inspectorLeft+10,291,0xFF8C9CA8);
        mc.fontRenderer.drawString("Static scene object; no keys",
                inspectorLeft+10,305,0xFF8C9CA8);
        mc.fontRenderer.drawString("Max 32 / reflection view: 1",
                inspectorLeft+10,319,0xFF8C9CA8);
        mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                CameraSceneMirrorRenderer.status(),169),
                inspectorLeft+10,334,0xFF9AD5E5);
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
                screen.getCameraWorkspace().saveScene();
                return true;
            }
            if(my>=106)
            {
                int index=listScroll+(my-106)/21;
                if(index>=0&&index<mirrors.all().size())
                    mirrors.select(mirrors.all().get(index).id);
            }
            return true;
        }
        if(mx<screen.width-185)return false;
        if(button!=0)return true;
        CameraSceneMirrors.Mirror m=list(screen).selected();
        if(m==null)return true;
        commit(screen);
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
                edit=i;
                field=new GuiTextField(19251,mc().fontRenderer,
                        screen.width-185+82,yy-2,89,16);
                field.setText(String.format(Locale.ROOT,"%.4f",get(m,i)));
                field.setFocused(true);
                return true;
            }
        }
        return true;
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
    /** Pick the actual rectangle, not an unbounded plane. */
    public boolean beginDrag(AnimationEditorScreen screen,int mx,int my,
            int button,int px,int py,int pw,int ph)
    {
        if(!opened||button!=0||field!=null||
                mx<px||my<py||mx>=px+pw||my>=py+ph)return false;
        PreviewCameraPose cam=screen.getCameraWorkspaceNativePose();
        double[] ray=PreviewCameraProjection.ray(cam,mx,my,
                px,py,pw,ph);
        if(ray==null)return false;
        List<CameraSceneMirrors.Mirror> all=list(screen).all();
        for(int i=all.size()-1;i>=0;i--)
        {
            CameraSceneMirrors.Mirror m=all.get(i);
            double[] hit=CameraMirrorMath.hit(m.x,m.y,m.z,m.yaw,
                    m.width,m.height,cam.x,cam.y,cam.z,
                    ray[0],ray[1],ray[2]);
            if(hit==null)continue;
            list(screen).select(m.id);
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
        if(dragging==null)return false;
        PreviewCameraPose cam=screen.getCameraWorkspaceNativePose();
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
    }
}
