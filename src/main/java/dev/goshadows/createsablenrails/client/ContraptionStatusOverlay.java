package dev.goshadows.createsablenrails.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import dev.goshadows.createsablenrails.interaction.ContraptionInteractionPolicy;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.ref.WeakReference;

/**
 * Draws a single line of text when the player's crosshair is on a contraption whose blocks are locked by the
 * interactivity policy.
 *
 * <h2>Why an overlay and not an outline</h2>
 * <p>An outline would have to be drawn in the world pass and would have to agree with Create's own selection
 * outline, which it does not own; a HUD line cannot conflict with any renderer and is trivially suppressed by
 * F1. The indicator answers "why did my click do nothing", which is a question the player asks while looking at
 * the object, so a line near the crosshair is sufficient.</p>
 *
 * <h2>Data source</h2>
 * <p>The target is resolved with {@link ContraptionTargetProbe} over Create's own per-level registry
 * ({@code ContraptionHandler.loadedContraptions}), and the decision comes from
 * {@link ContraptionInteractionPolicy} — the same method the interaction gate uses. The overlay therefore
 * cannot claim "locked" for a contraption the server would allow, and it adds no authority of its own.</p>
 *
 * <h2>Registration</h2>
 * <p>Registered from {@code ClientSetup} through {@code RegisterGuiLayersEvent}, which NeoForge fires on the
 * mod event bus on the physical client only. Nothing here runs on a dedicated server.</p>
 */
public class ContraptionStatusOverlay implements LayeredDraw.Layer {

    public static final ContraptionStatusOverlay INSTANCE = new ContraptionStatusOverlay();

    /** Distance above the hotbar where the line is drawn, in GUI pixels. */
    private static final int BOTTOM_OFFSET = 59;

    private ContraptionStatusOverlay() {
    }

    @Override
    public void render(final GuiGraphics graphics, final DeltaTracker deltaTracker) {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.font == null) {
            return;
        }

        final AbstractContraptionEntity target = findTarget(mc, player);
        if (target == null) {
            return;
        }

        final ContraptionStatusIndicator.Status status = ContraptionStatusIndicator.resolve(
                mc.options.hideGui,
                mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR,
                ContraptionTargetProbe.hasBlocks(target.getContraption()),
                ContraptionInteractionPolicy.isInteractive(target));
        final String key = ContraptionStatusIndicator.translationKey(status);
        if (key == null) {
            return;
        }

        final Component text = Component.translatable(key);
        final int x = (graphics.guiWidth() - mc.font.width(text)) / 2;
        final int y = graphics.guiHeight() - BOTTOM_OFFSET;
        graphics.drawString(mc.font, text, x, y, 0xFFAAAAAA, true);
    }

    /**
     * Resolves the contraption under the crosshair, or {@code null}.
     *
     * <p>Reads Create's registry instead of the client level because contraptions are not level entities, and
     * takes the ray from Create's {@code getRayInputs} so the reach limit and the occlusion by a nearer block
     * are exactly the ones the interaction path uses. Candidates are pre-filtered with a box test before any
     * block raytrace happens; the hit itself comes from Create's block-shape raytrace.</p>
     */
    private static AbstractContraptionEntity findTarget(final Minecraft mc, final LocalPlayer player) {
        final Couple<Vec3> ray = ContraptionTargetProbe.rayInputs(player);
        if (ray == null) {
            return null;
        }
        final Vec3 origin = ray.getFirst();
        final Vec3 target = ray.getSecond();

        final Map<Integer, WeakReference<AbstractContraptionEntity>> registered =
                ContraptionHandler.loadedContraptions.get(mc.level);
        if (registered == null) {
            return null;
        }

        final List<AbstractContraptionEntity> candidates = new ArrayList<>(registered.size());
        for (final WeakReference<AbstractContraptionEntity> reference : registered.values()) {
            final AbstractContraptionEntity entity = reference.get();
            // Cheap filter first: no block raytrace for contraptions the ray does not come near.
            if (entity != null && ContraptionTargetProbe.isCandidate(origin, target, entity)) {
                candidates.add(entity);
            }
        }
        return ContraptionTargetProbe.nearest(origin, target, candidates);
    }
}
