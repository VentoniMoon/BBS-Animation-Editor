package net.minecraftforge.fml.common;
public final class Loader {
    public static boolean available;
    public static boolean isModLoaded(String name){
        return available&&"better_lights".equals(name);
    }
}
