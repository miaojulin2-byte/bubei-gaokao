package cn.local.gaokao;

import android.database.Cursor;
import android.graphics.Typeface;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Read-only catalog. Browsing never reserves a learning queue or records an answer. */
final class WordCatalog {
    final MainActivity a;final boolean startedOnly;boolean familiarOnly;
    final ArrayList<JSONObject> source=new ArrayList<>(),visible=new ArrayList<>();
    final HashMap<String,Integer> points=new HashMap<>();
    String query="";int position,offset;ListView list;BaseAdapter adapter;TextView count;
    WordCatalog(MainActivity a,boolean startedOnly){this.a=a;this.startedOnly=startedOnly;}
    WordCatalog(MainActivity a){this(a,false);familiarOnly=true;}
    int d(int value){return a.dp(value);}
    TextView text(String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    void show(){
        try{
            source.clear();points.clear();
            try(Cursor c=a.store.getReadableDatabase().rawQuery("SELECT id,passes FROM word_pass",null)){while(c.moveToNext())points.put(c.getString(0),c.getInt(1));}
            Set<String> familiar=a.store.familiarIds();JSONArray cards=a.textbookWords().getJSONArray("cards");for(int n=0;n<cards.length();n++){JSONObject card=cards.getJSONObject(n);if((!familiarOnly||familiar.contains(card.getString("id")))&&(!startedOnly||points.containsKey(card.getString("id"))))source.add(card);}
            a.screen("wordCatalog");a.childHeader(familiarOnly?"熟词本":startedOnly?"已开始":"词卡总数",familiarOnly?()->new ProfilePanel(a).show():a::library);a.dock.setVisibility(View.GONE);
            a.frame.removeView((View)a.root.getParent());
            LinearLayout tools=new LinearLayout(a);tools.setOrientation(1);tools.setPadding(d(20),d(10),d(20),d(10));
            EditText search=new EditText(a);search.setSingleLine(true);search.setTextSize(16);search.setHint("搜索词语");search.setContentDescription("搜索词语");search.setText(query);search.setPadding(d(14),0,d(14),0);search.setBackground(a.bg(0xaaffffff,12));tools.addView(search,new LinearLayout.LayoutParams(-1,d(48)));
            count=text("",12,0xff898989);count.setTag("word-catalog-count");count.setPadding(0,d(10),0,0);tools.addView(count);a.frame.addView(tools,new LinearLayout.LayoutParams(-1,-2));
            FrameLayout content=new FrameLayout(a);a.frame.addView(content,new LinearLayout.LayoutParams(-1,0,1));
            list=new ListView(a);list.setTag("word-catalog-list");list.setDivider(null);list.setPadding(d(16),0,d(16),d(16));list.setClipToPadding(false);list.setCacheColorHint(0);content.addView(list,new FrameLayout.LayoutParams(-1,-1));
            TextView empty=text(startedOnly?"暂无已开始的词卡":"暂无匹配词卡",14,0xff898989);empty.setGravity(Gravity.CENTER);content.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
            adapter=new BaseAdapter(){public int getCount(){return visible.size();}public Object getItem(int n){return visible.get(n);}public long getItemId(int n){return n;}public View getView(int n,View old,ViewGroup parent){return row(visible.get(n),old);}};
            list.setAdapter(adapter);list.setOnItemClickListener((parent,view,n,id)->{position=list.getFirstVisiblePosition();View first=list.getChildAt(0);offset=first==null?0:first.getTop();showCard(visible.get(n));});
            filter();list.setSelectionFromTop(position,offset);a.frame.setFocusableInTouchMode(true);a.frame.requestFocus();
            search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int before,int after){}public void onTextChanged(CharSequence s,int start,int before,int after){query=s.toString();position=offset=0;filter();}public void afterTextChanged(Editable e){}});
        }catch(Exception e){a.error("无法读取词卡："+e.getMessage());}
    }
    void filter(){visible.clear();String needle=query.trim();for(JSONObject card:source)if(needle.isEmpty()||card.optString("word").contains(needle))visible.add(card);count.setText(visible.size()+" 个词卡");adapter.notifyDataSetChanged();}
    View row(JSONObject card,View reused){
        LinearLayout outer;
        if(reused instanceof LinearLayout)outer=(LinearLayout)reused;else{
            outer=new LinearLayout(a);outer.setOrientation(1);outer.setPadding(0,0,0,d(8));
            LinearLayout body=new LinearLayout(a);body.setOrientation(1);body.setPadding(d(16),d(14),d(16),d(14));body.setBackground(a.bg(0xddffffff,12));outer.addView(body,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout top=new LinearLayout(a);top.setGravity(Gravity.CENTER_VERTICAL);TextView word=text("",21,0xff26302c);word.setTypeface(null,Typeface.BOLD);top.addView(word,new LinearLayout.LayoutParams(0,-2,1));LinearLayout dots=new LinearLayout(a);for(int n=0;n<4;n++){View dot=new View(a);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(d(7),d(7));p.leftMargin=d(6);dots.addView(dot,p);}top.addView(dots);body.addView(top);
            TextView meaning=text("",14,0xff727b76);meaning.setMaxLines(2);meaning.setEllipsize(TextUtils.TruncateAt.END);meaning.setPadding(0,d(7),0,0);body.addView(meaning);
        }
        LinearLayout body=(LinearLayout)outer.getChildAt(0),top=(LinearLayout)body.getChildAt(0),dots=(LinearLayout)top.getChildAt(1);
        ((TextView)top.getChildAt(0)).setText(card.optString("word"));int value=points.getOrDefault(card.optString("id"),0);
        for(int n=0;n<4;n++)dots.getChildAt(n).setBackground(a.bg(value>=4?0xff219585:n<value?0xffff7900:0x26000000,4));
        JSONArray contexts=card.optJSONArray("contexts");String meaning=contexts!=null&&contexts.length()>0?contexts.optJSONObject(0).optString("meaning"):card.optString("gloss");((TextView)body.getChildAt(1)).setText(meaning);
        outer.setContentDescription(card.optString("word")+"，"+value+" / 4");outer.setTag("word-catalog-item:"+card.optString("id"));return outer;
    }
    void showCard(JSONObject card){new DictionaryPanel(a,this::show).detail(card.optString("word"));if(familiarOnly)a.button("移出熟词本",()->{a.store.setFamiliar(card.optString("id"),false);show();});}
}
