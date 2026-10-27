package cn.local.gaokao;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;
/** Local bounded 21-parameter sequence fitting. Uses recall log loss and a chronological holdout. */
final class FsrsTrainer {
 static final int MIN_REVIEWS=100,MIN_CARDS=10,MAX_TARGETS=5000,PASSES=16;
 static final class Review {
  final String id;final long day;final int grade,order;int target=-1;
  Review(String id,long day,int grade,int order){if(grade<1||grade>4)throw new IllegalArgumentException("Invalid review rating");this.id=id;this.day=day;this.grade=grade;this.order=order;}
 }
 static final class History {
  final String id;final Fsrs.Memory seed;final long seedDay;final ArrayList<Review> reviews=new ArrayList<>();
  History(String id,Fsrs.Memory seed,long seedDay){this.id=id;this.seed=seed;this.seedDay=seedDay;}
 }
 static final class Data {
  final ArrayList<History> histories;final int total,split,cards,forgotten,recalled,holdoutForgotten,holdoutRecalled;
  Data(Collection<History> source){histories=new ArrayList<>(source);ArrayList<Review> targets=new ArrayList<>();for(History h:histories){boolean known=h.seed!=null;long day=h.seedDay;for(Review r:h.reviews){r.target=-1;if(known&&r.day>day)targets.add(r);day=r.day;known=true;}}
   targets.sort(Comparator.comparingInt(r->r.order));int first=Math.max(0,targets.size()-MAX_TARGETS);total=targets.size()-first;split=(int)(total*.8);Set<String> ids=new HashSet<>();int fail=0,good=0,hf=0,hg=0;for(int n=first;n<targets.size();n++){Review r=targets.get(n);r.target=n-first;ids.add(r.id);if(r.grade==1)fail++;else good++;if(r.target>=split){if(r.grade==1)hf++;else hg++;}}cards=ids.size();forgotten=fail;recalled=good;holdoutForgotten=hf;holdoutRecalled=hg;
  }
  boolean sufficient(){return total>=MIN_REVIEWS&&cards>=MIN_CARDS&&forgotten-holdoutForgotten>=5&&recalled-holdoutRecalled>=20&&holdoutForgotten>=1&&holdoutRecalled>=1;}
 }
 static final class Result {
  final double[] weights;final double trainingBefore,trainingAfter,validationBefore,validationAfter,defaultValidation;final boolean usable;
  Result(double[] w,double tb,double ta,double vb,double va,double dv){weights=w.clone();trainingBefore=tb;trainingAfter=ta;validationBefore=vb;validationAfter=va;defaultValidation=dv;usable=Double.isFinite(ta)&&Double.isFinite(va)&&ta<tb-1e-6&&va<Math.min(vb,dv)-1e-5;}
 }
 // Same-day practice updates the sequence state, but contributes no recall target to the loss.
 static double loss(Data data,double[] weights,boolean validation){Fsrs.Model model=new Fsrs.Model(weights);double sum=0;int count=0;for(History h:data.histories){Fsrs.Memory memory=h.seed;long last=h.seedDay;for(Review r:h.reviews){double elapsed=memory==null?0:r.day-last;if(elapsed<0)throw new IllegalArgumentException("Review dates are not ordered");if(r.target>=0&&(validation?r.target>=data.split:r.target<data.split)){double probability=Math.max(1e-6,Math.min(1-1e-6,model.retrievability(memory,elapsed)));sum-=r.grade==1?Math.log1p(-probability):Math.log(probability);count++;}memory=model.next(memory,elapsed,r.grade);last=r.day;}}return count==0?Double.NaN:sum/count;}
 static double objective(Data data,double[] w){double penalty=0;double[] prior=Fsrs.defaultWeights();for(int n=0;n<21;n++){double z=(w[n]-prior[n])/Math.max(.2,Math.abs(prior[n]));penalty+=z*z;}return loss(data,w,false)+.002*penalty/21;}
 static double[] project(double[] w){double[] v=w.clone();for(int n=0;n<21;n++)v[n]=Math.max(Fsrs.LOWER[n],Math.min(Fsrs.UPPER[n],v[n]));Arrays.sort(v,0,4);return v;}
 static Result train(Data data,double[] current,IntConsumer progress){if(!data.sufficient())throw new IllegalArgumentException("Insufficient cross-day recall history");new Fsrs.Model(current);double[] best=current.clone(),scale=Fsrs.defaultWeights();double score=objective(data,best),before=loss(data,current,false);
  for(int pass=0;pass<PASSES;pass++){if(Thread.currentThread().isInterrupted())throw new CancellationException();for(int n=0;n<21;n++){double step=Math.max(.2,Math.abs(scale[n]))*.3*Math.pow(.78,pass);double[] origin=best.clone();for(int direction:new int[]{-1,1}){if(Thread.currentThread().isInterrupted())throw new CancellationException();double[] candidate=origin.clone();candidate[n]+=step*direction;candidate=project(candidate);double value=objective(data,candidate);if(Double.isFinite(value)&&value<score-1e-9){best=candidate;score=value;}}}if(progress!=null)progress.accept((pass+1)*100/PASSES);}
  return new Result(best,before,loss(data,best,false),loss(data,current,true),loss(data,best,true),loss(data,Fsrs.defaultWeights(),true));
 }
}
