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
    private static final int HEADER=35,ROW=28;
    private static final String[] TARGETS={
        "pelvis","chest","head","left_upper_arm","right_upper_arm",
        "left_forearm","right_forearm","left_thigh","right_thigh",
        "left_shin","right_shin"
    };
    private static final String[] TARGET_DISPLAY={
        "PELVIS","CHEST","HEAD","LEFT ARM","RIGHT ARM",
        "LEFT FOREARM","RIGHT FOREARM","LEFT THIGH","RIGHT THIGH",
        "LEFT SHIN","RIGHT SHIN"
    };
    private static final float[] GRAVITY_PRESETS={9.81F,1.62F,3.71F,0F};
    private static final String[] PRESET_NAMES={"EARTH","MOON","MARS","ZERO"};
    private final EditorTimeline timeline;
    private int selectedRow=PhysicsChannels.RAGDOLL;
    private long lastClick;
    private int lastClickRow=-1,lastClickFrame=-1;
    private int gizmoEndX=-1000,gizmoEndY=-1000;
    private boolean draggingImpulse;
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
    private int keyColor(int row)
    {
        return row==PhysicsChannels.RAGDOLL?0xFFBD80F2:
                row==PhysicsChannels.IMPULSE?0xFFFFA064:
                row==PhysicsChannels.GRAVITY?0xFF7ECAFF:0xFF67D7CC;
    }
    public void drawTimeline(AnimationEditorScreen screen,int sceneLength)
    {
        Minecraft mc=Minecraft.getMinecraft();
        int width=screen.width,height=screen.height,top=height-TIMELINE_HEIGHT;
        int background=EditorVisualStyle.themedSurface(.08F);
        rect(0,top,width,height,background);
        rect(0,top,width,top+2,accent());
        rect(0,top,GUTTER,height,EditorVisualStyle.themedSurface(.11F));
        rect(GUTTER-1,top,GUTTER,height,EditorVisualStyle.themedSurface(.27F));
        rect(0,top+HEADER-1,width,top+HEADER,
                EditorVisualStyle.themedSurface(.28F));
        text(mc,"PHYSICS / KEYFRAMES",9,top+10,0xFFE3D8EE);

        // Reuse Character's twenty-tick ruler and grid density.
        for(int f=0;f<Math.max(1,sceneLength);f++)
        {
            int x=timeline.getFrameX(f,GUTTER);
            if(x<GUTTER||x>=width)continue;
            boolean major=f%20==0;
            if(f%5!=0&&timeline.getPixelsPerFrame()<4F)continue;
            rect(x,top+22,x+1,height,major?
                    EditorVisualStyle.themedSurface(.29F):
                    EditorVisualStyle.themedSurface(.17F));
            if(major)text(mc,Integer.toString(f),x+2,top+8,0xFFA9B5C2);
        }
        CharacterTimelineController t=track(screen);
        List<CharacterKey> keys=PhysicsChannels.keys(t);
        for(int row=0;row<4;row++)
        {
            int y=top+HEADER+row*ROW;
            int bg=row==selectedRow?
                    EditorVisualStyle.themedSurface(.22F):
                    EditorVisualStyle.themedSurface(row%2==0?.13F:.10F);
            rect(0,y,width,y+ROW,bg);
            rect(0,y+ROW-1,width,y+ROW,
                    EditorVisualStyle.themedSurface(.25F));
            if(row==selectedRow)rect(0,y,3,y+ROW,accent());
            text(mc,PhysicsChannels.LABELS[row],11,y+9,
                    row==selectedRow?0xFFF5E8FF:0xFFB7C0C9);
            for(CharacterKey key:keys)
            {
                if(key==null||!PhysicsChannels.has(key,row))continue;
                int x=timeline.getFrameX(key.getFrame(),GUTTER);
                if(x<GUTTER-8||x>width+8)continue;
                rect(x-4,y+7,x+5,y+19,keyColor(row));
                rect(x-2,y+9,x+3,y+17,0xFFFFFFFF);
            }
        }
        int playX=timeline.getFrameX(frame(screen),GUTTER);
        if(playX>=GUTTER&&playX<width)
        {
            rect(playX,top,playX+1,height,0xFFF4EEFF);
            rect(playX-3,top,playX+4,top+5,accent());
        }
        text(mc,"Double-click: add key    Right-click: remove channel",
                GUTTER+8,top+HEADER+4*ROW+5,0xFF8FA4AF);
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
        screen.setCameraWorkspaceFrame(f);
        CharacterTimelineController t=track(screen);
        if(t==null)return true;
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
            text(mc,"Physics 0% = animation",x+10,offset+64,0xFF97AAB5);
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
    /** Project an arrow anchored to the selected actor into the preview. */
    public void drawImpulseGizmo(AnimationEditorScreen screen,
            int vx,int vy,int vw,int vh)
    {
        gizmoEndX=-1000;gizmoEndY=-1000;displayedImpulse=null;
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
        double ox=x+impact.offsetX,oy=y+targetOffset+impact.offsetY,
                oz=z+impact.offsetZ;
        float norm=(float)Math.sqrt(impact.x*impact.x+
                impact.y*impact.y+impact.z*impact.z);
        if(norm<.001F)norm=1F;
        float[] start=PreviewCameraProjection.project(ox,oy,oz,
                camera.sample(),vx,vy,vw,vh);
        float[] end=PreviewCameraProjection.project(
                ox+impact.x/norm,oy+impact.y/norm,oz+impact.z/norm,
                camera.sample(),vx,vy,vw,vh);
        if(start==null||end==null)return;
        if(start[0]<vx||start[0]>vx+vw||start[1]<vy||
                start[1]>vy+vh)return;
        int sx=(int)start[0],sy=(int)start[1];
        gizmoEndX=(int)end[0];gizmoEndY=(int)end[1];
        displayedImpulse=impact;
        drawLine(sx,sy,gizmoEndX,gizmoEndY,0xFFF2A35D);
        rect(sx-4,sy-4,sx+5,sy+5,0xFFEAD2AC);
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
        if(Math.abs(mx-gizmoEndX)>12||Math.abs(my-gizmoEndY)>12)
            return false;
        draggingImpulse=true;dragX=mx;dragY=my;
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
        impact.x+=Math.cos(yaw)*dx;
        impact.z-=Math.sin(yaw)*dx;
        impact.y+=dy;
        float norm=(float)Math.sqrt(impact.x*impact.x+
                impact.y*impact.y+impact.z*impact.z);
        if(norm>.01F)
        {
            impact.x/=norm;impact.y/=norm;impact.z/=norm;
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
        draggingImpulse=false;
        return was;
    }
}
