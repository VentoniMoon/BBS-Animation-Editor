package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;

/**
 * CAMERA Export dialog, intentionally separate from the 4000-line editor GUI.
 * The panel is a non-GL film overlay and is NEVER captured into the MP4.
 *
 * First integrated backend: 20 FPS H.264 MP4; quality/output file/FFmpeg/
 * resolution are user-selectable. Unsupported high-FPS and sound are labelled
 * explicitly instead of making controls appear functional when they aren't.
 */
public final class CameraExportDialog
{
    private static final int PANEL=0xF41A222B;
    private static final int BORDER=0xFF415767;
    private static final int BLUE=0xFF65CDF7;
    private static final int MUTED=0xFFAABCCA;
    private static final int WHITE=0xFFE5EFF4;
    private static final int[] QUALITY={18,22,26};
    private static final String[] QUALITY_LABELS={
            "High (CRF 18)","Balanced (22)","Smaller (26)"};
    private boolean open;
    private int x,y,w,h,quality=0;
    private GuiTextField widthField,heightField,ffmpegField,outputField;
    private String error;

    public boolean isOpen(){return open;}

    public void open(AnimationEditorScreen editor)
    {
        if(editor==null)return;
        open=true;
        error=null;
        CameraOutputSettings format=editor.getCameraOutputSettings();
        Minecraft mc=Minecraft.getMinecraft();
        FontRenderer font=mc.fontRenderer;
        widthField=new GuiTextField(94,font,0,0,70,18);
        heightField=new GuiTextField(95,font,0,0,70,18);
        ffmpegField=new GuiTextField(96,font,0,0,270,18);
        outputField=new GuiTextField(97,font,0,0,270,18);
        widthField.setMaxStringLength(5);
        heightField.setMaxStringLength(5);
        ffmpegField.setMaxStringLength(1024);
        outputField.setMaxStringLength(1024);
        widthField.setText(Integer.toString(format.getWidth()));
        heightField.setText(Integer.toString(format.getHeight()));
        ffmpegField.setText(CameraExportPreferences.loadFfmpeg(mc.mcDataDir));
        File folder=new File(mc.mcDataDir,"config/bbs-editor/exports");
        String name="camera-"+new SimpleDateFormat("yyyyMMdd-HHmmss",
                Locale.ROOT).format(new Date())+".mp4";
        outputField.setText(new File(folder,name).getAbsolutePath());
        quality=0;
    }

    public void close()
    {
        if(open&&ffmpegField!=null)saveFfmpegPreference();
        open=false;
        widthField=heightField=ffmpegField=outputField=null;
        error=null;
    }

    private void saveFfmpegPreference()
    {
        if(ffmpegField==null)return;
        String path=ffmpegField.getText().trim();
        if(path.isEmpty())return;
        try
        {
            CameraExportPreferences.saveFfmpeg(
                    Minecraft.getMinecraft().mcDataDir,path);
        }
        catch(IOException badPreference)
        {
            System.err.println("[BBS CAMERA] Cannot save FFmpeg path: "+
                    badPreference.getMessage());
        }
    }

    private void bounds(AnimationEditorScreen screen)
    {
        w=Math.min(426,Math.max(290,screen.width-14));
        h=Math.min(300,Math.max(250,screen.height-12));
        x=Math.max(4,(screen.width-w)/2);
        y=Math.max(4,(screen.height-h)/2);
        int fieldWidth=Math.max(100,w-30);
        widthField.x=x+92;widthField.y=y+53;
        widthField.width=65;
        heightField.x=x+202;heightField.y=y+53;
        heightField.width=65;
        ffmpegField.x=x+15;ffmpegField.y=y+173;
        ffmpegField.width=fieldWidth;
        outputField.x=x+15;outputField.y=y+212;
        outputField.width=fieldWidth;
    }

    public void draw(AnimationEditorScreen editor,int mx,int my)
    {
        if(!open||editor==null)return;
        bounds(editor);
        Minecraft mc=Minecraft.getMinecraft();
        FontRenderer font=mc.fontRenderer;
        CameraVideoRenderController video=editor.getCameraVideoExport();
        Gui.drawRect(0,0,editor.width,editor.height,0xA900070C);
        Gui.drawRect(x-2,y-2,x+w+2,y+h+2,0xEB071017);
        Gui.drawRect(x,y,x+w,y+h,PANEL);
        Gui.drawRect(x,y,x+w,y+2,BLUE);
        // Rebind the real GL texture state immediately before painting the
        // modal text. This is needed even if START RENDER fails its FFmpeg /
        // ffprobe preflight and no native frame is ever captured. It also
        // protects the dialog from texture mutations later in drawPreview.
        CameraNativeFrameProbe.resyncDefaultTextureForGui();
        font.drawString("CAMERA  /  EXPORT VIDEO",x+15,y+13,BLUE);
        font.drawString("x",x+w-20,y+13,WHITE);
        Gui.drawRect(x+12,y+32,x+w-12,y+33,BORDER);

        font.drawString("Resolution",x+15,y+58,MUTED);
        widthField.drawTextBox();
        font.drawString("x",x+179,y+58,MUTED);
        heightField.drawTextBox();
        font.drawString("MP4  -  H.264 / yuv420p",x+15,y+83,WHITE);
        font.drawString("20 FPS  (30/60 FPS: future Jolt sync)",
                x+15,y+99,MUTED);

        font.drawString("Encoding quality",x+15,y+120,MUTED);
        int gap=6,bw=Math.max(58,(w-30-2*gap)/3);
        for(int i=0;i<QUALITY.length;i++)
        {
            int bx=x+15+i*(bw+gap);
            Gui.drawRect(bx,y+133,bx+bw,y+154,
                    i==quality?0xFF31586C:0xFF293540);
            if(i==quality)Gui.drawRect(bx,y+152,bx+bw,y+154,BLUE);
            String label=QUALITY_LABELS[i];
            font.drawString(font.trimStringToWidth(label,bw-8),
                    bx+4,y+139,i==quality?BLUE:WHITE);
        }

        font.drawString("FFmpeg executable (ffmpeg or absolute path)",
                x+15,y+162,MUTED);
        ffmpegField.drawTextBox();
        font.drawString("Save MP4 as",x+15,y+201,MUTED);
        outputField.drawTextBox();

        int videoCount=0,mediaCount=0;
        for(CameraClipDocument.Clip clip:editor.getSceneState()
                .getCameraClips().clips())
        {
            if(!clip.enabled)continue;
            if(clip.kind==CameraClipDocument.Kind.VIDEO)videoCount++;
            if(clip.kind==CameraClipDocument.Kind.IMAGE||
                    clip.kind==CameraClipDocument.Kind.SUBTITLE)mediaCount++;
        }
        String capability="VIDEO: "+videoCount+"  |  Images/Subtitles: "+
                mediaCount+"  |  Audio: not yet mixed";
        font.drawString(font.trimStringToWidth(
                    video.isExporting()?video.getStage():capability,w-30),
                x+15,y+239,video.isExporting()?BLUE:MUTED);

        int buttonY=y+h-34;
        if(video.isExporting())
        {
            int total=Math.max(1,video.getTotalFrames());
            int encoded=video.getEncodedFrames();
            Gui.drawRect(x+15,buttonY-10,x+w-15,buttonY-6,
                    0xFF243847);
            int fill=(int)((long)(w-30)*Math.min(total,encoded)/total);
            Gui.drawRect(x+15,buttonY-10,x+15+fill,buttonY-6,BLUE);
            drawButton(font,x+15,buttonY,w-30,"CANCEL EXPORT",true);
        }
        else
        {
            drawButton(font,x+15,buttonY,Math.max(118,w/2-20),
                    "START RENDER",true);
            drawButton(font,x+w/2+5,buttonY,
                    Math.max(98,w/2-20),"CLOSE",false);
        }
        if(error!=null)
            font.drawString(font.trimStringToWidth(error,w-30),
                    x+15,y+h-45,0xFFFF9393);
        else if(video.isExporting())
            font.drawString("Encoded: "+video.getEncodedFrames()+"/"+
                    video.getTotalFrames()+"    Captured: "+
                    video.getCapturedFrames(),x+15,y+h-47,BLUE);
        else if(video.status()!=null&&!video.status().isEmpty())
            font.drawString(font.trimStringToWidth(video.status(),w-30),
                    x+15,y+h-46,MUTED);
    }

    private static void drawButton(FontRenderer font,int bx,int by,
            int bw,String label,boolean active)
    {
        Gui.drawRect(bx,by,bx+bw,by+23,
                active?0xFF315A70:0xFF2D3740);
        Gui.drawRect(bx,by+21,bx+bw,by+23,
                active?BLUE:0xFF617482);
        font.drawString(label,bx+Math.max(5,(bw-
                font.getStringWidth(label))/2),by+7,active?BLUE:WHITE);
    }

    public boolean mouseClicked(AnimationEditorScreen editor,int mx,int my,
            int mouseButton)
    {
        if(!open)return false;
        if(mouseButton!=0)return true;
        CameraVideoRenderController video=editor.getCameraVideoExport();
        if(video.isExporting())
        {
            if(mx>=x+w-30&&my<y+32)video.cancel("MP4 export canceled");
            if(mx>=x+15&&mx<=x+w-15&&my>=y+h-34&&my<y+h-11)
                video.cancel("MP4 export canceled");
            return true;
        }
        if(mx>=x+w-30&&my<y+32){close();return true;}
        if(mx<x||mx>=x+w||my<y||my>=y+h)
        {close();return true;}

        widthField.mouseClicked(mx,my,mouseButton);
        heightField.mouseClicked(mx,my,mouseButton);
        ffmpegField.mouseClicked(mx,my,mouseButton);
        outputField.mouseClicked(mx,my,mouseButton);
        if(my>=y+133&&my<y+155)
        {
            int gap=6,bw=Math.max(58,(w-30-2*gap)/3);
            for(int i=0;i<QUALITY.length;i++)
            {
                int bx=x+15+i*(bw+gap);
                if(mx>=bx&&mx<bx+bw){quality=i;return true;}
            }
        }
        if(my>=y+h-34&&my<y+h-11)
        {
            if(mx>=x+15&&mx<x+w/2)
                begin(editor);
            else if(mx>x+w/2)close();
        }
        return true;
    }

    private void begin(AnimationEditorScreen editor)
    {
        error=null;
        try
        {
            int width=Integer.parseInt(widthField.getText().trim());
            int height=Integer.parseInt(heightField.getText().trim());
            if(!CameraOutputSettings.isValid(width,height))
                throw new IllegalArgumentException(
                        "Use even dimensions from 128 to 8192");
            // Resolution changes remain part of the CAMERA scene format.
            new CameraExportPlan(width,height,20,editor.getSceneLength());
            String ff=ffmpegField.getText().trim();
            if(ff.isEmpty())throw new IllegalArgumentException(
                    "Enter FFmpeg executable location");
            // Save even if a later encoder/source preflight fails.
            saveFfmpegPreference();
            String destination=outputField.getText().trim();
            if(destination.isEmpty()||
                    !destination.toLowerCase(Locale.ROOT).endsWith(".mp4"))
                throw new IllegalArgumentException(
                        "Output file must end in .mp4");
            File target=new File(destination);
            if(!target.isAbsolute())
                target=new File(Minecraft.getMinecraft().mcDataDir,
                        destination);
            if(target.exists())
                throw new IllegalArgumentException(
                        "File already exists; choose another name");
            CameraVideoRenderController.Options opts=
                    new CameraVideoRenderController.Options(
                            width,height,QUALITY[quality],"medium",
                            new File(ff),target);
            // The film viewport needs this aspect before the very first
            // native export pass, and the scene should remember the choice.
            editor.applyCameraExportOutputSize(width,height);
            if(!editor.getCameraVideoExport().start(editor,opts))
                throw new IllegalArgumentException(
                        editor.getCameraVideoExport().status());
        }
        catch(RuntimeException invalid)
        {
            error=invalid.getMessage();
            // The GUI can be unreadable during a GL/font failure: always
            // preserve the real START RENDER failure in latest.log.
            System.err.println("[BBS Editor][Video Export] Start failed: "+
                    invalid);
        }
    }

    public boolean keyTyped(AnimationEditorScreen editor,
            char character,int keyCode) throws IOException
    {
        if(!open)return false;
        if(keyCode==1)
        {
            if(editor.getCameraVideoExport().isExporting())
                editor.getCameraVideoExport().cancel("MP4 export canceled");
            else close();
            return true;
        }
        if(editor.getCameraVideoExport().isExporting())return true;
        if(keyCode==28||keyCode==156)
        {
            begin(editor);
            return true;
        }
        widthField.textboxKeyTyped(character,keyCode);
        heightField.textboxKeyTyped(character,keyCode);
        ffmpegField.textboxKeyTyped(character,keyCode);
        outputField.textboxKeyTyped(character,keyCode);
        return true;
    }

    /** Wheel events never reach CAMERA's timeline behind the modal window. */
    public boolean scroll(){return open;}
}
