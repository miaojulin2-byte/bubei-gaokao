package cn.local.gaokao;

import android.app.AlertDialog;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Learning dashboard. All counters are derived from the local study records. */
final class LearningPanel {
    final MainActivity a;
    final int orange=0xffff7900,ink=0xff242424,gray=0xff898989;
    final boolean preferences;
    LearningPanel(MainActivity a,boolean preferences){this.a=a;this.preferences=preferences;}
    int d(int n){return a.dp(n);}
    TextView text(String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    LinearLayout col(){LinearLayout v=new LinearLayout(a);v.setOrientation(1);return v;}
    LinearLayout card(){LinearLayout c=col();c.setPadding(d(16),d(16),d(16),d(16));c.setBackground(a.bg(Color.WHITE,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=d(18);a.root.addView(c,lp);return c;}
    void heading(String title){TextView t=text(title,16,ink);t.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=d(30);a.root.addView(t,lp);}
    void space(LinearLayout parent,int size){parent.addView(new View(a),new LinearLayout.LayoutParams(1,d(size)));}
    void row(LinearLayout c,String title,String value,Runnable run){LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(d(48));TextView label=text(title,16,ink);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView detail=text(value,15,value.contains("调整")?orange:gray);detail.setTag("preference-value:"+title);row.addView(detail);row.setContentDescription(title+" "+value);row.setFocusable(true);row.setClickable(true);row.setOnClickListener(v->run.run());c.addView(row,new LinearLayout.LayoutParams(-1,-2));}
    void show(){
        a.screen("stats");a.panelPreference=preferences;a.backAction=()->a.home();a.frame.removeView(a.dock);a.root.setPadding(d(16),0,d(16),d(28));
        LinearLayout bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBaselineAligned(false);bar.setPadding(d(16),d(2),d(16),d(6));
        Button close=a.action("",()->a.goBack(()->a.home()));close.setPadding(d(12),0,d(12),0);close.setCompoundDrawablesWithIntrinsicBounds(new UiIcon(4,d(24),ink),null,null,null);close.setContentDescription("关闭学习面板");bar.addView(close,new LinearLayout.LayoutParams(d(48),d(48)));
        LinearLayout tabs=new LinearLayout(a);tabs.setPadding(d(3),d(3),d(3),d(3));tabs.setBackground(a.bg(0xffeaeaea,30));LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(d(200),d(36));tabParams.gravity=Gravity.CENTER_VERTICAL;bar.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));bar.addView(tabs,tabParams);
        for(int i=0;i<2;i++){final boolean pref=i==1;Button tab=a.action(pref?"学习偏好":"我的学习",()->new LearningPanel(a,pref).show());tab.setTextSize(16);tab.setMinHeight(0);tab.setMinimumHeight(0);tab.setMinWidth(0);tab.setMinimumWidth(0);tab.setPadding(0,0,0,0);tab.setIncludeFontPadding(false);tab.setTextColor(pref==preferences?ink:gray);if(pref==preferences){tab.setBackground(a.bg(Color.WHITE,28));tab.setTypeface(null,Typeface.BOLD);}tabs.addView(tab,new LinearLayout.LayoutParams(0,d(30),1));}
        bar.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));bar.addView(new View(a),new LinearLayout.LayoutParams(d(48),1));a.frame.addView(bar,0);
        if(preferences)showPreferences();else showLearning();final ScrollView content=(ScrollView)a.root.getParent();content.post(()->content.scrollTo(0,a.panelOffsets[preferences?1:0]));
    }
    void showLearning(){
        int total=0,learned=0,excluded=0;
        try{String volume=WordVolumes.selected(a),text=WordVolumes.selectedText(a);total=WordVolumes.count(a,volume,text,0);org.json.JSONArray cards=WordVolumes.cards(a,volume,text);java.util.Set<String> full=WordVolumes.fourPointIds(a.store),familiar=a.store.familiarIds();for(int n=0;n<cards.length();n++){String id=cards.getJSONObject(n).getString("id");if(full.contains(id))learned++;else if(familiar.contains(id))excluded++;}}catch(Exception e){android.util.Log.e("WenyanProgress","Cannot read scope progress",e);}
        LinearLayout plan=card();
        ProgressBar progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);
        progress.setTag("total-progress");progress.setMax(Math.max(total,1));progress.setProgress(learned);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(orange));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffeeeeee));
        progress.setContentDescription("总进度 "+learned+" / "+total);
        plan.addView(progress,new LinearLayout.LayoutParams(-1,d(8)));space(plan,10);
        int daily=Math.max(1,a.prefs.getInt("daily",10)),remaining=Math.max(0,total-learned-excluded),days=(remaining+daily-1)/daily;
        LinearLayout progressLine=new LinearLayout(a);TextView done=text("已学习 "+learned+"/"+total,13,gray);done.setTag("plan-learned");progressLine.addView(done,new LinearLayout.LayoutParams(0,-2,1));TextView estimate=text(remaining==0?(learned==total?"已学完":"暂无待学"):"预计 "+days+" 天",13,gray);estimate.setTag("plan-estimate");progressLine.addView(estimate);plan.addView(progressLine);space(plan,12);
        row(plan,"学习科目",SubjectRules.title(a.subject)+"  ›",()->a.shell.chooseSubject(()->new LearningPanel(a,false).show()));
        row(plan,"学习范围",WordVolumes.selectedLabel(a)+"  ›",()->a.chooseScope(()->new LearningPanel(a,false).show()));
        row(plan,"每日学习 "+daily+" 个","调整  ›",()->daily());
        heading("统计");LinearLayout summary=card();
        int today=a.store.count("SELECT COUNT(DISTINCT word) FROM (SELECT id AS word,started AS at FROM state WHERE stage>0 UNION ALL SELECT word,at FROM events) WHERE at>=?",a.midnight());
        int all=a.wordStartedCount();
        int attempts=a.store.count("SELECT COUNT(*) FROM events WHERE kind!='self-correction' AND at>=?",a.midnight());
        LinearLayout first=new LinearLayout(a);metric(first,"今日学习＆复习",today,"个",0xffffb400,6,false);metric(first,"累计学习",all,"个",0xffef6471,7,false);summary.addView(first);space(summary,24);
        LinearLayout second=new LinearLayout(a);metric(second,"今日时长",(int)(a.store.duration(new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date()))/60000),"分钟",0xffffb400,8,true);metric(second,"累计时长",(int)(a.store.duration(null)/60000),"分钟",0xffef6471,9,true);summary.addView(second);
        calendar();
    }
    void metric(LinearLayout parent,String label,int value,String unit,int accent,int icon,boolean duration){LinearLayout cell=col();LinearLayout line=new LinearLayout(a);line.setGravity(Gravity.CENTER_VERTICAL);ImageView symbol=new ImageView(a);symbol.setImageDrawable(new UiIcon(icon,d(17),accent));line.addView(symbol,new LinearLayout.LayoutParams(d(18),d(18)));TextView title=text(label,12,ink);title.setPadding(d(8),0,0,0);line.addView(title);cell.addView(line);space(cell,8);TextView number=text(value+" "+unit,28,ink);android.text.SpannableString caption=new android.text.SpannableString(value+" "+unit);caption.setSpan(new android.text.style.RelativeSizeSpan(.45f),String.valueOf(value).length(),caption.length(),33);number.setText(caption);number.setTypeface(null,Typeface.BOLD);number.setPadding(d(26),0,0,0);cell.addView(number);cell.setContentDescription(label+" "+value+unit);cell.setFocusable(true);cell.setClickable(true);cell.setOnClickListener(v->showStatsDetail());parent.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}
    void calendar(){new CheckInCalendar(a).preview(card());}
    LinearLayout preferenceCard(){LinearLayout card=card();card.setPadding(d(16),0,d(16),0);return card;}
    void preferenceRow(LinearLayout card,String label,String value,Runnable action){row(card,label,value,action);card.getChildAt(card.getChildCount()-1).setMinimumHeight(d(56));}
    void showPreferences(){
        LinearLayout plan=preferenceCard();
        preferenceRow(plan,"学习科目",SubjectRules.title(a.subject)+"  ›",()->a.shell.chooseSubject(()->new LearningPanel(a,true).show()));
        preferenceRow(plan,"每日学习",a.prefs.getInt("daily",10)+" 个  ›",this::daily);
        preferenceRow(plan,"学习范围",WordVolumes.selectedLabel(a)+"  ›",this::source);
        preferenceRow(plan,"记忆保持率",Math.round(a.store.fsrsRetention()*100)+"%  ›",this::retention);
        preferenceRow(plan,"个人记忆参数","›",()->new FsrsTrainingPanel(a).show());
        LinearLayout records=preferenceCard();
        preferenceRow(records,a.subject.equals("biology")?"学习规则":"四点规则","›",this::wordRules);
        preferenceRow(records,a.subject.equals("wenyan")?"关于词汇":"关于内容","›",this::about);
        LinearLayout backup=preferenceCard();preferenceRow(backup,"导出全部学习备份","›",()->UnifiedBackup.export(a));preferenceRow(backup,"从备份恢复","›",()->UnifiedBackup.choose(a));
        LinearLayout reset=preferenceCard();preferenceRow(reset,"重置当前科目学习记录","›",this::confirmReset);
        ((TextView)((LinearLayout)reset.getChildAt(0)).getChildAt(0)).setTextColor(0xffc95454);
    }
    void confirmReset(){
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("重置"+SubjectRules.title(a.subject)+"学习记录？")
            .setMessage("将清空当前科目的作答、首答、点数、复习安排、学习进度、学习时长及熟词记录。其他科目与全应用签到保留。此操作无法撤销。\n\n教材内容、收藏、酷币、已兑换外观、学习范围和界面设置将保留。")
            .setNegativeButton("取消",null).setPositiveButton("确认重置",null).create();
        dialog.setOnShowListener(ignored->{Button confirm=dialog.getButton(AlertDialog.BUTTON_POSITIVE);confirm.setTextColor(0xffc95454);confirm.setOnClickListener(v->{
            confirm.setEnabled(false);
            try{
                a.flushStudyTime();a.store.resetLearning();new CheckInStore(a).days();
                a.wordPanel=null;a.wenyanPanel=null;a.wenyanActive=false;a.wenyanReveal=false;
                a.timerTick=a.lastInteraction=android.os.SystemClock.elapsedRealtime();a.panelOffsets=new int[]{0,0};
                dialog.dismiss();new LearningPanel(a,true).show();Toast.makeText(a,"学习记录已重置",Toast.LENGTH_SHORT).show();
            }catch(Exception e){confirm.setEnabled(true);Toast.makeText(a,"重置未完成，请重试",Toast.LENGTH_LONG).show();android.util.Log.e("WenyanReset","Cannot reset learning",e);}
        });});dialog.show();
    }
    void retention(){
        PreferenceSheet sheet=new PreferenceSheet(a,"FSRS 记忆保持率");sheet.note("目标保持率越高，通常复习越频繁。调整后用于下一次作答，不改动已有复习日期。");
        NumberPicker picker=new NumberPicker(a);picker.setMinValue(80);picker.setMaxValue(97);picker.setValue((int)Math.round(a.store.fsrsRetention()*100));picker.setWrapSelectorWheel(false);sheet.body.addView(picker,new LinearLayout.LayoutParams(-1,d(150)));
        sheet.confirm("确认",()->{picker.clearFocus();a.prefs.edit().putFloat("fsrs_retention",picker.getValue()/100f).apply();sheet.dismiss();show();});sheet.show();
    }
    void daily(){PreferenceSheet sheet=new PreferenceSheet(a,"每日学习");sheet.note("每日学习个数");NumberPicker picker=new NumberPicker(a);picker.setMinValue(1);picker.setMaxValue(100);picker.setValue(Math.min(100,a.prefs.getInt("daily",10)));picker.setWrapSelectorWheel(false);sheet.body.addView(picker,new LinearLayout.LayoutParams(-1,d(150)));TextView estimate=text("每天安排的词数，复习不占用此额度。",13,gray);estimate.setPadding(d(16),d(16),d(16),d(16));sheet.body.addView(estimate);sheet.confirm("确认",()->{picker.clearFocus();a.prefs.edit().putInt("daily",picker.getValue()).apply();sheet.dismiss();show();});sheet.show();}
    void source(){a.chooseScope(()->new LearningPanel(a,true).show());}

    void pace(boolean review){String key=review?"review_group":"learn_group";PreferenceSheet sheet=new PreferenceSheet(a,review?"复习节奏":"学习节奏");for(int count:review?new int[]{10,15,20,40,100}:new int[]{5,10,15,20})sheet.choice(count+(review?" 题/组":" 题/小结"),a.prefs.getInt(key,10)==count,()->{a.prefs.edit().putInt(key,count).apply();sheet.dismiss();show();});sheet.note("影响下一轮的题数，不改变知识点的复习日期。");sheet.show();}
    void statRow(LinearLayout card, String label, String value) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(d(42));
        TextView l = text(label, 15, ink);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text(value, 15, gray);
        row.addView(v);
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    int eventsBetween(long from, long to) {
        try (Cursor c = a.store.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM events WHERE kind!='self-correction' AND at>=? AND at<?",
                new String[]{String.valueOf(from), String.valueOf(to)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    int distinctWords(long from, long to, boolean review) {
        String mode = review ? "mode IN ('review_self','word_card_review')" : "mode NOT IN ('review_self','word_card_review')";
        try (Cursor c = a.store.getReadableDatabase().rawQuery(
                "SELECT COUNT(DISTINCT word) FROM events WHERE kind!='self-correction' AND at>=? AND at<? AND " + mode,
                new String[]{String.valueOf(from), String.valueOf(to)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    long minutesBetween(long from, long to) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(from);
        long total = 0;
        while (c.getTimeInMillis() < to) {
            total += a.store.duration(fmt.format(c.getTime()));
            c.add(Calendar.DATE, 1);
        }
        return total;
    }

    void showStatsDetail() {
        showStatsDetail(false);
    }

    void showStatsDetail(boolean month) {
        a.screen("statsDetail");
        a.dock.setVisibility(View.GONE);
        a.childHeader("概览", () -> new LearningPanel(a, false).show());

        LinearLayout tabs = new LinearLayout(a);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(0, d(4), 0, d(8));
        TextView week = text("最近一周", 15, month ? gray : ink);
        week.setGravity(Gravity.CENTER);
        week.setTypeface(null, month ? Typeface.NORMAL : Typeface.BOLD);
        if (!month) week.setBackground(a.bg(0x1aff7900, 14));
        TextView monthTab = text("按月查看", 15, month ? ink : gray);
        monthTab.setGravity(Gravity.CENTER);
        monthTab.setTypeface(null, month ? Typeface.BOLD : Typeface.NORMAL);
        if (month) monthTab.setBackground(a.bg(0x1aff7900, 14));
        week.setOnClickListener(v -> showStatsDetail(false));
        monthTab.setOnClickListener(v -> showStatsDetail(true));
        tabs.addView(week, new LinearLayout.LayoutParams(0, d(40), 1));
        tabs.addView(monthTab, new LinearLayout.LayoutParams(0, d(40), 1));
        a.root.addView(tabs, new LinearLayout.LayoutParams(-1, -2));

        int n = month ? 4 : 7;
        String[] labels = new String[n];
        int[] learn = new int[n], review = new int[n], minutes = new int[n];
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd", Locale.ROOT);
        for (int i = 0; i < n; i++) {
            long from, to;
            if (month) {
                Calendar start = (Calendar) today.clone();
                start.add(Calendar.DATE, -(n - 1 - i) * 7);
                from = start.getTimeInMillis();
                to = from + 7L * 86400000L;
                labels[i] = "第" + (i + 1) + "周";
            } else {
                from = today.getTimeInMillis() - (long) (n - 1 - i) * 86400000L;
                to = from + 86400000L;
                labels[i] = fmt.format(new Date(from));
            }
            learn[i] = distinctWords(from, to, false);
            review[i] = distinctWords(from, to, true);
            minutes[i] = (int) (minutesBetween(from, to) / 60000);
        }

        heading("学习量");
        LinearLayout chartCard = card();
        chartCard.setPadding(d(10), d(10), d(10), d(8));
        LinearLayout legend = new LinearLayout(a);
        legend.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        View orangeDot = new View(a); orangeDot.setBackground(a.bg(0xffff7900, 6));
        View purpleDot = new View(a); purpleDot.setBackground(a.bg(0xff8a7bd8, 6));
        legend.addView(orangeDot, new LinearLayout.LayoutParams(d(8), d(8)));
        TextView learnText = text(" 学习 ", 11, gray); legend.addView(learnText);
        legend.addView(purpleDot, new LinearLayout.LayoutParams(d(8), d(8)));
        TextView reviewText = text(" 复习", 11, gray); legend.addView(reviewText);
        chartCard.addView(legend, new LinearLayout.LayoutParams(-1, d(22)));
        WenyanBarChart chart = new WenyanBarChart(a);
        chart.setData(labels, learn, review, true);
        chartCard.addView(chart, new LinearLayout.LayoutParams(-1, d(230)));
        int sumLearn = 0, sumReview = 0;
        for (int i = 0; i < n; i++) { sumLearn += learn[i]; sumReview += review[i]; }
        TextView totals = text((month ? "近 4 周" : "本周") + "学习 " + sumLearn + " 个 · 复习 " + sumReview + " 个", 12, gray);
        totals.setPadding(d(4), d(6), d(4), d(2));
        chartCard.addView(totals);

        heading("学习时长");
        LinearLayout durationCard = card();
        durationCard.setPadding(d(10), d(10), d(10), d(8));
        WenyanBarChart duration = new WenyanBarChart(a);
        duration.setData(labels, minutes, new int[n], false);
        durationCard.addView(duration, new LinearLayout.LayoutParams(-1, d(180)));
    }

    void showCalendar(){new CheckInCalendar(a).show(0);}
    void showCalendar(int offset){new CheckInCalendar(a).show(offset);}

    void stateInfo(){PreferenceSheet sheet=new PreferenceSheet(a,"状态与间隔");sheet.note("每个知识点维护一行状态：stage / interval / lapses / attempts / due / started / suspended。");sheet.note("学习答对后安排 1 天复习；答错或看答案 10 分钟后可复习；复习按 1/3/7/14/30/60 天推进。");sheet.show();}
    void about(){if(!a.subject.equals("wenyan")){PreferenceSheet subjectSheet=new PreferenceSheet(a,"关于内容");subjectSheet.note(a.subject.equals("biology")?"1121 张生物填空与问答，按人教版五册组织，保留来源审核卡片与图片。":"91 篇古诗文、1312 张缺句背诵卡，课标推荐与教材补充分开保留。");subjectSheet.note("内容沿用既有整理和审核结果。重复练习与自评不代表新情境能力验证。");subjectSheet.show();return;}PreferenceSheet sheet=new PreferenceSheet(a,"关于词汇");sheet.note("五册32篇文言课文，按教材词语、短语注释整理；可按全册或单篇学习。");sheet.note("每条新增释义保留教材原句和PDF页码。原138张常考词卡及学习记录保留；同词多语境不再限制三条例句。");sheet.note("词语注释覆盖不等于所有生词均已收录；整句译注、专名与待核项另有整理记录。自评点数不代表已掌握。");sheet.show();}

    void wordRules(){if(a.subject.equals("biology")){PreferenceSheet sheet=new PreferenceSheet(a,"学习规则");sheet.note("生物揭晓后按四档自评进入 FSRS，不强制四遍；同笔记填空当天避让。");sheet.show();return;}PreferenceSheet sheet=new PreferenceSheet(a,"四点规则");sheet.note("先回想，再看释义。选择“想对了”累计 1 点，最多 4 点；“没记住”或“记错了”清零。自评后自动进入下一词。");sheet.note("各册、各篇分别保存学习位置。新学不足四点的词继续轮转，满四点才进入复习；复习中选择“重来”清零，并回到新学。点数按词卡累计，不按句子或义项分别计分。四点不代表已掌握；旧版点数保留，不能据此补认历史自评。");sheet.note("到期复习连续3次选择“困难”会清零回新学，同日重复和提前复习不累加；选择“良好”或“简单”会打断连续次数。FSRS-6根据每个词的复习反馈计算间隔；首次四点完成或旧词初始化后先安排次日复习，正常复习优先逾期最久的词。");sheet.show();}
    void audioSettings(){PreferenceSheet sheet=new PreferenceSheet(a,"学习设置");sheet.note("文言文子页面暂不使用发音与自动播放设置。");sheet.show();}

}
