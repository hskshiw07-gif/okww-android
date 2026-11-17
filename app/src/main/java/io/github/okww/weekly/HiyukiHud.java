package io.github.okww.weekly;

import java.util.List;

/** Mobile forte changes shape after R1; target lock stays in the same place in both forms. */
final class HiyukiHud {
    enum Phase { NORMAL, LIBERATED, UNKNOWN }
    private static final int LEFT=1015,TOP=935,WIDTH=110,HEIGHT=70;
    private final List<PortraitMatcher.Template> forms,grayForms;
    HiyukiHud(PortraitMatcher.Pixels normal,PortraitMatcher.Pixels liberated){
        forms=List.of(new PortraitMatcher.Template("normal",normal,1080,true),
                new PortraitMatcher.Template("liberated",liberated,1080,true));
        grayForms=List.of(new PortraitMatcher.Template("normal",PortraitMatcher.grayscale(normal),HEIGHT,true),
                new PortraitMatcher.Template("liberated",PortraitMatcher.grayscale(liberated),HEIGHT,true));
    }
    Phase phase(PortraitMatcher.Pixels frame){
        if(frame.width!=2400||frame.height!=1080)throw new IllegalStateException("绯雪回路画幅未标定");
        var match=PortraitMatcher.match(frame,forms,LEFT,TOP,LEFT+WIDTH,TOP+HEIGHT,1);
        if(match.score>=.75&&match.score-match.runnerUp>=.08)return phase(match);
        // Red battle effects color the background underneath the gauge; compare its luminance shape on RGB misses.
        int[] crop=new int[WIDTH*HEIGHT];
        for(int y=0;y<HEIGHT;y++)System.arraycopy(frame.argb,(TOP+y)*frame.width+LEFT,crop,y*WIDTH,WIDTH);
        var gray=PortraitMatcher.grayscale(new PortraitMatcher.Pixels(WIDTH,HEIGHT,crop));
        match=PortraitMatcher.match(gray,grayForms,0,0,WIDTH,HEIGHT,1);
        return match.score>=.80&&match.score-match.runnerUp>=.08?phase(match):Phase.UNKNOWN;
    }
    private static Phase phase(PortraitMatcher.Match match){
        return match.character.equals("normal")?Phase.NORMAL:Phase.LIBERATED;
    }
}
