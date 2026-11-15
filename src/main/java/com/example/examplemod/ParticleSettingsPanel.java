package com.example.examplemod;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Keyboard;

/**
 * Shared Blockbuster ParticleMorph inspector for POSE and CAMERA.
 * Uses the CameraClipInspector / LIGHTS visual language:
 * section headers, one compact label/value row, integral arrow buttons.
 * The whole UI stays within a 185px right-hand panel at every scroll offset.
 *
 * Only visual structure and hit testing are changed here; native NBT keys,
 * emission physics, editor timeline and saved values remain untouched.
 */
public final class ParticleSettingsPanel
{
    private static final String[] ENTRIES={
            "#EMISSION","Frequency","Duration","Delay","Cap","Speed",
            "Count","#VANILLA PROPERTIES","X","Y","Z","DX","DY","DZ",
            "#GENERAL","Args","LocalRotation"};
    private static final int PANEL_WIDTH=185;
    private static final int CONTENT_TOP=79;
    private static final int ROW=24;
    private static final int TYPE_TOP=48;
    private static final int TYPE_HEIGHT=22;
    private static final int PICK_TOP=74;
    private static final int PICK_ROW=20;
    private static final int PICK_MAX=7;
    private static final int FIELD_LEFT=89;
    private static final int FIELD_RIGHT=177;

    private NBTTagCompound settings;
    private Runnable onChange;
    private int x,y,bottom,scroll,selectionScroll;
    private boolean typesOpen;
    private GuiTextField input;
    private String editing;

    public void bind(NBTTagCompound value,Runnable callback)
    {
        if(settings!=value)
        {
            settings=value;
            scroll=0;
            selectionScroll=0;
            typesOpen=false;
            input=null;
            editing=null;
        }
        onChange=callback;
    }

    public void reset()
    {
        settings=null;
        onChange=null;
        scroll=0;
        selectionScroll=0;
        typesOpen=false;
        input=null;
        editing=null;
    }

    private void changed()
    {
        if(onChange!=null)onChange.run();
    }

    private int visibleRows()
    {
        return Math.max(0,(bottom-y-CONTENT_TOP-5)/ROW);
    }

    private void clampScroll()
    {
        scroll=Math.max(0,Math.min(scroll,
                Math.max(0,ENTRIES.length-visibleRows())));
    }

    private int pickerRows()
    {
        return Math.max(0,Math.min(PICK_MAX,
                Math.min(ParticleEmitterConfig.types().size(),
                        (bottom-y-PICK_TOP-4)/PICK_ROW)));
    }

    private void clampPicker()
    {
        selectionScroll=Math.max(0,Math.min(selectionScroll,
                Math.max(0,ParticleEmitterConfig.types().size()-
                        pickerRows())));
    }

    private static String display(String key)
    {
        // Short inspector labels; full native parameter names remain
        // unchanged in ParticleEmitterConfig and editor.dat.
        if("LocalRotation".equals(key))return "Rel. rotation";
        if("Args".equals(key))return "Args";
        return key;
    }

    private static String sectionName(String key)
    {
        return key.substring(1);
    }

    private String shown(String key)
    {
        if("LocalRotation".equals(key))
            return ParticleEmitterConfig.rotation(settings)?"ON":"OFF";
        if("Args".equals(key))return ParticleEmitterConfig.args(settings);
        double n=ParticleEmitterConfig.value(settings,key);
        if("Frequency".equals(key)||"Duration".equals(key)||
                "Delay".equals(key)||"Cap".equals(key)||
                "Count".equals(key))
            return Integer.toString((int)Math.round(n));
        return String.format(Locale.ROOT,"%.4f",n)
                .replaceAll("0+$","").replaceAll("\\.$","");
    }

    private static boolean within(int mx,int my,int left,int top,
            int right,int bottom)
    {
        return mx>=left&&mx<right&&my>=top&&my<bottom;
    }

    public void draw(Minecraft mc,int px,int py,int lower)
    {
        if(settings==null||mc==null)return;
        x=px;y=py;bottom=lower;
        clampScroll();
        final int accent=EditorThemeManager.get().getAccent();
        final int bright=EditorThemeManager.get().getAccentBright();

        // Same thin, accent-led section heading as camera clip properties.
        Gui.drawRect(x+7,y+3,x+178,y+22,
                EditorVisualStyle.themedSurface(.18F));
        Gui.drawRect(x+7,y+3,x+10,y+22,accent);
        String title=mc.fontRenderer.trimStringToWidth("PARTICLE MORPH",153);
        mc.fontRenderer.drawString(title,x+15,y+9,0xFFE4E5EB);

        boolean vanilla=settings.getString("Mode").isEmpty()||
                "vanilla".equalsIgnoreCase(settings.getString("Mode"));
        mc.fontRenderer.drawString(vanilla?"VANILLA":"MORPH",
                x+11,y+31,vanilla?0xFF9FE7C0:0xFFF2CB87);

        String type=ParticleEmitterConfig.type(settings);
        Gui.drawRect(x+7,y+TYPE_TOP,x+178,y+TYPE_TOP+TYPE_HEIGHT,
                EditorVisualStyle.themedSurface(.08F));
        Gui.drawRect(x+7,y+TYPE_TOP,x+22,y+TYPE_TOP+TYPE_HEIGHT,
                EditorVisualStyle.themedSurface(.28F));
        Gui.drawRect(x+163,y+TYPE_TOP,x+178,y+TYPE_TOP+TYPE_HEIGHT,
                EditorVisualStyle.themedSurface(.28F));
        mc.fontRenderer.drawString("<",x+11,y+TYPE_TOP+7,0xFFE9E5F0);
        mc.fontRenderer.drawString(">",x+168,y+TYPE_TOP+7,0xFFE9E5F0);
        String visibleType=mc.fontRenderer.trimStringToWidth(type,132);
        mc.fontRenderer.drawString(visibleType,
                x+26,y+TYPE_TOP+7,0xFFE7EDF2);

        int count=visibleRows();
        for(int row=0;row<count&&row+scroll<ENTRIES.length;row++)
        {
            String key=ENTRIES[row+scroll];
            int ry=y+CONTENT_TOP+row*ROW;
            if(ry+ROW>bottom)break;
            if(key.startsWith("#"))
            {
                Gui.drawRect(x+7,ry+2,x+178,ry+21,
                        EditorVisualStyle.themedSurface(.18F));
                Gui.drawRect(x+7,ry+2,x+10,ry+21,accent);
                mc.fontRenderer.drawString(
                        mc.fontRenderer.trimStringToWidth(
                                sectionName(key),155),
                        x+15,ry+7,0xFFE0E3E8);
                continue;
            }

            Gui.drawRect(x+7,ry+1,x+178,ry+ROW-1,
                    EditorVisualStyle.themedSurface(.045F));
            Gui.drawRect(x+FIELD_LEFT,ry+3,x+FIELD_RIGHT,ry+ROW-3,
                    EditorVisualStyle.themedSurface(.07F));
            Gui.drawRect(x+FIELD_LEFT,ry+ROW-4,x+FIELD_RIGHT,ry+ROW-3,
                    EditorVisualStyle.themedSurface(.26F));

            String label=mc.fontRenderer.trimStringToWidth(
                    display(key),FIELD_LEFT-17);
            mc.fontRenderer.drawString(label,x+11,ry+8,0xFFC9CED7);

            if("LocalRotation".equals(key))
            {
                boolean enabled=ParticleEmitterConfig.rotation(settings);
                mc.fontRenderer.drawString(enabled?"ON":"OFF",
                        x+105,ry+8,0xFFE6EDF0);
                Gui.drawRect(x+161,ry+6,x+172,ry+18,
                        enabled?accent:0xFF57525E);
                continue;
            }

            if(!"Args".equals(key))
            {
                Gui.drawRect(x+FIELD_LEFT,ry+3,x+103,ry+ROW-3,
                        EditorVisualStyle.themedSurface(.29F));
                Gui.drawRect(x+163,ry+3,x+FIELD_RIGHT,ry+ROW-3,
                        EditorVisualStyle.themedSurface(.29F));
                mc.fontRenderer.drawString("<",x+93,ry+8,0xFFE9EAF1);
                mc.fontRenderer.drawString(">",x+168,ry+8,0xFFE9EAF1);
            }

            if(input!=null&&key.equals(editing))
            {
                input.x=x+104;input.y=ry+5;
                input.width=57;input.height=15;
                input.drawTextBox();
            }
            else
            {
                String value=mc.fontRenderer.trimStringToWidth(
                        shown(key),"Args".equals(key)?76:55);
                mc.fontRenderer.drawString(value,
                        "Args".equals(key)?x+95:x+105,
                        ry+8,0xFFE6EBF0);
            }
        }

        // The same narrow scrollbar as the scene search list makes
        // additional settings discoverable even on low-height windows.
        if(count>0&&ENTRIES.length>count)
        {
            int track=y+CONTENT_TOP,trackHeight=count*ROW;
            int handle=Math.max(12,trackHeight*count/ENTRIES.length);
            int knob=track+(trackHeight-handle)*scroll/
                    Math.max(1,ENTRIES.length-count);
            Gui.drawRect(x+180,track,x+183,track+trackHeight,
                    0xFF25212B);
            Gui.drawRect(x+180,knob,x+183,knob+handle,accent);
        }

        // Popup overlays rows just like the scene dropdown. Bounds are
        // clipped by the inspector bottom and the full enum catalog scrolls.
        if(typesOpen)
        {
            List<String> choices=ParticleEmitterConfig.types();
            int rows=pickerRows();
            if(rows<1)return;
            clampPicker();
            int pickerY=y+PICK_TOP;
            Gui.drawRect(x+5,pickerY-2,x+180,pickerY+rows*PICK_ROW+2,
                    0xFF4C4454);
            Gui.drawRect(x+6,pickerY-1,x+179,pickerY+rows*PICK_ROW+1,
                    0xFF111115);
            for(int i=0;i<rows;i++)
            {
                int index=selectionScroll+i;
                if(index>=choices.size())break;
                String candidate=choices.get(index);
                boolean selected=candidate.equals(type);
                int iy=pickerY+i*PICK_ROW;
                Gui.drawRect(x+7,iy,x+178,iy+PICK_ROW-1,
                        selected?EditorVisualStyle.themedSurface(.33F):
                                0xFF1A1A1D);
                if(selected)
                    Gui.drawRect(x+7,iy,x+10,iy+PICK_ROW-1,accent);
                mc.fontRenderer.drawString(
                        mc.fontRenderer.trimStringToWidth(candidate,151),
                        x+15,iy+5,selected?bright:0xFFDFDAE4);
            }
            if(choices.size()>rows)
            {
                int height=rows*PICK_ROW;
                int handle=Math.max(9,height*rows/choices.size());
                int knob=pickerY+(height-handle)*selectionScroll/
                        Math.max(1,choices.size()-rows);
                Gui.drawRect(x+174,pickerY,x+177,pickerY+height,
                        0xFF26222E);
                Gui.drawRect(x+174,knob,x+177,knob+handle,accent);
            }
        }
    }

    private void step(String key,int direction)
    {
        if(settings==null)return;
        boolean integral="Frequency".equals(key)||"Duration".equals(key)||
                "Delay".equals(key)||"Cap".equals(key)||
                "Count".equals(key);
        double increment=integral?1D:.0625D;
        if(ParticleEmitterConfig.update(settings,key,
                ParticleEmitterConfig.value(settings,key)+
                        direction*increment))
            changed();
    }

    private void cycleType(int delta)
    {
        List<String> types=ParticleEmitterConfig.types();
        if(types.isEmpty())return;
        int index=types.indexOf(ParticleEmitterConfig.type(settings));
        if(index<0)index=0;
        if(ParticleEmitterConfig.changeType(settings,
                types.get((index+delta+types.size())%types.size())))
            changed();
    }

    private void commit()
    {
        if(input==null||editing==null)return;
        if("Args".equals(editing))
        {
            ParticleEmitterConfig.setArgs(settings,input.getText());
            changed();
        }
        else
        {
            try
            {
                if(ParticleEmitterConfig.update(settings,editing,
                        Double.parseDouble(input.getText().trim())))
                    changed();
            }
            catch(NumberFormatException ignored){}
        }
        input=null;editing=null;
    }

    public boolean click(int mx,int my,int button)
    {
        if(settings==null||!within(mx,my,x,y,x+PANEL_WIDTH,bottom))
            return false;
        if(button!=0)return true;

        if(typesOpen)
        {
            int rows=pickerRows();
            int py=y+PICK_TOP;
            if(within(mx,my,x+7,py,x+178,py+rows*PICK_ROW))
            {
                int choice=selectionScroll+(my-py)/PICK_ROW;
                List<String> choices=ParticleEmitterConfig.types();
                if(choice>=0&&choice<choices.size()&&
                        ParticleEmitterConfig.changeType(settings,
                                choices.get(choice)))
                    changed();
            }
            typesOpen=false;
            return true;
        }

        if(within(mx,my,x+7,y+TYPE_TOP,x+178,y+TYPE_TOP+TYPE_HEIGHT))
        {
            commit();
            if(mx<x+22)cycleType(-1);
            else if(mx>=x+163)cycleType(1);
            else
            {
                typesOpen=true;
                int index=ParticleEmitterConfig.types().indexOf(
                        ParticleEmitterConfig.type(settings));
                selectionScroll=Math.max(0,index-2);
                clampPicker();
            }
            return true;
        }

        int rows=visibleRows(),py=y+CONTENT_TOP;
        if(my<py||my>=py+rows*ROW)return true;
        int index=scroll+(my-py)/ROW;
        if(index<0||index>=ENTRIES.length)return true;
        String key=ENTRIES[index];
        if(key.startsWith("#"))return true;
        int ry=py+(index-scroll)*ROW;
        if(!within(mx,my,x+7,ry,x+178,ry+ROW))return true;

        if("LocalRotation".equals(key))
        {
            commit();
            settings.setBoolean("LocalRotation",
                    !ParticleEmitterConfig.rotation(settings));
            changed();
            return true;
        }
        if("Args".equals(key))
        {
            commit();startEdit(key,ry);return true;
        }

        if(mx>=x+FIELD_LEFT&&mx<x+103)
        {
            commit();step(key,-1);
        }
        else if(mx>=x+163&&mx<x+FIELD_RIGHT)
        {
            commit();step(key,1);
        }
        else if(mx>=x+103&&mx<x+163)
        {
            commit();startEdit(key,ry);
        }
        return true;
    }

    private void startEdit(String key,int ry)
    {
        editing=key;
        input=new GuiTextField(785,Minecraft.getMinecraft().fontRenderer,
                x+104,ry+5,57,15);
        input.setMaxStringLength("Args".equals(key)?96:40);
        input.setText(shown(key));
        input.setFocused(true);
    }

    public boolean keyTyped(char character,int key)
    {
        if(settings==null)return false;
        if(typesOpen)
        {
            if(key==Keyboard.KEY_ESCAPE||key==Keyboard.KEY_RETURN||
                    key==Keyboard.KEY_NUMPADENTER)
            {
                typesOpen=false;
                return true;
            }
            if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN)
            {
                selectionScroll+=key==Keyboard.KEY_UP?-1:1;
                clampPicker();
                return true;
            }
            return false;
        }
        if(input==null)return false;
        if(key==Keyboard.KEY_ESCAPE)
        {
            input=null;editing=null;return true;
        }
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER)
        {
            commit();return true;
        }
        if((key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN)&&
                !"Args".equals(editing))
        {
            String keyName=editing;
            commit();
            step(keyName,key==Keyboard.KEY_UP?1:-1);
            return true;
        }
        input.textboxKeyTyped(character,key);
        return true;
    }

    public boolean scroll(int mx,int my,int direction)
    {
        if(settings==null||direction==0||
                !within(mx,my,x,y,x+PANEL_WIDTH,bottom))
            return false;
        if(typesOpen)
        {
            selectionScroll+=direction>0?-1:1;
            clampPicker();
            return true;
        }
        if(within(mx,my,x+7,y+TYPE_TOP,x+178,y+TYPE_TOP+TYPE_HEIGHT))
        {
            commit();
            cycleType(direction>0?-1:1);
        }
        else
        {
            commit();
            scroll+=direction>0?-1:1;
            clampScroll();
        }
        return true;
    }
}
