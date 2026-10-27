package cn.local.gaokao;

import android.content.ContextWrapper;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** Resumable word-card self-report. Points are not evidence of sense mastery. */
final class WordStudyPanel extends ContextWrapper {
    private final MainActivity host;
    private final StudyStore store;
    private JSONArray cards;
    private int index, passes, contextIndex;
    private boolean busy, revealed, assessed, correct, corrected;
    private final boolean review, early;
    private JSONObject lastReview;
    private String draft="";
    private TextView correctionView;
    private final Runnable back;
    private final String kind, volume, textId;
    private JSONArray ids = new JSONArray();
    private String event, saved;
    private int generation;
    private final String openedDay=WordSchedule.today();
    void resumeDay(){if(!openedDay.equals(WordSchedule.today())){host.wordPanel=new WordStudyPanel(host,review,early,back,volume,textId);host.wordPanel.show();}}
    private static final int INK=0xff26302c, MUTED=0xff626e68, POINT=0xffff7900, READY=0xff219585, EMPTY=0x26000000;
    private TextView countView;
    private LinearLayout pointDots;
    private boolean revealMotion;

    WordStudyPanel(MainActivity host, boolean review, Runnable back) {
        this(host,review,back,WordVolumes.selected(host));
    }
    WordStudyPanel(MainActivity host, boolean review, Runnable back,String volume) {
        this(host,review,back,volume,"");
    }
    WordStudyPanel(MainActivity host, boolean review, Runnable back,String volume,String textId) {
        this(host,review,false,back,volume,textId);
    }
    WordStudyPanel(MainActivity host, boolean review, boolean early, Runnable back,String volume,String textId) {
        super(host);this.host=host;this.store=host.store;this.volume=volume;this.textId=textId;
        this.review=review;this.early=early;this.back=back;this.kind=early?WordVolumes.earlyKind(volume,textId):WordVolumes.kind(review,volume,textId);
        try {
            cards=SubjectRules.studyCards(host,volume,textId,review);
            if(review)orderReviewCards();
            if(!early&&textId.isEmpty())WordVolumes.migrateLegacy(host,cards,review,volume);
            saved=store.round(kind);String originalDailySnapshot=saved;
            if(review&&saved!=null&&!WordSchedule.today().equals(new JSONObject(saved).optString("review_day",WordSchedule.today()))){store.round("word-card-deferred:"+kind,saved);restore(saved);startReviewDay();persist();}
            if(saved!=null){restore(saved);if(index>=ids.length()){store.round(kind,null);saved=null;}else if(new JSONObject(saved).optInt("learning_revision",0)<2||(!volume.isEmpty()&&new JSONObject(saved).optInt("catalog_revision",0)<1)){refreshExpandedQueue();persist();}else if(pruneIneligiblePending()){persist();}}
            if(saved==null){
                ids=new JSONArray();index=0;contextIndex=0;revealed=false;assessed=false;correct=false;corrected=false;
                java.util.HashSet<String> ready=eligibleIds();
                for(int i=0;i<cards.length();i++){
                    String id=cards.getJSONObject(i).getString("id");
                    boolean complete=ready.contains(id);
                    if(complete)ids.put(id);
                }
                event=java.util.UUID.randomUUID().toString();
                if(ids.length()>0)persist();
            }
            if(!review){
                android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
                try{restrictToDailyPlan(originalDailySnapshot);db.setTransactionSuccessful();}finally{db.endTransaction();}
            }else if(saved!=null&&sortReviewPending()){persist();}
        }catch(Exception e){throw new IllegalStateException("无法读取背诵卡进度",e);}
    }
    private void orderReviewCards()throws Exception{
        java.util.HashMap<String,Integer> rank=new java.util.HashMap<>();int n=0;
        for(String id:eligibleIds())rank.put(id,n++);
        java.util.ArrayList<JSONObject> ordered=new java.util.ArrayList<>();for(int i=0;i<cards.length();i++)ordered.add(cards.getJSONObject(i));
        ordered.sort((left,right)->Integer.compare(rank.getOrDefault(left.optString("id"),Integer.MAX_VALUE),rank.getOrDefault(right.optString("id"),Integer.MAX_VALUE)));cards=new JSONArray(ordered);
    }
    private boolean sortReviewPending()throws Exception{
        String current=ids.optString(index,"");JSONArray next=new JSONArray();java.util.HashSet<String> pending=new java.util.HashSet<>();
        for(int n=0;n<ids.length();n++)if(n<index)next.put(ids.getString(n));else pending.add(ids.getString(n));
        if(revealed&&pending.remove(current))next.put(current);
        for(int n=0;n<cards.length();n++){String id=cards.getJSONObject(n).getString("id");if(pending.remove(id))next.put(id);}
        if(ids.toString().equals(next.toString()))return false;ids=next;
        if(!current.equals(ids.optString(index,""))){contextIndex=0;revealed=false;assessed=false;correct=false;corrected=false;event=java.util.UUID.randomUUID().toString();}return true;
    }
    private void startReviewDay()throws Exception{
        String current=ids.optString(index,"");java.util.HashSet<String> eligible=eligibleIds(),queued=new java.util.HashSet<>();JSONArray pending=new JSONArray();
        for(int n=index;n<ids.length();n++){String id=ids.getString(n);if(eligible.contains(id)&&queued.add(id))pending.put(id);}
        for(int n=0;n<cards.length();n++){String id=cards.getJSONObject(n).getString("id");if(eligible.contains(id)&&queued.add(id))pending.put(id);}
        ids=pending;index=0;lastReview=null;
        if(!current.equals(ids.optString(0,""))){contextIndex=0;revealed=false;assessed=false;correct=false;corrected=false;event=java.util.UUID.randomUUID().toString();}
    }
    private java.util.HashSet<String> eligibleIds(){
        if(review)return early?store.wordEarlyIds(WordSchedule.today()):WordVolumes.dueIds(store);
        java.util.HashSet<String> eligible=new java.util.HashSet<>(),ready=WordVolumes.fourPointIds(store);
        java.util.Set<String> familiar=store.familiarIds();for(int n=0;n<cards.length();n++){String id=cards.optJSONObject(n).optString("id");if(!ready.contains(id)&&!familiar.contains(id))eligible.add(id);}return eligible;
    }
    boolean hasActiveWord(){return index<ids.length();}
    private void refreshExpandedQueue()throws Exception{
        java.util.HashSet<String> available=new java.util.HashSet<>(),queued=new java.util.HashSet<>(),ready=eligibleIds();
        for(int n=0;n<cards.length();n++)available.add(cards.getJSONObject(n).getString("id"));
        String current=ids.optString(index,"");JSONArray next=new JSONArray();int nextIndex=0;
        for(int n=0;n<ids.length();n++){
            String id=ids.getString(n);boolean eligible=ready.contains(id);
            if(available.contains(id)&&(n<index||eligible||(n==index&&assessed&&!review))){
                if(n<index)nextIndex++;else queued.add(id);next.put(id);
            }
        }
        for(int n=0;n<cards.length();n++){
            String id=cards.getJSONObject(n).getString("id");
            if(!queued.contains(id)&&ready.contains(id)){next.put(id);queued.add(id);}
        }
        ids=next;index=nextIndex;
        if(!current.equals(ids.optString(index,""))){contextIndex=0;revealed=false;assessed=false;correct=false;corrected=false;event=java.util.UUID.randomUUID().toString();}
    }
    private boolean pruneIneligiblePending()throws Exception{
        java.util.HashSet<String> ready=eligibleIds(),pending=new java.util.HashSet<>();JSONArray next=new JSONArray();String current=ids.optString(index,"");
        for(int n=0;n<ids.length();n++){
            String id=ids.getString(n);if(n<index||ready.contains(id)||(n==index&&assessed&&!review)){next.put(id);if(n>=index)pending.add(id);}
        }
        if(!review)for(int n=0;n<Math.min(index,ids.length());n++){String id=ids.getString(n);if(ready.contains(id)&&pending.add(id))next.put(id);}
        if(next.toString().equals(ids.toString()))return false;
        ids=next;if(!current.equals(ids.optString(index,""))){contextIndex=0;revealed=false;assessed=false;correct=false;corrected=false;event=java.util.UUID.randomUUID().toString();}return true;
    }
    private void restrictToDailyPlan(String original)throws Exception{
        JSONArray planned=DailyPlan.planned(host,volume,textId,true);java.util.HashSet<String> allowed=new java.util.HashSet<>();
        for(int n=0;n<planned.length();n++)allowed.add(planned.getString(n));
        String current=ids.optString(index,"");JSONArray next=new JSONArray();int nextIndex=0;
        boolean sameDay=original!=null&&DailyPlan.day().equals(new JSONObject(original).optString("plan_day"));
        java.util.HashSet<String> queued=new java.util.HashSet<>();
        for(int n=0;n<ids.length();n++){String id=ids.getString(n);
            if(n<index&&sameDay){next.put(id);nextIndex++;}
            else if(n>=index&&allowed.contains(id)&&queued.add(id))next.put(id);
        }
        for(int n=0;n<planned.length();n++){String id=planned.getString(n);if(queued.add(id))next.put(id);}
        boolean changed=!next.toString().equals(ids.toString())||index!=nextIndex||!sameDay;
        if(!changed)return;
        if(original!=null&&(!sameDay||next.length()<ids.length()))store.round("word-card-deferred:"+kind,original);
        ids=next;index=nextIndex;
        if(!current.equals(ids.optString(index,""))){contextIndex=0;revealed=false;assessed=false;correct=false;corrected=false;event=java.util.UUID.randomUUID().toString();}
        persist();
    }
    private String progressLabel(){
        if(review)return Math.min(index+1,ids.length())+" / "+ids.length();
        java.util.HashSet<String> unique=new java.util.HashSet<>(),ready=WordVolumes.fourPointIds(store);int done=0;
        for(int n=0;n<ids.length();n++)unique.add(ids.optString(n));
        for(String id:unique)if(ready.contains(id))done++;
        return done+" / "+unique.size();
    }
    private String progressDescription(){return (review?"本组第 ":"本组达标 ")+progressLabel();}
    private JSONObject card() throws Exception {
        String id=ids.getString(index);
        for(int i=0;i<cards.length();i++)if(cards.getJSONObject(i).getString("id").equals(id))return cards.getJSONObject(i);
        throw new IllegalStateException("背诵卡已不存在："+id);
    }
    private String encode() throws Exception {
        JSONObject s=new JSONObject();s.put("plan_day",DailyPlan.day());s.put("review_day",WordSchedule.today());if(lastReview!=null)s.put("last_review",lastReview);s.put("learning_revision",2);s.put("catalog_revision",1);s.put("ids",ids);s.put("index",index);s.put("context",contextIndex);
        s.put("practice_revision",1);s.put("draft",draft);s.put("revealed",revealed);s.put("assessed",assessed);s.put("correct",correct);s.put("corrected",corrected);s.put("event",event);
        return s.toString();
    }
    private void restore(String value) throws Exception {
        JSONObject s=new JSONObject(value);draft=s.optString("draft","");lastReview=s.optJSONObject("last_review");ids=s.getJSONArray("ids");index=s.getInt("index");contextIndex=s.getInt("context");
        revealed=s.getBoolean("revealed");assessed=s.getBoolean("assessed");correct=s.getBoolean("correct");corrected=s.getBoolean("corrected");event=s.getString("event");
        // A legacy draft may answer different blanks; keep the queue, ID and learning state.
        if(s.optInt("practice_revision",0)<1){draft="";if(!assessed)revealed=false;}
    }
    private void persist() throws Exception {String next=encode();store.round(kind,next);saved=next;}
    private void failed(Exception e){
        busy=false;
        try{if(saved!=null)restore(saved);}catch(Exception ignored){}
        Toast.makeText(this,"未保存，请重试",Toast.LENGTH_LONG).show();
        android.util.Log.e("WenyanWordCard","Cannot save word card",e);
        renderCard();
    }
    private void bind(TextView v,Runnable action){
        int version=generation;v.setOnClickListener(view->{if(version==generation&&!busy&&view.isAttachedToWindow())action.run();});
    }

    private int d(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(d(radius));
        return g;
    }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    void show() {
        host.screen("wordStudy");
        host.wenyanActive=false;
        host.root.setPadding(d(28),d(20),d(28),d(24));
        host.dock.setPadding(d(28),d(12),d(28),d(22));
        host.backAction = () -> host.goBack(back);
        buildHeader();
        if (ids.length() == 0) {
            TextView empty = text(review ? "这里暂时没有要复习的词。" : "当前范围今日暂无待背诵。", 14, 0xff6f7773);
            empty.setPadding(d(20), d(30), d(20), d(20));
            host.root.addView(empty);addEarlyEntry();
            return;
        }
        renderCard();
    }

    private void buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(d(12), d(6), d(28), 0);
        TextView back = text("‹", 28, 0xff26302c);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setContentDescription("返回");
        back.setFocusable(true);
        back.setOnClickListener(v -> host.goBack(this.back));
        bar.addView(back, new LinearLayout.LayoutParams(d(48), d(48)));

        countView = text(progressLabel(), 14, MUTED);
        countView.setGravity(Gravity.CENTER_VERTICAL);
        countView.setContentDescription(progressDescription());
        bar.addView(countView, new LinearLayout.LayoutParams(-2, d(48)));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if(review){correctionView=text("记错了",12,MUTED);correctionView.setGravity(Gravity.CENTER);correctionView.setVisibility(lastReview==null?View.GONE:View.VISIBLE);correctionView.setContentDescription("记错了，撤回上一张复习的正确自评");correctionView.setOnClickListener(v->correctLastReview());bar.addView(correctionView,new LinearLayout.LayoutParams(d(56),d(48)));}
        TextView star=text("☆",24,MUTED);star.setGravity(Gravity.CENTER);star.setTag("collect-word");star.setContentDescription("收藏当前词");star.setOnClickListener(v->{try{new WordBooks(host).chooseTerm(card().getString("word"),this::renderCard);}catch(Exception e){failed(e);}});bar.addView(star,new LinearLayout.LayoutParams(d(42),d(48)));
        TextView familiar=text("熟",17,MUTED);familiar.setGravity(Gravity.CENTER);familiar.setContentDescription("标熟当前词");familiar.setTag("mark-familiar");familiar.setEnabled(index<ids.length());familiar.setVisibility(index<ids.length()?View.VISIBLE:View.GONE);familiar.setOnClickListener(v->markFamiliar());bar.addView(familiar,new LinearLayout.LayoutParams(d(42),d(48)));
        host.frame.addView(bar, 0);
    }

    private void renderCard() {
        if(host.wordPanel!=this||!host.page.equals("wordStudy"))return;
        generation++;
        host.root.removeAllViews();host.root.setGravity(Gravity.TOP);host.dock.removeAllViews();host.dock.setVisibility(View.VISIBLE);
        if(countView!=null){countView.setText(progressLabel());countView.setContentDescription(progressDescription());}
        View mark=host.currentCanvas.findViewWithTag("mark-familiar");if(mark!=null){mark.setEnabled(index<ids.length());mark.setVisibility(index<ids.length()?View.VISIBLE:View.GONE);}
        View collect=host.currentCanvas.findViewWithTag("collect-word");if(collect!=null){collect.setVisibility(index<ids.length()?View.VISIBLE:View.GONE);try{if(index<ids.length())((TextView)collect).setText(new WordBooks(host).saved(card().getString("word"))?"★":"☆");}catch(Exception ignored){}}
        pointDots=null;
        if(correctionView!=null){correctionView.setVisibility(lastReview==null?View.GONE:View.VISIBLE);correctionView.setEnabled(!busy);}
        if(index>=ids.length()){
            host.root.setGravity(Gravity.CENTER);host.dock.setVisibility(View.GONE);
            View check=new View(this){
                final android.graphics.Paint pen=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);float unit=getWidth()/64f;pen.setColor(android.graphics.Color.WHITE);pen.setStyle(android.graphics.Paint.Style.STROKE);pen.setStrokeWidth(5*unit);pen.setStrokeCap(android.graphics.Paint.Cap.ROUND);pen.setStrokeJoin(android.graphics.Paint.Join.ROUND);android.graphics.Path path=new android.graphics.Path();path.moveTo(18*unit,33*unit);path.lineTo(28*unit,43*unit);path.lineTo(47*unit,22*unit);canvas.drawPath(path,pen);}
            };check.setBackground(bg(READY,16));check.setTag("completion-check");check.setContentDescription("完成");
            LinearLayout.LayoutParams badge=new LinearLayout.LayoutParams(d(64),d(64));badge.gravity=Gravity.CENTER;host.root.addView(check,badge);
            TextView done=text("已完成",25,INK);done.setGravity(Gravity.CENTER);done.setPadding(0,d(20),0,d(16));host.root.addView(done);
            check.setScaleX(.3f);check.setScaleY(.3f);check.animate().scaleX(1).scaleY(1).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            done.setAlpha(0);done.setTranslationY(d(8));done.animate().alpha(1).translationY(0).setDuration(260).start();
            addEarlyEntry();return;
        }
        try {
            JSONObject card = card();
            String id = card.optString("id");
            passes = store.wordPasses(id);
            JSONArray availableContexts=card.optJSONArray("contexts");
            if(availableContexts!=null&&availableContexts.length()>0){
                JSONObject context=availableContexts.getJSONObject(Math.min(contextIndex,availableContexts.length()-1));
                if(!context.optString("meaning").isEmpty()){renderContextCard(card,context,availableContexts);return;}
            }

            LinearLayout cardView = new LinearLayout(this);
            cardView.setOrientation(LinearLayout.VERTICAL);
            cardView.setPadding(d(4), 0, d(4), 0);

            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.TOP);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView word = text(card.optString("word"), revealed ? 56 : 64, INK);
            word.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            left.addView(word);
            String pinyinText = card.optString("pinyin");
            if (!pinyinText.isEmpty()) {
                TextView pinyin = text(pinyinText, 12, MUTED);
                pinyin.setPadding(0, d(6), 0, 0);
                left.addView(pinyin);
            }
            top.addView(left, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams dotsLp=new LinearLayout.LayoutParams(-2,-2);dotsLp.leftMargin=d(16);dotsLp.topMargin=d(16);
            top.addView(dots(passes),dotsLp);
            cardView.addView(top);

            String pos = card.optString("pos");
            String gloss = card.optString("gloss");
            if(revealed){
                String formatted=gloss.replaceAll("(?<!^)([①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]|○[0-9]+)", "\n$1");
                String content=(pos.isEmpty()?"":pos+"\n")+formatted;
                SpannableString styled=new SpannableString(content);
                if(!pos.isEmpty()){
                    styled.setSpan(new android.text.style.AbsoluteSizeSpan(12,true),0,pos.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    styled.setSpan(new ForegroundColorSpan(MUTED),0,pos.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                java.util.regex.Matcher number=java.util.regex.Pattern.compile("[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]|○[0-9]+").matcher(content);
                while(number.find()){
                    styled.setSpan(new android.text.style.AbsoluteSizeSpan(13,true),number.start(),number.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    styled.setSpan(new ForegroundColorSpan(MUTED),number.start(),number.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                TextView meaning=text("",19,INK);meaning.setText(styled);meaning.setLineSpacing(d(5),1f);
                meaning.setPadding(0,d(18),0,0);cardView.addView(meaning);
            }else{
                LinearLayout hidden=new LinearLayout(this);hidden.setOrientation(LinearLayout.VERTICAL);
                hidden.setPadding(0,d(24),0,d(12));hidden.setContentDescription("释义未揭晓");
                for(int width:new int[]{178,112}){
                    View line=new View(this);line.setBackground(bg(0x58ffffff,7));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(d(width),d(14));lp.bottomMargin=d(9);hidden.addView(line,lp);
                }
                cardView.addView(hidden);
            }

            LinearLayout contextCard = new LinearLayout(this);
            contextCard.setOrientation(LinearLayout.VERTICAL);
            contextCard.setPadding(d(20), d(18), d(20), d(4));
            GradientDrawable contextBackground=bg(0x70ffffff,16);contextBackground.setStroke(d(1),0x48ffffff);contextCard.setBackground(contextBackground);
            JSONArray contexts = card.optJSONArray("contexts");
            TextView contextTag = text(contexts==null||contexts.length()==0 ? "课文语境" : "课文语境 · " + (Math.min(contextIndex,contexts.length()-1)+1) + "/" + contexts.length(), 11, MUTED);
            contextCard.addView(contextTag);
            if (contexts != null && contexts.length() > 0) {
                int ci = Math.min(contextIndex, contexts.length() - 1);
                JSONObject context = contexts.getJSONObject(ci);
                String sentenceText = context.optString("sentence");
                SpannableString marked = new SpannableString(sentenceText);
                String wordText = card.optString("word");
                int from = 0;
                while (wordText.length() > 0 && from < sentenceText.length()) {
                    int at = sentenceText.indexOf(wordText, from);
                    if (at < 0) break;
                    marked.setSpan(new ForegroundColorSpan(0xffff7900), at, at + wordText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    marked.setSpan(new StyleSpan(Typeface.BOLD), at, at + wordText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    from = at + wordText.length();
                }
                TextView sentence = text("", 19, INK);
                sentence.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL));
                sentence.setText(marked);
                sentence.setLineSpacing(d(9), 1f);
                sentence.setPadding(0, d(14), 0, 0);
                contextCard.addView(sentence);
                TextView source = text("《" + context.optString("title") + "》", 11, MUTED);
                source.setPadding(0, d(14), 0, d(4));
                contextCard.addView(source);
                LinearLayout links = new LinearLayout(this);
                links.setGravity(Gravity.END);
                if (contexts.length() > 1) {
                    TextView swap = text("换一句  ›", 11, MUTED);
                    swap.setPadding(d(10),0,d(10),0);swap.setMinHeight(d(48));swap.setGravity(Gravity.CENTER);swap.setFocusable(true);
                    swap.setClickable(true);
                    bind(swap, () -> { try{contextIndex = (contextIndex + 1) % contexts.length(); persist(); renderCard();}catch(Exception e){failed(e);} });
                    links.addView(swap);
                }
                TextView open = text("查看原文  ›", 11, MUTED);
                open.setPadding(d(10),0,0,0);open.setMinHeight(d(48));open.setGravity(Gravity.CENTER);open.setFocusable(true);
                open.setClickable(true);
                bind(open, () -> { host.openWenyan("text", context.optString("text_id"));host.wenyanPanel.externalBack=this::show; });
                if(revealed)links.addView(open);
                contextCard.addView(links);
            } else {
                TextView none = text("32 篇课文中暂未出现，先按义项记忆。", 12, MUTED);
                none.setPadding(0, d(8), 0, 0);
                contextCard.addView(none);
            }
            LinearLayout.LayoutParams contextLp = new LinearLayout.LayoutParams(-1, -2);
            contextLp.topMargin = d(revealed?24:42);
            cardView.addView(contextCard, contextLp);

            host.root.addView(cardView, new LinearLayout.LayoutParams(-1, -2));
            showDock();
        } catch (Exception e) {
            host.root.addView(text("无法显示背诵卡，请返回后重试。",16,INK));
            android.util.Log.e("WenyanWordCard","Cannot render card",e);
        }
    }

    // The dock holds one row of actions in every state, so the buttons never move between steps.
    private void showDock(){
            LinearLayout actions = new LinearLayout(this);actions.setGravity(Gravity.CENTER);
            if(!revealed)addAction(actions,(host.subject.equals("wenyan")?"看释义":host.subject.equals("biology")?"显示答案":"揭晓原文"),0xffff7900,()->{try{if(host.getCurrentFocus()!=null)((android.view.inputmethod.InputMethodManager)host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(host.getCurrentFocus().getWindowToken(),0);revealed=true;persist();revealMotion=true;renderCard();}catch(Exception e){failed(e);}});
            else if(!assessed&&(review||host.subject.equals("biology"))){
                String[] labels={"重来","困难","良好","简单"};int[] colors={0xffda6070,0xffbd8a42,READY,0xff5487bc};
                for(int n=0;n<labels.length;n++){final int rating=n;addAction(actions,labels[n],colors[n],()->markRating(rating,false));}
            }else if(!assessed){
                addAction(actions,(host.subject.equals("wenyan")?"想对了":"背出来了"),0xff219585,()->mark(true,false));
                addAction(actions,"没记住",0xffda6070,()->mark(false,false));
            }else{
                if(correct&&!corrected)addAction(actions,"记错了",0xffda6070,()->mark(false,true));
                addAction(actions,"下一张",0xffff7900,this::nextWord);
            }
            host.dock.addView(actions);
            if(revealMotion){actions.setAlpha(0);actions.animate().alpha(1).setDuration(200).start();}
            revealMotion=false;
    }

    private void renderContextCard(JSONObject card,JSONObject context,JSONArray contexts) throws Exception {
        if("biology".equals(context.optString("source_kind"))){renderBiology(card,context);return;}
        if("recitation".equals(context.optString("source_kind"))){renderRecitation(card,context);return;}
        String word=card.getString("word"),sentenceText=WordLexicon.sentence(context.getString("sentence"));
        boolean motion=revealMotion;
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(dots(passes),new LinearLayout.LayoutParams(-2,-2));heading.addView(new View(this),new LinearLayout.LayoutParams(0,d(28),1));
        TextView position=text((Math.min(contextIndex,contexts.length()-1)+1)+" / "+contexts.length(),12,0x99626e68);position.setContentDescription("第 "+position.getText()+" 句");
        if(contexts.length()<2)position.setVisibility(View.INVISIBLE);
        heading.addView(position);host.root.addView(heading);
        // Long sentences step down in size so the revealed meaning stays near the first screen.
        int length=sentenceText.length(),size=length>44?21:length>26?24:28;
        TextView sentence=text("",size,INK);sentence.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL));WordLexicon.clickable(host,sentence,sentenceText,word,WordLexicon.displayTarget(context,word));
        sentence.setLineSpacing(d(size>24?12:9),1f);sentence.setPadding(0,d(length>26?16:24),0,d(14));host.root.addView(sentence);
        LinearLayout sourceRow=new LinearLayout(this);sourceRow.setGravity(Gravity.CENTER_VERTICAL);
        String attribution=WordLexicon.section(host,context);
        TextView source=text("— "+(attribution.isEmpty()?WordLexicon.title(context.optString("title")):attribution),12,0x99626e68);sourceRow.addView(source,new LinearLayout.LayoutParams(0,-2,1));
        if(!revealed&&contexts.length()<2){View hold=new View(this);sourceRow.addView(hold,new LinearLayout.LayoutParams(1,d(48)));}
        if(!revealed&&contexts.length()>1){
            TextView swap=action("换一句  ›",MUTED);swap.setTextSize(12);swap.setMinHeight(d(48));swap.setPadding(d(12),0,0,0);
            bind(swap,()->{try{contextIndex=(Math.min(contextIndex,contexts.length()-1)+1)%contexts.length();persist();renderCard();}catch(Exception e){failed(e);}});sourceRow.addView(swap);
        }
        if(revealed){
            TextView original=action("查看原文  ›",MUTED);original.setTextSize(12);original.setMinHeight(d(48));original.setPadding(d(12),0,0,0);
            bind(original,()->{host.openWenyan("text",context.optString("text_id"));host.wenyanPanel.externalBack=this::show;});sourceRow.addView(original);
        }
        host.root.addView(sourceRow);
        View answerSpace=new View(this);host.root.addView(answerSpace,new LinearLayout.LayoutParams(1,d(16)));
        LinearLayout answer=new LinearLayout(this);answer.setOrientation(LinearLayout.VERTICAL);answer.setPadding(d(22),d(20),d(22),d(22));answer.setMinimumHeight(d(96));
        GradientDrawable surface=bg(revealed?0x9effffff:0x70ffffff,16);surface.setStroke(d(1),0x48ffffff);answer.setBackground(surface);
        if(revealed){
            TextView meaning=text(context.getString("meaning"),26,INK);meaning.setLineSpacing(d(4),1f);answer.addView(meaning);
            String explanationText=WordLexicon.translation(host,context);
            if(!explanationText.isEmpty()){
                TextView explanation=text(explanationText,15,MUTED);explanation.setLineSpacing(d(6),1f);explanation.setPadding(0,d(12),0,0);answer.addView(explanation);
            }
            if(motion)for(int n=0;n<answer.getChildCount();n++){View part=answer.getChildAt(n);part.setAlpha(0);part.setTranslationY(d(10));part.animate().alpha(1).translationY(0).setStartDelay(n*70L).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}
        }else{
            answer.setContentDescription("释义未揭晓");
            for(int width:new int[]{146,94}){View line=new View(this);line.setBackground(bg(0x78ffffff,6));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(d(width),d(14));lp.bottomMargin=d(14);answer.addView(line,lp);}
        }
        host.root.addView(answer,new LinearLayout.LayoutParams(-1,-2));
        if(motion)keepVisible(answer);
        showDock();
    }
    private void renderBiology(JSONObject card,JSONObject context)throws Exception {
        TextView tag=text("生物 · "+context.optString("title"),13,MUTED);tag.setPadding(0,d(18),0,d(22));host.root.addView(tag);
        TextView question=text(context.optString("sentence"),23,INK);question.setTag("biology-prompt");question.setLineSpacing(d(8),1);host.root.addView(question);
        JSONObject bio=card.getJSONObject("biology");JSONArray images=bio.optJSONArray("images");
        addBiologyImages(images,0);
        if(revealed){LinearLayout answer=new LinearLayout(this);answer.setOrientation(1);answer.setPadding(d(18),d(18),d(18),d(18));answer.setBackground(bg(0xc0ffffff,16));answer.addView(text("答案核对",12,MUTED));TextView value=text(context.optString("meaning"),20,INK);value.setLineSpacing(d(7),1);value.setTag("biology-answer");answer.addView(value);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=d(24);host.root.addView(answer,lp);addBiologyImages(images,1);}
        TextView note=action("我的笔记",MUTED);note.setTextSize(13);bind(note,()->host.shell.note(card.optString("id")));host.root.addView(note);showDock();
    }
    private void addBiologyImages(JSONArray images,int field)throws Exception {if(images==null)return;for(int n=0;n<images.length();n++){JSONObject image=images.getJSONObject(n);if(image.optInt("field")!=field)continue;String name=image.optString("src");if(!name.matches("media/[a-f0-9]{64}\\.(png|jpe?g)"))continue;try(java.io.InputStream in=host.getAssets().open("biology/"+name)){android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(in);if(bitmap==null)continue;android.widget.ImageView view=new android.widget.ImageView(this);view.setAdjustViewBounds(true);view.setImageBitmap(bitmap);view.setContentDescription(field==0?"题目配图":"答案配图");host.root.addView(view,new LinearLayout.LayoutParams(-1,-2));view.setOnClickListener(v->host.shell.image(name));}}}
    private void renderRecitation(JSONObject card,JSONObject context)throws Exception {
        host.root.addView(dots(passes));
        TextView title=text(context.optString("title"),26,INK);title.setTypeface(Typeface.SERIF,Typeface.BOLD);title.setPadding(0,d(18),0,d(8));host.root.addView(title);
        TextView author=text(context.optString("author")+" · "+card.optString("word").substring(card.optString("word").lastIndexOf(" · ")+3),13,MUTED);host.root.addView(author);
        final ClozeExercise exercise=new ClozeExercise(context.getString("meaning"),event.hashCode());
        TextView task=text("补写缺句",12,MUTED);task.setPadding(0,d(22),0,d(4));host.root.addView(task);
        TextView cue=text(exercise.prompt,23,INK);cue.setTag("cloze-prompt");cue.setTypeface(Typeface.SERIF,Typeface.NORMAL);cue.setLineSpacing(d(8),1f);cue.setPadding(0,d(12),0,d(18));host.root.addView(cue);
        if(!revealed){
            TextView hint=text("根据上下文回忆缺句，按顺序写在纸上或输入下方，再揭晓核对。",12,MUTED);hint.setLineSpacing(d(4),1);host.root.addView(hint);
            android.widget.EditText input=new android.widget.EditText(this);input.setContentDescription("默写答案");input.setTag("cloze-input");input.setTextSize(18);input.setMinLines(2);input.setMaxLines(6);input.setGravity(Gravity.TOP);input.setHint("在这里默写");input.setText(draft);input.setPadding(d(14),d(14),d(14),d(14));input.setBackground(bg(0x80ffffff,12));
            host.root.addView(input,new LinearLayout.LayoutParams(-1,-2));
            input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){draft=s.toString();try{persist();}catch(Exception e){android.util.Log.e("Gushi","Draft save failed",e);}}public void afterTextChanged(android.text.Editable e){}});
        }else{
            LinearLayout answer=new LinearLayout(this);answer.setOrientation(1);answer.setPadding(d(18),d(18),d(18),d(20));answer.setBackground(bg(0xb0ffffff,16));
            TextView answerTitle=text("原文核对",12,MUTED);answer.addView(answerTitle);
            TextView original=text(context.getString("meaning"),23,INK);original.setTag("recitation-answer");original.setTypeface(Typeface.SERIF,Typeface.NORMAL);original.setLineSpacing(d(8),1f);original.setPadding(0,d(10),0,d(12));answer.addView(original);
            if(!draft.trim().isEmpty()){
                TextView outcome=text(exercise.matches(draft)?"本次输入与应填文字一致":"本次输入有差异，请对照原文",14,exercise.matches(draft)?READY:0xffc74e51);outcome.setTag("cloze-result");answer.addView(outcome);
                String actual=ClozeExercise.normalize(draft),expected=ClozeExercise.normalize(exercise.expected);SpannableString diff=new SpannableString(actual);
                for(int n=0;n<actual.length();n++)if(n>=expected.length()||actual.charAt(n)!=expected.charAt(n))diff.setSpan(new ForegroundColorSpan(0xffc74e51),n,n+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                TextView typed=text("",16,MUTED);typed.setText(diff);typed.setTag("cloze-diff");typed.setPadding(0,d(8),0,d(8));answer.addView(typed);
                TextView expectedView=text("应填："+exercise.expected,15,MUTED);expectedView.setLineSpacing(d(4),1);answer.addView(expectedView);
                answer.addView(text("仅忽略标点与空格；异体字不自动互认。核对结果不代替背诵自评。",11,MUTED));
            }else{answer.addView(text("请自行核对背诵。四点记录自评次数，不证明已掌握。",12,MUTED));}
            host.root.addView(answer,new LinearLayout.LayoutParams(-1,-2));
            TextView open=action("阅读全文  ›",MUTED);open.setTextSize(13);bind(open,()->{host.openWenyan("text",context.optString("text_id"));host.wenyanPanel.externalBack=this::show;});host.root.addView(open);
        }
        showDock();
    }
    // After a reveal on a long sentence, glide just far enough for the answer to clear the dock.
    private void keepVisible(View target){
        if(!(host.root.getParent() instanceof android.widget.ScrollView))return;
        android.widget.ScrollView scroll=(android.widget.ScrollView)host.root.getParent();
        scroll.post(()->{int overflow=target.getBottom()+d(12)-(scroll.getScrollY()+scroll.getHeight());if(overflow>0)scroll.smoothScrollBy(0,overflow);});
    }

    private void addAction(LinearLayout row,String label,int accent,Runnable action){
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);column.setGravity(Gravity.CENTER);
        TextView button=action(label,INK);button.setMinHeight(d(52));button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x18000000),null,null));
        bind(button,action);column.addView(button,new LinearLayout.LayoutParams(-1,-2));
        View line=new View(this);line.setBackground(bg(accent,2));column.addView(line,new LinearLayout.LayoutParams(d(20),d(3)));
        row.addView(column,new LinearLayout.LayoutParams(0,-2,1));
    }

    // Four points for the current word only: orange while practising, all teal once it reaches four.
    private LinearLayout dots(int passes) {
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d(8), d(8));
            if(i>0)lp.leftMargin = d(7);
            dots.addView(dot, lp);
        }
        paintDots(dots,passes);
        pointDots=dots;
        return dots;
    }
    private void paintDots(LinearLayout dots,int points){
        for(int i=0;i<dots.getChildCount();i++)dots.getChildAt(i).setBackground(bg(points>=4?READY:i<points?POINT:EMPTY,4));
        dots.setContentDescription(points>=4?"本词已达标":"本词 "+points+" / 4");
    }
    // Answer feedback is the point change itself, then the next card slides in.
    private void respond(int before,int after,Runnable then){
        LinearLayout dots=pointDots;android.view.animation.Interpolator pop=new android.view.animation.OvershootInterpolator(3f);
        if(dots!=null){
            paintDots(dots,after);
            if(after>before)for(int i=after>=4?0:after-1;i<after;i++){View dot=dots.getChildAt(i);dot.setScaleX(.3f);dot.setScaleY(.3f);dot.animate().scaleX(1).scaleY(1).setStartDelay(after>=4?i*40L:0).setDuration(260).setInterpolator(pop).start();}
            else if(after<before)dots.animate().translationX(d(5)).setDuration(60).withEndAction(()->dots.animate().translationX(-d(5)).setDuration(90).withEndAction(()->dots.animate().translationX(0).setDuration(60).start()).start()).start();
            else{dots.setScaleX(1.25f);dots.setScaleY(1.25f);dots.animate().scaleX(1).scaleY(1).setDuration(260).setInterpolator(pop).start();}
        }
        View canvas=host.currentCanvas;
        host.root.postDelayed(()->{if(host.currentCanvas==canvas&&host.wordPanel==this)turn(then);},dots==null?0:300);
    }
    private void turn(Runnable render){
        View canvas=host.currentCanvas;
        View[] parts={host.root,host.dock};android.view.animation.Interpolator ease=new android.view.animation.PathInterpolator(.22f,.61f,.36f,1);
        host.root.animate().alpha(0).translationX(-d(28)).setDuration(140).start();
        host.dock.animate().alpha(0).setDuration(140).withEndAction(()->{
            if(host.currentCanvas!=canvas)return;
            render.run();
            if(host.root.getParent() instanceof android.widget.ScrollView)((android.widget.ScrollView)host.root.getParent()).scrollTo(0,0);
            host.root.setTranslationX(d(28));
            for(View part:parts)part.animate().alpha(1).translationX(0).setDuration(220).setInterpolator(ease).start();
        }).start();
    }

    private TextView action(String label, int color) {
        TextView v = text(label, 18, color);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private void addEarlyEntry(){
        if(!review||early)return;
        try{int count=WordVolumes.count(host,volume,textId,3);if(count==0)return;
            TextView entry=action("提前复习 · "+count,MUTED);entry.setTextSize(13);entry.setPadding(0,d(24),0,d(12));
            entry.setOnClickListener(v->{host.wordPanel=new WordStudyPanel(host,true,true,back,volume,textId);host.wordPanel.show();});host.root.addView(entry);
        }catch(Exception e){android.util.Log.e("WenyanWordCard","Cannot open early review",e);}
    }
    private void markFamiliar(){
        if(busy||index>=ids.length())return;busy=true;
        try{String id=card().getString("id");JSONArray nextIds=new JSONArray();for(int n=0;n<ids.length();n++)if(n<index||!id.equals(ids.getString(n)))nextIds.put(ids.getString(n));ids=nextIds;draft="";contextIndex=0;revealed=false;assessed=false;correct=false;corrected=false;event=java.util.UUID.randomUUID().toString();String next=encode();if(!store.markFamiliar(id,kind,saved,next,WordSchedule.now()))throw new IllegalStateException("Stale familiar mark");saved=next;generation++;renderCard();Toast.makeText(this,"已加入熟篇本",Toast.LENGTH_SHORT).show();}catch(Exception e){failed(e);}finally{busy=false;}
    }
    private void correctLastReview(){
        if(busy||lastReview==null)return;busy=true;
        try{JSONObject previous=lastReview;lastReview=null;String next=encode();
            if(!store.wordCardAnswer(previous.getString("event")+".correction",previous.getString("id"),WordSchedule.AGAIN,true,early?"early":"review",previous.optString("cue"),kind,saved,next,WordSchedule.now()))throw new IllegalStateException("Stale correction");
            saved=next;generation++;renderCard();
        }catch(Exception e){failed(e);}finally{busy=false;}
    }
    private void mark(boolean value,boolean correction) {markRating(value?WordSchedule.GOOD:WordSchedule.AGAIN,correction);}
    private void markRating(int rating,boolean correction) {
        boolean value=rating>WordSchedule.AGAIN;
        if(busy||!revealed||(correction?(!assessed||!correct||corrected):assessed))return;
        // Past local midnight in the foreground, an early card may now be due and the store would reject it on every tap; rebuild the day's queue instead.
        if(!openedDay.equals(WordSchedule.today())){resumeDay();return;}
        busy=true;
        try{
            JSONObject card=card();String id=card.getString("id");String submittedDraft=draft;
            JSONObject submission=new JSONObject().put("input",submittedDraft);
            JSONArray sourceContexts=card.optJSONArray("contexts");if(sourceContexts!=null&&sourceContexts.length()>0&&"recitation".equals(sourceContexts.getJSONObject(Math.min(contextIndex,sourceContexts.length()-1)).optString("source_kind"))){ClozeExercise exercise=new ClozeExercise(sourceContexts.getJSONObject(Math.min(contextIndex,sourceContexts.length()-1)).getString("meaning"),event.hashCode());submission.put("expected",exercise.expected);if(!submittedDraft.trim().isEmpty())submission.put("matched",exercise.matches(submittedDraft));}
            String submittedEvent=event+(correction?".correction":"");
            JSONArray contexts=card.optJSONArray("contexts");String cue=contexts!=null&&contexts.length()>0?contexts.getJSONObject(Math.min(contextIndex,contexts.length()-1)).optString("sentence"):"";
            // Requeue unfinished new words; the result and queue still commit atomically.
            int nextPasses=correction?0:store.wordAnswerPasses(id,rating,early?"early":review?"review":"learn",WordSchedule.now());
            if(!review&&nextPasses<4)ids.put(id);
            advanceWord();
            if(review)lastReview=nextPasses>0&&!correction?new JSONObject().put("event",submittedEvent).put("id",id).put("cue",cue):null;
            if(index<ids.length()){
                JSONObject upcoming=card();JSONArray upcomingContexts=upcoming.optJSONArray("contexts");
                int points=upcoming.getString("id").equals(id)?nextPasses:store.wordPasses(upcoming.getString("id"));
                if(upcomingContexts!=null&&upcomingContexts.length()>0)contextIndex=points%upcomingContexts.length();
            }
            String next=new JSONObject(encode()).put("submission",submission).toString();
            if(!store.wordCardAnswer(submittedEvent,id,rating,correction,early?"early":review?"review":"learn",cue,kind,saved,next,WordSchedule.now()))
                throw new IllegalStateException("Stale or repeated submission");
            saved=next;
            // The answer is already committed; retire the old buttons so the response cannot be tapped twice.
            generation++;respond(passes,nextPasses,this::renderCard);
        }catch(Exception e){failed(e);}finally{busy=false;}
    }
    private void advanceWord() {
        index++;draft="";contextIndex=0;revealed=false;assessed=false;correct=false;corrected=false;event=java.util.UUID.randomUUID().toString();
    }
    private void nextWord() {
        if(!assessed||busy)return;
        try{
            if(!review&&store.wordPasses(ids.getString(index))<4)ids.put(ids.getString(index));
            advanceWord();
            persist();
            if(index>=ids.length())store.round(kind,null);
            generation++;turn(this::renderCard);
        }catch(Exception e){failed(e);}
    }
}
