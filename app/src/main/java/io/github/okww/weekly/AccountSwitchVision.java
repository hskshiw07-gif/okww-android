package io.github.okww.weekly;

import java.util.List;
import static io.github.okww.weekly.GardenRules.*;

/** Public power-icon samples from dev7 terminal, never a PC coordinate or an arbitrary bright pixel. */
final class AccountSwitchVision {
    // 12x14 RGB samples of the 60x64 icon at (120,996); no account/avatar area is stored.
    private static final String RGB =
            "151f26151f25151f25151f25151f24151f24151e24151e24151e24151e23151e24151e24"+
            "151f25151f25151f24151f24141f24232d32242e32151f24141f24141f24141f24151f24"+
            "151f24151f24151f24151f24141f24ffffffebecec141f24141f24141f24141f24141f24"+
            "141f24151f24212a2f3f484c242f33ffffffeaebeb263035313b3f141f24141f24141f24"+
            "151f24151f24555c60ffffff30393dffffffeaebeb394145ffffff373f42162025151f24"+
            "151e231f282cffffff5a6165182226ffffffeaebeb1c252a929698f7f7f7182227151f24"+
            "141e233d4649ffffff1f292d151f23ffffffeaebeb141e232f373bffffff263033151f24"+
            "151f234f575aacafb1162025151f24eaebebd5d7d7151f231e282cfdfdfd343d41151f24"+
            "151f23434b4ff0f1f11a2429162025212b30222b30151f24273035ffffff2d363a151f25"+
            "151e23232c31ffffff3e474a152024151f24151f241620265e6569ffffff1a242a151f25"+
            "151e23202a2ed2d4d5e2e3e3343d411822271a2329434b50fbfbfc757b7e162025151f24"+
            "151f24151f242f383cc8caccffffffdbdddeeaebebffffff858a8d1b252a141f24141f24"+
            "151f24151f24151f25202a2f424b4f636a6e60686b353e42192227141e23141e23141e23"+
            "151f24151f24151f24141f24141f24141e24141e23141e23141e22131d22131d22131d22";
    private static final PortraitMatcher.Template POWER=template();
    private static PortraitMatcher.Template template(){
        int[] pixels=new int[60*64];
        for(int y=0;y<14;y++)for(int x=0;x<12;x++){
            int index=(y*12+x)*6;
            pixels[Math.round(y*63f/13)*60+Math.round(x*59f/11)]=0xff000000|Integer.parseInt(RGB.substring(index,index+6),16);
        }
        return new PortraitMatcher.Template("terminal-power",new PortraitMatcher.Pixels(60,64,pixels),1080);
    }
    static boolean terminal(Screen s){
        return s.aspect>2.18f&&s.aspect<2.26f&&!s.automationPanelVisible()
                &&s.one(".*终端",.05f,.02f,.18f,.10f)!=null
                &&s.one("编队",.36f,.48f,.49f,.60f)!=null
                &&s.one("数据坞",.48f,.48f,.59f,.60f)!=null
                &&s.one("[索素]拉指南",.66f,.48f,.79f,.60f)!=null
                &&s.one("提示|确认|取消|返回登录|退出登录")==null;
    }
    static Text exit(Screen s,PortraitMatcher.Pixels pixels){
        if(pixels==null||pixels.width!=2400||pixels.height!=1080||!terminal(s))return null;
        // shortcut: only the measured 2400x1080 terminal footer; other layouts need phone samples.
        PortraitMatcher.Match match=PortraitMatcher.match(pixels,List.of(POWER),105,990,195,1070,1);
        if(match.score<.94)return null;
        return new Text("终端电源入口（仅请求确认）",match.x-.01f,match.y-.02f,match.x+.01f,match.y+.02f);
    }
}
