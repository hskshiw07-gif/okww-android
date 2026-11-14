package io.github.okww.weekly;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class MultiAccountFiles {
    static DailyStaminaLedger.Store plan(Context context){return store(new File(context.getFilesDir(),"multi-account-plan.txt"));}
    static DailyStaminaLedger.Store progress(Context context){return store(new File(context.getFilesDir(),"multi-account-progress.txt"));}
    static DailyStaminaLedger.Store store(File path){
        AtomicFile file=new AtomicFile(path);
        return new DailyStaminaLedger.Store(){
            public String read()throws IOException{
                try{return new String(file.readFully(),StandardCharsets.UTF_8);}
                catch(FileNotFoundException absent){
                    if(path.exists()||new File(path+".bak").exists())throw absent;
                    return null;
                }
            }
            public void write(String value)throws IOException{
                FileOutputStream out=file.startWrite();
                try{out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();file.finishWrite(out);}
                catch(IOException|RuntimeException failure){file.failWrite(out);throw failure;}
            }
        };
    }
}
