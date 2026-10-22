package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Persisted scene-wide camera output target. This is the sole source of
 * width/height for the film preview and, later, a frame/video exporter.
 * Resolution changes never modify the camera's position, FOV or clip keys.
 */
public final class CameraOutputSettings
{
    public static final int MIN_SIDE=128;
    public static final int MAX_SIDE=8192;
    public static final int DEFAULT_WIDTH=1920;
    public static final int DEFAULT_HEIGHT=1080;

    public static final class Preset
    {
        public final String group;
        public final String label;
        public final int width;
        public final int height;

        public Preset(String group,String label,int width,int height)
        {
            this.group=group;
            this.label=label;
            this.width=width;
            this.height=height;
        }
        public String dimensions(){return width+" x "+height;}
    }

    public static final List<Preset> PRESETS;
    static
    {
        List<Preset> all=new ArrayList<Preset>();
        all.add(new Preset("16:9 LANDSCAPE","HD 720p",1280,720));
        all.add(new Preset("16:9 LANDSCAPE","Full HD 1080p",1920,1080));
        all.add(new Preset("16:9 LANDSCAPE","QHD 1440p",2560,1440));
        all.add(new Preset("16:9 LANDSCAPE","4K UHD",3840,2160));
        all.add(new Preset("9:16 PORTRAIT","HD Vertical",720,1280));
        all.add(new Preset("9:16 PORTRAIT","Full HD Vertical",1080,1920));
        all.add(new Preset("9:16 PORTRAIT","4K Vertical",2160,3840));
        all.add(new Preset("CINEMA","Ultrawide 21:9",2560,1080));
        all.add(new Preset("CINEMA","Scope 2.39:1",1920,804));
        all.add(new Preset("OTHER","Classic 4:3",1440,1080));
        all.add(new Preset("OTHER","Square 1:1",1080,1080));
        PRESETS=Collections.unmodifiableList(all);
    }

    private int width=DEFAULT_WIDTH;
    private int height=DEFAULT_HEIGHT;

    public int getWidth(){return width;}
    public int getHeight(){return height;}
    public double aspect(){return width/(double)height;}

    public void reset()
    {
        width=DEFAULT_WIDTH;
        height=DEFAULT_HEIGHT;
    }

    /** Reject impractical dimensions and odd encoder-unfriendly sizes. */
    public static boolean isValid(int w,int h)
    {
        return w>=MIN_SIDE&&h>=MIN_SIDE&&
                w<=MAX_SIDE&&h<=MAX_SIDE&&
                (w&1)==0&&(h&1)==0&&
                w/(double)h>=0.20D&&w/(double)h<=5D;
    }

    public boolean set(int w,int h)
    {
        if(!isValid(w,h))return false;
        boolean changed=w!=width||h!=height;
        width=w;height=h;
        return changed;
    }

    public boolean matches(Preset preset)
    {
        return preset!=null&&preset.width==width&&preset.height==height;
    }

    public String aspectLabel()
    {
        int gcd=gcd(width,height);
        return (width/gcd)+":"+(height/gcd);
    }

    /** Preset-label fallback for the custom size picker. */
    public String summary()
    {
        for(Preset p:PRESETS)if(matches(p))return p.label;
        return String.format(Locale.ROOT,"%d x %d (custom)",width,height);
    }

    public NBTTagCompound toNBT()
    {
        NBTTagCompound tag=new NBTTagCompound();
        tag.setInteger("Width",width);
        tag.setInteger("Height",height);
        return tag;
    }

    public void readNBT(NBTTagCompound tag)
    {
        reset();
        if(tag==null||!tag.hasKey("Width",3)||!tag.hasKey("Height",3))
            return;
        set(tag.getInteger("Width"),tag.getInteger("Height"));
    }

    /**
     * Fit the specified aspect inside available GUI space with centered
     * margins. Coordinates are GUI pixels, independent of export pixels.
     * This never stretches the recorded world: world capture uses the
     * fitted viewport's aspect to crop the source correctly.
     */
    public int[] fit(int x,int y,int availableWidth,int availableHeight)
    {
        int aw=Math.max(1,availableWidth);
        int ah=Math.max(1,availableHeight);
        double target=aspect();
        int fw,fh;
        if(aw/(double)ah>target)
        {
            fh=ah;
            fw=Math.max(1,Math.min(aw,(int)Math.floor(ah*target)));
        }
        else
        {
            fw=aw;
            fh=Math.max(1,Math.min(ah,(int)Math.floor(aw/target)));
        }
        return new int[]{x+(aw-fw)/2,y+(ah-fh)/2,fw,fh};
    }

    private static int gcd(int a,int b)
    {
        while(b!=0){int n=a%b;a=b;b=n;}
        return Math.max(1,a);
    }
}
