package dev.goshadows.createsablenrails.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Truth table for the indicator's pure decision logic.
 *
 * <p>{@link ContraptionStatusIndicator} has no Minecraft imports, so this runs on a plain JUnit classpath
 * without the game. It asserts the precedence rules that the overlay relies on: F1 and spectator suppress
 * everything, no target means nothing, an allowed contraption means nothing, and only a denied contraption
 * produces the badge.</p>
 */
class ContraptionStatusIndicatorTest {

    @Test
    void lockedContraptionShowsTheBadge() {
        assertEquals(ContraptionStatusIndicator.Status.LOCKED,
                ContraptionStatusIndicator.resolve(false, false, true, false));
    }

    @Test
    void interactiveContraptionShowsNothing() {
        assertEquals(ContraptionStatusIndicator.Status.NONE,
                ContraptionStatusIndicator.resolve(false, false, true, true));
    }

    @Test
    void noTargetShowsNothing() {
        assertEquals(ContraptionStatusIndicator.Status.NONE,
                ContraptionStatusIndicator.resolve(false, false, false, false));
        assertEquals(ContraptionStatusIndicator.Status.NONE,
                ContraptionStatusIndicator.resolve(false, false, false, true));
    }

    @Test
    void hiddenGuiWinsOverEverything() {
        assertEquals(ContraptionStatusIndicator.Status.NONE,
                ContraptionStatusIndicator.resolve(true, false, true, false));
        assertEquals(ContraptionStatusIndicator.Status.NONE,
                ContraptionStatusIndicator.resolve(true, true, true, false));
    }

    @Test
    void spectatorSuppressesTheBadge() {
        assertEquals(ContraptionStatusIndicator.Status.NONE,
                ContraptionStatusIndicator.resolve(false, true, true, false));
    }

    @Test
    void onlyLockedHasATranslationKey() {
        assertNull(ContraptionStatusIndicator.translationKey(ContraptionStatusIndicator.Status.NONE));
        assertEquals("create_sable_n_rails.hud.interactivity.locked",
                ContraptionStatusIndicator.translationKey(ContraptionStatusIndicator.Status.LOCKED));
    }

    @Test
    void lockedIsTheOnlyStatusThatDraws() {
        for (final ContraptionStatusIndicator.Status status : ContraptionStatusIndicator.Status.values()) {
            final boolean draws = ContraptionStatusIndicator.translationKey(status) != null;
            assertTrue(draws == (status == ContraptionStatusIndicator.Status.LOCKED),
                    "unexpected draw decision for " + status);
        }
    }
}
