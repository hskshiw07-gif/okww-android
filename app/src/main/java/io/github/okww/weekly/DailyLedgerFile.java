package io.github.okww.weekly;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Called only by the accessibility service's single worker, never by a second process. */
final class DailyLedgerFile implements DailyStaminaLedger.Store {
    private final AtomicFile file;
    DailyLedgerFile(File directory, String uid) throws IOException {
        if (uid == null || !uid.matches("[0-9]{9}")) throw new IllegalArgumentException("账号标记异常");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("波片账本目录创建失败");
        file = new AtomicFile(new File(directory, "stamina-" + uid + ".txt"));
    }
    @Override public String read() throws IOException {
        try { return new String(file.readFully(), StandardCharsets.UTF_8); }
        catch (java.io.FileNotFoundException e) {
            if (file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists()) throw e;
            return null;
        }
    }
    @Override public void write(String value) throws IOException {
        FileOutputStream out = file.startWrite();
        try {
            out.write(value.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
            file.finishWrite(out);
        } catch (IOException | RuntimeException error) { file.failWrite(out); throw error; }
    }
}
