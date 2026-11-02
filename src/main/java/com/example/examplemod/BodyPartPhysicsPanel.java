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
    private int focusedNumber=-1; // gravity, stiffness, damping, scale

    public void setTarget(int x, int y, String boneName,
            BodyPartPhysicsSettings settings, BodyPartCollisionSettings colliders,
            int chainBones)
    {
        this.x = x;
        this.y = y;
        this.boneName = boneName == null ? "" : boneName;
        this.settings = settings;
        this.colliders = colliders;
        this.chainBones = chainBones;
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
        Gui.drawRect(x,y,x+175,y+235,0xFF171D23);
        Gui.drawRect(x,y,x+175,y+22,0xFF293038);
        Gui.drawRect(x,y,x+3,y+22,accent);
        Gui.drawRect(x+5,y+2,x+87,y+21,
                colliderTab?0xFF303134:0xFF484C50);
        Gui.drawRect(x+88,y+2,x+174,y+21,
                colliderTab?0xFF484C50:0xFF303134);
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
    }

    private void drawValue(Minecraft mc, String title, float value, int lineY)
    {
        mc.fontRenderer.drawString(title, x + 9, y + lineY, 0xFFE0E0E0);
        String number=EditorNumericStep.format(value);
        drawButton(mc,"<",x+92,y+lineY-4);
        Gui.drawRect(x+110,y+lineY-4,x+149,y+lineY+13,
                0xFF10151A);
        mc.fontRenderer.drawString(
                mc.fontRenderer.trimStringToWidth(number,35),
                x+113,y+lineY,0xFFE5EFF3);
        drawButton(mc,">",x+150,y+lineY-4);
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
        Gui.drawRect(left,top,left+17,top+17,0xFF293640);
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
            colliderTab=mouseX>=x+88;
            return true;
        }
        if(row>=208 && row<=226 && mouseX>=x+140 &&
                BodyPartJoltPreviewAdapter.isRequested())
        {
            BodyPartJoltPreviewAdapter.toggleDrawContacts();
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
                focusedNumber=3;
                float delta=mouseX>=x+92&&mouseX<x+109?
                        (float)-EditorNumericStep.MOUSE:
                        mouseX>=x+150&&mouseX<x+167?
                        (float)EditorNumericStep.MOUSE:0F;
                if(delta!=0F)
                    colliders.setScale(Math.min(2F,Math.max(0.25F,
                            colliders.getScale()+delta)));
            }
            return true;
        }
        if(row>=47 && row<=64 && mouseX>=x+140)
            settings.setEnabled(!settings.isEnabled());
        else if(row>=73 && row<=93){focusedNumber=0;change(mouseX,0);}
        else if(row>=104 && row<=124){focusedNumber=1;change(mouseX,1);}
        else if(row>=135 && row<=155){focusedNumber=2;change(mouseX,2);}
        else if(row>=167 && row<=185 && mouseX>=x+140)
            settings.setBodyCollisions(!settings.hasBodyCollisions());
        return true;
    }

    public boolean keyTyped(char typedChar,int keyCode)
    {
        if(focusedNumber<0)return false;
        if(keyCode==org.lwjgl.input.Keyboard.KEY_ESCAPE)
        {
            focusedNumber=-1;return true;
        }
        if(keyCode!=org.lwjgl.input.Keyboard.KEY_UP&&
                keyCode!=org.lwjgl.input.Keyboard.KEY_DOWN)return false;
        float delta=(float)(keyCode==org.lwjgl.input.Keyboard.KEY_UP?
                EditorNumericStep.KEYBOARD:-EditorNumericStep.KEYBOARD);
        if(focusedNumber==3&&colliders!=null)
            colliders.setScale(Math.min(2F,Math.max(0.25F,
                    colliders.getScale()+delta)));
        else if(focusedNumber==0)
            settings.setGravity(settings.getGravity()+delta);
        else if(focusedNumber==1)
            settings.setStiffness(settings.getStiffness()+delta);
        else if(focusedNumber==2)
            settings.setDamping(settings.getDamping()+delta);
        return true;
    }

    private void change(int mouseX, int property)
    {
        float delta;
        if(mouseX>=x+92&&mouseX<x+109)
            delta=(float)-EditorNumericStep.MOUSE;
        else if(mouseX>=x+150&&mouseX<x+167)
            delta=(float)EditorNumericStep.MOUSE;
        else return;
        if (property == 0) settings.setGravity(settings.getGravity() + delta);
        else if (property == 1) settings.setStiffness(settings.getStiffness() + delta);
        else settings.setDamping(settings.getDamping() + delta);
    }
}
