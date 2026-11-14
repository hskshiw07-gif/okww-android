package io.github.okww.weekly;
import android.content.Context;
import java.io.File;
final class RedeemStore {
    static DailyStaminaLedger.Store codes(Context context){return MultiAccountFiles.store(new File(context.getFilesDir(),"redeem-codes.txt"));}
    static DailyStaminaLedger.Store ledger(Context context){return MultiAccountFiles.store(new File(context.getFilesDir(),"redeem-ledger.txt"));}
    static DailyStaminaLedger.Store developmentLedger(Context context){return MultiAccountFiles.store(new File(context.getFilesDir(),"redeem-development-ledger.txt"));}
    static void saveCodes(Context context,String original,RedeemCodes next)throws Exception{
        DailyStaminaLedger.Store file=codes(context);
        if(!RedeemCodes.parse(file.read()).save().equals(original))throw new IllegalStateException("兑换码缓存已变化，请重新打开");
        file.write(next.save());if(!next.save().equals(file.read()))throw new java.io.IOException("兑换码缓存写后核验失败");
    }
}
