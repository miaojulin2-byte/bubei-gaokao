package cn.local.gaokao;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Native line artwork matching the supplied profile reference. No font glyph icons. */
final class ProfileIcon extends Drawable {
 static final int PALETTE=0,SLIDERS=1,GEAR=2,MAIL=3,COIN=4,VIP=5,BOOK=6,TREE=7,DICTIONARY=8,SHUFFLE=9,RIGHT=10,BACK=11;
 final int kind,size,color;final Paint p=new Paint(3);
 ProfileIcon(int kind,int size,int color){this.kind=kind;this.size=size;this.color=color;setBounds(0,0,size,size);}
 public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
 void line(Canvas c,float x,float y,float xx,float yy){c.drawLine(x,y,xx,yy,p);}void fill(int color){p.setStyle(Paint.Style.FILL);p.setColor(color);}void stroke(){p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(2.1f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
 public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/32f,getBounds().height()/32f);stroke();
  if(kind==PALETTE){Path path=new Path();path.moveTo(17,4);path.cubicTo(5,2,0,19,9,26);path.cubicTo(15,31,20,27,19,23);path.cubicTo(18,19,23,22,27,17);path.cubicTo(32,10,25,4,17,4);path.close();c.drawPath(path,p);fill(color);for(float[] xy:new float[][]{{10,11},{18,9},{24,14},{9,19}})c.drawCircle(xy[0],xy[1],2,p);}
  else if(kind==SLIDERS){for(int n=0;n<3;n++){float y=8+n*8,x=n==0?12:n==1?22:9;line(c,4,y,28,y);fill(0xfffcf6ff);c.drawCircle(x,y,3,p);stroke();c.drawCircle(x,y,3,p);}}
  else if(kind==GEAR){Path path=new Path();for(int n=0;n<40;n++){double angle=2*Math.PI*n/40-Math.PI/2;float r=(n%5==0||n%5==4)?10:13;float x=16+(float)Math.cos(angle)*r,y=16+(float)Math.sin(angle)*r;if(n==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();c.drawPath(path,p);c.drawCircle(16,16,4.5f,p);}
  else if(kind==MAIL){c.drawRoundRect(3,6,29,26,2,2,p);line(c,4,8,16,17);line(c,16,17,28,8);}
  else if(kind==COIN){fill(0xffffd64e);c.drawCircle(16,16,14,p);fill(0xfff6b50a);c.drawCircle(16,16,11,p);p.setColor(0xfffff2b0);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.4f);c.drawArc(10,9,23,24,50,260,false,p);line(c,15,6,15,10);line(c,19,6,19,10);line(c,15,23,15,27);line(c,19,23,19,27);}
  else if(kind==VIP){Path shield=new Path();shield.moveTo(16,5);shield.lineTo(27,9);shield.lineTo(25,23);shield.lineTo(16,29);shield.lineTo(7,23);shield.lineTo(5,9);shield.close();c.drawPath(shield,p);line(c,10,6,9,1);line(c,9,1,16,4);line(c,16,4,22,1);line(c,22,1,21,6);p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(8);p.setTextAlign(Paint.Align.CENTER);c.drawText("VIP",16,19,p);}
  else if(kind==BOOK){c.drawRoundRect(7,3,27,29,3,3,p);for(int y=7;y<28;y+=5){line(c,4,y,9,y);}line(c,13,9,21,9);line(c,13,14,21,14);fill(0xffffa32a);c.drawRoundRect(16,18,30,30,4,4,p);fill(Color.WHITE);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(18);p.setTextAlign(Paint.Align.CENTER);c.drawText("∞",23,29,p);}
  else if(kind==TREE){c.drawRoundRect(11,3,21,11,1,1,p);line(c,16,11,16,18);line(c,6,18,26,18);line(c,6,18,6,23);line(c,16,18,16,23);line(c,26,18,26,23);for(int x:new int[]{2,12,22})c.drawRoundRect(x,23,x+8,29,1,1,p);}
  else if(kind==DICTIONARY){c.drawRoundRect(5,3,26,29,2,2,p);line(c,9,3,9,29);line(c,13,17,23,17);line(c,13,22,23,22);fill(color);p.setTextSize(5);p.setTextAlign(Paint.Align.CENTER);c.drawText("古文",17,12,p);}
  else if(kind==SHUFFLE){c.drawRoundRect(3,3,29,29,2,2,p);Path path=new Path();path.moveTo(8,11);path.lineTo(12,11);path.lineTo(20,22);path.lineTo(25,22);path.moveTo(8,22);path.lineTo(12,22);path.lineTo(20,11);path.lineTo(25,11);c.drawPath(path,p);line(c,22,8,25,11);line(c,25,11,22,14);line(c,22,19,25,22);line(c,25,22,22,25);}
  else {Path path=new Path();if(kind==BACK){path.moveTo(21,5);path.lineTo(10,16);path.lineTo(21,27);}else{path.moveTo(12,8);path.lineTo(20,16);path.lineTo(12,24);}c.drawPath(path,p);}c.restore();
 }
 public void setAlpha(int alpha){p.setAlpha(alpha);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
