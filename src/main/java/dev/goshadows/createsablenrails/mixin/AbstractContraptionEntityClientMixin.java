package dev.goshadows.createsablenrails.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.goshadows.createsablenrails.interaction.ContraptionInteractionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side cache invalidation for contraption block changes.
 *
 * <h2>What is verified</h2>
 * <p>Read from Create {@code mc1.21.1-6.0.10},
 * {@code src/main/java/com/simibubi/create/content/contraptions/AbstractContraptionEntity.java}:</p>
 * <ul>
 *     <li>The method carries {@code @OnlyIn(Dist.CLIENT)} (line 761) and is declared
 *     {@code protected void handleBlockChange(BlockPos localPos, BlockState newState)} (line 762).</li>
 *     <li>Its only caller is {@code handleBlockChangedPacket} (lines 745-749), which is itself
 *     {@code @OnlyIn(Dist.CLIENT)} and is invoked from the client-side handling of
 *     {@code ContraptionBlockChangedPacket}. No server-side code path calls this method.</li>
 *     <li>It writes {@code contraption.blocks.put(...)} directly, so the injection on
 *     {@code AbstractContraptionEntity#setBlock} never observes a packet-driven client update.</li>
 * </ul>
 *
 * <h2>Why the inject is in a client-listed mixin</h2>
 * <p>Because the target is a client-only member, the injector is placed in a mixin listed under the
 * {@code client} key of {@code create_sable_n_rails.mixins.json} instead of the common {@code mixins} list.
 * That is the fact this class relies on, and it holds regardless of how a given loader version treats
 * {@code @OnlyIn}: a mixin that is not applied on the dedicated server cannot fail to find a target there,
 * and the method has no server-side caller whose invalidation would be lost.</p>
 *
 * <p><b>Not claimed:</b> that {@code handleBlockChange} is physically absent from the dedicated-server class
 * as built by NeoForge {@code 21.1.228}. That was not verified. The only distribution cleaner found in
 * NeoForge's loader sources ({@code NeoForgeDevDistCleaner}, FancyModLoader) documents itself as masking
 * classes "from the Minecraft jar" "in a development environment"; no equivalent runtime stripping of
 * {@code @OnlyIn} members of mod classes was confirmed for the target loader version. The observed behaviour
 * is therefore the weaker, sufficient statement above, not the stronger one.</p>
 *
 * <h2>Why invalidate at all on the client</h2>
 * <p>Without it the client's mirrored copy of the decision would stay at its pre-packet value after a
 * {@code ContraptionBlockChangedPacket}, and the raytrace filter in {@code ContraptionHandlerClientMixin}
 * would keep hiding (or showing) the contraption according to the old markers. The server stays
 * authoritative; this only keeps the two sides agreeing, and the exact-signature cache key would in any case
 * refuse to reuse the stale entry.</p>
 */
@Mixin(AbstractContraptionEntity.class)
public abstract class AbstractContraptionEntityClientMixin {

    /**
     * Drops the cached decision before the local block map is rewritten.
     *
     * <p>{@code remap = false} for the same reason as the other mixins here: the target is a Create class,
     * not a Minecraft class. The target method is {@code protected}, which requires no special handling for
     * an {@code @Inject}.</p>
     */
    @Inject(method = "handleBlockChange", at = @At("HEAD"), remap = false)
    private void sableNRails$invalidateOnClientBlockChange(final BlockPos localPos, final BlockState newState,
                                                           final CallbackInfo ci) {
        ContraptionInteractionPolicy.invalidate(((AbstractContraptionEntity) (Object) this).getUUID());
    }
}
