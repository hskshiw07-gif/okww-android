package io.github.okww.weekly;

import java.util.List;

/** Measured mobile pink forte endpoint; ordinary resonance is not proof of a charged forte action. */
final class ChisaHud {
    enum State { NORMAL, FULL, POWERED }
    static final class PoweredConfirmation {
        private long previous=-1;
        private int count;
        boolean observe(State state,long capturedAt){
            if(capturedAt<=previous)return false;
            if(previous>=0&&capturedAt-previous>1500)count=0;
            count=state==State.POWERED?count+1:0;previous=capturedAt;
            return count>=2;
        }
    }
    private final List<PortraitMatcher.Template> attacks;
    ChisaHud(PortraitMatcher.Pixels normal,PortraitMatcher.Pixels powered,PortraitMatcher.Pixels resonance){
        attacks=List.of(new PortraitMatcher.Template("normal",normal,1080),new PortraitMatcher.Template("powered",powered,1080),
                new PortraitMatcher.Template("resonance",resonance,1080));
    }
    State read(PortraitMatcher.Pixels frame){
        if(frame.width!=2400||frame.height!=1080)throw new IllegalStateException("千咲回路画幅未标定");
        PortraitMatcher.Match match=PortraitMatcher.match(frame,attacks,1907,791,2007,891,1);
        if(match.score<.88||match.score-match.runnerUp<.06)throw new IllegalStateException("千咲普攻形态未知，未猜测回路");
        if(match.character.equals("powered"))return State.POWERED;
        return pinkEndpoint(frame)>=.35?State.FULL:State.NORMAL;
    }
    static double pinkEndpoint(PortraitMatcher.Pixels frame){
        int pink=0;
        // shortcut: only calibrated Xiaomi 2400x1080; remeasure if the HUD layout changes.
        for(int y=973;y<984;y++)for(int x=1290;x<1322;x++){
            int c=frame.argb[y*frame.width+x],r=c>>16&255,g=c>>8&255,b=c&255;
            if(r>170&&b>100&&r>g+40&&b>g+20)pink++;
        }
        return pink/(32.0*11);
    }
}
