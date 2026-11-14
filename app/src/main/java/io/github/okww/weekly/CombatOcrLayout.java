package io.github.okww.weekly;

/** One OCR image keeps the full-screen guards and enlarges only the three small arena labels. */
final class CombatOcrLayout {
    static final int WIDTH=2400, HEIGHT=1416;
    // Source left/top/width/height, destination left/top, magnification.
    static final int[][] TILES={{105,290,520,52,0,1100,3},{1110,128,210,45,1600,1100,3},
            {1050,990,390,42,0,1270,3}};
    static int[] project(int left,int top,int right,int bottom) {
        if(left<0||top<0||right<=left||bottom<=top||right>WIDTH||bottom>HEIGHT)return null;
        for(int[] t:TILES) {
            if(left>=t[4]&&top>=t[5]&&right<=t[4]+t[2]*t[6]&&bottom<=t[5]+t[3]*t[6])
                return new int[]{t[0]+Math.round((left-t[4])/(float)t[6]),t[1]+Math.round((top-t[5])/(float)t[6]),
                        t[0]+Math.round((right-t[4])/(float)t[6]),t[1]+Math.round((bottom-t[5])/(float)t[6])};
        }
        if(bottom>1080)return null;
        int x=(left+right)/2,y=(top+bottom)/2;
        for(int[] t:TILES)if(x>=t[0]&&x<=t[0]+t[2]&&y>=t[1]&&y<=t[1]+t[3])return null;
        return new int[]{left,top,right,bottom};
    }
}
