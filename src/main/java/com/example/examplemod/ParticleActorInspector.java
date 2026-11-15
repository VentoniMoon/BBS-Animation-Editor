package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.NBTTagCompound;

/** The ParticleMorph POSE inspector has two sections:
 * POSITION (existing virtual Origin key/transform panel) and
 * SETTINGS (the exact same native fields as static CAMERA emitters).
 */
public final class ParticleActorInspector
{
    private boolean settingsTab;
    private BlockbusterSceneActorData actor;
    private AnimationKeyframe activeKey;
    private NBTTagCompound editTag;
    private final ParticleSettingsPanel panel=new ParticleSettingsPanel();
    private int x,top,bottom;

    public boolean settingsTab(){return settingsTab;}
    public void reset()
    {
        settingsTab=false;actor=null;activeKey=null;editTag=null;
        panel.reset();
    }
    public void select(AnimationEditorScreen screen,
            BlockbusterSceneActorData source,AnimationKeyframe key)
    {
        if(source==actor&&key==activeKey)return;
        actor=source;activeKey=key;
        if(source==null||source.getActor()==null)
        {
            editTag=null;panel.reset();return;
        }
        NBTTagCompound base=source.getActor().getMorph();
        NBTTagCompound stored=key==null?base:key.getParticleSettings();
        if(stored==null)stored=base;
        editTag=ParticleEmitterConfig.normalize(stored);
        panel.bind(editTag,()->persist(screen));
    }
    private void persist(AnimationEditorScreen screen)
    {
        if(editTag==null||actor==null)return;
        if(activeKey!=null)
            activeKey.setParticleSettings(editTag);
        else actor.getActor().setMorph(editTag.copy());
        screen.getSceneState().getSaveController().markDirty();
    }
    /** Matching the CAMERA LIGHTS and CLIPS segmented inspector tabs. */
    public void drawTabs(Minecraft mc,int panelX,int topY)
    {
        x=panelX;top=topY;
        int accent=EditorThemeManager.get().getAccent();
        final String[] titles={"POSITION","SETTINGS"};
        for(int i=0;i<2;i++)
        {
            int tx=x+7+i*86;
            boolean selected=(settingsTab?1:0)==i;
            Gui.drawRect(tx,top+10,tx+84,top+34,
                    EditorVisualStyle.themedSurface(
                            selected?.29F:.11F));
            Gui.drawRect(tx,top+32,tx+84,top+34,
                    selected?accent:0xFF45404B);
            String title=mc.fontRenderer.trimStringToWidth(
                    titles[i],76);
            mc.fontRenderer.drawString(title,
                    tx+(84-mc.fontRenderer.getStringWidth(title))/2,
                    top+18,selected?0xFFE9E5F0:0xFFB0A9B8);
        }
    }
    public void drawSettings(AnimationEditorScreen screen,
            int px,int py,int lower)
    {
        if(!settingsTab||editTag==null)return;
        x=px;top=py;bottom=lower;
        panel.bind(editTag,()->persist(screen));
        panel.draw(Minecraft.getMinecraft(),x,top+40,bottom);
    }
    public boolean selectTab(int mx,int my,int px,int py)
    {
        if(mx<px+7||mx>=px+180||
                my<py+10||my>=py+34)return false;
        boolean next=mx>=px+94;
        if(next!=settingsTab)panel.reset();
        settingsTab=next;
        if(editTag!=null)panel.bind(editTag,()->{});
        return true;
    }
    public boolean click(int mx,int my,int button)
    {
        return settingsTab&&panel.click(mx,my,button);
    }
    public boolean keyTyped(char c,int key)
    {
        return settingsTab&&panel.keyTyped(c,key);
    }
    public boolean scroll(int mx,int my,int dir)
    {
        return settingsTab&&panel.scroll(mx,my,dir);
    }
}
