package com.example.examplemod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Persist external tool preferences independently of CAMERA scene/actor data.
 * The tool location is a machine setting, not a property of a film project.
 * This file can be reused by a dedicated Settings screen later.
 */
public final class CameraExportPreferences
{
    private static final String FILE="config/bbs-editor/camera-export.properties";
    private static final String FFMPEG="ffmpeg.path";

    public static String loadFfmpeg(File gameDirectory)
    {
        String fallback=System.getProperty("bbs.editor.export.ffmpeg","ffmpeg");
        if(gameDirectory==null)return fallback;
        File file=new File(gameDirectory,FILE);
        if(!file.isFile()||file.length()>65536L)return fallback;
        Properties properties=new Properties();
        try(FileInputStream input=new FileInputStream(file))
        {
            properties.load(input);
            String saved=properties.getProperty(FFMPEG,"").trim();
            return saved.isEmpty()?fallback:saved;
        }
        catch(IOException error)
        {
            System.err.println("[BBS CAMERA] Cannot read export preferences: "+
                    error.getMessage());
            return fallback;
        }
    }

    public static void saveFfmpeg(File gameDirectory,String path)
            throws IOException
    {
        if(gameDirectory==null||path==null||path.trim().isEmpty())
            throw new IOException("Missing FFmpeg preference path");
        String value=path.trim();
        if(value.length()>2048||value.indexOf('\n')>=0||value.indexOf('\r')>=0)
            throw new IOException("Invalid FFmpeg preference path");
        File target=new File(gameDirectory,FILE);
        File folder=target.getParentFile();
        if(!folder.isDirectory()&&!folder.mkdirs())
            throw new IOException("Cannot create CAMERA config directory");

        Properties props=new Properties();
        // Keep any future settings, even when an older editor saves just FFmpeg.
        if(target.isFile()&&target.length()<=65536L)
        {
            try(FileInputStream input=new FileInputStream(target))
            {props.load(input);}
        }
        props.setProperty(FFMPEG,value);
        File temp=File.createTempFile("camera-export-",".tmp",folder);
        try
        {
            try(FileOutputStream out=new FileOutputStream(temp))
            {props.store(out,"BBS Animation Editor: machine-local export settings");}
            try
            {
                Files.move(temp.toPath(),target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            catch(java.nio.file.AtomicMoveNotSupportedException fallback)
            {
                Files.move(temp.toPath(),target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally {if(temp.exists())temp.delete();}
    }

    private CameraExportPreferences(){}
}
