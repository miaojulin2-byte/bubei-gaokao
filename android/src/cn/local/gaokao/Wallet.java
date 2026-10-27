package cn.local.gaokao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Offline attendance and virtual coins, committed with the action that earns them. */
final class Wallet {
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS attendance(day TEXT PRIMARY KEY,at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS coin_ledger(id TEXT PRIMARY KEY,amount INTEGER NOT NULL,reason TEXT NOT NULL,at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS coin_unlock(id TEXT PRIMARY KEY,at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS reward_progress(day TEXT NOT NULL,kind TEXT NOT NULL,id TEXT NOT NULL,PRIMARY KEY(day,kind,id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS familiar(id TEXT PRIMARY KEY,marked INTEGER NOT NULL)");
    }
    static int balance(SQLiteDatabase db){try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM coin_ledger",null)){c.moveToFirst();return c.getInt(0);}}
    static boolean grant(SQLiteDatabase db,String key,int coins,String reason,long at){
        try(Cursor c=db.rawQuery("SELECT 1 FROM coin_ledger WHERE id=?",new String[]{key})){if(c.moveToFirst())return false;} ContentValues value=new ContentValues();value.put("id",key);value.put("amount",coins);value.put("reason",reason);value.put("at",at);db.insertOrThrow("coin_ledger",null,value);return true;
    }
    static Set<String> days(SQLiteDatabase db){Set<String> result=new HashSet<>();try(Cursor c=db.rawQuery("SELECT day FROM attendance",null)){while(c.moveToNext())result.add(c.getString(0));}return result;}
    static int streak(Set<String> days,long now){Calendar cursor=Calendar.getInstance();cursor.setTimeInMillis(now);if(!days.contains(WordSchedule.day(cursor.getTimeInMillis(),0)))cursor.add(Calendar.DATE,-1);int n=0;while(days.contains(WordSchedule.day(cursor.getTimeInMillis(),0))){n++;cursor.add(Calendar.DATE,-1);}return n;}
    static int sign(StudyStore store,long now){SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
        String day=WordSchedule.day(now,0);try(Cursor c=db.rawQuery("SELECT 1 FROM attendance WHERE day=?",new String[]{day})){if(c.moveToFirst())return 0;}
        ContentValues v=new ContentValues();v.put("day",day);v.put("at",now);db.insertOrThrow("attendance",null,v);
        int received=grant(db,"checkin:"+day,10,"签到",now)?10:0;
        int count=streak(days(db),now);int[] thresholds={7,30,90,180,360},bonuses={35,150,450,900,1800};for(int n=0;n<thresholds.length;n++)if(count==thresholds[n]&&grant(db,"streak:"+day+":"+count,bonuses[n],"连续签到"+count+"天",now))received+=bonuses[n];
        db.setTransactionSuccessful();return received;
    }finally{db.endTransaction();}}
    static boolean purchase(StudyStore store,String item,int price,long now){if(price<0)throw new IllegalArgumentException("Invalid coin price");SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
        try(Cursor c=db.rawQuery("SELECT 1 FROM coin_unlock WHERE id=?",new String[]{item})){if(c.moveToFirst()){db.setTransactionSuccessful();return true;}}
        if(balance(db)<price)return false;ContentValues v=new ContentValues();v.put("id",item);v.put("at",now);db.insertOrThrow("coin_unlock",null,v);if(!grant(db,"purchase:"+item,-price,"兑换外观",now))throw new IllegalStateException("Duplicate purchase ledger");db.setTransactionSuccessful();return true;
    }finally{db.endTransaction();}}
    static boolean owned(StudyStore store,String item){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT 1 FROM coin_unlock WHERE id=?",new String[]{item})){return c.moveToFirst();}}
    static void answerRewards(StudyStore store,String id,String flow,int before,int after,String payload,long now,int daily){
        SQLiteDatabase db=store.getWritableDatabase();String day=WordSchedule.day(now,0);
        if(flow.equals("learn")&&before<4&&after>=4){
            ContentValues v=new ContentValues();v.put("day",day);v.put("kind","learn");v.put("id",id);try(Cursor exists=db.rawQuery("SELECT 1 FROM reward_progress WHERE day=? AND kind='learn' AND id=?",new String[]{day,id})){if(!exists.moveToFirst())db.insertOrThrow("reward_progress",null,v);}
            try{String raw=store.round(DailyPlan.KIND);if(raw!=null){JSONObject plan=new JSONObject(raw);JSONArray ids=plan.optJSONArray("ids");if(day.equals(plan.optString("day"))&&ids!=null&&ids.length()>0){int target=Math.min(daily,ids.length());int done=0;for(int n=0;n<target;n++)try(Cursor c=db.rawQuery("SELECT 1 FROM reward_progress WHERE day=? AND kind='learn' AND id=?",new String[]{day,ids.getString(n)})){if(c.moveToFirst())done++;}if(done==target)grant(db,"learn:"+day,20,"完成每日新学计划",now);}}}catch(JSONException e){throw new IllegalStateException(e);}
        }
        if(flow.equals("review"))try{JSONObject group=new JSONObject(payload);JSONArray ids=group.optJSONArray("ids");if(ids!=null&&ids.length()>0&&group.optInt("index")>=ids.length())grant(db,"review:"+day,10,"完成到期复习",now);}catch(JSONException e){throw new IllegalStateException(e);}
    }
}
