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
    final PrioritySkillHud priority;
    final HiyukiHud hiyuki;
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
        if(templates.size()!=12)throw new IllegalStateException("战斗特征资源缺失");
        priority=new PrioritySkillHud(templates);
        hiyuki=new HiyukiHud(load(assets,"priority-mobile/hiyuki-normal-forte.png"),
                load(assets,"priority-mobile/hiyuki-liberated-forte.png"));
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
        String name;CombatTouchLayout.Control control;
        switch(feature) {
            case AEMEATH_LIB2 -> {name="aemeath_lib2";control=CombatTouchLayout.Control.LIBERATION;}
            case AEMEATH_E1 -> {name="aemeath_e1";control=CombatTouchLayout.Control.RESONANCE;}
            case AEMEATH_E2 -> {name="aemeath_e2";control=CombatTouchLayout.Control.RESONANCE;}
            case AEMEATH_HUMAN_HEAVY -> {name="aemeath_human_heavy";control=CombatTouchLayout.Control.ATTACK;}
            default -> throw new IllegalArgumentException("未知内置特征");
        }
        return priority.controlScore(p,name,control)>=.78;
    }
    boolean normalTargetIndicator(PortraitMatcher.Pixels p) {
        return Math.max(PrioritySkillHud.score(p,templates,"has_target",535,945,620,1025),
                PrioritySkillHud.score(p,templates,"no_target",535,945,620,1025))>=.75;
    }
}
