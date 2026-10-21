package net.optifine.shaders;

import net.minecraft.entity.Entity;

public class Shaders
{
    public static boolean isRenderingWorld = true;
    public static final class Program
    {
        public final String id;
        public Program(String id){this.id=id;}
    }
    public static final Program prior = new Program("terrain");
    public static final Program entities = new Program("entities");
    public static Program activeProgram = prior;
    public static int beginCount, endCount, entityCount, restoreCount;

    public static void beginEntities()
    {
        beginCount++;
        activeProgram=entities;
    }
    public static void nextEntity(Entity entity)
    {
        if (activeProgram != entities)
            throw new AssertionError("record actor not in entity shader stage");
        entityCount++;
    }
    public static void endEntities()
    {
        endCount++;
    }
    public static void useProgram(Program program)
    {
        restoreCount++;
        activeProgram=program;
    }
}
