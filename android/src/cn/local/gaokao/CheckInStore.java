package cn.local.gaokao;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.LongSupplier;

/** Explicit daily attendance; never inferred from learning events. */
final class CheckInStore {
    private final SharedPreferences prefs;
    private final StudyStore study;
    private final LongSupplier clock;
    CheckInStore(MainActivity context){this(context,System::currentTimeMillis);}
    CheckInStore(MainActivity context,LongSupplier clock){study=context.commonStore;prefs=context.getSharedPreferences("check_in",Context.MODE_PRIVATE);this.clock=clock;}
    Calendar now(){Calendar c=Calendar.getInstance();c.setTimeInMillis(clock.getAsLong());return c;}
    static String key(Calendar date){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(date.getTime());}
    String today(){return key(now());}
    private String generation(){String value=study.round(StudyStore.CHECK_IN_GENERATION);return value==null?"":value;}
    Set<String> days(){
        android.database.sqlite.SQLiteDatabase db=study.getWritableDatabase();
        String current=generation();
        if(!current.equals(prefs.getString("generation",""))){prefs.edit().remove("days").putString("generation",current).commit();return Wallet.days(db);}
        Set<String> legacy=prefs.getStringSet("days",Collections.emptySet());
        if(!legacy.isEmpty()){
            db.beginTransaction();try{for(String day:legacy){android.content.ContentValues v=new android.content.ContentValues();v.put("day",day);v.put("at",0);try(android.database.Cursor exists=db.rawQuery("SELECT 1 FROM attendance WHERE day=?",new String[]{day})){if(!exists.moveToFirst())db.insertOrThrow("attendance",null,v);}}db.setTransactionSuccessful();}finally{db.endTransaction();}
            prefs.edit().remove("days").commit();
        }
        return Wallet.days(db);
    }
    synchronized boolean sign(){days();boolean existed=days().contains(today());Wallet.sign(study,clock.getAsLong());return !existed;}
    int streak(){
        Set<String> days=days();Calendar cursor=now();if(!days.contains(key(cursor)))cursor.add(Calendar.DATE,-1);
        int count=0;while(days.contains(key(cursor))){count++;cursor.add(Calendar.DATE,-1);}return count;
    }
}
