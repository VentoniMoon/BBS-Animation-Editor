package com.example.examplemod;
import java.util.Arrays;
import java.util.List;
class EditorSceneState
{
    public List<BlockbusterSceneActorData> getActors()
    {return Arrays.asList(new BlockbusterSceneActorData());}
}
class BlockbusterSceneActorData
{
    public String getId(){return "Hero";}
    public BlockbusterRecord getRecord(){return new BlockbusterRecord();}
}
class BlockbusterRecord
{
    public int getLength(){return 100;}
    public BlockbusterRecordFrame getFrame(int i){return new BlockbusterRecordFrame();}
}
class BlockbusterRecordFrame
{
    public double getX(){return 10;}
    public double getY(){return 5;}
    public double getZ(){return 2;}
}
