package com.jinnan.miaomiaoreader;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.io.*;
import java.util.*;

public final class ReaderStore extends SQLiteOpenHelper {
    private final SQLiteDatabase dictionary,general;
    public ReaderStore(Context context)throws IOException{
        this(context,"reader.sqlite");
    }
    ReaderStore(Context context,String databaseName)throws IOException{
        super(context,databaseName,null,2);
        File seed=new File(context.getFilesDir(),"dictionary-v1.sqlite");
        dictionary=seed.exists()?SQLiteDatabase.openDatabase(seed.getPath(),null,SQLiteDatabase.OPEN_READONLY):null;
        File extra=new File(context.getFilesDir(),"general-dictionary-v1.sqlite");
        if(!extra.exists()){File temp=new File(extra+".tmp");try(InputStream in=context.getAssets().open("general_dictionary.sqlite");OutputStream out=new FileOutputStream(temp)){copy(in,out);}if(!temp.renameTo(extra))throw new IOException("无法安装通用词库");}
        general=SQLiteDatabase.openDatabase(extra.getPath(),null,SQLiteDatabase.OPEN_READONLY);
        getWritableDatabase();
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE documents(id TEXT PRIMARY KEY,name TEXT NOT NULL,file TEXT NOT NULL,pages INTEGER NOT NULL,current_page INTEGER NOT NULL DEFAULT 0,created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE page_cache(doc TEXT NOT NULL,page INTEGER NOT NULL,data TEXT NOT NULL,PRIMARY KEY(doc,page))");
        db.execSQL("CREATE TABLE annotations(id INTEGER PRIMARY KEY AUTOINCREMENT,doc TEXT NOT NULL,page INTEGER NOT NULL,start_token INTEGER NOT NULL,end_token INTEGER NOT NULL,quote TEXT NOT NULL,translation TEXT NOT NULL,style TEXT NOT NULL,visible INTEGER NOT NULL DEFAULT 1,color TEXT NOT NULL DEFAULT 'honey',sentence TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX annotation_page ON annotations(doc,page)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){
            db.execSQL("ALTER TABLE annotations ADD COLUMN color TEXT NOT NULL DEFAULT 'honey'");
            db.execSQL("ALTER TABLE annotations ADD COLUMN sentence TEXT NOT NULL DEFAULT ''");
        }
    }
    public synchronized JSONObject document(String id)throws JSONException{try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM documents WHERE id=?",new String[]{id})){return c.moveToFirst()?row(c):null;}}
    public synchronized JSONArray documents()throws JSONException{return query("SELECT * FROM documents ORDER BY created DESC",null);}
    public synchronized void addDocument(String id,String name,String file,int pages){
        ContentValues v=new ContentValues();v.put("id",id);v.put("name",name);v.put("file",file);v.put("pages",pages);v.put("current_page",0);v.put("created",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("documents",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public synchronized void progress(String doc,int page){ContentValues v=new ContentValues();v.put("current_page",page);getWritableDatabase().update("documents",v,"id=?",new String[]{doc});}
    public synchronized JSONObject cachedPage(String doc,int page)throws JSONException{try(Cursor c=getReadableDatabase().rawQuery("SELECT data FROM page_cache WHERE doc=? AND page=?",new String[]{doc,""+page})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}}
    public synchronized void cache(String doc,int page,JSONObject data){ContentValues v=new ContentValues();v.put("doc",doc);v.put("page",page);v.put("data",data.toString());getWritableDatabase().insertWithOnConflict("page_cache",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public synchronized JSONArray annotations(String doc,int page)throws JSONException{return query("SELECT id,start_token AS start,end_token AS end,quote,translation,style,visible,page,color,sentence FROM annotations WHERE doc=? AND page=? ORDER BY start_token",new String[]{doc,""+page});}
    public synchronized JSONArray allAnnotations(String doc)throws JSONException{return query("SELECT * FROM annotations WHERE doc=? ORDER BY page,start_token",new String[]{doc});}
    public synchronized void annotate(String doc,int page,int start,int end,String quote,String translation,String style,boolean visible){
        annotate(doc,page,start,end,quote,translation,style,visible,AnnotationStyle.DEFAULT_COLOR,"");
    }
    public synchronized void annotate(String doc,int page,int start,int end,String quote,String translation,String style,boolean visible,String color,String sentence){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            db.delete("annotations","doc=? AND page=? AND NOT(end_token<? OR start_token>?)",new String[]{doc,""+page,""+start,""+end});
            ContentValues v=new ContentValues();v.put("doc",doc);v.put("page",page);v.put("start_token",start);v.put("end_token",end);v.put("quote",quote);v.put("translation",translation);v.put("style",AnnotationStyle.style(style));v.put("visible",visible?1:0);v.put("color",AnnotationStyle.color(color));v.put("sentence",sentence==null?"":sentence);db.insertOrThrow("annotations",null,v);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized void deleteAnnotation(long id){getWritableDatabase().delete("annotations","id=?",new String[]{""+id});}
    public synchronized void visible(long id,boolean show){ContentValues v=new ContentValues();v.put("visible",show?1:0);getWritableDatabase().update("annotations",v,"id=?",new String[]{""+id});}
    public synchronized JSONObject lookup(String spelling)throws JSONException{
        if(dictionary!=null)for(String word:TextRules.candidates(spelling))try(Cursor c=dictionary.rawQuery("SELECT spelling,phonetic,part_of_speech,chinese_definition,english_definition,english_definition_zh,collocations,example,morphology,definition_source FROM words WHERE lower(spelling)=? ORDER BY priority_score DESC LIMIT 1",new String[]{word})){if(c.moveToFirst())return row(c);}
        for(String word:TextRules.candidates(spelling))try(Cursor c=general.rawQuery("SELECT word AS spelling,phonetic,translation AS chinese_definition,definition AS english_definition,'ECDICT' AS definition_source FROM dictionary WHERE word=? LIMIT 1",new String[]{word})){if(c.moveToFirst())return row(c);}
        return null;
    }
    public synchronized int annotationCount(String doc){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM annotations WHERE doc=?",new String[]{doc})){c.moveToFirst();return c.getInt(0);}}
    public synchronized boolean hasAnnotations(String doc){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM annotations"+(doc==null?"":" WHERE doc=?")+" LIMIT 1",doc==null?null:new String[]{doc})){return c.moveToFirst();}}
    public synchronized JSONArray vocabulary(String document)throws JSONException{
        String sql="SELECT a.*,d.name AS document_name FROM annotations a JOIN documents d ON d.id=a.doc";
        JSONArray rows=query(sql+(document==null?"":" WHERE a.doc=?")+" ORDER BY d.created DESC,a.page,a.start_token",document==null?null:new String[]{document});
        JSONObject cached=null;String cacheKey="";
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);
            if(r.optString("sentence").trim().isEmpty()){
                String key=r.getString("doc")+":"+r.getInt("page");
                if(!key.equals(cacheKey)){cached=cachedPage(r.getString("doc"),r.getInt("page"));cacheKey=key;}
                r.put("sentence",ReadingContext.sentence(cached,r.getInt("start_token"),r.getInt("end_token")));
            }
        }
        return rows;
    }
    public synchronized JSONObject backup()throws JSONException{
        return new JSONObject().put("format","miaomiao-reader-backup").put("version",2).put("documents",documents()).put("page_cache",query("SELECT * FROM page_cache",null)).put("annotations",query("SELECT * FROM annotations",null));
    }
    public synchronized void restore(JSONObject state)throws JSONException{
        if(!"miaomiao-reader-backup".equals(state.optString("format"))||state.optInt("version")<1||state.optInt("version")>2)throw new JSONException("备份格式不支持");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            for(String table:new String[]{"documents","page_cache","annotations"}){
                JSONArray rows=state.getJSONArray(table);if(rows.length()>200000)throw new JSONException("备份内容过多");
                for(int i=0;i<rows.length();i++){
                    JSONObject r=rows.getJSONObject(i);ContentValues v=new ContentValues();
                    for(Iterator<String> it=r.keys();it.hasNext();){String k=it.next();Object value=r.get(k);if(value instanceof Number)v.put(k,((Number)value).longValue());else v.put(k,String.valueOf(value));}
                    if(table.equals("annotations")){v.remove("id");db.delete(table,"doc=? AND page=? AND start_token=? AND end_token=?",new String[]{r.getString("doc"),r.getString("page"),r.getString("start_token"),r.getString("end_token")});}
                    db.insertWithOnConflict(table,null,v,table.equals("documents")?SQLiteDatabase.CONFLICT_IGNORE:SQLiteDatabase.CONFLICT_REPLACE);
                }
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private JSONArray query(String sql,String[] args)throws JSONException{JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery(sql,args)){while(c.moveToNext())out.put(row(c));}return out;}
    private static JSONObject row(Cursor c)throws JSONException{JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++){if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER)o.put(c.getColumnName(i),c.getLong(i));else o.put(c.getColumnName(i),c.isNull(i)?"":c.getString(i));}return o;}
    public static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    @Override public synchronized void close(){if(dictionary!=null)dictionary.close();general.close();super.close();}
}
