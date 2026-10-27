package cn.local.wenyan;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.InputStream;
/** Original generated wallpaper and a low-detail study surface. */
public final class Atmosphere extends View {
 private final boolean landscape,study,fixed; private final Paint p=new Paint(3); private static Bitmap wallpaper,palette; private static int wallpaperIndex=-1;
 public Atmosphere(Context c,boolean scene){super(c);landscape=scene;study=c instanceof MainActivity&&((MainActivity)c).page.equals("study");fixed=c instanceof MainActivity&&(((MainActivity)c).page.equals("profile")||((MainActivity)c).page.equals("contentHub")||((MainActivity)c).page.equals("stats")||((MainActivity)c).page.equals("statsDetail")||((MainActivity)c).page.equals("calendar")||((MainActivity)c).page.equals("textLibrary"));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);if(!fixed)ensureWallpaper(c);}
 private static void ensureWallpaper(Context c){
  int index=WallpaperRotation.todayIndex();
  if(wallpaper!=null&&index==wallpaperIndex)return;
  wallpaper=WallpaperRotation.load(c);
  palette=WallpaperRotation.loadPalette(c);
  if(wallpaper==null)wallpaper=BitmapFactory.decodeResource(c.getResources(),c.getResources().getIdentifier("study_wallpaper","drawable",c.getPackageName()));
  if(palette==null)palette=wallpaper;
  wallpaperIndex=index;
 }
 protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();if(fixed){p.setShader(new LinearGradient(0,0,w*.65f,h,new int[]{0xffdfe5ed,0xfff3e0d6,0xffe4dfd3},new float[]{0,.47f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);return;}if(landscape&&wallpaper!=null){float scale=Math.max(w/wallpaper.getWidth(),h/wallpaper.getHeight());float bw=wallpaper.getWidth()*scale,bh=wallpaper.getHeight()*scale;c.drawBitmap(wallpaper,null,new RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),p);p.setShader(new LinearGradient(0,h*.78f,0,h,new int[]{0x00ffffff,0xb3ffffff,0xb3ffffff},new float[]{0,.30f,1},Shader.TileMode.CLAMP));c.drawRect(0,h*.78f,w,h,p);p.setShader(null);return;}if(!landscape&&palette!=null){float scale=Math.max(w/palette.getWidth(),h/palette.getHeight());float bw=palette.getWidth()*scale,bh=palette.getHeight()*scale;c.drawBitmap(palette,null,new RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),p);return;}p.setShader(new LinearGradient(0,0,w*.65f,h,study?new int[]{0xffe4e9ef,0xffe7ebf1,0xffdce2ed}:new int[]{0xffdfe5ed,0xfff3e0d6,0xffe4dfd3},new float[]{0,.47f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);}
}
