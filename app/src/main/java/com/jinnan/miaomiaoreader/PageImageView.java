package com.jinnan.miaomiaoreader;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.util.*;

public final class PageImageView extends View {
    public interface Selection {void select(int start,int end);}
    public interface Turning {void turn(int direction);}
    private Bitmap bitmap; private JSONObject page;
    private JSONArray annotations=new JSONArray();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final Selection selection; private final Runnable blankTap;private final Turning turning;
    private final float density;
    private float zoom=1,offsetX=0,offsetY=0,lastX,lastY,downX,downY;
    private boolean dragged,showChinese=true,multiTouch,longHeld;
    private long downTime;private RectF pressedRect;
    private final Runnable longPress=this::armLongPress;
    private void armLongPress(){
        if(!dragged&&!multiTouch&&!scaleDetector.isInProgress()){
            JSONObject token=findToken(downX,downY);
            if(token!=null){longHeld=true;pressedRect=rect(token);performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);invalidate();}
        }
    }

    public PageImageView(Context c,Selection callback){this(c,callback,()->{},direction->{});}
    public PageImageView(Context c,Selection callback,Runnable blank){this(c,callback,blank,direction->{});}
    public PageImageView(Context c,Selection callback,Runnable blank,Turning turn){
        super(c);selection=callback;blankTap=blank;turning=turn;density=c.getResources().getDisplayMetrics().density;
        setContentDescription("原始 PDF 页面，可双指缩放；长按单词 1 秒后标注，轻点中央打开工具栏，左右滑动翻页");
        scaleDetector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){
                float old=zoom;zoom=Math.max(1,Math.min(5,zoom*d.getScaleFactor()));float f=zoom/old;
                offsetX=d.getFocusX()-(d.getFocusX()-offsetX)*f;offsetY=d.getFocusY()-(d.getFocusY()-offsetY)*f;
                dragged=true;invalidate();return true;
            }
        });
    }
    public void setPage(Bitmap b,JSONObject data,JSONArray marks,boolean show){
        cancelPress();
        if(bitmap!=null&&bitmap!=b)bitmap.recycle();bitmap=b;page=data;annotations=marks;showChinese=show;
        zoom=1;offsetX=offsetY=0;invalidate();
    }
    public void setAnnotations(JSONArray data,boolean show){annotations=data;showChinese=show;invalidate();}
    private float fit(){return bitmap==null||bitmap.getWidth()==0?1:getWidth()/(float)bitmap.getWidth();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(0xffe7e8dd);if(bitmap==null||bitmap.isRecycled()||getWidth()==0)return;
        float scale=fit()*zoom;canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawBitmap(bitmap,0,0,paint);
        try{
            if(page==null){canvas.restore();return;}
            Map<Integer,JSONObject> tokens=new HashMap<>();JSONArray blocks=page.getJSONArray("blocks");
            for(int i=0;i<blocks.length();i++){
                JSONArray ts=blocks.getJSONObject(i).getJSONArray("tokens");
                for(int j=0;j<ts.length();j++){JSONObject t=ts.getJSONObject(j);tokens.put(t.getInt("i"),t);}
            }
            for(int i=0;i<annotations.length();i++){
                JSONObject a=annotations.getJSONObject(i);JSONObject first=tokens.get(a.getInt("start"));
                String style=AnnotationStyle.style(a.optString("style"));
                for(int j=a.getInt("start");j<=a.getInt("end");j++){
                    JSONObject t=tokens.get(j);if(t==null||!t.has("x"))continue;RectF r=rect(t);
                    if("highlight".equals(style)){
                        paint.setStyle(Paint.Style.FILL);paint.setColor((AnnotationStyle.colorValue(a.optString("color"))&0x00ffffff)|0x80000000);
                        canvas.drawRoundRect(r,2,2,paint);
                    }else drawUnderline(canvas,r,scale,style);
                }
                if(showChinese&&a.optInt("visible",1)==1&&first!=null&&first.has("x")){
                    RectF r=rect(first);paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
                    paint.setTextSize(Math.max(18,r.height()*.65f));String s=a.optString("translation");
                    float width=Math.min(bitmap.getWidth()-r.left-8,paint.measureText(s)+10);
                    paint.setColor(0xfaf7f4ec);canvas.drawRoundRect(r.left,r.bottom+5,r.left+width,r.bottom+paint.getTextSize()+14,3,3,paint);
                    paint.setColor(0xff315c4a);canvas.save();
                    canvas.clipRect(r.left,r.bottom+5,r.left+width,r.bottom+paint.getTextSize()+14);
                    canvas.drawText(s,r.left+3,r.bottom+paint.getTextSize()+7,paint);canvas.restore();
                }
            }
        }catch(JSONException ignored){}
        if(pressedRect!=null){paint.setColor(0x887d9d83);paint.setStyle(Paint.Style.FILL);canvas.drawRect(pressedRect,paint);}
        paint.setStyle(Paint.Style.FILL);canvas.restore();
    }
    private void drawUnderline(Canvas canvas,RectF r,float scale,String style){
        float unit=density/scale,y=r.bottom+3*unit;paint.setColor(AnnotationStyle.RED);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.25f*unit);
        if("underline-wavy".equals(style)){
            Path wave=new Path();wave.moveTo(r.left,y);float step=5*unit,amplitude=1.4f*unit;
            boolean up=true;
            for(float x=r.left;x<r.right;){
                float end=Math.min(x+step,r.right);wave.quadTo((x+end)/2,y+(up?-1:1)*amplitude*2,end,y);
                x=end;up=!up;
            }
            canvas.drawPath(wave,paint);
        }else{
            canvas.drawLine(r.left,y,r.right,y,paint);
            if("underline-double".equals(style))canvas.drawLine(r.left,y+3*unit,r.right,y+3*unit,paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private RectF rect(JSONObject t){
        return new RectF((float)t.optDouble("x")*bitmap.getWidth(),(float)t.optDouble("y")*bitmap.getHeight(),
                (float)(t.optDouble("x")+t.optDouble("w"))*bitmap.getWidth(),
                (float)(t.optDouble("y")+t.optDouble("h"))*bitmap.getHeight());
    }
    private void cancelPress(){removeCallbacks(longPress);longHeld=false;pressedRect=null;invalidate();}
    @Override public boolean onTouchEvent(MotionEvent e){
        scaleDetector.onTouchEvent(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                cancelPress();lastX=downX=e.getX();lastY=downY=e.getY();downTime=e.getEventTime();
                dragged=false;multiTouch=false;postDelayed(longPress,1000);return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiTouch=true;dragged=true;cancelPress();return true;
            case MotionEvent.ACTION_MOVE:
                if(!scaleDetector.isInProgress()){
                    float dx=e.getX()-downX,dy=e.getY()-downY;
                    if(Math.abs(dx)+Math.abs(dy)>ViewConfiguration.get(getContext()).getScaledTouchSlop()){dragged=true;cancelPress();}
                    if(dragged){
                        if(zoom>1.01f){offsetX+=e.getX()-lastX;offsetY+=e.getY()-lastY;}
                        else if(Math.abs(dy)>=Math.abs(dx)){offsetY+=e.getY()-lastY;}
                        invalidate();
                    }
                    lastX=e.getX();lastY=e.getY();
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                float dx=e.getX()-downX,dy=e.getY()-downY;long elapsed=e.getEventTime()-downTime;
                if(!multiTouch&&!scaleDetector.isInProgress()){
                    if(longHeld&&!dragged)hit(downX,downY);
                    else if(dragged&&zoom<=1.01f&&elapsed<=1400&&Math.abs(dx)>=Math.max(64*density,getWidth()*.16f)&&Math.abs(dx)>Math.abs(dy)*1.6f)turning.turn(dx<0?1:-1);
                    else if(!dragged&&elapsed<500&&e.getX()>=getWidth()*.18f&&e.getX()<=getWidth()*.82f&&e.getY()>=getHeight()*.18f&&e.getY()<=getHeight()*.82f)blankTap.run();
                    performClick();
                }
                cancelPress();return true;
            case MotionEvent.ACTION_CANCEL:dragged=true;cancelPress();return true;
            default:return true;
        }
    }
    private JSONObject findToken(float x,float y){
        if(bitmap==null||page==null)return null;float scale=fit()*zoom;
        x=(x-offsetX)/scale;y=(y-offsetY)/scale;
        try{
            JSONArray blocks=page.getJSONArray("blocks");
            for(int i=0;i<blocks.length();i++){
                JSONArray ts=blocks.getJSONObject(i).getJSONArray("tokens");
                for(int j=0;j<ts.length();j++){
                    JSONObject t=ts.getJSONObject(j);if(!t.has("x"))continue;RectF r=rect(t);r.inset(-3,-3);
                    if(r.contains(x,y))return t;
                }
            }
        }catch(JSONException ignored){}
        return null;
    }
    private boolean hit(float x,float y){
        JSONObject t=findToken(x,y);if(t==null)return false;
        selection.select(t.optInt("i"),t.optInt("i"));return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public void release(){cancelPress();if(bitmap!=null){bitmap.recycle();bitmap=null;}}
}
