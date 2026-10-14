package com.example.examplemod;

/**
 * Model-independent mouth shape. Both texture and bone-pose output use this
 * vocabulary so switching output modes never changes speech timing.
 */
public enum LipSyncViseme
{
    IDLE, A, O, U, EE, B, F, D, L;

    public static LipSyncViseme forLetter(char letter)
    {
        switch (Character.toLowerCase(letter))
        {
            case 'а': case 'е': case 'я': case 'э':
            case 'a': return A;
            case 'о': case 'ё': case 'o': return O;
            case 'у': case 'ю': case 'u': return U;
            case 'и': case 'ы': case 'й': case 'к':
            case 'г': case 'х': case 'ш': case 'i':
            case 'y': case 'k': case 'g': return EE;
            case 'м': case 'б': case 'п':
            case 'm': case 'b': case 'p': return B;
            case 'в': case 'ф': case 'v': case 'f': return F;
            case 'д': case 'т': case 'с': case 'н':
            case 'з': case 'ж': case 'ц': case 'ч':
            case 'd': case 't': case 's': case 'n':
            case 'z': case 'j': case 'c': return D;
            case 'л': case 'р': case 'l': case 'r': return L;
            case 'w': return U;
            default: return null;
        }
    }

    public static float weight(char letter)
    {
        char c = Character.toLowerCase(letter);
        if ("аеёиоуыэюяaeiouy".indexOf(c) >= 0) return 1.5F;
        if ("йлрнlrmnw".indexOf(c) >= 0) return 1.2F;
        if ("пбтдкгpbtdkg".indexOf(c) >= 0) return 0.5F;
        return 1.0F;
    }
}
