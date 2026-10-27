package cn.local.gaokao;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;

/** Textbook membership comes from the original text; each book keeps its own queue. */
final class WordVolumes {
    static final String ALL="全部内容";
    static final String[] NAMES={"必修上","必修下","选择性必修上","选择性必修中","选择性必修下","课标与教材补充"};
    static String[] names(MainActivity a){java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();if(a.libraryTexts!=null)for(int n=0;n<a.libraryTexts.length();n++)names.add(a.libraryTexts.optJSONObject(n).optString("volume"));return names.toArray(new String[0]);}
    static String selected(MainActivity a){return a.prefs.getString("word_volume",ALL);}
    static String selectedText(MainActivity a){return a.prefs.getString("word_text","");}
    static String selectedLabel(MainActivity a){return label(a,selected(a),selectedText(a));}
    static String label(MainActivity a,String volume,String text){
        if(!text.isEmpty()&&a.libraryTexts!=null)for(int i=0;i<a.libraryTexts.length();i++){JSONObject t=a.libraryTexts.optJSONObject(i);if(t!=null&&text.equals(t.optString("id")))return volume+" · "+t.optString("title");}
        return volume;
    }
    static String kind(boolean review,String volume,String text){return kind(review,volume)+(text.isEmpty()?"":":"+text);}
    static String kind(boolean review,String volume){return (review?"word-card-review":"word-card-learn")+(volume.isEmpty()?"":":"+volume);}
    static String earlyKind(String volume,String text){return "word-card-early:"+volume+(text.isEmpty()?"":":"+text);}
    static java.util.HashSet<String> dueIds(StudyStore store){return store.wordDueIds(WordSchedule.today());}
    static JSONArray cards(MainActivity a,String volume) throws Exception {
        return cards(a,volume,"");
    }
    static JSONArray cards(MainActivity a,String volume,String textId) throws Exception {
        JSONArray all=volume.isEmpty()?new JSONObject(a.readAsset("word-cards.json")).getJSONArray("cards"):a.textbookWords().getJSONArray("cards");
        if(volume.isEmpty()||(ALL.equals(volume)&&textId.isEmpty()))return all; // Read legacy sessions without rewriting their records.
        HashMap<String,String> membership=new HashMap<>(),canonical=new HashMap<>();
        if(a.libraryTexts!=null)for(int i=0;i<a.libraryTexts.length();i++){
            JSONObject text=a.libraryTexts.getJSONObject(i);String book=text.optString("volume");
            boolean core=false;for(int n=0;n<5;n++)if(NAMES[n].equals(book))core=true;
            membership.put(text.getString("id"),book);
            if(text.optBoolean("textbook"))canonical.put(text.optString("legacy_id"),text.getString("id"));
        }
        JSONArray result=new JSONArray();
        for(int i=0;i<all.length();i++){
            JSONObject card=all.getJSONObject(i);JSONArray contexts=card.optJSONArray("contexts"),filtered=new JSONArray();java.util.HashSet<String> added=new java.util.HashSet<>();
            if(contexts!=null)for(int j=0;j<contexts.length();j++){
                JSONObject context=contexts.getJSONObject(j);
                if((ALL.equals(volume)||volume.equals(membership.getOrDefault(context.optString("text_id"),"教材补充")))&&(textId.isEmpty()||textId.equals(context.optString("text_id")))){
                    String id=context.optString("text_id"),key=canonical.getOrDefault(id,id)+"\n"+context.optString("sentence").replaceAll("[^\\u3400-\\u9fff]","")+"\n"+context.optString("meaning");
                    if(added.add(key))filtered.put(context);
                }
            }
            if(filtered.length()>0){JSONObject copy=new JSONObject(card.toString());copy.put("contexts",WordLexicon.unique(filtered));result.put(copy);}
        }
        java.util.ArrayList<JSONObject> ordered=new java.util.ArrayList<>();for(int i=0;i<result.length();i++)ordered.add(result.getJSONObject(i));
        ordered.sort((left,right)->Integer.compare(firstOrder(left),firstOrder(right)));
        return new JSONArray(ordered);
    }
    private static int firstOrder(JSONObject card){int first=Integer.MAX_VALUE;JSONArray contexts=card.optJSONArray("contexts");for(int i=0;i<contexts.length();i++){JSONObject c=contexts.optJSONObject(i);first=Math.min(first,c.optInt("curriculum_order",999)*1000+c.optInt("note_order",999));}return first;}
    static java.util.HashSet<String> fourPointIds(StudyStore store){
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT id FROM word_pass WHERE passes>=4",null)){while(c.moveToNext())ids.add(c.getString(0));}
        return ids;
    }
    static java.util.HashSet<String> startedIds(StudyStore store,JSONArray cards,String volume)throws Exception{
        java.util.HashSet<String> started=new java.util.HashSet<>(),valid=new java.util.HashSet<>();
        if(volume.isEmpty()){try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT id FROM word_pass",null)){while(c.moveToNext())started.add(c.getString(0));}return started;}
        for(int i=0;i<cards.length();i++){JSONObject card=cards.getJSONObject(i);JSONArray contexts=card.getJSONArray("contexts");for(int j=0;j<contexts.length();j++)valid.add(card.getString("id")+"\n"+contexts.getJSONObject(j).getString("sentence"));}
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT word,cue FROM events WHERE mode='word_card_self' AND kind='self-report'",null)){while(c.moveToNext())if(valid.contains(c.getString(0)+"\n"+c.getString(1)))started.add(c.getString(0));}
        return started;
    }
    static void migrateLegacy(MainActivity a,JSONArray cards,boolean review,String volume)throws Exception{
        StudyStore store=a.store;
        if(volume.isEmpty()||store.round(kind(review,volume))!=null)return;
        String legacyKind=kind(review,"");String saved=store.round(legacyKind);if(saved==null)return;
        JSONObject state=new JSONObject(saved);JSONArray oldIds=state.getJSONArray("ids");int oldIndex=state.getInt("index");
        if(oldIndex>=oldIds.length())return;
        String current=oldIds.getString(oldIndex);JSONObject match=null;
        for(int i=0;i<cards.length();i++)if(current.equals(cards.getJSONObject(i).getString("id")))match=cards.getJSONObject(i);
        if(match==null)return;
        // Use the original visible context to choose the book that receives the old queue.
        JSONArray all=cards(a,"");String sentence="";
        for(int i=0;i<all.length();i++)if(current.equals(all.getJSONObject(i).getString("id"))){
            JSONArray contexts=all.getJSONObject(i).getJSONArray("contexts");
            sentence=contexts.getJSONObject(Math.min(state.optInt("context"),contexts.length()-1)).optString("sentence");break;
        }
        int context=-1;JSONArray contexts=match.getJSONArray("contexts");
        for(int i=0;i<contexts.length();i++)if(sentence.equals(contexts.getJSONObject(i).optString("sentence")))context=i;
        if(context<0)return;
        java.util.HashSet<String> available=new java.util.HashSet<>();
        for(int i=0;i<cards.length();i++)available.add(cards.getJSONObject(i).getString("id"));
        JSONArray remaining=new JSONArray();for(int i=oldIndex;i<oldIds.length();i++)if(available.contains(oldIds.getString(i)))remaining.put(oldIds.getString(i));
        state.put("ids",remaining);state.put("index",0);state.put("context",context);
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{store.round(kind(review,volume),state.toString());store.round(legacyKind,null);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    static boolean started(StudyStore store,JSONObject card,String volume)throws Exception{
        if(volume.isEmpty())return store.wordStarted(card.getString("id"));
        JSONArray contexts=card.getJSONArray("contexts");
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery(
                "SELECT cue FROM events WHERE word=? AND mode='word_card_self' AND kind='self-report'",new String[]{card.getString("id")})){
            while(c.moveToNext())for(int i=0;i<contexts.length();i++)
                if(contexts.getJSONObject(i).optString("sentence").equals(c.getString(0)))return true;
        }
        return false;
    }
    static String pending(StudyStore store,String volume)throws Exception{return pending(store,volume,"");}
    static String pending(StudyStore store,String volume,String text)throws Exception{
        String saved=store.round(kind(false,volume,text));if(saved==null)return "";
        JSONObject state=new JSONObject(saved);return state.getJSONArray("ids").optString(state.getInt("index"),"");
    }
    static int count(MainActivity a,String volume,int mode)throws Exception{
        return count(a,volume,"",mode);
    }
    /** mode 0 all, 1 under four points, 2 due for review, 3 four points but not yet due (early review). */
    static int count(MainActivity a,String volume,String text,int mode)throws Exception{
        JSONArray cards=mode==2?SubjectRules.studyCards(a,volume,text,true):cards(a,volume,text);int count=0;
        java.util.HashSet<String> ready=fourPointIds(a.store),due=dueIds(a.store),familiar=a.store.familiarIds();
        for(int i=0;i<cards.length();i++){
            String id=cards.getJSONObject(i).getString("id");boolean complete=ready.contains(id);
            if(mode==0||(mode==1&&!complete&&!familiar.contains(id))||(mode==2&&due.contains(id))||(mode==3&&complete&&!due.contains(id)&&!familiar.contains(id)))count++;
        }
        return count;
    }
}
