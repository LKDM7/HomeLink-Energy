package fr.lkdm.homelink.energy.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only information screen of an energy block. It has no slots and no client-to-server
 * input: the server stays the source of truth and closes the screen as soon as the block is
 * gone, in another dimension or more than 8 blocks away.
 */
public abstract class EnergyMenu extends AbstractContainerMenu {
    private static final double MAX_DISTANCE_SQR = 8 * 8;

    private final BlockPos pos;
    private final ContainerData data;
    @Nullable private final BlockEntity source;

    protected EnergyMenu(MenuType<?> type, int id, BlockPos pos, ContainerData data, @Nullable BlockEntity source) {
        super(type, id);
        this.pos = pos;
        this.data = data;
        this.source = source;
        addDataSlots(data);
    }

    /** @return position of the block */
    public BlockPos pos() { return pos; }

    /** @param index value index
     *  @return synchronized 32-bit value */
    public int value(int index) { return MenuData.value(data, index); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        if (source == null) return true;
        return !source.isRemoved() && source.getLevel() == player.level()
                && source.getLevel().getBlockEntity(pos) == source
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
    }
}
