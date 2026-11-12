package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exact 40-bone skirt visual-phase regression with Java 8, no JNI. */
public final class SkirtPoseVisualLeadSmokeTest
{
    public static void main(String[] args)
    {
        boundedLeadOnThreeOfFiveTickStep();
        noPredictionWithoutCompleteSkirt();
        rewindAndPauseDoesNotUseStalePhysics();
        widthStaysInAuthoredBounds();
        System.out.println("SkirtPoseVisualLeadSmokeTest OK");
    }

    private static void boundedLeadOnThreeOfFiveTickStep()
    {
        BodyPartPhysicsFrameState state=new BodyPartPhysicsFrameState();
        BodyPartModelData skirt=new BodyPartModelData();
        // 5-tick lunge; Jolt samples arrive only AFTER the actor draws.
        float[] motions={0F,.05F,.11F,.18F,.18F,.07F};
        for(int tick=0;tick<motions.length;tick++)
        {
            state.beginFrame(tick);
            if(tick>=2)
            {
                Map<String,float[]> frozen=state.forRender(skirt,tick);
                Map<String,float[]> lead=state.forRenderSkirtLead(skirt,tick);
                need(frozen!=null&&lead!=null,"physics preview missing");
                for(String key:frozen.keySet())
                {
                    float old=frozen.get(key)[3];
                    float advanced=lead.get(key)[3];
                    need(advanced>=old-.0001F,
                            "forward knee lunge must not reverse fabric");
                    need(advanced-old<=SkirtPoseVisualLead.MAX_STEP+.0002F,
                            "lead overshot maximum one-tick cloth bend");
                }
                Map<String,float[]> repeat=state.forRenderSkirtLead(skirt,tick);
                close(repeat.get("bone0")[3],lead.get("bone0")[3],
                        .00001F,"same actor frame must be stable");
            }
            addFrame(state,skirt,tick,motions[tick]);
            if(tick==0)state.publishEditedFrame(skirt,tick);
        }
    }

    private static void noPredictionWithoutCompleteSkirt()
    {
        Map<String,float[]> older=pose(.15F);
        Map<String,float[]> newest=pose(.18F);
        newest.remove("bone39");
        need(SkirtPoseVisualLead.swings(older,newest)==null,
                "should not predict incomplete 20x2 skirt");
    }

    private static void rewindAndPauseDoesNotUseStalePhysics()
    {
        BodyPartPhysicsFrameState state=new BodyPartPhysicsFrameState();
        BodyPartModelData skirt=new BodyPartModelData();
        state.beginFrame(0);
        addFrame(state,skirt,0,.2F);
        state.publishEditedFrame(skirt,0);
        state.beginFrame(1);
        addFrame(state,skirt,1,.25F);
        state.beginFrame(2);
        need(state.forRenderSkirtLead(skirt,2)!=null,
                "contiguous prediction missing");
        state.beginFrame(19);
        need(state.forRenderSkirtLead(skirt,19)==null,
                "timeline seek must invalidate prediction");
        addFrame(state,skirt,19,.01F);
        state.publishEditedFrame(skirt,19);
        close(state.forRenderSkirtLead(skirt,19).get("bone0")[3],
                .01F,.0001F,"paused snapshot must be its own frame");
        state.beginFrame(2);
        need(state.forRenderSkirtLead(skirt,2)==null,
                "rewind must clear prior frame prediction");
    }

    private static void widthStaysInAuthoredBounds()
    {
        Map<String,SkirtAdaptiveWidth.Width> old=
                new LinkedHashMap<String,SkirtAdaptiveWidth.Width>();
        Map<String,SkirtAdaptiveWidth.Width> latest=
                new LinkedHashMap<String,SkirtAdaptiveWidth.Width>();
        for(int i=0;i<40;i++)
        {
            float a=i<20?1.02F:1.12F;
            old.put("bone"+i,new SkirtAdaptiveWidth.Width(0,a));
            latest.put("bone"+i,new SkirtAdaptiveWidth.Width(0,a+.06F));
        }
        Map<String,SkirtAdaptiveWidth.Width> pred=
                SkirtPoseVisualLead.widths(old,latest);
        need(pred!=null&&pred.size()==40,"missing visual width lead");
        for(int i=0;i<40;i++)
        {
            float x=pred.get("bone"+i).multiplier;
            need(x>=1F&&x<=(i<20?1.07001F:1.28001F),
                    "predicted cloth expansion out of range");
        }
    }

    private static Map<String,float[]> pose(float rad)
    {
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        for(int i=0;i<40;i++)
            result.put("bone"+i,new float[]{1F,0F,0F,rad});
        return result;
    }

    private static void addFrame(BodyPartPhysicsFrameState state,
            BodyPartModelData model,int tick,float angle)
    {
        for(Map.Entry<String,float[]> entry:pose(angle).entrySet())
        {
            state.accept(model,tick,entry.getKey(),entry.getValue(),true);
            state.acceptWidth(model,tick,entry.getKey(),
                    new SkirtAdaptiveWidth.Width(0,1F));
        }
    }

    private static void need(boolean yes,String label)
    {if(!yes)throw new AssertionError(label);}
    private static void close(float actual,float expected,float epsilon,String label)
    {need(Math.abs(actual-expected)<=epsilon,
            label+" expected "+expected+" got "+actual);}
}
