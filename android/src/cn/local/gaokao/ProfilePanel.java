package cn.local.gaokao;
import android.app.AlertDialog;
import android.database.Cursor;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
/** Local profile and auditable virtual rewards; no account or subscription fiction. */
final class ProfilePanel {
 final MainActivity a;ProfilePanel(MainActivity a){this.a=a;}
 void page(String title,Runnable back){a.screen("profile");a.childHeader(title,back);a.dock.setVisibility(View.GONE);}
 void show(){new ProfileHome(a,this).show();}
 void editName(){EditText input=new EditText(a);input.setSingleLine();input.setText(a.prefs.getString("nickname","高考学习者"));new AlertDialog.Builder(a).setTitle("昵称").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{String value=input.getText().toString().trim();if(!value.isEmpty())a.prefs.edit().putString("nickname",value.substring(0,Math.min(16,value.length()))).apply();show();}).show();}
 void inbox(){page("消息",this::show);int count=0;try(Cursor c=a.commonStore.getReadableDatabase().rawQuery("SELECT reason,amount,at FROM coin_ledger WHERE amount>0 ORDER BY at DESC,rowid DESC LIMIT 50",null)){while(c.moveToNext()){a.label(c.getString(0)+"　+"+c.getInt(1)+" 酷币",18);a.note(new SimpleDateFormat("yyyy年M月d日 HH:mm",Locale.CHINA).format(new Date(c.getLong(2))));count++;}}if(count==0)a.note("暂无消息");}
 int ownedAvatars(){int n=0;for(String value:new String[]{"竹","山","月"})if(Wallet.owned(a.commonStore,"avatar:"+value))n++;return n;}
 void settings(){page("更多设置",this::show);Switch motion=new Switch(a);motion.setText("开场动画");motion.setChecked(a.prefs.getBoolean("intro_motion",true));motion.setOnCheckedChangeListener((v,on)->a.prefs.edit().putBoolean("intro_motion",on).apply());a.root.addView(motion);a.button("熟词本",()->new WordCatalog(a).show());a.button("酷币攻略",this::guide);a.note("不背高考 · 本机离线学习");}
 void wallet(){page("我的酷币",this::show);TextView balance=a.label("酷币余额\n"+String.format(Locale.CHINA,"%,d",Wallet.balance(a.commonStore.getReadableDatabase())),36);balance.setTextColor(0xffedaa13);balance.setBackground(a.bg(0xeefff7d9,18));balance.setPadding(a.dp(20),a.dp(24),a.dp(20),a.dp(24));a.button("查看攻略  ›",this::guide);a.title("酷币明细");
  try(Cursor c=a.commonStore.getReadableDatabase().rawQuery("SELECT amount,reason,at FROM coin_ledger ORDER BY at DESC,rowid DESC",null)){if(!c.moveToFirst())a.note("暂无记录");else do{int n=c.getInt(0);a.label(c.getString(1)+"　"+(n>0?"+":"")+n,18);a.note(new SimpleDateFormat("yyyy年M月d日 HH:mm",Locale.CHINA).format(new Date(c.getLong(2))));}while(c.moveToNext());}
 }
 void guide(){page("酷币攻略",this::wallet);a.title("什么是酷币？");a.note("酷币是本机内的虚拟奖励，可兑换头像外观，不充值、不提现。");a.title("签到奖励");a.label("每日在首页签到 +10\n连续第7天 +35，第30天 +150\n第90天 +450，第180天 +900\n第360天 +1800",16);a.note("连续奖励在对应当天正常签到时发放，每天仅发放一次。不支持补签。");a.title("学习与复习奖励");a.label("完成当天已分配的新学计划 +20\n完成一组到期复习 +10",16);a.note("学习奖励按科保存，当前酷币页显示全应用签到账户。两类学习奖励每天各限一次。提前练习、标熟和浏览不发奖励。重置学习记录保留酷币与已兑换外观，同日不能重复领取。");}
 void appearance(){page("外观与沉浸场景",this::show);a.title("场景");a.button("自动轮换 · 清风、秋水与湖雪",()->{a.prefs.edit().remove("scene_fixed").apply();WallpaperRotation.beginSession(a);appearance();});for(int n=0;n<WallpaperRotation.THEMES.length;n++){final int pick=n;a.button(WallpaperRotation.THEMES[n]+"  ·  "+WallpaperRotation.WORDS[n],()->{a.prefs.edit().putInt("scene_fixed",pick).apply();WallpaperRotation.beginSession(a);appearance();});}a.title("头像");a.button("古 · 默认",()->{a.prefs.edit().putString("avatar","古").apply();show();});String[] symbols={"竹","山","月"};int[] prices={30,60,90};for(int n=0;n<symbols.length;n++){String symbol=symbols[n],id="avatar:"+symbol;int price=prices[n];a.button(symbol+"　"+(Wallet.owned(a.commonStore,id)?"使用":price+" 酷币"),()->{if(Wallet.owned(a.commonStore,id)){a.prefs.edit().putString("avatar",symbol).apply();show();return;}new AlertDialog.Builder(a).setTitle("兑换“"+symbol+"”头像").setMessage(price+" 酷币").setNegativeButton("取消",null).setPositiveButton("兑换",(d,w)->{try{if(Wallet.purchase(a.commonStore,id,price,WordSchedule.now())){a.prefs.edit().putString("avatar",symbol).apply();show();}else Toast.makeText(a,"酷币不足",Toast.LENGTH_SHORT).show();}catch(Exception e){a.error("兑换未保存，请重试");}}).show();});}}
}
