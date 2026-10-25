package com.example.examplemod;

import java.util.Locale;

/**
 * Bounded Java 8 expression evaluator for CAMERA procedural motion.
 * No JS engine, reflection, file access, variables with side effects,
 * code execution or unbounded loops. Invalid input leaves pose unchanged.
 *
 * Supported assignments: x, y, z, yaw, pitch, roll, fov.
 * Supported variables: t (relative ticks), ticks, seconds, pi, duration.
 * Supported math: + - * / ^, sin, cos, tan, abs, sqrt, floor, ceil,
 * min, max, clamp. All functions are deterministic.
 */
public final class CameraMathExpression
{
    private static final class Parser
    {
        private final String s;
        private final double t,ticks,duration;
        private final PreviewCameraPose camera;
        private int index;
        private int operations;
        private Parser(String s,double t,double ticks,double duration,
                PreviewCameraPose camera)
        {
            this.s=s;this.t=t;this.ticks=ticks;this.duration=duration;
            this.camera=camera;
        }
        private void space()
        {
            while(index<s.length()&&Character.isWhitespace(s.charAt(index)))index++;
        }
        private boolean eat(char c)
        {
            space();if(index<s.length()&&s.charAt(index)==c){index++;return true;}
            return false;
        }
        private double parse()
        {
            if(s.length()>512)throw new IllegalArgumentException("Expression too long");
            double answer=expression();
            space();
            if(index!=s.length()||!Double.isFinite(answer))
                throw new IllegalArgumentException("Invalid camera expression");
            return answer;
        }
        private double expression()
        {
            double value=term();
            for(;;)
            {
                if(eat('+'))value+=term();
                else if(eat('-'))value-=term();
                else return value;
                count();
            }
        }
        private double term()
        {
            double value=power();
            for(;;)
            {
                if(eat('*'))value*=power();
                else if(eat('/'))value/=power();
                else return value;
                count();
            }
        }
        private double power()
        {
            double base=unary();
            if(eat('^'))return Math.pow(base,power());
            return base;
        }
        private double unary()
        {
            if(eat('+'))return unary();
            if(eat('-'))return -unary();
            return primary();
        }
        private double primary()
        {
            count();
            space();
            if(eat('('))
            {
                double v=expression();
                if(!eat(')'))throw new IllegalArgumentException("Expected closing )");
                return v;
            }
            if(index>=s.length())throw new IllegalArgumentException("Expected value");
            char c=s.charAt(index);
            if(Character.isDigit(c)||c=='.')
            {
                int start=index;
                while(index<s.length()&&(Character.isDigit(s.charAt(index))
                        ||s.charAt(index)=='.'))index++;
                return Double.parseDouble(s.substring(start,index));
            }
            if(Character.isLetter(c)||c=='_')
            {
                int start=index;
                while(index<s.length()&&(Character.isLetterOrDigit(s.charAt(index))
                        ||s.charAt(index)=='_'))index++;
                String name=s.substring(start,index).toLowerCase(Locale.ROOT);
                if(!eat('('))
                {
                    if("t".equals(name))return t;
                    if("ticks".equals(name))return ticks;
                    if("seconds".equals(name))return ticks/20D;
                    if("duration".equals(name))return duration;
                    if("pi".equals(name))return Math.PI;
                    if("e".equals(name))return Math.E;
                    // Read the incoming pose from lower-layer clips. Allows
                    // MATH to modify a moving Path instead of replacing it
                    // with absolute world coordinates.
                    if("x".equals(name))return camera.x;
                    if("y".equals(name))return camera.y;
                    if("z".equals(name))return camera.z;
                    if("yaw".equals(name))return camera.yaw;
                    if("pitch".equals(name))return camera.pitch;
                    if("roll".equals(name))return camera.roll;
                    if("fov".equals(name))return camera.fov;
                    throw new IllegalArgumentException("Unknown variable");
                }
                double a=expression(),b=0D,d=0D;
                if(eat(','))b=expression();
                if(eat(','))d=expression();
                if(!eat(')'))throw new IllegalArgumentException("Expected closing )");
                if("sin".equals(name))return Math.sin(a);
                if("cos".equals(name))return Math.cos(a);
                if("tan".equals(name))return Math.tan(a);
                if("abs".equals(name))return Math.abs(a);
                if("sqrt".equals(name))return Math.sqrt(a);
                if("floor".equals(name))return Math.floor(a);
                if("ceil".equals(name))return Math.ceil(a);
                if("min".equals(name))return Math.min(a,b);
                if("max".equals(name))return Math.max(a,b);
                if("clamp".equals(name))return Math.max(b,Math.min(d,a));
                throw new IllegalArgumentException("Unknown function");
            }
            throw new IllegalArgumentException("Unexpected character");
        }
        private void count()
        {
            if(++operations>2048)throw new IllegalArgumentException("Too complex");
        }
    }

    public static PreviewCameraPose apply(String expression,PreviewCameraPose initial,
            int absolute,int clipTick,int duration)
    {
        return apply(expression,initial,(double)absolute,clipTick,duration);
    }

    public static PreviewCameraPose apply(String expression,PreviewCameraPose initial,
            double absolute,int clipTick,int duration)
    {
        if(expression==null||expression.trim().isEmpty())return initial;
        double x=initial.x,y=initial.y,z=initial.z;
        double yaw=initial.yaw,pitch=initial.pitch,roll=initial.roll,fov=initial.fov;
        try
        {
            String[] parts=expression.split(";");
            if(parts.length>16)return initial;
            for(String assignment:parts)
            {
                if(assignment.trim().isEmpty())continue;
                int eq=assignment.indexOf('=');
                if(eq<0)throw new IllegalArgumentException("Missing =");
                String name=assignment.substring(0,eq).trim().toLowerCase(Locale.ROOT);
                double value=new Parser(assignment.substring(eq+1),
                        absolute-clipTick,absolute,duration,initial).parse();
                if("x".equals(name))x=value;
                else if("y".equals(name))y=value;
                else if("z".equals(name))z=value;
                else if("yaw".equals(name))yaw=value;
                else if("pitch".equals(name))pitch=value;
                else if("roll".equals(name))roll=value;
                else if("fov".equals(name))fov=value;
                else throw new IllegalArgumentException("Unknown output channel");
            }
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                    ||!Double.isFinite(yaw)||!Double.isFinite(pitch)
                    ||!Double.isFinite(roll)||!Double.isFinite(fov))
                return initial;
            return new PreviewCameraPose(x,y,z,(float)yaw,(float)pitch,
                    (float)roll,(float)Math.max(1,Math.min(170,fov)),
                    initial.nearPlane,initial.farPlane);
        }
        catch(RuntimeException invalid)
        {
            return initial;
        }
    }
    private CameraMathExpression(){}
}
