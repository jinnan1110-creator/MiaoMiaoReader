package com.jinnan.miaomiaoreader;

import java.util.*;

/** Column order is applied inside horizontal bands separated by spanning headings. */
public final class LayoutOrder {
    public static final class Block<T> {
        public final float left,top,right,bottom; public final T value;
        public Block(float l,float t,float r,float b,T v){left=l;top=t;right=r;bottom=b;value=v;}
    }
    public static <T> List<Block<T>> sort(List<Block<T>> input,float width){
        List<Block<T>> all=new ArrayList<>(input);
        Comparator<Block<T>> byY=Comparator.comparingDouble((Block<T> b)->b.top).thenComparingDouble(b->b.left);
        all.sort(byY);
        int left=0,right=0;
        for(Block<T>b:all)if(b.right-b.left<width*.60f){if(b.right<width*.58f)left++;if(b.left>width*.42f)right++;}
        if(left<2||right<2)return all;
        List<Block<T>> result=new ArrayList<>(),band=new ArrayList<>();
        for(Block<T>b:all){
            if(b.left<width*.43f&&b.right>width*.57f){flush(band,result,width,byY);result.add(b);}
            else band.add(b);
        }
        flush(band,result,width,byY);return result;
    }
    private static <T> void flush(List<Block<T>> band,List<Block<T>> out,float width,Comparator<Block<T>> byY){
        band.sort(Comparator.comparingInt((Block<T>b)->(b.left+b.right)/2<width/2?0:1).thenComparing(byY));out.addAll(band);band.clear();
    }
}
