package com.example.examplemod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.client.multiplayer.WorldClient;

/**
 * Exact vanilla-facing data keys of Blockbuster 2.7.1 ParticleMorph.
 * Preserve unknown NBT (including nested MORPH mode data) on every edit.
 * Emitters are editor sources, not a copied EntityActor rendered twice.
 */
public final class ParticleEmitterConfig
{
    public static final String NAME="particle";
    public static final String DEFAULT_TYPE="explode";
    public static final String[] NUMBERS={
            "Frequency","Duration","Delay","Cap","Speed","Count",
            "X","Y","Z","DX","DY","DZ"};
    private static final List<String> TYPES=discover();
    private ParticleEmitterConfig(){}

    public static NBTTagCompound defaults()
    {
        NBTTagCompound t=new NBTTagCompound();
        t.setString("Name",NAME);
        t.setString("Type",EnumParticleTypes.EXPLOSION_NORMAL.getParticleName());
        t.setInteger("Frequency",2);
        t.setInteger("Duration",-1);
        t.setInteger("Delay",0);
        t.setInteger("Cap",2500);
        t.setDouble("Speed",0.1D);
        t.setInteger("Count",10);
        for(String s:new String[]{"X","Y","Z"})
            t.setDouble(s,0D);
        for(String s:new String[]{"DX","DY","DZ"})
            t.setDouble(s,0.1D);
        t.setBoolean("LocalRotation",true);
        return t;
    }
    public static NBTTagCompound normalize(NBTTagCompound source)
    {
        NBTTagCompound d=defaults();
        if(source==null)return d;
        // Keep native keys, nested Morph, movement and future properties.
        NBTTagCompound merged=source.copy();
        for(String name:d.getKeySet())
            if(!merged.hasKey(name))merged.setTag(name,d.getTag(name).copy());
        merged.setString("Name",NAME);
        return merged;
    }
    public static boolean isParticle(NBTTagCompound tag)
    {
        return tag!=null&&NAME.equals(tag.getString("Name"));
    }
    public static List<String> types(){return TYPES;}
    private static List<String> discover()
    {
        List<String> all=new ArrayList<String>();
        for(EnumParticleTypes type:EnumParticleTypes.values())
            if(type!=null&&type.getParticleName()!=null)
                all.add(type.getParticleName());
        Collections.sort(all,String.CASE_INSENSITIVE_ORDER);
        return Collections.unmodifiableList(all);
    }
    public static String type(NBTTagCompound tag)
    {
        String name=normalize(tag).getString("Type");
        return EnumParticleTypes.getByName(name)!=null?name:
                EnumParticleTypes.EXPLOSION_NORMAL.getParticleName();
    }
    public static boolean changeType(NBTTagCompound tag,String name)
    {
        if(tag==null||EnumParticleTypes.getByName(name)==null)return false;
        tag.setString("Type",name);return true;
    }
    public static int intValue(NBTTagCompound tag,String name)
    {
        NBTTagCompound d=defaults();
        return tag!=null&&tag.hasKey(name)?tag.getInteger(name):
                d.getInteger(name);
    }
    public static double value(NBTTagCompound tag,String name)
    {
        NBTTagCompound d=defaults();
        return tag!=null&&tag.hasKey(name)?tag.getDouble(name):
                d.getDouble(name);
    }
    public static boolean rotation(NBTTagCompound tag)
    {
        return tag==null||!tag.hasKey("LocalRotation")||
                tag.getBoolean("LocalRotation");
    }
    public static boolean update(NBTTagCompound tag,String name,
            double value)
    {
        if(tag==null||!Double.isFinite(value)||Math.abs(value)>1E8D)
            return false;
        switch(name)
        {
            case "Frequency":
            case "Count":
                if(value<1D||value>100000D)return false;
                tag.setInteger(name,(int)Math.round(value));return true;
            case "Duration":
                if(value< -1D||value>1000000D)return false;
                tag.setInteger(name,(int)Math.round(value));return true;
            case "Delay":
            case "Cap":
                if(value<0D||value>1000000D)return false;
                tag.setInteger(name,(int)Math.round(value));return true;
            case "Speed":
            case "DX":
            case "DY":
            case "DZ":
                if(value<0D||value>100D)return false;
                tag.setDouble(name,value);return true;
            case "X":
            case "Y":
            case "Z":
                if(Math.abs(value)>30000000D)return false;
                tag.setDouble(name,value);return true;
            default:return false;
        }
    }
    public static String args(NBTTagCompound tag)
    {
        int[] a=tag==null?new int[0]:tag.getIntArray("Args");
        StringBuilder s=new StringBuilder();
        for(int value:a)
        {
            if(s.length()>0)s.append(", ");
            s.append(value);
        }
        return s.toString();
    }
    public static void setArgs(NBTTagCompound tag,String source)
    {
        if(tag==null)return;
        String text=source==null?"":source.trim();
        if(text.isEmpty()){tag.setIntArray("Args",new int[0]);return;}
        String[] input=text.split(",");
        if(input.length>8)return;
        int[] result=new int[input.length];
        try
        {
            for(int i=0;i<input.length;i++)
                result[i]=Integer.parseInt(input[i].trim());
            tag.setIntArray("Args",result);
        }
        catch(NumberFormatException ignored){}
    }
    /** Spawn same vanilla parameters as ParticleMorph.update(), without
     * its dependent EntityActor. This is once per editor tick and source. */
    public static void emit(WorldClient world,NBTTagCompound tag,
            double x,double y,double z,double yawDegrees,
            Random random,int elapsed)
    {
        emit(world,tag,x,y,z,yawDegrees,0D,0D,random,elapsed);
    }
    /** Full actor/local orientation: Z roll, X pitch, Y yaw. The
     * native particle-relative-rotation flag affects spawn dispersion,
     * not the emitter's authored world-coordinate X/Y/Z offset.
     */
    public static void emit(WorldClient world,NBTTagCompound tag,
            double x,double y,double z,double yawDegrees,
            double pitchDegrees,double rollDegrees,
            Random random,int elapsed)
    {
        if(world==null||tag==null||random==null||elapsed<0||
                !"VANILLA".equalsIgnoreCase(
                        tag.hasKey("Mode")?tag.getString("Mode"):"vanilla"))
            return;
        int frequency=Math.max(1,intValue(tag,"Frequency"));
        int delay=Math.max(0,intValue(tag,"Delay"));
        int duration=intValue(tag,"Duration");
        int count=Math.min(128,Math.max(1,intValue(tag,"Count")));
        int cap=Math.max(0,intValue(tag,"Cap"));
        if(elapsed<delay||duration>=0&&elapsed>=duration||
                elapsed%frequency!=0||20D/frequency*count>cap)return;
        EnumParticleTypes particle=EnumParticleTypes.getByName(type(tag));
        if(particle==null)return;
        double ox=value(tag,"X"),oy=value(tag,"Y"),oz=value(tag,"Z");
        double dx=value(tag,"DX"),dy=value(tag,"DY"),
                dz=value(tag,"DZ");
        double speed=value(tag,"Speed");
        boolean rotate=rotation(tag);
        double yaw=Math.toRadians(yawDegrees),c=Math.cos(yaw),
                s=Math.sin(yaw);
        double pitch=Math.toRadians(pitchDegrees),
                cp=Math.cos(pitch),sp=Math.sin(pitch);
        double roll=Math.toRadians(rollDegrees),
                cr=Math.cos(roll),sr=Math.sin(roll);
        int[] args=tag.getIntArray("Args");
        for(int i=0;i<count;i++)
        {
            double rx=random.nextGaussian()*dx,
                    ry=random.nextGaussian()*dy,
                    rz=random.nextGaussian()*dz;
            if(rotate)
            {
                double xx=rx*cr-ry*sr,yy=rx*sr+ry*cr;
                double y2=yy*cp-rz*sp,z2=yy*sp+rz*cp;
                double a=xx*c-z2*s,b=xx*s+z2*c;
                rx=a;ry=y2;rz=b;
            }
            try
            {
                world.spawnParticle(particle,true,x+ox+rx,y+oy+ry,
                        z+oz+rz,random.nextGaussian()*speed,
                        random.nextGaussian()*speed,
                        random.nextGaussian()*speed,args);
            }
            catch(RuntimeException unsupported)
            {
                // Invalid block/item Args must not break editor rendering.
            }
        }
    }
}
