package cn.local.gaokao;
/** FSRS-6 math port from ts-fsrs. See third_party/fsrs/NOTICE.md and LICENSE. */
final class Fsrs {
 static final String VERSION="FSRS-6@28410b2757ca";
 static final double DEFAULT_RETENTION=.90,MIN_S=.001,MAX_S=36500;
 private static final double[] W={.212,1.2931,2.3065,8.2956,6.4133,.8334,3.0194,.001,1.8722,.1666,.796,1.4835,.0614,.2629,1.6483,.6014,1.8729,.5425,.0912,.0658,.1542};
 static final double[] LOWER={.001,.001,.001,.001,1,.001,.001,.001,0,0,.001,.001,.001,.001,0,0,1,0,0,0,.1};
 static final double[] UPPER={100,100,100,100,10,4,4,.75,4.5,.8,3.5,5,.25,.9,4,1,6,2,2,.8,.8};
 static final Model DEFAULT=new Model(W);
 static double[] defaultWeights(){return W.clone();}
 static final class Memory {final double stability,difficulty;Memory(double s,double d){if(!Double.isFinite(s)||!Double.isFinite(d)||s<MIN_S||s>MAX_S||d<1||d>10)throw new IllegalArgumentException("Invalid FSRS memory");stability=s;difficulty=d;}}
 private static double clip(double x,double min,double max){return Math.min(Math.max(x,min),max);}
 private static double round(double x){return Math.round(x*1e8)/1e8;}
 static Memory next(Memory old,double elapsed,int grade){return DEFAULT.next(old,elapsed,grade);}
 static double retrievability(Memory m,double elapsed){return DEFAULT.retrievability(m,elapsed);}
 static int interval(Memory m,double retention){return DEFAULT.interval(m,retention);}
 static final class Model {
  private final double[] w;private final double decay,factor;
  Model(double[] parameters){if(parameters==null||parameters.length!=21)throw new IllegalArgumentException("FSRS requires 21 weights");w=parameters.clone();for(int n=0;n<21;n++)if(!Double.isFinite(w[n])||w[n]<LOWER[n]||w[n]>UPPER[n])throw new IllegalArgumentException("FSRS weight out of bounds: "+n);decay=-w[20];factor=round(Math.exp(Math.log(.9)/decay)-1);}
  double[] weights(){return w.clone();}
  private double initialDifficulty(int grade){return round(w[4]-Math.exp((grade-1)*w[5])+1);}
  double retrievability(Memory m,double elapsed){if(!Double.isFinite(elapsed)||elapsed<0)throw new IllegalArgumentException("Invalid FSRS elapsed days");return round(Math.pow(1+factor*elapsed/m.stability,decay));}
  Memory next(Memory old,double elapsed,int grade){
   if(grade<1||grade>4||!Double.isFinite(elapsed)||elapsed<0)throw new IllegalArgumentException("Invalid FSRS input");
   if(old==null)return new Memory(clip(w[grade-1],MIN_S,MAX_S),clip(initialDifficulty(grade),1,10));
   double s=old.stability,d=old.difficulty,r=retrievability(old,elapsed),nextS;
   if(elapsed==0){double gain=Math.pow(s,-w[19])*Math.exp(w[17]*(grade-3+w[18]));if(grade>=2)gain=Math.max(gain,1);nextS=round(clip(s*gain,MIN_S,MAX_S));}
   else if(grade==1){double failed=round(clip(w[11]*Math.pow(d,-w[12])*(Math.pow(s+1,w[13])-1)*Math.exp((1-r)*w[14]),MIN_S,MAX_S));nextS=clip(round(s/Math.exp(w[17]*w[18])),MIN_S,failed);}
   else{double hard=grade==2?w[15]:1,easy=grade==4?w[16]:1;nextS=round(clip(s*(1+Math.exp(w[8])*(11-d)*Math.pow(s,-w[9])*(Math.exp((1-r)*w[10])-1)*hard*easy),MIN_S,MAX_S));}
   double damp=round((-w[6]*(grade-3))*(10-d)/9),nextD=clip(round(w[7]*initialDifficulty(4)+(1-w[7])*(d+damp)),1,10);return new Memory(nextS,nextD);
  }
  int interval(Memory m,double retention){if(!Double.isFinite(retention)||retention<=0||retention>1)throw new IllegalArgumentException("Invalid FSRS retention");return (int)clip(Math.round(m.stability/factor*(Math.pow(retention,1/decay)-1)),1,MAX_S);}
 }
}
