package dev.goshadows.createsablenrails.interaction;

import java.util.Locale;

/**
 * Per-category policy deciding whether player interaction with a moved Create contraption is
 * allowed to reach the blocks inside that contraption.
 *
 * <p>The policy is only consulted on the logical server (see {@link ContraptionInteractionPolicy}),
 * so the server is the single source of truth. A rejected interaction is ignored by the server
 * exactly like any other server-side permission check, so a modified client cannot bypass it.</p>
 *
 * <p>This enum deliberately has no Minecraft imports so {@link InteractiveDecision} and this class
 * can be unit tested without a game classpath.</p>
 */
public enum InteractiveHandling {
    /** Marker blocks are ignored; the contraption is always interactive. */
    ALWAYS,
    /** Interactive unless the contraption contains an {@code interact_me_not} marker. */
    WITHOUT_STICKER,
    /** Interactive only when the contraption contains an {@code interact_me} marker. */
    WITH_STICKER,
    /** Marker blocks are ignored; the contraption is never interactive. */
    NEVER;

    /**
     * Resolves the effective decision for one contraption.
     *
     * @param hasInteractMe    whether the contraption contains at least one {@code interact_me} marker
     * @param hasInteractMeNot whether the contraption contains at least one {@code interact_me_not} marker
     * @return {@code true} when players may interact with blocks inside the contraption
     */
    public boolean allowsInteraction(final boolean hasInteractMe, final boolean hasInteractMeNot) {
        return switch (this) {
            case ALWAYS -> true;
            case WITHOUT_STICKER -> !hasInteractMeNot;
            case WITH_STICKER -> hasInteractMe;
            case NEVER -> false;
        };
    }

    /** Stable config-file identifier, matching NeoForge's enum serialisation. */
    public String configName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
