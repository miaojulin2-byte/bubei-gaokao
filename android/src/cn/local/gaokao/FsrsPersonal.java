package cn.local.gaokao;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
/** Personal parameters are separate from answer history; activation never rewrites existing due dates. */
final class FsrsPersonal {
 static final String TABLE="word_fsrs_personal";
 static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS word_fsrs_personal(name TEXT PRIMARY KEY,payload TEXT NOT NULL)");}
 static String activeJson(SQLiteDatabase db){try(Cursor c=db.rawQuery("SELECT payload FROM "+TABLE+" WHERE name='active'",null)){return c.moveToFirst()?c.getString(0):null;}}
 static JSONArray json(double[] weights){JSONArray out=new JSONArray();for(double value:weights)try{out.put(value);}catch(JSONException e){throw new IllegalArgumentException(e);}return out;}
 static Fsrs.Model parse(JSONArray weights)throws JSONException{if(weights==null||weights.length()!=21)throw new IllegalArgumentException("FSRS requires 21 weights");double[] w=new double[21];for(int n=0;n<21;n++)w[n]=weights.getDouble(n);return new Fsrs.Model(w);}
 static Fsrs.Model model(SQLiteDatabase db){String raw=activeJson(db);if(raw==null)return Fsrs.DEFAULT;try{JSONObject profile=new JSONObject(raw);if(!Fsrs.VERSION.equals(profile.getString("model")))throw new IllegalArgumentException("Unsupported personal model");return parse(profile.getJSONArray("weights"));}catch(JSONException e){throw new IllegalStateException("Invalid personal FSRS profile",e);}}
 static final class Snapshot {
  final FsrsTrainer.Data data;final String fingerprint,active;final double[] current;
  Snapshot(FsrsTrainer.Data data,String fingerprint,String active,double[] current){this.data=data;this.fingerprint=fingerprint;this.active=active;this.current=current;}
 }
 static Snapshot snapshot(SQLiteDatabase db){db.beginTransactionNonExclusive();try{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");LinkedHashMap<String,FsrsTrainer.History> histories=new LinkedHashMap<>();String active=activeJson(db);int order=0;
  // Read the latest bounded window; a first before_json supplies the unobserved prefix state.
  String query="SELECT r.event,r.id,r.rating,r.at,r.before_json FROM word_fsrs_review r JOIN events e ON e.id=r.event WHERE r.rowid IN (SELECT rowid FROM word_fsrs_review ORDER BY rowid DESC LIMIT 20000) AND r.corrected_by IS NULL AND e.kind IN ('self-report','self-correction','early-review') ORDER BY r.at,r.rowid";
  try(Cursor c=db.rawQuery(query,null)){while(c.moveToNext()){
   String event=c.getString(0),id=c.getString(1),before=c.getString(4);int grade=c.getInt(2);long at=c.getLong(3);for(String value:new String[]{event,id,String.valueOf(grade),String.valueOf(at),before}){byte[] bytes=(value==null?"null":value).getBytes(StandardCharsets.UTF_8);digest.update(String.valueOf(bytes.length).getBytes(StandardCharsets.UTF_8));digest.update((byte)':');digest.update(bytes);}
   FsrsTrainer.History history=histories.get(id);if(history==null){FsrsStore.State seed=FsrsStore.State.parse(before);history=new FsrsTrainer.History(id,seed==null?null:seed.memory,seed==null?0:LocalDate.parse(WordSchedule.day(seed.at,0)).toEpochDay());histories.put(id,history);}
   history.reviews.add(new FsrsTrainer.Review(id,LocalDate.parse(WordSchedule.day(at,0)).toEpochDay(),grade,order++));
  }}
  // Count/rowid also detects history changes outside the bounded fitting window.
  try(Cursor c=db.rawQuery("SELECT count(*),COALESCE(max(rowid),0) FROM word_fsrs_review",null)){c.moveToFirst();digest.update((c.getLong(0)+":"+c.getLong(1)).getBytes(StandardCharsets.UTF_8));}
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));Snapshot result=new Snapshot(new FsrsTrainer.Data(histories.values()),hash.toString(),active,model(db).weights());db.setTransactionSuccessful();return result;
 }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}finally{db.endTransaction();}}
 static boolean activate(StudyStore store,Snapshot source,FsrsTrainer.Result result,long now){if(!result.usable)return false;new Fsrs.Model(result.weights);SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
  Snapshot current=snapshot(db);if(!source.fingerprint.equals(current.fingerprint)||!Objects.equals(source.active,current.active))return false;
  JSONObject profile=new JSONObject().put("model",Fsrs.VERSION).put("weights",json(result.weights)).put("at",now).put("reviews",source.data.total).put("cards",source.data.cards).put("history",source.fingerprint).put("validation_before",result.validationBefore).put("validation_after",result.validationAfter).put("method","bounded-coordinate-sequence-logloss-v1");ContentValues value=new ContentValues();value.put("name","active");value.put("payload",profile.toString());if(db.insertWithOnConflict(TABLE,null,value,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new android.database.sqlite.SQLiteException("Cannot save personal weights");db.setTransactionSuccessful();return true;
 }catch(JSONException e){throw new IllegalStateException(e);}finally{db.endTransaction();}}
 static void restoreDefault(StudyStore store){store.getWritableDatabase().delete(TABLE,"name='active'",null);}
}
