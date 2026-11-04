package dev.goshadows.createsablenrails.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive truth-table test for the interactivity policy.
 *
 * <p>These tests only touch classes without Minecraft imports, so they run on a plain JUnit
 * classpath and do not require the NeoForge/Minecraft test harness. Every combination of the four
 * policies and the four marker states is asserted explicitly, which is what makes the documented
 * precedence rules in {@link InteractiveDecision} verifiable rather than aspirational.</p>
 */
class InteractiveDecisionTest {

    // ------------------------------------------------------------------ ALWAYS
    @Test
    void alwaysIgnoresBothMarkers() {
        assertTrue(InteractiveDecision.allowsInteraction(InteractiveHandling.ALWAYS, false, false));
        assertTrue(InteractiveDecision.allowsInteraction(InteractiveHandling.ALWAYS, true, false));
        assertTrue(InteractiveDecision.allowsInteraction(InteractiveHandling.ALWAYS, false, true));
        assertTrue(InteractiveDecision.allowsInteraction(InteractiveHandling.ALWAYS, true, true));
    }

    // ------------------------------------------------------------------ NEVER
    @Test
    void neverIgnoresBothMarkers() {
        assertFalse(InteractiveDecision.allowsInteraction(InteractiveHandling.NEVER, false, false));
        assertFalse(InteractiveDecision.allowsInteraction(InteractiveHandling.NEVER, true, false));
        assertFalse(InteractiveDecision.allowsInteraction(InteractiveHandling.NEVER, false, true));
        assertFalse(InteractiveDecision.allowsInteraction(InteractiveHandling.NEVER, true, true));
    }

    // ---------------------------------------------------------- WITHOUT_STICKER
    @Test
    void withoutStickerIsADenyList() {
        // No deny marker -> interactive, regardless of an allow marker.
        assertTrue(InteractiveDecision.allowsInteraction(InteractiveHandling.WITHOUT_STICKER, false, false));
        assertTrue(InteractiveDecision.allowsInteraction(InteractiveHandling.WITHOUT_STICKER, true, false));
        // A single deny marker vetoes, even when an allow marker is also present.
        assertFalse(InteractiveDecision.allowsInteraction(InteractiveHandling.WITHOUT_STICKER, false, true));
        assertFalse(InteractiveDecision.allowsInteraction(InteractiveHandling.WITHOUT_STICKER, true, true));
    }

    // ------------------------------------------------------------- WITH_STICKER
    @Test
    void withStickerIsAnAllowList() {
        // No allow marker -> not interactive, even when no deny marker is present.
        assertFalse(InteractiveDecision.allowsInteraction(InteractiveHandling.WITH_STICKER, false, false));
        assertFalse(InteractiveDecision.allowsInteraction(InteractiveHandling.WITH_STICKER, false, true));
        // A single allow marker permits, even when a deny marker is also present.
        assertTrue(InteractiveDecision.allowsInteraction(InteractiveHandling.WITH_STICKER, true, false));
        assertTrue(InteractiveDecision.allowsInteraction(InteractiveHandling.WITH_STICKER, true, true));
    }

    // ------------------------------------------------------------- defensive
    @Test
    void nullHandlingDoesNotRestrictPlayers() {
        assertTrue(InteractiveDecision.allowsInteraction(null, false, false));
    }

    @Test
    void enumPolicyMatchesDecisionHelper() {
        for (final InteractiveHandling handling : InteractiveHandling.values()) {
            for (final boolean me : new boolean[]{false, true}) {
                for (final boolean meNot : new boolean[]{false, true}) {
                    final boolean viaEnum = handling.allowsInteraction(me, meNot);
                    final boolean viaHelper = InteractiveDecision.allowsInteraction(handling, me, meNot);
                    assertTrue(viaEnum == viaHelper,
                            "policy " + handling + " disagrees for me=" + me + " meNot=" + meNot);
                }
            }
        }
    }

    @Test
    void configNamesAreStableAndLowerCase() {
        assertTrue("with_sticker".equals(InteractiveHandling.WITH_STICKER.configName()));
        assertTrue("without_sticker".equals(InteractiveHandling.WITHOUT_STICKER.configName()));
        assertTrue("always".equals(InteractiveHandling.ALWAYS.configName()));
        assertTrue("never".equals(InteractiveHandling.NEVER.configName()));
    }
}
