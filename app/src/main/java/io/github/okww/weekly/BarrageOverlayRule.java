package io.github.okww.weekly;

/** MIUI game-turbo notification danmaku: non-focusable, non-touchable top band while the game keeps focus. */
final class BarrageOverlayRule {
    static final String PACKAGE="com.xiaomi.barrage";
    private BarrageOverlayRule(){}
    static boolean expected(String packageName,boolean gameFocused,boolean systemWindow,boolean focused,boolean active,
                            int displayWidth,int displayHeight,int left,int top,int right,int bottom){
        // shortcut: calibrated on 2400x1080 (observed frame [0,76][2400,496]); other layouts must stop for evidence.
        return PACKAGE.equals(packageName)&&gameFocused&&systemWindow&&!focused&&!active
                &&displayWidth==2400&&displayHeight==1080
                &&left>=0&&left<right&&right<=2400&&top>=0&&top<bottom&&bottom<=540;
    }
}
