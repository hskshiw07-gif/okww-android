package io.github.okww.weekly;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Reuses first-party exported features and the existing normalized matcher. */
final class MobileSkillFeatures {
    private final List<PortraitMatcher.Template> templates=new ArrayList<>();
    private final MobileToolHud tools;
    final ChisaHud chisa;
    MobileSkillFeatures(AssetManager assets) throws Exception {
        List<PortraitMatcher.Template> nonLevitator=new ArrayList<>();
        for(String file:assets.list("combat-features")) {
            java.util.regex.Matcher geometry=java.util.regex.Pattern.compile("(.+)_([0-9]+)x([0-9]+)_([0-9]+)_([0-9]+)\\.png").matcher(file);
            if(!geometry.matches())throw new IllegalStateException("战斗特征资源格式异常");
            try(InputStream input=assets.open("combat-features/"+file)) {
                Bitmap image=BitmapFactory.decodeStream(input);
                if(image==null)throw new IllegalStateException("战斗特征资源解码失败");
                try {
                    int[] pixels=new int[image.getWidth()*image.getHeight()];image.getPixels(pixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());
                    templates.add(new PortraitMatcher.Template(geometry.group(1),new PortraitMatcher.Pixels(image.getWidth(),image.getHeight(),pixels),Integer.parseInt(geometry.group(3))));
                } finally {image.recycle();}
            }
        }
        if(templates.size()!=6)throw new IllegalStateException("战斗特征资源缺失");
        try(InputStream in=assets.open("galbrena-mobile/non-levitator.png")) {
            Bitmap image=BitmapFactory.decodeStream(in);
            if(image==null)throw new IllegalStateException("手机工具状态模板缺失");
            try {
                int[] colors=new int[image.getWidth()*image.getHeight()];
                image.getPixels(colors,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());
                nonLevitator.add(new PortraitMatcher.Template("non-levitator",new PortraitMatcher.Pixels(image.getWidth(),image.getHeight(),colors),1080));
            } finally {image.recycle();}
        }
        nonLevitator.add(new PortraitMatcher.Template("empty-tool",load(assets,"chisa-mobile/empty-tool.png"),1080));
        tools=new MobileToolHud(nonLevitator);
        chisa=new ChisaHud(load(assets,"chisa-mobile/normal-attack.png"),load(assets,"chisa-mobile/powered-attack.png"),
                load(assets,"chisa-mobile/resonance-attack.png"));
    }
    private static PortraitMatcher.Pixels load(AssetManager assets,String path)throws Exception{
        try(InputStream in=assets.open(path)){
            Bitmap image=BitmapFactory.decodeStream(in);if(image==null)throw new IllegalStateException("手机状态模板缺失："+path);
            try{int[] colors=new int[image.getWidth()*image.getHeight()];image.getPixels(colors,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());
                return new PortraitMatcher.Pixels(image.getWidth(),image.getHeight(),colors);
            }finally{image.recycle();}
        }
    }
    boolean knownNonLevitator(PortraitMatcher.Pixels frame) {
        return tools.knownNonLevitator(frame);
    }
    boolean aemeath(PortraitMatcher.Pixels p,TeamBuiltinRotation.Feature feature) {
        String name;int cx,cy;
        switch(feature) {
            case AEMEATH_LIB2 -> {name="aemeath_lib2";cx=1659;cy=841;}
            case AEMEATH_E1 -> {name="aemeath_e1";cx=1780;cy=933;}
            case AEMEATH_E2 -> {name="aemeath_e2";cx=1780;cy=933;}
            case AEMEATH_HUMAN_HEAVY -> {name="aemeath_human_heavy";cx=1951;cy=825;}
            default -> throw new IllegalArgumentException("未知内置特征");
        }
        return score(p,name,cx-65,cy-65,cx+65,cy+65)>=.78;
    }
    boolean normalTargetIndicator(PortraitMatcher.Pixels p) {
        return Math.max(score(p,"has_target",535,945,620,1025),score(p,"no_target",535,945,620,1025))>=.75;
    }
    private double score(PortraitMatcher.Pixels frame,String name,int l,int t,int r,int b) {
        List<PortraitMatcher.Template> selected=new ArrayList<>();for(PortraitMatcher.Template template:templates)if(template.character.equals(name))selected.add(template);
        if(selected.isEmpty())throw new IllegalStateException("内置特征缺失："+name);
        double best=-1;
        for(double scale:new double[]{.8,1,1.2,1.4,1.6})best=Math.max(best,PortraitMatcher.match(frame,selected,l,t,r,b,scale).score);
        return best;
    }
}
