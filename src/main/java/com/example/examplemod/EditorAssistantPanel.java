package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * Small, deliberately offline assistant console.
 *
 * A command is previewed first. Scene-changing commands require a separate
 * explicit click on APPROVE. Enter only PREVIEWS and never edits a scene.
 * There is no API key, network request, reflection or generated-code runner.
 */
public final class EditorAssistantPanel
{
    private static final int BG=0xF0161B26;
    private static final int ACCENT=0xFF8B76D7;
    private static final int TEXT=0xFFE8EAF2;
    private static final int MUTED=0xFFABB0C2;

    private boolean open;
    private int x,y,w,h;
    private GuiTextField command;
    private EditorAssistantCore.Proposal proposal;
    private String feedback="";
    private boolean feedbackGood;

    public boolean isOpen(){return open;}

    public void open(AnimationEditorScreen screen)
    {
        if(screen==null)return;
        open=true;
        proposal=null;
        feedback="";
        layout(screen);
        if(command!=null)command.setFocused(true);
        Keyboard.enableRepeatEvents(true);
    }

    public void close()
    {
        open=false;
        proposal=null;
        if(command!=null)command.setFocused(false);
        Keyboard.enableRepeatEvents(false);
    }

    public void toggle(AnimationEditorScreen screen)
    {
        if(open)close();
        else open(screen);
    }

    private void layout(AnimationEditorScreen screen)
    {
        int newWidth=Math.max(240,Math.min(540,screen.width-28));
        int nx=(screen.width-newWidth)/2;
        int ny=Math.max(22,(screen.height-190)/2);
        if(command!=null&&x==nx&&y==ny&&w==newWidth)return;
        String old=command==null?"":command.getText();
        x=nx;y=ny;w=newWidth;h=190;
        FontRenderer font=Minecraft.getMinecraft().fontRenderer;
        command=new GuiTextField(401,font,x+12,y+58,w-24,19);
        command.setMaxStringLength(1000);
        command.setText(old);
        command.setFocused(true);
    }

    public void draw(AnimationEditorScreen screen)
    {
        if(!open||screen==null)return;
        layout(screen);
        Minecraft mc=Minecraft.getMinecraft();
        FontRenderer font=mc.fontRenderer;
        Gui.drawRect(0,0,screen.width,screen.height,0x9C080B13);
        Gui.drawRect(x-1,y-1,x+w+1,y+h+1,ACCENT);
        Gui.drawRect(x,y,x+w,y+h,BG);
        font.drawString("BBS ASSISTANT - OFFLINE",x+12,y+11,TEXT);
        font.drawString("ESC: close  |  ENTER: preview  |  Ctrl+Shift+A: toggle",
                x+12,y+27,MUTED);
        font.drawString("Command / Команда:",x+12,y+45,MUTED);
        command.drawTextBox();

        if(proposal!=null)
        {
            String mode=proposal.requiresApproval?"WAITING FOR APPROVAL":"READ-ONLY";
            font.drawString(mode,x+12,y+85,
                    proposal.requiresApproval?0xFFFFD58C:0xFF93D3FF);
            font.drawString(font.trimStringToWidth(
                    proposal.description,w-24),x+12,y+100,TEXT);
        }
        else
        {
            font.drawString("Try: помощь, состояние, перейди на кадр 25, создай ключ",
                    x+12,y+91,MUTED);
        }
        if(!feedback.isEmpty())
            font.drawString(font.trimStringToWidth(feedback,w-24),
                    x+12,y+120,feedbackGood?0xFF76E69D:0xFFFF9A91);

        drawButton(font,x+12,y+h-34,108,22,"PREVIEW",true);
        drawButton(font,x+130,y+h-34,113,22,"APPROVE",
                proposal!=null&&proposal.requiresApproval);
        drawButton(font,x+w-100,y+h-34,88,22,"CLOSE",true);
    }

    private static void drawButton(FontRenderer font,int bx,int by,
            int width,int height,String label,boolean enabled)
    {
        Gui.drawRect(bx,by,bx+width,by+height,
                enabled?0xFF38364D:0xFF242631);
        Gui.drawRect(bx,by,bx+width,by+1,
                enabled?ACCENT:0xFF474958);
        int left=bx+(width-font.getStringWidth(label))/2;
        font.drawString(label,left,by+7,enabled?TEXT:MUTED);
    }

    private boolean hit(int mx,int my,int bx,int by,int bw,int bh)
    {
        return mx>=bx&&mx<bx+bw&&my>=by&&my<by+bh;
    }

    public boolean mouseClicked(AnimationEditorScreen screen,
            int mouseX,int mouseY,int button)
    {
        if(!open)return false;
        if(button!=0)return true;
        layout(screen);
        command.mouseClicked(mouseX,mouseY,button);
        if(hit(mouseX,mouseY,x+12,y+h-34,108,22))
            preview(screen);
        else if(hit(mouseX,mouseY,x+130,y+h-34,113,22))
            approve(screen);
        else if(hit(mouseX,mouseY,x+w-100,y+h-34,88,22))
            close();
        return true; // modal: never click through to actor, gizmo or timeline
    }

    public boolean keyTyped(AnimationEditorScreen screen,char typed,int key)
    {
        if(!open)return false;
        if(key==Keyboard.KEY_ESCAPE){close();return true;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER)
        {
            preview(screen);
            return true;
        }
        String old=command.getText();
        if(command.textboxKeyTyped(typed,key)&&
                !old.equals(command.getText()))
        {
            proposal=null;
            feedback="";
        }
        return true;
    }

    private void preview(AnimationEditorScreen screen)
    {
        proposal=screen.previewAssistantCommand(command.getText());
        feedback="";
        if(proposal!=null&&!proposal.requiresApproval)
        {
            feedback=proposal.action==EditorAssistantCore.Action.UNKNOWN?
                    "Unknown command. No changes made.":
                    "Read-only result. No scene changes.";
            feedbackGood=proposal.action!=EditorAssistantCore.Action.UNKNOWN;
        }
    }

    private void approve(AnimationEditorScreen screen)
    {
        if(proposal==null||!proposal.requiresApproval)return;
        boolean applied=screen.executeAssistantCommand(proposal,true);
        feedback=applied?"Command completed.":"Unavailable or scene context changed.";
        feedbackGood=applied;
        proposal=null; // one-time approval; never replay on a later click
    }
}
