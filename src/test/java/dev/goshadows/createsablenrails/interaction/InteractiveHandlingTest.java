package dev.goshadows.createsablenrails.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the enum-level truth table independently of {@link InteractiveDecision}. */
class InteractiveHandlingTest {

    @Test
    void denyWinsInWithoutStickerMode() {
        assertFalse(InteractiveHandling.WITHOUT_STICKER.allowsInteraction(true, true));
    }

    @Test
    void allowWinsInWithStickerMode() {
        assertTrue(InteractiveHandling.WITH_STICKER.allowsInteraction(true, true));
    }

    @Test
    void defaultModeRequiresAMarker() {
        assertFalse(InteractiveHandling.WITH_STICKER.allowsInteraction(false, false));
    }

    @Test
    void allPoliciesAreCoveredByTheSwitch() {
        // If a new constant is added without updating the decision logic, this fails.
        assertEquals(4, InteractiveHandling.values().length);
        for (final InteractiveHandling handling : InteractiveHandling.values()) {
            // Must not throw: a switch over a new constant would throw IncompatibleClassChangeError.
            handling.allowsInteraction(false, false);
        }
    }
}
