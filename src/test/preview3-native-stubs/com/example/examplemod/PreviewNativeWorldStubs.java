package com.example.examplemod;
import net.minecraft.client.shader.Framebuffer;
class PreviewCameraEntity extends net.minecraft.entity.Entity {
    public double x,y,z;
    public float yaw,pitch;
    PreviewCameraEntity(Object world){super(world);}
    public void updateCamera(double x,double y,double z,float yaw,float pitch){
        this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;
    }
}
class PreviewShaderBridge {
    final Framebuffer preview=new Framebuffer();
    public Framebuffer getPreviewFramebuffer(){return preview;}
}
class PreviewRenderBackend {
    final PreviewShaderBridge shaders=new PreviewShaderBridge();
    public PreviewShaderBridge getShaderBridge(){return shaders;}
}
