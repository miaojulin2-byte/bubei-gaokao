package cn.local.gaokao;

import android.app.AlertDialog;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Explicit full-app backups. A restore replaces this app only, across all four databases. */
final class UnifiedBackup {
    static final int WRITE=4201,READ=4202;
    static final String[] SUBJECTS={"common","wenyan","gushi","biology"};
    static String pending;
    static void export(MainActivity a){try{pending=snapshot(a).toString();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"不背高考-"+WordSchedule.today()+".json");a.startActivityForResult(i,WRITE);}catch(Exception e){pending=null;a.error("无法导出："+e.getMessage());}}
    static void choose(MainActivity a){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");a.startActivityForResult(i,READ);}
    static ArrayList<String> tableNames(SQLiteDatabase db){ArrayList<String> names=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata' ORDER BY name",null)){while(c.moveToNext())names.add(c.getString(0));}return names;}
    static JSONObject snapshot(MainActivity a)throws Exception{
        JSONObject root=new JSONObject().put("schema",1).put("app","cn.local.gaokao").put("at",System.currentTimeMillis()),subjects=new JSONObject();
        for(String subject:SUBJECTS)try(StudyStore store=new StudyStore(a,subject)){
            SQLiteDatabase db=store.getReadableDatabase();JSONObject tables=new JSONObject();
            db.beginTransaction();try{for(String name:tableNames(db))try(Cursor c=db.rawQuery("SELECT * FROM \""+name+"\"",null)){
                JSONArray rows=new JSONArray();while(c.moveToNext()){JSONArray row=new JSONArray();for(int n=0;n<c.getColumnCount();n++){int type=c.getType(n);if(type==Cursor.FIELD_TYPE_BLOB)throw new IllegalStateException("Unsupported blob");row.put(type==Cursor.FIELD_TYPE_NULL?JSONObject.NULL:type==Cursor.FIELD_TYPE_INTEGER?c.getLong(n):type==Cursor.FIELD_TYPE_FLOAT?c.getDouble(n):c.getString(n));}rows.put(row);}tables.put(name,new JSONObject().put("columns",new JSONArray(Arrays.asList(c.getColumnNames()))).put("rows",rows));
            }db.setTransactionSuccessful();}finally{db.endTransaction();}
            subjects.put(subject,new JSONObject().put("tables",tables).put("preferences",preferences(a.getSharedPreferences("study-"+subject,0))));
        }
        return root.put("subjects",subjects).put("app_preferences",preferences(a.appPrefs));
    }
    static JSONObject preferences(SharedPreferences preferences)throws Exception{JSONObject out=new JSONObject();for(Map.Entry<String,?> e:preferences.getAll().entrySet()){Object v=e.getValue();String type=v instanceof Boolean?"boolean":v instanceof Integer?"int":v instanceof Long?"long":v instanceof Float?"float":v instanceof Set?"set":"string";out.put(e.getKey(),new JSONObject().put("type",type).put("value",v instanceof Set?new JSONArray((Set<?>)v):v));}return out;}
    static void validatePreferences(JSONObject values)throws Exception{if(values.length()>20000)throw new IllegalArgumentException("偏好数量过多");Iterator<String> keys=values.keys();while(keys.hasNext()){JSONObject item=values.getJSONObject(keys.next());String type=item.getString("type");Object value=item.get("value");if(type.equals("set")){JSONArray array=item.getJSONArray("value");for(int n=0;n<array.length();n++)if(!(array.get(n) instanceof String))throw new IllegalArgumentException("偏好集合无效");}else if(type.equals("boolean")){if(!(value instanceof Boolean))throw new IllegalArgumentException("偏好类型无效");}else if(Arrays.asList("int","long","float").contains(type)){if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue()))throw new IllegalArgumentException("偏好数值无效");}else if(!type.equals("string")||!(value instanceof String))throw new IllegalArgumentException("偏好类型无效");}}
    static void putPreferences(SharedPreferences preferences,JSONObject values)throws Exception{SharedPreferences.Editor edit=preferences.edit().clear();Iterator<String> keys=values.keys();while(keys.hasNext()){String key=keys.next();JSONObject item=values.getJSONObject(key);switch(item.getString("type")){case "boolean":edit.putBoolean(key,item.getBoolean("value"));break;case "int":edit.putInt(key,item.getInt("value"));break;case "long":edit.putLong(key,item.getLong("value"));break;case "float":edit.putFloat(key,(float)item.getDouble("value"));break;case "set":Set<String> set=new HashSet<>();JSONArray array=item.getJSONArray("value");for(int n=0;n<array.length();n++)set.add(array.getString(n));edit.putStringSet(key,set);break;default:edit.putString(key,item.getString("value"));}}if(!edit.commit())throw new IllegalStateException("偏好无法写入");}
    static void validate(MainActivity a,JSONObject root)throws Exception{
        if(root.getInt("schema")!=1||!root.getString("app").equals("cn.local.gaokao"))throw new IllegalArgumentException("不是不背高考的备份");JSONObject subjects=root.getJSONObject("subjects");if(subjects.length()!=4)throw new IllegalArgumentException("备份学科不完整");validatePreferences(root.getJSONObject("app_preferences"));
        for(String subject:SUBJECTS)try(StudyStore store=new StudyStore(a,subject)){
            JSONObject block=subjects.getJSONObject(subject),tables=block.getJSONObject("tables");validatePreferences(block.getJSONObject("preferences"));ArrayList<String> expected=tableNames(store.getReadableDatabase());if(tables.length()!=expected.size())throw new IllegalArgumentException("数据表版本不匹配");
            for(String name:expected){JSONObject table=tables.getJSONObject(name);JSONArray columns=table.getJSONArray("columns"),rows=table.getJSONArray("rows");try(Cursor c=store.getReadableDatabase().rawQuery("SELECT * FROM \""+name+"\" LIMIT 0",null)){if(columns.length()!=c.getColumnCount())throw new IllegalArgumentException("字段不匹配");for(int n=0;n<columns.length();n++)if(!columns.getString(n).equals(c.getColumnName(n)))throw new IllegalArgumentException("字段不匹配");}for(int n=0;n<rows.length();n++){JSONArray row=rows.getJSONArray(n);if(row.length()!=columns.length())throw new IllegalArgumentException("数据行无效");for(int k=0;k<row.length();k++){Object value=row.get(k);if(value!=JSONObject.NULL&&!(value instanceof String)&&!(value instanceof Number))throw new IllegalArgumentException("字段类型无效");}}}
        }
    }
    static void restore(MainActivity a,JSONObject root)throws Exception{
        validate(a,root);SQLiteDatabase master=a.commonStore.getWritableDatabase();int attached=0;JSONObject oldPrefs=new JSONObject();
        try{
            for(int n=1;n<SUBJECTS.length;n++){try(StudyStore initialized=new StudyStore(a,SUBJECTS[n])){initialized.getWritableDatabase();}master.execSQL("ATTACH DATABASE ? AS db"+n,new Object[]{a.getDatabasePath("learning-"+SUBJECTS[n]+".db").getAbsolutePath()});attached++;}
            master.beginTransaction();try{
                for(int n=0;n<SUBJECTS.length;n++){String subject=SUBJECTS[n],schema=n==0?"main":"db"+n;JSONObject tables=root.getJSONObject("subjects").getJSONObject(subject).getJSONObject("tables");for(String name:tableNames(master)){String target=schema+".\""+name+"\"";master.execSQL("DELETE FROM "+target);JSONObject table=tables.getJSONObject(name);JSONArray rows=table.getJSONArray("rows");int width=table.getJSONArray("columns").length();String[] marks=new String[width];Arrays.fill(marks,"?");for(int k=0;k<rows.length();k++){JSONArray row=rows.getJSONArray(k);Object[] values=new Object[width];for(int c=0;c<width;c++)values[c]=row.isNull(c)?null:row.get(c);master.execSQL("INSERT INTO "+target+" VALUES ("+String.join(",",marks)+")",values);}}}
                // Keep preferences recoverable if any write fails before the database transaction commits.
                oldPrefs.put("gaokao",preferences(a.appPrefs));for(String subject:SUBJECTS)oldPrefs.put(subject,preferences(a.getSharedPreferences("study-"+subject,0)));
                for(String subject:SUBJECTS)putPreferences(a.getSharedPreferences("study-"+subject,0),root.getJSONObject("subjects").getJSONObject(subject).getJSONObject("preferences"));putPreferences(a.appPrefs,root.getJSONObject("app_preferences"));master.setTransactionSuccessful();
            }catch(Exception e){if(oldPrefs.has("gaokao")){for(String subject:SUBJECTS)putPreferences(a.getSharedPreferences("study-"+subject,0),oldPrefs.getJSONObject(subject));putPreferences(a.appPrefs,oldPrefs.getJSONObject("gaokao"));}throw e;}finally{master.endTransaction();}
        }finally{for(int n=attached;n>=1;n--)master.execSQL("DETACH DATABASE db"+n);}
    }
    static void result(MainActivity a,int request,int result,Intent data){if(result!=android.app.Activity.RESULT_OK||data==null||data.getData()==null){pending=null;return;}try{
        if(request==WRITE){if(pending==null)throw new IllegalStateException("没有待导出记录");try(java.io.OutputStream out=a.getContentResolver().openOutputStream(data.getData())){out.write(pending.getBytes(java.nio.charset.StandardCharsets.UTF_8));}pending=null;android.widget.Toast.makeText(a,"备份已导出",0).show();}
        else if(request==READ){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(java.io.InputStream in=a.getContentResolver().openInputStream(data.getData())){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>32000000)throw new IllegalArgumentException("备份超过32MB");out.write(bytes,0,n);}}JSONObject root=new JSONObject(out.toString("UTF-8"));validate(a,root);new AlertDialog.Builder(a).setTitle("恢复学习备份").setMessage("将用此备份替换不背高考的三科学习记录、签到和设置。原来的独立 App 不受影响。").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->{try{restore(a,root);a.recreate();}catch(Exception e){a.error("恢复失败："+e.getMessage());}}).show();}
    }catch(Exception e){pending=null;a.error("备份操作失败："+e.getMessage());}}
}
