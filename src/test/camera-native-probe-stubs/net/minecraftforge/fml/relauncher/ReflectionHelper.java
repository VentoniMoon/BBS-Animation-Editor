package net.minecraftforge.fml.relauncher;
import java.lang.reflect.Field;
public final class ReflectionHelper {
  public static Field findField(Class<?> type,String... names) {
    for(String name:names) {
      try {
        Field f=type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
      } catch(NoSuchFieldException notFound){}
    }
    throw new IllegalArgumentException("No matching field");
  }
}
