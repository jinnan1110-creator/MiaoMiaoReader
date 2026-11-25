package com.jinnan.miaomiaoreader;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewParent;
import android.webkit.WebView;

/** The reader owns long-press timing; platform long presses must not announce it early. */
public final class ReaderWebView extends WebView {
    public static final int LONG_PRESS_MILLIS=1000;

    public ReaderWebView(Context context){
        super(context);
        setLongClickable(false);
        setHapticFeedbackEnabled(false);
    }

    @Override public boolean performLongClick(){return true;}
    @Override public boolean performLongClick(float x,float y){return true;}
    @Override public boolean performHapticFeedback(int feedbackConstant){return false;}
    @Override public boolean performHapticFeedback(int feedbackConstant,int flags){return false;}

    /** Called only after JavaScript has armed a valid selection, respecting system haptic settings. */
    public void selectionReady(){
        ViewParent parent=getParent();
        if(parent instanceof View)((View)parent).performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }
}
