package io.github.okww.weekly;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public final class CaptureService extends Service {
    static volatile CaptureService instance;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Bitmap latest;
    private long capturedAt;
    private long frameTimestampNanos;
    private int width, height;
    private volatile boolean closing;
    private final Object lock = new Object();
    private final CaptureDemand demand = new CaptureDemand();

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null || "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (projection != null) return START_NOT_STICKY;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("capture", "屏幕采集", NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, CaptureService.class).setAction("STOP");
        PendingIntent stopAction = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "capture")
                .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("鸣潮周常屏幕采集中")
                .setContentText("点击停止会同时停止自动化").setContentIntent(stopAction)
                .addAction(new Notification.Action.Builder(null, "停止", stopAction).build())
                .setOngoing(true).build();
        if (android.os.Build.VERSION.SDK_INT >= 29)
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(1, notification);
        try {
            DisplayMetrics metrics = new DisplayMetrics();
            getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(metrics);
            width = metrics.widthPixels;
            height = metrics.heightPixels;
            if (width <= height) throw new IllegalStateException("请在横屏状态授权整个屏幕采集");
            thread = new HandlerThread("screen-capture");
            thread.start();
            Handler handler = new Handler(thread.getLooper());
            Intent permission = intent.getParcelableExtra("permission");
            if (permission == null) throw new IllegalStateException("缺少屏幕采集授权");
            projection = getSystemService(MediaProjectionManager.class)
                    .getMediaProjection(intent.getIntExtra("result", 0), permission);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
                @Override public void onCapturedContentResize(int newWidth, int newHeight) {
                    if (newWidth != width || newHeight != height) stopSelf();
                }
            }, new Handler(getMainLooper()));
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(source -> {
                try (Image image = source.acquireLatestImage()) {
                    if (image == null || closing || SystemClock.elapsedRealtime() - capturedAt < 200) return;
                    if (!demand.take()) return;
                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer buffer = plane.getBuffer();
                    int paddedWidth = plane.getRowStride() / plane.getPixelStride();
                    Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
                    padded.copyPixelsFromBuffer(buffer);
                    Bitmap frame = Bitmap.createBitmap(padded, 0, 0, width, height);
                    if (padded != frame) padded.recycle();
                    synchronized (lock) {
                        if (closing) { frame.recycle(); return; }
                        if (latest != null) latest.recycle();
                        latest = frame;
                        capturedAt = SystemClock.elapsedRealtime();
                        frameTimestampNanos = image.getTimestamp();
                    }
                } catch (RuntimeException error) { stopSelf(); }
            }, handler);
            display = projection.createVirtualDisplay("weekly-screen", width, height, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
            instance = this;
        } catch (RuntimeException error) {
            if (GameAccessibilityService.instance != null) GameAccessibilityService.instance.stop("采集失败：" + error.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    Bitmap freshFrame(long after) {
        synchronized (lock) {
            DisplayMetrics metrics = new DisplayMetrics();
            getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(metrics);
            if (metrics.widthPixels != width || metrics.heightPixels != height)
                throw new IllegalStateException("屏幕方向或尺寸已变化，请重新授权采集");
            if (latest == null || frameTimestampNanos <= after || SystemClock.elapsedRealtime() - capturedAt > 1500) {
                demand.request();
                throw new IllegalStateException("暂无新鲜画面，请重新授权屏幕采集");
            }
            return latest.copy(Bitmap.Config.ARGB_8888, false);
        }
    }

    void saveEvidence(Bitmap frame, String label) throws Exception {
        File directory = new File(getFilesDir(), "evidence");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("截图目录创建失败");
        // Keep only the latest diagnostic, not a growing collection of account screenshots.
        try (FileOutputStream output = new FileOutputStream(new File(directory, label + ".png"))) {
            frame.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }

    @Override public void onDestroy() {
        closing = true;
        instance = null;
        if (GameAccessibilityService.instance != null) GameAccessibilityService.instance.stop("屏幕采集已结束");
        if (display != null) display.release();
        if (reader != null) reader.close();
        if (projection != null) projection.stop();
        if (thread != null) thread.quitSafely();
        synchronized (lock) { if (latest != null) { latest.recycle(); latest = null; } }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
