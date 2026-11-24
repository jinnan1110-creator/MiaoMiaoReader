package com.jinnan.miaomiaoreader;

import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class PageEngine implements AutoCloseable {
    private final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    public static int pageCount(File file)throws IOException{
        if(file.getName().endsWith(".txt"))return textPages(file).size();
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){return renderer.getPageCount();}
    }
    public static Bitmap render(File file,int page)throws IOException{
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd);PdfRenderer.Page p=renderer.openPage(page)){
            float factor=Math.min(1800f/p.getWidth(),3600f/p.getHeight());
            Bitmap b=Bitmap.createBitmap(Math.max(1,Math.round(p.getWidth()*factor)),Math.max(1,Math.round(p.getHeight()*factor)),Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return b;
        }
    }
    public JSONObject recognize(File file,int page)throws Exception{
        if(file.getName().endsWith(".txt"))return fromText(textPages(file).get(page));
        Bitmap bitmap=render(file,page);
        try{
            Text text=Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap,0)),90,TimeUnit.SECONDS);
            List<LayoutOrder.Block<Text.TextBlock>> boxes=new ArrayList<>();
            for(Text.TextBlock b:text.getTextBlocks()){Rect r=b.getBoundingBox();if(r!=null)boxes.add(new LayoutOrder.Block<>(r.left,r.top,r.right,r.bottom,b));}
            JSONArray blocks=new JSONArray();int index=0;
            for(LayoutOrder.Block<Text.TextBlock> box:LayoutOrder.sort(boxes,bitmap.getWidth())){
                JSONArray tokens=new JSONArray();
                for(Text.Line line:box.value.getLines())for(Text.Element e:line.getElements()){
                    Rect r=e.getBoundingBox();if(r==null||e.getText().trim().isEmpty())continue;
                    tokens.put(new JSONObject().put("i",index++).put("t",e.getText()).put("x",r.left/(double)bitmap.getWidth()).put("y",r.top/(double)bitmap.getHeight()).put("w",r.width()/(double)bitmap.getWidth()).put("h",r.height()/(double)bitmap.getHeight()));
                }
                if(tokens.length()>0)blocks.put(new JSONObject().put("tokens",tokens));
            }
            return new JSONObject().put("blocks",blocks).put("w",bitmap.getWidth()).put("h",bitmap.getHeight()).put("engine","ML Kit bundled Latin 16.0.1");
        }finally{bitmap.recycle();}
    }
    static List<String> textPages(File file)throws IOException{
        String content=new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
        List<String> pages=new ArrayList<>();int start=0;
        while(start<content.length()){
            int end=Math.min(content.length(),start+6000);
            if(end<content.length()){int newline=content.lastIndexOf('\n',end);if(newline>start+3000)end=newline+1;}
            pages.add(content.substring(start,end));start=end;
        }
        if(pages.isEmpty())pages.add("");return pages;
    }
    static JSONObject fromText(String text)throws JSONException{
        JSONArray blocks=new JSONArray();int index=0;
        for(String paragraph:text.split("\\n\\s*\\n|\\r?\\n")){
            if(paragraph.trim().isEmpty())continue;
            JSONArray tokens=new JSONArray();for(String word:paragraph.trim().split("\\s+"))tokens.put(new JSONObject().put("i",index++).put("t",word));
            blocks.put(new JSONObject().put("tokens",tokens));
        }
        return new JSONObject().put("blocks",blocks).put("w",0).put("h",0).put("engine","UTF-8 text");
    }
    @Override public void close(){recognizer.close();}
}
