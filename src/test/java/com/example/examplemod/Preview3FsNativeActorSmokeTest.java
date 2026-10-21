package com.example.examplemod;

import net.minecraft.client.Minecraft;

/**
 * Java 8 state-machine test only. Runtime depth/shader test remains separate.
 */
public final class Preview3FsNativeActorSmokeTest
{
    private static void check(boolean expected,String msg)
    {
        if(!expected)throw new AssertionError(msg);
    }

    public static void main(String[] args)
    {
        System.setProperty("bbs.editor.preview3.nativeActors","true");
        check(BbsFsNativeActorPass.isRequested(),"opt-in wasn't read");

        Minecraft mc = new Minecraft();
        EditorSceneViewport viewport = new EditorSceneViewport();
        AnimationEditorScreen screen = new AnimationEditorScreen();

        BbsFsNativeActorPass.beginFrame();
        check(!BbsFsNativeActorPass.didRenderThisFrame(),
                "frame had actors before native event");
        BbsFsNativeActorPass.render(mc,screen,viewport,0F);
        check(BbsFsNativeActorPass.didRenderThisFrame(),
                "native event did not own actor frame");
        check(screen.renders==1,"first world event did not call renderer");

        BbsFsNativeActorPass.render(mc,screen,viewport,0F);
        check(screen.renders==1,"actor simulation was invoked twice");

        BbsFsNativeActorPass.beginFrame();
        check(!BbsFsNativeActorPass.didRenderThisFrame(),
                "native actor ownership leaked across frames");
        viewport.width=0;
        BbsFsNativeActorPass.render(mc,screen,viewport,0F);
        check(screen.renders==1,"invalid film viewport rendered actors");
        check(!BbsFsNativeActorPass.didRenderThisFrame(),
                "invalid film viewport stole GUI actor rendering");

        viewport.width=900;
        screen.shouldRender=false;
        BbsFsNativeActorPass.render(mc,screen,viewport,0F);
        check(!BbsFsNativeActorPass.didRenderThisFrame(),
                "failed scene preparation stole GUI actor rendering");

        screen.shouldRender=true;
        screen.throwOnRender=true;
        BbsFsNativeActorPass.render(mc,screen,viewport,0F);
        check(!BbsFsNativeActorPass.isRendering(),
                "exception leaked native world render flag");
        check(BbsFsNativeActorPass.didRenderThisFrame(),
                "exception would replay potentially advanced Jolt state");
        check(screen.renders==3,"unexpected number of native actor invocations");

        System.out.println("Preview FS native actor ownership and exception cleanup PASS");
    }
}
