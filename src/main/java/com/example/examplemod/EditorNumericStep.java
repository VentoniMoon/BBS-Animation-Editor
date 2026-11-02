package com.example.examplemod;

/** Precision used for numerical editor arrows, matching Blockbuster's
 * fine value adjustment workflow. Fractions are binary-exact so a group
 * of sixteen mouse clicks returns exactly one unit. */
public final class EditorNumericStep
{
    public static final double MOUSE=1D/16D;
    public static final double KEYBOARD=1D/32D;

    private EditorNumericStep(){}

    public static double adjust(double value,int direction,boolean keyboard)
    {
        if(!Double.isFinite(value)||direction==0)return value;
        double step=keyboard?KEYBOARD:MOUSE;
        // Quantize the step relative to the source value, not the
        // displayed rounded string, to avoid compounding decimal error.
        double result=value+(direction<0?-step:step);
        return Math.abs(result)<1E-10D?0D:result;
    }

    public static String format(double value)
    {
        if(!Double.isFinite(value))return Double.toString(value);
        java.math.BigDecimal decimal=java.math.BigDecimal
                .valueOf(value).setScale(6,
                        java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return decimal.toPlainString();
    }

    public static boolean isIntegralField(String key)
    {
        return "@start".equals(key)||"@duration".equals(key)||
                "@layer".equals(key)||"tick".equals(key)||
                "mask".equals(key)||"frame".equalsIgnoreCase(key);
    }
}
