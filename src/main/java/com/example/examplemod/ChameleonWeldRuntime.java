package com.example.examplemod;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Map;

/**
 * Called from the REAL ChameleonRenderer.render(Model) after ChameleonMorph
 * has already resolved the Blockbuster pose. Works in the editor Preview and
 * in regular world / Blockbuster record playback, including nested BodyParts.
 *
 * Welding is restricted by the Chameleon model registry key, never applied to
 * arbitrary actors. All modified shared ModelVertex positions are rolled back
 * immediately after the corresponding native render, even on exception.
 */
public final class ChameleonWeldRuntime
{
    private static final ThreadLocal<ArrayDeque<ChameleonWeldPreviewPass>> SCOPES =
            new ThreadLocal<ArrayDeque<ChameleonWeldPreviewPass>>()
            {
                @Override
                protected ArrayDeque<ChameleonWeldPreviewPass> initialValue()
                {
                    return new ArrayDeque<ChameleonWeldPreviewPass>();
                }
            };

    private static boolean reportedSuccess;
    private static boolean reportedFailure;

    private ChameleonWeldRuntime() {}

    /**
     * True by default for the dedicated hair model. Set
     * -Dbbs.editor.weld.runtime=false to restore standard Chameleon rendering.
     */
    public static boolean isEnabled()
    {
        return Boolean.parseBoolean(
                System.getProperty("bbs.editor.weld.runtime","true"));
    }

    /** Whether ChameleonRenderer.render has been instrumented successfully. */
    public static boolean isRendererHookInstalled()
    {
        return ChameleonWeldRendererTransformer.isPatched();
    }

    /**
     * Class transformer emits one begin/end pair around each original render.
     * Must not throw, since a failure must not prevent the actor from drawing.
     */
    public static void begin(Object model)
    {
        ChameleonWeldPreviewPass scope = ChameleonWeldPreviewPass.beginPreparedModel(null);
        try
        {
            if(isEnabled() && isSelectedModel(model))
            {
                scope=ChameleonWeldPreviewPass.beginPreparedModel(model);
                if(!reportedSuccess && scope.getSeamCount()>0)
                {
                    reportedSuccess=true;
                    System.out.println("[BBS Editor][Weld][Runtime] Native Chameleon render: "+
                            scope.getSeamCount()+" matching joints; model=hair body");
                }
            }
        }
        catch(Throwable problem)
        {
            scope.close();
            scope=ChameleonWeldPreviewPass.beginPreparedModel(null);
            if(!reportedFailure)
            {
                reportedFailure=true;
                System.err.println("[BBS Editor][Weld][Runtime] Weld skipped: "+
                        problem.getClass().getSimpleName()+": "+problem.getMessage());
            }
        }
        SCOPES.get().push(scope);
    }

    public static void end()
    {
        ArrayDeque<ChameleonWeldPreviewPass> scopes=SCOPES.get();
        if(!scopes.isEmpty())
            scopes.pop().close();
        if(scopes.isEmpty())
            SCOPES.remove();
    }

    /**
     * Resolve model identity through Chameleon's cache: Model.id in geo.json
     * is often a generic string like geometry.steve, not the morph's name.
     */
    @SuppressWarnings("unchecked")
    private static boolean isSelectedModel(Object model)throws Exception
    {
        if(model==null)return false;
        Class<?> clientProxy=Class.forName("mchorse.chameleon.ClientProxy");
        Field modelsField=clientProxy.getField("chameleonModels");
        Object cache=modelsField.get(null);
        if(!(cache instanceof Map))return false;
        String names=System.getProperty("bbs.editor.weld.models","hair body");
        for(Map.Entry<?,?> entry:((Map<?,?>)cache).entrySet())
        {
            Object wrapped=entry.getValue();
            if(wrapped==null || wrapped.getClass().getField("model").get(wrapped)!=model)
                continue;
            String key=String.valueOf(entry.getKey());
            for(String selected:names.split(";"))
            {
                if(key.equalsIgnoreCase(selected.trim()))
                    return true;
            }
            return false;
        }
        return false;
    }
}
