package cn.local.gaokao;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;

/** State, first attempts and events commit in one SQLite transaction. */
public final class StudyStore extends SQLiteOpenHelper {
    private final SharedPreferences preferences;final String subject;
    public StudyStore(Context c){this(c,c instanceof MainActivity?((MainActivity)c).subject:"wenyan");}
    public StudyStore(Context c,String subject){super(c,"learning-"+subject+".db",null,12);this.subject=subject;preferences=c.getSharedPreferences("study-"+subject,Context.MODE_PRIVATE);}
    double fsrsRetention(){double value=Math.round(preferences.getFloat("fsrs_retention",.90f)*100.0)/100.0;return Double.isFinite(value)&&value>=.8&&value<=.97?value:Fsrs.DEFAULT_RETENTION;}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE retrieval_evidence(event TEXT PRIMARY KEY,subject TEXT NOT NULL,input TEXT,expected TEXT,matched INTEGER,assessment TEXT NOT NULL)");
        db.execSQL("CREATE TABLE round_session(kind TEXT PRIMARY KEY,payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE state(id TEXT PRIMARY KEY,stage INTEGER,interval INTEGER,lapses INTEGER,attempts INTEGER,due INTEGER,started INTEGER,suspended INTEGER)");
        db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY,word TEXT,mode TEXT,at INTEGER,correct INTEGER,hinted INTEGER,answer TEXT,cue TEXT,kind TEXT,context_seen INTEGER,rating INTEGER)");
        db.execSQL("CREATE TABLE first_attempt(word TEXT,mode TEXT,event TEXT,PRIMARY KEY(word,mode))");
        db.execSQL("CREATE TABLE exposure(cue TEXT PRIMARY KEY,at INTEGER)");db.execSQL("CREATE TABLE study_time(day TEXT PRIMARY KEY,millis INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE word_pass(id TEXT PRIMARY KEY,passes INTEGER NOT NULL,updated INTEGER NOT NULL)");
        db.execSQL(WORD_SCHEDULE);FsrsStore.create(db);Wallet.create(db);FsrsPersonal.create(db);WordBooks.create(db);ExampleCollections.create(db);
    }
    // Four-point cards without a row are pre-schedule records: they count as due today and get a row on their next review.
    static final String WORD_SCHEDULE="CREATE TABLE IF NOT EXISTS word_schedule(id TEXT PRIMARY KEY,step INTEGER NOT NULL,due_day TEXT NOT NULL,reviewed_day TEXT,lapses INTEGER NOT NULL,updated INTEGER NOT NULL)";
    public void onUpgrade(SQLiteDatabase d,int old,int n){if(old<12)d.execSQL("CREATE TABLE IF NOT EXISTS retrieval_evidence(event TEXT PRIMARY KEY,subject TEXT NOT NULL,input TEXT,expected TEXT,matched INTEGER,assessment TEXT NOT NULL)");if(old<4)d.execSQL("CREATE TABLE IF NOT EXISTS round_session(kind TEXT PRIMARY KEY,payload TEXT NOT NULL)");if(old<2){d.execSQL("ALTER TABLE events ADD COLUMN rating INTEGER DEFAULT 2");d.execSQL("UPDATE events SET rating=CASE WHEN correct=1 THEN 2 ELSE 0 END");}if(old<3)d.execSQL("CREATE TABLE IF NOT EXISTS study_time(day TEXT PRIMARY KEY,millis INTEGER NOT NULL)");if(old<5)d.execSQL("CREATE TABLE IF NOT EXISTS word_pass(id TEXT PRIMARY KEY,passes INTEGER NOT NULL,updated INTEGER NOT NULL)");if(old<6)d.execSQL(WORD_SCHEDULE);if(old<7)FsrsStore.create(d);if(old<8&&n>=8)Wallet.create(d);if(old<9&&n>=9)FsrsPersonal.create(d);if(old<10&&n>=10)WordBooks.create(d);if(old<11&&n>=11)ExampleCollections.create(d);}
    static final String CHECK_IN_GENERATION="check-in-generation";
    /** One transaction clears all learning data. The generation also invalidates attendance preferences. */
    public void resetLearning(){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            for(String table:new String[]{"retrieval_evidence","state","events","first_attempt","exposure","study_time","round_session","word_pass","word_schedule","word_fsrs_review","word_fsrs","familiar","attendance","reward_progress","word_fsrs_personal"})db.delete(table,null,null);
            round(CHECK_IN_GENERATION,java.util.UUID.randomUUID().toString());
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public int wordPasses(String id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT passes FROM word_pass WHERE id=?",new String[]{id})){return c.moveToFirst()?c.getInt(0):0;}
    }

    public boolean wordStarted(String id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM word_pass WHERE id=?",new String[]{id})){return c.moveToFirst();}
    }

    java.util.HashSet<String> familiarIds(){java.util.HashSet<String> result=new java.util.HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM familiar",null)){while(c.moveToNext())result.add(c.getString(0));}return result;}
    void setFamiliar(String id,boolean marked){if(marked){ContentValues value=new ContentValues();value.put("id",id);value.put("marked",WordSchedule.now());if(getWritableDatabase().insertWithOnConflict("familiar",null,value,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Cannot mark familiar");}else getWritableDatabase().delete("familiar","id=?",new String[]{id});}
    boolean markFamiliar(String id,String kind,String expected,String next,long now){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{if(!java.util.Objects.equals(expected,round(kind)))return false;setFamiliar(id,true);round(kind,next);db.setTransactionSuccessful();return true;}finally{db.endTransaction();}}
    private String hardKey(String id){return "word-card-hard:"+id;}
    private JSONObject hardState(String id){
        String raw=round(hardKey(id));try{return raw==null?new JSONObject():new JSONObject(raw);}catch(JSONException e){throw new IllegalStateException("Invalid difficulty state",e);}
    }
    /** Predict the result for UI feedback; the transaction calculates it again before saving. */
    int wordAnswerPasses(String id,int rating,String flow,long now){
        if(rating==WordSchedule.AGAIN)return 0;
        if(subject.equals("biology"))return 4;
        if(flow.equals("review")&&rating==WordSchedule.HARD){
            JSONObject hard=hardState(id);String today=WordSchedule.day(now,0);
            if(!today.equals(wordReviewedDay(id))&&!today.equals(hard.optString("day"))&&hard.optInt("count")>=2)return 0;
        }
        return Math.min(4,wordPasses(id)+1);
    }
    private void updateHardState(String id,int rating,boolean learn,boolean early,boolean correction,int passes,long now){
        String key=hardKey(id),today=WordSchedule.day(now,0);
        if(correction||learn||passes<4||(!early&&rating!=WordSchedule.HARD)){round(key,null);return;}
        if(early||today.equals(wordReviewedDay(id)))return;
        JSONObject hard=hardState(id);if(today.equals(hard.optString("day")))return;
        try{hard.put("count",hard.optInt("count")+1).put("day",today);round(key,hard.toString());}catch(JSONException e){throw new IllegalStateException(e);}
    }
    /** Self-report only. Navigation never calls this method. Snapshot and result commit together. */
    public boolean wordCardAnswer(String event,String id,boolean correct,boolean correction,
                                  String cue,String kind,String expected,String payload,long now){
        return wordCardAnswer(event,id,correct?WordSchedule.GOOD:WordSchedule.AGAIN,correction,kind.startsWith("word-card-early")?"early":kind.startsWith("word-card-review")?"review":"learn",cue,kind,expected,payload,now);
    }
    /**
     * flow is learn, review (due) or early (not yet due). Points, schedule, event, first attempt and
     * snapshot commit in one transaction; a repeated event id or stale snapshot changes nothing.
     */
    public boolean wordCardAnswer(String event,String id,int rating,boolean correction,String flow,
                                  String cue,String kind,String expected,String payload,long now){
        if(rating<WordSchedule.AGAIN||rating>WordSchedule.EASY)throw new IllegalArgumentException("Invalid rating");
        boolean learn=flow.equals("learn"),early=flow.equals("early");
        if(!learn&&!early&&!flow.equals("review"))throw new IllegalArgumentException("Invalid flow");
        String mode=learn?"word_card_self":"word_card_review";
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
        try{
            if(!java.util.Objects.equals(expected,round(kind)))return false;
            try(Cursor c=d.rawQuery("SELECT 1 FROM events WHERE id=?",new String[]{event})){if(c.moveToFirst())return false;}
            if(correction){
                String original=event.endsWith(".correction")?event.substring(0,event.length()-11):"";
                try(Cursor c=d.rawQuery("SELECT 1 FROM events WHERE id=? AND word=? AND mode=? AND correct=1 AND kind IN ('self-report','early-review')",new String[]{original,id,mode})){
                    if(!c.moveToFirst())throw new IllegalStateException("No self-report to correct");
                }
            }
            if(correction){
                rating=WordSchedule.AGAIN;
                String original=event.substring(0,event.length()-11);
                try(Cursor c=d.rawQuery("SELECT 1 FROM events WHERE word=? AND id<>? AND kind IN ('self-report','early-review','self-correction') AND rowid>(SELECT rowid FROM events WHERE id=?)",new String[]{id,original,original})){if(c.moveToFirst())return false;}
            }
            if(!correction&&familiarIds().contains(id))return false;
            int current=wordPasses(id);String due=wordDueDay(id),today=WordSchedule.day(now,0);
            if(!correction){
                if(learn&&current>=4)return false;
                if(!learn&&(current<4||(early?(due==null||due.compareTo(today)<=0):(due!=null&&due.compareTo(today)>0))))return false;
            }
            int before=wordPasses(id),passes=correction?0:wordAnswerPasses(id,rating,flow,now);
            boolean correct=passes>0;
            updateHardState(id,rating,learn,early,correction,passes,now);
            ContentValues v=new ContentValues();v.put("id",id);v.put("passes",passes);v.put("updated",now);
            if(d.insertWithOnConflict("word_pass",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Cannot save points");
            schedule(d,id,event,learn,early,before,passes,rating,correction,now);
            ContentValues e=new ContentValues();e.put("id",event);e.put("word",id);e.put("mode",mode);e.put("at",now);
            e.put("correct",correct?1:0);e.put("hinted",1);e.put("answer",correction?"correction":rating==WordSchedule.HARD&&passes==0?"self-hard-relearn":new String[]{"self-forgot","self-hard","self-recalled","self-easy"}[rating]);
            e.put("cue",cue);e.put("kind",correction?"self-correction":early?"early-review":"self-report");e.put("context_seen",1);e.put("rating",correction?0:rating);
            d.insertOrThrow("events",null,e);
            if(!correction){ContentValues first=new ContentValues();first.put("word",id);first.put("mode",mode);first.put("event",event);
                d.insertWithOnConflict("first_attempt",null,first,SQLiteDatabase.CONFLICT_IGNORE);}
            try{JSONObject submission=new JSONObject(payload).optJSONObject("submission");if(submission!=null){ContentValues evidence=new ContentValues();evidence.put("event",event);evidence.put("subject",subject);evidence.put("input",submission.optString("input"));evidence.put("expected",submission.optString("expected"));if(submission.has("matched"))evidence.put("matched",submission.getBoolean("matched")?1:0);evidence.put("assessment","self-report-after-reveal");d.insertOrThrow("retrieval_evidence",null,evidence);}}catch(JSONException failure){throw new IllegalStateException(failure);}
            round(kind,payload);
            if(!correction)Wallet.answerRewards(this,id,flow,before,passes,payload,now,Math.max(1,preferences.getInt("daily",10)));
            d.setTransactionSuccessful();return true;
        }finally{d.endTransaction();}
    }
    // FSRS supplies adaptive intervals; four-point graduation/relearning and early practice are app policies.
    private void schedule(SQLiteDatabase db,String id,String event,boolean learn,boolean early,int before,int passes,int rating,boolean correction,long now){
        Integer interval=FsrsStore.apply(db,id,event,rating,learn,early,passes,correction,now,fsrsRetention());
        if(interval==null)return;
        int[] old=wordSchedule(id);int lapses=(old==null?0:old[1])+(!learn&&before>=4&&passes<4?1:0);
        ContentValues value=new ContentValues();value.put("id",id);value.put("step",0);value.put("due_day",WordSchedule.day(now,interval));value.put("reviewed_day",WordSchedule.day(now,0));value.put("lapses",lapses);value.put("updated",now);
        if(db.insertWithOnConflict("word_schedule",null,value,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Cannot save word schedule");
    }
    /** {step, lapses} or null when the card has never been scheduled. */
    public int[] wordSchedule(String id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT step,lapses FROM word_schedule WHERE id=?",new String[]{id})){return c.moveToFirst()?new int[]{c.getInt(0),c.getInt(1)}:null;}
    }
    public String wordDueDay(String id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT due_day FROM word_schedule WHERE id=?",new String[]{id})){return c.moveToFirst()?c.getString(0):null;}
    }
    String wordReviewedDay(String id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT reviewed_day FROM word_schedule WHERE id=?",new String[]{id})){return c.moveToFirst()?c.getString(0):null;}
    }
    /** Four-point cards due on or before the day; unscheduled four-point cards are due. */
    public java.util.HashSet<String> wordDueIds(String today){
        java.util.HashSet<String> ids=new java.util.LinkedHashSet<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT p.id FROM word_pass p LEFT JOIN word_schedule s ON s.id=p.id WHERE p.passes>=4 AND NOT EXISTS(SELECT 1 FROM familiar f WHERE f.id=p.id) AND (s.due_day IS NULL OR s.due_day<=?) ORDER BY COALESCE(s.due_day,?),p.updated,p.id",new String[]{today,today})){while(c.moveToNext())ids.add(c.getString(0));}
        return ids;
    }
    /** Four-point cards not yet due, earliest first: the early-review pool. */
    public java.util.LinkedHashSet<String> wordEarlyIds(String today){
        java.util.LinkedHashSet<String> ids=new java.util.LinkedHashSet<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT p.id FROM word_pass p JOIN word_schedule s ON s.id=p.id WHERE p.passes>=4 AND NOT EXISTS(SELECT 1 FROM familiar f WHERE f.id=p.id) AND s.due_day>? ORDER BY s.due_day,p.id",new String[]{today})){while(c.moveToNext())ids.add(c.getString(0));}
        return ids;
    }

    public String pendingNewWord(){
        String payload=round("word-card-learn");if(payload==null)return "";
        try{JSONObject s=new JSONObject(payload);return s.getJSONArray("ids").optString(s.getInt("index"),"");}
        catch(JSONException e){throw new IllegalStateException("Invalid word-card session",e);}
    }

    public int wordStartedCount(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM word_pass",null)){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int wordFourPointCount(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM word_pass WHERE passes>=4",null)){return c.moveToFirst()?c.getInt(0):0;}
    }

    public int wordReviewCount(){return wordDueIds(WordSchedule.today()).size();}

    public StudyEngine.State get(String id){
        StudyEngine.State s=new StudyEngine.State();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM state WHERE id=?",new String[]{id})){if(c.moveToFirst()){
            s.stage=c.getInt(1);s.interval=c.getInt(2);s.lapses=c.getInt(3);s.attempts=c.getInt(4);s.due=c.getLong(5);s.started=c.getLong(6);s.suspended=c.getInt(7)==1;
        }}return s;
    }
    void save(SQLiteDatabase d,String id,StudyEngine.State s){ContentValues v=new ContentValues();v.put("id",id);v.put("stage",s.stage);v.put("interval",s.interval);v.put("lapses",s.lapses);v.put("attempts",s.attempts);v.put("due",s.due);v.put("started",s.started);v.put("suspended",s.suspended?1:0);if(d.insertWithOnConflict("state",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Cannot save word state");}
    public void start(String id,long now){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{save(d,id,StudyEngine.start(get(id),now));d.setTransactionSuccessful();}finally{d.endTransaction();}}
    public void suspend(String id){SQLiteDatabase d=getWritableDatabase();StudyEngine.State s=get(id);s.suspended=!s.suspended;save(d,id,s);}
    public boolean answer(String event,String id,String mode,boolean correct,boolean hinted,String answer,String cue,long now){
        return answer(event,id,mode,correct?2:0,hinted,answer,cue,now);
    }
    public boolean answer(String event,String id,String mode,int rating,boolean hinted,String answer,String cue,long now){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            try(Cursor c=d.rawQuery("SELECT 1 FROM events WHERE id=?",new String[]{event})){if(c.moveToFirst())return false;}
            ContentValues v=new ContentValues();v.put("id",event);v.put("word",id);v.put("mode",mode);v.put("at",now);v.put("correct",rating>0?1:0);v.put("rating",rating);v.put("hinted",hinted?1:0);v.put("answer",answer);v.put("cue",cue);v.put("kind",answer.equals("correction")?"self-correction":mode.equals("meaning")?"self-report":"typed-reading");v.put("context_seen",seen(cue)?1:0);d.insertOrThrow("events",null,v);
            ContentValues first=new ContentValues();first.put("word",id);first.put("mode",mode);first.put("event",event);d.insertWithOnConflict("first_attempt",null,first,SQLiteDatabase.CONFLICT_IGNORE);
            save(d,id,StudyEngine.answer(get(id),rating,hinted,now));d.setTransactionSuccessful();return true;
        }finally{d.endTransaction();}
    }
    public String round(String kind){try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM round_session WHERE kind=?",new String[]{kind})){return c.moveToFirst()?c.getString(0):null;}}
    public void round(String kind,String payload){if(payload==null){getWritableDatabase().delete("round_session","kind=?",new String[]{kind});return;}ContentValues v=new ContentValues();v.put("kind",kind);v.put("payload",payload);if(getWritableDatabase().insertWithOnConflict("round_session",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Cannot save learning group");}
    public boolean reservedElsewhere(String id,String kind){try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM round_session WHERE kind<>? AND kind NOT LIKE 'word-card-%' AND kind<>'check-in-generation'",new String[]{kind})){while(c.moveToNext()){try{JSONObject j=new JSONObject(c.getString(0));if(j.getJSONArray("queue").length()==0&&!j.getBoolean("feedback")&&!((j.optBoolean("readingRequired",false)||j.optString("policy").equals("review-v1"))&&j.optInt("postReview")<2))continue;JSONArray ids=j.getJSONArray("ids");for(int i=0;i<ids.length();i++)if(ids.getString(i).equals(id))return true;}catch(JSONException e){throw new IllegalStateException("Invalid saved learning group",e);}}}return false;}
    /** An answer, its first-attempt identity and the resumable group commit together. */
    public boolean roundAnswer(String event,String id,String mode,int rating,boolean hinted,String typed,String cue,String kind,String payload,boolean completed,boolean correction,long now){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            try(Cursor c=d.rawQuery("SELECT 1 FROM events WHERE id=?",new String[]{event})){if(c.moveToFirst())return false;}
            StudyEngine.State s=StudyEngine.start(get(id),now);if(!correction)s.attempts++;
            if(rating<2||hinted){s.lapses++;s.stage=1;s.interval=0;}
            // Japanese groups reserve an unfinished word with MAX due. The Wenyan
            // subpage has no multi-phase requeue, so a miss becomes due again in
            // ten minutes instead of staying invisible to review forever.
            boolean wenyan=kind.startsWith("wenyan");
            s.due=wenyan?now+10*StudyEngine.MINUTE:Long.MAX_VALUE;
            if(completed){boolean reviewKind=kind.equals("review")||kind.equals("wenyan-review");if(reviewKind&&s.stage>=3)s.interval=Math.min(StudyEngine.DAYS.length-1,s.interval+1);else s.interval=0;boolean needsReading=readingRequired(payload);s.stage=needsReading?2:3;s.due=needsReading?Long.MAX_VALUE:now+StudyEngine.DAYS[s.interval]*StudyEngine.DAY;}
            save(d,id,s);
            ContentValues v=new ContentValues();v.put("id",event);v.put("word",id);v.put("mode",mode);v.put("at",now);v.put("correct",rating==2&&!hinted?1:0);v.put("rating",rating);v.put("hinted",hinted?1:0);v.put("answer",typed);v.put("cue",cue);boolean choiceMode=mode.equals("choice")||mode.equals("listening")||mode.endsWith("_choice")||mode.equals("context_contrast");v.put("kind",correction?"self-correction":choiceMode&&!typed.startsWith("self-")?"meaning-choice":"self-report");v.put("context_seen",seen(cue)?1:0);d.insertOrThrow("events",null,v);
            ContentValues f=new ContentValues();f.put("word",id);f.put("mode",mode);f.put("event",event);d.insertWithOnConflict("first_attempt",null,f,SQLiteDatabase.CONFLICT_IGNORE);
            round(kind,payload);d.setTransactionSuccessful();return true;
        }finally{d.endTransaction();}
    }
    private static boolean readingRequired(String payload){try{return new JSONObject(payload).optBoolean("readingRequired",false);}catch(JSONException e){throw new IllegalStateException("Invalid reading session",e);}}
    /** Keep old callers compatible; new reading sessions always supply their own kind. */
    public boolean spellingAnswer(String event,String id,boolean correct,boolean hinted,String input,String cue,String parentPayload,long now){return spellingAnswer(event,id,correct,hinted,input,cue,"review",parentPayload,now);}
    /** Reading evidence, state and the correct learn/review snapshot commit together once. */
    public boolean spellingAnswer(String event,String id,boolean correct,boolean hinted,String input,String cue,String sessionKind,String parentPayload,long now){
        if(!sessionKind.equals("learn")&&!sessionKind.equals("review"))throw new IllegalArgumentException("Invalid session kind");SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            try(Cursor c=d.rawQuery("SELECT 1 FROM events WHERE id=?",new String[]{event})){if(c.moveToFirst())return false;}
            if(readingRequired(parentPayload)){StudyEngine.State state=get(id);if(state.suspended||state.stage==0)throw new IllegalStateException("Word is not active for reading");state.attempts++;if(correct&&!hinted){if(state.stage!=3||state.due==Long.MAX_VALUE){state.stage=3;state.due=now+StudyEngine.DAYS[state.interval]*StudyEngine.DAY;}}else{state.stage=2;state.interval=0;state.lapses++;state.due=Long.MAX_VALUE;}save(d,id,state);}
            ContentValues v=new ContentValues();v.put("id",event);v.put("word",id);v.put("mode","reading");v.put("at",now);v.put("correct",correct&&!hinted?1:0);v.put("rating",correct&&!hinted?2:0);v.put("hinted",hinted?1:0);v.put("answer",input);v.put("cue",cue);v.put("kind","typed-reading");v.put("context_seen",seen(cue)?1:0);d.insertOrThrow("events",null,v);
            ContentValues f=new ContentValues();f.put("word",id);f.put("mode","reading");f.put("event",event);d.insertWithOnConflict("first_attempt",null,f,SQLiteDatabase.CONFLICT_IGNORE);round(sessionKind,parentPayload);d.setTransactionSuccessful();return true;
        }finally{d.endTransaction();}
    }
    /** Add the reading gate to resumable older groups without deleting any progress. */
    public void upgradeReadingSessions(){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{java.util.LinkedHashMap<String,String> updates=new java.util.LinkedHashMap<>();try(Cursor c=d.rawQuery("SELECT kind,payload FROM round_session WHERE kind IN ('learn','review')",null)){while(c.moveToNext()){try{JSONObject j=new JSONObject(c.getString(1));if(!j.has("readingRequired")){j.put("readingRequired",j.optInt("postReview")<2);updates.put(c.getString(0),j.toString());}}catch(JSONException e){throw new IllegalStateException("Cannot upgrade saved reading session",e);}}}for(java.util.Map.Entry<String,String> entry:updates.entrySet())round(entry.getKey(),entry.getValue());d.setTransactionSuccessful();}finally{d.endTransaction();}}
    public void expose(String cue){ContentValues v=new ContentValues();v.put("cue",cue);v.put("at",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("exposure",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    public boolean seen(String cue){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM exposure WHERE cue=?",new String[]{cue})){return c.moveToFirst();}}
    public int count(String sql,long since){try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{Long.toString(since)})){c.moveToFirst();return c.getInt(0);}}
    public JSONArray history(String id)throws Exception {JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT mode,at,correct,hinted,answer,rating FROM events WHERE word=? ORDER BY at DESC LIMIT 12",new String[]{id})){while(c.moveToNext())a.put(new JSONObject().put("mode",c.getString(0)).put("at",c.getLong(1)).put("correct",c.getInt(2)==1).put("hinted",c.getInt(3)==1).put("answer",c.getString(4)).put("rating",c.getInt(5)));}return a;}
    public void addDuration(long end,long millis){if(millis<=0)return;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT);while(millis>0){java.util.Calendar c=java.util.Calendar.getInstance();c.setTimeInMillis(end-1);String day=fmt.format(c.getTime());c.set(java.util.Calendar.HOUR_OF_DAY,0);c.set(java.util.Calendar.MINUTE,0);c.set(java.util.Calendar.SECOND,0);c.set(java.util.Calendar.MILLISECOND,0);long piece=Math.min(millis,end-c.getTimeInMillis());db.execSQL("INSERT OR IGNORE INTO study_time(day,millis) VALUES(?,0)",new Object[]{day});db.execSQL("UPDATE study_time SET millis=millis+? WHERE day=?",new Object[]{piece,day});end-=piece;millis-=piece;}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public long duration(String day){try(Cursor c=getReadableDatabase().rawQuery(day==null?"SELECT COALESCE(SUM(millis),0) FROM study_time":"SELECT COALESCE(SUM(millis),0) FROM study_time WHERE day=?",day==null?null:new String[]{day})){c.moveToFirst();return c.getLong(0);}}
    public JSONObject export()throws Exception{JSONObject out=new JSONObject();out.put("schema_version",2);for(String table:new String[]{"state","events","first_attempt","exposure","study_time","round_session"}){JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM "+table,null)){while(c.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<c.getColumnCount();i++)row.put(c.getColumnName(i),c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));rows.put(row);}}out.put(table,rows);}return out;}
}
