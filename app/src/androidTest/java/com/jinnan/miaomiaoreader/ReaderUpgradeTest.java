package com.jinnan.miaomiaoreader;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReaderUpgradeTest {
    @Test public void versionOneUpgradeRetainsDocumentsHiddenMarksAndOldBackup() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String dbName="reader-upgrade-test.sqlite";context.deleteDatabase(dbName);
        try {
            try(SQLiteDatabase db=context.openOrCreateDatabase(dbName,0,null)) {
                db.execSQL("CREATE TABLE documents(id TEXT PRIMARY KEY,name TEXT NOT NULL,file TEXT NOT NULL,pages INTEGER NOT NULL,current_page INTEGER NOT NULL DEFAULT 0,created INTEGER NOT NULL)");
                db.execSQL("CREATE TABLE page_cache(doc TEXT,page INTEGER,data TEXT,PRIMARY KEY(doc,page))");
                db.execSQL("CREATE TABLE annotations(id INTEGER PRIMARY KEY AUTOINCREMENT,doc TEXT,page INTEGER,start_token INTEGER,end_token INTEGER,quote TEXT,translation TEXT,style TEXT,visible INTEGER)");
                db.execSQL("INSERT INTO documents VALUES('upgrade','Test','test.txt',1,0,1)");
                db.execSQL("INSERT INTO annotations VALUES(7,'upgrade',0,1,1,'policy','政策','underline',0)");
                JSONObject data=PageEngine.fromText("The policy protects forests.");
                db.execSQL("INSERT INTO page_cache VALUES('upgrade',0,?)",new Object[]{data.toString()});db.setVersion(1);
            }
            try(ReaderStore store=new ReaderStore(context,dbName)) {
                JSONObject old=store.annotations("upgrade",0).getJSONObject(0);
                assertEquals(7,old.getInt("id"));assertEquals("政策",old.getString("translation"));
                assertEquals(0,old.getInt("visible"));assertEquals("honey",old.getString("color"));
                assertEquals("The policy protects forests.",store.vocabulary("upgrade").getJSONObject(0).getString("sentence"));
                JSONObject backup=store.backup();backup.put("version",1);
                for(int i=0;i<backup.getJSONArray("annotations").length();i++) {
                    JSONObject mark=backup.getJSONArray("annotations").getJSONObject(i);mark.remove("color");mark.remove("sentence");
                }
                store.restore(backup);assertEquals(0,store.annotations("upgrade",0).getJSONObject(0).getInt("visible"));
                store.annotate("upgrade",0,1,1,"policy","保护政策","underline-wavy",false,"sage","The policy protects forests.");
                JSONObject changed=store.annotations("upgrade",0).getJSONObject(0);
                assertEquals("underline-wavy",changed.getString("style"));assertEquals("sage",changed.getString("color"));
                assertEquals(0,changed.getInt("visible"));assertEquals(1,store.vocabulary("upgrade").length());
            }
        } finally {context.deleteDatabase(dbName);}
    }
}
