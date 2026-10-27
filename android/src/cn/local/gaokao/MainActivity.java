package cn.local.gaokao;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Offline Wenyan word cards, reading and chapter exercises. */
public class MainActivity extends Activity {
    final int INK=0xff242424,GREEN=0xffff7900,PAPER=0xfff4f4f4,MUTED=0xff898989;
    String subject="wenyan";SharedPreferences appPrefs;StudyStore commonStore;GaokaoShell shell;LinearLayout root,frame,dock;SharedPreferences prefs;StudyStore store;String page="home";
    PreferenceSheet activeSheet;FrameLayout navigationHost;View currentCanvas;boolean reverseNavigation=false;Runnable backAction;int[] panelOffsets={0,0};boolean panelPreference=false;long timerTick,lastInteraction;boolean resumed,hasResumed;String homeDay="";Handler studyTimer=new Handler();TextView reviewNumber,learnNumber;WenyanPanel wenyanPanel;WordStudyPanel wordPanel;boolean wenyanReveal,wenyanActive;org.json.JSONArray libraryTexts;JSONObject textbookWordData;
    final Runnable clockTick=new Runnable(){public void run(){flushStudyTime();if(resumed&&page.equals("wordStudy")&&wordPanel!=null)wordPanel.resumeDay();if(resumed&&page.equals("home")&&!homeDay.equals(WordSchedule.today()))refreshHomeCounts();if(resumed)studyTimer.postDelayed(this,15000);}};
    void flushStudyTime(){long now=SystemClock.elapsedRealtime();if(resumed&&timerTick>0&&(page.equals("study")||page.equals("sentence")||page.equals("detail")||page.equals("preview")||page.equals("spelling")||(page.equals("wordStudy")&&wordPanel!=null&&wordPanel.hasActiveWord()))){long until=Math.min(now,lastInteraction+60000),delta=Math.max(0,until-timerTick);if(delta>0)store.addDuration(System.currentTimeMillis()-(now-until),delta);}timerTick=now;}
    @Override protected void onResume(){super.onResume();boolean returning=hasResumed;hasResumed=true;resumed=true;timerTick=lastInteraction=SystemClock.elapsedRealtime();studyTimer.removeCallbacks(clockTick);studyTimer.postDelayed(clockTick,15000);if(returning&&page.equals("home")){refreshHomeCounts();}else if(returning&&page.equals("wordStudy")&&wordPanel!=null){wordPanel.resumeDay();}}
    void refreshHomeCounts(){
        homeDay=WordSchedule.today();
        for(int mode=1;mode<=2;mode++){TextView number=mode==1?learnNumber:reviewNumber;if(number==null)continue;
            int n=volumeWordCount(mode);number.setText(String.valueOf(n));View card=(View)number.getParent();
            if(card!=null){card.setContentDescription((mode==1?"Learn ":"Review ")+n);setHomeCardAvailability(card,n);}}
    }
    @Override protected void onPause(){flushStudyTime();resumed=false;studyTimer.removeCallbacks(clockTick);super.onPause();}
    @Override public void onUserInteraction(){super.onUserInteraction();flushStudyTime();lastInteraction=SystemClock.elapsedRealtime();}
    static final long PAGE_MOTION_MS=560, PANEL_MOTION_MS=620, CONTENT_MOTION_MS=360;
    void goBack(Runnable destination){reverseNavigation=true;destination.run();}
    void mount(FrameLayout next,String previous,String name){
        if(navigationHost==null){navigationHost=new FrameLayout(this);setContentView(navigationHost);navigationHost.addView(next,new FrameLayout.LayoutParams(-1,-1));currentCanvas=next;return;}
        View old=currentCanvas;boolean reverse=reverseNavigation;reverseNavigation=false;
        boolean opening=name.equals("stats")&&previous.equals("home"),closing=previous.equals("stats")&&name.equals("home"),same=previous.equals(name);
        boolean searchOpen=name.equals("dictionary")&&previous.equals("home"),searchClose=previous.equals("dictionary")&&name.equals("home");
        boolean reveal=wenyanReveal;wenyanReveal=false;
        long duration=searchOpen||searchClose?CONTENT_MOTION_MS:reveal?260:opening||closing?PANEL_MOTION_MS:same?CONTENT_MOTION_MS:PAGE_MOTION_MS;
        float width=navigationHost.getWidth(),height=navigationHost.getHeight();
        if(old!=null){old.animate().setListener(null);old.animate().cancel();old.setTranslationX(0);old.setTranslationY(0);old.setAlpha(1);}
        navigationHost.removeAllViews();currentCanvas=next;
        // Initial positions and stacking are set before attachment, so no new page flashes over the old one.
        if(searchOpen)next.setTranslationY(-height);else if(opening)next.setTranslationY(height);else if(reverse&&!closing&&!searchClose)next.setTranslationX(-width*.18f);else if(!same&&!closing&&!searchClose)next.setTranslationX(width);else if(same)next.setAlpha(0);
        if(reverse||closing||searchClose){navigationHost.addView(next,new FrameLayout.LayoutParams(-1,-1));if(old!=null)navigationHost.addView(old);}
        else {if(old!=null)navigationHost.addView(old);navigationHost.addView(next,new FrameLayout.LayoutParams(-1,-1));}
        View blocker=new View(this);blocker.setClickable(true);navigationHost.addView(blocker,new FrameLayout.LayoutParams(-1,-1));
        next.postOnAnimation(()->{if(currentCanvas!=next)return;android.view.animation.Interpolator ease=new android.view.animation.PathInterpolator(.22f,.61f,.36f,1);
            if(old!=null){if(searchClose)old.animate().translationY(-height).setDuration(duration).setInterpolator(ease).start();else if(searchOpen){}else if(closing)old.animate().translationY(height).setDuration(duration).setInterpolator(ease).start();else if(reverse)old.animate().translationX(width).setDuration(duration).setInterpolator(ease).start();else if(!same&&!opening)old.animate().translationX(-width*.18f).setDuration(duration).setInterpolator(ease).start();}
            next.animate().translationX(0).translationY(0).alpha(1).setDuration(duration).setInterpolator(ease).withEndAction(()->{if(currentCanvas==next){if(old!=null)navigationHost.removeView(old);navigationHost.removeView(blocker);}}).start();
        });
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);appPrefs=getSharedPreferences("gaokao",MODE_PRIVATE);subject=appPrefs.getString("subject","wenyan");if(!java.util.Arrays.asList("wenyan","gushi","biology").contains(subject))subject="wenyan";prefs=getSharedPreferences("study-"+subject,MODE_PRIVATE);commonStore=new StudyStore(this,"common");store=new StudyStore(this,subject);shell=new GaokaoShell(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);loadLibrary();WallpaperRotation.beginSession(this);home();if(state==null&&prefs.getBoolean("intro_motion",true)&&android.provider.Settings.Global.getFloat(getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0)showIntro();}
    void setSubject(String value){if(value.equals(subject))return;if(!java.util.Arrays.asList("wenyan","gushi","biology").contains(value))throw new IllegalArgumentException("Unknown subject");flushStudyTime();if(wordPanel!=null)wordPanel=null;store.close();subject=value;appPrefs.edit().putString("subject",value).commit();prefs=getSharedPreferences("study-"+value,MODE_PRIVATE);store=new StudyStore(this,value);panelOffsets=new int[]{0,0};textbookWordData=null;WordLexicon.sentenceTranslations=null;libraryTexts=null;loadLibrary();}
    @Override protected void onDestroy(){studyTimer.removeCallbacks(clockTick);if(store!=null)store.close();if(commonStore!=null)commonStore.close();super.onDestroy();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);UnifiedBackup.result(this,request,result,data);}
    int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    void screen(String name){
        String previous=page;flushStudyTime();if(page.equals("stats")&&root!=null&&root.getParent() instanceof ScrollView)panelOffsets[panelPreference?1:0]=((ScrollView)root.getParent()).getScrollY();page=name;backAction=null;FrameLayout canvas=new FrameLayout(this);View surface=name.equals("calendar")?new View(this):new Atmosphere(this,name.equals("home"));if(name.equals("calendar"))surface.setBackgroundColor(0xfff5f5f5);canvas.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        frame=new LinearLayout(this);frame.setOrientation(1);canvas.addView(frame,new FrameLayout.LayoutParams(-1,-1));
        root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(name.equals("study")?16:26),dp(name.equals("study")?34:16),dp(name.equals("study")?16:26),dp(24));
        ScrollView scroll=name.equals("home")?new HomeSearchScroll(this):new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.addView(root);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        dock=new LinearLayout(this);dock.setOrientation(1);dock.setPadding(dp(name.equals("study")?8:20),dp(name.equals("study")?0:8),dp(name.equals("study")?8:20),dp(name.equals("study")?8:12));frame.addView(dock,new LinearLayout.LayoutParams(-1,-2));
        final LinearLayout safeFrame=frame;
        canvas.setOnApplyWindowInsetsListener((v,in)->{applySafeInsets(safeFrame,in,name,scroll);return in;});
        mount(canvas,previous,name);canvas.requestApplyInsets();
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    void applySafeInsets(View content,WindowInsets in,String name,ScrollView scroll){
        int top=in.getSystemWindowInsetTop(),bottom=in.getSystemWindowInsetBottom(),left=in.getSystemWindowInsetLeft(),right=in.getSystemWindowInsetRight();
        if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=in.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());android.graphics.Insets ime=in.getInsets(WindowInsets.Type.ime());top=safe.top;bottom=Math.max(safe.bottom,ime.bottom);left=safe.left;right=safe.right;}
        else {int id=getResources().getIdentifier("status_bar_height","dimen","android");if(id>0)top=Math.max(top,getResources().getDimensionPixelSize(id));if(Build.VERSION.SDK_INT>=28&&in.getDisplayCutout()!=null){DisplayCutout cutout=in.getDisplayCutout();top=Math.max(top,cutout.getSafeInsetTop());bottom=Math.max(bottom,cutout.getSafeInsetBottom());left=Math.max(left,cutout.getSafeInsetLeft());right=Math.max(right,cutout.getSafeInsetRight());}}
        boolean panel=name.equals("stats")||name.equals("statsDetail")||name.equals("calendar");
        content.setPadding(left,top,right,panel?0:bottom);
        if(panel){scroll.setPadding(0,0,0,bottom);scroll.setClipToPadding(false);scroll.setVerticalFadingEdgeEnabled(false);}

    }
    Button action(String text,Runnable run){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(INK);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18000000),null,null));b.setStateListAnimator(null);b.setMinHeight(dp(48));b.setOnClickListener(v->run.run());return b;}
    TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextColor(INK);t.setTextSize(size);t.setIncludeFontPadding(false);int textInset=page.equals("study")&&root.getParent() instanceof ScrollView?dp(16):0;t.setPadding(textInset,dp(8),textInset,dp(8));root.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    void title(String text){label(text,24).setTypeface(null,Typeface.BOLD);}
    void note(String text){label(text,13).setTextColor(MUTED);}
    Button button(String text,Runnable run){Button b=action(text,run);b.setTextColor(GREEN);b.setBackground(bg(0x99ffffff,16));b.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);root.addView(b,p);return b;}
    void bottom(String text,Runnable run){Button b=action(text,run);b.setTextColor(GREEN);dock.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));}
    void primary(String text,Runnable run){if(page.equals("study")){bottom(text,run);return;}Button b=button(text,run);b.setTextColor(Color.WHITE);b.setBackground(bg(GREEN,16));}
    void childHeader(String title,Runnable destination){backAction=destination;LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(6),0,dp(6),0);Button back=action("",()->goBack(backAction));back.setContentDescription("返回");back.setCompoundDrawablesWithIntrinsicBounds(new UiIcon(5,dp(24),INK),null,null,null);bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView heading=new TextView(this);heading.setText(title);heading.setTextSize(16);heading.setTextColor(INK);heading.setIncludeFontPadding(false);heading.setGravity(Gravity.CENTER);heading.setMaxLines(1);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);bar.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));bar.addView(new View(this),new LinearLayout.LayoutParams(dp(48),1));frame.addView(bar,0);}
    void back(){Button b=action("‹ 返回首页",()->goBack(()->home()));root.addView(b);}
    void nav(){dock.removeAllViews();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);for(int n=1;n<=2;n++){final int pick=n;FrameLayout slot=new FrameLayout(this);Button b=action("",()->{if(pick==1)library();else stats();});b.setPadding(0,0,0,0);b.setMinWidth(0);b.setContentDescription(n==1?"我的内容":"仪表盘");b.setCompoundDrawablesWithIntrinsicBounds(null,new UiIcon(n,dp(28),INK),null,null);slot.addView(b,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER));row.addView(slot,new LinearLayout.LayoutParams(0,dp(60),1));}dock.addView(row);}

    void learn(){openWords();}
    void homeCard(LinearLayout row,String name,int count,Runnable run){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(16),0,dp(16),0);card.setBackground(bg(0xa6f8f4ec,9));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(70),1);cp.setMargins(dp(5),0,dp(5),0);row.addView(card,cp);TextView t=new TextView(this);t.setText(name);t.setTextSize(18);t.setTypeface(null,Typeface.BOLD);t.setTextColor(INK);card.addView(t);TextView number=new TextView(this);number.setText(String.valueOf(count));number.setTextSize(14);number.setTextColor(0xffff7900);card.addView(number);if(name.equals("Review"))reviewNumber=number;else if(name.equals("Learn"))learnNumber=number;card.setContentDescription(name+" "+count);card.setFocusable(true);card.setClickable(true);card.setOnClickListener(v->{if(card.isEnabled())run.run();});setHomeCardAvailability(card,count);}
    void error(String s){new AlertDialog.Builder(this).setTitle("暂时无法完成").setMessage(s).setPositiveButton("知道了",null).show();}
    String date(long time){if(time==Long.MAX_VALUE)return "本组回练中";return new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(time));}
    long midnight(){Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    int startedToday(){return store.count("SELECT count(*) FROM state WHERE started>=?",midnight());}
    void setHomeCardAvailability(View card,int count){boolean enabled=count>0;card.setEnabled(enabled);card.setClickable(enabled);card.setAlpha(1f);}
    void home(){
        wenyanPanel=null;wenyanActive=false;WallpaperRotation.refreshDay(this);screen("home");homeDay=WordSchedule.today();nav();
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        AvatarBadge profile=new AvatarBadge(this,prefs.getString("avatar","古"));profile.setOnClickListener(v->new ProfilePanel(this).show());bar.addView(profile,new LinearLayout.LayoutParams(dp(48),dp(48)));
        bar.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        CheckInStore attendance=new CheckInStore(this);boolean signed=attendance.days().contains(attendance.today());Button sign=action(signed?"已签到":"签到",this::signHome);sign.setTextSize(14);sign.setBackground(bg(0xaaffffff,22));sign.setContentDescription("首页签到");sign.setTag("home-check-in");bar.addView(sign,new LinearLayout.LayoutParams(dp(80),dp(42)));root.addView(bar);
        root.addView(new View(this),new LinearLayout.LayoutParams(1,0,2));TextView word=label(WallpaperRotation.quote(),28);word.setGravity(Gravity.CENTER);word.setTypeface(Typeface.SERIF,Typeface.BOLD);word.setLineSpacing(dp(7),1);word.setTag("scene-quote");word.setOnClickListener(v->new DictionaryPanel(this,this::home).quote(WallpaperRotation.quote()));TextView source=label(WallpaperRotation.source(),12);source.setGravity(Gravity.CENTER);source.setTextColor(0xaa242424);
        root.addView(new View(this),new LinearLayout.LayoutParams(1,0,5));LinearLayout cards=new LinearLayout(this);root.addView(cards);homeCard(cards,"Learn",volumeWordCount(1),()->openWords());homeCard(cards,"Review",volumeWordCount(2),()->openWords(true));

    }
    void signHome(){try{CheckInStore attendance=new CheckInStore(this);attendance.days();int coins=Wallet.sign(commonStore,WordSchedule.now());new AlertDialog.Builder(this).setTitle(coins>0?"签到成功  +"+coins+" 酷币":"今日已签到").setMessage(WallpaperRotation.quote()+"\n\n"+WallpaperRotation.source()).setPositiveButton("收下",(d,w)->home()).show();}catch(Exception e){error("签到未保存，请重试");}}
    void showIntro(){
        FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(0xfff7f4ee);TextView logo=new TextView(this);logo.setText("不\n背\n高\n考");logo.setTextSize(28);logo.setGravity(Gravity.CENTER);logo.setTextColor(GREEN);logo.setTypeface(Typeface.SERIF,Typeface.BOLD);overlay.addView(logo,new FrameLayout.LayoutParams(-1,-1));overlay.setContentDescription("不背高考");overlay.setTag("launch-intro");navigationHost.addView(overlay,new FrameLayout.LayoutParams(-1,-1));overlay.setOnClickListener(v->{overlay.animate().cancel();navigationHost.removeView(overlay);});logo.setAlpha(0);logo.setScaleX(.84f);logo.setScaleY(.84f);logo.animate().alpha(1).scaleX(1).scaleY(1).setDuration(650).start();overlay.postDelayed(()->overlay.animate().alpha(0).setDuration(650).withEndAction(()->navigationHost.removeView(overlay)).start(),950);
    }
    void settings(){new LearningPanel(this,true).show();}
    void library(){new ContentHub(this).show();}
    void loadLibrary(){try{libraryTexts=new org.json.JSONObject(readAsset("wenyan-library.json")).optJSONArray("texts");}catch(Exception e){libraryTexts=null;}}
    int libraryTextCount(){int n=0;if(libraryTexts!=null)for(int i=0;i<libraryTexts.length();i++)if(!libraryTexts.optJSONObject(i).optBoolean("reference_only"))n++;return n;}
    JSONObject textbookWords()throws Exception{if(textbookWordData==null)textbookWordData=new JSONObject(readAsset("textbook-word-cards.json"));return textbookWordData;}
    int wordCardCount(){try{return textbookWords().getInt("count");}catch(Exception e){return 0;}}
    boolean hasLearningPack(String id){return "quanxue".equals(id)||"shishuo".equals(id)||"chibi-fu".equals(id)||"liuguo-lun".equals(id);}
    int learningPackCount(){if(libraryTexts==null)return 0;int n=0;for(int i=0;i<libraryTexts.length();i++){try{if(libraryTexts.getJSONObject(i).optBoolean("learning_ready"))n++;}catch(Exception ignored){}}return n;}
    void showTextLibrary(){
        screen("textLibrary");childHeader("课内篇目",()->library());
        if(libraryTexts==null)return;
        String lastStage="";
        for(int i=0;i<libraryTexts.length();i++){
            try{
                org.json.JSONObject t=libraryTexts.getJSONObject(i);if(t.optBoolean("reference_only"))continue;String stage=t.optString("volume");
                if(!stage.equals(lastStage)){lastStage=stage;label(stage,13).setTextColor(MUTED);}
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(12),dp(16),dp(12));row.setBackground(bg(0xccf8f4ec,12));
                LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
                TextView title=new TextView(this);title.setText(t.optString("title"));title.setTextSize(17);title.setTextColor(INK);title.setTypeface(null,Typeface.BOLD);
                info.addView(title);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
                TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextSize(22);arrow.setTextColor(MUTED);row.addView(arrow);
                row.setFocusable(true);row.setClickable(true);row.setContentDescription(t.optString("title"));
                final String id=t.optString("id");row.setOnClickListener(v->openWenyan("text",id));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);root.addView(row,lp);
            }catch(Exception ignored){}
        }
    }
    String readAsset(String name)throws Exception{try(java.io.InputStream in=getAssets().open(subject+"/"+name);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    void openWords(){openWords(false);}
    void openWords(boolean review){String origin=page;Runnable back=origin.equals("home")?this::home:origin.equals("stats")?this::stats:this::library;openWordSession(review,review?WordVolumes.ALL:WordVolumes.selected(this),review?"":WordVolumes.selectedText(this),back);}
    void openWordSession(boolean review,String volume,Runnable back){try{wordPanel=new WordStudyPanel(this,review,back,volume);wordPanel.show();}catch(Exception e){error("无法打开词卡："+e.getMessage());}}
    void openWordSession(boolean review,String volume,String textId,Runnable back){try{wordPanel=new WordStudyPanel(this,review,back,volume,textId);wordPanel.show();}catch(Exception e){error("无法打开词卡："+e.getMessage());}}
    int volumeWordCount(int mode){try{return mode==1?DailyPlan.pendingCount(this,WordVolumes.selected(this),WordVolumes.selectedText(this)):WordVolumes.count(this,WordVolumes.ALL,"",mode);}catch(Exception e){return 0;}}
    void showWordVolumes(boolean review){
        String origin=page;Runnable back=origin.equals("home")?this::home:origin.equals("stats")?(panelPreference?this::settings:this::stats):this::library;
        showWordVolumes(review,back,null);
    }
    // Scope picking reuses the book/chapter pages; choosing writes the same preferences and returns to the origin.
    void chooseScope(Runnable origin){showWordVolumes(false,origin,origin);}
    void pickScope(String volume,String textId,Runnable picked){prefs.edit().putString("word_volume",volume).putString("word_text",textId).apply();goBack(picked);}
    private void showWordVolumes(boolean review,Runnable back,Runnable picked){
        screen("wordVolumes");childHeader(picked!=null?"学习范围":review?"分册复习":"分册学词",back);dock.setVisibility(View.GONE);
        note(SubjectRules.title(subject)+" · 选择教材册次");
        button("全部内容",()->{if(picked!=null){pickScope(WordVolumes.ALL,"",picked);return;}prefs.edit().putString("word_volume",WordVolumes.ALL).putString("word_text","").apply();openWordSession(review,WordVolumes.ALL,"",back);});
        for(String volume:WordVolumes.names(this)){
            try{
                int due=review?WordVolumes.count(this,volume,2):0;
                LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(16),dp(18),dp(16));
                String cover=shell.cover(volume);if(cover!=null)try(java.io.InputStream in=getAssets().open(cover)){ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));image.setContentDescription(volume+"教材封面");LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(dp(50),dp(72));imageParams.rightMargin=dp(16);row.addView(image,imageParams);}row.setBackground(bg(0xccffffff,14));
                TextView name=new TextView(this);name.setText(volume+(volume.equals(WordVolumes.selected(this))?" · 当前":"")+"  ›");name.setTextSize(18);name.setTextColor(INK);row.addView(name);
                row.setContentDescription(volume);boolean available=!review||picked!=null||due>0;row.setEnabled(available);row.setClickable(available);row.setFocusable(true);row.setOnClickListener(v->{if(!row.isEnabled())return;if(picked!=null){if(volume.equals("拓展篇目"))pickScope(volume,"",picked);else showWordVolumeContents(review,volume,()->showWordVolumes(review,back,picked),picked);return;}prefs.edit().putString("word_volume",volume).putString("word_text","").apply();showWordVolumeContents(review,volume,()->showWordVolumes(review,back,null));});
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);root.addView(row,lp);
            }catch(Exception e){error("无法读取分册："+e.getMessage());return;}
        }
    }
    void showWordVolumeContents(boolean review,String volume,Runnable back){showWordVolumeContents(review,volume,back,null);}
    void showWordVolumeContents(boolean review,String volume,Runnable back,Runnable picked){
        if(volume.equals("拓展篇目")){openWordSession(review,volume,"",back);return;}
        screen("wordVolumeContents");childHeader(volume,back);dock.setVisibility(View.GONE);
        boolean bookSelected=volume.equals(WordVolumes.selected(this))&&WordVolumes.selectedText(this).isEmpty();
        button((picked!=null?"全册":review?"全册复习":"全册学习")+(picked!=null&&bookSelected?" · 当前":""),()->{if(picked!=null){pickScope(volume,"",picked);return;}prefs.edit().putString("word_volume",volume).putString("word_text","").apply();openWordSession(review,volume,"",()->showWordVolumeContents(review,volume,back));});
        if(libraryTexts==null)return;
        for(int i=0;i<libraryTexts.length();i++){
            JSONObject t=libraryTexts.optJSONObject(i);if(!t.optBoolean("textbook")||!volume.equals(t.optString("volume")))continue;
            String textId=t.optString("id");
            try{
                boolean current=picked!=null&&volume.equals(WordVolumes.selected(this))&&textId.equals(WordVolumes.selectedText(this));
                button(t.optString("lesson")+" · "+t.optString("title")+(current?" · 当前":""),()->{
                    if(picked!=null){pickScope(volume,textId,picked);return;}
                    prefs.edit().putString("word_volume",volume).putString("word_text",textId).apply();openWordSession(review,volume,textId,()->showWordVolumeContents(review,volume,back));
                });
            }catch(Exception e){error("无法读取课文词语："+e.getMessage());return;}
        }
    }
    int wordFourPointCount(){return store.wordFourPointCount();}
    int wordReviewCount(){return store.wordReviewCount();}
    int wordStartedCount(){return store.wordStartedCount();}
    void openWenyan(String mode){openWenyan(mode,"quanxue");}
    void openWenyan(String mode,String textId){wenyanActive=true;wenyanPanel=new WenyanPanel(this,mode,textId);backAction=()->{if(wenyanPanel!=null)wenyanPanel.back();};}
    int wenyanReviewCount(){try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT count(*) FROM state WHERE id LIKE 'quanxue-kp%' AND stage>0 AND suspended=0 AND due<=?",new String[]{String.valueOf(System.currentTimeMillis())})){return c.moveToFirst()?c.getInt(0):0;}catch(Exception e){return 0;}}
    int wenyanKpCount(){return 79;}
    int wenyanSentenceCount(){return 15;}
    int wenyanLearnedCount(){try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT count(*) FROM state WHERE id LIKE 'quanxue-kp%' AND stage>0 AND suspended=0",null)){return c.moveToFirst()?c.getInt(0):0;}catch(Exception e){return 0;}}
    void startSession(boolean review){openWenyan(review?"review":"learn");}
    void stats(){new LearningPanel(this,false).show();}

    @Override public void onBackPressed(){if(activeSheet!=null&&activeSheet.isShowing()){activeSheet.dismiss();return;}if(backAction!=null){Runnable action=backAction;goBack(action);}else if(!page.equals("home"))goBack(()->home());else super.onBackPressed();}
}
