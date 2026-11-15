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
    public void drawTabs(Minecraft mc,int panelX,int topY)
    {
        x=panelX;top=topY;
        Gui.drawRect(x+7,top+10,x+91,top+34,
                EditorVisualStyle.themedSurface(settingsTab?.11F:.30F));
        Gui.drawRect(x+94,top+10,x+180,top+34,
                EditorVisualStyle.themedSurface(settingsTab?.30F:.11F));
        String left=mc.fontRenderer.trimStringToWidth("POSITION",78);
        String right=mc.fontRenderer.trimStringToWidth("SETTINGS",78);
        mc.fontRenderer.drawString(left,
                x+7+(84-mc.fontRenderer.getStringWidth(left))/2,
                top+18,0xFFE4ECF2);
        mc.fontRenderer.drawString(right,
                x+94+(86-mc.fontRenderer.getStringWidth(right))/2,
                top+18,0xFFE4ECF2);
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
