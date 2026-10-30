package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java shader source adaptation for CAMERA Curve.
 *
 * Only options with a numeric GLSL #define and an OptiFine slider comment
 * are candidates. Preprocessor-dependent values, array sizes and switch
 * labels MUST remain compile-time constants, so they are never rewritten.
 *
 * An optional caller-supplied allowlist further restricts candidates to
 * enabled settings reported by the active OptiFine shader pack.
 */
public final class CameraShaderCurveSource
{
    public static final String PREFIX="bbs_curve_";
    private static final Pattern DEFINE=Pattern.compile(
            "(?m)^\\s*#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+"+
            "([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[Ee][-+]?\\d+)?[fF]?)"+
            "\\s*(?://\\s*\\[([^]\\r\\n]*)\\].*)?$");
    private static final Pattern DIRECTIVE=Pattern.compile(
            "(?m)^\\s*#\\s*(?:if|elif|define)\\b([^\\r\\n]*)");
    private static final Pattern ARRAY_OR_CASE=Pattern.compile(
            "\\[([^]\\r\\n]*)\\]|\\bcase\\s+([^:]+):");
    private static final Pattern CONST=Pattern.compile(
            "\\bconst\\s+(?:float|int)\\s+([A-Za-z_]\\w*)\\s*=\\s*([^;{}]+);");

    public static final class Option
    {
        public final String name;
        public final float fallback;
        public final boolean integer;
        public final String limits;
        public Option(String name,float fallback,boolean integer,String limits)
        {
            this.name=name;this.fallback=fallback;this.integer=integer;
            this.limits=limits;
        }
    }
    public static final class Result
    {
        public final String source;
        public final Map<String,Option> options;
        Result(String source,Map<String,Option> options)
        {
            this.source=source;
            this.options=Collections.unmodifiableMap(options);
        }
    }

    public static Result convert(String original,Set<String> enabledOptions)
    {
        if(original==null||!original.contains("#version"))
            return new Result(original,new LinkedHashMap<String,Option>());
        Map<String,Option> candidates=new LinkedHashMap<String,Option>();
        Matcher definitions=DEFINE.matcher(original);
        while(definitions.find())
        {
            String name=definitions.group(1),literal=definitions.group(2);
            String allowed=definitions.group(3);
            // Only actual pack options, not arbitrary internal macros. The
            // original option-menu slider annotation is also required.
            if(allowed==null||enabledOptions!=null&&
                    !enabledOptions.contains(name)||
                    "WATER_WAVE_ITERATIONS".equals(name))continue;
            try
            {
                boolean integer=!literal.matches(".*[.eEfF].*");
                float v=Float.parseFloat(literal.replaceAll("[fF]$",""));
                if(Float.isFinite(v)&&!candidates.containsKey(name))
                    candidates.put(name,new Option(name,v,integer,allowed));
            }
            catch(NumberFormatException invalid){}
        }
        if(candidates.isEmpty())
            return new Result(original,candidates);

        String commentsRemoved=original.replaceAll("(?s)/\\*.*?\\*/"," ")
                .replaceAll("(?m)//[^\\r\\n]*","");
        Matcher preprocessor=DIRECTIVE.matcher(commentsRemoved);
        while(preprocessor.find())
        {
            String expression=preprocessor.group(1);
            for(String name:new ArrayList<String>(candidates.keySet()))
                if(hasIdentifier(expression,name)&&
                        !isSelfDefine(expression,name))
                    candidates.remove(name);
        }
        Matcher constantContexts=ARRAY_OR_CASE.matcher(commentsRemoved);
        while(constantContexts.find())
            for(String name:new ArrayList<String>(candidates.keySet()))
                if(hasIdentifier(constantContexts.group(),name))
                    candidates.remove(name);
        if(candidates.isEmpty())
            return new Result(original,candidates);

        StringBuilder rewritten=new StringBuilder(original.length()+512);
        boolean directive=false,inlineComment=false,blockComment=false;
        for(int i=0;i<original.length();)
        {
            char c=original.charAt(i);
            char next=i+1<original.length()?original.charAt(i+1):0;
            if(c=='\n'){directive=false;inlineComment=false;}
            if(!inlineComment&&!blockComment&&c=='#')directive=true;
            if(!blockComment&&!inlineComment&&c=='/'&&next=='/')
                inlineComment=true;
            if(!inlineComment&&!blockComment&&c=='/'&&next=='*')
            {blockComment=true;rewritten.append("/*");i+=2;continue;}
            if(blockComment&&c=='*'&&next=='/')
            {blockComment=false;rewritten.append("*/");i+=2;continue;}
            if(!directive&&!inlineComment&&!blockComment&&isStart(c))
            {
                int end=i+1;
                while(end<original.length()&&isPart(original.charAt(end)))
                    end++;
                String word=original.substring(i,end);
                if(candidates.containsKey(word))rewritten.append(PREFIX);
                rewritten.append(word);i=end;
            }
            else{rewritten.append(c);i++;}
        }
        String result=rewritten.toString();
        // Shader globals that depend on a converted macro cannot remain
        // const. Propagate through other float/int const dependencies.
        Set<String> mutable=new LinkedHashSet<String>();
        for(String key:candidates.keySet())mutable.add(PREFIX+key);
        for(int round=0;round<16;round++)
        {
            Matcher constants=CONST.matcher(result);
            StringBuffer output=new StringBuffer();
            boolean changed=false;
            while(constants.find())
            {
                boolean dependent=false;
                for(String key:mutable)
                    if(hasIdentifier(constants.group(2),key))
                    {dependent=true;break;}
                if(dependent)
                {
                    mutable.add(constants.group(1));
                    constants.appendReplacement(output,Matcher.quoteReplacement(
                            constants.group().replaceFirst("\\bconst\\s+","")));
                    changed=true;
                }
            }
            constants.appendTail(output);
            result=output.toString();
            if(!changed)break;
        }
        int version=result.indexOf("#version"), newline=result.indexOf('\n',version);
        if(version<0||newline<0)
            return new Result(original,new LinkedHashMap<String,Option>());
        StringBuilder uniforms=new StringBuilder();
        for(Option opt:candidates.values())
            uniforms.append("uniform ").append(opt.integer?"int ":"float ")
                    .append(PREFIX).append(opt.name).append(";\n");
        result=result.substring(0,newline+1)+uniforms+
                result.substring(newline+1);
        return new Result(result,candidates);
    }

    private static boolean isSelfDefine(String expression,String name)
    {
        // The defining #define NAME ... must not disqualify its own name.
        return expression.trim().matches(
                Pattern.quote(name)+"\\b.*");
    }
    private static boolean hasIdentifier(String text,String name)
    {
        return Pattern.compile("(?<![A-Za-z0-9_])"+Pattern.quote(name)+
                "(?![A-Za-z0-9_])").matcher(text).find();
    }
    private static boolean isStart(char c)
    {return Character.isLetter(c)||c=='_';}
    private static boolean isPart(char c)
    {return Character.isLetterOrDigit(c)||c=='_';}
    private CameraShaderCurveSource(){}
}
