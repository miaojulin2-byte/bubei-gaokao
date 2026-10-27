package cn.local.gaokao;

/** Independent, deterministic scheduling policy. Not BBDC's proprietary algorithm. */
public final class StudyEngine {
    public static final long MINUTE=60000L, DAY=86400000L;
    public static final int[] DAYS={1,3,7,14,30,60};
    public static final class State {
        public int stage, interval, lapses, attempts; public long due, started; public boolean suspended;
        public State copy(){State n=new State();n.stage=stage;n.interval=interval;n.lapses=lapses;n.attempts=attempts;n.due=due;n.started=started;n.suspended=suspended;return n;}
    }
    public static State start(State old,long now) {State s=old.copy();if(s.stage==0){s.stage=1;s.started=now;s.due=now;}return s;}
    public static State answer(State old,boolean correct,boolean hinted,long now){
        return answer(old,correct?2:0,hinted,now);
    }
    public static State answer(State old,int rating,boolean hinted,long now){
        if(rating<0||rating>2)throw new IllegalArgumentException("Invalid rating");
        if(old.stage==0||old.suspended)throw new IllegalStateException("Word is not active");
        State s=old.copy();s.attempts++;
        if(rating==0||hinted){s.lapses++;s.stage=1;s.interval=0;s.due=now+10*MINUTE;return s;}
        if(rating==1){s.due=now+(s.stage>=3?DAY:2*MINUTE);if(s.stage>=3)s.interval=Math.max(0,s.interval-1);return s;}
        if(s.stage==1){s.stage=2;s.due=now+2*MINUTE;return s;}
        if(s.stage==2){s.stage=3;s.interval=0;s.due=now+DAY;return s;}
        s.interval=Math.min(DAYS.length-1,s.interval+1);s.due=now+DAYS[s.interval]*DAY;return s;
    }
}
