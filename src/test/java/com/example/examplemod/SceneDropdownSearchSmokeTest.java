package com.example.examplemod;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Plain Java 8 regression for the scene dropdown's non-destructive search. */
public final class SceneDropdownSearchSmokeTest
{
    private static void check(boolean value,String message)
    {
        if(!value)throw new AssertionError(message);
    }

    private static void equals(List<Integer> actual,Integer... expected)
    {
        check(actual.equals(Arrays.asList(expected)),
                "Expected "+Arrays.toString(expected)+" but found "+actual);
    }

    public static void main(String[] args)
    {
        List<File> scenes=Arrays.asList(
                new File("School scene"),
                new File("Казино Гамбург"),
                new File("forest_take_02"),
                new File("КАЗИНО ночь"),
                new File("school final"),
                new File("Сцена Казино 3"));

        equals(SceneDropdownSearch.filter(scenes,"school"),0,4);
        equals(SceneDropdownSearch.filter(scenes,"SCHOOL"),0,4);
        equals(SceneDropdownSearch.filter(scenes,"казино"),1,3,5);
        equals(SceneDropdownSearch.filter(scenes,"  КАЗИНО  "),1,3,5);
        equals(SceneDropdownSearch.filter(scenes,"take_02"),2);
        equals(SceneDropdownSearch.filter(scenes,"absent"));
        equals(SceneDropdownSearch.filter(scenes,null),0,1,2,3,4,5);
        equals(SceneDropdownSearch.filter(scenes,""),0,1,2,3,4,5);
        equals(SceneDropdownSearch.filter(Collections.<File>emptyList(),"a"));
        equals(SceneDropdownSearch.filter(null,"a"));

        check(SceneDropdownSearch.limitOffset(50,3,8)==0,
                "short filtered menus must never scroll past their end");
        check(SceneDropdownSearch.limitOffset(-3,100,8)==0,
                "negative offset must clamp");
        check(SceneDropdownSearch.limitOffset(200,100,8)==92,
                "scroll must be clamped by filtered count");
        List<File> many=new ArrayList<File>();
        for(int i=0;i<150;i++)
            many.add(new File("Scene "+i));
        List<Integer> matches=SceneDropdownSearch.filter(many,"Scene 14");
        equals(matches,14,140,141,142,143,144,145,146,147,148,149);
        // The ninth filtered row must open Scene 147, NOT the ninth
        // scene in the original list (Scene 8).
        check(matches.get(8).intValue()==147,
                "scene selection must keep original indexes");
        System.out.println("SceneDropdownSearchSmokeTest OK");
    }
}
