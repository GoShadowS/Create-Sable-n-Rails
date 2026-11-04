package dev.goshadows.createsablenrails.index;

import com.simibubi.create.AllCreativeModeTabs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Adds the marker items to Create's base creative tab (and to the search tab).
 *
 * <p>Create 6.0.10 fills its base tab from its own registrate generator
 * ({@code AllCreativeModeTabs.BASE_CREATIVE_TAB}), which only knows about Create's own entries, so an
 * addon has to append its items through {@link BuildCreativeModeTabContentsEvent}. The tab key is read
 * from the holder instead of being hardcoded, so a future Create release that renames the
 * {@code create:base} id cannot silently target a tab that no longer exists.</p>
 *
 * <p>The two-argument {@code accept} overload with an explicit
 * {@link CreativeModeTab.TabVisibility} is used on purpose: it is the form NeoForge documents and
 * guarantees the entry appears in both the parent tab and the search tab.</p>
 */
public final class ModCreativeTabs {

    private ModCreativeTabs() {
    }

    public static void onBuildContents(final BuildCreativeModeTabContentsEvent event) {
        final ResourceKey<CreativeModeTab> tabKey = AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey();
        if (tabKey == null || !tabKey.equals(event.getTabKey())) {
            return;
        }
        event.accept(new ItemStack(ModBlocks.INTERACT_ME_ITEM.get()),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        event.accept(new ItemStack(ModBlocks.INTERACT_ME_NOT_ITEM.get()),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}
