package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static io.github.okww.weekly.GardenRules.*;
import static org.junit.Assert.*;

/** Completion-status geometry only; no claim that synthetic OCR is a live mail test. */
public class DailyRewardsTest {
    @Test public void recordedTerminalIconGlyphStillRequiresAllEnvelopeContext(){
        Screen terminal=new Screen(new ArrayList<>(Arrays.asList(t("必终端",.09f,.055f),t("索拉指南",.728f,.55f),
                t("数据坞",.527f,.55f),t("联觉经验",.1f,.78f))),new int[0],2400f/1080);
        DailyRewards r=new DailyRewards();r.start();r.applied(new Action(Kind.BACK,"daily-rewards-to-mail","",0,0));
        assertEquals("daily-open-mail",r.next(terminal,true).scene);
        for(int i=0;i<4;i++){Screen missing=new Screen(new ArrayList<>(terminal.words),new int[0],2400f/1080);missing.words.remove(i);
            assertNotEquals("daily-open-mail",r.next(missing,true).scene);}
    }
    private Text t(String value, float x, float y) {
        return new Text(value, x - .01f, y - .01f, x + .01f, y + .01f);
    }
    private Screen mail(Text... extra) {
        ArrayList<Text> words = new ArrayList<>(Arrays.asList(t("邮件", .08f, .06f), t("全部领取", .14f, .91f)));
        words.addAll(Arrays.asList(extra));
        return new Screen(words, new int[3], 2400f / 1080);
    }
    private Action next(DailyRewards rewards, Screen screen) {
        rewards.frameStarted();
        return rewards.next(screen, false);
    }
    private DailyRewards pendingMail() {
        DailyRewards rewards = new DailyRewards();
        rewards.start();
        rewards.frameStarted();
        rewards.applied(rewards.activity(new Screen(new ArrayList<>(), new int[3]), 100));
        Action claim = next(rewards, mail());
        assertEquals("daily-claim-mail", claim.scene);
        rewards.applied(claim);
        return rewards;
    }
    private void unverified(DailyRewards rewards) {
        assertNotEquals("verified", rewards.status().get("mail"));
    }

    @Test public void recordedTopToastNeedsTwoConsecutiveFrames() {
        for (String value : new String[]{"全部邮件的附件已经领取完毕", "全部邮件的附件己经领取完毕"}) {
            DailyRewards rewards = pendingMail();
            // Existing dev10/dev11 toast rectangle; no private mail text or UID is copied.
            Screen complete = mail(new Text(value, 1015f / 2400, 194f / 1080, 1390f / 2400, 230f / 1080));
            assertEquals(Kind.WAIT, next(rewards, complete).kind);
            unverified(rewards);
            assertEquals("daily-mail-back", next(rewards, complete).scene);
            assertEquals("verified", rewards.status().get("mail"));
        }
    }
    @Test public void recordedToastFinalGlyphAliasRequiresOwnClaimTwoFramesAndTopRoi(){
        String value="e孙全部邮件的附件己经领取完生";
        DailyRewards r=pendingMail();Screen complete=mail(new Text(value,1015f/2400,194f/1080,1390f/2400,230f/1080));
        assertEquals(Kind.WAIT,next(r,complete).kind);assertEquals("daily-mail-back",next(r,complete).scene);
        assertEquals("verified",r.status().get("mail"));
        DailyRewards fresh=new DailyRewards();fresh.start();fresh.applied(new Action(Kind.BACK,"daily-rewards-to-mail","",0,0));
        assertEquals("daily-claim-mail",next(fresh,complete).scene);assertNotEquals("verified",fresh.status().get("mail"));
        DailyRewards body=pendingMail();assertEquals(Kind.WAIT,next(body,mail(t(value,.5f,.45f))).kind);
        assertNotEquals("verified",body.status().get("mail"));
    }

    @Test public void liveToastMergedWithSenderStillRequiresTwoTopFrames() {
        DailyRewards rewards = pendingMail();
        Screen complete = mail(new Text("2秧建部件的附件己经领取完毕",
                931f / 2400, 191f / 1080, 1400f / 2400, 240f / 1080));
        assertEquals(Kind.WAIT, next(rewards, complete).kind);
        unverified(rewards);
        assertEquals("daily-mail-back", next(rewards, complete).scene);
        assertEquals("verified", rewards.status().get("mail"));
    }

    @Test public void recordedMailClaimAllTypoDoesNotDepartEarly() {
        DailyRewards rewards = new DailyRewards(); rewards.start();
        rewards.applied(new Action(Kind.BACK,"daily-rewards-to-mail","",0,0));
        Screen typo = new Screen(new ArrayList<>(Arrays.asList(t("邮件",.08f,.06f),
                new Text("全都领取",278f/2400,967f/1080,394f/2400,998f/1080))),new int[3],2400f/1080);
        Action claim = next(rewards,typo);
        assertEquals("daily-claim-mail",claim.scene);
        rewards.applied(claim);
        assertEquals(Kind.WAIT,next(rewards,typo).kind);
    }

    @Test public void tolerantToastPrefixDoesNotAcceptBodyOrIncompleteStatus() {
        for (String value : new String[]{"2秧建部件的附件己经领取完毕", "附件已经领取完毕"}) {
            DailyRewards rewards = pendingMail();
            Screen body = mail(t(value, .50f, .45f));
            for (int i = 0; i < 3; i++) assertEquals(Kind.WAIT, next(rewards, body).kind);
            unverified(rewards);
        }
        for (String value : new String[]{"附件尚未领取完毕", "附件已经领取", "附件已经领取完毕了吗",
                "abcdefghijklmnop附件已经领取完毕"}) {
            DailyRewards rewards = pendingMail();
            Screen wrong = mail(t(value, .50f, .20f));
            for (int i = 0; i < 3; i++) assertEquals(Kind.WAIT, next(rewards, wrong).kind);
            unverified(rewards);
        }
    }

    @Test public void mergedToastNeedsOwnedClaimAndCannotSurviveMissingFrame() {
        DailyRewards rewards = new DailyRewards(); rewards.start();
        rewards.applied(new Action(Kind.BACK,"daily-rewards-to-mail","",0,0));
        Screen complete = mail(t("2秧建部件的附件己经领取完毕",.50f,.20f));
        assertEquals("daily-claim-mail",next(rewards,complete).scene);
        assertEquals("daily-claim-mail",next(rewards,complete).scene);
        unverified(rewards);
        rewards.applied(next(rewards,mail()));
        assertEquals(Kind.WAIT,next(rewards,complete).kind);
        assertEquals(Kind.WAIT,next(rewards,mail()).kind);
        assertEquals(Kind.WAIT,next(rewards,complete).kind);
        unverified(rewards);
        assertEquals("daily-mail-back",next(rewards,complete).scene);
    }

    @Test public void sameStatusWordsInBodyNeverVerifyOrRepeatClaim() {
        for (String value : new String[]{"暂无邮件", "没有邮件", "暂无可领取附件", "暂无可领取的附件",
                "全部附件已领取", "全部邮件已领取", "全部邮件的附件已经领取完毕"}) {
            DailyRewards rewards = pendingMail();
            Screen body = mail(t(value, .60f, .45f));
            for (int frame = 0; frame < 3; frame++) {
                assertEquals(Kind.WAIT, next(rewards, body).kind);
                unverified(rewards);
            }
        }
    }

    @Test public void duplicateTopStatusIsAmbiguousNotCompletion() {
        DailyRewards rewards = pendingMail();
        Screen duplicate = mail(t("全部附件已领取", .45f, .20f), t("暂无邮件", .55f, .20f));
        assertThrows(IllegalStateException.class, () -> next(rewards, duplicate));
        unverified(rewards);
    }

    @Test public void missingTopStatusBreaksTheTwoFrameSequence() {
        DailyRewards rewards = pendingMail();
        Screen complete = mail(t("全部邮件已领取", .50f, .20f));
        assertEquals(Kind.WAIT, next(rewards, complete).kind);
        assertEquals(Kind.WAIT, next(rewards, mail(t("全部邮件已领取", .60f, .45f))).kind);
        assertEquals(Kind.WAIT, next(rewards, complete).kind);
        unverified(rewards);
        assertEquals("daily-mail-back", next(rewards, complete).scene);
        assertEquals("verified", rewards.status().get("mail"));
    }

    @Test public void existingShortStatusOnlyCountsInTopToastRegion() {
        for (String value : new String[]{"暂无邮件", "没有邮件", "暂无可领取附件", "暂无可领取的附件",
                "全部附件已领取", "全部邮件已领取"}) {
            DailyRewards rewards = pendingMail();
            Screen complete = mail(t(value, .50f, .20f));
            assertEquals(Kind.WAIT, next(rewards, complete).kind);
            unverified(rewards);
            assertEquals("daily-mail-back", next(rewards, complete).scene);
            assertEquals("verified", rewards.status().get("mail"));
        }
    }

    @Test public void nearbyMailTitleOrBodyEdgeCannotBeCompletionToast() {
        for (float[] position : new float[][]{{.50f, .14f}, {.50f, .25f}, {.34f, .20f}, {.66f, .20f}}) {
            DailyRewards rewards = pendingMail();
            Screen wrong = mail(t("全部附件已领取", position[0], position[1]));
            assertEquals(Kind.WAIT, next(rewards, wrong).kind);
            assertEquals(Kind.WAIT, next(rewards, wrong).kind);
            unverified(rewards);
        }
    }
}
