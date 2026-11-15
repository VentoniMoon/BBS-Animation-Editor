package net.minecraft.util;
public enum EnumParticleTypes
{
    EXPLOSION_NORMAL("explode"),FLAME("flame"),
    CRIT("crit"),BLOCK_CRACK("blockcrack"),
    SMOKE_NORMAL("smoke");
    private final String id;
    EnumParticleTypes(String n){id=n;}
    public String getParticleName(){return id;}
    public static EnumParticleTypes getByName(String name)
    {
        for(EnumParticleTypes t:values())
            if(t.id.equals(name))return t;
        return null;
    }
}
