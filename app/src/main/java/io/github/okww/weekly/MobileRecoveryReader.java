package io.github.okww.weekly;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.util.List;
import static io.github.okww.weekly.GardenRules.*;

/** Native mobile Nexus icon; travel still requires the named Nexus detail panel and owning UID. */
final class MobileRecoveryReader {
    private final List<PortraitMatcher.Template> templates;
    MobileRecoveryReader(AssetManager assets) throws Exception {
        try(InputStream input=assets.open("daily-features/recovery-nexus.png")) {
            Bitmap image=BitmapFactory.decodeStream(input);
            if(image==null)throw new IllegalStateException("中枢信标资源解码失败");
            try { templates=List.of(template(MobileCombatReader.pixels(image))); }
            finally { image.recycle(); }
        }
    }
    static PortraitMatcher.Template template(PortraitMatcher.Pixels image) {
        return new PortraitMatcher.Template("nexus",image,1080,true);
    }
    static boolean map(Screen screen) {
        return scoped(screen)
                &&screen.one("切换地图",.74f,.88f,.92f,.98f)!=null;
    }
    private static boolean scoped(Screen screen) {
        return screen.aspect>2.18f&&screen.aspect<2.26f&&!screen.automationPanelVisible()
                &&AccountDailyConfig.detectUid(screen)!=null;
    }
    static Text travel(Screen screen) {
        if(!scoped(screen)||screen.one("中枢信标",.70f,0,1,.12f)==null)return null;
        return screen.one("快速旅行",.72f,.86f,.99f,.98f);
    }
    PortraitMatcher.Match find(PortraitMatcher.Pixels frame,Screen screen) {
        if(frame.width!=2400||frame.height!=1080||!map(screen)||travel(screen)!=null)return null;
        PortraitMatcher.Match match=PortraitMatcher.match(frame,templates,250,160,2215,960);
        return match.score>=.90?match:null;
    }
}
