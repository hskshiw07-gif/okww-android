package io.github.okww.weekly;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

public class RedeemDevelopmentTest {
    @Test public void anyUnknownAccountPendingBlocksBeforeRestartCanCloseEvidence()throws Exception{
        RedeemLedger real=new RedeemLedger(new RedeemDataTest.Memory()),dev=new RedeemLedger(new RedeemDataTest.Memory());
        RedeemDevelopment.requireNoPending(real,dev);
        real.submit("222222222","REALCODE",1);
        assertThrows(IllegalStateException.class,()->RedeemDevelopment.requireNoPending(real,dev));
        real.result("222222222","REALCODE",RedeemLedger.Status.ALREADY_USED,2,"verified");
        dev.submit("111111111","WWTEST1",3);
        assertThrows(IllegalStateException.class,()->RedeemDevelopment.requireNoPending(real,dev));
        dev.result("111111111","WWTEST1",RedeemLedger.Status.INVALID,4,"verified");
        RedeemDevelopment.requireNoPending(real,dev);
    }
    @Test public void syntheticCodesNeverResetRealRecordsOrPending()throws Exception{
        RedeemCodes catalog=new RedeemCodes();catalog.manual("MINGCHAO666");String saved=catalog.save();
        RedeemDataTest.Memory real=new RedeemDataTest.Memory(),dev=new RedeemDataTest.Memory();
        RedeemLedger live=new RedeemLedger(real),tests=new RedeemLedger(dev);
            live.submit("111111111","MINGCHAO666",10);live.result("111111111","MINGCHAO666",RedeemLedger.Status.ALREADY_USED,11,"real");
            String realBefore=real.data;
            String first=RedeemDevelopment.testCode(100,catalog,tests),second=RedeemDevelopment.testCode(101,catalog,tests);
            assertNotEquals(first,second);assertTrue(first.startsWith("WWTEST"));assertEquals(saved,catalog.save());
            tests.submit("111111111",first,12);
            assertThrows(IllegalStateException.class,()->tests.requireNoPending("111111111"));
            assertThrows(IllegalStateException.class,()->RedeemDevelopment.testCode(100,catalog,tests));
            tests.result("111111111",first,RedeemLedger.Status.INVALID,13,"synthetic invalid, no reward");
            tests.requireNoPending("111111111");assertEquals(realBefore,real.data);
            catalog.manual(second);assertThrows(IllegalStateException.class,()->RedeemDevelopment.testCode(101,catalog,tests));
    }
    private Text t(String value,float x,float y){return new Text(value,x-.01f,y-.01f,x+.01f,y+.01f);}
    private Screen s(Text...words){return new Screen(new ArrayList<>(Arrays.asList(words)),new int[0],2400f/1080);}
    @Test public void restartOnlyBacksOutOfKnownPagesAndNeverSubmits(){
        assertEquals(Kind.BACK,RedeemDevelopment.restart(s(t("邮件",.08f,.06f))).kind);
        assertEquals(Kind.WAIT,RedeemDevelopment.restart(s(t("邮件内容",.5f,.5f))).kind);
        assertEquals(Kind.BACK,RedeemDevelopment.restart(s(t("兑换码",.5f,.2f),t("取消",.35f,.65f),t("确认",.65f,.65f))).kind);
        assertEquals(Kind.DONE,RedeemDevelopment.restart(s(t("Lv.90",.58f,.94f),t("特征码:111111111",.9f,.98f))).kind);
        assertEquals(Kind.STOP,RedeemDevelopment.restart(s(t("确认购买",.5f,.5f))).kind);
        assertEquals(Kind.WAIT,RedeemDevelopment.restart(s()).kind);
    }
}
