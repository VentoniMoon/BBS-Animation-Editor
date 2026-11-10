package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/**
 * CAMERA-style PHYSICS inspector. Timeline lanes choose the section;
 * there is no duplicate RAG/HIT/GRAV/WATER tab bar or redundant FRAME label.
 * Numeric controls use the same inset edit field, two arrows and Java 8
 * Minecraft GuiTextField behavior as CAMERA's inspector.
 */
public final class PhysicsInspectorPanel
{
    private static final String[] TARGETS={
        "pelvis","chest","head","left_upper_arm","right_upper_arm",
        "left_forearm","right_forearm","left_thigh","right_thigh",
        "left_shin","right_shin"
    };
    private static final String[] NAMES={
        "Таз","Грудь","Голова","Левая рука","Правая рука",
        "Лев. предплечье","Прав. предплечье","Лев. бедро",
        "Прав. бедро","Лев. голень","Прав. голень"
    };
    private static final String[] PRESETS={"EARTH","MOON","MARS","ZERO"};
    private static final float[] PRESET_G={9.81F,1.62F,3.71F,0F};
    private static final int W=185,ROW=18,POP_ROWS=6;
    private final PhysicsWorkspace workspace;
    private final List<RowHit> hitRows=new ArrayList<RowHit>();
    private final List<RowHit> presetHits=new ArrayList<RowHit>();
    private int left,top,bottom,scroll,contentHeight,section=-1;
    private String activeActor="";
    private GuiTextField input;
    private int inputId=-1,inputFrame=-1,inputSection=-1;
    private boolean targetOpen;
    private int targetScroll,targetTop;
    private int toggleY=-1,hitModeY=-1,footerY=-1;

    private static final class RowHit
    {
        final int id,x,y,w,h;
        RowHit(int id,int x,int y,int w,int h)
        {this.id=id;this.x=x;this.y=y;this.w=w;this.h=h;}
        boolean contains(int mx,int my)
        {return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    }

    public PhysicsInspectorPanel(PhysicsWorkspace workspace)
    {this.workspace=workspace;}

    private static int accent(){return EditorThemeManager.get().getAccent();}
    private static int bright(){return EditorThemeManager.get().getAccentBright();}
    private static void rect(int x,int y,int w,int h,int color)
    {if(w>0&&h>0)Gui.drawRect(x,y,x+w,y+h,color);}
    private static void label(String text,int x,int y,int color)
    {Minecraft.getMinecraft().fontRenderer.drawString(text,x,y,color);}
    private static String number(float value)
    {return String.format(Locale.ROOT,"%.3f",value);}
    private static float clamp(float value,float lo,float hi)
    {return Math.max(lo,Math.min(hi,value));}
    private static CharacterTimelineController timeline(AnimationEditorScreen s)
    {
        BlockbusterSceneActorData actor=s.getSelectedActorForTimeline();
        return actor==null?null:actor.getCharacterTimeline();
    }
    private static int frame(AnimationEditorScreen s){return s.getCurrentFrame();}
    private static String actorId(AnimationEditorScreen s)
    {
        BlockbusterSceneActorData actor=s.getSelectedActorForTimeline();
        return actor==null?"":actor.getId();
    }
    private PhysicsChannels.Impact impact(AnimationEditorScreen screen)
    {
        CharacterTimelineController t=timeline(screen);
        PhysicsChannels.Impact i=PhysicsChannels.impulseAt(t,frame(screen));
        if(i!=null)return i;
        i=PhysicsChannels.previousImpulse(t,frame(screen));
        return i==null?new PhysicsChannels.Impact():i;
    }
    private static String targetName(String id)
    {
        for(int i=0;i<TARGETS.length;i++)
            if(TARGETS[i].equals(id))return NAMES[i];
        return NAMES[1];
    }
    private void sectionLabel(String name,int y)
    {
        if(y+16>top+4&&y<bottom-32)
        {
            rect(left+5,y,W-11,16,EditorVisualStyle.themedSurface(.18F));
            rect(left+5,y,3,16,accent());
            label(name,left+13,y+4,0xFFE6DDED);
        }
    }
    private void numeric(int id,String title,float value,int y)
    {
        int x=left+91,w=W-101;
        if(y<top+7||y+16>=bottom-33)return;
        label(title,left+9,y+4,0xFFADBBC1);
        rect(x,y,w,16,EditorVisualStyle.themedSurface(.045F));
        rect(x,y+15,w,1,EditorVisualStyle.themedSurface(.20F));
        rect(x,y,14,16,EditorVisualStyle.themedSurface(.26F));
        rect(x+w-14,y,14,16,EditorVisualStyle.themedSurface(.26F));
        label("<",x+4,y+4,0xFFE6F0F3);
        label(">",x+w-10,y+4,0xFFE6F0F3);
        if(input!=null&&inputId==id&&inputSection==section)
        {
            input.x=x+15;input.y=y+1;input.width=w-30;
            input.height=14;
            input.drawTextBox();
        }
        else
        {
            String valueText=number(value);
            String visible=Minecraft.getMinecraft().fontRenderer
                    .trimStringToWidth(valueText,w-31);
            label(visible,x+17,y+4,0xFFE6F0F3);
        }
        hitRows.add(new RowHit(id,x,y,w,16));
    }
    private int start(AnimationEditorScreen screen,int newSection)
    {
        if(newSection!=section||!activeActor.equals(actorId(screen)))
        {
            // This editor never commits a typed value into another actor
            // or into another channel after switching selections.
            input=null;inputId=-1;targetOpen=false;scroll=0;
            section=newSection;activeActor=actorId(screen);
        }
        hitRows.clear();presetHits.clear();toggleY=-1;
        hitModeY=-1;footerY=bottom-25;
        return top+10-scroll;
    }
    private void smallButton(String text,int x,int y,int w,boolean active)
    {
        rect(x,y,w,18,active?EditorVisualStyle.themedSurface(.28F):
                EditorVisualStyle.themedSurface(.13F));
        rect(x,y+17,w,1,active?accent():
                EditorVisualStyle.themedSurface(.23F));
        label(text,x+5,y+5,active?0xFFEEE4F5:0xFFC4CBD0);
    }
    public void draw(AnimationEditorScreen screen,int row,boolean hitPoint,
            int x,int y,int b)
    {
        left=x;top=y;bottom=b;
        CharacterTimelineController track=timeline(screen);
        int cy=start(screen,row);
        if(track==null)
        {
            sectionLabel("PHYSICS",cy);
            label("Выберите актёра",left+10,cy+30,0xFF9DAFB9);
            return;
        }
        switch(row)
        {
        case PhysicsChannels.RAGDOLL:
            sectionLabel("RAGDOLL",cy);cy+=22;
            numeric(0,"Weight %",CharacterRagdollController.sampleWeight(
                    track,frame(screen))*100F,cy);cy+=ROW;
            if(cy+19<bottom-30)
                label("0% = анимация",left+10,cy+7,0xFF9AAAB4);
            cy+=30;
            break;
        case PhysicsChannels.IMPULSE:
            sectionLabel("IMPULSE",cy);cy+=22;
            PhysicsChannels.Impact i=impact(screen);
            if(cy>=top+8&&cy+16<bottom-32)
            {
                label("Target",left+9,cy+4,0xFFADBBC1);
                int fx=left+91;
                rect(fx,cy,84,16,EditorVisualStyle.themedSurface(.16F));
                rect(fx,cy+15,84,1,accent());
                String title=Minecraft.getMinecraft().fontRenderer
                        .trimStringToWidth(targetName(i.target),65);
                label(title,fx+3,cy+4,0xFFE6F0F3);
                label("v",fx+74,cy+4,bright());
                hitRows.add(new RowHit(1,fx,cy,84,16));
                targetTop=cy+18;
            }
            cy+=ROW;
            numeric(2,"Strength",i.strength,cy);cy+=ROW;
            sectionLabel("DIRECTION",cy);cy+=18;
            numeric(3,"Dir X",i.x,cy);cy+=ROW;
            numeric(4,"Dir Y",i.y,cy);cy+=ROW;
            numeric(5,"Dir Z",i.z,cy);cy+=ROW;
            sectionLabel("HIT POINT",cy);cy+=18;
            numeric(6,"Hit X",i.offsetX,cy);cy+=ROW;
            numeric(7,"Hit Y",i.offsetY,cy);cy+=ROW;
            numeric(8,"Hit Z",i.offsetZ,cy);cy+=ROW+7;
            if(cy+18<bottom-31)
            {
                hitModeY=cy;
                smallButton("DIRECTION",left+8,cy,82,!hitPoint);
                smallButton("HIT POINT",left+96,cy,79,hitPoint);
                cy+=22;
            }
            break;
        case PhysicsChannels.GRAVITY:
            sectionLabel("GRAVITY",cy);cy+=22;
            float gravity=PhysicsChannels.gravityAt(track,frame(screen));
            numeric(9,"Magnitude",gravity,cy);cy+=ROW+7;
            sectionLabel("PRESETS",cy);cy+=20;
            for(int p=0;p<4;p++)
            {
                int px=left+7+(p%2)*89;
                int py=cy+(p/2)*21;
                if(py+18>=bottom-32)continue;
                smallButton(PRESETS[p],px,py,84,
                        Math.abs(gravity-PRESET_G[p])<.001F);
                presetHits.add(new RowHit(p,px,py,84,18));
            }
            cy+=46;
            break;
        case PhysicsChannels.WATER:
            sectionLabel("WATER",cy);cy+=21;
            PhysicsChannels.Fluid f=PhysicsChannels.waterAt(track,frame(screen));
            numeric(10,"Amount",f.amount,cy);cy+=ROW;
            numeric(11,"Buoyancy",f.buoyancy,cy);cy+=ROW;
            numeric(12,"Drag",f.drag,cy);cy+=ROW;
            numeric(13,"Angular",f.angularDrag,cy);cy+=ROW+6;
            sectionLabel("WATER SURFACE",cy);cy+=20;
            numeric(14,"Surface Y",f.surfaceY,cy);cy+=ROW+5;
            if(cy+18<bottom-33)
            {
                toggleY=cy;
                smallButton("USE SURFACE",left+8,cy,166,f.surfaceEnabled);
                cy+=22;
            }
            if(cy+14<bottom-33)
                label(f.surfaceEnabled?"Surface controls immersion":
                        "Legacy whole-actor water",left+9,cy+3,0xFF98AAB5);
            cy+=15;
            break;
        default:break;
        }
        contentHeight=Math.max(0,cy+scroll-top+32);
        smallButton("ADD / UPDATE KEY",left+8,footerY,167,
                PhysicsChannels.has(PhysicsChannels.at(track,frame(screen)),row));
        // Popups render last, above other fields.
        if(targetOpen&&row==PhysicsChannels.IMPULSE)
        {
            int firstY=targetTop;
            int max=Math.min(POP_ROWS,TARGETS.length-targetScroll);
            for(int i=0;i<max;i++)
            {
                int option=targetScroll+i;
                int py=firstY+i*17;
                if(py<top+8||py+17>=bottom-32)break;
                rect(left+78,py,97,17,EditorVisualStyle.themedSurface(.33F));
                label(NAMES[option],left+82,py+4,0xFFE9EDF1);
            }
        }
    }
    private float value(AnimationEditorScreen screen,int id)
    {
        CharacterTimelineController t=timeline(screen);
        if(t==null)return 0F;
        PhysicsChannels.Impact i;
        PhysicsChannels.Fluid f;
        switch(id)
        {
        case 0:return CharacterRagdollController.sampleWeight(t,frame(screen))*100F;
        case 2:return impact(screen).strength;
        case 3:return impact(screen).x;
        case 4:return impact(screen).y;
        case 5:return impact(screen).z;
        case 6:return impact(screen).offsetX;
        case 7:return impact(screen).offsetY;
        case 8:return impact(screen).offsetZ;
        case 9:return PhysicsChannels.gravityAt(t,frame(screen));
        default:
            f=PhysicsChannels.waterAt(t,frame(screen));
            switch(id)
            {
            case 10:return f.amount;
            case 11:return f.buoyancy;
            case 12:return f.drag;
            case 13:return f.angularDrag;
            case 14:return f.surfaceY;
            default:return 0F;
            }
        }
    }
    private void write(AnimationEditorScreen screen,int id,float v)
    {
        CharacterTimelineController t=timeline(screen);
        if(t==null||!Float.isFinite(v))return;
        int frame=frame(screen);
        if(id==0)
            PhysicsChannels.setWeight(t,frame,clamp(v,0F,100F)/100F);
        else if(id==9)
            PhysicsChannels.setGravity(t,frame,clamp(v,0F,100F));
        else if(id>=2&&id<=8)
        {
            PhysicsChannels.Impact i=impact(screen);
            if(id==2)i.strength=clamp(v,0F,200F);
            if(id==3)i.x=clamp(v,-1F,1F);
            if(id==4)i.y=clamp(v,-1F,1F);
            if(id==5)i.z=clamp(v,-1F,1F);
            if(id==6)i.offsetX=clamp(v,-1.5F,1.5F);
            if(id==7)i.offsetY=clamp(v,-1.5F,1.5F);
            if(id==8)i.offsetZ=clamp(v,-1.5F,1.5F);
            PhysicsChannels.setImpulse(t,frame,i);
            if(CharacterRagdollController.sampleWeight(t,frame)<=0F)
                PhysicsChannels.setWeight(t,frame,1F);
        }
        else if(id>=10&&id<=14)
        {
            PhysicsChannels.Fluid f=PhysicsChannels.waterAt(t,frame);
            if(id==10)f.amount=clamp(v,0F,1F);
            if(id==11)f.buoyancy=clamp(v,0F,3F);
            if(id==12)f.drag=clamp(v,0F,5F);
            if(id==13)f.angularDrag=clamp(v,0F,5F);
            if(id==14){f.surfaceY=clamp(v,-2048F,2048F);
                f.surfaceEnabled=true;}
            PhysicsChannels.setWater(t,frame,f);
        }
        screen.markEditorDirty();
    }
    private static float step(int id,boolean keyboard)
    {
        if(id==0)return keyboard?3.125F:6.25F;
        if(id==2)return keyboard?.5F:1F;
        return keyboard?.03125F:.0625F;
    }
    private void nudge(AnimationEditorScreen s,int id,int direction,
            boolean keyboard)
    {
        write(s,id,value(s,id)+direction*step(id,keyboard));
    }
    private void beginInput(AnimationEditorScreen screen,int id,RowHit hit)
    {
        if(input!=null)commit(screen);
        inputId=id;inputSection=section;inputFrame=frame(screen);
        input=new GuiTextField(19561,Minecraft.getMinecraft().fontRenderer,
                hit.x+15,hit.y+1,hit.w-30,14);
        input.setMaxStringLength(32);
        input.setText(String.format(Locale.ROOT,"%.5f",value(screen,id)));
        input.setFocused(true);
    }
    public void commit(AnimationEditorScreen screen)
    {
        if(input==null)return;
        String typed=input.getText().trim().replace(',','.');
        int id=inputId;
        boolean same=frame(screen)==inputFrame&&
                section==inputSection&&activeActor.equals(actorId(screen));
        input=null;inputId=-1;
        if(!same)return;
        try{
            float v=Float.parseFloat(typed);
            if(Float.isFinite(v))write(screen,id,v);
        }catch(NumberFormatException ignored){}
    }
    public boolean keyTyped(AnimationEditorScreen screen,char c,int key)
    {
        if(input==null)return false;
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||
                key==Keyboard.KEY_TAB){commit(screen);return true;}
        if(key==Keyboard.KEY_ESCAPE)
        {input=null;inputId=-1;return true;}
        if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN)
        {
            int id=inputId;commit(screen);
            nudge(screen,id,key==Keyboard.KEY_UP?1:-1,true);
            // Keyboard editing stays active after each nudge.
            for(RowHit hit:hitRows)
                if(hit.id==id){beginInput(screen,id,hit);break;}
            return true;
        }
        input.textboxKeyTyped(c,key);
        return true;
    }
    public boolean scrollAt(int mx,int my,int direction)
    {
        if(mx<left||mx>=left+W||my<top||my>=bottom)return false;
        if(targetOpen)
        {
            targetScroll=Math.max(0,Math.min(TARGETS.length-POP_ROWS,
                    targetScroll+(direction>0?-1:1)));
            return true;
        }
        scroll=Math.max(0,Math.min(Math.max(0,contentHeight-
                (bottom-top-36)),scroll+(direction>0?-18:18)));
        return true;
    }
    public boolean click(AnimationEditorScreen screen,int mx,int my,int button)
    {
        if(mx<left||mx>=left+W||my<top||my>=bottom)return false;
        if(button!=0)return true;
        if(targetOpen)
        {
            int index=(my-targetTop)/17;
            if(mx>=left+78&&mx<left+175&&
                    my>=targetTop&&index>=0&&index<POP_ROWS)
            {
                int chosen=targetScroll+index;
                if(chosen<TARGETS.length)
                {
                    PhysicsChannels.Impact i=impact(screen);
                    i.target=TARGETS[chosen];
                    CharacterTimelineController t=timeline(screen);
                    if(t!=null)PhysicsChannels.setImpulse(t,frame(screen),i);
                    screen.markEditorDirty();
                }
                targetOpen=false;return true;
            }
            targetOpen=false;
        }
        if(my>=footerY&&my<footerY+18&&mx>=left+8&&mx<left+175)
        {
            commit(screen);
            workspace.authorDefault(screen,section);
            screen.markEditorDirty();
            return true;
        }
        for(RowHit hit:presetHits)
            if(hit.contains(mx,my))
            {
                commit(screen);
                write(screen,9,PRESET_G[hit.id]);
                return true;
            }
        if(toggleY>=0&&mx>=left+8&&mx<left+174&&
                my>=toggleY&&my<toggleY+18)
        {
            commit(screen);
            CharacterTimelineController t=timeline(screen);
            if(t!=null)
            {
                PhysicsChannels.Fluid f=PhysicsChannels.waterAt(t,frame(screen));
                f.surfaceEnabled=!f.surfaceEnabled;
                PhysicsChannels.setWater(t,frame(screen),f);
                screen.markEditorDirty();
            }
            return true;
        }
        if(hitModeY>=0&&mx>=left+8&&mx<left+175&&
                my>=hitModeY&&my<hitModeY+18)
        {
            commit(screen);
            workspace.selectHitPoint(mx>=left+96);
            return true;
        }
        for(RowHit hit:hitRows)
            if(hit.contains(mx,my))
            {
                if(hit.id==1)
                {
                    commit(screen);targetOpen=!targetOpen;
                    targetScroll=0;return true;
                }
                if(mx<hit.x+14){commit(screen);nudge(screen,hit.id,-1,false);}
                else if(mx>=hit.x+hit.w-14)
                {commit(screen);nudge(screen,hit.id,1,false);}
                else beginInput(screen,hit.id,hit);
                return true;
            }
        commit(screen);
        return true;
    }
}
