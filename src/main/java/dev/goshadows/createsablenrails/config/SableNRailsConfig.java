package dev.goshadows.createsablenrails.config;

import dev.goshadows.createsablenrails.interaction.ContraptionCategory;
import dev.goshadows.createsablenrails.interaction.InteractiveHandling;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Server configuration for contraption interactivity.
 *
 * <p>This is a {@link net.neoforged.fml.config.ModConfig.Type#SERVER} config: it lives in the
 * world's {@code serverconfig} directory and NeoForge synchronises it to connecting clients.
 * Gameplay decisions are nevertheless made on the server only, so a modified client cannot grant
 * itself interaction rights.</p>
 */
public final class SableNRailsConfig {

    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    static {
        final Pair<Common, ModConfigSpec> common = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();

        final Pair<Server, ModConfigSpec> server = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
    }

    private SableNRailsConfig() {
    }

    /**
     * Options that must be identical on client and server.
     *
     * <p>Kept deliberately empty for now: every interactivity option is server-authoritative, and
     * NeoForge ignores an empty spec ({@code ModContainer#registerConfig} logs a debug line), so this
     * file is reserved rather than used. It exists so the socket for a future client-visible option
     * (for example a HUD hint) does not require re-registering configs.</p>
     */
    public static final class Common {
        private Common(final ModConfigSpec.Builder builder) {
            // Intentionally empty; see the class comment.
        }
    }

    /** Per-contraption-category interactivity policy, evaluated on the logical server only. */
    public static final class Server {
        private final ModConfigSpec.EnumValue<InteractiveHandling> trainHandling;
        private final ModConfigSpec.EnumValue<InteractiveHandling> translatingHandling;
        private final ModConfigSpec.EnumValue<InteractiveHandling> bearingHandling;
        private final ModConfigSpec.EnumValue<InteractiveHandling> clockworkHandling;
        private final ModConfigSpec.EnumValue<InteractiveHandling> mountedHandling;
        private final ModConfigSpec.EnumValue<InteractiveHandling> otherHandling;

        private Server(final ModConfigSpec.Builder builder) {
            builder.comment(
                            "How player interaction with moved Create contraptions is handled.",
                            "ALWAYS: ignore marker blocks, always interactive.",
                            "WITHOUT_STICKER: interactive unless the contraption contains an interact_me_not marker.",
                            "WITH_STICKER: interactive only when the contraption contains an interact_me marker.",
                            "NEVER: ignore marker blocks, never interactive.",
                            "The server configuration always wins over marker blocks in ALWAYS and NEVER mode.")
                    .push("interactivity");

            this.trainHandling = builder
                    .comment("Policy for train carriages.")
                    .defineEnum("trainHandling", InteractiveHandling.WITH_STICKER);
            this.translatingHandling = builder
                    .comment("Policy for translating contraptions (piston, gantry, pulley).")
                    .defineEnum("translatingHandling", InteractiveHandling.WITH_STICKER);
            this.bearingHandling = builder
                    .comment("Policy for bearing contraptions, including stabilized bearings.")
                    .defineEnum("bearingHandling", InteractiveHandling.WITH_STICKER);
            this.clockworkHandling = builder
                    .comment("Policy for clockwork contraptions (clockwork bearings).")
                    .defineEnum("clockworkHandling", InteractiveHandling.WITH_STICKER);
            this.mountedHandling = builder
                    .comment("Policy for mounted contraptions (minecart contraptions).")
                    .defineEnum("mountedHandling", InteractiveHandling.WITH_STICKER);
            this.otherHandling = builder
                    .comment("Policy for contraption types this addon does not recognise. Defaults to NEVER,",
                            "so contraptions added by other mods keep their previous, non-interactive behaviour.")
                    .defineEnum("otherHandling", InteractiveHandling.NEVER);

            builder.pop();
        }

        /**
         * Reads a value, falling back to a safe default when the server config has not been loaded yet.
         *
         * <p>{@code ConfigValue#get()} throws when the owning spec is not loaded, which can happen if
         * the addon is queried before server configs are read. Callers must never crash because of a
         * lifecycle ordering issue.</p>
         */
        private InteractiveHandling valueOr(final ModConfigSpec.EnumValue<InteractiveHandling> value,
                                            final InteractiveHandling fallback) {
            if (!SERVER_SPEC.isLoaded()) {
                return fallback;
            }
            final InteractiveHandling loaded = value.get();
            return loaded == null ? fallback : loaded;
        }

        public InteractiveHandling forCategory(final ContraptionCategory category) {
            return switch (category) {
                case TRAIN -> valueOr(this.trainHandling, InteractiveHandling.WITH_STICKER);
                case TRANSLATING -> valueOr(this.translatingHandling, InteractiveHandling.WITH_STICKER);
                case BEARING -> valueOr(this.bearingHandling, InteractiveHandling.WITH_STICKER);
                case CLOCKWORK -> valueOr(this.clockworkHandling, InteractiveHandling.WITH_STICKER);
                case MOUNTED -> valueOr(this.mountedHandling, InteractiveHandling.WITH_STICKER);
                case OTHER -> valueOr(this.otherHandling, InteractiveHandling.NEVER);
            };
        }
    }
}
