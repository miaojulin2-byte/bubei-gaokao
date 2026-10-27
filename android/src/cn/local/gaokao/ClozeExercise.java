package cn.local.gaokao;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One retrieval exercise for both learning and review; stable until the answer is submitted. */
final class ClozeExercise {
    final String prompt,expected,original;
    final ArrayList<int[]> blanks=new ArrayList<>();
    ClozeExercise(String original,int seed){
        this.original=original;
        Matcher matcher=Pattern.compile("[\\u3400-\\u9fff]+").matcher(original);
        ArrayList<int[]> clauses=new ArrayList<>();
        while(matcher.find())clauses.add(new int[]{matcher.start(),matcher.end()});
        int parity=Math.floorMod(seed,2);
        for(int n=0;n<clauses.size();n++)if(n%2==parity)blanks.add(clauses.get(n));
        if(blanks.isEmpty()){blanks.add(new int[]{0,original.length()});}
        StringBuilder cue=new StringBuilder(),answer=new StringBuilder();int from=0;
        for(int[] b:blanks){cue.append(original.substring(from,b[0])).append("____");if(answer.length()>0)answer.append("\n");answer.append(original.substring(b[0],b[1]));from=b[1];}
        cue.append(original.substring(from));prompt=cue.toString();expected=answer.toString();
    }
    static String normalize(String s){return s.replaceAll("[\\s\\p{P}\\p{Z}]","");}
    boolean matches(String typed){return !normalize(typed).isEmpty()&&normalize(typed).equals(normalize(expected));}
}
