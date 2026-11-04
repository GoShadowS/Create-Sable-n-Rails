package dev.goshadows.createsablenrails.client;

/**
 * Pure decision logic for the on-screen contraption status indicator, with no Minecraft imports so it can be
 * unit tested on a plain classpath.
 *
 * <h2>Why this exists</h2>
 * <p>When the policy denies interaction, a right click on a contraption does nothing at all: no swing, no
 * sound, no message. Before this indicator the only feedback was the absence of a reaction, which is
 * indistinguishable from a missed click. The indicator answers one question — "am I looking at a contraption
 * whose blocks are locked?" — and nothing else.</p>
 *
 * <h2>What it deliberately does not do</h2>
 * <ul>
 *     <li>It never shows anything for an allowed contraption. A marker block sitting on a machine is already
 *     the statement "this is interactive"; repeating it on screen would be noise on every frame.</li>
 *     <li>It does not evaluate the policy itself. The authoritative decision comes from
 *     {@code ContraptionInteractionPolicy}, which is also what the server uses, so the indicator cannot
 *     disagree with what actually happens to the click.</li>
 *     <li>It has no authority. It is a client-side hint drawn from the client's mirrored decision; the server
 *     still rejects the interaction on its own.</li>
 * </ul>
 */
public final class ContraptionStatusIndicator {

    /** What, if anything, the indicator should draw. */
    public enum Status {
        /** Nothing to draw. */
        NONE,
        /** The player is looking at a contraption whose blocks the policy locks. */
        LOCKED
    }

    /** Translation key for {@link Status#LOCKED}. One key, so the draw path cannot pick a stale string. */
    static final String LOCKED_KEY = "create_sable_n_rails.hud.interactivity.locked";

    private ContraptionStatusIndicator() {
    }

    /**
     * Resolves the indicator state for one frame.
     *
     * <p>Order matters and is the whole point of the helper: a hidden HUD or spectator mode suppresses
     * everything first, then the "is there a contraption under the crosshair at all" question, and only a
     * contraption that is <em>not</em> interactive produces {@link Status#LOCKED}.</p>
     *
     * @param guiHidden           {@code Minecraft#options.hideGui} — F1 must win over everything
     * @param spectator           the local player is in spectator mode
     * @param targetIsContraption a contraption is under the crosshair within reach and not occluded
     * @param interactive         the policy's decision for that contraption
     * @return what to draw, never {@code null}
     */
    public static Status resolve(final boolean guiHidden, final boolean spectator,
                                 final boolean targetIsContraption, final boolean interactive) {
        if (guiHidden || spectator) {
            return Status.NONE;
        }
        if (!targetIsContraption) {
            return Status.NONE;
        }
        // An interactive contraption needs no badge: the absence of a lock is the expected state.
        return interactive ? Status.NONE : Status.LOCKED;
    }

    /**
     * Translation key for a status.
     *
     * @param status the status to describe
     * @return the key to translate, or {@code null} for {@link Status#NONE}
     */
    public static String translationKey(final Status status) {
        return status == Status.LOCKED ? LOCKED_KEY : null;
    }
}
