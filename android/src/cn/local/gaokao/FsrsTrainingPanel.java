package cn.local.gaokao;
import android.app.*;
import android.content.DialogInterface;
import android.widget.*;
import java.util.*;
/** Training stays on this device. Candidate parameters must pass the held-out-history check. */
final class FsrsTrainingPanel {
 final MainActivity a;FsrsTrainingPanel(MainActivity a){this.a=a;}
 void show(){a.screen("fsrsTraining");a.childHeader("个人记忆参数",a::settings);a.dock.setVisibility(android.view.View.GONE);try{
  FsrsPersonal.Snapshot source=FsrsPersonal.snapshot(a.store.getReadableDatabase());a.title(source.active==null?"默认权重":"个人权重");a.label("有效跨日复习 "+source.data.total+" 次",18);a.note("来自 "+source.data.cards+" 张词卡；只使用真实自评，不包含浏览和标熟。");a.note("用本机复习记录训练21项参数。最后20%的记录用于检验，不参与拟合。新参数仅影响后续调度，现有到期日不变。");
  if(!source.data.sufficient())a.note("本机训练需至少100次跨日复习、10张词卡，并有足够的记住与遗忘反馈。当前记录不足，继续诚实自评即可。");Button train=a.button("训练个人权重",()->start(source));train.setEnabled(source.data.sufficient());a.button("熟词本",()->new WordCatalog(a).show());if(source.active!=null)a.button("恢复默认权重",()->{FsrsPersonal.restoreDefault(a.store);show();});
 }catch(Exception e){a.note("暂时无法读取训练记录，请重试。");android.util.Log.e("WenyanFsrs","Training history",e);}}
 void start(FsrsPersonal.Snapshot source){ProgressDialog progress=new ProgressDialog(a);progress.setTitle("训练个人权重");progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);progress.setMax(100);progress.setMessage("仅在本机运行");progress.setCancelable(true);Thread worker=new Thread(()->{try{FsrsTrainer.Result result=FsrsTrainer.train(source.data,source.current,n->a.runOnUiThread(()->{if(progress.isShowing())progress.setProgress(n);}));a.runOnUiThread(()->{if(!progress.isShowing())return;progress.dismiss();result(source,result);});}catch(java.util.concurrent.CancellationException cancelled){a.runOnUiThread(progress::dismiss);}catch(Exception error){a.runOnUiThread(()->{boolean visible=progress.isShowing();progress.dismiss();if(visible)a.error("训练未完成，当前参数和学习记录未改动。");});}},"FSRS-personal-trainer");progress.setOnCancelListener(d->worker.interrupt());progress.setButton(DialogInterface.BUTTON_NEGATIVE,"取消",(d,w)->{worker.interrupt();progress.cancel();});progress.show();worker.start();}
 void result(FsrsPersonal.Snapshot source,FsrsTrainer.Result result){String message=result.usable?"个人参数在留出记录上的预测误差更低。\n\n":"本次参数没有通过留出记录检验，继续保留当前权重。\n\n";message+=String.format(Locale.CHINA,"检验误差：%.4f → %.4f\n有效跨日复习：%d次",result.validationBefore,result.validationAfter,source.data.total);AlertDialog.Builder dialog=new AlertDialog.Builder(a).setTitle("训练结果").setMessage(message).setNegativeButton("关闭",null);if(result.usable)dialog.setPositiveButton("使用个人权重",(d,w)->{try{if(!FsrsPersonal.activate(a.store,source,result,WordSchedule.now()))a.error("学习记录已变化，请重新训练。");else show();}catch(Exception e){a.error("个人参数未保存，原参数保留。");}});dialog.show();}
}
