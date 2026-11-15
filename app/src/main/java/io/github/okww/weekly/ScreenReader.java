package io.github.okww.weekly;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ScreenReader implements AutoCloseable {
    interface DuringRead { void run() throws Exception; }
    private final TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());

    static final class Word {
        final String text;
        final Rect bounds;
        Word(String text, Rect bounds) { this.text = text; this.bounds = bounds; }
    }

    static final class Snapshot {
        final List<Word> words;
        final int width, height;
        Snapshot(List<Word> words) { this(words, 0, 0); }
        Snapshot(List<Word> words, int width, int height) {
            if (words == null || width < 0 || height < 0 || (width == 0) != (height == 0))
                throw new IllegalArgumentException("无效识别快照尺寸");
            this.words = words; this.width = width; this.height = height;
        }
    }

    Snapshot read(Bitmap frame) throws Exception {
        return read(frame,null);
    }
    private Snapshot read(Bitmap frame,DuringRead duringRead) throws Exception {
        // OCR owns its copy until the asynchronous native task ends, including cancellation/timeouts.
        Bitmap input = frame.copy(Bitmap.Config.ARGB_8888, false);
        com.google.android.gms.tasks.Task<Text> task = recognizer.process(InputImage.fromBitmap(input, 0));
        task.addOnCompleteListener(Runnable::run, finished -> input.recycle());
        if(duringRead!=null) {
            long deadline=android.os.SystemClock.elapsedRealtime()+8000;
            while(!task.isComplete()) {
                duringRead.run();
                long remaining=deadline-android.os.SystemClock.elapsedRealtime();
                if(remaining<=0)throw new java.util.concurrent.TimeoutException("战斗OCR超时");
                try { Tasks.await(task,Math.min(40,remaining),TimeUnit.MILLISECONDS); }
                catch(java.util.concurrent.TimeoutException pending) { /* Pump only the caller's verified normal-attack intent. */ }
            }
        }
        Text result = Tasks.await(task, 8, TimeUnit.SECONDS);
        List<Word> words = new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) for (Text.Line line : block.getLines()) {
            Rect bounds = line.getBoundingBox();
            if (bounds != null) words.add(new Word(line.getText(), new Rect(bounds)));
        }
        return new Snapshot(words, frame.getWidth(), frame.getHeight());
    }

    void enlarge(Snapshot snapshot, Bitmap frame, int left, int top, int width, int height) throws Exception {
        enlarge(snapshot,frame,left,top,width,height,3);
    }
    void enlarge(Snapshot snapshot, Bitmap frame, int left, int top, int width, int height,int scale) throws Exception {
        Bitmap crop=Bitmap.createBitmap(frame,left,top,width,height);
        Bitmap enlarged=Bitmap.createScaledBitmap(crop,width*scale,height*scale,true);
        try {
            Snapshot detail=read(enlarged);
            snapshot.words.removeIf(w->w.bounds.centerX()>=left && w.bounds.centerX()<=left+width
                    && w.bounds.centerY()>=top && w.bounds.centerY()<=top+height);
            for(Word word:detail.words)snapshot.words.add(new Word(word.text,new Rect(
                    left+Math.round(word.bounds.left/(float)scale),top+Math.round(word.bounds.top/(float)scale),
                    left+Math.round(word.bounds.right/(float)scale),top+Math.round(word.bounds.bottom/(float)scale))));
        } finally {enlarged.recycle();crop.recycle();}
    }

    void combatDetail(Snapshot snapshot, Bitmap frame) throws Exception {
        if(frame.getWidth()!=2400 || frame.getHeight()!=1080)throw new IllegalStateException("战斗OCR标定尺寸不符");
        enlarge(snapshot,frame,105,290,520,52);
        enlarge(snapshot,frame,1110,128,210,45);
        enlarge(snapshot,frame,1050,990,390,42);
    }

    Snapshot combat(Bitmap frame) throws Exception {
        return combat(frame,null);
    }
    Snapshot combat(Bitmap frame,DuringRead duringRead) throws Exception {
        if(frame.getWidth()!=2400 || frame.getHeight()!=1080)throw new IllegalStateException("战斗OCR标定尺寸不符");
        Bitmap composite=Bitmap.createBitmap(CombatOcrLayout.WIDTH,CombatOcrLayout.HEIGHT,Bitmap.Config.ARGB_8888);
        try {
            android.graphics.Canvas canvas=new android.graphics.Canvas(composite);
            canvas.drawColor(android.graphics.Color.BLACK);canvas.drawBitmap(frame,0,0,null);
            android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
            for(int[] t:CombatOcrLayout.TILES)canvas.drawBitmap(frame,new Rect(t[0],t[1],t[0]+t[2],t[1]+t[3]),
                    new Rect(t[4],t[5],t[4]+t[2]*t[6],t[5]+t[3]*t[6]),paint);
            List<Word> words=new ArrayList<>();
            for(Word word:read(composite,duringRead).words) {
                Rect r=word.bounds;int[] p=CombatOcrLayout.project(r.left,r.top,r.right,r.bottom);
                if(p!=null)words.add(new Word(word.text,new Rect(p[0],p[1],p[2],p[3])));
            }
            return new Snapshot(words,2400,1080);
        } finally {composite.recycle();}
    }

    @Override public void close() { recognizer.close(); }
}
