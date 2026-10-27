package cn.local.gaokao;

import android.app.AlertDialog;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Shared, read-only dictionary projection. Original IDs and stored answer cues remain untouched. */
final class WordLexicon {
 static final int ORANGE=0xffdf761b;
 static String sentence(String raw){return raw.replaceFirst("^\\s*[（(]《[^》]+》[）)]\\s*","").trim();}
 static String key(JSONObject c){return c.optString("text_id").replaceFirst("^textbook-","")+"\n"+sentence(c.optString("sentence")).replaceAll("[^\\u3400-\\u9fff]","");}
 static JSONArray unique(JSONArray contexts)throws Exception{LinkedHashMap<String,JSONObject> out=new LinkedHashMap<>();for(int i=0;i<contexts.length();i++){JSONObject c=contexts.getJSONObject(i);String key=key(c);if(!out.containsKey(key)||"textbook-note".equals(c.optString("source_kind")))out.put(key,c);}return new JSONArray(out.values());}
 static String title(String value){return value.startsWith("《")?value:"《"+value+"》";}
 static String section(MainActivity a,JSONObject c){
  String needle=sentence(c.optString("sentence")),id=c.optString("text_id").replaceFirst("^textbook-","");
  if(a.libraryTexts!=null)for(int n=0;n<a.libraryTexts.length();n++){JSONObject t=a.libraryTexts.optJSONObject(n);if(!t.optString("id").replaceFirst("^textbook-","").equals(id))continue;StringBuilder full=new StringBuilder();JSONArray ss=t.optJSONArray("sections");if(ss==null)continue;for(int i=0;i<ss.length();i++){JSONArray ps=ss.optJSONObject(i).optJSONArray("paragraphs");if(ps!=null)for(int j=0;j<ps.length();j++)full.append(ps.optJSONObject(j).optString("text"));}int at=full.indexOf(needle);if(at>=0){Matcher m=Pattern.compile("[（(](《[^》]+》)[）)]").matcher(full);if(m.find(at+needle.length()))return m.group(1);}}
  String s=c.optString("section");return s.startsWith("《")?s:"";
 }
 static JSONObject sentenceTranslations;
 static String translation(MainActivity a,JSONObject c){
  String s=sentence(c.optString("sentence"));
  try{if(sentenceTranslations==null)sentenceTranslations=new JSONObject(a.readAsset("sentence-translations.json")).getJSONObject("translations");String fixed=sentenceTranslations.optString(s);if(!fixed.isEmpty())return fixed;}catch(Exception e){android.util.Log.w("WordLexicon","Sentence translations unavailable",e);}
  String value=c.optString("sentence_translation");if(!value.isEmpty())return value.equals(c.optString("meaning"))?"":value;
  value=c.optString("explanation");
  // Multi-word textbook notes follow the sentence translation. Never repeat that annotation list.
  Matcher m=Pattern.compile("[。；]\\s*[\\u3400-\\u9fff]{1,8}[，：]").matcher(value);
  if(m.find())value=value.substring(0,m.start()+1);
  if(value.replaceAll("[。；\\s]","").equals(c.optString("meaning").replaceAll("[。；\\s]","")))return "";
  return value;
 }
 static SpannableString highlighted(String text,String word){return highlighted(text,word,-1);}
 static SpannableString highlighted(String text,String word,int target){SpannableString s=new SpannableString(text);if(!word.isEmpty()){int at=target>=0?target:text.indexOf(word);if(at>=0&&at+word.length()<=text.length()&&text.substring(at,at+word.length()).equals(word)){s.setSpan(new ForegroundColorSpan(ORANGE),at,at+word.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);s.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),at,at+word.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}}return s;}
 static int displayTarget(JSONObject c,String word){String raw=c.optString("sentence"),display=sentence(raw);int at=c.has("target_start")?c.optInt("target_start"):raw.indexOf(word);return at-(raw.length()-display.length());}
 static JSONArray examples(MainActivity a,String query)throws Exception{
  String q=query.trim();JSONArray out=new JSONArray();if(q.isEmpty())return out;LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();HashMap<String,Integer> priority=new HashMap<>();JSONArray cards=matches(a,q);
  for(int n=0;n<cards.length();n++){JSONObject card=cards.getJSONObject(n);String word=card.optString("word");JSONArray cs=card.getJSONArray("contexts");for(int i=0;i<cs.length();i++){JSONObject c=cs.getJSONObject(i);if(!word.contains(q)&&!c.optString("sentence").contains(q)&&!c.optString("meaning").contains(q))continue;String id=key(c);int rank=word.equals(q)?0:word.contains(q)?1:2;JSONObject value=new JSONObject(c.toString()).put("dictionary_word",word);if(!unique.containsKey(id)||rank<priority.get(id)||(rank==priority.get(id)&&"textbook-note".equals(c.optString("source_kind")))){unique.put(id,value);priority.put(id,rank);}}}
  for(JSONObject c:unique.values())out.put(c);return out;
 }

 static void clickable(MainActivity a,TextView view,String raw,String target){clickable(a,view,raw,target,-1);}
 static void clickable(MainActivity a,TextView view,String raw,String target,int targetOffset){
  final Runnable parent=a.backAction;final Runnable returnTo=a.page.equals("wordStudy")&&a.wordPanel!=null?a.wordPanel::show:()->new DictionaryPanel(a,parent==null?a::home:parent).detail(target);
  SpannableString s=highlighted(raw,target,targetOffset);try{HashSet<String> seen=new HashSet<>();JSONArray cards=a.textbookWords().getJSONArray("cards");ArrayList<String> terms=new ArrayList<>();for(int i=0;i<cards.length();i++){String word=cards.getJSONObject(i).optString("word");if(!word.isEmpty()&&raw.contains(word)&&seen.add(word))terms.add(word);}terms.sort((l,r)->Integer.compare(r.length(),l.length()));boolean[] used=new boolean[raw.length()];for(String word:terms)for(int at=raw.indexOf(word);at>=0;at=raw.indexOf(word,at+word.length())){boolean overlap=false;for(int j=at;j<at+word.length();j++)overlap|=used[j];if(overlap)continue;for(int j=at;j<at+word.length();j++)used[j]=true;final String term=word;s.setSpan(new ClickableSpan(){public void onClick(View v){popup(a,term,returnTo);}public void updateDrawState(android.text.TextPaint pen){pen.setUnderlineText(false);}},at,at+word.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}}
  catch(Exception e){android.util.Log.w("WordLexicon","Word links unavailable",e);}view.setText(s);view.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());view.setHighlightColor(0x44ff7900);
 }
 static JSONArray matches(MainActivity a,String query)throws Exception{JSONArray result=new JSONArray(),all=a.textbookWords().getJSONArray("cards");String q=query.trim();if(q.isEmpty())return result;for(int n=0;n<all.length();n++){JSONObject card=all.getJSONObject(n);boolean hit=card.optString("word").contains(q);JSONArray contexts=card.optJSONArray("contexts");if(!hit&&contexts!=null)for(int i=0;i<contexts.length();i++){JSONObject c=contexts.optJSONObject(i);if(c.optString("sentence").contains(q)||c.optString("meaning").contains(q)){hit=true;break;}}if(hit)result.put(card);}String[] meanings={"清凉的风缓缓吹来，水面没有泛起波澜。","庭院中的月光像积水一样清澈透明。","阳光直照水底，鱼的影子映在石头上。","落日的霞光与孤独的水鸟一同飞翔。","天空、云、山和水，上上下下全是一片白色。","山峦凭借高峻的地势争相向上，仿佛互相向高处和远处伸展。","有一座亭子，像鸟张开翅膀一样高踞在泉水上方。"};for(int i=0;i<WallpaperRotation.WORDS.length;i++){String quote=WallpaperRotation.WORDS[i];if(quote.contains(q)||meanings[i].contains(q)){JSONObject c=new JSONObject().put("text_id","scene-"+i).put("title",WallpaperRotation.SOURCES[i].substring(WallpaperRotation.SOURCES[i].indexOf("《"))).put("sentence",quote).put("meaning",meanings[i]).put("sentence_translation",meanings[i]);result.put(new JSONObject().put("id","scene-"+i).put("word",quote).put("contexts",new JSONArray().put(c)));}}return result;}
 static void popup(MainActivity a,String term,Runnable returnTo){try{JSONArray hits=matches(a,term);StringBuilder meanings=new StringBuilder();HashSet<String> seen=new HashSet<>();for(int n=0;n<hits.length();n++){JSONObject card=hits.getJSONObject(n);if(!term.equals(card.optString("word")))continue;JSONArray cs=card.getJSONArray("contexts");for(int j=0;j<cs.length();j++){String value=cs.getJSONObject(j).optString("meaning");if(seen.add(value))meanings.append(value).append("\n");}}new AlertDialog.Builder(a).setTitle(term).setMessage(meanings.toString().trim()).setNeutralButton("收藏",(d,w)->new WordBooks(a).chooseTerm(term,null)).setPositiveButton("查看详细释义",(d,w)->new DictionaryPanel(a,returnTo).detail(term)).setNegativeButton("关闭",null).show();}catch(Exception e){a.error("暂时无法读取释义");}}
}
