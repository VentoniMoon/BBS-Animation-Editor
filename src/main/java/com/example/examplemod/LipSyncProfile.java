package com.example.examplemod;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-mouth binding for two distinct output types.
 *
 * TEXTURE: a texture resource path per viseme.
 * BODY_PARTS: a pose name per viseme, to be resolved by the
 * selected Body Part's model when baking keyframes.
 *
 * The profile deliberately does not modify existing Character/Body Parts
 * keys until a target-specific adapter validates the mapping.
 */
public final class LipSyncProfile
{
    public enum Mode { TEXTURE, BODY_PARTS }

    private final Mode mode;
    private final Map<LipSyncViseme, String> mapping =
            new EnumMap<LipSyncViseme, String>(LipSyncViseme.class);

    public LipSyncProfile(Mode mode)
    {
        if (mode == null) throw new IllegalArgumentException("Mode is required");
        this.mode = mode;
    }

    public Mode getMode() { return mode; }

    public void map(LipSyncViseme viseme, String resource)
    {
        if (viseme == null) throw new IllegalArgumentException("Viseme is required");
        if (resource == null || resource.trim().isEmpty())
            mapping.remove(viseme);
        else mapping.put(viseme, resource.trim());
    }

    public String resolve(LipSyncViseme viseme)
    {
        String name = mapping.get(viseme);
        if (name != null) return name;
        // Prefer a similar available shape rather than silently replacing it
        // with an arbitrary character's mouth.
        LipSyncViseme substitute;
        switch (viseme)
        {
            case EE: substitute = LipSyncViseme.A; break;
            case U: substitute = LipSyncViseme.O; break;
            case D: case L: substitute = LipSyncViseme.EE; break;
            case F: substitute = LipSyncViseme.B; break;
            default: substitute = LipSyncViseme.IDLE; break;
        }
        return mapping.get(substitute);
    }

    public boolean isUsable()
    {
        if (mapping.get(LipSyncViseme.IDLE) == null) return false;
        for (LipSyncViseme viseme : LipSyncViseme.values())
        {
            if (viseme != LipSyncViseme.IDLE && mapping.containsKey(viseme))
                return true;
        }
        return false;
    }
}
