package com.example.examplemod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Editor-local UTF-8 language catalog.
 *
 * In Minecraft 1.12.2, Minecraft's I18n.format() may resolve a translated
 * label to the ENGLISH fallback (or to its key) even while the client UI
 * language is ru_ru. The font size can be correct while all custom editor
 * labels remain English. Read this mod's bundled Russian catalog directly
 * instead of depending on the global game's resource/locale reload order.
 *
 * This affects ONLY BBS Animation Editor UI. World names, morph identifiers,
 * shaders, filenames and the vanilla game's translations are untouched.
 */
public final class EditorLanguageCatalog
{
    private static final String PREFIX = "bbsanimationeditor.ui.";
    private static final String RUSSIAN =
            "assets/bbsanimationeditor/lang/ru_ru.lang";
    private static final Map<String,String> RU = read(RUSSIAN);

    private EditorLanguageCatalog(){}

    /** Returns null for missing keys, never the English fallback. */
    public static String russian(String key)
    {
        return key == null ? null : RU.get(key);
    }

    public static int russianEntryCount()
    {
        return RU.size();
    }

    private static Map<String,String> read(String path)
    {
        InputStream stream=EditorLanguageCatalog.class
                .getClassLoader().getResourceAsStream(path);
        if(stream==null)
        {
            System.err.println("[BBS Editor][Locale] Bundled catalog missing: "+path);
            return Collections.emptyMap();
        }
        Map<String,String> values=new HashMap<String,String>();
        try(BufferedReader reader=new BufferedReader(
                new InputStreamReader(stream,StandardCharsets.UTF_8)))
        {
            String line;
            while((line=reader.readLine())!=null)
            {
                // Ignore comments but preserve trailing spaces in translations
                // such as "СЦЕНА: " (used for dynamic prefixes).
                if(line.isEmpty()||line.charAt(0)=='#')continue;
                int split=line.indexOf('=');
                if(split<=0)continue;
                String key=line.substring(0,split).trim();
                String value=line.substring(split+1);
                if(key.startsWith(PREFIX)&&!value.trim().isEmpty())
                    values.put(key,value);
            }
        }
        catch(IOException error)
        {
            System.err.println("[BBS Editor][Locale] Could not load "+path
                    +": "+error.getMessage());
        }
        return Collections.unmodifiableMap(values);
    }
}
