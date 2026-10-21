package com.example.examplemod;

/**
 * Editor scene-space metadata, retained for existing animation controller
 * interfaces. This is NOT a renderer.
 *
 * Preview 3.0 uses Minecraft's normal world render and BbsFsPreviewTarget.
 * The old AnimationPreview.draw()/PreviewShaderBridge/PreviewWorldRenderer
 * pipeline has been removed completely. This object stores only the bounds
 * and world/reference positions needed by the editor controllers.
 */
public final class AnimationPreview
{
    private int x, y, width, height;
    private double referenceX, referenceY, referenceZ;
    private double worldX, worldY, worldZ;

    public AnimationPreview()
    {
        this.width = 300;
        this.height = 300;
    }

    public void setBounds(int x, int y, int width, int height)
    {
        this.x=x;
        this.y=y;
        this.width=width;
        this.height=height;
    }

    public void setReferencePosition(double x, double y, double z)
    {
        this.referenceX=x;
        this.referenceY=y;
        this.referenceZ=z;
        this.worldX=0D;
        this.worldY=0D;
        this.worldZ=0D;
    }

    public void setWorldPosition(double x, double y, double z)
    {
        this.worldX=x;
        this.worldY=y;
        this.worldZ=z;
    }

    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }
    public double getWorldZ() { return worldZ; }

    /** No graphics resources are owned by scene metadata. */
    public void delete() {}
}
