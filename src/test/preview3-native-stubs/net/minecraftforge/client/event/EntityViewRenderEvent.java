package net.minecraftforge.client.event;
public class EntityViewRenderEvent {
    public static class FOVModifier {
        public float fov;
        public void setFOV(float fov){this.fov=fov;}
    }
    public static class CameraSetup {
        public float roll;
        public void setRoll(float roll){this.roll=roll;}
    }
}
