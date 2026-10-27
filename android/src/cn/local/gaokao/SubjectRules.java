package cn.local.gaokao;

import org.json.*;
import android.database.Cursor;
import java.util.*;

/** One queue policy shared by all views. Biology siblings cannot cue each other today. */
final class SubjectRules {
    static final String[] IDS={"wenyan","gushi","biology"};
    static final String[] TITLES={"古文","古诗文背诵","生物"};
    static String title(String id){for(int n=0;n<IDS.length;n++)if(IDS[n].equals(id))return TITLES[n];return "高考";}
    static JSONArray studyCards(MainActivity a,String volume,String text,boolean review)throws Exception {
        JSONArray all=WordVolumes.cards(a,volume,text);
        if(!a.subject.equals("biology"))return all;
        JSONArray catalog=a.textbookWords().getJSONArray("cards");
        Map<String,String> notes=new HashMap<>();
        for(int n=0;n<catalog.length();n++){JSONObject card=catalog.getJSONObject(n);notes.put(card.getString("id"),card.getJSONObject("biology").getString("note_id"));}
        Map<String,String> seen=new HashMap<>();
        try(Cursor c=a.store.getReadableDatabase().rawQuery("SELECT word FROM events WHERE at>=? AND kind='self-report' ORDER BY at,rowid",new String[]{String.valueOf(a.midnight())})){
            while(c.moveToNext()){String id=c.getString(0),note=notes.get(id);if(note!=null&&!seen.containsKey(note))seen.put(note,id);}
        }
        Set<String> eligible=review?WordVolumes.dueIds(a.store):null;
        JSONArray result=new JSONArray();
        for(int n=0;n<all.length();n++){
            JSONObject card=all.getJSONObject(n);String id=card.getString("id"),note=notes.get(id);
            if(review?!eligible.contains(id):a.store.wordPasses(id)>=4)continue;
            if(seen.containsKey(note)&&!id.equals(seen.get(note)))continue;
            seen.put(note,id);result.put(card);
        }
        return result;
    }
}
