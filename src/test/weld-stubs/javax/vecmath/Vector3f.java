package javax.vecmath;
public final class Vector3f {
    public float x,y,z;
    public Vector3f() {}
    public Vector3f(float x,float y,float z) {set(x,y,z);}
    public void set(float x,float y,float z) {
        this.x=x;this.y=y;this.z=z;
    }
}
