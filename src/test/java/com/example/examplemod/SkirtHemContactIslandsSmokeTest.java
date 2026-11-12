package com.example.examplemod;

/** Exact 20-panel contact-island allocation, pure Java 8. */
public final class SkirtHemContactIslandsSmokeTest
{
    public static void main(String[] args)
    {
        twoValidThighsOverlappingNineteenPanelsDoNotAbort();
        localSingleLegIsNotModified();
        inputOrderDoesNotChangePhysicalWindow();
        absentAndMalformedInputsFailClosed();
        System.out.println("SkirtHemContactIslandsSmokeTest OK");
    }

    private static void twoValidThighsOverlappingNineteenPanelsDoNotAbort()
    {
        Fixture f=new Fixture();
        // Measured case: two individually credible thigh groups create
        // almost ring-wide contact when their unions are combined.
        for(int i=0;i<10;i++)
        {
            f.pressure[SkirtHemContactIslands.LEFT][i]=.17F+
                    .12F*(1F-Math.abs(i-4.5F)/5F);
            f.depth[SkirtHemContactIslands.LEFT][i]=.12F;
        }
        for(int i=9;i<19;i++)
        {
            f.pressure[SkirtHemContactIslands.RIGHT][i]=.16F+
                    .13F*(1F-Math.abs(i-13.5F)/5F);
            f.depth[SkirtHemContactIslands.RIGHT][i]=.13F;
        }
        SkirtHemContactIslands.Decision d=f.choose();
        need(d!=null&&d.limited,"19-panel combined overlap must be bounded");
        need(d.candidatePanels==19,"unexpected source contact union "+d.candidatePanels);
        need(d.keptPanels>=8&&d.keptPanels<=10,
                "must preserve just two local 5-panel islands: "+d.keptPanels);
        need(d.keep[4]&&d.keep[14],
                "strongest local thigh contacts must still respond");
        need(!d.keep[0]&&!d.keep[18],
                "distant edge strips must not inflate");
        for(int i=0;i<20;i++)
            need(f.pressure[0][i]>=0F&&f.pressure[1][i]>=0F,
                    "selection mutated input");
    }

    private static void localSingleLegIsNotModified()
    {
        Fixture f=new Fixture();
        for(int i=0;i<4;i++)
        {
            f.pressure[0][i]=.22F;
            f.depth[0][i]=.08F;
        }
        SkirtHemContactIslands.Decision d=f.choose();
        need(d!=null&&!d.limited&&d.keptPanels==4,
                "small local contacts must retain exact former behavior");
        for(int i=0;i<20;i++)
            need(d.keep[i]==(i<4),
                    "unchanged local response changed mask");
    }

    private static void inputOrderDoesNotChangePhysicalWindow()
    {
        Fixture f=new Fixture();
        for(int i=0;i<20;i++)
        {
            int side=i<10?0:1;
            f.pressure[side][i]=.05F+.01F*(i%10);
            f.depth[side][i]=.12F;
        }
        SkirtHemContactIslands.Decision a=f.choose();
        Fixture shuffled=new Fixture();
        for(int i=0;i<20;i++)
        {
            int from=(i*7)%20;
            shuffled.names[i]=f.names[from];
            shuffled.roots[i]=f.roots[from].clone();
            for(int g=0;g<3;g++)
            {
                shuffled.pressure[g][i]=f.pressure[g][from];
                shuffled.depth[g][i]=f.depth[g][from];
            }
        }
        SkirtHemContactIslands.Decision b=shuffled.choose();
        need(a.limited&&b.limited,"expected overflow on two groups");
        for(int i=0;i<20;i++)
        {
            String name=f.names[i];
            boolean matched=false;
            for(int j=0;j<20;j++)
                if(shuffled.names[j].equals(name))
                {
                    matched=true;
                    need(a.keep[i]==b.keep[j],
                            "rotation/sort altered selected world panel "+name);
                    break;
                }
            need(matched,"missing source model panel");
        }
    }

    private static void absentAndMalformedInputsFailClosed()
    {
        Fixture f=new Fixture();
        f.depth[0][3]=Float.NaN;
        need(f.choose()==null,
                "invalid depth must not drive unstable contact math");
        need(SkirtHemContactIslands.sideOf("emoticons.low_left_leg")==0,
                "left lower leg owner classification");
        need(SkirtHemContactIslands.sideOf("emoticons.low_leg_right")==1,
                "right lower leg owner classification");
        need(SkirtHemContactIslands.sideOf("emoticons.leg")==2,
                "unknown owner should get bounded third group");
    }

    private static final class Fixture
    {
        final String[] names=new String[20];
        final float[][] roots=new float[20][3];
        final float[][] pressure=new float[3][20],depth=new float[3][20];
        Fixture()
        {
            for(int i=0;i<20;i++)
            {
                names[i]="bone"+i;
                double angle=2D*Math.PI*i/20;
                roots[i][0]=(float)Math.cos(angle)*.35F;
                roots[i][2]=(float)Math.sin(angle)*.35F;
            }
        }
        SkirtHemContactIslands.Decision choose()
        {return SkirtHemContactIslands.choose(names,roots,pressure,depth,12);}
    }
    private static void need(boolean ok,String reason)
    {if(!ok)throw new AssertionError(reason);}
}
