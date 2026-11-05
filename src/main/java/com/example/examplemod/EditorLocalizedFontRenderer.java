package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/**
 * Stable editor font for both English and Russian.
 *
 * Vanilla 1.12.2 switches EVERY character to its smaller Unicode glyph
 * texture when Russian is selected. This also shrinks English labels.
 * The editor instead always uses Minecraft's original 8px ASCII texture for
 * Latin, and proportionally upscales only Cyrillic fallback glyphs to the
 * same visual cap height. The glyphs remain sharp/nearest-neighbour pixels.
 *
 * Localization is applied during draw AND layout measurement, so tab
 * alignment, centered labels and truncation use the same translated text.
 * No Minecraft language, global GUI scale or saved client setting is edited.
 */
public final class EditorLocalizedFontRenderer extends FontRenderer
{
    private static final float CYRILLIC_SCALE=1.28F;

    public EditorLocalizedFontRenderer(Minecraft mc)
    {
        super(mc.gameSettings,
                new ResourceLocation("textures/font/ascii.png"),
                mc.getTextureManager(),false);
        onResourceManagerReload(mc.getResourceManager());
        super.setUnicodeFlag(false);
        super.setBidiFlag(false);
    }

    @Override
    public void setUnicodeFlag(boolean ignored)
    {
        // Keep the identical Latin font independent of the selected language.
        super.setUnicodeFlag(false);
    }

    @Override
    public int drawString(String text,float x,float y,int color,
            boolean dropShadow)
    {
        if(text==null)return 0;
        String result=EditorLanguage.tr(text);
        if(result.isEmpty())return (int)x;

        // Vanilla formatting/color codes depend on internal FontRenderer
        // state across a whole string. Keep those intact.
        if(result.indexOf('\u00a7')>=0)
            return super.drawString(result,x,y,color,dropShadow);

        float cursor=x;
        for(int i=0;i<result.length();)
        {
            boolean cyrillic=isCyrillic(result.charAt(i));
            int end=i+1;
            while(end<result.length()&&isCyrillic(result.charAt(end))==cyrillic)
                end++;
            String run=result.substring(i,end);
            if(cyrillic)
            {
                GlStateManager.pushMatrix();
                try
                {
                    // Only the native Unicode fallback is resized; Latin
                    // stays on Minecraft's own normal-height 8px font.
                    GlStateManager.translate(cursor,y-0.65F,0F);
                    GlStateManager.scale(CYRILLIC_SCALE,CYRILLIC_SCALE,1F);
                    super.drawString(run,0F,0F,color,dropShadow);
                }
                finally
                {
                    GlStateManager.popMatrix();
                }
                cursor+=super.getStringWidth(run)*CYRILLIC_SCALE;
            }
            else
            {
                super.drawString(run,cursor,y,color,dropShadow);
                cursor+=super.getStringWidth(run);
            }
            i=end;
        }
        return (int)Math.ceil(cursor);
    }

    @Override
    public int getStringWidth(String text)
    {
        if(text==null)return 0;
        String result=EditorLanguage.tr(text);
        if(result.indexOf('\u00a7')>=0)
            return super.getStringWidth(result);
        float width=0F;
        for(int i=0;i<result.length();)
        {
            boolean cyrillic=isCyrillic(result.charAt(i));
            int end=i+1;
            while(end<result.length()&&isCyrillic(result.charAt(end))==cyrillic)
                end++;
            int nativeWidth=super.getStringWidth(result.substring(i,end));
            width+=cyrillic?nativeWidth*CYRILLIC_SCALE:nativeWidth;
            i=end;
        }
        return (int)Math.ceil(width);
    }

    @Override
    public String trimStringToWidth(String text,int width)
    {
        return trimStringToWidth(text,width,false);
    }

    @Override
    public String trimStringToWidth(String text,int width,boolean reverse)
    {
        if(text==null)return "";
        String localized=EditorLanguage.tr(text);
        if(localized.indexOf('\u00a7')>=0)
            return super.trimStringToWidth(localized,width,reverse);
        if(getStringWidth(localized)<=width)return localized;
        if(width<=0)return "";
        // Linear scan: Lip Sync may have 1,024-character typed text.
        // Re-measuring every prefix was quadratic and could stall the GUI.
        float advance=0F;
        for(int n=0;n<localized.length();n++)
        {
            int idx=reverse?localized.length()-1-n:n;
            char ch=localized.charAt(idx);
            int nativeWidth=super.getCharWidth(ch);
            advance+=isCyrillic(ch)?
                    nativeWidth*CYRILLIC_SCALE:nativeWidth;
            if(Math.ceil(advance)>width)
                return reverse?
                        localized.substring(idx+1):
                        localized.substring(0,idx);
        }
        return localized;
    }

    private static boolean isCyrillic(char ch)
    {
        return (ch>=0x0400&&ch<=0x052F) ||
                (ch>=0x2DE0&&ch<=0x2DFF) ||
                (ch>=0xA640&&ch<=0xA69F);
    }
}
