package dev.goshadows.createsablenrails.interaction;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.TranslatingContraption;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.ClockworkContraption;
import com.simibubi.create.content.contraptions.bearing.StabilizedContraption;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import com.simibubi.create.content.trains.entity.CarriageContraption;

/**
 * Classification of a Create contraption, used to select the matching server configuration value.
 *
 * <p>The categories mirror the contraption types that exist in Create 6.0.10 for Minecraft 1.21.1;
 * see {@code UPSTREAM_RESEARCH.md} for the per-class evidence. Unknown contraptions contributed by
 * other addons fall back to {@link #OTHER} so that a strict default can be applied to them.</p>
 *
 * <p>Ordering matters: {@link ClockworkContraption} is checked before {@link BearingContraption}
 * even though it does not currently extend it, so the classification stays correct if upstream
 * ever changes the hierarchy in a compatible way.</p>
 */
public enum ContraptionCategory {
    TRAIN,
    TRANSLATING,
    BEARING,
    CLOCKWORK,
    MOUNTED,
    OTHER;

    public static ContraptionCategory of(final Contraption contraption) {
        if (contraption == null) {
            return OTHER;
        }
        if (contraption instanceof CarriageContraption) {
            return TRAIN;
        }
        if (contraption instanceof ClockworkContraption) {
            return CLOCKWORK;
        }
        if (contraption instanceof BearingContraption) {
            return BEARING;
        }
        if (contraption instanceof StabilizedContraption) {
            // Stabilized bearings are bearing-driven contraptions without rotation of their own.
            return BEARING;
        }
        if (contraption instanceof MountedContraption) {
            return MOUNTED;
        }
        if (contraption instanceof TranslatingContraption) {
            // Piston, gantry and pulley contraptions; TranslatingContraption itself is abstract.
            return TRANSLATING;
        }
        return OTHER;
    }
}
