package cn.local.gaokao;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.HashSet;

/**
 * A persisted daily budget of never-started words shared by all scopes; resets on the local day.
 * Started words under four points (including review forgets) are always kept and never charged.
 * Raising the limit adds words today; lowering it releases allocated words not yet started.
 */
final class DailyPlan {
    static final String KIND="word-card-daily-plan";
    static String day(){return WordSchedule.today();}
    static int pendingCount(MainActivity a,String volume,String text)throws Exception{
        return planned(a,volume,text,false).length();
    }
    static JSONArray planned(MainActivity a,String volume,String text,boolean reserve)throws Exception{
        return planned(a,volume,text,reserve,day());
    }
    static JSONArray planned(MainActivity a,String volume,String text,boolean reserve,String day)throws Exception{
        int limit=Math.max(1,Math.min(100,a.prefs.getInt("daily",10)));
        String raw=a.store.round(KIND);JSONObject plan=raw==null?new JSONObject():new JSONObject(raw);
        LinkedHashSet<String> allocated=new LinkedHashSet<>();
        if(day.equals(plan.optString("day"))){JSONArray ids=plan.optJSONArray("ids");if(ids!=null)for(int n=0;n<ids.length();n++)allocated.add(ids.getString(n));}
        java.util.HashSet<String> familiar=a.store.familiarIds();allocated.removeAll(familiar);
        JSONArray cards=SubjectRules.studyCards(a,volume,text,false);HashSet<String> available=new HashSet<>(),ready=WordVolumes.fourPointIds(a.store);
        for(int n=0;n<cards.length();n++)available.add(cards.getJSONObject(n).getString("id"));
        // Browsing a different scope must not spend the day's budget on unseen cards.
        // Real first attempts remain charged across scopes, including completed cards.
        allocated.removeIf(id->!available.contains(id)&&!a.store.wordStarted(id));
        LinkedHashSet<String> candidates=new LinkedHashSet<>();
        String saved=a.store.round(WordVolumes.kind(false,volume,text));boolean activeGroup=false;
        if(saved!=null){JSONObject group=new JSONObject(saved);JSONArray ids=group.getJSONArray("ids");int index=group.getInt("index");
            activeGroup=index<ids.length();
            for(int n=index;n<ids.length();n++){String id=ids.getString(n);if(available.contains(id)&&!ready.contains(id))candidates.add(id);}
        }
        // Preserve an existing small group rather than silently expanding it on upgrade.
        if((plan.has("day")&&!day.equals(plan.optString("day")))||!activeGroup||candidates.isEmpty()||limit>plan.optInt("limit",limit))for(int n=0;n<cards.length();n++){String id=cards.getJSONObject(n).getString("id");if(!ready.contains(id))candidates.add(id);}
        HashSet<String> learning=new HashSet<>();
        try(android.database.Cursor c=a.store.getReadableDatabase().rawQuery("SELECT id FROM word_pass WHERE passes<4",null)){while(c.moveToNext())learning.add(c.getString(0));}
        // Release only reservations that have never been started when the setting is reduced.
        LinkedHashSet<String> charged=new LinkedHashSet<>();
        for(String id:allocated)if(a.store.wordStarted(id))charged.add(id);
        for(String id:allocated)if(charged.size()<limit)charged.add(id);
        allocated=charged;
        for(String id:candidates){if(allocated.size()>=limit)break;if(!learning.contains(id)&&!familiar.contains(id))allocated.add(id);}
        LinkedHashSet<String> kept=new LinkedHashSet<>();int position=0;
        for(String id:allocated){if(position++>=limit)break;if(available.contains(id)&&!ready.contains(id))kept.add(id);}
        for(int n=0;n<cards.length();n++){String id=cards.getJSONObject(n).getString("id");if(learning.contains(id)&&!familiar.contains(id))kept.add(id);}
        JSONArray active=new JSONArray();for(String id:kept)active.put(id);
        if(reserve){JSONArray ids=new JSONArray();for(String id:allocated)ids.put(id);JSONObject next=new JSONObject().put("day",day).put("limit",limit).put("ids",ids);
            if(!next.toString().equals(raw))a.store.round(KIND,next.toString());}
        return active;
    }
}
