package com.example.examplemod;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Per-instance Weld attached to ChameleonMorph, NOT a model registry key.
 * The transformer adds a boolean field to each morph and propagates it via
 * the normal Chameleon NBT / copy / merge operations, including exported
 * Blockbuster Body Parts and one-tick baked pose snapshots.
 */
public final class ChameleonWeldRuntime
{
    public static final String NBT_KEY="BBSWeld";

    private static final ThreadLocal<ArrayDeque<Object>> ACTIVE_MORPHS=
            new ThreadLocal<ArrayDeque<Object>>()
            {
                @Override
                protected ArrayDeque<Object> initialValue()
                {
                    return new ArrayDeque<Object>();
                }
            };

    private static final ThreadLocal<ArrayDeque<ChameleonWeldPreviewPass>> SCOPES=
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

    /** Master A/B test switch, independent of each attachment's flag. */
    public static boolean isEnabled()
    {
        String setting=System.getProperty("bbs.editor.weld.runtime");
        if(setting==null)
            setting=System.getProperty("bbs.editor.weld.preview","true");
        return Boolean.parseBoolean(setting);
    }

    public static boolean isRendererHookInstalled()
    {
        return ChameleonWeldRendererTransformer.isPatched() &&
                ChameleonWeldMorphTransformer.isPatched();
    }

    /**
     * The native ChameleonMorph.renderModel wrapper pushes its exact instance.
     * The native ChameleonRenderer.render(Model) wrapper reads the top frame;
     * different instances of the same model can have different Weld choices.
     */
    public static void enterMorph(Object morph)
    {
        if(morph!=null)ACTIVE_MORPHS.get().push(morph);
    }

    public static void exitMorph()
    {
        ArrayDeque<Object> stack=ACTIVE_MORPHS.get();
        if(!stack.isEmpty())stack.pop();
        if(stack.isEmpty())ACTIVE_MORPHS.remove();
    }

    private static boolean currentMorphWeld()
    {
        ArrayDeque<Object> stack=ACTIVE_MORPHS.get();
        return !stack.isEmpty() && isMorphWeldEnabled(stack.peek());
    }

    /**
     * Called from the temporary renderer wrapper. Always returns normally to
     * Chameleon, even if a morph/model does not support Weld.
     */
    public static void begin(Object model)
    {
        ChameleonWeldPreviewPass scope=
                ChameleonWeldPreviewPass.beginPreparedModel(null);
        try
        {
            if(isEnabled() && currentMorphWeld())
            {
                scope=ChameleonWeldPreviewPass.beginPreparedModel(model);
                if(!reportedSuccess && scope.getSeamCount()>0)
                {
                    reportedSuccess=true;
                    System.out.println("[BBS Editor][Weld][Runtime] Per-morph Weld active: "+
                            scope.getSeamCount()+" matching joints");
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
                        problem.getClass().getSimpleName()+": "+
                        problem.getMessage());
            }
        }
        SCOPES.get().push(scope);
    }

    public static void end()
    {
        ArrayDeque<ChameleonWeldPreviewPass> scopes=SCOPES.get();
        if(!scopes.isEmpty())scopes.pop().close();
        if(scopes.isEmpty())SCOPES.remove();
    }

    /**
     * A field is injected into the ChameleonMorph class rather than using a
     * global model-name table. Java reflection keeps Chameleon optional.
     */
    public static void setMorphWeld(Object morph,boolean enabled)
    {
        if(morph==null)return;
        try
        {
            Field field=morph.getClass().getField("bbs$weld");
            field.setBoolean(morph,enabled);
        }
        catch(ReflectiveOperationException missing)
        {
            // No Chameleon injection: never activate broad/global Weld.
        }
    }

    public static boolean isMorphWeldEnabled(Object morph)
    {
        if(morph==null)return false;
        try
        {
            return morph.getClass().getField("bbs$weld").getBoolean(morph);
        }
        catch(ReflectiveOperationException missing)
        {
            return false;
        }
    }

    /** Persist in the morph's own NBT so exported .dat keeps this setting. */
    public static void writeMorph(Object morph,NBTTagCompound tag)
    {
        if(isMorphWeldEnabled(morph))tag.setBoolean(NBT_KEY,true);
        else tag.removeTag(NBT_KEY);
    }

    /** Missing metadata is unambiguously OFF (no implicit hair default). */
    public static void readMorph(Object morph,NBTTagCompound tag)
    {
        setMorphWeld(morph,tag != null && tag.getBoolean(NBT_KEY));
    }

    public static void copyMorph(Object target,Object source)
    {
        setMorphWeld(target,isMorphWeldEnabled(source));
    }

    public static void mergeMorph(Object target,Object source,boolean accepted)
    {
        if(accepted)copyMorph(target,source);
    }
}
