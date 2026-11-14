package io.github.okww.weekly;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;

/** Loads the unchanged PC reward-marker export separately from combat-skill features. */
final class MobileTreasureReader {
    private final TreasureMarker marker;

    MobileTreasureReader(AssetManager assets) throws Exception {
        try(InputStream input=assets.open("tacet-features/"+TreasureMarker.FILE)) {
            Bitmap image=BitmapFactory.decodeStream(input);
            if(image==null)throw new IllegalStateException("宝箱特征资源解码失败");
            try {marker=new TreasureMarker(pixels(image));}finally{image.recycle();}
        }
    }

    PortraitMatcher.Match find(PortraitMatcher.Pixels frame){return marker.find(frame);}
    PortraitMatcher.Match find(Bitmap frame) {
        if(frame==null||frame.getWidth()!=2400||frame.getHeight()!=1080)return null;
        return marker.find(pixels(frame));
    }

    private static PortraitMatcher.Pixels pixels(Bitmap image) {
        int width=image.getWidth(),height=image.getHeight();int[] rgb=new int[width*height];
        image.getPixels(rgb,0,width,0,0,width,height);
        return new PortraitMatcher.Pixels(width,height,rgb);
    }
}
