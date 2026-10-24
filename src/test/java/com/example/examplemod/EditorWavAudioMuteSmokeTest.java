package com.example.examplemod;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import javax.sound.sampled.Clip;

/**
 * Does not depend on an audio device: test the editor monitor state and
 * seeking with an instrumented Java Sound Clip. No Lip Sync files get muted.
 */
public final class EditorWavAudioMuteSmokeTest
{
    private static void check(boolean valid,String description)
    {
        if(!valid)throw new AssertionError(description);
    }

    static class Sound implements InvocationHandler
    {
        boolean playing,closed;
        long current;
        int starts,stops;
        @Override public Object invoke(Object target,Method method,
                Object[] args)
        {
            String name=method.getName();
            if(name.equals("getMicrosecondLength"))return 10_000_000L;
            if(name.equals("getMicrosecondPosition"))return current;
            if(name.equals("setMicrosecondPosition"))
            {current=(Long)args[0];return null;}
            if(name.equals("start")){playing=true;starts++;return null;}
            if(name.equals("stop")){playing=false;stops++;return null;}
            if(name.equals("isRunning"))return playing;
            if(name.equals("close")){playing=false;closed=true;return null;}
            if(name.equals("isOpen"))return !closed;
            Class<?> type=method.getReturnType();
            if(type==boolean.class)return false;
            if(type==int.class)return 0;
            if(type==long.class)return 0L;
            if(type==float.class)return 0F;
            return null;
        }
    }

    public static void main(String[] args) throws Exception
    {
        File root=Files.createTempDirectory("bbs-wav-monitor-").toFile();
        new net.minecraft.client.Minecraft(root);
        EditorWavAudioController monitor=new EditorWavAudioController();
        check(monitor.isAudible(),"Original default remains WAV monitor ON");
        Sound driver=new Sound();
        Clip fake=(Clip)Proxy.newProxyInstance(Clip.class.getClassLoader(),
                new Class<?>[]{Clip.class},driver);
        Field field=EditorWavAudioController.class.getDeclaredField("clip");
        field.setAccessible(true);field.set(monitor,fake);
        monitor.update(0,true);
        check(driver.playing&&driver.starts==1,
                "Enabled monitoring plays local WAV on scene timeline");
        monitor.setAudible(false);
        check(!driver.playing,"Muting immediately stops existing playback");
        monitor.update(10,true);
        check(driver.starts==1,"Muted WAV never starts during playback");
        check(driver.current==0L,
                "Muted mode does not keep seeking/decode audio clips");

        EditorWavAudioController reopened=new EditorWavAudioController();
        reopened.restoreAudible();
        check(!reopened.isAudible(),"Monitor OFF persists across editor reload");
        monitor.setAudible(true);
        monitor.update(10,true);
        check(driver.starts==2,"Unmuting resumes local WAV playback");
        check(driver.current==500000L,
                "Unmute resynchronizes to the current 20 TPS frame");
        check(monitor.isLoaded(),"Mute never unloads the lip-sync clip");

        reopened.restoreAudible();
        check(reopened.isAudible(),"Monitor ON is restored too");
        monitor.unload();
        check(driver.closed,"Closing editor still releases Java Sound clip");
        System.out.println("WAV monitor toggle, persistence and resync PASS");
    }
}
