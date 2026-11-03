package io.github.fortuneoflab.dualsimbands;
import org.junit.Test;
import static org.junit.Assert.*;
public class IdentityTest {
    @Test public void sameSimMatches() { assertTrue(Identity.matches(7, 10, "440", "50", 7, 10, "440", "50")); }
    @Test public void differentSubscriptionDoesNotMatchEvenOnSameCarrier() { assertFalse(Identity.matches(7, 10, "440", "50", 8, 10, "440", "50")); }
    @Test public void reusedIdDifferentCarrierDoesNotMatch() { assertFalse(Identity.matches(7, 10, "440", "50", 7, 20, "440", "50")); }
    @Test public void changedNetworkIdentityDoesNotMatch() { assertFalse(Identity.matches(7, 10, "440", "50", 7, 10, "440", "10")); }
    @Test public void invalidIdNeverMatches() { assertFalse(Identity.matches(-1, 10, "440", "50", -1, 10, "440", "50")); }
    /** Reference device: au and docomo both report carrier ID 2106, so MCC/MNC must also match. */
    @Test public void sameCarrierIdWithDifferentNetworkIsNotACandidate() {
        assertFalse(Identity.sameCarrier(2106, "440", "51", 2106, "440", "10"));
        assertTrue(Identity.sameCarrier(2106, "440", "51", 2106, "440", "51"));
    }
    @Test public void changedSubscriptionIdIsACandidateButNotAMatch() {
        assertFalse(Identity.matches(2, 2106, "440", "51", 7, 2106, "440", "51"));
        assertTrue(Identity.sameCarrier(2106, "440", "51", 2106, "440", "51"));
    }
}
