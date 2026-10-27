package cn.local.gaokao;

import android.app.Instrumentation;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Four self-reports gate review; answers and queue transitions must remain atomic. */
public final class FourPointInstrumentation extends Instrumentation {
    MainActivity a;int checks;long now=System.currentTimeMillis();
    void tomorrow(){java.util.Calendar c=java.util.Calendar.getInstance();c.setTimeInMillis(now);c.add(java.util.Calendar.DATE,1);now=c.getTimeInMillis();}
    final String volume="必修上",firstId="gk-shi-001",secondId="gk-shi-002";
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    void ui(Runnable r){final Throwable[] failure={null};runOnMainSync(()->{try{r.run();}catch(Throwable t){failure[0]=t;}});waitForIdleSync();if(failure[0]!=null)throw new AssertionError(failure[0]);}
    TextView find(View v,String text){if(v.getVisibility()!=View.VISIBLE)return null;if(v instanceof TextView&&text.replaceAll("\\s","").equals(((TextView)v).getText().toString().replaceAll("\\s","")))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){TextView t=find(g.getChildAt(n),text);if(t!=null)return t;}}return null;}
    TextView find(String s){return find(a.currentCanvas,s);}
    void click(String text){android.os.SystemClock.sleep(400);ui(()->{TextView button=find(text);check(button!=null&&button.isEnabled(),"action exists: "+text);button.performClick();});android.os.SystemClock.sleep(800);}
    void rate(boolean correct){click("看释义");click(find("良好")!=null?(correct?"良好":"重来"):(correct?"想对了":"没记住"));}
    String kind(boolean review){return WordVolumes.kind(review,review?WordVolumes.ALL:volume);}
    JSONObject snapshot(boolean review)throws Exception{return new JSONObject(a.store.round(kind(review)));}
    int events(String id){try(Cursor c=a.store.getReadableDatabase().rawQuery("SELECT count(*) FROM events WHERE word=?",new String[]{id})){c.moveToFirst();return c.getInt(0);}}
    String first(String id){try(Cursor c=a.store.getReadableDatabase().rawQuery("SELECT event FROM first_attempt WHERE word=? AND mode='word_card_self'",new String[]{id})){return c.moveToFirst()?c.getString(0):null;}}
    JSONObject fixture(int index,boolean old)throws Exception{
        JSONObject s=new JSONObject();s.put("ids",new JSONArray().put(firstId).put(secondId));s.put("index",index);s.put("context",0);s.put("revealed",false);s.put("assessed",false);s.put("correct",false);s.put("corrected",false);s.put("event",java.util.UUID.randomUUID().toString());s.put("catalog_revision",1);if(!old)s.put("learning_revision",2);return s;
    }
    void capture(String name)throws Exception{android.os.SystemClock.sleep(700);android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();java.io.File dir=new java.io.File(a.getExternalFilesDir(null),"refinement");dir.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    boolean remaining(JSONObject s,String id)throws Exception{for(int n=s.getInt("index");n<s.getJSONArray("ids").length();n++)if(id.equals(s.getJSONArray("ids").getString(n)))return true;return false;}
    void projectedCounts()throws Exception{
        java.util.HashSet<String> ready=WordVolumes.fourPointIds(a.store),due=WordVolumes.dueIds(a.store);
        for(String book:WordVolumes.NAMES){JSONArray cards=WordVolumes.cards(a,book);int expected=0,complete=0;for(int n=0;n<cards.length();n++){String id=cards.getJSONObject(n).getString("id");if(due.contains(id))expected++;if(ready.contains(id))complete++;}
            check(WordVolumes.count(a,book,2)==expected,"review count is exactly four-point words: "+book);
            check(WordVolumes.count(a,book,1)==cards.length()-complete,"new learning includes all unfinished words: "+book);
        }
    }
    String learningState(){StringBuilder b=new StringBuilder();for(String q:new String[]{"SELECT id||':'||passes FROM word_pass ORDER BY id","SELECT word||':'||mode||':'||event FROM first_attempt ORDER BY word,mode","SELECT id FROM events ORDER BY id","SELECT kind||':'||payload FROM round_session ORDER BY kind"})try(Cursor c=a.store.getReadableDatabase().rawQuery(q,null)){while(c.moveToNext())b.append(c.getString(0)).append('\n');b.append("--\n");}return b.toString();}
    void waitResumed(boolean expected){for(int n=0;n<50&&a.resumed!=expected;n++)android.os.SystemClock.sleep(100);check(a.resumed==expected,"activity resumed="+expected);}
    // Leave through the launcher and return to the same activity instance, as a real background trip does.
    void backgroundAndReturn(){android.os.SystemClock.sleep(400);getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);waitResumed(false);android.os.SystemClock.sleep(600);getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));waitResumed(true);waitForIdleSync();}
    void resumedReviewCount(String text,String scope)throws Exception{
        ui(()->{a.prefs.edit().putString("word_volume",volume).putString("word_text",text).commit();a.home();});
        int expected=WordVolumes.count(a,WordVolumes.ALL,"",2);check(expected>0&&expected!=a.wenyanReviewCount(),"resume fixture distinguishes word review from quanxue: "+scope);
        String before=learningState();backgroundAndReturn();
        check("home".equals(a.page)&&String.valueOf(expected).equals(a.reviewNumber.getText().toString()),"returning home shows selected four-point count: "+scope);
        check(("Review "+expected).equals(String.valueOf(((View)a.reviewNumber.getParent()).getContentDescription())),"returning home updates Review description: "+scope);
        check(before.equals(learningState()),"returning home creates no answer or progress change: "+scope);
        ui(()->((View)a.reviewNumber.getParent()).performClick());android.os.SystemClock.sleep(400);JSONObject s=new JSONObject(a.store.round(WordVolumes.kind(true,WordVolumes.ALL,"")));
        check(s.getJSONArray("ids").length()-s.getInt("index")==expected&&find("看释义")!=null,"Review opens the same scope and count: "+scope);
        String opened=learningState();backgroundAndReturn();check(opened.equals(learningState())&&find("看释义")!=null,"returning to an open review leaves its queue untouched: "+scope);
    }
    void runChecks()throws Exception{
        check(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish"),"emulator only");
        WordSchedule.clock=()->now;
        a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        ui(()->a.prefs.edit().putString("word_volume",volume).putString("word_text","").commit());
        check(a.wordReviewCount()==0&&WordVolumes.count(a,volume,2)==0,"no unseen word enters review");
        a.store.round(kind(false),fixture(0,false).toString());ui(()->a.openWords());
        check(find("0 / 2")!=null,"header counts distinct words rather than repeat queue slots");
        click("看释义");check(events(firstId)==0&&a.store.wordPasses(firstId)==0,"revealing a meaning gives no point");
        final TextView[] stale={null};ui(()->{stale[0]=find("想对了");stale[0].performClick();stale[0].performClick();});android.os.SystemClock.sleep(800);
        check(a.store.wordPasses(firstId)==1&&events(firstId)==1,"double tap commits only one self-report");
        check(snapshot(false).getInt("index")==1&&remaining(snapshot(false),firstId)&&find("看释义")!=null,"one-point word is requeued while next word starts hidden");
        check(a.wordReviewCount()==0&&WordVolumes.count(a,volume,2)==0,"one point never enters review");String firstEvent=first(firstId);
        String saved=a.store.round(kind(false));ui(()->a.finish());a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();ui(()->a.openWords());
        check(saved.equals(a.store.round(kind(false)))&&a.store.wordPasses(firstId)==1,"restart preserves the repeated-learning queue without rescore");
        // A failed snapshot write must roll back both the point and the requeue.
        click("看释义");saved=a.store.round(kind(false));
        a.store.getWritableDatabase().execSQL("CREATE TRIGGER four_point_failure BEFORE INSERT ON round_session BEGIN SELECT RAISE(ABORT,'injected failure'); END");
        click("想对了");
        check(saved.equals(a.store.round(kind(false)))&&events(secondId)==0&&a.store.wordPasses(secondId)==0&&find("想对了")!=null,"failed answer restores the same revealed word and unchanged queue");
        a.store.getWritableDatabase().execSQL("DROP TRIGGER four_point_failure");click("想对了");
        check(a.store.wordPasses(secondId)==1&&events(secondId)==1,"retry commits once");
        rate(false);check(a.store.wordPasses(firstId)==0&&remaining(snapshot(false),firstId)&&firstEvent.equals(first(firstId)),"forgotten new word resets and stays in learning without replacing first");
        rate(true);check(a.store.wordPasses(secondId)==2&&a.wordReviewCount()==0,"two points remain in learning");
        rate(true);rate(true);check(a.store.wordPasses(secondId)==3&&a.wordReviewCount()==0,"three points remain in learning");
        rate(true);rate(true);
        check(a.store.wordPasses(secondId)==4&&a.wordReviewCount()==0&&!remaining(snapshot(false),secondId),"fourth self-report alone moves word into review");
        check(a.store.wordPasses(firstId)==2&&remaining(snapshot(false),firstId),"unfinished companion remains queued");
        capture("four-point-first-ready");rate(true);check(a.store.wordPasses(firstId)==3&&find("看释义")!=null,"last unfinished word continues immediately");rate(true);
        check(find("已完成")!=null&&a.store.wordPasses(firstId)==4&&a.wordReviewCount()==0,"learning completes only after every queued word reaches four");
        check(a.currentCanvas.findViewWithTag("completion-check")!=null&&a.dock.getVisibility()==View.GONE&&find("返回")==null,"completion has animated check and no bottom return button");
        check(find("2 / 2")!=null,"completed progress is based on words");capture("four-point-learning-complete");projectedCounts();tomorrow();
        ui(()->{a.home();a.openWords(true);});check(snapshot(true).getJSONArray("ids").length()==2,"review takes only four-point words");
        rate(false);check(a.store.wordPasses(firstId)==0&&a.wordReviewCount()==1,"forgetting in review returns word to unfinished pool");
        rate(true);check(a.store.wordPasses(secondId)==4&&find("已完成")!=null,"review stays finite and does not exceed four points");
        ui(()->{a.home();a.openWords();});check(remaining(snapshot(false),firstId)&&!remaining(snapshot(false),secondId),"new learning contains forgotten word and excludes four-point word");
        check(firstEvent.equals(first(firstId)),"later review retains original first attempt");projectedCounts();now=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).parse(a.store.wordDueDay(secondId)).getTime()+12*3600000L;
        // Migrate pre-rule review snapshots: unfinished words cannot leak into review.
        a.store.round(kind(true),fixture(0,true).toString());ui(()->{a.home();a.openWords(true);});
        JSONObject migrated=snapshot(true);check(migrated.getJSONArray("ids").length()==1&&secondId.equals(migrated.getJSONArray("ids").getString(migrated.getInt("index"))),"old review queue filters out zero-point words");
        int before=events(firstId);a.store.round(kind(false),fixture(1,true).toString());ui(()->{a.home();a.openWords();});
        check(remaining(snapshot(false),firstId)&&!remaining(snapshot(false),secondId)&&events(firstId)==before,"old learning snapshot restores previously started unfinished words without answering");
        // A current-version snapshot also reacts to a word completed in a different scope.
        a.store.round(kind(false),fixture(1,false).toString());ui(()->{a.home();a.openWords();});check(!remaining(snapshot(false),secondId),"reopening a queue skips words already completed elsewhere");
        // Start the independent chapter-entry scenario with available daily capacity.
        a.store.round(DailyPlan.KIND,null);
        String chapter="textbook-jianai";ui(()->a.openWordSession(false,"选择性必修上",chapter,a::home));
        check(find("看释义")!=null,"chapter new-learning entry still works");rate(true);
        check(WordVolumes.count(a,"选择性必修上",chapter,2)==0,"one-point chapter word cannot enter chapter review");
        capture("four-point-chapter");
        String reviewChapter=null;JSONArray bookCards=WordVolumes.cards(a,volume);for(int n=0;n<bookCards.length()&&reviewChapter==null;n++)if(secondId.equals(bookCards.getJSONObject(n).getString("id")))reviewChapter=bookCards.getJSONObject(n).getJSONArray("contexts").getJSONObject(0).getString("text_id");
        check(reviewChapter!=null,"four-point word belongs to a chapter in the selected book");
        resumedReviewCount("","book");resumedReviewCount(reviewChapter,"chapter");
        pickedScope(reviewChapter);
    }
    View described(View v,String prefix){if(v.getVisibility()!=View.VISIBLE)return null;if(v.isClickable()&&String.valueOf(v.getContentDescription()).startsWith(prefix))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){View t=described(g.getChildAt(n),prefix);if(t!=null)return t;}}return null;}
    TextView containing(View v,String text){if(v.getVisibility()!=View.VISIBLE)return null;if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){TextView t=containing(g.getChildAt(n),text);if(t!=null)return t;}}return null;}
    void tap(View v,String what){check(v!=null,"tappable: "+what);ui(v::performClick);android.os.SystemClock.sleep(600);}
    // Scope comes from the My Learning plan and returns there; home follows it without touching any queue.
    void pickedScope(String chapter)throws Exception{
        ui(()->{a.prefs.edit().putString("word_volume","必修下").putString("word_text","").commit();a.home();});
        check(containing(a.currentCanvas,"必修下")==null&&containing(a.currentCanvas,"必修上")==null,"home has no volume selector");
        String title=null,textbook=null;for(int n=0;n<a.libraryTexts.length();n++){JSONObject t=a.libraryTexts.getJSONObject(n);if(t.optBoolean("textbook")&&(chapter.equals(t.optString("id"))||chapter.equals(t.optString("legacy_id")))){title=t.optString("title");textbook=t.optString("id");}}final String picked=textbook;check(picked!=null,"chapter has a textbook entry");
        String before=learningState();
        ui(a::stats);tap(described(a.currentCanvas,"学习范围"),"plan scope row");check("wordVolumes".equals(a.page),"plan scope opens book list");
        tap(described(a.currentCanvas,volume),"book row");check("wordVolumeContents".equals(a.page),"book opens chapter list");
        tap(containing(a.currentCanvas,title),"chapter row");
        check("stats".equals(a.page)&&!a.panelPreference&&volume.equals(WordVolumes.selected(a))&&picked.equals(WordVolumes.selectedText(a)),"picking a chapter returns to My Learning with shared preferences");
        check(described(a.currentCanvas,"学习范围 "+volume+" · "+title)!=null,"plan shows the picked scope");
        ui(a::settings);tap(described(a.currentCanvas,"学习范围"),"preference scope row");tap(described(a.currentCanvas,volume),"preference book row");tap(find("全册"),"whole book");
        check("stats".equals(a.page)&&a.panelPreference&&WordVolumes.selectedText(a).isEmpty(),"preference scope returns to its own tab");
        ui(a::home);int learn=a.volumeWordCount(1),review=WordVolumes.count(a,WordVolumes.ALL,2);
        check(String.valueOf(review).equals(a.reviewNumber.getText().toString())&&described(a.currentCanvas,"Learn "+learn)!=null,"home counts follow picked scope");
        check(before.equals(learningState()),"picking scope changes no answer, point or queue");
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();int code=-1;try{runChecks();result.putString("stream","PASS "+checks+" four-point checks: four self-reports before review, automatic rotation, reset, finite review, atomic rollback, restart, old-queue migration, book/chapter counts, resumed home review counts and first retention, plan scope picking.\n");}catch(Throwable t){code=0;result.putString("stream","FAIL after "+checks+": "+t+"\n");android.util.Log.e("WenyanTest","Four-point failure",t);}WordSchedule.clock=System::currentTimeMillis;finish(code,result);}
}
