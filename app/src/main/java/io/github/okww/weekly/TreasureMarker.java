package io.github.okww.weekly;

import java.util.Arrays;

/** Pixel-only marker evidence, not permission to move, interact, claim rewards, or spend waveplates. */
final class TreasureMarker {
    static final String FILE="treasure_icon_3840x2160_2925_970.png";
    private static final int LEFT=72,TOP=108,RIGHT=2328,BOTTOM=875,WIDTH=31,HEIGHT=27;
    private static final int GRID_X=12,GRID_Y=14,CANDIDATES=16;
    private final double[] normalized;
    private final int[] sampleX=offsets(WIDTH,GRID_X),sampleY=offsets(HEIGHT,GRID_Y);

    TreasureMarker(PortraitMatcher.Pixels template) {
        if(template==null||template.width!=61||template.height!=54)
            throw new IllegalArgumentException("宝箱原始模板尺寸不符");
        // Preserve the existing RGB feature and measured scores; its sampling grid is 12 by 14.
        normalized=new PortraitMatcher.Template("treasure",template,2160).normalized;
    }

    /** Returns normalized center coordinates and correlation score, or null for an unrecognized frame. */
    PortraitMatcher.Match find(PortraitMatcher.Pixels frame) {
        if(frame==null||frame.width!=2400||frame.height!=1080||normalized==null)return null;
        // shortcut: Xiaomi 2400x1080 uses the PC 61x54 export at31x27, UI scale1.0; other layouts need new measured calibration.
        double[] scores=new double[CANDIDATES];Arrays.fill(scores,-1);
        int[] xs=new int[CANDIDATES],ys=new int[CANDIDATES];
        for(int y=TOP;y<=BOTTOM-HEIGHT;y+=3)for(int x=LEFT;x<=RIGHT-WIDTH;x+=3) {
            double score=correlate(frame,x,y);
            if(score<=scores[CANDIDATES-1])continue;
            int i=CANDIDATES-1;
            while(i>0&&score>scores[i-1]) {scores[i]=scores[i-1];xs[i]=xs[i-1];ys[i]=ys[i-1];i--;}
            scores[i]=score;xs[i]=x;ys[i]=y;
        }
        // Dev36's real marker was coarse rank 3 (.504), but its fine peak was .928.
        // Refine bounded top candidates, not just the strongest background coarse peak; avatar searches stay unchanged.
        double best=-1;int bestX=0,bestY=0;
        for(int i=0;i<CANDIDATES&&scores[i]>-1;i++)
            for(int y=Math.max(TOP,ys[i]-3);y<=Math.min(BOTTOM-HEIGHT,ys[i]+3);y++)
                for(int x=Math.max(LEFT,xs[i]-3);x<=Math.min(RIGHT-WIDTH,xs[i]+3);x++) {
                    double score=correlate(frame,x,y);
                    if(score>best) {best=score;bestX=x;bestY=y;}
                }
        // Keep the PC find_treasure_icon threshold .8. A single template's runnerUp is NOT spatial uniqueness evidence.
        return best>=.8?new PortraitMatcher.Match("treasure",best,-1,
                (bestX+WIDTH/2f)/frame.width,(bestY+HEIGHT/2f)/frame.height):null;
    }

    private static int[] offsets(int size,int count) {
        int[] result=new int[count];
        for(int i=0;i<count;i++)result[i]=Math.round(i*(size-1f)/(count-1));
        return result;
    }

    private double correlate(PortraitMatcher.Pixels frame,int left,int top) {
        double sumB=0,sumG=0,sumR=0,squared=0,dot=0;int index=0;
        for(int y=0;y<GRID_Y;y++) {
            int row=(top+sampleY[y])*frame.width+left;
            for(int x=0;x<GRID_X;x++) {
                int color=frame.argb[row+sampleX[x]];
                int b=color&255,g=(color>>8)&255,r=(color>>16)&255;
                sumB+=b;sumG+=g;sumR+=r;squared+=b*b+g*g+r*r;
                dot+=b*normalized[index++]+g*normalized[index++]+r*normalized[index++];
            }
        }
        double norm=squared-(sumB*sumB+sumG*sumG+sumR*sumR)/(GRID_X*GRID_Y);
        return norm>1?Math.max(-1,Math.min(1,dot/Math.sqrt(norm))):-1;
    }
}
