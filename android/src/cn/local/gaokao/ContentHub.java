package cn.local.gaokao;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Local content destinations, with counts read from the same catalog and study state. */
final class ContentHub {
    private final MainActivity a;
    private static final int INK=0xff242424,MUTED=0xff8b8b8b;
    ContentHub(MainActivity a){this.a=a;}
    void show(){
        a.screen("contentHub");a.childHeader("我的内容",()->a.home());
        a.dock.setVisibility(View.GONE);
        a.root.setPadding(a.dp(16),a.dp(24),a.dp(16),a.dp(28));
        LinearLayout subjects=group();row(subjects,"学习科目",SubjectRules.title(a.subject),0,0xffff7900,()->a.shell.chooseSubject(this::show));
        LinearLayout practice=new LinearLayout(a);practice.setOrientation(1);practice.setPadding(a.dp(16),a.dp(14),a.dp(16),a.dp(16));practice.setBackground(a.bg(0xffffead8,18));
        TextView caption=text(SubjectRules.title(a.subject),15,0xffe9a059);caption.setTypeface(null,Typeface.BOLD);practice.addView(caption);
        LinearLayout cards=new LinearLayout(a);LinearLayout.LayoutParams cardRow=new LinearLayout.LayoutParams(-1,-2);cardRow.topMargin=a.dp(14);practice.addView(cards,cardRow);
        card(cards,"分册学词",a.wordCardCount(),()->a.showWordVolumes(false),true);
        card(cards,"分册复习",a.wordReviewCount(),()->a.showWordVolumes(true),false);
        LinearLayout.LayoutParams practiceParams=new LinearLayout.LayoutParams(-1,-2);practiceParams.topMargin=a.dp(18);a.root.addView(practice,practiceParams);
        LinearLayout library=group();row(library,"教材分册",WordVolumes.names(a).length+" 个分组",0,0xffff7900,()->a.showWordVolumes(false));
        if(!a.subject.equals("biology"))row(library,"课内篇目",a.libraryTextCount()+" 篇",0,0xff26bba5,()->a.showTextLibrary());
        LinearLayout vocab=group();row(vocab,"词卡总数",a.wordCardCount()+" 个",1,0xffe9b42d,()->new WordCatalog(a,false).show());
        row(vocab,"已开始",a.wordStartedCount()+" 个",7,0xffe9b42d,()->new WordCatalog(a,true).show());
        LinearLayout personal=group();
        row(personal,"单词本",new WordBooks(a).count()+" 本",0,0xff5797ed,()->new WordBooks(a).show());
        row(personal,"复习总览","三科学科",7,0xff5797ed,()->a.shell.reviews());
        row(personal,"真题库","第二阶段",0,0xff5797ed,()->Toast.makeText(a,"真题库将在下一阶段接入",Toast.LENGTH_SHORT).show());
    }
    void reviewOverview(){
        a.screen("contentHub");a.childHeader("复习总览",a::library);a.dock.setVisibility(View.GONE);a.root.setPadding(a.dp(16),a.dp(24),a.dp(16),a.dp(28));
        LinearLayout items=group();for(int n=0;n<SubjectRules.IDS.length;n++){final String id=SubjectRules.IDS[n];int due;try(StudyStore store=new StudyStore(a,id)){due=store.wordDueIds(WordSchedule.today()).size();}row(items,SubjectRules.TITLES[n],due+" 张到期",7,0xffff7900,()->{a.setSubject(id);a.openWordSession(true,WordVolumes.ALL,"",this::reviewOverview);});}
        a.note("生物同笔记的其他填空当天避让，进入后显示实际可练数量。");
    }
    private TextView text(String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private void card(LinearLayout parent,String title,int count,Runnable action,boolean first){
        LinearLayout box=new LinearLayout(a);box.setOrientation(1);box.setPadding(a.dp(14),a.dp(14),a.dp(12),a.dp(12));box.setBackground(a.bg(0xe6ffffff,12));
        TextView name=text(title,16,INK);name.setTypeface(null,Typeface.BOLD);box.addView(name);
        TextView amount=text(count+" 个",13,MUTED);LinearLayout.LayoutParams number=new LinearLayout.LayoutParams(-1,-2);number.topMargin=a.dp(6);box.addView(amount,number);
        TextView arrow=text("›",22,MUTED);arrow.setGravity(Gravity.RIGHT|Gravity.BOTTOM);box.addView(arrow,new LinearLayout.LayoutParams(-1,a.dp(30)));
        boolean enabled=!title.equals("分册复习")||count>0;box.setEnabled(enabled);box.setClickable(enabled);box.setFocusable(true);box.setContentDescription(title+" "+count+" 个");box.setOnClickListener(v->{if(box.isEnabled())action.run();});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(first)p.rightMargin=a.dp(8);else p.leftMargin=a.dp(8);parent.addView(box,p);
    }
    private LinearLayout group(){LinearLayout group=new LinearLayout(a);group.setOrientation(1);group.setBackground(a.bg(Color.WHITE,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=a.dp(18);a.root.addView(group,p);return group;}
    private void row(LinearLayout group,String label,String value,int icon,int color,Runnable action){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(a.dp(16),0,a.dp(16),0);
        ImageView image=new ImageView(a);image.setImageDrawable(new UiIcon(icon,a.dp(22),color));image.setPadding(a.dp(6),a.dp(6),a.dp(6),a.dp(6));image.setBackground(a.bg((color&0x00ffffff)|0x14000000,9));row.addView(image,new LinearLayout.LayoutParams(a.dp(34),a.dp(34)));
        TextView title=text(label,16,INK);title.setPadding(a.dp(12),0,0,0);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView total=text(value,13,MUTED);row.addView(total);TextView arrow=text("›",25,MUTED);arrow.setPadding(a.dp(10),0,0,0);row.addView(arrow);
        row.setClickable(true);row.setFocusable(true);row.setContentDescription(label+(value.isEmpty()?"":" "+value));row.setOnClickListener(v->action.run());group.addView(row,new LinearLayout.LayoutParams(-1,a.dp(64)));
    }
}
