package cn.local.wenyan;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Original book-shaped badge, shared by home and local profile. */
final class AvatarBadge extends View {
 final Paint paint=new Paint(3);final String symbol;
 AvatarBadge(Context c,String symbol){super(c);this.symbol=symbol;setContentDescription("我的个人页");setFocusable(true);}
 protected void onDraw(Canvas c){float size=Math.min(getWidth(),getHeight());c.save();c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/100f,size/100f);paint.setStyle(Paint.Style.FILL);paint.setColor(0xffcdced0);c.drawCircle(50,50,49,paint);paint.setColor(0xffffe1a0);c.drawCircle(50,50,45,paint);paint.setColor(0xffd5d7d9);c.drawRoundRect(27,21,72,80,5,5,paint);paint.setColor(0xff414753);paint.setStrokeWidth(2.5f);paint.setStyle(Paint.Style.STROKE);c.drawRoundRect(27,21,72,80,5,5,paint);c.drawLine(29,28,69,28,paint);c.drawLine(33,24.5f,38,24.5f,paint);c.drawLine(43,24.5f,48,24.5f,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(0xffd5d7d9);c.drawRoundRect(20,31,48,52,2,2,paint);paint.setColor(0xff414753);paint.setStyle(Paint.Style.STROKE);c.drawRoundRect(20,31,48,52,2,2,paint);c.drawLine(20,37,48,37,paint);c.drawLine(20,45,48,45,paint);c.drawLine(25,53,28,56,paint);c.drawLine(41,53,39,56,paint);c.drawLine(26,57,23,66,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(29,40,1.5f,paint);c.drawCircle(39,40,1.5f,paint);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(23);c.drawText(symbol.equals("古")?"Up":symbol,59,67,paint);c.restore();}
}
