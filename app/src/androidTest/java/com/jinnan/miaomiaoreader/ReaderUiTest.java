package com.jinnan.miaomiaoreader;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReaderUiTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Activity activity;
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String text,String cls){
        if(root==null)return null;
        if((text==null||(root.getText()!=null&&root.getText().toString().equals(text)))&&(cls==null||cls.contentEquals(root.getClassName())))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo hit=find(root.getChild(i),text,cls);if(hit!=null)return hit;}return null;
    }
    private AccessibilityNodeInfo waitNode(String text,String cls)throws Exception{
        long until=SystemClock.uptimeMillis()+45000;while(SystemClock.uptimeMillis()<until){AccessibilityNodeInfo n=find(inst.getUiAutomation().getRootInActiveWindow(),text,cls);if(n!=null)return n;Thread.sleep(200);}throw new AssertionError("UI node missing: "+text+" / "+cls);
    }
    private WebView web(View root){if(root instanceof WebView)return (WebView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){WebView w=web(((ViewGroup)root).getChildAt(i));if(w!=null)return w;}return null;}
    private String js(String script)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();inst.runOnMainSync(()->{WebView w=web(activity.getWindow().getDecorView());if(w==null){latch.countDown();return;}w.evaluateJavascript(script,result->{value.set(result);latch.countDown();});});assertTrue(latch.await(15,TimeUnit.SECONDS));return value.get();
    }
    private void waitJs(String script,String expected)throws Exception{long until=SystemClock.uptimeMillis()+45000;while(SystemClock.uptimeMillis()<until){if(expected.equals(js(script)))return;Thread.sleep(200);}throw new AssertionError("JS state missing: "+script+" = "+js(script));}
    private void launch()throws Exception{Intent i=new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);activity=inst.startActivitySync(i);waitNode("继续阅读",null).performAction(AccessibilityNodeInfo.ACTION_CLICK);waitJs("document.querySelectorAll('[data-i]').length>10","true");}
    private void tapToken(int index)throws Exception{
        js("document.querySelector('[data-i=\""+index+"\"]').scrollIntoView();true");
        JSONObject point=new JSONObject((String)new JSONTokener(js("JSON.stringify((function(){var r=document.querySelector('[data-i=\""+index+"\"]').getBoundingClientRect();return {x:(r.left+r.right)/2,y:(r.top+r.bottom)/2};})())")).nextValue());
        int[] origin=new int[2];inst.runOnMainSync(()->web(activity.getWindow().getDecorView()).getLocationOnScreen(origin));float density=inst.getTargetContext().getResources().getDisplayMetrics().density;
        float x=origin[0]+(float)point.getDouble("x")*density,y=origin[1]+(float)point.getDouble("y")*density;long now=SystemClock.uptimeMillis();
        inst.sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0));Thread.sleep(1150);inst.sendPointerSync(MotionEvent.obtain(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0));
    }
    @Test public void editInlineChineseHideRestoreAndReopen()throws Exception{
        launch();tapToken(37);AccessibilityNodeInfo edit=waitNode(null,"android.widget.EditText");Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"雨林（复数）");assertTrue(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));waitNode("保存标注",null).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        waitJs("document.querySelector('.annotation .zh').textContent","\"雨林（复数）\"");
        waitJs("document.body.classList.contains('tools-visible')","false");js("AndroidReader.toggleToolbar();true");waitJs("document.body.classList.contains('tools-visible')","true");
        waitNode("隐藏中文",null).performAction(AccessibilityNodeInfo.ACTION_CLICK);waitJs("document.body.classList.contains('hidden-zh')","true");waitNode("显示中文",null).performAction(AccessibilityNodeInfo.ACTION_CLICK);waitJs("document.body.classList.contains('hidden-zh')","false");
        inst.runOnMainSync(()->activity.finish());launch();waitJs("document.querySelector('.annotation .zh').textContent","\"雨林（复数）\"");
        js("AndroidReader.select(37,37);true");waitNode("隐藏这条中文",null).performAction(AccessibilityNodeInfo.ACTION_CLICK);waitNode("关闭",null).performAction(AccessibilityNodeInfo.ACTION_CLICK);waitJs("document.querySelector('.annotation').classList.contains('hide')","true");
        js("AndroidReader.select(37,37);true");waitNode("恢复这条中文",null).performAction(AccessibilityNodeInfo.ACTION_CLICK);waitNode("关闭",null).performAction(AccessibilityNodeInfo.ACTION_CLICK);waitJs("document.querySelector('.annotation').classList.contains('hide')","false");
        js("AndroidReader.select(37,37);true");waitNode("下划线",null).performAction(AccessibilityNodeInfo.ACTION_CLICK);waitNode("保存标注",null).performAction(AccessibilityNodeInfo.ACTION_CLICK);waitJs("document.querySelector('.annotation').classList.contains('underline')","true");
        Bitmap screenshot=inst.getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(inst.getTargetContext().getFilesDir(),"reading-ui-test.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
    }
    @After public void finish(){if(activity!=null)inst.runOnMainSync(()->activity.finish());}
}
