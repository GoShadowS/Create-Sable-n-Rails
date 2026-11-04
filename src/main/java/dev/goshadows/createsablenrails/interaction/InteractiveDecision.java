package dev.goshadows.createsablenrails.interaction;

/**
 * Pure decision logic for the interactivity policy, with no Minecraft dependencies so it can be
 * unit tested on a plain classpath.
 *
 * <h2>Precedence rules (documented contract)</h2>
 * <ul>
 *     <li>{@link InteractiveHandling#ALWAYS} and {@link InteractiveHandling#NEVER} ignore markers
 *     completely. The server configuration wins over both marker blocks.</li>
 *     <li>{@link InteractiveHandling#WITHOUT_STICKER} is a deny-list: a single
 *     {@code interact_me_not} vetoes interaction regardless of any {@code interact_me} marker.</li>
 *     <li>{@link InteractiveHandling#WITH_STICKER} is an allow-list: a single {@code interact_me}
 *     permits interaction regardless of any {@code interact_me_not} marker.</li>
 *     <li>When both markers are present, the marker that matches the active mode wins; the other
 *     marker has no effect in that mode.</li>
 * </ul>
 */
public final class InteractiveDecision {

    private InteractiveDecision() {
    }

    /**
     * @param handling         the server-configured handling for the contraption's category
     * @param hasInteractMe    whether an {@code interact_me} marker exists in the contraption
     * @param hasInteractMeNot whether an {@code interact_me_not} marker exists in the contraption
     * @return whether interaction with blocks inside the contraption is allowed
     */
    public static boolean allowsInteraction(final InteractiveHandling handling, final boolean hasInteractMe,
                                            final boolean hasInteractMeNot) {
        if (handling == null) {
            // A missing value means the config was not read; do not silently restrict players.
            return true;
        }
        return handling.allowsInteraction(hasInteractMe, hasInteractMeNot);
    }
}
