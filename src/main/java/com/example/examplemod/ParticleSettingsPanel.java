package com.example.examplemod;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Keyboard;

/**
 * Shared, scrollable native ParticleMorph property editor.
 * Used unchanged for actor keys and static CAMERA particle emitters.
 * The vanilla type picker contains every EnumParticleTypes entry.
 */
public final class ParticleSettingsPanel
{
    private static final String[] PROPERTIES={
            "Frequency","Duration","Delay","Cap","Speed","Count",
            "X","Y","Z","DX","DY","DZ","Args","LocalRotation"};
    private NBTTagCompound settings;
    private Runnable onChange;
    private int x,y,bottom,scroll,selectionScroll;
    private boolean typesOpen;
    private GuiTextField input;
    private String editing;
    private static final int ROW=29;
    private static final int LIST_TOP=71;
    private static final int PICK_ROWS=7;

    public void bind(NBTTagCompound value,Runnable callback)
    {
        if(settings!=value)
        {
            settings=value;
            scroll=0;typesOpen=false;input=null;editing=null;
        }
        onChange=callback;
    }
    public void reset()
    {
        settings=null;onChange=null;scroll=0;
        typesOpen=false;input=null;editing=null;
    }
    private void changed()
    {
        if(onChange!=null)onChange.run();
    }
    private int rows()
    {
        return Math.max(1,(bottom-y-LIST_TOP-8)/ROW);
    }
    private void clampScroll()
    {
        scroll=Math.max(0,Math.min(Math.max(0,
                PROPERTIES.length-rows()),scroll));
    }
    private static String display(String key)
    {
        if("LocalRotation".equals(key))return "Relative rotation";
        if("Args".equals(key))return "Extra arguments";
        return key;
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
    public void draw(Minecraft mc,int px,int py,int lower)
    {
        if(settings==null||mc==null)return;
        x=px;y=py;bottom=lower;
        clampScroll();
        int accent=EditorThemeManager.get().getAccent();
        Gui.drawRect(x+5,y+3,x+179,y+23,
                EditorVisualStyle.themedSurface(0.23F));
        mc.fontRenderer.drawString("PARTICLE MORPH",x+12,y+9,0xFFE5EBF1);
        String mode=settings.getString("Mode");
        boolean vanilla=mode.isEmpty()||
                "vanilla".equalsIgnoreCase(mode);
        mc.fontRenderer.drawString(vanilla?"VANILLA":"MORPH",
                x+9,y+32,vanilla?0xFFA5E8BE:0xFFFFD796);

        String selected=ParticleEmitterConfig.type(settings);
        Gui.drawRect(x+7,y+49,x+178,y+70,
                EditorVisualStyle.themedSurface(0.22F));
        mc.fontRenderer.drawString("<",x+12,y+55,0xFFEAF1F5);
        mc.fontRenderer.drawString(
                mc.fontRenderer.trimStringToWidth(selected,118),
                x+26,y+55,0xFFEAF1F5);
        mc.fontRenderer.drawString(">",x+165,y+55,0xFFEAF1F5);

        int visible=rows();
        for(int row=0;row<visible&&row+scroll<PROPERTIES.length;row++)
        {
            String property=PROPERTIES[row+scroll];
            int line=y+LIST_TOP+row*ROW;
            Gui.drawRect(x+7,line,x+178,line+ROW-2,
                    EditorVisualStyle.themedSurface(0.12F));
            mc.fontRenderer.drawString(display(property),
                    x+11,line+3,0xFFC8D3DC);
            if(input!=null&&property.equals(editing))
            {
                input.x=x+89;input.y=line+13;
                input.width=69;input.height=14;
                input.drawTextBox();
            }
            else
            {
                String value=shown(property);
                mc.fontRenderer.drawString(
                        mc.fontRenderer.trimStringToWidth(value,64),
                        x+93,line+17,0xFFE5F0F2);
            }
            if(!"Args".equals(property)&&!"LocalRotation".equals(property))
            {
                Gui.drawRect(x+77,line+13,x+91,line+27,
                        EditorVisualStyle.themedSurface(0.3F));
                Gui.drawRect(x+159,line+13,x+175,line+27,
                        EditorVisualStyle.themedSurface(0.3F));
                mc.fontRenderer.drawString("<",x+81,line+16,0xFFFFFFFF);
                mc.fontRenderer.drawString(">",x+164,line+16,0xFFFFFFFF);
            }
            else if("LocalRotation".equals(property))
                Gui.drawRect(x+151,line+14,x+176,line+26,
                        ParticleEmitterConfig.rotation(settings)?
                                0xFF34795A:0xFF50535B);
        }
        if(typesOpen)
        {
            List<String> types=ParticleEmitterConfig.types();
            int top=y+71;
            int max=Math.min(PICK_ROWS,types.size());
            Gui.drawRect(x+7,top-2,x+178,top+max*20+2,
                    0xFF1B2027);
            for(int i=0;i<max;i++)
            {
                int index=selectionScroll+i;
                if(index>=types.size())break;
                String name=types.get(index);
                int cy=top+i*20;
                Gui.drawRect(x+8,cy,x+177,cy+19,
                        name.equals(selected)?
                                EditorVisualStyle.themedSurface(.36F):
                                EditorVisualStyle.themedSurface(.18F));
                mc.fontRenderer.drawString(
                        mc.fontRenderer.trimStringToWidth(name,156),
                        x+13,cy+5,name.equals(selected)?
                                accent:0xFFE5E7EA);
            }
            mc.fontRenderer.drawString(
                    (selectionScroll+1)+"-"+Math.min(types.size(),
                            selectionScroll+max)+"/"+types.size(),
                    x+90,top+max*20+3,0xFF9CA6B0);
        }
    }
    private boolean in(int mx,int my,int left,int top,int w,int h)
    {
        return mx>=left&&mx<left+w&&my>=top&&my<top+h;
    }
    private void step(String key,int direction)
    {
        if(settings==null)return;
        boolean integer="Frequency".equals(key)||"Duration".equals(key)||
                "Delay".equals(key)||"Cap".equals(key)||
                "Count".equals(key);
        double increment=integer?1D:0.0625D;
        if(ParticleEmitterConfig.update(settings,key,
                ParticleEmitterConfig.value(settings,key)+
                        direction*increment))
            changed();
    }
    private void cycleType(int delta)
    {
        List<String> types=ParticleEmitterConfig.types();
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
        if(settings==null||mx<x||mx>=x+185||my<y||my>=bottom)
            return false;
        if(button!=0)return true;
        if(typesOpen)
        {
            List<String> types=ParticleEmitterConfig.types();
            int pos=(my-y-71)/20;
            if(in(mx,my,x+7,y+71,171,
                    PICK_ROWS*20)&&pos>=0&&
                    selectionScroll+pos<types.size())
            {
                ParticleEmitterConfig.changeType(settings,
                        types.get(selectionScroll+pos));
                changed();
            }
            typesOpen=false;
            return true;
        }
        if(in(mx,my,x+7,y+49,171,22))
        {
            commit();
            if(mx<x+24)cycleType(-1);
            else if(mx>=x+159)cycleType(1);
            else
            {
                typesOpen=true;
                selectionScroll=Math.max(0,
                        ParticleEmitterConfig.types().indexOf(
                                ParticleEmitterConfig.type(settings))-2);
            }
            return true;
        }
        int index=(my-y-LIST_TOP)/ROW+scroll;
        if(my>=y+LIST_TOP&&index>=0&&index<PROPERTIES.length)
        {
            String prop=PROPERTIES[index];
            int rowY=y+LIST_TOP+(index-scroll)*ROW;
            if(!in(mx,my,x+7,rowY,171,ROW))return true;
            if("LocalRotation".equals(prop))
            {
                commit();
                settings.setBoolean("LocalRotation",
                        !ParticleEmitterConfig.rotation(settings));
                changed();return true;
            }
            if("Args".equals(prop))
            {
                commit();startEdit(prop,rowY);return true;
            }
            if(my>=rowY+13)
            {
                if(mx>=x+77&&mx<x+91){commit();step(prop,-1);}
                else if(mx>=x+159&&mx<x+177)
                {commit();step(prop,1);}
                else {commit();startEdit(prop,rowY);}
            }
            return true;
        }
        return true;
    }
    private void startEdit(String key,int rowY)
    {
        editing=key;
        input=new GuiTextField(785,Minecraft.getMinecraft().fontRenderer,
                x+89,rowY+13,69,14);
        input.setMaxStringLength("Args".equals(key)?96:40);
        input.setText(shown(key));
        input.setFocused(true);
    }
    public boolean keyTyped(char c,int key)
    {
        if(settings==null||input==null)return false;
        if(key==Keyboard.KEY_ESCAPE||key==Keyboard.KEY_RETURN||
                key==Keyboard.KEY_NUMPADENTER)
        {
            if(key==Keyboard.KEY_ESCAPE){input=null;editing=null;}
            else commit();
            return true;
        }
        if((key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN)&&
                !"Args".equals(editing))
        {
            String property=editing;
            commit();
            step(property,key==Keyboard.KEY_UP?1:-1);
            return true;
        }
        input.textboxKeyTyped(c,key);
        return true;
    }
    public boolean scroll(int mx,int my,int direction)
    {
        if(settings==null||direction==0||
                mx<x||mx>=x+185||my<y||my>=bottom)return false;
        if(typesOpen)
        {
            selectionScroll=Math.max(0,Math.min(
                    Math.max(0,ParticleEmitterConfig.types().size()-PICK_ROWS),
                    selectionScroll+(direction>0?-1:1)));
            return true;
        }
        if(in(mx,my,x+7,y+49,171,22))
            cycleType(direction>0?-1:1);
        else
        {
            scroll+=direction>0?-1:1;
            clampScroll();
        }
        return true;
    }
}
