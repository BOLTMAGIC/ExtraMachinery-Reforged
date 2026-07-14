package net.lmor.botanicalextramachinery.blocks.screens.mechanicalRunicAltar;

import net.lmor.botanicalextramachinery.blocks.base.ExtraScreenBase;
import net.lmor.botanicalextramachinery.blocks.containers.mechanicalRunicAltar.ContainerRunicAltarBase;
import net.lmor.botanicalextramachinery.blocks.screens.uitlScreen.ScreenAddInventory;
import net.lmor.botanicalextramachinery.blocks.screens.uitlScreen.ScreenDrawLabelText;
import net.lmor.botanicalextramachinery.blocks.screens.uitlScreen.ScreenInventory;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalRunicAltar.BlockEntityRunicAltarBase;
import net.lmor.botanicalextramachinery.core.LibResources;
import net.lmor.botanicalextramachinery.gui.AllBars;
import net.lmor.botanicalextramachinery.gui.Bars;
import net.lmor.botanicalextramachinery.gui.SlotInfo;
import net.lmor.botanicalextramachinery.util.GhostItemRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import vazkii.botania.common.block.BotaniaBlocks;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class ScreenRunicAltarBase extends ExtraScreenBase<ContainerRunicAltarBase> {
    BlockEntityRunicAltarBase blockEntity;
    ScreenAddInventory screenAddInventory = new ScreenAddInventory(ScreenInventory.BASE);
    Bars bars;
    SlotInfo slotInfo;

    public ScreenRunicAltarBase(ContainerRunicAltarBase menu, Inventory inventory, Component title) {
        super(menu, inventory, title);

        bars = new Bars(this);
        slotInfo = new SlotInfo(this);

        this.imageWidth = ContainerRunicAltarBase.WIDTH_GUI;
        this.imageHeight = ContainerRunicAltarBase.HEIGHT_GUI;

        bars.setBar(AllBars.MANA);
        bars.setDrawCoord(43, 121);

        blockEntity = this.menu.getBlockEntity();

        Map<Integer, int[]> slots = new HashMap<>();
        slots.put(0, new int[] {100, 100});

        slotInfo.setCoord(slots);
        slotInfo.setTranslatableText(new String[] {
                "botanicalextramachinery.tooltip.screen.livingrock_slot"
        });
    }

    @Override
    protected ScreenAddInventory getAddInventory() {
        return screenAddInventory;
    }

    @OnlyIn(Dist.CLIENT)
    protected void renderBg(@Nonnull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        this.drawDefaultGuiBackgroundLayer(guiGraphics, LibResources.BASE_MECHANICAL_RUNIC_ALTAR_GUI, screenAddInventory,
                new int[] {blockEntity.getCurrentMana()}, new int[] {blockEntity.getMaxMana()}, bars, slotInfo);

        ScreenDrawLabelText.drawLabelText(guiGraphics, this.font, "block.botanicalextramachinery.base_runic_altar",
                new int[] {this.leftPos, this.topPos}, new int[] {this.imageWidth, this.imageHeight}, 6);

        if (blockEntity.getInventory().getStackInSlot(0).isEmpty() && this.minecraft != null) {
            GhostItemRenderer.renderGhostItem(new ItemStack(BotaniaBlocks.livingrock), guiGraphics, this.leftPos + 100, this.topPos + 100);
        }

        if (blockEntity.getProgress() > 0) {
            float pct = Math.min((float)blockEntity.getProgress() / (float)blockEntity.getMaxProgress(), 1.0F);
            guiGraphics.blit(LibResources.BASE_MECHANICAL_RUNIC_ALTAR_GUI, this.leftPos + 102, this.topPos + 41, this.imageWidth, 0, Math.round(11.0F * pct), 37);
        }

        slotInfo.renderHoveredToolTip(guiGraphics, mouseX, mouseY, blockEntity.getInventory());
        bars.renderHoveredToolTip(guiGraphics, mouseX, mouseY, blockEntity.getCurrentMana(), blockEntity.getMaxMana(), 0);
    }
}
