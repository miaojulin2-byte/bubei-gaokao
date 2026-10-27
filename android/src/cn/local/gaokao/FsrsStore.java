package cn.local.gaokao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** SQL adapter; all calls participate in StudyStore's existing answer transaction. */
final class FsrsStore {
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS word_fsrs(id TEXT PRIMARY KEY,stability REAL NOT NULL,difficulty REAL NOT NULL,last_review INTEGER NOT NULL,reps INTEGER NOT NULL,lapses INTEGER NOT NULL,version TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS word_fsrs_review(event TEXT PRIMARY KEY,id TEXT NOT NULL,rating INTEGER NOT NULL,at INTEGER NOT NULL,retention REAL NOT NULL,before_json TEXT,after_json TEXT NOT NULL,interval_days INTEGER NOT NULL,model_interval INTEGER NOT NULL,policy TEXT NOT NULL,corrected_by TEXT)");
    }
    static final class State {
        final Fsrs.Memory memory;final long at;final int reps,lapses;
        State(Fsrs.Memory memory,long at,int reps,int lapses){this.memory=memory;this.at=at;this.reps=reps;this.lapses=lapses;}
        String json(){try{return new JSONObject().put("stability",memory.stability).put("difficulty",memory.difficulty).put("last_review",at).put("reps",reps).put("lapses",lapses).put("version",Fsrs.VERSION).toString();}catch(JSONException e){throw new IllegalStateException(e);}}
        static State parse(String value){if(value==null)return null;try{JSONObject j=new JSONObject(value);return new State(new Fsrs.Memory(j.getDouble("stability"),j.getDouble("difficulty")),j.getLong("last_review"),j.getInt("reps"),j.getInt("lapses"));}catch(JSONException e){throw new IllegalStateException(e);}}
    }
    static State read(SQLiteDatabase db,String id){try(Cursor c=db.rawQuery("SELECT stability,difficulty,last_review,reps,lapses,version FROM word_fsrs WHERE id=?",new String[]{id})){if(!c.moveToFirst())return null;if(!Fsrs.VERSION.equals(c.getString(5)))throw new IllegalStateException("Unsupported FSRS version");return new State(new Fsrs.Memory(c.getDouble(0),c.getDouble(1)),c.getLong(2),c.getInt(3),c.getInt(4));}}
    static double elapsed(long before,long now){if(now<before)throw new IllegalStateException("Device clock precedes previous FSRS review");return ChronoUnit.DAYS.between(LocalDate.parse(WordSchedule.day(before,0)),LocalDate.parse(WordSchedule.day(now,0)));}
    /** Null means practice only. Zero means relearning; a positive value is a local-calendar-day interval. */
    static Integer apply(SQLiteDatabase db,String id,String event,int rating,boolean learn,boolean early,int passes,boolean correction,long now,double retention){
        Fsrs.Model model=FsrsPersonal.model(db);State old=read(db,id);long reviewedAt=now;String policy="fsrs-review";
        if(correction){
            String original=event.substring(0,event.length()-11);boolean found=false;
            try(Cursor c=db.rawQuery("SELECT before_json,at,corrected_by,after_json FROM word_fsrs_review WHERE event=? AND id=?",new String[]{original,id})){if(c.moveToFirst()){if(c.getString(2)!=null)throw new IllegalStateException("FSRS review already corrected");old=State.parse(c.getString(0));reviewedAt=c.getLong(1);try{JSONArray weights=new JSONObject(c.getString(3)).optJSONArray("weights");model=weights==null?Fsrs.DEFAULT:FsrsPersonal.parse(weights);}catch(JSONException e){throw new IllegalStateException(e);}found=true;}}
            if(learn&&!found)return null;
            if(found){ContentValues correctionRow=new ContentValues();correctionRow.put("corrected_by",event);db.update("word_fsrs_review",correctionRow,"event=?",new String[]{original});}
            rating=WordSchedule.AGAIN;policy="correction";
        }else{
            if(early&&passes>0)return null;
            if(!learn&&passes>0&&old!=null&&WordSchedule.day(old.at,0).equals(WordSchedule.day(now,0)))return null;
        }
        double days=old==null?0:elapsed(old.at,reviewedAt);
        Fsrs.Memory next=model.next(old==null?null:old.memory,days,rating+1);
        State state=new State(next,reviewedAt,old==null?1:old.reps+1,(old==null?0:old.lapses)+(rating==WordSchedule.AGAIN?1:0));
        int predicted=model.interval(next,retention),interval=predicted;
        if(passes<4){interval=0;if(!correction)policy=learn?"four-point-learning":"four-point-relearning";}
        else if(learn){if(db.getPath().endsWith("learning-biology.db")){interval=predicted;policy="biology-direct-fsrs";}else{interval=1;policy="four-point-graduation";}}
        else if(old==null){interval=1;policy="legacy-initialization";}
        ContentValues value=new ContentValues();value.put("id",id);value.put("stability",next.stability);value.put("difficulty",next.difficulty);value.put("last_review",reviewedAt);value.put("reps",state.reps);value.put("lapses",state.lapses);value.put("version",Fsrs.VERSION);
        if(db.insertWithOnConflict("word_fsrs",null,value,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new android.database.sqlite.SQLiteException("Cannot save FSRS memory");
        ContentValues log=new ContentValues();log.put("event",event);log.put("id",id);log.put("rating",rating+1);log.put("at",reviewedAt);log.put("retention",retention);log.put("before_json",old==null?null:old.json());try{log.put("after_json",new JSONObject(state.json()).put("weights",FsrsPersonal.json(model.weights())).toString());}catch(JSONException e){throw new IllegalStateException(e);}log.put("interval_days",interval);log.put("model_interval",predicted);log.put("policy",policy);db.insertOrThrow("word_fsrs_review",null,log);
        return interval;
    }
}
