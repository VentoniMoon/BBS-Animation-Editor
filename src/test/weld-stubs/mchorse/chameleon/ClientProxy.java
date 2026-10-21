package mchorse.chameleon;
import java.util.HashMap;
import java.util.Map;
public class ClientProxy
{
    public static final Map<String,Wrapper> chameleonModels=new HashMap<String,Wrapper>();
    public static final class Wrapper
    {
        public Object model;
        public Wrapper(Object model) {this.model=model;}
    }
}
