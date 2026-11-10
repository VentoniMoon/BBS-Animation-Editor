package com.example.examplemod;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * PHYSICS work area: Character-style film timeline, actor-scoped inspector
 * and a projected, mouse-draggable impulse direction guide in Preview.
 * Shares the editor's existing actor selector, world pass and frame clock.
 *
 * All authored data is in the actor's main CharacterKey NBT; there is no
 * parallel unsaved animation clock or dangerous session-only state.
 */
public final class PhysicsWorkspace
{
    public static final int TIMELINE_HEIGHT=180;
    private static final int GUTTER=AnimationEditorScreen.LEFT_PANEL_WIDTH;
    private static final int HEADER=35,ROW=20;
    private static final String[] TARGETS={
        "pelvis","chest","head","left_upper_arm","right_upper_arm",
        "left_forearm","right_forearm","left_thigh","right_thigh",
        "left_shin","right_shin"
    };
    private static final String[] TARGET_DISPLAY={
        "PELVIS","CHEST","HEAD","L ARM","R ARM",
        "L FORE","R FORE","L THIGH","R THIGH",
        "L SHIN","R SHIN"
    };
    private static final float[] GRAVITY_PRESETS={9.81F,1.62F,3.71F,0F};
    private static final String[] PRESET_NAMES={"EARTH","MOON","MARS","ZERO"};
    private final EditorTimeline timeline;
    private int selectedRow=PhysicsChannels.RAGDOLL;
    private long lastClick;
    private int lastClickRow=-1,lastClickFrame=-1;
    private boolean draggingKey;
    private int draggingRow=-1,draggingFrame=-1;
    private int gizmoEndX=-1000,gizmoEndY=-1000;
    private int gizmoBaseX=-1000,gizmoBaseY=-1000;
    private boolean draggingImpulse,draggingPoint;
    private int dragX,dragY;
    private PhysicsChannels.Impact displayedImpulse;
    private final int[] hitRows=new int[12];
    private final int[] hitValues=new int[12];
    private int hitCount;
    private int panelX,panelY,panelBottom;

    public PhysicsWorkspace(EditorTimeline timeline){this.timeline=timeline;}
    private static int accent(){return EditorThemeManager.get().getAccent();}
    private static void rect(int l,int t,int r,int b,int color)
    {
        GuiScreen.drawRect(l,t,r,b,color);
    }
    private static void text(Minecraft mc,String str,int x,int y,int color)
    {
        mc.fontRenderer.drawStringWithShadow(str,x,y,color);
    }
    private static String fixed(float value)
    {
        return String.format(java.util.Locale.ROOT,"%.2f",value);
    }
    private CharacterTimelineController track(AnimationEditorScreen screen)
    {
        BlockbusterSceneActorData actor=screen.getSelectedActorForTimeline();
        return actor==null?null:actor.getCharacterTimeline();
    }
    private PhysicsChannels.Impact selectedImpact(AnimationEditorScreen screen)
    {
        CharacterTimelineController t=track(screen);
        PhysicsChannels.Impact key=PhysicsChannels.impulseAt(t,frame(screen));
        if(key!=null)return key;
        PhysicsChannels.Impact last=PhysicsChannels.previousImpulse(t,frame(screen));
        return last==null?new PhysicsChannels.Impact():last;
    }
    private int frame(AnimationEditorScreen screen)
    {
        return Math.max(0,Math.min(screen.getSceneLength()-1,timeline.getTick()));
    }
    /**
     * Exact CHARACTER timeline palette: both workspaces must remain
     * visually interchangeable when the active theme changes.
     */
    private static int timelineShade(float strength)
    {
        int color=accent();
        int r=Math.max(0,Math.min(255,Math.round(((color>>16)&255)*strength)));
        int g=Math.max(0,Math.min(255,Math.round(((color>>8)&255)*strength)));
        int b=Math.max(0,Math.min(255,Math.round((color&255)*strength)));
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }

    /**
     * CHARACTER's 180px film timeline, including its compact 20px rows,
     * two-level ruler, frame ticks, grid, key handles and playhead.
     *
     * Draw order matters: row fills FIRST, then the full vertical grid.
     * Otherwise an opaque selected-row fill hides the frame lines.
     * Only the content differs: four PHYSICS channels instead of
     * CHARACTER / Body Part tracks.
     */
    public void drawTimeline(AnimationEditorScreen screen,int sceneLength)
    {
        Minecraft mc=Minecraft.getMinecraft();
        int width=screen.width,height=screen.height;
        int top=height-TIMELINE_HEIGHT;
        int tracksTop=top+HEADER;
        int background=timelineShade(.07F);
        int labelWidth=GUTTER-14;
        rect(0,top,width,height,background);
        rect(0,top,width,top+1,accent());

        // The CHARACTER track gutter is independent from the film grid.
        for(int row=0;row<PhysicsChannels.LABELS.length;row++)
        {
            int y=tracksTop+row*ROW;
            int rowEnd=y+ROW;
            boolean selected=row==selectedRow;
            int shade=selected?timelineShade(.22F):
                    timelineShade(row%2==0?.12F:.09F);
            rect(GUTTER,y,width,rowEnd,shade);
            rect(0,y,GUTTER,rowEnd,
                    selected?timelineShade(.22F):timelineShade(.12F));
            if(selected)rect(0,y,3,rowEnd,accent());
            rect(0,rowEnd-1,width,rowEnd,timelineShade(.05F));

            String label=PhysicsChannels.LABELS[row];
            if(mc.fontRenderer.getStringWidth(label)>labelWidth)
                label=mc.fontRenderer.trimStringToWidth(label,labelWidth);
            text(mc,label,10,y+6,
                    selected?EditorThemeManager.get().getAccentBright():
                            0xFF9DA4A9);
        }

        // CHARACTER's 35px header: 20px blank audio strip over a
        // 15px ruler, retaining the existing WAV waveform overlay.
        rect(0,top,width,tracksTop,timelineShade(.12F));
        rect(0,top,GUTTER,tracksTop,timelineShade(.17F));
        text(mc,"PHYSICS",10,top+11,
                EditorThemeManager.get().getAccentBright());
        rect(GUTTER,top,width,top+20,timelineShade(.17F));
        rect(GUTTER,top+20,width,tracksTop,timelineShade(.09F));
        rect(GUTTER,tracksTop-1,width,tracksTop,timelineShade(.20F));

        // Match CHARACTER's tick density at every zoom and honour the
        // shared horizontal scroll. Frame labels at y=top+23 remain
        // visible below the WAV strip.
        float ppf=timeline.getPixelsPerFrame();
        if(ppf>0F)
        {
            int minorStep=timeline.getZoom()>=2F?1:
                    timeline.getZoom()>=1F?5:
                    timeline.getZoom()>=.5F?10:20;
            int first=Math.max(0,(int)Math.floor(
                    timeline.getOffset()/ppf)-2);
            int last=Math.max(first,(int)Math.ceil(
                    (timeline.getOffset()+width-GUTTER)/ppf)+2);
            for(int f=first;f<=last;f++)
            {
                int x=timeline.getFrameX(f,GUTTER);
                if(x<GUTTER||x>=width)continue;
                boolean major=f%minorStep==0;
                boolean secondary=!major&&f%5==0;
                int shade=major?timelineShade(.20F):
                        secondary?timelineShade(.15F):timelineShade(.10F);
                // Grid crosses the entire film, including unused
                // rows, exactly as it does in CHARACTER.
                rect(x,tracksTop,x+1,height,shade);
                int tickHeight=major?8:secondary?5:3;
                rect(x,tracksTop-tickHeight,x+1,tracksTop,
                        major?0xFF9DA4A9:0xFF666D72);
                if(major)
                {
                    String label=Integer.toString(f);
                    int textWidth=mc.fontRenderer.getStringWidth(label);
                    text(mc,label,x-textWidth/2,top+23,0xFF9DA4A9);
                }
            }
        }

        // Render exactly the same small accent-coloured CHARACTER keys;
        // keep physics channels independent of ordinary Morph/Skin keys.
        CharacterTimelineController actorTimeline=track(screen);
        List<CharacterKey> keys=PhysicsChannels.keys(actorTimeline);
        int currentFrame=frame(screen);
        for(int row=0;row<PhysicsChannels.LABELS.length;row++)
        {
            int centerY=tracksTop+row*ROW+ROW/2;
            for(CharacterKey key:keys)
            {
                if(key==null||!PhysicsChannels.has(key,row))continue;
                int x=timeline.getFrameX(key.getFrame(),GUTTER);
                if(x<GUTTER-10||x>width+10)continue;
                EditorTimelineSkin.key(x,centerY,
                        row==selectedRow&&key.getFrame()==currentFrame);
            }
        }

        // CHARACTER playhead: subtle shadow, accent line, arrowhead
        // and current-frame number in the upper header strip.
        int x=timeline.getFrameX(currentFrame,GUTTER);
        if(x>=GUTTER&&x<width)
        {
            int bright=EditorThemeManager.get().getAccentBright();
            rect(x-1,top,x+2,height,0x66000000);
            rect(x,top,x+1,height,accent());
            rect(x-4,top,x+5,top+4,bright);
            rect(x-2,top+4,x+3,top+6,bright);
            String label=Integer.toString(currentFrame);
            int w=mc.fontRenderer.getStringWidth(label);
            int textX=Math.max(GUTTER+2,
                    Math.min(width-w-2,x-w/2));
            text(mc,label,textX,top+7,bright);
        }
    }

    public boolean clickTimeline(AnimationEditorScreen screen,
            int mx,int my,int button)
    {
        int top=screen.height-TIMELINE_HEIGHT;
        if(my<top||my>=screen.height)return false;
        if(mx<GUTTER)
        {
            if(my<top+HEADER)return true;
            int row=(my-top-HEADER)/ROW;
            if(row>=0&&row<4)selectedRow=row;
            return true;
        }
        int f=Math.max(0,Math.min(Math.max(0,screen.getSceneLength()-1),
                timeline.getFrameFromMouseX(mx,GUTTER)));
        if(my<top+HEADER)
        {
            screen.setCameraWorkspaceFrame(f);
            return true;
        }
        int row=(my-top-HEADER)/ROW;
        if(row<0||row>=4)return true;
        selectedRow=row;
        CharacterTimelineController t=track(screen);
        if(t==null)return true;
        for(CharacterKey key:PhysicsChannels.keys(t))
            if(key!=null&&PhysicsChannels.has(key,row)&&
                    Math.abs(timeline.getFrameX(key.getFrame(),GUTTER)-mx)<=5)
            {
                f=key.getFrame();break;
            }
        screen.setCameraWorkspaceFrame(f);
        if(button==0&&PhysicsChannels.has(PhysicsChannels.at(t,f),row))
        {
            draggingKey=true;
            draggingRow=row;
            draggingFrame=f;
        }
        if(button==1)
        {
            PhysicsChannels.clear(t,f,row);
            return true;
        }
        if(button==0)
        {
            long now=System.currentTimeMillis();
            boolean duplicate=now-lastClick<=360&&row==lastClickRow&&
                    Math.abs(lastClickFrame-f)<=1;
            lastClick=now;lastClickRow=row;lastClickFrame=f;
            if(duplicate)authorDefault(screen,row);
        }
        return true;
    }
    public boolean dragTimeline(AnimationEditorScreen screen,int mx,int my)
    {
        if(!draggingKey)return false;
        if(my<screen.height-TIMELINE_HEIGHT)return false;
        int target=Math.max(0,Math.min(Math.max(0,screen.getSceneLength()-1),
                timeline.getFrameFromMouseX(mx,GUTTER)));
        if(target!=draggingFrame)
        {
            PhysicsChannels.moveChannel(track(screen),
                    draggingFrame,target,draggingRow);
            draggingFrame=target;
            screen.setCameraWorkspaceFrame(target);
        }
        return true;
    }
    public void releaseTimelineDrag()
    {
        draggingKey=false;
        draggingRow=-1;
    }

    public boolean mouseScrolled(AnimationEditorScreen screen,int mx,int my,
            int direction,boolean shift)
    {
        if(my<screen.height-TIMELINE_HEIGHT||mx<GUTTER)return false;
        if(shift)timeline.changeZoom(direction>0?.25F:-.25F);
        else timeline.addOffset(direction>0?-35:35);
        timeline.clampOffset(Math.max(0,screen.getSceneLength()-1),
                Math.max(1,screen.width-GUTTER));
        return true;
    }
    private void authorDefault(AnimationEditorScreen screen,int row)
    {
        CharacterTimelineController t=track(screen);
        if(t==null)return;
        int f=frame(screen);
        switch(row)
        {
        case PhysicsChannels.RAGDOLL:
            PhysicsChannels.setWeight(t,f,
                    CharacterRagdollController.sampleWeight(t,f));
            break;
        case PhysicsChannels.IMPULSE:
            PhysicsChannels.setImpulse(t,f,selectedImpact(screen));
            // Impact without an active ragdoll cannot affect native bodies.
            if(CharacterRagdollController.sampleWeight(t,f)<=0F)
                PhysicsChannels.setWeight(t,f,1F);
            break;
        case PhysicsChannels.GRAVITY:
            PhysicsChannels.setGravity(t,f,PhysicsChannels.gravityAt(t,f));
            break;
        case PhysicsChannels.WATER:
            PhysicsChannels.setWater(t,f,PhysicsChannels.waterAt(t,f));
            break;
        }
    }
    private void drawButton(Minecraft mc,String value,int x,int y,int w,
            boolean selected)
    {
        rect(x,y,x+w,y+19,
                selected?EditorVisualStyle.themedSurface(.28F):
                        EditorVisualStyle.themedSurface(.13F));
        rect(x,y+18,x+w,y+19,selected?accent():
                EditorVisualStyle.themedSurface(.25F));
        text(mc,value,x+4,y+5,selected?0xFFF4E5FF:0xFFC2CED8);
    }
    private void drawHeading(Minecraft mc,String title,int y)
    {
        text(mc,title,panelX+10,y,0xFFEED9FF);
        rect(panelX+10,y+15,panelX+173,y+16,
                EditorVisualStyle.themedSurface(.28F));
    }
    private void buttonRow(Minecraft mc,int id,String label,String value,int y)
    {
        if(hitCount>=hitRows.length)return;
        hitRows[hitCount]=y;hitValues[hitCount++]=id;
        text(mc,label,panelX+10,y+5,0xFFB1BCC8);
        drawButton(mc,"-",panelX+117,y,19,false);
        drawButton(mc,"+",panelX+147,y,19,false);
        if(value!=null)text(mc,value,panelX+70,y+5,0xFFE8E4EE);
    }
    public void drawInspector(AnimationEditorScreen screen,int x,int y,int bottom)
    {
        Minecraft mc=Minecraft.getMinecraft();
        panelX=x;panelY=y;panelBottom=bottom;hitCount=0;
        drawHeading(mc,"PHYSICS",y+12);
        String[] shortLabels={"RAG","HIT","GRAV","WATER"};
        for(int i=0;i<4;i++)
            drawButton(mc,shortLabels[i],x+7+i*44,y+33,42,selectedRow==i);
        CharacterTimelineController t=track(screen);
        if(t==null)
        {
            text(mc,"Select an actor",x+10,y+74,0xFFB0C0CB);
            return;
        }
        int f=frame(screen);
        text(mc,"FRAME "+f,x+10,y+63,0xFF91A5B3);
        int offset=y+85;
        if(selectedRow==PhysicsChannels.RAGDOLL)
        {
            drawHeading(mc,"RAGDOLL WEIGHT",offset);
            float strength=CharacterRagdollController.sampleWeight(t,f);
            buttonRow(mc,0,"Weight",Math.round(strength*100F)+"%",offset+23);
            rect(x+10,offset+53,x+174,offset+66,
                    EditorVisualStyle.themedSurface(.21F));
            rect(x+10,offset+53,x+10+Math.round(164F*strength),offset+66,
                    accent());
            text(mc,"Physics 0% = animation",x+10,offset+75,0xFF97AAB5);
        }
        else if(selectedRow==PhysicsChannels.IMPULSE)
        {
            drawHeading(mc,"IMPULSE EVENT",offset);
            PhysicsChannels.Impact impact=selectedImpact(screen);
            buttonRow(mc,1,"Target",shortTarget(impact.target),offset+23);
            buttonRow(mc,2,"Strength",fixed(impact.strength),offset+48);
            buttonRow(mc,3,"Dir X",fixed(impact.x),offset+73);
            buttonRow(mc,4,"Dir Y",fixed(impact.y),offset+98);
            buttonRow(mc,5,"Dir Z",fixed(impact.z),offset+123);
            buttonRow(mc,6,"Hit X",fixed(impact.offsetX),offset+153);
            buttonRow(mc,7,"Hit Y",fixed(impact.offsetY),offset+178);
            buttonRow(mc,8,"Hit Z",fixed(impact.offsetZ),offset+203);
            text(mc,"Drag arrow tip in preview",x+10,offset+230,0xFF9FC7D8);
        }
        else if(selectedRow==PhysicsChannels.GRAVITY)
        {
            drawHeading(mc,"GRAVITY",offset);
            buttonRow(mc,9,"Magnitude",
                    fixed(PhysicsChannels.gravityAt(t,f)),offset+23);
            rect(x+10,offset+52,x+174,offset+64,
                    EditorVisualStyle.themedSurface(.21F));
            rect(x+10,offset+52,x+10+Math.round(164F*
                    Math.min(30F,PhysicsChannels.gravityAt(t,f))/30F),
                    offset+64,0xFF79BCF0);
            text(mc,"EARTH 9.81 | MOON 1.62",x+10,offset+70,0xFF97AAB5);
            text(mc,"MARS 3.71 | ZERO 0.00",x+10,offset+86,0xFF97AAB5);
            for(int i=0;i<4;i++)
                drawButton(mc,PRESET_NAMES[i],x+5+(i%2)*90,
                        offset+110+(i/2)*24,86,false);
        }
        else
        {
            drawHeading(mc,"WATER / IMMERSION",offset);
            PhysicsChannels.Fluid water=PhysicsChannels.waterAt(t,f);
            buttonRow(mc,10,"Amount",fixed(water.amount),offset+23);
            buttonRow(mc,11,"Buoyancy",fixed(water.buoyancy),offset+48);
            rect(x+10,offset+73,x+174,offset+81,
                    EditorVisualStyle.themedSurface(.21F));
            rect(x+10,offset+73,x+10+Math.round(164F*water.amount),
                    offset+81,0xFF69CFC0);
            text(mc,"Viscosity = linear drag",x+10,offset+87,0xFF97AAB5);
            text(mc,"Drag: "+fixed(water.drag),x+10,offset+105,0xFFBCD1D8);
            text(mc,"Angular: "+fixed(water.angularDrag),x+10,offset+123,0xFFBCD1D8);
            drawButton(mc,"DRAG -",x+10,offset+151,73,false);
            drawButton(mc,"DRAG +",x+98,offset+151,73,false);
            drawButton(mc,"SPIN -",x+10,offset+176,73,false);
            drawButton(mc,"SPIN +",x+98,offset+176,73,false);
            text(mc,"Whole-actor fluid volume",x+10,offset+207,0xFF97AAB5);
        }
        if(offset+264<bottom)
        {
            drawButton(mc,"ADD / UPDATE KEY",x+10,bottom-33,163,
                    PhysicsChannels.has(PhysicsChannels.at(t,f),selectedRow));
        }
    }
    private static String shortTarget(String target)
    {
        for(int i=0;i<TARGETS.length;i++)
            if(TARGETS[i].equals(target))return TARGET_DISPLAY[i];
        return "CHEST";
    }
    private void edit(AnimationEditorScreen screen,int id,int sign)
    {
        CharacterTimelineController t=track(screen);
        if(t==null)return;
        int f=frame(screen);
        if(id==0)
        {
            float v=CharacterRagdollController.sampleWeight(t,f);
            PhysicsChannels.setWeight(t,f,Math.max(0F,Math.min(1F,v+sign*.1F)));
        }
        else if(id>=1&&id<=8)
        {
            PhysicsChannels.Impact impact=selectedImpact(screen);
            if(id==1)
            {
                int index=1;
                for(int i=0;i<TARGETS.length;i++)
                    if(TARGETS[i].equals(impact.target)){index=i;break;}
                impact.target=TARGETS[(index+sign+TARGETS.length)%TARGETS.length];
            }
            if(id==2)impact.strength=Math.max(0F,Math.min(200F,
                    impact.strength+sign*2F));
            if(id==3)impact.x=Math.max(-1F,Math.min(1F,impact.x+sign*.1F));
            if(id==4)impact.y=Math.max(-1F,Math.min(1F,impact.y+sign*.1F));
            if(id==5)impact.z=Math.max(-1F,Math.min(1F,impact.z+sign*.1F));
            if(id==6)impact.offsetX=Math.max(-1.5F,Math.min(1.5F,
                    impact.offsetX+sign*.1F));
            if(id==7)impact.offsetY=Math.max(-1.5F,Math.min(1.5F,
                    impact.offsetY+sign*.1F));
            if(id==8)impact.offsetZ=Math.max(-1.5F,Math.min(1.5F,
                    impact.offsetZ+sign*.1F));
            PhysicsChannels.setImpulse(t,f,impact);
            if(CharacterRagdollController.sampleWeight(t,f)<=0F)
                PhysicsChannels.setWeight(t,f,1F);
        }
        else if(id==9)
            PhysicsChannels.setGravity(t,f,
                    PhysicsChannels.gravityAt(t,f)+sign*.5F);
        else if(id==10||id==11)
        {
            PhysicsChannels.Fluid water=PhysicsChannels.waterAt(t,f);
            if(id==10)water.amount=Math.max(0F,Math.min(1F,
                    water.amount+sign*.1F));
            else water.buoyancy=Math.max(0F,Math.min(3F,
                    water.buoyancy+sign*.1F));
            PhysicsChannels.setWater(t,f,water);
        }
    }
    public boolean clickInspector(AnimationEditorScreen screen,
            int mx,int my,int button)
    {
        if(mx<panelX||mx>=panelX+185||my<panelY||my>=panelBottom)
            return false;
        if(button!=0)return true;
        int t=my-panelY;
        if(t>=33&&t<53)
        {
            int choice=(mx-panelX-7)/44;
            if(choice>=0&&choice<4)selectedRow=choice;
            return true;
        }
        if(my>=panelBottom-33&&my<panelBottom-14&&
                mx>=panelX+10&&mx<panelX+174)
        {
            authorDefault(screen,selectedRow);
            return true;
        }
        int offset=panelY+85;
        if(selectedRow==PhysicsChannels.RAGDOLL&&
                my>=offset+53&&my<offset+66)
        {
            PhysicsChannels.setWeight(track(screen),frame(screen),
                    Math.max(0F,Math.min(1F,
                            (mx-panelX-10)/164F)));
            return true;
        }
        if(selectedRow==PhysicsChannels.GRAVITY&&
                my>=offset+52&&my<offset+64)
        {
            PhysicsChannels.setGravity(track(screen),frame(screen),
                    Math.max(0F,Math.min(30F,
                            (mx-panelX-10)*30F/164F)));
            return true;
        }
        if(selectedRow==PhysicsChannels.WATER&&
                my>=offset+73&&my<offset+81)
        {
            PhysicsChannels.Fluid water=PhysicsChannels.waterAt(
                    track(screen),frame(screen));
            water.amount=Math.max(0F,Math.min(1F,
                    (mx-panelX-10)/164F));
            PhysicsChannels.setWater(track(screen),frame(screen),water);
            return true;
        }
        if(selectedRow==PhysicsChannels.GRAVITY&&
                my>=offset+110&&my<offset+158)
        {
            int index=((my-offset-110)/24)*2+
                    ((mx-panelX)<95?0:1);
            if(index>=0&&index<4)
                PhysicsChannels.setGravity(track(screen),frame(screen),
                        GRAVITY_PRESETS[index]);
            return true;
        }
        if(selectedRow==PhysicsChannels.WATER&&
                my>=offset+151&&my<offset+195)
        {
            PhysicsChannels.Fluid water=
                    PhysicsChannels.waterAt(track(screen),frame(screen));
            float sign=mx<panelX+92?-1F:1F;
            if(my<offset+171)
                water.drag=Math.max(0F,Math.min(5F,water.drag+sign*.1F));
            else
                water.angularDrag=Math.max(0F,Math.min(5F,
                        water.angularDrag+sign*.1F));
            PhysicsChannels.setWater(track(screen),frame(screen),water);
            return true;
        }
        for(int i=0;i<hitCount;i++)
        {
            int y=hitRows[i];
            if(my>=y&&my<y+19)
            {
                if(mx>=panelX+117&&mx<panelX+138)
                    edit(screen,hitValues[i],-1);
                else if(mx>=panelX+147&&mx<panelX+168)
                    edit(screen,hitValues[i],1);
                return true;
            }
        }
        return true;
    }
    /** PHYSICS intentionally has no interpolation inspector. */
    public void drawLeftGuide(AnimationEditorScreen screen)
    {
        Minecraft mc=Minecraft.getMinecraft();
        int y=AnimationEditorScreen.TOP_BAR_HEIGHT+
                AnimationEditorScreen.ACTOR_PANEL_HEIGHT;
        int bottom=screen.height-TIMELINE_HEIGHT;
        if(bottom-y<78)return;
        rect(0,y,GUTTER,bottom,EditorVisualStyle.themedSurface(.07F));
        rect(0,y,GUTTER,y+1,EditorVisualStyle.themedSurface(.27F));
        text(mc,"PHYSICS TOOLS",10,y+11,0xFFE2CFEC);
        text(mc,"4 independent key tracks",10,y+34,0xFF9DAFB9);
        text(mc,"Double click: add key",10,y+52,0xFF9DAFB9);
        text(mc,"Drag key: move frame",10,y+69,0xFF9DAFB9);
        if(bottom-y>112)
            text(mc,"Right click: delete key",10,y+86,0xFF9DAFB9);
        if(bottom-y>155)
        {
            text(mc,"IMPULSE",10,y+119,0xFFFFBD83);
            text(mc,"Drag orange tip: aim",10,y+136,0xFF9DAFB9);
            text(mc,"Drag pale center: hit",10,y+151,0xFF9DAFB9);
        }
        if(bottom-y>216)
        {
            text(mc,"WATER",10,y+189,0xFF7EDFC9);
            text(mc,"Immersion 0..100%",10,y+206,0xFF9DAFB9);
        }
    }

    /** Project an arrow anchored to the selected actor into the preview. */
    public void drawImpulseGizmo(AnimationEditorScreen screen,
            int vx,int vy,int vw,int vh)
    {
        gizmoEndX=-1000;gizmoEndY=-1000;
        gizmoBaseX=-1000;gizmoBaseY=-1000;displayedImpulse=null;
        if(selectedRow!=PhysicsChannels.IMPULSE||track(screen)==null)return;
        BlockbusterRecordFrame record=screen.getCurrentRecordFrame();
        EditorCamera camera=screen.getPreviewInteractionCamera();
        if(record==null||camera==null)return;
        PhysicsChannels.Impact impact=selectedImpact(screen);
        double x=record.getX(),y=record.getY()+1.3D,z=record.getZ();
        double targetOffset=0D;
        if("head".equals(impact.target))targetOffset=.4D;
        if("pelvis".equals(impact.target))targetOffset=-.5D;
        if(impact.target.contains("leg")||impact.target.contains("shin"))
            targetOffset=-.7D;
        double bodyYaw=Math.toRadians(record.hasBodyYaw()?
                record.getBodyYaw():record.getYaw());
        double cy=Math.cos(bodyYaw),sy=Math.sin(bodyYaw);
        // Follow the actual dynamic segment, not just its standing
        // pose. This keeps the hit-point handle on a fallen body.
        float[] center=RagdollEmoticonsJoltBridge.previewBodyCenter(
                screen.getSelectedActorForTimeline(),impact.target);
        if(center!=null)
        {
            x+=cy*center[0]+sy*center[2];
            y=record.getY()+center[1];
            z+=-sy*center[0]+cy*center[2];
            targetOffset=0D;
        }
        double offX=cy*impact.offsetX+sy*impact.offsetZ;
        double offZ=-sy*impact.offsetX+cy*impact.offsetZ;
        double ox=x+offX,oy=y+targetOffset+impact.offsetY,
                oz=z+offZ;
        float norm=(float)Math.sqrt(impact.x*impact.x+
                impact.y*impact.y+impact.z*impact.z);
        if(norm<.001F)norm=1F;
        float[] start=PreviewCameraProjection.project(ox,oy,oz,
                camera.sample(),vx,vy,vw,vh);
        double directionX=cy*impact.x+sy*impact.z;
        double directionZ=-sy*impact.x+cy*impact.z;
        float[] end=PreviewCameraProjection.project(
                ox+directionX/norm,oy+impact.y/norm,oz+directionZ/norm,
                camera.sample(),vx,vy,vw,vh);
        if(start==null||end==null)return;
        if(start[0]<vx||start[0]>vx+vw||start[1]<vy||
                start[1]>vy+vh)return;
        int sx=(int)start[0],screenY=(int)start[1];
        gizmoBaseX=sx;gizmoBaseY=screenY;
        gizmoEndX=(int)end[0];gizmoEndY=(int)end[1];
        displayedImpulse=impact;
        drawLine(sx,screenY,gizmoEndX,gizmoEndY,0xFFF2A35D);
        rect(sx-4,screenY-4,sx+5,screenY+5,0xFFEAD2AC);
        rect(gizmoEndX-6,gizmoEndY-6,
                gizmoEndX+7,gizmoEndY+7,0xFFFF9255);
        text(Minecraft.getMinecraft(),fixed(impact.strength)+" N.s",
                gizmoEndX+9,gizmoEndY-5,0xFFFFC28B);
    }
    private void drawLine(int x1,int y1,int x2,int y2,int color)
    {
        int dx=x2-x1,dy=y2-y1,steps=Math.max(Math.abs(dx),Math.abs(dy));
        if(steps<1)return;
        if(steps>3000)return;
        for(int i=0;i<=steps;i++)
        {
            int x=x1+dx*i/steps,y=y1+dy*i/steps;
            rect(x-1,y-1,x+2,y+2,color);
        }
    }
    public boolean beginImpulseDrag(int mx,int my,int button)
    {
        if(button!=0||selectedRow!=PhysicsChannels.IMPULSE||
                displayedImpulse==null)return false;
        boolean arrow=Math.abs(mx-gizmoEndX)<=12&&
                Math.abs(my-gizmoEndY)<=12;
        boolean base=Math.abs(mx-gizmoBaseX)<=9&&
                Math.abs(my-gizmoBaseY)<=9;
        if(!arrow&&!base)return false;
        draggingImpulse=true;draggingPoint=!arrow;
        dragX=mx;dragY=my;
        return true;
    }
    public boolean dragImpulse(AnimationEditorScreen screen,int mx,int my)
    {
        if(!draggingImpulse)return false;
        CharacterTimelineController t=track(screen);
        if(t==null){draggingImpulse=false;return false;}
        PhysicsChannels.Impact impact=selectedImpact(screen);
        EditorCamera camera=screen.getPreviewInteractionCamera();
        if(camera==null)return false;
        double yaw=Math.toRadians(camera.sample().yaw);
        float dx=(mx-dragX)*.02F,dy=(dragY-my)*.02F;
        float worldX=(float)Math.cos(yaw)*dx;
        float worldZ=-(float)Math.sin(yaw)*dx;
        BlockbusterRecordFrame record=screen.getCurrentRecordFrame();
        double actorYaw=record==null?0D:Math.toRadians(
                record.hasBodyYaw()?record.getBodyYaw():record.getYaw());
        float localX=(float)(Math.cos(actorYaw)*worldX-
                Math.sin(actorYaw)*worldZ);
        float localZ=(float)(Math.sin(actorYaw)*worldX+
                Math.cos(actorYaw)*worldZ);
        if(draggingPoint)
        {
            impact.offsetX=Math.max(-1.5F,Math.min(1.5F,
                    impact.offsetX+localX));
            impact.offsetY=Math.max(-1.5F,Math.min(1.5F,
                    impact.offsetY+dy));
            impact.offsetZ=Math.max(-1.5F,Math.min(1.5F,
                    impact.offsetZ+localZ));
        }
        else
        {
            impact.x+=localX;
            impact.z+=localZ;
            impact.y+=dy;
        }
        float norm=(float)Math.sqrt(impact.x*impact.x+
                impact.y*impact.y+impact.z*impact.z);
        if(norm>.01F)
        {
            if(!draggingPoint)
            {
                impact.x/=norm;impact.y/=norm;impact.z/=norm;
            }
            PhysicsChannels.setImpulse(t,frame(screen),impact);
            if(CharacterRagdollController.sampleWeight(t,frame(screen))<=0F)
                PhysicsChannels.setWeight(t,frame(screen),1F);
        }
        dragX=mx;dragY=my;
        return true;
    }
    public boolean releaseImpulseDrag()
    {
        boolean was=draggingImpulse;
        draggingImpulse=false;draggingPoint=false;
        return was;
    }
}
