package com.example.examplemod;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI assistant integration boundary, not a hidden network client.
 *
 * The model (when added later) must emit one of these typed, allowlisted
 * editor actions. A proposal only describes an operation: no scene data
 * can be mutated until the UI requests an explicit confirmation and the
 * host invokes execute(). Unknown or unsafe text is always read-only.
 *
 * The deterministic parser is intentionally small and works offline.
 * It also gives future model prompts a stable schema and a testable
 * fallback for Russian/English commands.
 */
public final class EditorAssistantCore
{
    public enum Action
    {
        HELP, INSPECT, GOTO_FRAME, CREATE_BONE_KEY, PIN_BONE,
        UNPIN_BONE, UNKNOWN
    }

    public static final class Context
    {
        public final int frame;
        public final String actorId,boneName,editorMode;
        public Context(int frame,String actorId,
                String boneName,String editorMode)
        {
            this.frame=Math.max(0,frame);
            this.actorId=safe(actorId);
            this.boneName=safe(boneName);
            this.editorMode=safe(editorMode);
        }
    }

    public static final class Proposal
    {
        public final Action action;
        public final String description;
        public final int frame;
        public final String actorId,boneName;
        /** Any possible scene edit MUST be previewed/approved in the UI. */
        public final boolean requiresApproval;
        private Proposal(Action action,String description,int frame,
                String actor,String bone,boolean approval)
        {
            this.action=action;this.description=description;
            this.frame=frame;this.actorId=actor;this.boneName=bone;
            this.requiresApproval=approval;
        }
    }

    /** Isolated callback: execution uses editor's existing trusted
     * keyframe/timeline controllers, never arbitrary reflection or code. */
    public interface EditorActions
    {
        boolean gotoFrame(int frame);
        boolean createBoneKey(String actorId,String boneName,int frame);
        boolean setWorldPin(String actorId,String boneName,boolean pinned);
    }

    private static final Pattern FRAME=Pattern.compile(
            "(?:кадр(?:е|а|у)?|frame|tick)\\s*[#:=№]?\\s*(\\d{1,7})",
            Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);

    private EditorAssistantCore(){}

    public static Proposal propose(String text,Context context)
    {
        if(context==null)throw new IllegalArgumentException("Missing context");
        String raw=safe(text);
        if(raw.length()>1000)
            return plan(Action.UNKNOWN,"Команда слишком длинная",
                    context,context.frame,false);
        String lower=raw.toLowerCase(Locale.ROOT).trim();
        if(lower.isEmpty()||lower.equals("помощь")||
                lower.equals("help"))
            return plan(Action.HELP,
                    "Доступно: показать состояние, перейти на кадр N, "+
                    "создать ключ, закрепить/открепить кость",
                    context,context.frame,false);
        if(lower.contains("состояни")||lower.contains("информац")||
                lower.equals("status")||lower.equals("inspect"))
            return plan(Action.INSPECT,
                    "Актёр: "+context.actorId+", кость: "+
                    context.boneName+", кадр: "+context.frame,
                    context,context.frame,false);

        Matcher m=FRAME.matcher(lower);
        int frame=context.frame;
        if(m.find())
        {
            try{frame=Integer.parseInt(m.group(1));}
            catch(NumberFormatException invalid)
            {return plan(Action.UNKNOWN,"Неверный кадр",context,frame,false);}
        }
        if(lower.contains("перей")||lower.contains("go to")||
                lower.startsWith("кадр ")||lower.startsWith("frame "))
            return plan(Action.GOTO_FRAME,"Перейти на кадр "+frame,
                    context,frame,true);
        if(lower.contains("ключ")||lower.contains("keyframe"))
            return plan(Action.CREATE_BONE_KEY,
                    "Создать ключ кости "+context.boneName+" на кадре "+
                    frame,context,frame,true);
        if(lower.contains("откреп")||lower.contains("снять фиксац")||
                lower.contains("unpin"))
            return plan(Action.UNPIN_BONE,
                    "Снять мировую фиксацию с "+context.boneName,
                    context,context.frame,true);
        if(lower.contains("закреп")||lower.contains("зафикс")||
                lower.contains("pin bone"))
            return plan(Action.PIN_BONE,
                    "Зафиксировать кость "+context.boneName+" в мире",
                    context,context.frame,true);
        return plan(Action.UNKNOWN,
                "Неизвестная команда; сцена не изменена",
                context,context.frame,false);
    }

    private static Proposal plan(Action action,String description,
            Context context,int frame,boolean mutation)
    {
        if(mutation&&(context.actorId.isEmpty()||
                (action!=Action.GOTO_FRAME&&
                 context.boneName.isEmpty())))
            return new Proposal(Action.UNKNOWN,
                    "Сначала выбери актёра и кость",
                    frame,context.actorId,context.boneName,false);
        return new Proposal(action,description,frame,
                context.actorId,context.boneName,mutation);
    }

    /** Approval is mandatory even if a future model claims otherwise.
     * Read-only and unknown plans NEVER touch the scene. */
    public static boolean execute(Proposal plan,boolean approved,
            EditorActions actions)
    {
        if(plan==null||actions==null||!approved||
                !plan.requiresApproval)return false;
        switch(plan.action)
        {
            case GOTO_FRAME:
                return actions.gotoFrame(plan.frame);
            case CREATE_BONE_KEY:
                return actions.createBoneKey(
                        plan.actorId,plan.boneName,plan.frame);
            case PIN_BONE:
                return actions.setWorldPin(
                        plan.actorId,plan.boneName,true);
            case UNPIN_BONE:
                return actions.setWorldPin(
                        plan.actorId,plan.boneName,false);
            default:return false;
        }
    }

    private static String safe(String input)
    {
        return input==null?"":input.trim();
    }
}
