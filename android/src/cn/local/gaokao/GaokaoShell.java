package cn.local.gaokao;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Subject and backup adapters. Visible pages reuse the original Wenyan UI components. */
final class GaokaoShell {
    final MainActivity a;
    GaokaoShell(MainActivity a){this.a=a;}
    void home(){a.home();}
    void books(){a.showWordVolumes(false);}
    void book(String volume){a.showWordVolumeContents(false,volume,a::library);}
    void stats(){new LearningPanel(a,false).show();}
    void settings(){new LearningPanel(a,true).show();}
    void reviews(){new ContentHub(a).reviewOverview();}
    void chooseSubject(Runnable after){
        PreferenceSheet sheet=new PreferenceSheet(a,"学习科目");
        for(int n=0;n<SubjectRules.IDS.length;n++){final String id=SubjectRules.IDS[n];sheet.choice(SubjectRules.TITLES[n],a.subject.equals(id),()->{sheet.dismiss();a.setSubject(id);after.run();});}
        sheet.show();
    }
    String cover(String volume){
        String[] chinese={"必修上","必修下","选择性必修上","选择性必修中","选择性必修下"};for(int n=0;n<chinese.length;n++)if(chinese[n].equals(volume))return "covers/cn"+n+".jpg";
        String[] names={"分子与细胞","遗传与进化","稳态与调节","生物与环境","生物技术与工程"},files={"b1","b2","x1","x2","x3"};for(int n=0;n<names.length;n++)if(names[n].equals(volume))return "biology/covers/"+files[n]+".jpg";return null;
    }
    void note(String id){EditText input=new EditText(a);input.setText(a.prefs.getString("note:"+id,""));input.setMinLines(4);new AlertDialog.Builder(a).setTitle("我的笔记").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->a.prefs.edit().putString("note:"+id,input.getText().toString()).commit()).show();}
    void image(String name){try(java.io.InputStream in=a.getAssets().open("biology/"+name)){ImageView view=new ImageView(a);view.setAdjustViewBounds(true);view.setImageBitmap(BitmapFactory.decodeStream(in));ScrollView scroll=new ScrollView(a);scroll.addView(view);new AlertDialog.Builder(a).setView(scroll).setPositiveButton("关闭",null).show();}catch(Exception e){a.error("图片暂时无法打开");}}
}
