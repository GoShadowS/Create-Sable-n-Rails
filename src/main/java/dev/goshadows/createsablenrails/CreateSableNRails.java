package dev.goshadows.createsablenrails;

import dev.goshadows.createsablenrails.config.SableNRailsConfig;
import dev.goshadows.createsablenrails.index.ModBlocks;
import dev.goshadows.createsablenrails.index.ModCreativeTabs;
import dev.goshadows.createsablenrails.interaction.ContraptionInteractionPolicy;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for Create: Sable 'n' Rails.
 *
 * <p>The addon does not replace or reimplement Create's contraption lifecycle, and it does not
 * duplicate Sable's physics integration. Create already owns assembly, movement, disassembly and NBT
 * persistence; Sable already owns the physics pipeline, collision adaptation and the projected
 * raytrace for Create contraptions on sub-levels. What neither implements is an <em>authorisation</em>
 * layer deciding whether a player may interact with the blocks of a moved contraption at all. That
 * gap is what this addon fills. The evidence for the gap is recorded in {@code UPSTREAM_RESEARCH.md}.</p>
 */
@Mod(CreateSableNRails.MOD_ID)
public final class CreateSableNRails {

    public static final String MOD_ID = "create_sable_n_rails";
    public static final Logger LOGGER = LoggerFactory.getLogger("Create: Sable 'n' Rails");

    public CreateSableNRails(final IEventBus modBus, final ModContainer container) {
        ModBlocks.register(modBus);

        container.registerConfig(ModConfig.Type.COMMON, SableNRailsConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.SERVER, SableNRailsConfig.SERVER_SPEC);

        modBus.addListener(BuildCreativeModeTabContentsEvent.class, ModCreativeTabs::onBuildContents);

        // The interaction cache is keyed by contraption entity UUID, which is only meaningful inside
        // one running world. Drop it on every world start/stop so a reopened world can never inherit
        // a stale decision from a previous world in the same JVM. The markers themselves are persisted
        // by Create as ordinary contraption blocks, so nothing has to be reloaded here.
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ContraptionInteractionPolicy.clear());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ContraptionInteractionPolicy.clear());

        // A changed policy invalidates every cached decision.
        modBus.addListener((ModConfigEvent.Loading event) -> ContraptionInteractionPolicy.clear());
        modBus.addListener((ModConfigEvent.Reloading event) -> ContraptionInteractionPolicy.clear());

        LOGGER.info("Create: Sable 'n' Rails loaded (contraption interactivity authorisation)");
    }

    public static ResourceLocation asResource(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
