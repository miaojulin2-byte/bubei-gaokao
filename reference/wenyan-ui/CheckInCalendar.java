package cn.local.wenyan;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** A monthly attendance card for records only; attendance is entered on the home page. */
final class CheckInCalendar {
    final MainActivity a;final CheckInStore store;
    final int ink=0xff242424,gray=0xff999999,orange=0xffff7900;
    static final String[] WEEK={"MON","TUE","WED","THU","FRI","SAT","SUN"};
    CheckInCalendar(MainActivity a){this.a=a;store=new CheckInStore(a);}
    int d(int n){return a.dp(n);}
    TextView label(String s,int size,int color){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    void gap(LinearLayout parent,int size){parent.addView(new View(a),new LinearLayout.LayoutParams(1,d(size)));}
    void weekdays(LinearLayout parent){LinearLayout row=new LinearLayout(a);for(String name:WEEK){TextView t=label(name,11,gray);t.setGravity(Gravity.CENTER);t.setTypeface(null,Typeface.BOLD);row.addView(t,new LinearLayout.LayoutParams(0,d(32),1));}parent.addView(row);}
    View day(Calendar date,Set<String> days,int height){
        String key=CheckInStore.key(date),today=store.today();boolean signed=days.contains(key),current=key.equals(today),future=key.compareTo(today)>0;
        FrameLayout cell=new FrameLayout(a);TextView t=label(current?"今":String.valueOf(date.get(Calendar.DAY_OF_MONTH)),17,future?0xffdddddd:signed?Color.WHITE:ink);
        t.setGravity(Gravity.CENTER);t.setTypeface(null,Typeface.BOLD);int size=current?44:signed?32:42;
        if(signed&&!future)t.setBackground(a.bg(orange,size/2));else if(current)t.setBackground(a.bg(0xffeeeeee,10));
        t.setContentDescription(key+(signed?" 已签到":current?" 今天，未签到":future?" 未来日期":" 未签到"));t.setFocusable(true);
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(d(size),d(size),Gravity.CENTER);cell.addView(t,params);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0,d(height),1));return cell;
    }
    void preview(LinearLayout card){
        card.setContentDescription("签到日历");card.setClickable(true);card.setOnClickListener(v->show(0));
        LinearLayout head=new LinearLayout(a);TextView title=label("签到日历",16,ink);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));head.addView(label("连续签到 "+store.streak()+" 天",12,gray));card.addView(head);gap(card,14);
        weekdays(card);Calendar date=store.now();date.add(Calendar.DATE,-((date.get(Calendar.DAY_OF_WEEK)+5)%7));
        LinearLayout week=new LinearLayout(a);Set<String> days=store.days();for(int n=0;n<7;n++){week.addView(day(date,days,48));date.add(Calendar.DATE,1);}card.addView(week);
    }
    void show(int offset){
        offset=Math.min(0,offset);final int shown=offset;
        a.screen("calendar");a.dock.setVisibility(View.GONE);a.root.setPadding(d(16),d(8),d(16),d(28));
        a.childHeader("签到日历",()->new LearningPanel(a,false).show());
        LinearLayout header=(LinearLayout)a.frame.getChildAt(0);header.getChildAt(0).setBackground(a.bg(0xffeeeeee,24));
        Calendar month=store.now();month.set(Calendar.DAY_OF_MONTH,1);month.add(Calendar.MONTH,offset);
        Set<String> signed=store.days();
        LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(d(16),d(16),d(16),d(28));card.setBackground(a.bg(Color.WHITE,16));card.setTag("check-in-month-card");
        int rows=((month.get(Calendar.DAY_OF_WEEK)+5)%7+month.getActualMaximum(Calendar.DAY_OF_MONTH)+6)/7;
        int height=Math.max(d(rows==6?610:555),a.getResources().getDisplayMetrics().heightPixels-d(160));
        a.root.addView(card,new LinearLayout.LayoutParams(-1,height));
        LinearLayout nav=new LinearLayout(a);nav.setGravity(Gravity.CENTER);
        Button previous=a.action("‹",()->show(shown-1));previous.setTextSize(28);previous.setTextColor(gray);previous.setContentDescription("上个月");
        Button next=a.action("›",()->show(shown+1));next.setTextSize(28);next.setEnabled(offset<0);next.setTextColor(offset<0?gray:0xffe2e2e2);next.setContentDescription("下个月");
        nav.addView(previous,new LinearLayout.LayoutParams(d(44),d(48)));
        TextView title=label(new SimpleDateFormat("yyyy年M月",Locale.CHINA).format(month.getTime()),19,ink);title.setGravity(Gravity.CENTER);title.setTypeface(null,Typeface.BOLD);title.setTag("check-in-month");nav.addView(title,new LinearLayout.LayoutParams(0,d(48),1));
        nav.addView(next,new LinearLayout.LayoutParams(d(44),d(48)));card.addView(nav);gap(card,12);
        View line=new View(a);line.setBackgroundColor(0xfff3f3f3);card.addView(line,new LinearLayout.LayoutParams(-1,d(1)));gap(card,12);weekdays(card);
        int first=(month.get(Calendar.DAY_OF_WEEK)+5)%7,max=month.getActualMaximum(Calendar.DAY_OF_MONTH);
        for(int r=0;r<rows;r++){
            LinearLayout week=new LinearLayout(a);
            for(int c=0;c<7;c++){int value=r*7+c-first+1;
                if(value<1||value>max)week.addView(new View(a),new LinearLayout.LayoutParams(0,d(55),1));
                else{Calendar date=(Calendar)month.clone();date.set(Calendar.DAY_OF_MONTH,value);week.addView(day(date,signed,55));}
            }
            card.addView(week);
        }
        card.addView(new View(a),new LinearLayout.LayoutParams(1,0,1));
        LinearLayout stats=new LinearLayout(a);stats.addView(stat("连续签到",store.streak()));stats.addView(stat("累计签到",signed.size()));card.addView(stats);
    }
    View stat(String title,int count){
        LinearLayout column=new LinearLayout(a);column.setOrientation(1);column.setPadding(d(24),0,0,0);column.addView(label(title,12,gray));gap(column,8);
        LinearLayout line=new LinearLayout(a);line.setGravity(Gravity.BOTTOM);TextView value=label(String.valueOf(count),30,ink);value.setTypeface(null,Typeface.BOLD);line.addView(value);TextView unit=label(" 天",12,gray);unit.setPadding(d(3),0,0,d(5));line.addView(unit);column.addView(line);
        column.setContentDescription(title+" "+count+" 天");column.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));return column;
    }
}
