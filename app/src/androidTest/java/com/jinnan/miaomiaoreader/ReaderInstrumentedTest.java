package com.jinnan.miaomiaoreader;

import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReaderInstrumentedTest {
    private Context context;
    @Before public void context(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();}
    @Test public void annotationsRemainLocalAndPersistEditsVisibilityAndStyle()throws Exception{
        ReaderStore s=new ReaderStore(context);String id="instrumentation-document";
        s.addDocument(id,"Test","test.pdf",2);
        s.annotate(id,0,2,2,"policy","策略","highlight",true);
        s.annotate(id,0,7,7,"policy","政策","underline",true);
        JSONArray marks=s.annotations(id,0);assertEquals(2,marks.length());assertEquals("策略",marks.getJSONObject(0).getString("translation"));assertEquals("政策",marks.getJSONObject(1).getString("translation"));
        s.visible(marks.getJSONObject(0).getLong("id"),false);
        assertEquals(0,s.annotations(id,0).getJSONObject(0).getInt("visible"));
        s.annotate(id,0,2,2,"policy","控制策略","underline",true);
        s.close();s=new ReaderStore(context);
        JSONObject restored=s.annotations(id,0).getJSONObject(0);assertEquals("控制策略",restored.getString("translation"));assertEquals("underline",restored.getString("style"));assertEquals(1,restored.getInt("visible"));assertEquals(0,s.annotations(id,1).length());
        JSONObject backup=s.backup();s.deleteAnnotation(restored.getLong("id"));s.restore(backup);assertEquals(2,s.annotations(id,0).length());
        assertNotNull(s.lookup("studies"));assertNotNull(s.lookup("photosynthesis"));assertNotNull(s.lookup("the"));
        s.getWritableDatabase().delete("annotations","doc=?",new String[]{id});s.getWritableDatabase().delete("documents","id=?",new String[]{id});s.close();
    }
    @Test public void bundledOcrReadsOriginalSampleWithoutPlayServices()throws Exception{
        File f=new File(context.getCacheDir(),"ocr-test.pdf");try(InputStream in=context.getAssets().open("sample_reading.pdf");OutputStream out=new FileOutputStream(f)){ReaderStore.copy(in,out);}
        try(PageEngine engine=new PageEngine()){
            JSONObject result=engine.recognize(f,2);JSONArray blocks=result.getJSONArray("blocks");int words=0;StringBuilder full=new StringBuilder();
            for(int i=0;i<blocks.length();i++){JSONArray tokens=blocks.getJSONObject(i).getJSONArray("tokens");words+=tokens.length();for(int j=0;j<tokens.length();j++)full.append(tokens.getJSONObject(j).getString("t")).append(' ');}
            assertTrue("Too few recognized words: "+words,words>100);assertTrue(full.toString().toLowerCase().contains("forest"));
        }
    }
}
