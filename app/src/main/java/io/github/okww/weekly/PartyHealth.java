package io.github.okww.weekly;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Visible party recovery only; caller must also require fresh identity and consecutive frames. */
final class PartyHealth {
    private static final Pattern HP=Pattern.compile("([1-9][0-9]{0,6})/([1-9][0-9]{0,6})");

    static boolean deadStandby(PortraitMatcher.Pixels frame,int slot) {
        if(frame==null||frame.width!=2400||frame.height!=1080||slot<0||slot>1)return false;
        int top=slot==0?319:458;
        for(int y=top;y<top+2;y++)for(int x=2135;x<2223;x++){
            int c=frame.argb[y*2400+x],r=c>>16&255,g=c>>8&255,b=c&255;
            if(r<40||r>60||Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>3)return false;
        }
        return true;
    }

    static boolean fullParty(PortraitMatcher.Pixels frame,GardenRules.Screen screen) {
        if(frame==null||screen==null||frame.width!=2400||frame.height!=1080
                ||!GardenRules.worldHud(screen)||AccountDailyConfig.detectUid(screen)==null
                ||screen.automationPanelVisible())return false;
        if(screen.has("提示|挑战成功|挑战失败|领取奖励|补充波片|兑换波片|复苏|复活|使用药品|确认离开|重新挑战|名词注释|教程|教学|暂停")
                ||screen.text(.20f,.15f,.85f,.85f).matches(".*(?:^|[|])(?:确认|确定|取消|关闭)(?:[|]|$).*")
                ||screen.text(.02f,.16f,.40f,.42f).matches(".*清理无[音普]区.*")
                ||screen.text(.42f,.08f,.59f,.18f).matches(".*[0-9]{2}:[0-9]{2}:[0-9]{2}.*"))return false;
        boolean found=false;
        for(GardenRules.Text word:screen.words) {
            if(word.x()<.40f||word.x()>.56f||word.y()<.915f||word.y()>.96f||!word.value.contains("/"))continue;
            Matcher hp=HP.matcher(word.value);
            if(found||!hp.matches()||!hp.group(1).equals(hp.group(2)))return false;
            found=true;
        }
        return found&&fullBar(frame,319,2223)&&fullBar(frame,458,2224);
    }

    private static boolean fullBar(PortraitMatcher.Pixels frame,int top,int right) {
        // shortcut: Xiaomi 2400x1080 visible bars resolve about 1% HP; other layouts or exact standby HP need recalibration/OCR.
        // Measured healed interiors/right ends are neutral RGB200..215; empty is44. Include the tail: arena-shore is not full.
        for(int y=top;y<top+2;y++)for(int x=2135;x<right;x++) {
            int c=frame.argb[y*frame.width+x],r=c>>16&255,g=c>>8&255,b=c&255;
            int min=Math.min(r,Math.min(g,b)),max=Math.max(r,Math.max(g,b));
            if(min<195||max>230||max-min>8)return false;
        }
        return true;
    }
}
