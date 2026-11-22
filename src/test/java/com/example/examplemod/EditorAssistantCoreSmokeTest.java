package com.example.examplemod;

public final class EditorAssistantCoreSmokeTest
{
    private EditorAssistantCoreSmokeTest(){}

    private static final class FakeActions
            implements EditorAssistantCore.EditorActions
    {
        int calls,lastFrame=-1;
        String lastBone="";
        @Override public boolean gotoFrame(int frame)
        {
            calls++;lastFrame=frame;return true;
        }
        @Override public boolean createBoneKey(
                String actor,String bone,int frame)
        {
            calls++;lastBone=bone;lastFrame=frame;return true;
        }
        @Override public boolean setWorldPin(
                String actor,String bone,boolean pinned)
        {
            calls++;lastBone=bone;return true;
        }
    }

    public static void main(String[] args)
    {
        EditorAssistantCore.Context ctx=
                new EditorAssistantCore.Context(26,
                        "actor-A","left_leg","POSE");
        FakeActions host=new FakeActions();
        EditorAssistantCore.Proposal plan=EditorAssistantCore.propose(
                "создай ключ на кадре 35",ctx);
        need(plan.action==EditorAssistantCore.Action.CREATE_BONE_KEY,
                "did not parse key creation");
        need(plan.frame==35&&plan.requiresApproval,
                "write plan must require approval");
        need(!EditorAssistantCore.execute(plan,false,host)&&
                host.calls==0,"unapproved write mutated scene");
        need(EditorAssistantCore.execute(plan,true,host)&&
                host.calls==1&&host.lastFrame==35&&
                host.lastBone.equals("left_leg"),
                "approved typed key action not routed");

        plan=EditorAssistantCore.propose("закрепи кость",ctx);
        need(plan.action==EditorAssistantCore.Action.PIN_BONE,
                "Russian world pin not detected");
        plan=EditorAssistantCore.propose("открепи кость",ctx);
        need(plan.action==EditorAssistantCore.Action.UNPIN_BONE,
                "Russian unpin not detected");
        plan=EditorAssistantCore.propose("перейти на кадр 40",ctx);
        need(plan.action==EditorAssistantCore.Action.GOTO_FRAME&&
                plan.frame==40,"frame navigation parse failed");
        plan=EditorAssistantCore.propose("показать состояние",ctx);
        need(plan.action==EditorAssistantCore.Action.INSPECT&&
                !plan.requiresApproval,"inspection must be read-only");
        need(!EditorAssistantCore.execute(plan,true,host)&&
                host.calls==1,"read-only plan executed a write");
        plan=EditorAssistantCore.propose("execute code()",ctx);
        need(plan.action==EditorAssistantCore.Action.UNKNOWN,
                "unknown script was not rejected");
        need(!EditorAssistantCore.execute(plan,true,host)&&
                host.calls==1,"unknown command ran an unsafe action");
        System.out.println("EditorAssistantCoreSmokeTest OK");
    }

    private static void need(boolean pass,String reason)
    {
        if(!pass)throw new AssertionError(reason);
    }
}
