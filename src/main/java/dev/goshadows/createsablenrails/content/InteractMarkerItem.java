package dev.goshadows.createsablenrails.content;

import dev.goshadows.createsablenrails.index.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Item form of a contraption marker.
 *
 * <p>The tooltip explains which of the four server policies the marker actually affects, so the
 * precedence rules in {@link dev.goshadows.createsablenrails.interaction.InteractiveDecision} are
 * discoverable in game instead of only in the documentation.</p>
 */
public class InteractMarkerItem extends BlockItem {

    private final boolean allowMarker;

    public InteractMarkerItem(final Block block, final boolean allowMarker, final Item.Properties properties) {
        super(block, properties);
        this.allowMarker = allowMarker;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context, final List<Component> tooltip,
                                final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        final String key = this.allowMarker ? "interact_me" : "interact_me_not";
        tooltip.add(Component.translatable("create_sable_n_rails.tooltip." + key + ".1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("create_sable_n_rails.tooltip." + key + ".2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Shortcut used by {@link ModBlocks} so both markers stay in sync. */
    public static Item.Properties properties() {
        return new Item.Properties();
    }
}
