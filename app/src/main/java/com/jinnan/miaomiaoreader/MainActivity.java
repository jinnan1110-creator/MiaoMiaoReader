package com.jinnan.miaomiaoreader;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public final class MainActivity extends Activity {
    private static final int IMPORT=101,EXPORT=102,RESTORE=103,VOCABULARY=104;
    private static final int INK=0xff293b33,GREEN=0xff244d40,MUTED=0xff7a8275,
            BG=0xfff7f4ec,PAPER=0xfffffdf7,BORDER=0xffdedfd2,GOLD=0xffc6ab75;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ReaderStore store; private PageEngine engine; private SharedPreferences prefs;
    private LinearLayout root,topBar,bottomBar;
    private WebView web; private PageImageView original; private FrameLayout content;
    private TextView pageLabel,status,title;
    private Button modeButton,chineseButton,previous,next;
    private JSONObject doc,pageData;
    private int page=0,generation=0,focusToken=-1,chromeGeneration=0;
    private boolean originalMode=false,destroyed=false,ready=false,toolsVisible=false,ttsReady=false;
    private File documentsDir; private ProgressDialog busy;
    private TextToSpeech tts;
    private String pendingVocabularyDocument;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("reader",MODE_PRIVATE);
        if(state!=null)pendingVocabularyDocument=state.getString("vocabulary_document");
        documentsDir=new File(getFilesDir(),"documents");documentsDir.mkdirs();
        base();
        TextView preparing=text("正在准备本地阅读与词库…",17,MUTED);
        preparing.setPadding(dp(28),dp(64),dp(28),dp(28));root.addView(preparing);
        tts=new TextToSpeech(this,result->ui(()->{
            if(tts!=null&&result==TextToSpeech.SUCCESS){
                int language=tts.setLanguage(Locale.US);
                ttsReady=language!=TextToSpeech.LANG_MISSING_DATA&&language!=TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setSpeechRate(.86f);
            }
        }));
        worker.execute(()->{try{
            store=new ReaderStore(this);engine=new PageEngine();
            ui(()->{showShelf();installSample();});
        }catch(Exception e){ui(()->error("初始化失败",e));}});
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,float size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;
    }
    private GradientDrawable surface(int color,int radius,int border){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(border!=Color.TRANSPARENT)d.setStroke(dp(1),border);return d;
    }
    private GradientDrawable round(int color,int radius){return surface(color,radius,Color.TRANSPARENT);}
    private void touchSurface(View view,int color,int radius,int border){
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22315c4a),
                surface(color,radius,border),surface(Color.WHITE,radius,Color.TRANSPARENT)));
    }
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
                new int[]{0xff9da497,GREEN}));
        b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(12),0,dp(12),0);b.setStateListAnimator(null);b.setElevation(0);
        touchSurface(b,Color.TRANSPARENT,14,Color.TRANSPARENT);
        b.setOnClickListener(v->action.run());return b;
    }
    private void primary(Button b){b.setTextColor(BG);touchSurface(b,GREEN,16,Color.TRANSPARENT);}
    private void base(){
        chromeGeneration++;topBar=null;bottomBar=null;toolsVisible=false;
        if(original!=null){original.release();original=null;}if(web!=null){web.destroy();web=null;}
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
        setContentView(root);root.requestApplyInsets();
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private void add(LinearLayout parent,View view,int width,int height){parent.addView(view,new LinearLayout.LayoutParams(width,height));}
    private void gap(LinearLayout parent,int h){add(parent,new View(this),1,dp(h));}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private AlertDialog polish(AlertDialog d){
        d.setOnShowListener(ignored->{
            if(d.getWindow()!=null)d.getWindow().setBackgroundDrawable(surface(PAPER,24,BORDER));
            if(d.getButton(AlertDialog.BUTTON_POSITIVE)!=null)d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GREEN);
            if(d.getButton(AlertDialog.BUTTON_NEGATIVE)!=null)d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
            if(d.getButton(AlertDialog.BUTTON_NEUTRAL)!=null)d.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(GREEN);
        });return d;
    }
    private void error(String heading,Exception e){
        if(destroyed)return;
        polish(new AlertDialog.Builder(this).setTitle(heading)
                .setMessage(e.getMessage()==null?"请重新打开文件后重试。":e.getMessage())
                .setPositiveButton("知道了",null).create()).show();
    }
    private void setBusy(String message){clearBusy();busy=new ProgressDialog(this);busy.setMessage(message);busy.setCancelable(false);busy.show();}
    private void clearBusy(){if(busy!=null){busy.dismiss();busy=null;}}
    private void ui(Runnable r){runOnUiThread(()->{if(!destroyed)r.run();});}
    private void installSample(){
        worker.execute(()->{try{
            if(store.documents().length()==0){
                File temp=new File(getCacheDir(),"sample.pdf");
                try(InputStream in=getAssets().open("sample_reading.pdf");OutputStream out=new FileOutputStream(temp)){ReaderStore.copy(in,out);}
                JSONObject sample=register(temp,"妙妙阅读 · 英文试读","pdf");
                JSONArray pages=new JSONArray(readAsset("sample_pages.json"));
                for(int i=0;i<pages.length();i++)store.cache(sample.getString("id"),i,pages.getJSONObject(i));
            }
            ui(()->{if(doc==null)showShelf();});
        }catch(Exception e){ui(()->error("示例材料加载失败",e));}});
    }
    private void showShelf(){
        generation++;doc=null;pageData=null;ready=false;focusToken=-1;
        if(tts!=null)tts.stop();base();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout list=column();list.setPadding(dp(24),dp(26),dp(24),dp(24));scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout brand=row();TextView wordmark=text("MIAOMIAO  /  READER",10,GREEN);wordmark.setLetterSpacing(.17f);
        brand.addView(wordmark,new LinearLayout.LayoutParams(0,dp(48),1));
        Button importButton=button("＋ 导入",this::pickImport);touchSurface(importButton,PAPER,16,BORDER);brand.addView(importButton);list.addView(brand);
        gap(list,18);TextView heading=text("妙妙阅读",34,INK);heading.setTypeface(Typeface.create("serif",Typeface.BOLD));list.addView(heading);
        TextView subtitle=text("读得慢一点，记得深一点。",14,MUTED);subtitle.setPadding(0,dp(12),0,dp(30));list.addView(subtitle);
        try{
            JSONArray docs=store.documents();
            if(docs.length()==0){TextView empty=text("正在为你准备第一本阅读材料…",16,MUTED);empty.setPadding(0,dp(20),0,dp(32));list.addView(empty);}
            for(int i=0;i<docs.length();i++){
                if(i==1){TextView label=text("我的书架",17,INK);label.setTypeface(null,Typeface.BOLD);label.setPadding(0,dp(28),0,dp(16));list.addView(label);}
                JSONObject item=docs.getJSONObject(i);boolean featured=i==0;
                LinearLayout card=column();card.setPadding(dp(22),dp(23),dp(22),dp(22));
                touchSurface(card,featured?GREEN:PAPER,22,featured?Color.TRANSPARENT:BORDER);
                TextView badge=text(featured?"继续上次的阅读":item.optString("file").endsWith(".txt")?"TEXT  /  英文阅读":"PDF  /  英文阅读",11,featured?0xffc6d7ba:MUTED);
                badge.setLetterSpacing(.08f);card.addView(badge);
                TextView name=text(item.getString("name"),featured?24:19,featured?BG:INK);name.setTypeface(Typeface.create("serif",Typeface.BOLD));
                name.setLineSpacing(dp(4),1);name.setPadding(0,dp(18),0,dp(16));card.addView(name);
                int count=store.annotationCount(item.getString("id"));
                card.addView(text("第 "+(item.optInt("current_page")+1)+" / "+item.getInt("pages")+" 页    ·    "+count+" 处标注",12,featured?0xffc4d1bc:MUTED));
                gap(card,18);
                ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
                progress.setMax(Math.max(1,item.optInt("pages")));progress.setProgress(item.optInt("current_page")+1);
                progress.setProgressTintList(ColorStateList.valueOf(featured?GOLD:0xff7a9a7a));
                progress.setProgressBackgroundTintList(ColorStateList.valueOf(featured?0xff436354:0xffe4e6d9));add(card,progress,-1,dp(3));
                gap(card,16);Button start=button(featured?"继续阅读   →":"打开阅读   →",()->openDocument(item));
                if(featured){start.setTextColor(GREEN);touchSurface(start,BG,14,Color.TRANSPARENT);}
                else touchSurface(start,0xffedf0e6,14,Color.TRANSPARENT);
                add(card,start,-1,dp(50));card.setOnClickListener(v->openDocument(item));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);list.addView(card,lp);
            }
        }catch(Exception e){error("书架读取失败",e);}
        gap(list,24);
        LinearLayout exportCard=column();exportCard.setPadding(dp(18),dp(16),dp(18),dp(12));exportCard.setBackground(surface(0xffeeefe5,18,Color.TRANSPARENT));
        exportCard.addView(text("把读过的词，留给以后的自己",15,GREEN));
        TextView hint=text("导出中文注释与原句，随时整理复习。",12,MUTED);hint.setPadding(0,dp(8),0,dp(8));exportCard.addView(hint);
        add(exportCard,button("导出全部生词  ↗",()->pickVocabulary(null)),-1,dp(48));list.addView(exportCard);
        LinearLayout footer=row();footer.setPadding(dp(8),dp(6),dp(8),dp(8));footer.setBackgroundColor(PAPER);
        footer.addView(button("导出备份",this::pickExport),new LinearLayout.LayoutParams(0,dp(48),1));
        footer.addView(button("恢复备份",this::pickRestore),new LinearLayout.LayoutParams(0,dp(48),1));
        footer.addView(button("使用说明",this::help),new LinearLayout.LayoutParams(0,dp(48),1));add(root,footer,-1,-2);
    }
    private void help(){
        polish(new AlertDialog.Builder(this).setTitle("让阅读，慢慢发生")
                .setMessage("快速轻点阅读页中央，上下工具栏会滑出；再次轻点即可收起。中央有英文单词也可轻点。\n\n长按单词约 1 秒后松手，在弹窗中修改中文注释、选择六色高亮或红色单线、双线、波浪线。长按约 1 秒后拖动，可选择短语；向左滑动下一页，向右滑动上一页。\n\n单词弹窗可播放英文单词发音，使用手机的英语语音引擎；短语不提供朗读。\n\n底部可以查看原页、隐藏或显示中文、查看我的标注。原页支持双指缩放。\n\n“我的标注”可以导出本书生词；书架可以导出全部生词。CSV 包含原文中的词语、你填写的中文、所在原句、文档及页码。相同词在不同语境分别保留。\n\n扫描文字可能有识别误差，可打开原页核对。原页的中文注释不会写入原 PDF。导出备份用于保留文档、识别缓存与标注。")
                .setPositiveButton("开始阅读",null).create()).show();
    }
    private void openDocument(JSONObject item){
        generation++;doc=item;page=item.optInt("current_page",0);originalMode=false;ready=false;pageData=null;focusToken=-1;base();
        content=new FrameLayout(this);content.setBackgroundColor(BG);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        web=new WebView(this);WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setBlockNetworkLoads(true);ws.setBuiltInZoomControls(false);
        web.setBackgroundColor(BG);web.addJavascriptInterface(new Bridge(),"AndroidReader");
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){
                if(pageData==null||!ready||doc==null||view!=web)return;
                try{web.evaluateJavascript("Reader.init("+pageData+","+store.annotations(doc.getString("id"),page)+","+options()+")",null);focusToken=-1;}
                catch(Exception e){error("阅读页加载失败",e);}
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return true;}
        });
        content.addView(web,new FrameLayout.LayoutParams(-1,-1));
        original=new PageImageView(this,this::select,this::toggleToolbar,direction->loadPage(page+direction));original.setVisibility(View.GONE);
        content.addView(original,new FrameLayout.LayoutParams(-1,-1));
        status=text("",15,MUTED);status.setGravity(Gravity.CENTER);status.setPadding(dp(28),dp(40),dp(28),dp(40));status.setBackgroundColor(BG);
        content.addView(status,new FrameLayout.LayoutParams(-1,-1));
        createToolbars(item);loadPage(page);applyReadingTheme();
        if(!prefs.getBoolean("immersion_hint_seen",false)){
            prefs.edit().putBoolean("immersion_hint_seen",true).apply();
            toast("轻点中央打开工具栏；长按 1 秒标注，左右滑动翻页");
        }
    }
    private void createToolbars(JSONObject item){
        topBar=column();topBar.setPadding(dp(10),dp(8),dp(10),dp(10));topBar.setBackground(surface(PAPER,0,Color.TRANSPARENT));topBar.setElevation(dp(8));
        LinearLayout header=row();header.addView(button("‹ 书架",this::showShelf));
        title=text(item.optString("name"),14,INK);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);title.setPadding(dp(8),0,dp(4),0);header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        header.addView(button("设置",this::settings));topBar.addView(header);
        FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);content.addView(topBar,top);
        bottomBar=column();bottomBar.setPadding(dp(10),dp(10),dp(10),dp(10));bottomBar.setBackgroundColor(PAPER);bottomBar.setElevation(dp(10));
        LinearLayout controls=row();previous=button("‹ 上一页",()->loadPage(page-1));next=button("下一页 ›",()->loadPage(page+1));
        pageLabel=text("",12,MUTED);pageLabel.setGravity(Gravity.CENTER);pageLabel.setOnClickListener(v->jump());pageLabel.setContentDescription("跳转到页码");pageLabel.setClickable(true);
        controls.addView(previous);controls.addView(pageLabel,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(next);bottomBar.addView(controls);
        View divider=new View(this);divider.setBackgroundColor(BORDER);add(bottomBar,divider,-1,dp(1));gap(bottomBar,4);
        LinearLayout tools=row();modeButton=button("查看原页",this::toggleMode);chineseButton=button("",this::toggleChinese);
        tools.addView(modeButton,new LinearLayout.LayoutParams(0,dp(52),1));
        tools.addView(chineseButton,new LinearLayout.LayoutParams(0,dp(52),1));
        tools.addView(button("我的标注",this::showMarks),new LinearLayout.LayoutParams(0,dp(52),1));bottomBar.addView(tools);
        content.addView(bottomBar,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));updateChineseLabel();
        topBar.setVisibility(View.INVISIBLE);bottomBar.setVisibility(View.INVISIBLE);
        topBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        bottomBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    private void toggleToolbar(){if(doc!=null)setToolsVisible(!toolsVisible,true);}
    private void setToolsVisible(boolean visible,boolean animate){
        toolsVisible=visible;if(topBar==null||bottomBar==null)return;
        int ticket=++chromeGeneration;
        slideBar(topBar,true,visible,animate,ticket);slideBar(bottomBar,false,visible,animate,ticket);
        configureWeb();
    }
    private void slideBar(View bar,boolean upper,boolean show,boolean animate,int ticket){
        bar.animate().cancel();float outside=(upper?-1:1)*Math.max(bar.getHeight(),dp(upper?76:140));
        bar.setImportantForAccessibility(show?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if(show){
            bar.setVisibility(View.VISIBLE);
            if(!animate){bar.setTranslationY(0);bar.setAlpha(1);return;}
            bar.setTranslationY(outside);bar.setAlpha(0);
            bar.animate().translationY(0).alpha(1).setDuration(220).setInterpolator(new DecelerateInterpolator()).withEndAction(null).start();
        }else if(animate&&bar.getVisibility()==View.VISIBLE){
            bar.animate().translationY(outside).alpha(0).setDuration(200).withEndAction(()->{if(ticket==chromeGeneration&&!toolsVisible)bar.setVisibility(View.INVISIBLE);}).start();
        }else{bar.setTranslationY(outside);bar.setAlpha(0);bar.setVisibility(View.INVISIBLE);}
    }
    private JSONObject options()throws JSONException{
        return new JSONObject().put("docId",doc.getString("id")).put("page",page)
                .put("font",prefs.getInt("font",20)).put("night",prefs.getBoolean("night",false))
                .put("showChinese",prefs.getBoolean("showChinese",true)).put("toolsVisible",toolsVisible)
                .put("caption","MIAOMIAO   /   "+String.format(Locale.US,"%02d",page+1)+"  ·  "+doc.optInt("pages")+" PAGES")
                .put("focusToken",focusToken).put("scroll",prefs.getInt("scroll_"+doc.getString("id")+"_"+page,0));
    }
    private File currentFile(){return new File(documentsDir,doc.optString("file"));}
    private void loadPage(int target){
        if(doc==null||target<0||target>=doc.optInt("pages"))return;
        setToolsVisible(false,true);page=target;ready=false;pageData=null;
        int ticket=++generation;String id=doc.optString("id");File file=currentFile();
        previous.setEnabled(page>0);next.setEnabled(page<doc.optInt("pages")-1);
        pageLabel.setText((page+1)+" / "+doc.optInt("pages")+"  ·  跳页");
        status.setText("正在翻开这一页…\n\n扫描页首次识别可能需要几秒钟。");status.setVisibility(View.VISIBLE);
        store.progress(id,page);try{doc.put("current_page",page);}catch(JSONException ignored){}
        worker.execute(()->{try{
            JSONObject data=store.cachedPage(id,target);
            if(data==null){data=engine.recognize(file,target);store.cache(id,target,data);}
            JSONObject result=data;
            ui(()->{
                if(ticket!=generation||doc==null||!id.equals(doc.optString("id")))return;
                pageData=result;ready=true;status.setVisibility(View.GONE);web.loadUrl("file:///android_asset/reader.html");
                if(originalMode)renderOriginal();else{web.setVisibility(View.VISIBLE);original.setVisibility(View.GONE);}
            });
        }catch(Exception e){ui(()->{
            if(ticket!=generation||doc==null)return;
            status.setText("这一页暂时无法识别。\n轻点中央空白处打开工具栏，可以翻页或查看原页。\n\n"+e.getMessage());
            status.setOnClickListener(v->toggleToolbar());error("页面读取失败",e);
        });}});
    }
    private void jump(){
        if(doc==null)return;int count=doc.optInt("pages");EditText input=new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setHint("1–"+count);input.setPadding(dp(24),dp(12),dp(24),dp(12));
        AlertDialog d=polish(new AlertDialog.Builder(this).setTitle("跳转到页码").setView(input).setPositiveButton("跳转",null).setNegativeButton("取消",null).create());
        d.show();d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{int p=Integer.parseInt(input.getText().toString())-1;if(p<0||p>=count)input.setError("请输入 1–"+count);else{focusToken=-1;loadPage(p);d.dismiss();}}
            catch(NumberFormatException e){input.setError("请输入页码");}
        });
    }
    private void toggleMode(){
        if(doc==null)return;
        if(currentFile().getName().endsWith(".txt")){toast("TXT 已按手机屏幕排版");return;}
        originalMode=!originalMode;modeButton.setText(originalMode?"返回阅读":"查看原页");
        if(originalMode)renderOriginal();else{original.setVisibility(View.GONE);web.setVisibility(View.VISIBLE);status.setVisibility(View.GONE);}
    }
    private void renderOriginal(){
        int ticket=generation;File file=currentFile();int p=page;
        status.setText("正在打开原始页面…");status.setVisibility(View.VISIBLE);
        worker.execute(()->{try{
            Bitmap bitmap=PageEngine.render(file,p);
            ui(()->{
                if(ticket!=generation||doc==null||!originalMode){bitmap.recycle();if(ticket==generation)status.setVisibility(View.GONE);return;}
                try{original.setPage(bitmap,pageData,store.annotations(doc.getString("id"),page),prefs.getBoolean("showChinese",true));
                    web.setVisibility(View.GONE);original.setVisibility(View.VISIBLE);status.setVisibility(View.GONE);}
                catch(Exception e){bitmap.recycle();status.setVisibility(View.GONE);error("原页显示失败",e);}
            });
        }catch(Exception e){ui(()->{if(ticket==generation){status.setVisibility(View.GONE);error("原页打开失败",e);}});}});
    }
    private void toggleChinese(){
        prefs.edit().putBoolean("showChinese",!prefs.getBoolean("showChinese",true)).apply();updateChineseLabel();refreshMarks();
    }
    private void updateChineseLabel(){if(chineseButton!=null)chineseButton.setText(prefs.getBoolean("showChinese",true)?"隐藏中文":"显示中文");}
    private void settings(){
        if(doc==null)return;
        polish(new AlertDialog.Builder(this).setTitle("阅读设置").setItems(
                new String[]{prefs.getBoolean("night",false)?"切换日间阅读":"切换夜间阅读","导出本书生词","使用说明"},
                (d,which)->{if(which==0){prefs.edit().putBoolean("night",!prefs.getBoolean("night",false)).apply();applyReadingTheme();configureWeb();}
                    else if(which==1)pickVocabulary(doc.optString("id"));else help();}).setNegativeButton("关闭",null).create()).show();
    }
    private void applyReadingTheme(){
        if(content==null||doc==null)return;boolean night=prefs.getBoolean("night",false);
        int color=night?0xff182b23:BG;root.setBackgroundColor(color);content.setBackgroundColor(color);web.setBackgroundColor(color);
        getWindow().setStatusBarColor(color);getWindow().setNavigationBarColor(color);
        getWindow().getDecorView().setSystemUiVisibility(night?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        status.setBackgroundColor(color);status.setTextColor(night?0xffc4d1bc:MUTED);
    }
    private void configureWeb(){
        if(web!=null&&ready&&doc!=null)try{web.evaluateJavascript("Reader.configure("+options()+")",null);}catch(JSONException ignored){}
    }
    private void refreshMarks(){
        if(!ready||doc==null)return;
        try{JSONArray marks=store.annotations(doc.getString("id"),page);
            web.evaluateJavascript("Reader.updateAnnotations("+marks+")",null);configureWeb();
            original.setAnnotations(marks,prefs.getBoolean("showChinese",true));
        }catch(Exception e){error("标注刷新失败",e);}
    }
    private void showMarks(){
        if(doc==null)return;final String document=doc.optString("id");
        try{
            JSONArray marks=store.allAnnotations(document);LinearLayout list=column();list.setPadding(dp(22),dp(8),dp(22),dp(12));
            if(marks.length()==0){TextView empty=text("这里会收好你在本书留下的词语。\n点选正文中的单词，添加第一处标注。",15,MUTED);empty.setPadding(0,dp(20),0,dp(24));list.addView(empty);}
            AlertDialog[] holder=new AlertDialog[1];
            for(int i=0;i<marks.length();i++){
                JSONObject a=marks.getJSONObject(i);
                LinearLayout card=column();card.setPadding(dp(14),dp(14),dp(14),dp(14));touchSurface(card,0xfff0f1e8,14,Color.TRANSPARENT);
                TextView quote=text(a.getString("quote"),19,GREEN);quote.setTypeface(Typeface.create("serif",Typeface.BOLD));card.addView(quote);
                TextView zh=text(a.getString("translation"),14,INK);zh.setPadding(0,dp(6),0,dp(6));card.addView(zh);
                card.addView(text("第 "+(a.getInt("page")+1)+" 页  ·  "+AnnotationStyle.label(a.optString("style"))+(a.optInt("visible",1)==0?"  ·  中文已隐藏":""),11,MUTED));
                card.setOnClickListener(v->{holder[0].dismiss();focusToken=a.optInt("start_token");loadPage(a.optInt("page"));});
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);list.addView(card,lp);
            }
            ScrollView scroll=new ScrollView(this);scroll.addView(list);
            holder[0]=polish(new AlertDialog.Builder(this).setTitle("我的标注 · "+marks.length()).setView(scroll)
                    .setPositiveButton("导出生词",(d,w)->pickVocabulary(document)).setNegativeButton("关闭",null).create());
            holder[0].show();
        }catch(Exception e){error("标注读取失败",e);}
    }
    public final class Bridge {
        @JavascriptInterface public void turnPage(int direction){if(direction==1||direction==-1)ui(()->loadPage(page+direction));}
        @JavascriptInterface public void select(int start,int end){ui(()->MainActivity.this.select(start,end));}
        @JavascriptInterface public void toggleToolbar(){ui(()->MainActivity.this.toggleToolbar());}
        @JavascriptInterface public void saveScroll(String document,int atPage,int y){
            JSONObject current=doc;
            if(current!=null&&document.equals(current.optString("id"))&&page==atPage)
                prefs.edit().putInt("scroll_"+document+"_"+atPage,Math.max(0,y)).apply();
        }
    }
    private void select(int start,int end){
        if(!ready||pageData==null||doc==null)return;
        if(start<0||end<start||end-start>=40){toast("请一次选择不超过 40 个词");return;}
        try{
            JSONArray marks=store.annotations(doc.getString("id"),page);JSONObject existing=null;
            // Original-page hit testing reports a token, including a token inside a saved phrase.
            for(int i=0;i<marks.length();i++){
                JSONObject m=marks.getJSONObject(i);
                if(start>=m.getInt("start")&&end<=m.getInt("end")){existing=m;start=m.getInt("start");end=m.getInt("end");break;}
            }
            Map<Integer,String> tokens=new TreeMap<>();JSONArray blocks=pageData.getJSONArray("blocks");
            for(int b=0;b<blocks.length();b++){
                JSONArray ts=blocks.getJSONObject(b).getJSONArray("tokens");
                for(int i=0;i<ts.length();i++){JSONObject t=ts.getJSONObject(i);int index=t.getInt("i");if(index>=start&&index<=end)tokens.put(index,t.getString("t"));}
            }
            if(tokens.size()!=end-start+1)return;
            String quote=android.text.TextUtils.join(" ",tokens.values());JSONObject entry=store.lookup(quote);
            setToolsVisible(false,true);showWord(start,end,quote,entry,existing);
        }catch(Exception e){error("查词失败",e);}
    }
    private void speak(String word){
        if(!VocabularyText.isSingleWord(word)){toast("发音仅支持单个英文单词");return;}
        if(tts==null||!ttsReady){
            polish(new AlertDialog.Builder(this).setTitle("英语语音尚未就绪")
                    .setMessage("请在系统的文字转语音设置中，安装或启用英语语音，然后重新打开妙妙阅读。")
                    .setPositiveButton("打开语音设置",(d,w)->{
                        try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}
                        catch(ActivityNotFoundException e){toast("请前往系统设置，搜索“文字转语音”");}
                    }).setNegativeButton("稍后",null).create()).show();return;
        }
        int result=tts.speak(TextRules.normalize(word),TextToSpeech.QUEUE_FLUSH,null,"reader-word");
        if(result==TextToSpeech.ERROR)toast("暂时无法发音，请检查系统英语语音");
    }
    private void showWord(int start,int end,String quote,JSONObject entry,JSONObject existing)throws JSONException{
        final String id=doc.getString("id");final int atPage=page;
        final String sentence=ReadingContext.sentence(pageData,start,end);
        final boolean[] visible={existing==null||existing.optInt("visible",1)==1};
        final String[] selectedStyle={AnnotationStyle.style(existing==null?prefs.getString("mark_style","highlight"):existing.optString("style"))};
        final String[] selectedColor={AnnotationStyle.color(existing==null?prefs.getString("mark_color",AnnotationStyle.DEFAULT_COLOR):existing.optString("color"))};
        AlertDialog[] holder=new AlertDialog[1];
        LinearLayout body=column();body.setPadding(dp(22),dp(8),dp(22),dp(20));body.setFocusableInTouchMode(true);body.requestFocus();
        LinearLayout wordHeader=row();TextView word=text(quote,27,INK);word.setTypeface(Typeface.create("serif",Typeface.BOLD));
        word.setPadding(0,0,dp(8),0);wordHeader.addView(word,new LinearLayout.LayoutParams(0,-2,1));
        if(VocabularyText.isSingleWord(quote))wordHeader.addView(button("发音",()->speak(quote)));
        body.addView(wordHeader);
        if(entry!=null){String phonetic=entry.optString("phonetic");TextView lemma=text(entry.optString("spelling")+(phonetic.isEmpty()?"":"   /"+phonetic+"/"),12,MUTED);lemma.setPadding(0,dp(8),0,dp(8));body.addView(lemma);}
        TextView label=text("留在原文的中文",12,GREEN);label.setPadding(0,dp(18),0,dp(10));body.addView(label);
        EditText translation=new EditText(this);translation.setTextSize(16);translation.setTextColor(INK);translation.setHintTextColor(MUTED);
        translation.setPadding(dp(14),dp(12),dp(14),dp(12));translation.setBackground(surface(BG,14,BORDER));
        translation.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);translation.setMaxLines(4);
        translation.setHint("写下符合这句话的中文意思");
        translation.setText(existing!=null?existing.optString("translation"):entry!=null?TextRules.compactGloss(entry.optString("chinese_definition")):"");
        add(body,translation,-1,-2);
        TextView styleLabel=text("标注样式",12,GREEN);styleLabel.setPadding(0,dp(22),0,dp(12));body.addView(styleLabel);
        RadioGroup types=new RadioGroup(this);types.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton highlight=new RadioButton(this);highlight.setId(View.generateViewId());highlight.setText("高亮");highlight.setTextSize(14);highlight.setTextColor(INK);
        RadioButton underline=new RadioButton(this);underline.setId(View.generateViewId());underline.setText("下划线");underline.setTextSize(14);underline.setTextColor(INK);
        types.addView(highlight,new RadioGroup.LayoutParams(0,dp(48),1));types.addView(underline,new RadioGroup.LayoutParams(0,dp(48),1));body.addView(types);
        LinearLayout colors=row();Button[] colorButtons=new Button[AnnotationStyle.COLORS.length];
        Runnable refreshColors=()->{
            for(int i=0;i<colorButtons.length;i++){
                Button b=colorButtons[i];if(b==null)continue;boolean active=AnnotationStyle.COLORS[i].equals(selectedColor[0]);
                b.setText(active?"✓":"");b.setSelected(active);b.setTextColor(GREEN);b.setTextSize(20);
                b.setBackground(surface(AnnotationStyle.COLOR_VALUES[i],22,active?GREEN:Color.TRANSPARENT));
                b.setContentDescription(AnnotationStyle.COLOR_LABELS[i]+(active?"，已选择":""));
            }
        };
        for(int i=0;i<colorButtons.length;i++){
            final int choice=i;Button b=button("",()->{selectedColor[0]=AnnotationStyle.COLORS[choice];refreshColors.run();});
            b.setPadding(0,0,0,0);colorButtons[i]=b;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);
            lp.setMargins(dp(2),dp(6),dp(2),dp(6));colors.addView(b,lp);
        }
        body.addView(colors);
        RadioGroup lines=new RadioGroup(this);lines.setOrientation(LinearLayout.HORIZONTAL);
        String[] lineIds={"underline","underline-double","underline-wavy"},lineLabels={"单线","双线","波浪线"};
        for(int i=0;i<lineIds.length;i++){
            RadioButton b=new RadioButton(this);b.setId(View.generateViewId());b.setTag(lineIds[i]);b.setText(lineLabels[i]);b.setTextSize(12);b.setTextColor(AnnotationStyle.RED);b.setPadding(dp(2),0,0,0);
            lines.addView(b,new RadioGroup.LayoutParams(0,dp(48),1));
            if(lineIds[i].equals(selectedStyle[0]))lines.check(b.getId());
        }
        if(lines.getCheckedRadioButtonId()==-1)lines.check(lines.getChildAt(0).getId());
        body.addView(lines);
        TextView lineHint=text("下划线始终使用红色",11,MUTED);lineHint.setPadding(dp(4),0,0,dp(8));body.addView(lineHint);
        Runnable switchType=()->{
            boolean isHighlight=types.getCheckedRadioButtonId()==highlight.getId();
            colors.setVisibility(isHighlight?View.VISIBLE:View.GONE);lines.setVisibility(isHighlight?View.GONE:View.VISIBLE);
            lineHint.setVisibility(isHighlight?View.GONE:View.VISIBLE);
            selectedStyle[0]=isHighlight?"highlight":String.valueOf(lines.findViewById(lines.getCheckedRadioButtonId()).getTag());
        };
        types.check("highlight".equals(selectedStyle[0])?highlight.getId():underline.getId());
        types.setOnCheckedChangeListener((g,checked)->switchType.run());
        lines.setOnCheckedChangeListener((g,checked)->{if(checked!=-1)selectedStyle[0]=String.valueOf(g.findViewById(checked).getTag());});
        refreshColors.run();switchType.run();
        if(!sentence.isEmpty())section(body,"所在原句",sentence);
        if(existing!=null){
            gap(body,12);LinearLayout actions=row();long markId=existing.optLong("id");
            Button visibility=button(visible[0]?"隐藏这条中文":"恢复这条中文",()->{});
            visibility.setOnClickListener(v->{visible[0]=!visible[0];store.visible(markId,visible[0]);visibility.setText(visible[0]?"隐藏这条中文":"恢复这条中文");refreshMarks();});
            actions.addView(visibility,new LinearLayout.LayoutParams(0,dp(48),1));
            Button delete=button("删除标注",()->polish(new AlertDialog.Builder(this).setTitle("删除这处标注？").setMessage("这处中文注释也会一并删除。")
                    .setPositiveButton("删除",(d,w)->{store.deleteAnnotation(markId);refreshMarks();holder[0].dismiss();}).setNegativeButton("保留",null).create()).show());
            delete.setTextColor(AnnotationStyle.RED);actions.addView(delete,new LinearLayout.LayoutParams(0,dp(48),1));body.addView(actions);
        }
        if(entry==null){TextView absent=text("词库暂未收录，可手动填写中文。",13,MUTED);absent.setPadding(0,dp(14),0,dp(6));body.addView(absent);}
        else{
            section(body,"词典中文释义",entry.optString("chinese_definition"));
            LinearLayout details=column();details.setVisibility(View.GONE);
            Button expand=button("展开完整词条  ↓",()->{});expand.setOnClickListener(v->{boolean show=details.getVisibility()!=View.VISIBLE;details.setVisibility(show?View.VISIBLE:View.GONE);expand.setText(show?"收起完整词条  ↑":"展开完整词条  ↓");});add(body,expand,-1,dp(48));
            section(details,"英英释义",entry.optString("english_definition"));section(details,"英英释义的中文解释",entry.optString("english_definition_zh"));
            section(details,"搭配",entry.optString("collocations"));section(details,"例句",entry.optString("example"));
            String morphology=entry.optString("morphology");
            if(!morphology.isEmpty())try{JSONObject m=new JSONObject(morphology);morphology=m.optString("form")+"\n"+m.optString("explanation");}catch(JSONException ignored){}
            section(details,"词根词缀",morphology);body.addView(details);
        }
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(scroll).setPositiveButton("保存标注",null).setNegativeButton("关闭",null).create();
        holder[0]=dialog;
        dialog.setOnShowListener(d->{
            dialog.getWindow().setBackgroundDrawable(surface(PAPER,26,BORDER));
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
            Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);save.setTextColor(GREEN);
            save.setOnClickListener(v->{
                String zh=translation.getText().toString().trim();
                if(zh.isEmpty()){translation.setError("请先填写中文意思");return;}
                if(zh.length()>300){translation.setError("行间中文请控制在 300 字以内");return;}
                try{
                    store.annotate(id,atPage,start,end,quote,zh,selectedStyle[0],visible[0],selectedColor[0],sentence);
                    prefs.edit().putString("mark_style",selectedStyle[0]).putString("mark_color",selectedColor[0]).apply();
                    refreshMarks();dialog.dismiss();
                }catch(Exception e){error("标注未能保存",e);}
            });
        });
        dialog.setOnDismissListener(d->{if(tts!=null)tts.stop();});dialog.show();
    }
    private void section(LinearLayout parent,String heading,String value){
        if(value.trim().isEmpty())return;TextView h=text(heading,11,GREEN);h.setPadding(0,dp(18),0,dp(8));parent.addView(h);
        TextView v=text(value,"所在原句".equals(heading)?14:15,INK);v.setTextIsSelectable(true);v.setLineSpacing(dp(4),1);parent.addView(v);
    }
    private void pickVocabulary(String document){
        try{
            if(!store.hasAnnotations(document)){
                toast("还没有生词，先在阅读中添加标注");return;
            }
            pendingVocabularyDocument=document;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,document==null?"妙妙阅读-全部生词.csv":"妙妙阅读-本书生词.csv");startActivityForResult(i,VOCABULARY);
        }catch(Exception e){error("无法创建生词文件",e);}
    }
    private void exportVocabulary(Uri uri){
        final String document=pendingVocabularyDocument;setBusy("正在整理生词与原句…");
        worker.execute(()->{try{
            JSONArray rows=store.vocabulary(document);
            try(OutputStream bytes=getContentResolver().openOutputStream(uri,"wt")){
                if(bytes==null)throw new IOException("无法写入选中的文件");bytes.write(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf});
                try(Writer out=new BufferedWriter(new OutputStreamWriter(bytes,StandardCharsets.UTF_8))){
                    out.write(VocabularyText.csvRow("单词或短语","中文注释","所在原句","文档","页码","标注样式","高亮颜色"));
                    for(int index=0;index<rows.length();index++){
                        JSONObject r=rows.getJSONObject(index);String style=r.optString("style");
                        out.write(VocabularyText.csvRow(r.optString("quote"),r.optString("translation"),r.optString("sentence"),
                                r.optString("document_name"),String.valueOf(r.optInt("page")+1),AnnotationStyle.label(style),
                                "highlight".equals(style)?AnnotationStyle.colorLabel(r.optString("color")):""));
                    }
                }
            }
            ui(()->{clearBusy();toast("已导出 "+rows.length()+" 条生词，包含中文与原句");});
        }catch(Exception e){ui(()->{clearBusy();error("生词导出失败",e);});}});
    }
    private void pickImport(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","text/plain"});startActivityForResult(i,IMPORT);}
    private void pickExport(){try{if(store.documents().length()==0){toast("请先添加阅读材料");return;}}catch(Exception ignored){}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"妙妙阅读备份.zip");startActivityForResult(i,EXPORT);}
    private void pickRestore(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,RESTORE);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(request==IMPORT)importDocument(uri);else if(request==EXPORT)exportBackup(uri);else if(request==RESTORE)restoreBackup(uri);else if(request==VOCABULARY)exportVocabulary(uri);}
    private String fileName(Uri uri){String name="阅读材料";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}return name;}
    private void importDocument(Uri uri){String name=fileName(uri);String lower=name.toLowerCase(Locale.US);String ext=lower.endsWith(".pdf")?"pdf":lower.endsWith(".txt")?"txt":"";if(ext.isEmpty()){toast("支持导入 PDF 和 UTF-8 TXT");return;}setBusy("正在导入 "+name+"…");worker.execute(()->{try{File temp=File.createTempFile("reader-", "."+ext,getCacheDir());try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){ReaderStore.copy(in,out);}JSONObject item=register(temp,name,ext);ui(()->{clearBusy();openDocument(item);});}catch(Exception e){ui(()->{clearBusy();error("导入失败",e);});}});}
    private JSONObject register(File temp,String name,String ext)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(temp)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.US,"%02x",b));String id=hex.toString();JSONObject existing=store.document(id);if(existing!=null){temp.delete();return existing;}
        int count=PageEngine.pageCount(temp);String file=id+"."+ext;File target=new File(documentsDir,file);if(!temp.renameTo(target)){try(InputStream in=new FileInputStream(temp);OutputStream out=new FileOutputStream(target)){ReaderStore.copy(in,out);}temp.delete();}store.addDocument(id,name,file,count);return store.document(id);
    }
    private void exportBackup(Uri uri){setBusy("正在备份文档和标注…");worker.execute(()->{try{
        JSONObject state=store.backup();try(ZipOutputStream zip=new ZipOutputStream(getContentResolver().openOutputStream(uri))){zip.putNextEntry(new ZipEntry("state.json"));zip.write(state.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();JSONArray docs=state.getJSONArray("documents");for(int i=0;i<docs.length();i++){String file=docs.getJSONObject(i).getString("file");zip.putNextEntry(new ZipEntry("documents/"+file));try(InputStream in=new FileInputStream(new File(documentsDir,file))){ReaderStore.copy(in,zip);}zip.closeEntry();}}ui(()->{clearBusy();toast("备份已导出，包含文档和标注");});
    }catch(Exception e){ui(()->{clearBusy();error("备份失败",e);});}});}
    private void restoreBackup(Uri uri){new AlertDialog.Builder(this).setTitle("恢复阅读备份").setMessage("将合并备份中的文档和标注；同一位置的标注以备份为准。").setPositiveButton("恢复",(d,w)->doRestore(uri)).setNegativeButton("取消",null).show();}
    private void doRestore(Uri uri){setBusy("正在恢复阅读备份…");worker.execute(()->{File stage=new File(getCacheDir(),"restore-"+System.nanoTime());stage.mkdirs();try{
        JSONObject state=null;long total=0;int count=0;
        try(ZipInputStream zip=new ZipInputStream(getContentResolver().openInputStream(uri))){ZipEntry e;byte[] b=new byte[65536];while((e=zip.getNextEntry())!=null){String name=e.getName();if(++count>3000)throw new IOException("备份文件数量过多");boolean isState=name.equals("state.json");boolean isDoc=name.matches("documents/[a-f0-9]{64}\\.(pdf|txt)");if(!isState&&!isDoc)throw new IOException("备份中含有不支持的文件");File dest=new File(stage,isState?"state.json":name.substring("documents/".length()));try(OutputStream out=new FileOutputStream(dest)){int n;while((n=zip.read(b))!=-1){total+=n;if(total>1500L*1024*1024)throw new IOException("备份超过 1.5 GB");out.write(b,0,n);}}zip.closeEntry();}}
        File stateFile=new File(stage,"state.json");if(!stateFile.exists()||stateFile.length()>100*1024*1024)throw new IOException("备份内容不完整");state=new JSONObject(new String(java.nio.file.Files.readAllBytes(stateFile.toPath()),StandardCharsets.UTF_8));if(!"miaomiao-reader-backup".equals(state.optString("format")))throw new IOException("这不是妙妙阅读备份");
        JSONArray docs=state.getJSONArray("documents");for(int i=0;i<docs.length();i++){JSONObject item=docs.getJSONObject(i);String f=item.getString("file");if(!f.matches("[a-f0-9]{64}\\.(pdf|txt)")||!item.getString("id").equals(f.substring(0,64)))throw new IOException("文档标识无效");if(!new File(stage,f).isFile())throw new IOException("备份缺少文档");}
        for(int i=0;i<docs.length();i++){String f=docs.getJSONObject(i).getString("file");File src=new File(stage,f),dest=new File(documentsDir,f);if(!dest.exists()&&!src.renameTo(dest))throw new IOException("恢复文档失败");}store.restore(state);ui(()->{clearBusy();showShelf();toast("备份已恢复");});
    }catch(Exception e){ui(()->{clearBusy();error("恢复失败",e);});}finally{File[] files=stage.listFiles();if(files!=null)for(File f:files)f.delete();stage.delete();}});}
    private String readAsset(String name)throws IOException{try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){ReaderStore.copy(in,out);return out.toString("UTF-8");}}

    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putString("vocabulary_document",pendingVocabularyDocument);
    }
    @Override protected void onPause(){if(tts!=null)tts.stop();super.onPause();}
    @Override public void onBackPressed(){
        if(doc!=null&&toolsVisible)setToolsVisible(false,true);
        else if(doc!=null)showShelf();else super.onBackPressed();
    }
    @Override public void onDestroy(){
        destroyed=true;generation++;chromeGeneration++;clearBusy();
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        if(web!=null){web.destroy();web=null;}if(original!=null)original.release();
        worker.shutdownNow();if(engine!=null)engine.close();super.onDestroy();
    }
}
