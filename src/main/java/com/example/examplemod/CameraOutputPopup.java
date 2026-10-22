package com.example.examplemod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;

/**
 * A single-icon CAMERA output menu. The resolution belongs to the scene.
 * No global Minecraft display resize and no encoder is started here.
 */
public final class CameraOutputPopup
{
    private static final int BG=0xF21A2026;
    private static final int ROW=0xFF28313A;
    private static final int SELECTED=0xFF294E62;
    private static final int TEXT=0xFFE2EDF4;
    private static final int MUTED=0xFFA4B7C3;
    private static final int BLUE=0xFF65CDF7;
    private static final int HEADER_HEIGHT=26;
    private static final int ROW_HEIGHT=19;

    private static final class Option
    {
        final String group;
        final CameraOutputSettings.Preset preset;
        final boolean custom;

        Option(String group,CameraOutputSettings.Preset preset,boolean custom)
        {
            this.group=group;this.preset=preset;this.custom=custom;
        }
        boolean heading(){return group!=null;}
    }

    private final List<Option> options=new ArrayList<Option>();
    private boolean opened;
    private boolean editingCustom;
    private int x,y,width,height,scroll,scrollMax;
    private int listX,listTop,listBottom;
    private int customX,customY,applyX,applyY,backX;
    private GuiTextField widthInput,heightInput;
    private String error;
    private final List<int[]> clicked=new ArrayList<int[]>();

    public CameraOutputPopup()
    {
        String current="";
        for(CameraOutputSettings.Preset preset:CameraOutputSettings.PRESETS)
        {
            if(!preset.group.equals(current))
            {
                current=preset.group;
                options.add(new Option(current,null,false));
            }
            options.add(new Option(null,preset,false));
        }
        options.add(new Option(null,null,true));
    }

    public boolean isOpen(){return opened;}

    public void toggle()
    {
        opened=!opened;
        editingCustom=false;
        error=null;
        scroll=0;
    }

    public void close()
    {
        opened=false;
        editingCustom=false;
        widthInput=null;
        heightInput=null;
        error=null;
    }

    public void draw(Minecraft mc,CameraOutputSettings settings,
            int left,int top,int right,int toolbarY,int mouseX,int mouseY)
    {
        if(!opened||mc==null)return;
        FontRenderer font=mc.fontRenderer;
        width=Math.min(263,Math.max(160,right-left-12));
        height=Math.min(335,Math.max(80,toolbarY-top-15));
        x=Math.max(left+4,Math.min(right-width-4,right-width-12));
        y=Math.max(top+3,toolbarY-height-4);
        listX=x+7;listTop=y+HEADER_HEIGHT+2;
        listBottom=y+height-6;
        Gui.drawRect(x-2,y-2,x+width+2,y+height+2,0xE900080C);
        Gui.drawRect(x,y,x+width,y+height,BG);
        Gui.drawRect(x,y,x+width,y+2,BLUE);
        font.drawString("OUTPUT FORMAT",x+10,y+9,BLUE);
        font.drawString("x",x+width-13,y+9,TEXT);
        clicked.clear();
        if(editingCustom)
        {
            drawCustom(mc,settings,mouseX,mouseY);
            return;
        }
        int total=options.size()*ROW_HEIGHT;
        scrollMax=Math.max(0,total-(listBottom-listTop));
        scroll=Math.max(0,Math.min(scrollMax,scroll));
        for(int i=0;i<options.size();i++)
        {
            int yy=listTop+i*ROW_HEIGHT-scroll;
            if(yy<listTop||yy+ROW_HEIGHT>listBottom)continue;
            Option o=options.get(i);
            if(o.heading())
            {
                font.drawString(o.group,listX+3,yy+5,MUTED);
                continue;
            }
            boolean chosen=o.preset!=null&&settings.matches(o.preset);
            boolean over=mouseX>=listX&&mouseX<x+width-7
                    &&mouseY>=yy&&mouseY<yy+ROW_HEIGHT;
            Gui.drawRect(listX,yy,x+width-7,yy+ROW_HEIGHT-1,
                    chosen?SELECTED:over?0xFF303E48:ROW);
            if(chosen)Gui.drawRect(listX,yy,listX+2,yy+ROW_HEIGHT-1,BLUE);
            String label=o.custom?"Custom resolution...":
                    o.preset.label;
            font.drawString(font.trimStringToWidth(label,width-135),
                    listX+8,yy+5,TEXT);
            if(o.preset!=null)
            {
                String size=o.preset.dimensions();
                font.drawString(size,x+width-12-font.getStringWidth(size),
                        yy+5,chosen?BLUE:MUTED);
            }
            else font.drawString(">",x+width-23,yy+5,BLUE);
            clicked.add(new int[]{i,yy,yy+ROW_HEIGHT});
        }
        if(scrollMax>0)
        {
            int track=listBottom-listTop;
            int thumb=Math.max(10,(int)((long)track*track/Math.max(1,total)));
            int pos=scroll*(track-thumb)/Math.max(1,scrollMax);
            Gui.drawRect(x+width-4,listTop+pos,
                    x+width-2,listTop+pos+thumb,BLUE);
        }
    }

    private void drawCustom(Minecraft mc,CameraOutputSettings settings,
            int mouseX,int mouseY)
    {
        FontRenderer font=mc.fontRenderer;
        font.drawString("Custom output size",x+12,y+38,TEXT);
        font.drawString("Width",x+12,y+64,MUTED);
        font.drawString("Height",x+12,y+94,MUTED);
        customX=x+77;customY=y+57;
        int fieldW=Math.max(46,width-91);
        if(widthInput==null||heightInput==null)
        {
            widthInput=new GuiTextField(35,font,customX,customY,fieldW,19);
            heightInput=new GuiTextField(36,font,customX,customY+30,fieldW,19);
            widthInput.setMaxStringLength(5);
            heightInput.setMaxStringLength(5);
            widthInput.setText(Integer.toString(settings.getWidth()));
            heightInput.setText(Integer.toString(settings.getHeight()));
            widthInput.setFocused(true);
        }
        widthInput.x=customX;widthInput.y=customY;
        widthInput.width=fieldW;
        heightInput.x=customX;heightInput.y=customY+30;
        heightInput.width=fieldW;
        widthInput.drawTextBox();heightInput.drawTextBox();
        applyX=x+width-76;applyY=y+128;backX=x+10;
        Gui.drawRect(backX,applyY,backX+58,applyY+20,ROW);
        Gui.drawRect(applyX,applyY,applyX+64,applyY+20,SELECTED);
        font.drawString("Cancel",backX+9,applyY+6,TEXT);
        font.drawString("Apply",applyX+16,applyY+6,BLUE);
        font.drawString("Even dimensions; 128-8192 px",
                x+11,y+160,MUTED);
        if(error!=null)
            font.drawString(font.trimStringToWidth(error,width-22),
                    x+11,y+177,0xFFFF9696);
    }

    public boolean mouseClicked(int mouseX,int mouseY,int button,
            CameraOutputSettings settings,Runnable save)
    {
        if(!opened)return false;
        if(button!=0)return true;
        if(mouseX<x||mouseX>=x+width||mouseY<y||mouseY>=y+height)
        {
            close();return true;
        }
        if(mouseY<y+HEADER_HEIGHT&&mouseX>x+width-26)
        {
            close();return true;
        }
        if(editingCustom)
        {
            if(widthInput!=null)widthInput.mouseClicked(mouseX,mouseY,button);
            if(heightInput!=null)heightInput.mouseClicked(mouseX,mouseY,button);
            if(mouseY>=applyY&&mouseY<applyY+20)
            {
                if(mouseX>=applyX&&mouseX<applyX+64)
                    apply(settings,save);
                else if(mouseX>=backX&&mouseX<backX+58)
                {editingCustom=false;error=null;}
            }
            return true;
        }
        if(mouseX>=listX&&mouseX<x+width-7)
        {
            for(int[] hit:clicked)
            {
                if(mouseY<hit[1]||mouseY>=hit[2])continue;
                Option option=options.get(hit[0]);
                if(option.custom)
                {
                    editingCustom=true;widthInput=null;heightInput=null;
                    error=null;
                }
                else if(option.preset!=null)
                {
                    if(settings.set(option.preset.width,option.preset.height))
                        save.run();
                    close();
                }
                return true;
            }
        }
        return true;
    }

    private void apply(CameraOutputSettings settings,Runnable save)
    {
        try
        {
            int w=Integer.parseInt(widthInput.getText());
            int h=Integer.parseInt(heightInput.getText());
            if(!CameraOutputSettings.isValid(w,h))
            {error="Use even dimensions (128-8192)";return;}
            if(settings.set(w,h))save.run();
            close();
        }
        catch(NumberFormatException invalid){error="Enter numeric dimensions";}
    }

    public boolean keyTyped(char character,int keyCode,
            CameraOutputSettings settings,Runnable save)
            throws IOException
    {
        if(!opened)return false;
        if(keyCode==1)
        {
            if(editingCustom){editingCustom=false;error=null;}
            else close();
            return true;
        }
        if(editingCustom)
        {
            if(keyCode==28||keyCode==156){apply(settings,save);return true;}
            if(keyCode==15)
            {
                boolean first=widthInput!=null&&widthInput.isFocused();
                if(widthInput!=null)widthInput.setFocused(!first);
                if(heightInput!=null)heightInput.setFocused(first);
                return true;
            }
            if(widthInput!=null)widthInput.textboxKeyTyped(character,keyCode);
            if(heightInput!=null)heightInput.textboxKeyTyped(character,keyCode);
        }
        return true;
    }

    public boolean scroll(int delta)
    {
        if(!opened)return false;
        if(!editingCustom)
            scroll=Math.max(0,Math.min(scrollMax,
                    scroll+(delta>0?-ROW_HEIGHT*2:ROW_HEIGHT*2)));
        return true;
    }
}
