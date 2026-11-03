package io.github.fortuneoflab.dualsimbands;

import org.junit.Test;
import static org.junit.Assert.*;

public class RestoreNoticeTest {
    @Test public void writtenButNotShownDoesNotClaimTheNameChanged() {
        String[] n = RestoreNotice.text(true, true);
        assertEquals("表示名の設定を戻しました", n[0]);
        assertTrue(n[1].contains("機内モード"));
    }
    @Test public void shownName() {
        assertEquals("表示名を戻しました", RestoreNotice.text(true, false)[0]);
    }
    @Test public void failureWinsOverDisplayState() {
        assertEquals("表示名を戻せませんでした", RestoreNotice.text(false, true)[0]);
    }
}
