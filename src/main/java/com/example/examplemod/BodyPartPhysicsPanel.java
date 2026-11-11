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
    private boolean colliderTab;
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

    public void draw(Minecraft mc, int mouseX, int mouseY)
    {
        if(this.settings==null)return;
        int accent=EditorThemeManager.get().getAccent();
        Gui.drawRect(x,y,x+175,y+285,EditorVisualStyle.themedSurface(0.080F));
        Gui.drawRect(x,y,x+175,y+22,EditorVisualStyle.themedSurface(0.170F));
        Gui.drawRect(x,y,x+3,y+22,accent);
        Gui.drawRect(x+5,y+2,x+87,y+21,
                colliderTab?EditorVisualStyle.themedSurface(0.170F):EditorVisualStyle.themedSurface(0.290F));
        Gui.drawRect(x+88,y+2,x+174,y+21,
                colliderTab?EditorVisualStyle.themedSurface(0.290F):EditorVisualStyle.themedSurface(0.170F));
        mc.fontRenderer.drawString("CHAINS",x+13,y+7,0xFFE0E0E0);
        mc.fontRenderer.drawString("COLLIDERS",x+96,y+7,0xFFE0E0E0);
        mc.fontRenderer.drawString(trim(mc,boneName,155),x+9,y+31,0xFFAAAAAA);
        if(colliderTab && colliders!=null)
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
        else
        {
            drawToggle(mc,"Enable chain root",settings.isEnabled(),50);
            drawValue(mc,"Gravity",settings.getGravity(),77);
            drawValue(mc,"Stiffness",settings.getStiffness(),108);
            drawValue(mc,"Damping",settings.getDamping(),139);
            drawToggle(mc,"Body collisions",settings.hasBodyCollisions(),170);
            mc.fontRenderer.drawString("Chain bones: "+chainBones,
                    x+9,y+196,0xFFAAAAAA);
        }
        if(BodyPartJoltPreviewAdapter.isRequested())
            drawToggle(mc,"Show Jolt colliders",
                    BodyPartJoltPreviewAdapter.drawContactsRequested(),211);
        else
            mc.fontRenderer.drawString("Solver: Jolt disabled",
                    x+9,y+209,0xFF999999);
        if(!colliderTab&&skirtModel!=null&&skirtWaist!=null)
        {
            boolean active=settings.isEnabled();
            Gui.drawRect(x+8,y+237,x+167,y+258,
                    EditorVisualStyle.themedSurface(active?.32F:.20F));
            Gui.drawRect(x+8,y+256,x+167,y+258,
                    active?0xFF74C1AD:EditorThemeManager.get().getAccent());
            mc.fontRenderer.drawString("SKIRT 3+2+2  "+(active?"ON":"SET"),
                    x+14,y+244,0xFFE7F1F5);
            String msg=skirtStatus;
            if(msg.isEmpty())
                msg="Select waist with 7 strip roots";
            mc.fontRenderer.drawString(trim(mc,msg,158),
                    x+9,y+267,0xFF9BAFB8);
        }
    }

    private void drawValue(Minecraft mc, String title, float value, int lineY)
    {
        mc.fontRenderer.drawString(title, x + 9, y + lineY, 0xFFE0E0E0);
        String number = String.format(java.util.Locale.ROOT, "%.4f", value);
        int id = colliderTab ? 3 : lineY == 77 ? 0 : lineY == 108 ? 1 : 2;
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
                mouseY<y || mouseY>=y+285)return false;
        int row=mouseY-y;
        if(row>=2 && row<=22)
        {
            colliderTab=mouseX>=x+88;
            activeNumericProperty = -1;
            return true;
        }
        if(row>=208 && row<=226 && mouseX>=x+140 &&
                BodyPartJoltPreviewAdapter.isRequested())
        {
            BodyPartJoltPreviewAdapter.toggleDrawContacts();
            return true;
        }
        if(!colliderTab&&row>=237&&row<259&&
                mouseX>=x+8&&mouseX<x+167&&
                skirtModel!=null&&skirtWaist!=null)
        {
            try
            {
                SkirtChainTopology.Plan<AnimationBone> plan=
                        BodyPartSkirtPreset.inspect(skirtWaist);
                if(settings.isEnabled())
                {
                    BodyPartSkirtPreset.disable(skirtModel,skirtWaist);
                    skirtStatus="Skirt disabled";
                }
                else
                {
                    BodyPartSkirtPreset.enable(skirtModel,skirtWaist,skirtFrame);
                    skirtStatus="7 strips active (3+2+2)";
                }
            }
            catch(IllegalArgumentException|IllegalStateException failed)
            {
                skirtStatus=failed.getMessage();
            }
            return true;
        }
        if(colliderTab)
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
        if (activeNumericProperty < 0 ||
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
        if (settings == null) return;
        if (property == 0) settings.setGravity(settings.getGravity() + delta);
        else if (property == 1) settings.setStiffness(settings.getStiffness() + delta);
        else if (property == 2) settings.setDamping(settings.getDamping() + delta);
    }
}
