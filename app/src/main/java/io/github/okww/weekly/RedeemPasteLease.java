package io.github.okww.weekly;

/** Only the observed non-focusable Xiaomi clipboard card may accompany our own recent paste. */
final class RedeemPasteLease {
    static final String PACKAGE="com.miui.contentextension";
    private volatile long started=-1;
    void start(long now){if(now<0)throw new IllegalArgumentException("粘贴时钟异常");started=now;}
    void stop(){started=-1;}
    boolean recent(long now){long from=started;return from>=0&&now>=from&&now-from<=6000;}
    static boolean eventWindow(int eventId,int candidateId){return eventId>=0&&eventId==candidateId;}
    boolean expected(long now,String packageName,boolean gameFocused,boolean systemWindow,boolean focused,boolean active,
                     int left,int top,int right,int bottom){
        // shortcut: calibrated 2400x1080 right-edge clipboard card only; changed layouts must stop for evidence.
        return recent(now)&&PACKAGE.equals(packageName)&&gameFocused&&systemWindow&&!focused&&!active
                &&left>=1824&&left<right&&right<=2400&&right>=2300
                &&top>=120&&top<=270&&bottom>top&&bottom<=529;
    }
}
