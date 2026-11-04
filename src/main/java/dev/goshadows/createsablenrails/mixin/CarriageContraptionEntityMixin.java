package dev.goshadows.createsablenrails.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.goshadows.createsablenrails.interaction.ContraptionInteractionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invalidates the cached interactivity decision when a carriage contraption's block map changes.
 *
 * <h2>Why the base-class inject is not enough</h2>
 * <p>{@code CarriageContraptionEntity#setBlock} ({@code mc1.21.1-6.0.10}, line 200) <b>overrides</b>
 * {@code AbstractContraptionEntity#setBlock} (line 427) and does not call {@code super}:</p>
 *
 * <pre>
 * public void setBlock(BlockPos localPos, StructureBlockInfo newInfo) {
 *     if (carriage == null)
 *         return;
 *     carriage.forEachPresentEntity(cce -&gt; {
 *         cce.contraption.getBlocks()
 *             .put(localPos, newInfo);
 *         CatnipServices.NETWORK.sendToClientsTrackingEntity(cce,
 *             new ContraptionBlockChangedPacket(cce.getId(), localPos, newInfo.state()));
 *     });
 * }
 * </pre>
 *
 * <p>So a carriage mutation written through {@code MovingInteractionBehaviour#setContraptionBlockData}
 * reaches {@code setBlock} of the carriage entity, never the injected base implementation, and the cached
 * decision for that carriage would survive the change.</p>
 *
 * <h2>Why the whole cache is cleared</h2>
 * <p>One call updates the block map of <b>every present</b> carriage entity — {@code forEachPresentEntity}
 * iterates {@code Carriage#entities}, i.e. one {@link CarriageContraptionEntity} per dimension the carriage
 * currently exists in. The entity this inject runs on is therefore not the only entity whose cache entry is
 * now wrong, and the other ones are not reachable from here without reproducing Create's internal iteration.
 * {@code clear()} is unconditional, cheap (an empty or small map), and cannot leave a stale decision behind;
 * that correctness is worth more than the saved re-evaluation, which only happens once per changed
 * contraption.</p>
 *
 * <p>{@code remap = false} because the target is a Create class, matching the convention used by the other
 * mixins here and by Sable 2.0.6.</p>
 */
@Mixin(CarriageContraptionEntity.class)
public abstract class CarriageContraptionEntityMixin {

    /**
     * Drops every cached decision before the carriage block map is rewritten.
     *
     * <p>An injector on an override requires nothing special: Mixin applies {@code setBlock} as declared on
     * {@code CarriageContraptionEntity}, so this injection does not fire for the base implementation and the
     * base injection does not fire for this one. Between them both server-side paths are covered.</p>
     */
    @Inject(method = "setBlock", at = @At("HEAD"), remap = false)
    private void sableNRails$invalidateCarriageCache(final BlockPos localPos, final StructureBlockInfo newInfo,
                                                     final CallbackInfo ci) {
        ContraptionInteractionPolicy.clear();
    }
}
