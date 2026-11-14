package io.github.okww.weekly;

/** Purple endpoint observed before/after the mobile resonance, not the generic white forte bar. */
final class GalbrenaHud {
    static boolean full(PortraitMatcher.Pixels frame) {
        if(frame.width!=2400||frame.height!=1080)throw new IllegalArgumentException("嘉贝莉娜回路画幅未标定");
        return purple(frame,1280,960,1328,982)>=.12;
    }
    static double purple(PortraitMatcher.Pixels p,int l,int t,int r,int b) {
        int filled=0;
        for(int y=t;y<b;y++)for(int x=l;x<r;x++) {
            int c=p.argb[y*p.width+x],red=c>>16&255,green=c>>8&255,blue=c&255;
            if(blue>=190&&red>=130&&blue>=green+15&&blue>=red+5)filled++;
        }
        return filled/(double)((r-l)*(b-t));
    }
}
