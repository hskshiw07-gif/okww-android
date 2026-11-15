package io.github.okww.weekly;

import java.util.List;

/** Mobile forte changes shape after R1; target lock stays in the same place in both forms. */
final class HiyukiHud {
    enum Phase { NORMAL, LIBERATED, UNKNOWN }
    private final List<PortraitMatcher.Template> forms;
    HiyukiHud(PortraitMatcher.Pixels normal,PortraitMatcher.Pixels liberated){
        forms=List.of(new PortraitMatcher.Template("normal",normal,1080,true),
                new PortraitMatcher.Template("liberated",liberated,1080,true));
    }
    Phase phase(PortraitMatcher.Pixels frame){
        if(frame.width!=2400||frame.height!=1080)throw new IllegalStateException("绯雪回路画幅未标定");
        var match=PortraitMatcher.match(frame,forms,1015,935,1125,1005,1);
        if(match.score<.75||match.score-match.runnerUp<.08)return Phase.UNKNOWN;
        return match.character.equals("normal")?Phase.NORMAL:Phase.LIBERATED;
    }
}
