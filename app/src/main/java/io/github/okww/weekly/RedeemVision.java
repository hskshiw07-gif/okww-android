package io.github.okww.weekly;
import java.util.List;
final class RedeemVision {
    // Public settings gear above y=1046; bottom UID watermark pixels are excluded.
    private static final String RGB=
        "2b31342c32352c32352c33362c33362c33362d33362c33362c32352c32352c32362c3236"+
        "2a31342b31342b31342b32342b32352b32352c32352c32352b31352a31342a3034293034"+
        "2930332a30342a30332a31332a3134ecececb8b9ba2b31342a3034282f33272e33262d32"+
        "292f332930332930332930332a3033ffffffffffff293033282f33272e32252c32232c31"+
        "282f33282f33282f33293033aaacacffffffffffffe8e9e9272e32252d32232c31222b30"+
        "282f33282f33fafafafffffff2f3f3282e32272f32fffffffffffffcfcfc232b30222b30"+
        "282f33282f33ffffffffffff272e32aaadaea6a9aa252d32fffffffcfcfc222b30222a30"+
        "272f32272f33cdceceffffff676b6e252d31242c30999c9effffff84888a222a30212a30"+
        "272f33272f33272e33ffffffa5a8aa242c31232b31a9abad74787b222a30212a31212930"+
        "272f33272f33262e33ffffffadafb1232b31222a31a5a7a9686d70212a30212930212930"+
        "272e33272e33b0b2b3ffffff777b7f232b31222a30a0a3a5ffffff9c9ea020293020292f"+
        "272e34272e34ffffffffffff242c32a5a7aaa7aaab222a30ffffffffffff20293020292f"+
        "272e33272e33efeff0f8f8f9ffffff232b31222a30fffffff5f6f6aaacae21293020292f"+
        "262e33262e32262d32252d323a4044ffffffffffff62676a212a3021293021293021292f";
    private static final String PASTE_RGB=
        "c4c5c4c4c5c4c4c5c4c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c4c5c4c3c4c3c2c4c3c2c4c3"+
        "c4c5c4c5c5c5c6c7c6c7c8c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c7c6c6c6c3c5c5c3c4c4"+
        "c5c6c5c6c7c6c8c9c8cacbcacbcccbcacbcacacacacacacacacacac8c8c8c4c6c6c2c4c4"+
        "c4c5c4c7c8c7efefeff6f6f6f6f6f6f6f7f6f6f7f6f6f6f6f6f7f6e1e2e1c6c7c7c2c4c3"+
        "c4c5c4c8c9c8eff0efd4d4d4cecfcecccccccccccccecfcee0e0e0e0e1e1c7c8c8c4c5c5"+
        "c5c6c5c8c9c8eff0efd5d6d5cbcccbc9c9c9c9c9c9cbcbcbe2e2e2e1e1e1c6c8c7c4c5c5"+
        "c5c6c5c8c9c8efefefd4d5d4cacbcac7c8c7c7c7c7c9cac9e1e1e1e1e1e1c7c8c8c4c5c5"+
        "c5c6c5c8c9c8efefefd4d5d4cdcecdc9cac9c7c7c7cacacae0e1e0e0e0e0c6c8c8c4c5c5"+
        "c4c5c4c7c8c7eeefeee6e7e6d4d5d4d2d3d2cacbcacacacae1e1e1e1e1e1c7c8c8c3c5c4"+
        "c4c5c4c6c7c6cccdccf6f6f6f6f7f6f6f7f6cecececdcecde2e2e2e0e1e1c8c9c8c5c6c5"+
        "c4c5c4c5c6c5c9cac9cfd0cff6f6f6f6f7f6d0d1d0cfd0cfe2e2e2e1e1e1c8c9c8c5c6c5"+
        "c4c5c4c4c5c4c6c7c6c9cac9cecfcef5f5f5f6f7f6f6f6f6f6f7f6e0e1e0c6c7c7c4c5c4"+
        "c4c5c4c4c5c4c4c5c4c6c7c6c8c8c8c9cac9cccccccccdcccacbcac7c8c7c5c6c5c4c5c4"+
        "c4c5c4c4c5c4c4c5c4c4c5c4c5c6c5c6c7c6c7c8c7c7c8c7c7c7c7c6c7c6c3c4c4c4c5c4";
    private static final PortraitMatcher.Template GEAR=template("terminal-settings",RGB,70,52);
    private static final PortraitMatcher.Template PASTE=template("redeem-paste",PASTE_RGB,37,40);
    private static PortraitMatcher.Template template(String name,String rgb,int width,int height){
        int[] pixels=new int[width*height];
        for(int y=0;y<14;y++)for(int x=0;x<12;x++){
            int i=(y*12+x)*6;pixels[Math.round(y*(height-1f)/13)*width+Math.round(x*(width-1f)/11)]=0xff000000|Integer.parseInt(rgb.substring(i,i+6),16);
        }
        return new PortraitMatcher.Template(name,new PortraitMatcher.Pixels(width,height,pixels),1080);
    }
    static GardenRules.Text settings(GardenRules.Screen screen,PortraitMatcher.Pixels pixels){
        if(pixels==null||pixels.width!=2400||pixels.height!=1080||!AccountSwitchVision.terminal(screen))return null;
        // shortcut: measured 2400x1080 footer only; other device layouts require samples.
        PortraitMatcher.Match match=PortraitMatcher.match(pixels,List.of(GEAR),2144,987,2235,1046,1);
        return match.score<.94?null:new GardenRules.Text("settings",match.x-.01f,match.y-.02f,match.x+.01f,match.y+.02f);
    }
    static GardenRules.Text paste(GardenRules.Screen screen,PortraitMatcher.Pixels pixels){
        if(pixels==null||pixels.width!=2400||pixels.height!=1080||!(screen.aspect>2.18f&&screen.aspect<2.26f)
                ||screen.automationPanelVisible()||AccountDailyConfig.detectUid(screen)==null||!RedeemRules.dialog(screen)
                ||screen.one("请输入(?:兑换码|礼包码)|输入兑换码",.29f,.43f,.65f,.51f)==null)return null;
        // shortcut: public icon crop from the observed 2400x1080 empty dialog; other layouts need samples.
        PortraitMatcher.Match match=PortraitMatcher.match(pixels,List.of(PASTE),1632,478,1687,534,1);
        return match.score<.94?null:new GardenRules.Text("粘贴图标",match.x-.005f,match.y-.01f,match.x+.005f,match.y+.01f);
    }
}
