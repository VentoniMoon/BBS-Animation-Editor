package net.minecraftforge.common;
public final class MinecraftForge {
    public static final EventBus EVENT_BUS=new EventBus();
    public static class EventBus {
        public Object registered;
        public void register(Object object){registered=object;}
    }
}
