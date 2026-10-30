package com.example.examplemod;

import java.util.Arrays;

/**
 * Pure Java pass contract for OptiFine's deferred/composite DRAWBUFFERS
 * directives. These are shader outputs (gl_FragData0...N), NOT a request
 * to rewrite OptiFine's shared dfbColorTexturesFlip instance.
 *
 * The renderer binds the BACK texture of each requested colortex to a
 * dedicated FBO color attachment. A completed pass flips only the
 * configured targets. Its inputs always sample the FRONT textures, so
 * the same texture is never read and written simultaneously.
 *
 * Render targets and per-buffer flip overrides come from OptiFine Program
 * metadata, not guessed from an eight-color BSL layout.
 */
public final class CameraMirrorCompositePlan
{
    public final String name;
    public final int[] targets;
    public final boolean[] flip;

    private CameraMirrorCompositePlan(String name,int[] targets,
            boolean[] flip)
    {
        this.name=name;
        this.targets=targets;
        this.flip=flip;
    }

    /**
     * @param rawDrawBuffers OpenGL COLOR_ATTACHMENT0+buffer index, in
     * fragment output order. Null means all buffers in order.
     * @param overrides Optional per-colortex Boolean flags; null entries
     * follow OptiFine's default flip-on-write behavior.
     */
    public static CameraMirrorCompositePlan create(String name,
            int[] rawDrawBuffers,Boolean[] overrides,int colorCount)
    {
        if(name==null||name.trim().isEmpty())
            throw new IllegalArgumentException("Pass name required");
        if(colorCount<1||colorCount>8)
            throw new IllegalArgumentException("Invalid color count");
        int[] input=rawDrawBuffers;
        if(input==null)
        {
            input=new int[colorCount];
            for(int i=0;i<input.length;i++)input[i]=36064+i;
        }
        if(input.length==0||input.length>colorCount)
            throw new IllegalArgumentException("Invalid shader target count");
        int[] targets=new int[input.length];
        boolean[] flips=new boolean[input.length];
        boolean[] seen=new boolean[colorCount];
        for(int i=0;i<input.length;i++)
        {
            int t=input[i]-36064;
            if(t<0||t>=colorCount||seen[t])
                throw new IllegalArgumentException(
                        "Invalid or duplicate colortex: "+t);
            seen[t]=true;
            targets[i]=t;
            Boolean choice=overrides!=null&&t<overrides.length?
                    overrides[t]:null;
            flips[i]=choice==null||choice.booleanValue();
        }
        return new CameraMirrorCompositePlan(name,targets,flips);
    }

    @Override public String toString()
    {
        return name+" targets="+Arrays.toString(targets)+
                " flip="+Arrays.toString(flip);
    }
}
