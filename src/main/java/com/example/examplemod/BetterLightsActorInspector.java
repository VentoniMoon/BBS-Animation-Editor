package com.example.examplemod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import mchorse.mclib.config.values.GenericBaseValue;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.ValueSerializer;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Keyboard;

/**
 * Actor light inspector displayed in POSE's Transform area.
 *
 * BetterLightsMorph has no skeletal animation bones. Its real, editable
 * properties live in Blockbuster 2.7.1's ValueSerializer, and are serialized
 * into the actor's scene NBT. This panel edits those exact values rather
 * than inventing a parallel editor-only brightness store.
 *
 * The actor's world location and rotation remain Record-owned; never
 * write a fake pose bone or a synthetic player transform for a LightCaster.
 */
public final class BetterLightsActorInspector
{
    private static final int BG=0xFF25292F;
    private static final int FIELD=0xFF151D25;
    private static final int LABEL=0xFFA9C0CC;
    private static final int TEXT=0xFFE4EEF2;
    private static final int ACCENT=0xFF54C8EE;
    private static final int ROW_HEIGHT=37;

    private static final class Entry
    {
        final GenericBaseValue<?> value;
        final String title;
        Entry(GenericBaseValue<?> value)
        {
            this.value=value;
            this.title=value.getPath();
        }
    }

    private BlockbusterSceneActorData actor;
    private AbstractMorph morph;
    private String signature;
    private String error;
    private final List<Entry> entries=new ArrayList<Entry>();
    private int x,top,bottom,scroll;
    private GuiTextField input;
    private Entry editing;
    private int inputY;
    private int previousFrame=-1;

    public static boolean isLightMorph(NBTTagCompound nbt)
    {
        return nbt!=null && "betterLights".equals(nbt.getString("Name"));
    }

    public static boolean isLightActor(BlockbusterSceneActorData actor)
    {
        return actor!=null && actor.getActor()!=null &&
                isLightMorph(actor.getActor().getMorph());
    }

    private void load(BlockbusterSceneActorData current)
    {
        NBTTagCompound tag=current!=null && current.getActor()!=null
                ?current.getActor().getMorph():null;
        String sig=tag==null?"":tag.toString();
        if(actor==current&&sig.equals(signature))return;

        // An actor change is committed by mouseClicked before selection.
        actor=current;
        signature=sig;
        morph=null;
        entries.clear();
        editing=null;
        input=null;
        scroll=0;
        error=null;
        if(!isLightActor(current))return;

        try
        {
            morph=MorphManager.INSTANCE.morphFromNBT(tag.copy());
            if(morph==null)
                throw new IllegalStateException("Missing BetterLightsMorph");
            java.lang.reflect.Method accessor=morph.getClass().getMethod(
                    "getValueManager");
            Object manager=accessor.invoke(morph);
            if(!(manager instanceof ValueSerializer))
                throw new IllegalStateException("Light values are unavailable");
            for(GenericBaseValue<?> value:
                    ((ValueSerializer)manager).getValues())
            {
                if(value==null)continue;
                Object field=value.getValue();
                if(field instanceof Number||field instanceof Boolean||
                        field instanceof Color)
                    entries.add(new Entry(value));
            }
        }
        catch(Throwable problem)
        {
            error=problem.getClass().getSimpleName()+
                    (problem.getMessage()==null?"":": "+problem.getMessage());
            morph=null;
        }
    }

    public void draw(Minecraft mc,int left,int top,int bottom,
            BlockbusterSceneActorData current,int frame)
    {
        if(mc==null)return;
        load(current);
        x=left;
        this.top=top;
        this.bottom=bottom;
        Gui.drawRect(left+4,top+9,left+181,bottom-4,BG);
        Gui.drawRect(left+4,top+9,left+181,top+33,0xFF303C45);
        Gui.drawRect(left+4,top+9,left+7,top+33,ACCENT);
        FontRenderer font=mc.fontRenderer;
        font.drawString("LIGHT / TRANSFORM",left+13,top+16,TEXT);
        font.drawString("Position / rotation: Record",left+12,top+43,LABEL);
        BlockbusterRecordFrame currentFrame=current==null||
                current.getRecord()==null?null:current.getRecord().getFrame(frame);
        if(currentFrame!=null)
        {
            String pos=String.format(Locale.ROOT,"XYZ %.1f / %.1f / %.1f",
                    currentFrame.getX(),currentFrame.getY(),currentFrame.getZ());
            font.drawString(font.trimStringToWidth(pos,161),
                    left+12,top+57,TEXT);
        }
        if(error!=null)
        {
            font.drawString(font.trimStringToWidth(error,159),
                    left+12,top+80,0xFFFF9797);
            return;
        }
        if(morph==null)
        {
            font.drawString("BetterLights unavailable",left+12,
                    top+82,0xFFFF9797);
            return;
        }
        Gui.drawRect(left+10,top+83,left+175,top+84,0xFF4A5660);
        int start=top+90;
        int available=Math.max(0,bottom-start-8);
        int visible=Math.max(1,available/ROW_HEIGHT);
        scroll=Math.max(0,Math.min(scroll,Math.max(0,entries.size()-visible)));
        for(int slot=0;slot<visible&&slot+scroll<entries.size();slot++)
        {
            int cy=start+slot*ROW_HEIGHT;
            if(cy+ROW_HEIGHT>bottom-5)break;
            Entry entry=entries.get(scroll+slot);
            String path=entry.title;
            font.drawString(font.trimStringToWidth(path,158),
                    left+13,cy+1,LABEL);
            Gui.drawRect(left+12,cy+12,left+172,cy+32,FIELD);
            if(entry==editing&&input!=null)
            {
                // GuiTextField dimensions are constructor-owned in 1.12.2.
                // The field is placed in this same row when clicked.
                input.drawTextBox();
            }
            else
            {
                font.drawString(font.trimStringToWidth(text(entry.value),146),
                        left+17,cy+18,TEXT);
            }
        }
        if(entries.size()>visible)
        {
            String paging=""+(scroll+1)+"-"+Math.min(entries.size(),
                    scroll+visible)+"/"+entries.size();
            font.drawString(paging,left+126,top+73,ACCENT);
        }
        previousFrame=frame;
    }

    private static String text(GenericBaseValue<?> value)
    {
        Object current=value.getValue();
        if(current instanceof Color)
            return String.format(Locale.ROOT,"#%08X",
                    ((Color)current).getRGBAColor());
        return current==null?"":String.valueOf(current);
    }

    private boolean commit(AnimationEditorScreen screen)
    {
        if(editing==null||input==null)return false;
        Entry old=editing;
        String raw=input.getText().trim();
        editing=null;
        input=null;
        try
        {
            Object was=old.value.getValue();
            Object next;
            if(was instanceof Boolean)
            {
                if(!"true".equalsIgnoreCase(raw)&&
                        !"false".equalsIgnoreCase(raw))
                    throw new IllegalArgumentException("Enter true or false");
                next=Boolean.parseBoolean(raw);
            }
            else if(was instanceof Color)
            {
                String hex=raw.replace("#","").replace("0x","").trim();
                if(hex.length()!=6&&hex.length()!=8)
                    throw new IllegalArgumentException("Enter #RRGGBB or #AARRGGBB");
                long parsed=Long.parseUnsignedLong(hex,16);
                if(hex.length()==6)parsed|=0xFF000000L;
                next=new Color((int)parsed);
            }
            else if(was instanceof Integer)
                next=Integer.valueOf(raw);
            else if(was instanceof Float)
                next=Float.valueOf(raw);
            else if(was instanceof Double)
                next=Double.valueOf(raw);
            else return true;
            if(next instanceof Number&&
                    !Double.isFinite(((Number)next).doubleValue()))
                throw new IllegalArgumentException("Not a finite value");
            old.value.setValue(next);
            // Save into the original scene actor's morph tag. The standard
            // editor Ctrl+S writes that scene, and the actor renderer picks
            // up the modified morph NBT from CharacterMorphResolver.
            NBTTagCompound source=actor.getActor().getMorph();
            NBTTagCompound updated=source.copy();
            morph.toNBT(updated);
            actor.getActor().setMorph(updated);
            signature=updated.toString();
            if(screen!=null)screen.markEditorDirty();
            error=null;
            return true;
        }
        catch(RuntimeException problem)
        {
            error="Light value: "+problem.getMessage();
            return true;
        }
    }

    public boolean mouseClicked(AnimationEditorScreen screen,
            int mx,int my,int button)
    {
        if(editing!=null)commit(screen);
        if(!isLightActor(screen.getSelectedActorForTimeline())||
                mx<x+8||mx>=x+176||my<top+88||my>=bottom)
            return false;
        if(button!=0)return true;
        int idx=scroll+(my-(top+90))/ROW_HEIGHT;
        int cy=top+90+(idx-scroll)*ROW_HEIGHT;
        if(idx<0||idx>=entries.size()||my<cy+12||my>=cy+32)
            return true;
        editing=entries.get(idx);
        input=new GuiTextField(17431,Minecraft.getMinecraft().fontRenderer,
                x+17,cy+13,149,17);
        input.setMaxStringLength(80);
        input.setText(text(editing.value));
        input.setFocused(true);
        return true;
    }

    public boolean keyTyped(AnimationEditorScreen screen,
            char c,int code)
    {
        if(editing==null||input==null)return false;
        if(code==Keyboard.KEY_RETURN||code==Keyboard.KEY_NUMPADENTER||
                code==Keyboard.KEY_TAB)
            return commit(screen);
        if(code==Keyboard.KEY_ESCAPE)
        {
            editing=null;
            input=null;
            error=null;
            return true;
        }
        input.textboxKeyTyped(c,code);
        return true;
    }

    public boolean scroll(int mx,int my,int wheel)
    {
        if(mx<x||mx>=x+185||my<top||my>=bottom||wheel==0)
            return false;
        int visible=Math.max(1,Math.max(0,bottom-(top+90)-8)/ROW_HEIGHT);
        scroll=Math.max(0,Math.min(Math.max(0,entries.size()-visible),
                scroll+(wheel>0?-1:1)));
        return true;
    }
}
