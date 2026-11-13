package com.example.examplemod;

/** Exact 20-panel contact-island allocation, pure Java 8. */
public final class SkirtHemContactIslandsSmokeTest
{
    public static void main(String[] args)
    {
        twoValidThighsOverlappingNineteenPanelsDoNotAbort();
        localSingleLegIsNotModified();
        strongContactExpandsPastFixedFivePanelWindows();
        shallowPeripheralContactsAreNotSelected();
        diffuseFullRingContactStaysBounded();
        legacyFivePanelABRemainsAvailable();
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
        need(d.keptPanels>10 &&
                d.keptPanels<=SkirtHemContactIslands.MAX_SELECTED_PANELS,
                "deep local thigh regions should expand without full ring: "+
                d.keptPanels);
        need(d.keep[4]&&d.keep[14],
                "strongest local thigh contacts must still respond");
        need(!d.keep[0]&&!d.keep[18]&&!d.keep[19],
                "weak distant edges must not trigger an umbrella");
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

    private static void strongContactExpandsPastFixedFivePanelWindows()
    {
        Fixture f=new Fixture();
        // The inner two-thirds of a wide step is strongly confirmed on
        // BOTH thighs; the old fixed 5+5 windows discarded real contacts.
        for(int i=1;i<=9;i++)
        {
            f.pressure[0][i]=.12F+(.03F*(1F-Math.abs(5-i)/5F));
            f.depth[0][i]=.13F;
        }
        for(int i=10;i<=18;i++)
        {
            f.pressure[1][i]=.13F+(.03F*(1F-Math.abs(14-i)/5F));
            f.depth[1][i]=.14F;
        }
        SkirtHemContactIslands.Decision d=f.choose();
        need(d!=null&&d.limited&&d.candidatePanels==18,
                "must recognize both legitimate wide regions");
        need(d.keptPanels>10&&
                d.keptPanels<=SkirtHemContactIslands.MAX_SELECTED_PANELS,
                "strong measured overlap cannot remain at fixed 10 selected");
        need(d.keep[5]&&d.keep[14],"both thigh contact centers are essential");
        need(!d.keep[0]&&!d.keep[19],
                "contact selection must not invent rear-panel pressure");
    }

    private static void shallowPeripheralContactsAreNotSelected()
    {
        Fixture f=new Fixture();
        for(int i=0;i<10;i++)
        {
            f.pressure[0][i]=.2F;
            f.depth[0][i]=(i>=2&&i<=7)?.14F:.003F;
        }
        for(int i=10;i<20;i++)
        {
            f.pressure[1][i]=.21F;
            f.depth[1][i]=(i>=12&&i<=17)?.14F:.003F;
        }
        SkirtHemContactIslands.Decision d=f.choose();
        need(d!=null&&d.candidatePanels==20,
                "all shallow witnesses should remain visible in audit");
        for(int i:new int[]{0,1,8,9,10,11,18,19})
            need(!d.keep[i],
                    "weak edge must not open skirt section "+i);
        for(int i:new int[]{3,4,5,13,14,15})
            need(d.keep[i],
                    "deep continuous contact cannot be discarded "+i);
        need(d.keptPanels<=12,
                "only true deep contacts should be selected");
    }

    private static void diffuseFullRingContactStaysBounded()
    {
        Fixture f=new Fixture();
        // A suspiciously uniform, nearly full-ring OBB union should
        // NOT receive the new nine-panel per-thigh allowance.
        for(int i=0;i<19;i++)
        {
            f.pressure[0][i]=.2F;
            f.depth[0][i]=.12F;
        }
        SkirtHemContactIslands.Decision d=f.choose();
        need(d!=null&&d.limited&&d.candidatePanels==19,
                "broad anomaly must still be measured");
        need(d.keptPanels<=5,
                "diffuse single-leg evidence must remain strictly local");
    }

    private static void legacyFivePanelABRemainsAvailable()
    {
        Fixture f=new Fixture();
        for(int i=0;i<10;i++)
        {
            f.pressure[0][i]=.2F;
            f.depth[0][i]=.12F;
        }
        for(int i=10;i<20;i++)
        {
            f.pressure[1][i]=.2F;
            f.depth[1][i]=.12F;
        }
        SkirtHemContactIslands.Decision old=
                SkirtHemContactIslands.choose(f.names,f.roots,
                        f.pressure,f.depth,12,false);
        SkirtHemContactIslands.Decision now=f.choose();
        need(old!=null&&old.keptPanels<=12,
                "explicit A/B off must retain original fixed windows");
        need(now!=null&&now.keptPanels>old.keptPanels &&
                now.keptPanels<=SkirtHemContactIslands.MAX_SELECTED_PANELS,
                "adaptive coverage must exceed old fixed five-panel budget");
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
