package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.List;

/** Match the white utility glyph, not the changing combat scene behind it. */
final class MobileToolHud {
    private final List<PortraitMatcher.Template> templates=new ArrayList<>();
    MobileToolHud(List<PortraitMatcher.Template> source){
        for(var template:source)templates.add(new PortraitMatcher.Template(template.character,
                glyph(template.pixels,0,0,template.pixels.width,template.pixels.height,210,255),63));
    }
    boolean knownNonLevitator(PortraitMatcher.Pixels frame){
        if(frame.width!=2400||frame.height!=1080)throw new IllegalStateException("手机工具画幅未标定");
        var roi=glyph(frame,1918,639,68,63,210,255);
        if(PortraitMatcher.match(roi,templates,0,0,68,63,1).score>=.92)return true;
        // During attacks the measured disabled utility glyph is neutral gray, not missing.
        var dim=glyph(frame,1918,639,68,63,90,170);
        return PortraitMatcher.match(dim,templates,0,0,68,63,1).score>=.92;
    }
    private static PortraitMatcher.Pixels glyph(PortraitMatcher.Pixels source,int left,int top,int width,int height,int low,int high){
        int[] mask=new int[width*height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int c=source.argb[(top+y)*source.width+left+x],r=c>>16&255,g=c>>8&255,b=c&255;
            int min=Math.min(r,Math.min(g,b)),max=Math.max(r,Math.max(g,b));
            mask[y*width+x]=min>=low&&max<=high&&max-min<=45?0xffffffff:0xff000000;
        }
        return new PortraitMatcher.Pixels(width,height,mask);
    }
}
