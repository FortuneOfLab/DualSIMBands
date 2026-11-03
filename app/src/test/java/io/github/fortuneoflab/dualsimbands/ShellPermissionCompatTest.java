package io.github.fortuneoflab.dualsimbands;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShellPermissionCompatTest {
    public static class EmptyOnly {
        boolean privileged = true;
        String[] permissions;
        int uid = -1;
        int emptyCalls;
        public void startDelegateShellPermissionIdentity(int value, String[] names) {
            uid = value; permissions = names; emptyCalls++;
            privileged = names == null || names.length != 0;
        }
        void verify() {
            if (privileged) throw new SecurityException("still delegated");
        }
    }
    public static class Legacy extends EmptyOnly {
        int stops;
        public void stopDelegateShellPermissionIdentity() { stops++; privileged = false; }
    }
    public static class WithUid extends Legacy {
        int stoppedUid = -1;
        public void stopDelegateShellPermissionIdentity(int uid) { stoppedUid = uid; privileged = false; }
    }
    public static class BrokenLegacy extends EmptyOnly {
        public void stopDelegateShellPermissionIdentity() { throw new SecurityException("not supported"); }
    }
    public static class BrokenUid extends Legacy {
        public void stopDelegateShellPermissionIdentity(int uid) { throw new SecurityException("not supported"); }
    }
    public static class IneffectiveLegacy extends EmptyOnly {
        public void stopDelegateShellPermissionIdentity() { /* OS did not revoke the grant. */ }
    }
    public static class IneffectiveEmpty extends EmptyOnly {
        @Override public void startDelegateShellPermissionIdentity(int uid, String[] names) {
            privileged = true;
        }
    }

    @Test public void legacyReleaseWorksWithoutReplacement() throws Exception {
        Legacy m = new Legacy();
        assertEquals("従来方式", ShellPermissionCompat.release(m, Legacy.class, 123, m::verify));
        assertEquals(1, m.stops); assertEquals(0, m.emptyCalls);
    }
    @Test public void uidVariantIsPreferredAndUsesTheAppUid() throws Exception {
        WithUid m = new WithUid();
        assertEquals("UID指定", ShellPermissionCompat.release(m, WithUid.class, 123, m::verify));
        assertEquals(123, m.stoppedUid); assertEquals(0, m.stops); assertEquals(0, m.emptyCalls);
    }
    @Test public void missingStopUsesAnEmptyNonNullListForTheSameUid() throws Exception {
        EmptyOnly m = new EmptyOnly();
        assertEquals("互換方式", ShellPermissionCompat.release(m, EmptyOnly.class, 123, m::verify));
        assertEquals(123, m.uid); assertNotNull(m.permissions); assertEquals(0, m.permissions.length);
        assertFalse(m.privileged);
    }
    @Test public void failedLegacyCallCanRecoverUsingEmptyPermissions() throws Exception {
        BrokenLegacy m = new BrokenLegacy();
        assertEquals("互換方式", ShellPermissionCompat.release(m, BrokenLegacy.class, 123, m::verify));
        assertFalse(m.privileged);
    }
    @Test public void failedUidVariantCanRecoverUsingLegacy() throws Exception {
        BrokenUid m = new BrokenUid();
        assertEquals("従来方式", ShellPermissionCompat.release(m, BrokenUid.class, 123, m::verify));
        assertEquals(1, m.stops);
    }
    @Test public void successfulCallWithoutEffectiveReleaseIsNotEnough() throws Exception {
        IneffectiveLegacy m = new IneffectiveLegacy();
        assertEquals("互換方式", ShellPermissionCompat.release(m, IneffectiveLegacy.class, 123, m::verify));
        assertEquals(1, m.emptyCalls); assertFalse(m.privileged);
    }
    @Test public void ineffectiveFallbackRemainsAnError() throws Exception {
        IneffectiveEmpty m = new IneffectiveEmpty();
        try {
            ShellPermissionCompat.release(m, IneffectiveEmpty.class, 123, m::verify);
            fail("must not report success when permissions remain granted");
        } catch (IllegalStateException expected) { assertEquals(3, expected.getSuppressed().length); }
    }
    @Test public void missingAllMethodsRemainsAnError() throws Exception {
        try {
            ShellPermissionCompat.release(new Object(), Object.class, 123, () -> {});
            fail("must not hide an unsupported OS");
        } catch (IllegalStateException expected) { assertEquals(3, expected.getSuppressed().length); }
    }
}
