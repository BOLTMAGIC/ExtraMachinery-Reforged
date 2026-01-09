package net.lmor.botanicalextramachinery.blocks.base;

import com.google.common.base.Predicates;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.moddingx.libx.base.tile.BlockEntityBase;
import org.moddingx.libx.base.tile.TickingBlock;
import org.moddingx.libx.capability.ItemCapabilities;
import org.moddingx.libx.inventory.BaseItemStackHandler;
import org.moddingx.libx.inventory.IAdvancedItemHandlerModifiable;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.BotaniaForgeClientCapabilities;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.api.item.SparkEntity;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.SparkAttachable;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.client.gui.HUDHandler;
import vazkii.botania.common.block.block_entity.mana.ThrottledPacket;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

@OnlyIn(
        value = Dist.CLIENT,
        _interface = WandHUD.class
)
public abstract class ExtraBotanicalTile extends BlockEntityBase implements ManaPool, SparkAttachable, ThrottledPacket, WandHUD, TickingBlock {
    private int mana;
    private int manaCap;

    private final LazyOptional<IAdvancedItemHandlerModifiable> capability = this.createCap(this::getInventory);

    public ExtraBotanicalTile(BlockEntityType<?> blockEntityTypeIn, BlockPos pos, BlockState state, int cap) {
        super(blockEntityTypeIn, pos, state);
        manaCap = cap;
    }

    protected LazyOptional<IAdvancedItemHandlerModifiable> createCap(Supplier<IItemHandlerModifiable> inventory) {
        return ItemCapabilities.create(inventory, this.getExtracts(inventory), this.getInserts(inventory));
    }

    protected abstract Predicate<Integer> getExtracts(Supplier<IItemHandlerModifiable> var1);

    protected BiPredicate<Integer, ItemStack> getInserts(Supplier<IItemHandlerModifiable> inventory) {
        return null;
    }

    @Nonnull
    public abstract BaseItemStackHandler getInventory();

    public abstract int getComparatorOutput();

    public void setChanged() {
        super.setChanged();
        if (this.level != null) {
            this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
        }

    }

    @Nonnull
    public <X> LazyOptional<X> getCapability(@Nonnull Capability<X> cap, Direction direction) {
        if (!this.remove && cap == ForgeCapabilities.ITEM_HANDLER) {
            return this.capability.cast();
        } else {
            return this.remove || !this.actAsMana() || cap != BotaniaForgeCapabilities.MANA_RECEIVER && cap != BotaniaForgeCapabilities.SPARK_ATTACHABLE ? DistExecutor.unsafeRunForDist(() -> {
                return () -> {
                    return !this.remove && this.actAsMana() && cap == BotaniaForgeClientCapabilities.WAND_HUD ? LazyOptional.of(() -> {
                        return this;
                    }).cast() : super.getCapability(cap, direction);};}, () -> {
                return () -> {
                    return super.getCapability(cap, direction);}
                        ;}
            ) : LazyOptional.of(() -> {return this;}).cast();
        }
    }

    public void load(@Nonnull CompoundTag tag) {
        super.load(tag);
        this.getInventory().deserializeNBT(tag.getCompound("inv"));
        this.mana = tag.getInt("mana");
        manaCap = tag.getInt("manaCap");

    }

    public void saveAdditional(@Nonnull CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("inv", this.getInventory().serializeNBT());
        nbt.putInt("mana", this.getCurrentMana());
        nbt.putInt("manaCap", this.getMaxMana());
    }

    public void handleUpdateTag(CompoundTag tag) {
        if (this.level == null || this.level.isClientSide) {
            this.getInventory().deserializeNBT(tag.getCompound("inv"));
            this.mana = tag.getInt("mana");
            manaCap = tag.getInt("manaCap");
        }
    }

    @Nonnull
    public CompoundTag getUpdateTag() {
        if (this.level != null && this.level.isClientSide) {
            return super.getUpdateTag();
        } else {
            CompoundTag nbt = super.getUpdateTag();
            nbt.put("inv", this.getInventory().serializeNBT());
            nbt.putInt("mana", this.getCurrentMana());
            nbt.putInt("manaCap", this.getMaxMana());
            return nbt;
        }
    }

    public void renderHUD(GuiGraphics guiGraphics, Minecraft minecraft) {
        ItemStack block = new ItemStack(this.getBlockState().getBlock());
        String name = block.getHoverName().getString();
        int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
        int centerY = minecraft.getWindow().getGuiScaledHeight() / 2;
        int width = Math.max(102, minecraft.font.width(name)) + 4;
        RenderHelper.renderHUDBox(guiGraphics, centerX - width / 2, centerY + 8, centerX + width / 2, centerY + 28);
        HUDHandler.drawSimpleManaHUD(guiGraphics, 4474111, this.getCurrentMana(), this.getMaxMana(), name);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 771);
        RenderSystem.disableBlend();
    }

    public boolean canAttachSpark(ItemStack itemStack) {
        return this.actAsMana();
    }

    public int getAvailableSpaceForMana() {
        return !this.actAsMana() ? 0 : Math.max(Math.max(0, this.getMaxMana() - this.getCurrentMana()), 0);
    }

    public ManaSpark getAttachedSpark() {
        if (!this.actAsMana()) {
            return null;
        } else {
            List<Entity> sparks = this.level.getEntitiesOfClass(Entity.class, new AABB(this.worldPosition.above(), this.worldPosition.above().offset(1, 1, 1)), Predicates.instanceOf(SparkEntity.class));
            if (sparks.size() == 1) {
                Entity entity = (Entity)sparks.get(0);
                return (ManaSpark)entity;
            }
        }

        return null;
    }

    public boolean areIncomingTranfersDone() {
        return !this.actAsMana() || this.isFull();
    }

    public boolean isFull() {
        return this.getCurrentMana() >= this.getMaxMana();
    }

    public void receiveMana(int i) {
        int old = this.getCurrentMana();
        this.mana = Math.max(0, Math.min(this.getCurrentMana() + i, this.getMaxMana()));
        if (old != this.getCurrentMana()) {
            this.setChanged();
            this.setDispatchable();
        }
    }

    public boolean canReceiveManaFromBursts() {
        return true;
    }

    public int getCurrentMana() {
        return this.mana;
    }

    public void setCurrentMana(int val) {
        this.mana = val;
    }

    public int getMaxMana() {
        return manaCap;
    }

    public void setMaxMana(int val) {
        manaCap = val;
    }

    public boolean isOutputtingPower() {
        return false;
    }

    public Optional<DyeColor> getColor() {
        return Optional.empty();
    }

    public void setColor(Optional<DyeColor> color) {}

    public void markDispatchable() {
        this.setDispatchable();
    }

    public Level getManaReceiverLevel() {
        return this.getLevel();
    }

    public BlockPos getManaReceiverPos() {
        return this.getBlockPos();
    }

    public boolean actAsMana() {
        return true;
    }
}
