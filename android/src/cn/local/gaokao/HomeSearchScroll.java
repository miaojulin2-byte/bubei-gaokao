package cn.local.gaokao;
import android.view.MotionEvent;
import android.widget.ScrollView;
/** Detect a deliberate downward pull before a clickable quote or other child consumes it. */
final class HomeSearchScroll extends ScrollView {
 final MainActivity a;float startX,startY;boolean opened;
 HomeSearchScroll(MainActivity a){super(a);this.a=a;}
 boolean pull(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();opened=false;}if(!opened&&e.getActionMasked()==MotionEvent.ACTION_MOVE&&getScrollY()==0&&e.getY()-startY>a.dp(90)&&Math.abs(e.getX()-startX)<a.dp(60)){opened=true;post(()->{if(a.page.equals("home"))new DictionaryPanel(a,a::home).show();});return true;}return opened;}
 @Override public boolean onInterceptTouchEvent(MotionEvent e){return pull(e)||super.onInterceptTouchEvent(e);}
 @Override public boolean onTouchEvent(MotionEvent e){return pull(e)||super.onTouchEvent(e);}
}
