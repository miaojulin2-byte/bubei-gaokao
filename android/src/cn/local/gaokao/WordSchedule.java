package cn.local.gaokao;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Local dates and rating names for the FSRS word-card adapter. */
final class WordSchedule {
    static final int AGAIN=0,HARD=1,GOOD=2,EASY=3;
    /** Injectable for tests; production reads the device clock. */
    static java.util.function.LongSupplier clock=System::currentTimeMillis;
    static long now(){return clock.getAsLong();}
    static String today(){return day(now(),0);}
    /** Local calendar day, so a step lands on a date rather than a fixed 24-hour offset. */
    static String day(long at,int plus){
        Calendar c=Calendar.getInstance();c.setTimeInMillis(at);c.add(Calendar.DAY_OF_MONTH,plus);
        return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(c.getTime());
    }
}
