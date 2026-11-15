package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/** Inspector for physics properties of a selected Body Part internal bone. */
public final class BodyPartPhysicsPanel
{
    private int x;
    private int y;
    private String boneName = "";
    private BodyPartPhysicsSettings settings;
    private BodyPartCollisionSettings colliders;
    /** Hair Jolt / Cloth XPBD / passive collision volumes. */
    private int section;
    private int chainBones;
    private int activeNumericProperty = -1;
    private BodyPartModelData skirtModel;
    private AnimationBone skirtWaist;
    private int skirtFrame;
    private String skirtStatus = "";
    private static final float CLICK_STEP = 0.0625F;
    private static final float KEY_STEP = 0.03125F;

    public void setTarget(int x, int y, String boneName,
            BodyPartPhysicsSettings settings, BodyPartCollisionSettings colliders,
            int chainBones)
    {
        this.x = x;
        this.y = y;
        String nextName = boneName == null ? "" : boneName;
        if (!this.boneName.equals(nextName)) activeNumericProperty = -1;
        this.boneName = nextName;
        this.settings = settings;
        this.colliders = colliders;
        this.chainBones = chainBones;
    }

    /** The selected Chameleon attachment and its actual internal bone. */
    public void setSkirtTarget(BodyPartModelData model,
            AnimationBone selectedBone,int frame)
    {
        if(skirtModel!=model)
        {
            section=model!=null&&model.supportsClothPhysics()?1:0;
            activeNumericProperty=-1;
        }
        if(skirtModel!=model||skirtWaist!=selectedBone)
            skirtStatus="";
        skirtModel=model;
        skirtWaist=selectedBone;
        skirtFrame=frame;
    }

    /** Older caller compatibility; authored colliders remain unchanged. */
    public void setTarget(int x, int y, String boneName,
            BodyPartPhysicsSettings settings,int chainBones)
    {
        setTarget(x,y,boneName,settings,null,chainBones);
    }

    private boolean clothAvailable()
    {
        return skirtModel!=null&&skirtModel.supportsClothPhysics();
    }

    public void draw(Minecraft mc, int mouseX, int mouseY)
    {
        if(this.settings==null)return;
        int accent=EditorThemeManager.get().getAccent();
        Gui.drawRect(x,y,x+175,y+235,EditorVisualStyle.themedSurface(0.080F));
        Gui.drawRect(x,y,x+175,y+22,EditorVisualStyle.themedSurface(0.170F));
        Gui.drawRect(x,y,x+3,y+22,accent);
        final String[] tabs={"HAIR","CLOTH","BOXES"};
        final int[] lefts={x+5,x+61,x+116};
        final int[] rights={x+60,x+115,x+174};
        for(int i=0;i<tabs.length;i++)
        {
            Gui.drawRect(lefts[i],y+2,rights[i],y+21,
                    EditorVisualStyle.themedSurface(
                            section==i?0.290F:0.170F));
            String title=EditorLanguage.tr(tabs[i]);
            mc.fontRenderer.drawString(trim(mc,tabs[i],
                    rights[i]-lefts[i]-3),
                    lefts[i]+Math.max(1,(rights[i]-lefts[i]-
                            mc.fontRenderer.getStringWidth(title))/2),
                    y+7,0xFFE0E0E0);
        }
        mc.fontRenderer.drawString(trim(mc,boneName,155),
                x+9,y+31,0xFFAAAAAA);
        if(section==2 && colliders!=null)
        {
            drawToggle(mc,"Enable collider",colliders.isEnabled(),50);
            mc.fontRenderer.drawString("Shape",x+9,y+81,0xFFE0E0E0);
            mc.fontRenderer.drawString(colliders.getShape().name(),
                    x+98,y+81,0xFFBDF8C6);
            drawValue(mc,"Shape scale",colliders.getScale(),108);
            mc.fontRenderer.drawString("Static passive geometry",x+9,y+152,
                    0xFF999999);
            mc.fontRenderer.drawString("Hair-driven bones excluded",x+9,y+169,
                    0xFF999999);
        }
        else if(section==1)
        {
            if(clothAvailable())
            {
                drawToggle(mc,"Enable cloth XPBD",
                        skirtModel.isClothPhysicsEnabled(),50);
                mc.fontRenderer.drawString(
                        EditorLanguage.tr("Solver: XPBD 60 Hz"),
                        x+9,y+82,0xFFE0E0E0);
                mc.fontRenderer.drawString(
                        EditorLanguage.tr("60 nodes / 20 waist pins"),
                        x+9,y+110,0xFFBDF8C6);
                mc.fontRenderer.drawString(
                        EditorLanguage.tr("Leg contacts: Emoticons"),
                        x+9,y+141,0xFFE0E0E0);
                mc.fontRenderer.drawString(
                        EditorLanguage.tr("Jolt chains: independent"),
                        x+9,y+171,0xFFAAAAAA);
                mc.fontRenderer.drawString(
                        trim(mc,SkirtXPBDRuntime.ENABLED
                                ?"XPBD ready (60 Hz)"
                                :"Enable XPBD at launch",155),
                        x+9,y+196,0xFFAAAAAA);
            }
            else
            {
                mc.fontRenderer.drawString("CLOTH: XPBD",
                        x+9,y+52,0xFFE0E0E0);
                mc.fontRenderer.drawString(
                        trim(mc,"20-strip Chameleon model required",155),
                        x+9,y+82,0xFFAAAAAA);
                mc.fontRenderer.drawString(
                        trim(mc,"Hair remains on Jolt",155),
                        x+9,y+110,0xFFAAAAAA);
            }
        }
        else
        {
            if(clothAvailable() && SkirtXPBDRuntime.ENABLED)
            {
                mc.fontRenderer.drawString(
                        trim(mc,"This attachment uses CLOTH",155),
                        x+9,y+60,0xFFAAAAAA);
                mc.fontRenderer.drawString(
                        trim(mc,"Jolt hair chains are isolated",155),
                        x+9,y+87,0xFFAAAAAA);
            }
            else
            {
                drawToggle(mc,"Enable chain root",settings.isEnabled(),50);
                drawValue(mc,"Gravity",settings.getGravity(),77);
                drawValue(mc,"Stiffness",settings.getStiffness(),108);
                drawValue(mc,"Damping",settings.getDamping(),139);
                drawToggle(mc,"Body collisions",
                        settings.hasBodyCollisions(),170);
                mc.fontRenderer.drawString(
                        trim(mc,"Chain bones: "+chainBones,155),
                        x+9,y+196,0xFFAAAAAA);
            }
        }
        if(BodyPartJoltPreviewAdapter.isRequested())
            drawToggle(mc,"Show Jolt colliders",
                    BodyPartJoltPreviewAdapter.drawContactsRequested(),211);
        else
            mc.fontRenderer.drawString("Solver: Jolt disabled",
                    x+9,y+209,0xFF999999);
    }

    private void drawValue(Minecraft mc, String title, float value, int lineY)
    {
        mc.fontRenderer.drawString(title, x + 9, y + lineY, 0xFFE0E0E0);
        String number = String.format(java.util.Locale.ROOT, "%.4f", value);
        int id = section==2 ? 3 : lineY == 77 ? 0 : lineY == 108 ? 1 : 2;
        Gui.drawRect(x + 87, y + lineY - 4, x + 174, y + lineY + 13,
                id == activeNumericProperty ? EditorVisualStyle.themedSurface(0.290F) : EditorVisualStyle.themedSurface(0.130F));
        mc.fontRenderer.drawString(number, x + 110, y + lineY, 0xFFE6F0F3);
        drawButton(mc, "<", x + 89, y + lineY - 4);
        drawButton(mc, ">", x + 155, y + lineY - 4);
    }

    private void drawToggle(Minecraft mc, String label, boolean selected, int lineY)
    {
        mc.fontRenderer.drawString(label, x + 9, y + lineY, 0xFFE0E0E0);
        Gui.drawRect(x + 145, y + lineY - 3, x + 166, y + lineY + 10,
                selected ? 0xFF408060 : 0xFF484848);
        mc.fontRenderer.drawString(selected ? "ON" : "OFF",
                x + (selected ? 148 : 146), y + lineY, 0xFFFFFFFF);
    }

    private void drawButton(Minecraft mc, String text, int left, int top)
    {
        Gui.drawRect(left, top, left + 17, top + 17, EditorVisualStyle.themedSurface(0.290F));
        mc.fontRenderer.drawString(text, left + 6, top + 4, 0xFFFFFFFF);
    }

    private static String trim(Minecraft mc, String value, int maxWidth)
    {
        return mc.fontRenderer.trimStringToWidth(value, maxWidth);
    }

    public boolean mouseClicked(int mouseX,int mouseY,int mouseButton)
    {
        if(mouseButton!=0 || settings==null ||
                mouseX<x || mouseX>=x+175 ||
                mouseY<y || mouseY>=y+235)return false;
        int row=mouseY-y;
        if(row>=2 && row<=22)
        {
            section=mouseX<x+61?0:mouseX<x+116?1:2;
            activeNumericProperty = -1;
            return true;
        }
        if(row>=208 && row<=226 && mouseX>=x+140 &&
                BodyPartJoltPreviewAdapter.isRequested())
        {
            BodyPartJoltPreviewAdapter.toggleDrawContacts();
            return true;
        }
        if(section==2)
        {
            if(colliders==null)return true;
            if(row>=47 && row<=64 && mouseX>=x+140)
                colliders.setEnabled(!colliders.isEnabled());
            else if(row>=75 && row<=97)
            {
                BodyPartCollisionSettings.Shape[] types=
                        BodyPartCollisionSettings.Shape.values();
                colliders.setShape(types[
                        (colliders.getShape().ordinal()+1)%types.length]);
            }
            else if(row>=104 && row<=128)
            {
                activeNumericProperty = 3;
                float delta=mouseX>=x+89 && mouseX<x+106?-CLICK_STEP:
                        mouseX>=x+155 && mouseX<x+172?CLICK_STEP:0F;
                if(delta!=0F)
                    colliders.setScale(Math.min(2F,Math.max(0.25F,
                            colliders.getScale()+delta)));
            }
            return true;
        }
        if(section==1)
        {
            if(clothAvailable()&&row>=47&&row<=64&&
                    mouseX>=x+8&&mouseX<x+167)
            {
                skirtModel.setClothPhysicsEnabled(
                        !skirtModel.isClothPhysicsEnabled());
                skirtStatus="";
            }
            return true;
        }
        if(clothAvailable()&&SkirtXPBDRuntime.ENABLED)
            return true;
        if(row>=47 && row<=64 && mouseX>=x+140)
            settings.setEnabled(!settings.isEnabled());
        else if(row>=73 && row<=93)change(mouseX,0);
        else if(row>=104 && row<=124)change(mouseX,1);
        else if(row>=135 && row<=155)change(mouseX,2);
        else if(row>=167 && row<=185 && mouseX>=x+140)
            settings.setBodyCollisions(!settings.hasBodyCollisions());
        return true;
    }

    private void change(int mouseX, int property)
    {
        activeNumericProperty = property;
        if (mouseX >= x + 89 && mouseX < x + 106) nudge(property, -CLICK_STEP);
        else if (mouseX >= x + 155 && mouseX < x + 172) nudge(property, CLICK_STEP);
    }

    public boolean keyTyped(char typedChar, int keyCode)
    {
        if (activeNumericProperty < 0 || section==1 ||
                (section==0&&clothAvailable()&&SkirtXPBDRuntime.ENABLED) ||
                (keyCode != org.lwjgl.input.Keyboard.KEY_UP &&
                 keyCode != org.lwjgl.input.Keyboard.KEY_DOWN)) return false;
        nudge(activeNumericProperty,
                keyCode == org.lwjgl.input.Keyboard.KEY_UP ? KEY_STEP : -KEY_STEP);
        return true;
    }

    private void nudge(int property, float delta)
    {
        if (property == 3)
        {
            if (colliders != null) colliders.setScale(Math.min(2F,
                    Math.max(0.25F, colliders.getScale() + delta)));
            return;
        }
        if (settings == null || section!=0) return;
        if (property == 0) settings.setGravity(settings.getGravity() + delta);
        else if (property == 1) settings.setStiffness(settings.getStiffness() + delta);
        else if (property == 2) settings.setDamping(settings.getDamping() + delta);
    }
}
