package com.example.examplemod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import mchorse.mclib.config.values.GenericBaseValue;
import mchorse.mclib.config.values.GenericNumberValue;
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
    private static final int ROW_HEIGHT=45;

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
    private AnimationKeyframe originKey;

    /**
     * Select an Origin key to edit its native Light/Shadows/Flare/Fog values.
     * No selected Origin key means editing the scene actor's base properties.
     */
    public void setOriginKey(AnimationKeyframe key)
    {
        if(originKey!=key)
        {
            originKey=key;
            signature=null; // identical values on different keys must reload
        }
    }
    private AbstractMorph morph;
    private String signature;
    private String error;
    private final List<Entry> entries=new ArrayList<Entry>();
    private int x,top,bottom,scroll;
    private GuiTextField input;
    private Entry editing;
    private int inputY;
    private int previousFrame=-1;
    /**
     * CAMERA's narrow inspector shares the CameraClipInspector palette.
     * This layout is independent of the larger POSE actor layout.
     */
    private boolean cameraLayout;
    private static final int CAMERA_ROW_HEIGHT=45;
    private static final int CAMERA_VALUES_OFFSET=70;
    private int section=BetterLightsPanelModel.LIGHT;
    private Entry sliding;
    private Entry picking;
    private float hue,saturation=1F,brightness=1F;
    private int pickerTop;
    private int dragColorPart; // 0 none; 1 SV; 2 hue; 3 alpha


    /** Light-actor POSE displays both real light properties and keyable rig. */
    private boolean rigTab;
    public boolean rigTab(){return rigTab;}

    public boolean selectTab(int mx,int my,int panelX,int panelBottom)
    {
        int y=panelBottom-32;
        if(mx<panelX+7||mx>=panelX+180||my<y||my>=y+25)
            return false;
        rigTab=mx<panelX+95;
        return true;
    }

    public void drawTabs(Minecraft mc,int panelX,int panelBottom)
    {
        int y=panelBottom-32;
        Gui.drawRect(panelX+7,y,panelX+95,y+25,
                rigTab?0xFF3A7898:0xFF283841);
        Gui.drawRect(panelX+96,y,panelX+180,y+25,
                !rigTab?0xFF3A7898:0xFF283841);
        mc.fontRenderer.drawString("RIG KEYS",panelX+16,y+8,
                0xFFFFFFFF);
        mc.fontRenderer.drawString("LIGHT",panelX+118,y+8,
                0xFFFFFFFF);
    }

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
        if(originKey!=null&&originKey.getLightSettings()!=null)
            tag=originKey.getLightSettings();
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
        sliding=null;
        picking=null;
        dragColorPart=0;
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

    private List<Entry> visibleEntries()
    {
        List<Entry> filtered=new ArrayList<Entry>();
        for(Entry entry:entries)
            if(BetterLightsPanelModel.section(entry.title)==section)
                filtered.add(entry);
        return filtered;
    }

    private int tabsY(){return cameraLayout?top+23:top+85;}
    private int rowsY(){return cameraLayout?top+CAMERA_VALUES_OFFSET:top+139;}
    private int rowHeight(){return cameraLayout?CAMERA_ROW_HEIGHT:ROW_HEIGHT;}
    private int visibleCount()
    {
        return Math.max(0,(bottom-rowsY()-8)/rowHeight());
    }

    /** The four native nested Value groups, in the same CAMERA theme. */
    private void drawSections(Minecraft mc)
    {
        int accent=EditorThemeManager.get().getAccent();
        int y=tabsY();
        for(int i=0;i<4;i++)
        {
            int sx=x+9+(i%2)*85, sy=y+(i/2)*21;
            Gui.drawRect(sx,sy,sx+80,sy+19,
                    section==i?0xFF333A40:0xFF252A30);
            Gui.drawRect(sx,sy+17,sx+80,sy+19,
                    section==i?accent:0xFF3B464D);
            String label=BetterLightsPanelModel.TITLES[i];
            int tw=mc.fontRenderer.getStringWidth(label);
            mc.fontRenderer.drawString(label,
                    sx+(80-tw)/2,sy+5,
                    section==i?0xFFF4FBFD:0xFFA2ADB4);
        }
    }

    private void drawEntry(Minecraft mc,Entry entry,int cy)
    {
        FontRenderer f=mc.fontRenderer;
        int accent=EditorThemeManager.get().getAccent();
        Object value=entry.value.getValue();
        Gui.drawRect(x+9,cy,x+176,cy+42,0xFF20262B);
        String label=BetterLightsPanelModel.label(entry.title);
        f.drawString(f.trimStringToWidth(label,154),
                x+13,cy+3,0xFFABBBC4);
        if(input!=null&&editing==entry)
        {
            input.drawTextBox();
            return;
        }
        if(value instanceof Boolean)
        {
            boolean enabled=(Boolean)value;
            f.drawString(enabled?"ON":"OFF",x+15,cy+24,
                    enabled?0xFFE7F5F7:0xFF9BA5AD);
            Gui.drawRect(x+140,cy+19,x+171,cy+33,
                    enabled?accent:0xFF596167);
            Gui.drawRect(x+(enabled?156:141),cy+20,
                    x+(enabled?169:154),cy+32,0xFFF1F5F7);
        }
        else if(value instanceof Color)
        {
            Color c=(Color)value;
            f.drawString(String.format(Locale.ROOT,"#%08X",
                    c.getRGBAColor()),x+14,cy+23,0xFFE5F2F5);
            Gui.drawRect(x+142,cy+18,x+171,cy+35,0xFF111111);
            Gui.drawRect(x+144,cy+20,x+169,cy+33,
                    c.getRGBAColor()|0xFF000000);
        }
        else if(value instanceof Number)
        {
            String number=String.format(Locale.ROOT,
                    entry.value.getValue() instanceof Integer?"%.0f":"%.3f",
                    ((Number)value).doubleValue());
            int tw=f.getStringWidth(number);
            f.drawString(number,x+172-tw,cy+3,0xFFE4EFF4);
            if(entry.value instanceof GenericNumberValue)
            {
                GenericNumberValue<?> n=
                        (GenericNumberValue<?>)entry.value;
                double min=n.getMin().doubleValue();
                double max=n.getMax().doubleValue();
                boolean bounded=Double.isFinite(min)&&
                        Double.isFinite(max)&&max>min;
                if(bounded)
                {
                    Gui.drawRect(x+15,cy+28,x+169,cy+33,0xFF101519);
                    int thumb=x+15+(int)Math.round(
                            146*BetterLightsPanelModel.rangeFraction(
                            ((Number)value).doubleValue(),min,max));
                    Gui.drawRect(x+15,cy+29,thumb+3,cy+32,accent);
                    Gui.drawRect(thumb,cy+24,thumb+8,cy+37,0xFFE3F1F5);
                }
                else f.drawString("Click value to edit",x+15,cy+29,
                        0xFF7D8E98);
            }
        }
    }

    private void drawRows(Minecraft mc)
    {
        List<Entry> shown=visibleEntries();
        int visible=visibleCount();
        scroll=Math.max(0,Math.min(scroll,
                Math.max(0,shown.size()-visible)));
        for(int i=0;i<visible&&i+scroll<shown.size();i++)
        {
            int cy=rowsY()+i*rowHeight();
            drawEntry(mc,shown.get(i+scroll),cy);
        }
        if(shown.isEmpty())
            mc.fontRenderer.drawString("No settings in this section",
                    x+12,rowsY()+10,0xFF8C9BA5);
        if(shown.size()>visible)
        {
            String paging=(scroll+1)+"-"+
                    Math.min(shown.size(),scroll+visible)+
                    "/"+shown.size();
            mc.fontRenderer.drawString(paging,
                    x+173-mc.fontRenderer.getStringWidth(paging),
                    cameraLayout?top+6:top+74,
                    EditorThemeManager.get().getAccentBright());
        }
        if(picking!=null)drawColorPicker(mc);
    }

    private void drawColorPicker(Minecraft mc)
    {
        if(picking==null)return;
        // Compact native-style hue + saturation/brightness + alpha palette.
        int px=x+8,py=pickerTop;
        Gui.drawRect(px,py,px+169,py+146,0xFF3C464D);
        Gui.drawRect(px+2,py+2,px+167,py+144,0xFF171C21);
        mc.fontRenderer.drawString("COLOR  [x]",px+8,py+6,0xFFE3F0F4);
        int rgb=java.awt.Color.HSBtoRGB(hue,1F,1F)&0xFFFFFF;
        for(int row=0;row<76;row+=4)
            for(int col=0;col<120;col+=4)
            {
                float sat=Math.min(1F,col/119F);
                float val=Math.max(0F,1F-row/75F);
                int shade=java.awt.Color.HSBtoRGB(hue,sat,val);
                Gui.drawRect(px+9+col,py+22+row,
                        px+13+col,py+26+row,shade|0xFF000000);
            }
        for(int row=0;row<76;row+=2)
            Gui.drawRect(px+136,py+22+row,px+150,py+24+row,
                    java.awt.Color.HSBtoRGB(row/75F,1F,1F));
        int cx=px+9+(int)(saturation*119F);
        int cy=py+22+(int)((1F-brightness)*75F);
        Gui.drawRect(cx-2,cy-2,cx+3,cy+3,0xFFFFFFFF);
        int hy=py+22+(int)(hue*75F);
        Gui.drawRect(px+134,hy-1,px+152,hy+2,0xFFFFFFFF);
        Color color=(Color)picking.value.getValue();
        int alpha=(color.getRGBAColor()>>>24)&255;
        Gui.drawRect(px+10,py+111,px+155,py+117,0xFF57636C);
        Gui.drawRect(px+10,py+111,px+10+(int)(145*alpha/255F),
                py+117,EditorThemeManager.get().getAccent());
        mc.fontRenderer.drawString("Alpha "+alpha,px+10,py+125,
                0xFFE0EBEE);
        mc.fontRenderer.drawString(
                String.format(Locale.ROOT,"#%08X",color.getRGBAColor()),
                px+78,py+125,0xFFE0EBEE);
    }

    public void draw(Minecraft mc,int left,int top,int bottom,
            BlockbusterSceneActorData current,int frame)
    {
        if(mc==null)return;
        cameraLayout=false;
        load(current);
        x=left;this.top=top;this.bottom=bottom;
        Gui.drawRect(left+4,top+9,left+181,bottom-4,0xFF25292F);
        Gui.drawRect(left+4,top+9,left+181,top+33,0xFF303C45);
        Gui.drawRect(left+4,top+9,left+7,top+33,
                EditorThemeManager.get().getAccent());
        FontRenderer f=mc.fontRenderer;
        f.drawString(originKey==null?"LIGHT / TRANSFORM":
                "LIGHT / KEY "+originKey.getFrame(),left+13,top+16,
                0xFFE4EEF2);
        f.drawString("Position / rotation: Record",left+12,top+43,
                0xFFA9C0CC);
        BlockbusterRecordFrame at=current==null||current.getRecord()==null
                ?null:current.getRecord().getFrame(frame);
        if(at!=null)
            f.drawString(f.trimStringToWidth(String.format(Locale.ROOT,
                    "XYZ %.1f / %.1f / %.1f",
                    at.getX(),at.getY(),at.getZ()),159),
                    left+12,top+57,0xFFE4EEF2);
        if(error!=null){f.drawString(f.trimStringToWidth(error,163),
                left+11,top+77,0xFFFF9999);return;}
        if(morph==null){f.drawString("BetterLights unavailable",
                left+11,top+79,0xFFFF9999);return;}
        drawSections(mc);
        drawRows(mc);
        previousFrame=frame;
    }

    public void drawCamera(Minecraft mc,int left,int top,int bottom,
            BlockbusterSceneActorData current)
    {
        if(mc==null)return;
        load(current);
        cameraLayout=true;
        x=left;this.top=top;this.bottom=bottom;
        int accent=EditorThemeManager.get().getAccent();
        FontRenderer f=mc.fontRenderer;
        Gui.drawRect(left+7,top+2,left+178,top+18,0xFF2B2D30);
        Gui.drawRect(left+7,top+2,left+10,top+18,accent);
        f.drawString("BETTERLIGHTS",left+15,top+6,0xFFE2E5E6);
        if(error!=null){f.drawString(f.trimStringToWidth(error,164),
                left+10,top+26,0xFFFF9999);return;}
        if(morph==null){f.drawString("BetterLights unavailable",
                left+10,top+26,0xFFFF9999);return;}
        drawSections(mc);
        drawRows(mc);
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
            saveNativeValue(screen);
            return true;
        }
        catch(RuntimeException problem)
        {
            error="Light value: "+problem.getMessage();
            return true;
        }
    }

    private void saveNativeValue(AnimationEditorScreen screen)
    {
        NBTTagCompound source=originKey!=null&&
                originKey.getLightSettings()!=null
                ?originKey.getLightSettings()
                :actor.getActor().getMorph();
        NBTTagCompound updated=source.copy();
        morph.toNBT(updated);
        if(originKey!=null)
            originKey.setLightSettings(updated);
        else actor.getActor().setMorph(updated);
        signature=updated.toString();
        if(screen!=null)screen.markEditorDirty();
        error=null;
    }

    public boolean mouseClicked(AnimationEditorScreen screen,
            int mx,int my,int button)
    {
        return mouseClicked(screen,screen.getSelectedActorForTimeline(),
                mx,my,button);
    }

    private boolean selectSection(int mx,int my)
    {
        int start=tabsY();
        for(int i=0;i<4;i++)
        {
            int tx=x+9+(i%2)*85,ty=start+(i/2)*21;
            if(mx>=tx&&mx<tx+80&&my>=ty&&my<ty+19)
            {
                section=i;
                scroll=0;
                picking=null;
                sliding=null;
                return true;
            }
        }
        return false;
    }

    private void adjustNumber(AnimationEditorScreen screen,
            Entry entry,int mx)
    {
        if(entry==null||!(entry.value instanceof GenericNumberValue))
            return;
        GenericNumberValue<?> numeric=(GenericNumberValue<?>)entry.value;
        double min=numeric.getMin().doubleValue(),
                max=numeric.getMax().doubleValue();
        if(!Double.isFinite(min)||!Double.isFinite(max)||max<=min)
            return;
        double fraction=(mx-(x+15))/154D;
        double next=BetterLightsPanelModel.rangedValue(fraction,min,max);
        if(!Double.isFinite(next))return;
        Object old=entry.value.getValue();
        if(old instanceof Integer)entry.value.setValue((int)Math.round(next));
        else if(old instanceof Float)entry.value.setValue((float)next);
        else if(old instanceof Double)entry.value.setValue(next);
        else return;
        if(!old.equals(entry.value.getValue()))saveNativeValue(screen);
    }

    private void openColor(Entry entry,int rowY)
    {
        picking=entry;sliding=null;
        int rgba=((Color)entry.value.getValue()).getRGBAColor();
        float[] hsb=java.awt.Color.RGBtoHSB(
                (rgba>>>16)&255,(rgba>>>8)&255,rgba&255,null);
        hue=hsb[0];saturation=hsb[1];brightness=hsb[2];
        pickerTop=Math.max(top+69,Math.min(rowY+43,bottom-147));
    }

    private boolean colorAt(AnimationEditorScreen screen,int mx,int my,
            boolean dragging)
    {
        if(picking==null)return false;
        int px=x+8,py=pickerTop;
        if(!dragging)
        {
            if(mx<px||mx>=px+169||my<py||my>=py+146)
            {
                picking=null;dragColorPart=0;return false;
            }
            if(my<py+19&&mx>px+112)
            {
                picking=null;dragColorPart=0;return true;
            }
            dragColorPart=mx>=px+9&&mx<px+129&&
                    my>=py+22&&my<py+99?1:
                    mx>=px+136&&mx<px+151&&
                    my>=py+22&&my<py+99?2:
                    mx>=px+10&&mx<px+155&&
                    my>=py+108&&my<py+121?3:0;
        }
        if(dragColorPart==0)return true;
        if(dragColorPart==1)
        {
            saturation=(float)Math.max(0D,Math.min(1D,
                    (mx-(px+9))/119D));
            brightness=(float)Math.max(0D,Math.min(1D,
                    1D-(my-(py+22))/75D));
        }
        else if(dragColorPart==2)
            hue=(float)Math.max(0D,Math.min(1D,
                    (my-(py+22))/75D));
        int rgba=((Color)picking.value.getValue()).getRGBAColor();
        int alpha=(rgba>>>24)&255;
        if(dragColorPart==3)
            alpha=(int)Math.round(255*Math.max(0D,Math.min(1D,
                    (mx-(px+10))/145D)));
        int rgb=java.awt.Color.HSBtoRGB(hue,saturation,brightness)&0xFFFFFF;
        int next=(alpha<<24)|rgb;
        if(next!=rgba)
        {
            picking.value.setValue(new Color(next));
            saveNativeValue(screen);
        }
        return true;
    }

    /** Same native light values for the CAMERA-only synthetic scene source. */
    public boolean mouseClicked(AnimationEditorScreen screen,
            BlockbusterSceneActorData lightActor,int mx,int my,int button)
    {
        if(editing!=null)commit(screen);
        if(!isLightActor(lightActor))return false;
        if(picking!=null&&colorAt(screen,mx,my,false))return true;
        if(mx<x+7||mx>=x+179||my<top||my>=bottom)return false;
        if(button!=0)return true;
        if(selectSection(mx,my))return true;
        List<Entry> shown=visibleEntries();
        int offset=my-rowsY();
        if(offset<0)return true;
        int slot=offset/rowHeight();
        int index=scroll+slot;
        if(index<0||index>=shown.size()||
                slot>=visibleCount())return true;
        Entry target=shown.get(index);
        int cy=rowsY()+slot*rowHeight();
        Object value=target.value.getValue();
        if(value instanceof Boolean)
        {
            target.value.setValue(!((Boolean)value));
            saveNativeValue(screen);
            return true;
        }
        if(value instanceof Color&&mx>=x+140&&my>=cy+15&&my<cy+39)
        {
            openColor(target,cy);
            return true;
        }
        if(value instanceof Number&&target.value instanceof GenericNumberValue
                &&my>=cy+23&&my<cy+42)
        {
            GenericNumberValue<?> n=(GenericNumberValue<?>)target.value;
            double min=n.getMin().doubleValue(),max=n.getMax().doubleValue();
            if(Double.isFinite(min)&&Double.isFinite(max)&&max>min)
            {
                sliding=target;
                adjustNumber(screen,target,mx);
                return true;
            }
        }
        editing=target;
        input=new GuiTextField(17431,Minecraft.getMinecraft().fontRenderer,
                x+14,cy+18,155,16);
        input.setMaxStringLength(80);
        input.setText(text(editing.value));
        input.setFocused(true);
        return true;
    }

    /** Matches MouseClickMove for both CAMERA statics and POSE actor lights. */
    public boolean mouseDragged(AnimationEditorScreen screen,int mx,int my)
    {
        if(sliding!=null)
        {
            adjustNumber(screen,sliding,mx);
            return true;
        }
        return picking!=null&&dragColorPart!=0&&
                colorAt(screen,mx,my,true);
    }

    public void mouseReleased()
    {
        sliding=null;
        dragColorPart=0;
    }

    /** Finish a pending value before changing the source or closing. */
    public void flush(AnimationEditorScreen screen)
    {
        if(editing!=null)commit(screen);
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
        // Don't detach a focused field from its value by scrolling while
        // the user is still editing the text.
        if(editing!=null)return true;
        int visible=Math.max(1,visibleCount());
        int count=visibleEntries().size();
        scroll=Math.max(0,Math.min(Math.max(0,count-visible),
                scroll+(wheel>0?-1:1)));
        return true;
    }
}
