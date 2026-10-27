package cn.local.gaokao;
import android.content.*;
import android.graphics.*;
import java.io.InputStream;
import java.util.*;
/** A shuffled quiet-water/snow cycle. A session shares one photo across home and study. */
final class WallpaperRotation {
 static final String[] THEMES={"清风","竹影","清潭","秋水","湖雪","青山","幽亭"};
 static final String[] WORDS={"清风徐来，水波不兴","庭下如积水空明","日光下澈，影布石上","落霞与孤鹜齐飞","天与云与山与水，上下一白","负势竞上，互相轩邈","有亭翼然临于泉上者"};
 static final String[] SOURCES={"苏轼《赤壁赋》","苏轼《记承天寺夜游》","柳宗元《小石潭记》","王勃《滕王阁序》","张岱《湖心亭看雪》","吴均《与朱元思书》","欧阳修《醉翁亭记》"};
 static final int[] AUTO={0,3,4};
 static boolean automatic(int index){for(int n:AUTO)if(n==index)return true;return false;}
 private static int selected;private static String day="";
 static void beginSession(Context c){
  SharedPreferences p=c.getSharedPreferences("scenes",0);int fixed=(c instanceof MainActivity?((MainActivity)c).prefs:c.getSharedPreferences("study-wenyan",0)).getInt("scene_fixed",-1);
  if(fixed>=0&&fixed<THEMES.length){selected=fixed;day=WordSchedule.today();return;}
  ArrayList<Integer> bag=new ArrayList<>();for(String value:p.getString("bag","").split(","))try{int n=Integer.parseInt(value);if(automatic(n)&&!bag.contains(n))bag.add(n);}catch(Exception ignored){}
  if(bag.isEmpty()){for(int n:AUTO)bag.add(n);Collections.shuffle(bag);int last=p.getInt("last",-1);if(bag.get(0)==last)Collections.swap(bag,0,1);}
  selected=bag.remove(0);StringBuilder rest=new StringBuilder();for(int n:bag){if(rest.length()>0)rest.append(',');rest.append(n);}p.edit().putString("bag",rest.toString()).putInt("last",selected).apply();day=WordSchedule.today();
 }
 static void refreshDay(Context c){if(!day.equals(WordSchedule.today()))beginSession(c);}
 static int todayIndex(){return selected;}
 static String theme(){return THEMES[selected];}
 static String quote(){return WORDS[selected];}
 static String source(){return SOURCES[selected];}
 static String assetName(){return "scenes/"+selected+".jpg";}
 static String paletteAssetName(){return "scenes/"+selected+"-blur.jpg";}
 static Bitmap load(Context c){return decode(c,assetName());}
 static Bitmap loadPalette(Context c){return decode(c,paletteAssetName());}
 static Bitmap decode(Context c,String name){try(InputStream in=c.getAssets().open(name)){return BitmapFactory.decodeStream(in);}catch(Exception e){return null;}}
}
