package io.github.okww.weekly;

/** Read-only five-slot activity reward evidence; callers separately authorize and verify every chest action. */
final class ActivityRewardVision {
    enum Status { ALL_CLAIMED, NOT_ALL, UNKNOWN }
    enum State { CLAIMED, AVAILABLE, LOCKED, UNKNOWN }
    static final class Observation {
        final Status status;
        final String uid;
        final long capturedAt;
        final Integer points;
        private final double[] claimed,closed;
        private final double[] available;
        private final int[] gold,red;
        private final State[] states;
        private Observation(Status status,String uid,long capturedAt,Integer points,double[] claimed,double[] closed){
            this.status=status;this.uid=uid;this.capturedAt=capturedAt;this.points=points;
            this.claimed=claimed.clone();this.closed=closed.clone();
            available=emptyScores();gold=new int[5];red=new int[5];states=unknownStates();
        }
        // Policy-test fixtures only; runtime callers must obtain observations from observe().
        Observation(String uid,long capturedAt,Integer points,State[] states){this(uid,capturedAt,points,states,emptyScores(),emptyScores(),emptyScores(),new int[5],new int[5]);}
        private Observation(String uid,long capturedAt,Integer points,State[] states,double[] claimed,double[] closed,double[] available,int[] gold,int[] red){
            if(states==null||states.length!=5||java.util.Arrays.asList(states).contains(null))throw new IllegalArgumentException("需要五档明确观测状态");
            this.uid=uid;this.capturedAt=capturedAt;this.points=points;this.states=states.clone();this.claimed=claimed.clone();this.closed=closed.clone();
            this.available=available.clone();this.gold=gold.clone();this.red=red.clone();
            boolean known=uid!=null&&points!=null,all=true;for(State state:states){known&=state!=State.UNKNOWN;all&=state==State.CLAIMED;}
            status=!known||all&&points<100?Status.UNKNOWN:all?Status.ALL_CLAIMED:Status.NOT_ALL;
        }
        State state(int slot){return states[slot];}
        State[] states(){return states.clone();}
        boolean known(){return status!=Status.UNKNOWN;}
        boolean allClaimed(){return status==Status.ALL_CLAIMED;}
        boolean claimed(int slot){return states[slot]==State.CLAIMED;}
        int firstAvailable(){if(!known())return -1;for(int i=0;i<5;i++)if(states[i]==State.AVAILABLE&&points>=20*(i+1))return i;return -1;}
        double[] claimedScores(){return claimed.clone();}
        double[] closedScores(){return closed.clone();}
        double[] availableScores(){return available.clone();}
        int[] goldPixels(){return gold.clone();}
        int[] redPixels(){return red.clone();}
    }
    private static final int[] CENTERS={894,1218,1542,1867,2191},LABELS={837,1162,1487,1809,2127};
    static final double MIN_SCORE=.90,MIN_MARGIN=.30;
    static final double MIN_AVAILABLE=.94;
    // Public UI only: 16x16 counts (0..G=16) of neutral-white pixels per 4x4 cell in a 64x64 icon.
    // Claimed: guide-followup.png first milestone; closed: daily-inspect.png first milestone. No full-screen/private assets.
    private static final double[] CLAIMED=decode(new String[]{
        "02A4000000004C20","3C100000000001C3","A1000000000001ED","4000000000000B83",
        "0000000000007D00","000000000005F200","00026300001F5000","0004GGC300D80000",
        "00029EGF2AC00000","000000AGEF100000","0000000DG4000000","0000000990000000",
        "3000000300000002","C00000000000000C","3A10000000000094","03C2000000002B30"});
    private static final double[] CLOSED=decode(new String[]{
        "0132000000002810","2300000000000171","5000000000000006","2000000000000002",
        "0003444224443000","006GGGGCCGGGG600","00FGBGG9AGGBGF00","00BB2B943AB2BB00",
        "0055054753505400","00GGAGG78GG8GG00","00GGEGGCCGGFGF00","00DGGGGDEGGGGB00",
        "1014444444444102","4000000000000009","2300000000000073","0232000000002930"});
    // Independent golden closed-chest shape, sampled inside the circle from dev41-free-68.png slot 1.
    private static final double[] AVAILABLE=decode(new String[]{
        "000000000000","000000000000","6CCCC78CCCC6","GGGGGCCGGGGG","GGCGGBAGGCGG","C94C9879C68C",
        "84184A948238","GGAGG87GGBFG","GGEGGCCGGEGG","GGGGGCFGGC6D","BCCCCCCC6069","000000000000"},12);
    private long lastFrame=-1;
    private String lastUid;

    Observation observe(PortraitMatcher.Pixels pixels,GardenRules.Screen screen,String expectedUid,long capturedAt,long now){
        long previous=lastFrame;
        boolean fresh=capturedAt>=0&&capturedAt>previous&&capturedAt<=now&&now-capturedAt<=1500;
        if(capturedAt>=0&&capturedAt<=now&&capturedAt>lastFrame)lastFrame=capturedAt;
        String uid=AccountDailyConfig.detectUid(screen);Integer points=null;
        double[] claimed={-1,-1,-1,-1,-1},closed={-1,-1,-1,-1,-1};
        if(!fresh||previous>=0&&capturedAt-previous>2500||pixels.width!=2400||pixels.height!=1080
                ||!(screen.aspect>2.18f&&screen.aspect<2.26f)||screen.automationPanelVisible()
                ||expectedUid==null||!expectedUid.matches("[0-9]{9}")||!expectedUid.equals(uid))
            return new Observation(Status.UNKNOWN,uid,capturedAt,null,claimed,closed);
        boolean accountChanged=lastUid!=null&&!lastUid.equals(uid);lastUid=uid;
        if(accountChanged)return new Observation(Status.UNKNOWN,uid,capturedAt,null,claimed,closed);
        try {points=DailyRules.activityPoints(screen);}catch(IllegalStateException ambiguousOcr){/* An ambiguous status is not a completion certificate. */}
        if(points==null)return new Observation(Status.UNKNOWN,uid,capturedAt,null,claimed,closed);
        State[] states=unknownStates();double[] available=emptyScores();int[] gold=new int[5],red=new int[5];
        for(int i=0;i<5;i++){
            if(!screen.hasOne(""+(20*(i+1)),(LABELS[i]-28)/2400f,956f/1080,(LABELS[i]+28)/2400f,990f/1080))
                return new Observation(Status.UNKNOWN,uid,capturedAt,points,claimed,closed);
            double[] values=sample(pixels,i);claimed[i]=correlate(values,CLAIMED);closed[i]=correlate(values,CLOSED);
            boolean checked=claimed[i]>=MIN_SCORE&&claimed[i]-closed[i]>=MIN_MARGIN;
            boolean shut=closed[i]>=MIN_SCORE&&closed[i]-claimed[i]>=MIN_MARGIN;
            available[i]=availableScore(pixels,i);ColorEvidence colors=colors(pixels,i);gold[i]=colors.gold;red[i]=colors.red;
            boolean reached=points>=20*(i+1);
            if(checked&&reached)states[i]=State.CLAIMED;
            else if(reached&&available[i]>=MIN_AVAILABLE&&colors.gold>=400&&colors.gold<=1200&&colors.badge)states[i]=State.AVAILABLE;
            else if(!reached&&shut&&colors.gold<80&&colors.red<20)states[i]=State.LOCKED;
        }
        return new Observation(uid,capturedAt,points,states,claimed,closed,available,gold,red);
    }
    static float tapX(int slot){if(slot<0||slot>=5)throw new IllegalArgumentException("活动档位越界");return CENTERS[slot]/2400f;}
    static float tapY(){return 950f/1080;}
    // Diagnostic score only; callers must use observe() and its page/UID/freshness certificate, never this value alone.
    static double score(PortraitMatcher.Pixels pixels,int slot,boolean claimed){return correlate(sample(pixels,slot),claimed?CLAIMED:CLOSED);}
    static double availableScore(PortraitMatcher.Pixels p,int slot){
        if(p.width!=2400||p.height!=1080||slot<0||slot>=5)throw new IllegalArgumentException("活动奖励图标布局未标定");
        double[] cells=new double[144];int left=CENTERS[slot]-24;
        for(int y=0;y<48;y++)for(int x=0;x<48;x++){int c=p.argb[(926+y)*2400+left+x];if((c>>16&255)>=190&&(c>>8&255)>=160&&(c&255)>=90)cells[y/4*12+x/4]++;}
        return correlate(cells,AVAILABLE);
    }
    private static final class ColorEvidence {int gold,red;boolean badge;}
    private static ColorEvidence colors(PortraitMatcher.Pixels p,int slot){
        ColorEvidence result=new ColorEvidence();int cx=CENTERS[slot];
        for(int y=-41;y<=41;y++)for(int x=-41;x<=41;x++){
            int r2=x*x+y*y;if(r2<33*33||r2>41*41)continue;
            int c=p.argb[(950+y)*2400+cx+x],r=c>>16&255,g=c>>8&255,b=c&255;
            if(r>=210&&g>=170&&b<=210&&r-b>=40&&g-b>=25)result.gold++;
        }
        int minX=99,maxX=-1,minY=99,maxY=-1;
        for(int y=898;y<928;y++)for(int x=19;x<50;x++){
            int c=p.argb[y*2400+cx+x],r=c>>16&255,g=c>>8&255,b=c&255;
            if(r>=180&&g<=150&&b<=160&&r-g>=65&&r-b>=45){result.red++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y-898);maxY=Math.max(maxY,y-898);}
        }
        int w=maxX-minX+1,h=maxY-minY+1;double density=w>0&&h>0?result.red/(double)(w*h):0;
        result.badge=result.red>=80&&result.red<=240&&w>=10&&w<=21&&h>=12&&h<=22
                &&minX+maxX>=60&&minX+maxX<=78&&minY+maxY>=20&&minY+maxY<=36&&density>=.35&&density<=.80;
        return result;
    }
    private static double[] sample(PortraitMatcher.Pixels p,int slot){
        if(p.width!=2400||p.height!=1080||slot<0||slot>=5)throw new IllegalArgumentException("活动奖励图标布局未标定");
        double[] cells=new double[256];int left=CENTERS[slot]-32;
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){
            int c=p.argb[(918+y)*p.width+left+x],r=c>>16&255,g=c>>8&255,b=c&255;
            int low=Math.min(r,Math.min(g,b)),high=Math.max(r,Math.max(g,b));
            if(low>=200&&high-low<45)cells[y/4*16+x/4]++;
        }
        return cells;
    }
    private static double[] decode(String[] rows){
        return decode(rows,16);
    }
    private static double[] decode(String[] rows,int size){
        double[] result=new double[size*size];int n=0;
        if(rows.length!=size)throw new IllegalArgumentException("图标数值模板损坏");
        for(String row:rows){if(row.length()!=size)throw new IllegalArgumentException("图标数值模板损坏");
            for(int i=0;i<size;i++){int value="0123456789ABCDEFG".indexOf(row.charAt(i));if(value<0)throw new IllegalArgumentException("图标数值模板损坏");result[n++]=value;}}
        return result;
    }
    private static double[] emptyScores(){return new double[]{-1,-1,-1,-1,-1};}
    private static State[] unknownStates(){return new State[]{State.UNKNOWN,State.UNKNOWN,State.UNKNOWN,State.UNKNOWN,State.UNKNOWN};}
    private static double correlate(double[] a,double[] b){
        double sumA=0,sumB=0;for(int i=0;i<a.length;i++){sumA+=a[i];sumB+=b[i];}
        double meanA=sumA/a.length,meanB=sumB/b.length,dot=0,normA=0,normB=0;
        for(int i=0;i<a.length;i++){double x=a[i]-meanA,y=b[i]-meanB;dot+=x*y;normA+=x*x;normB+=y*y;}
        return normA==0||normB==0?-1:dot/Math.sqrt(normA*normB);
    }
}
