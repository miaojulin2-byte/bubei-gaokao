package cn.local.gaokao;
import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;
/** Reference-proportioned layout that keeps all three menu rows visible on short phones. */
final class ProfileHome extends FrameLayout {
 final MainActivity a;final ProfilePanel panel;final ArrayList<View> items=new ArrayList<>();final ArrayList<int[]> positions=new ArrayList<>();
 ProfileHome(MainActivity a,ProfilePanel panel){super(a);this.a=a;this.panel=panel;setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xffefeff0,0xffedeef0,0xffe3e8eb}));}
 void add(View view,int x,int y,int w,int h){items.add(view);positions.add(new int[]{x,y,w,h});addView(view);}
 TextView label(String value,int sp,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;}
 ImageView icon(int type,int color){ImageView image=new ImageView(a);image.setImageDrawable(new ProfileIcon(type,a.dp(28),color));image.setScaleType(ImageView.ScaleType.FIT_CENTER);return image;}
 void touch(View view,String name,Runnable action){view.setContentDescription(name);view.setFocusable(true);view.setOnClickListener(v->action.run());}
 void show(){
  a.screen("profile");a.frame.removeAllViews();a.frame.addView(this,new LinearLayout.LayoutParams(-1,0,1));a.backAction=a::home;
  ImageView back=icon(ProfileIcon.BACK,a.INK);touch(back,"返回",a::home);add(back,44,106,64,94);
  ImageView mail=icon(ProfileIcon.MAIL,a.INK);touch(mail,"消息",panel::inbox);add(mail,817,110,72,90);
  AvatarBadge avatar=new AvatarBadge(a,a.prefs.getString("avatar","古"));touch(avatar,"修改头像",panel::appearance);add(avatar,350,280,230,230);
  TextView name=label(a.prefs.getString("nickname","高考学习者"),23,a.INK);name.setGravity(Gravity.CENTER);name.setTypeface(null,Typeface.BOLD);touch(name,"修改昵称",panel::editName);add(name,180,537,570,70);
  LinearLayout membership=new LinearLayout(a);membership.setGravity(Gravity.CENTER);membership.setBackground(a.bg(0xffe5e6e8,28));ImageView vip=icon(ProfileIcon.VIP,0xffa1a5a9);membership.addView(vip,new LinearLayout.LayoutParams(a.dp(22),a.dp(25)));TextView status=label(" 未开通大会员 ",16,0xff74787b);membership.addView(status);membership.addView(icon(ProfileIcon.RIGHT,0xff74787b),new LinearLayout.LayoutParams(a.dp(16),a.dp(20)));touch(membership,"会员信息",()->new AlertDialog.Builder(a).setTitle("不背古文").setMessage("全部学习功能均可免费使用。").setPositiveButton("知道了",null).show());add(membership,294,626,342,82);
  View coins=new View(a);coins.setBackground(a.bg(Color.WHITE,18));touch(coins,"我的酷币",panel::wallet);add(coins,40,864,344,312);
  TextView coinTitle=label("酷币",20,a.INK);touch(coinTitle,"我的酷币",panel::wallet);add(coinTitle,82,907,180,64);ImageView coinArrow=icon(ProfileIcon.RIGHT,0xff888a89);touch(coinArrow,"我的酷币",panel::wallet);add(coinArrow,302,912,32,46);
  ImageView coin=icon(ProfileIcon.COIN,0xffefb518);touch(coin,"我的酷币",panel::wallet);add(coin,82,1040,42,44);TextView balance=label(String.format(Locale.CHINA,"%,d",Wallet.balance(a.commonStore.getReadableDatabase())),23,a.INK);balance.setTypeface(null,Typeface.BOLD);touch(balance,"我的酷币",panel::wallet);add(balance,134,1028,220,68);
  View equipment=new View(a);equipment.setBackground(a.bg(Color.WHITE,18));touch(equipment,"装备",panel::appearance);add(equipment,424,864,466,312);TextView equipmentTitle=label("装备",20,a.INK);touch(equipmentTitle,"装备",panel::appearance);add(equipmentTitle,466,907,180,64);TextView owned=label((1+panel.ownedAvatars())+"/4",17,0xff939595);owned.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);touch(owned,"装备",panel::appearance);add(owned,715,909,85,60);ImageView equipmentArrow=icon(ProfileIcon.RIGHT,0xff888a89);touch(equipmentArrow,"装备",panel::appearance);add(equipmentArrow,810,912,32,46);
  for(int n=0;n<4;n++){FrameLayout badge=new FrameLayout(a);badge.setBackground(a.bg(n==0?0xfff4efff:0xfffafafa,28));ImageView glyph=icon(ProfileIcon.BOOK+n,n==0?0xff9962ed:0xffc8cbca);FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(a.dp(31),a.dp(31),Gravity.CENTER);badge.addView(glyph,gp);touch(badge,"装备",panel::appearance);add(badge,469+n*106,1016,100,102);}
  View menu=new View(a);menu.setBackground(a.bg(Color.WHITE,18));add(menu,40,1214,850,494);
  row("外观 & 沉浸场景",ProfileIcon.PALETTE,0xff39bda4,0xffeefaf5,1254,panel::appearance);
  row("学习偏好",ProfileIcon.SLIDERS,0xffb879ee,0xfffcf5ff,1420,a::settings);
  row("更多设置",ProfileIcon.GEAR,0xff68a6ec,0xfff2f7ff,1584,panel::settings);
 }
 void row(String title,int type,int color,int background,int y,Runnable action){LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);touch(row,title,action);FrameLayout square=new FrameLayout(a);square.setBackground(a.bg(background,9));ImageView image=icon(type,color);square.addView(image,new FrameLayout.LayoutParams(a.dp(28),a.dp(28),Gravity.CENTER));row.addView(square,new LinearLayout.LayoutParams(a.dp(36),a.dp(36)));TextView text=label(title,20,a.INK);text.setPadding(a.dp(14),0,0,0);row.addView(text,new LinearLayout.LayoutParams(0,-1,1));row.addView(icon(ProfileIcon.RIGHT,0xff777b79),new LinearLayout.LayoutParams(a.dp(21),a.dp(24)));add(row,82,y,766,120);}
 @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);setMeasuredDimension(width,height);for(int n=0;n<items.size();n++){int[] p=positions.get(n);items.get(n).measure(MeasureSpec.makeMeasureSpec(Math.round(width*p[2]/930f),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(height*p[3]/2048f),MeasureSpec.EXACTLY));}}
 @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){int width=right-left,height=bottom-top;for(int n=0;n<items.size();n++){int[] p=positions.get(n);int x=Math.round(width*p[0]/930f),y=Math.round(height*p[1]/2048f);View child=items.get(n);child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());}}
}
