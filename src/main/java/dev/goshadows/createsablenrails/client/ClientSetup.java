package dev.goshadows.createsablenrails.client;

import dev.goshadows.createsablenrails.CreateSableNRails;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Client-only registration for the contraption status indicator.
 *
 * <p>The class is annotated {@code @EventBusSubscriber(value = Dist.CLIENT)}, so it is loaded on the physical
 * client only and never referenced on a dedicated server. {@link RegisterGuiLayersEvent} is a mod-bus event
 * ("Allows users to register custom layers for GUI rendering", fired on the mod-specific event bus, client
 * only), which is why the handler is a static {@code @SubscribeEvent} method rather than a lambda added from
 * the mod constructor: the constructor runs on both sides and must not touch client classes.</p>
 *
 * <p>The layer is registered above the hotbar rather than replacing any vanilla layer, so it cannot disturb
 * other mods' HUD elements; within the hotbar group it is added last, which puts it on top of the bar area
 * only where it draws its own line of text.</p>
 */
@EventBusSubscriber(modid = CreateSableNRails.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {

    /** Layer id, unique within this addon. */
    public static final ResourceLocation INDICATOR_LAYER = CreateSableNRails.asResource("contraption_status");

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, INDICATOR_LAYER, ContraptionStatusOverlay.INSTANCE);
    }
}
