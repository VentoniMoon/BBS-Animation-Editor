package com.example.examplemod;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Non-destructive case-insensitive scene-name filtering.
 *
 * Return ORIGINAL scene-list indices: AnimationEditorScreen.loadScene(int)
 * accepts the original index, never a position in the filtered menu.
 * This class has no Minecraft dependencies and is Java 8 compatible.
 */
public final class SceneDropdownSearch
{
    private SceneDropdownSearch() {}

    public static List<Integer> filter(List<File> scenes,String query)
    {
        if(scenes==null||scenes.isEmpty())
            return Collections.emptyList();

        String needle=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        List<Integer> result=new ArrayList<Integer>();
        for(int i=0;i<scenes.size();i++)
        {
            File scene=scenes.get(i);
            if(scene!=null &&
                    (needle.isEmpty() ||
                     scene.getName().toLowerCase(Locale.ROOT).contains(needle)))
                result.add(Integer.valueOf(i));
        }
        return result;
    }

    public static int limitOffset(int offset,int count,int visibleRows)
    {
        return Math.max(0,Math.min(Math.max(0,count-visibleRows),offset));
    }
}
