package com.example.examplemod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * On-demand image textures for film IMAGE overlays.
 * All OpenGL interactions occur on the Minecraft client render thread.
 */
public final class CameraImageCache
{
    private static final int LIMIT=32;
    private static final Map<String,Entry> CACHE=new LinkedHashMap<String,Entry>(
            32,0.75F,true);
    private static final class Entry
    {
        final long modified;
        final DynamicTexture texture;
        final int width,height;
        Entry(long modified,DynamicTexture texture,int width,int height)
        {
            this.modified=modified;this.texture=texture;
            this.width=width;this.height=height;
        }
    }
    public static DynamicTexture get(String source)
    {
        if(source==null||source.trim().isEmpty())return null;
        File file=resolve(source);
        if(file==null||!file.isFile())return null;
        String key=file.getAbsolutePath();
        Entry existing=CACHE.get(key);
        if(existing!=null&&existing.modified==file.lastModified())
            return existing.texture;
        if(existing!=null)
        {
            existing.texture.deleteGlTexture();
            CACHE.remove(key);
        }
        BufferedImage image;
        try { image=ImageIO.read(file); }
        catch(IOException error){return null;}
        if(image==null||image.getWidth()<1||image.getHeight()<1||
                image.getWidth()>4096||image.getHeight()>4096)return null;
        DynamicTexture texture=new DynamicTexture(image);
        CACHE.put(key,new Entry(file.lastModified(),texture,
                image.getWidth(),image.getHeight()));
        while(CACHE.size()>LIMIT)
        {
            String first=CACHE.keySet().iterator().next();
            Entry removed=CACHE.remove(first);
            if(removed!=null)removed.texture.deleteGlTexture();
        }
        return texture;
    }
    /** Dimensions of the ORIGINAL decoded source, not the preview or FBO. */
    public static int[] size(String source)
    {
        if(get(source)==null)return null;
        File file=resolve(source);
        Entry entry=file==null?null:CACHE.get(file.getAbsolutePath());
        return entry==null?null:new int[]{entry.width,entry.height};
    }

    public static File resolve(String source)
    {
        if(source==null||source.trim().isEmpty())return null;
        File file=new File(source.trim());
        if(!file.isAbsolute())
            file=new File("config/bbs-editor/media",source.trim());
        return file;
    }
    public static void clear()
    {
        for(Entry entry:CACHE.values())entry.texture.deleteGlTexture();
        CACHE.clear();
    }
    private CameraImageCache(){}
}
