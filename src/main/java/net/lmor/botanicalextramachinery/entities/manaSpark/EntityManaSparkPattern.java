package net.lmor.botanicalextramachinery.entities.manaSpark;

import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.base.ExtraBotanicalTile;
import net.lmor.botanicalextramachinery.blocks.tiles.BlockEntityGreenhouse;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.block.WandHUD;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.SparkAttachable;
import vazkii.botania.api.mana.spark.SparkHelper;
import vazkii.botania.api.mana.spark.SparkUpgradeType;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.common.entity.SparkBaseEntity;
import vazkii.botania.common.helper.ColorHelper;
import vazkii.botania.common.helper.VecHelper;
import vazkii.botania.common.item.BotaniaItems;
import vazkii.botania.common.item.SparkAugmentItem;
import vazkii.botania.common.item.WandOfTheForestItem;
import vazkii.botania.network.EffectType;
import vazkii.botania.network.clientbound.BotaniaEffectPacket;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.*;

public class EntityManaSparkPattern extends SparkBaseEntity implements ManaSpark {

    protected int TRANSFER_RATE = 1000;
    private static final String TAG_UPGRADE = "upgrade";
    private static final EntityDataAccessor<Integer> UPGRADE;
    private final Set<ManaSpark> outgoingTransfers;
    private final ArrayList<ManaSpark> transfersTowardsSelfToRegister;
    private boolean shouldFilterTransfers;
    private boolean receiverWasFull;
    private boolean firstTick;

    public EntityManaSparkPattern(EntityType<?> entityEntityType, Level level) {
        super(entityEntityType, level);
        this.outgoingTransfers = Collections.newSetFromMap(new WeakHashMap<>());
        this.transfersTowardsSelfToRegister = new ArrayList<>();
        this.shouldFilterTransfers = true;
        this.receiverWasFull = true;
        this.firstTick = true;
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(UPGRADE, 0);
    }

    public @NotNull ItemStack getPickResult() {
        return new ItemStack(this.getSparkItem());
    }

    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (this.firstTick) {
                this.updateTransfers();
            }

            SparkAttachable tile = this.getAttachedTile();
            if (tile == null) {
                this.dropAndKill();
            } else {
                ManaReceiver receiver = this.getAttachedManaReceiver();
                SparkUpgradeType upgrade = this.getUpgrade();
                Collection<ManaSpark> transfers = this.getOutgoingTransfers();
                switch (upgrade) {
                    case DISPERSIVE -> {
                        AABB aabb = VecHelper.boxForRange(this.position().with(Direction.Axis.Y, this.getY() + (double) this.getBbHeight() / 2.0), 12.0);
                        List<Player> players = this.level().getEntitiesOfClass(Player.class, aabb, EntitySelector.ENTITY_STILL_ALIVE);
                        Map<Player, Map<ManaItem, Integer>> receivingPlayers = new HashMap<>();
                        ItemStack input = new ItemStack(this.getSparkItem());
                        Iterator<Player> iterator = players.iterator();
                        Player player;
                        while (iterator.hasNext()) {
                            player = iterator.next();
                            List<ItemStack> stacks = new ArrayList<>();
                            stacks.addAll(player.getInventory().items);
                            stacks.addAll(player.getInventory().armor);
                            Container inv = BotaniaAPI.instance().getAccessoriesInventory(player);

                            for (int i = 0; i < inv.getContainerSize(); ++i) {
                                stacks.add(inv.getItem(i));
                            }

                            for (ItemStack stack : stacks) {
                                ManaItem manaItem = XplatAbstractions.INSTANCE.findManaItem(stack);
                                if (!stack.isEmpty() && manaItem != null && manaItem.canReceiveManaFromItem(input)) {
                                    boolean add = false;
                                    Map<ManaItem, Integer> receivingStacks;
                                    if (!receivingPlayers.containsKey(player)) {
                                        add = true;
                                        receivingStacks = new HashMap<>();
                                    } else {
                                        receivingStacks = receivingPlayers.get(player);
                                    }

                                    assert receiver != null;
                                    int recv = Math.min(receiver.getCurrentMana(), Math.min(TRANSFER_RATE, manaItem.getMaxMana() - manaItem.getMana()));
                                    if (recv > 0) {
                                        receivingStacks.put(manaItem, recv);
                                        if (add) {
                                            receivingPlayers.put(player, receivingStacks);
                                        }
                                    }
                                }
                            }
                        }
                        if (!receivingPlayers.isEmpty()) {
                            List<Player> keys = new ArrayList<>(receivingPlayers.keySet());
                            Collections.shuffle(keys);
                            player = keys.iterator().next();
                            Map<ManaItem, Integer> items = receivingPlayers.get(player);
                            Map.Entry<ManaItem, Integer> e = items.entrySet().iterator().next();
                            ManaItem manaItem = e.getKey();
                            int cost = e.getValue();
                            int manaToPut = Math.min(receiver.getCurrentMana(), cost);
                            manaItem.addMana(manaToPut);
                            receiver.receiveMana(-manaToPut);
                            this.particlesTowards(player);
                        }
                    }
                    case DOMINANT -> {
                        if (this.receiverWasFull) {
                            assert receiver != null;
                            if (!receiver.isFull()) {
                                this.updateTransfers();
                            }
                        }
                        if (!this.transfersTowardsSelfToRegister.isEmpty()) {
                            (this.transfersTowardsSelfToRegister.remove(this.transfersTowardsSelfToRegister.size() - 1)).registerTransfer(this);
                        }
                    }
                    default -> {
                        if (this.receiverWasFull) {
                            assert receiver != null;
                            if (!receiver.isFull()) {
                                this.notifyOthers(this.getNetwork());
                            }
                        }
                    }
                }

                if (receiver != null) {
                    this.receiverWasFull = receiver.isFull();
                } else {
                    this.receiverWasFull = true;
                }

                if (!transfers.isEmpty()) {
                    assert receiver != null;
                    int manaTotal = Math.min(TRANSFER_RATE * transfers.size(), receiver.getCurrentMana());
                    int count = transfers.size();
                    int manaSpent = 0;
                    if (manaTotal > 0) {
                        if (this.shouldFilterTransfers) {
                            this.filterTransfers();
                            this.shouldFilterTransfers = false;
                        }

                        for (ManaSpark spark : transfers) {
                            --count;
                            SparkAttachable attached = spark.getAttachedTile();
                            ManaReceiver attachedReceiver = spark.getAttachedManaReceiver();
                            if (attached != null && attachedReceiver != null && !attachedReceiver.isFull() && !spark.areIncomingTransfersDone() && (!(receiver instanceof ExtraBotanicalTile) || receiver instanceof BlockEntityGreenhouse) && !(attachedReceiver instanceof BlockEntityGreenhouse)) {
                                int spend = Math.min(attached.getAvailableSpaceForMana(), (manaTotal - manaSpent) / (count + 1));
                                attachedReceiver.receiveMana(spend);
                                manaSpent += spend;
                                this.particlesTowards(spark.entity());
                            } else {
                                this.shouldFilterTransfers = true;
                            }
                        }

                        receiver.receiveMana(-manaSpent);
                    }
                }

                this.firstTick = false;
            }
        }
    }

    public void updateTransfers() {
        this.transfersTowardsSelfToRegister.clear();
        List<ManaSpark> otherSparks;
        Iterator<ManaSpark> iterator;
        ManaSpark otherSpark;
        SparkUpgradeType otherUpgrade;
        switch (this.getUpgrade()) {
            case DOMINANT -> {
                otherSparks = SparkHelper.getSparksAround(this.level(), this.getX(), this.getY() + (double) (this.getBbHeight() / 2.0F), this.getZ(), this.getNetwork());
                iterator = otherSparks.iterator();
                while (iterator.hasNext()) {
                    otherSpark = iterator.next();
                    otherUpgrade = otherSpark.getUpgrade();
                    if (otherSpark != this && otherUpgrade == SparkUpgradeType.NONE && otherSpark.getAttachedManaReceiver() instanceof ManaPool) {
                        this.transfersTowardsSelfToRegister.add(otherSpark);
                    }
                }
                Collections.shuffle(this.transfersTowardsSelfToRegister);
            }
            case RECESSIVE -> {
                otherSparks = SparkHelper.getSparksAround(this.level(), this.getX(), this.getY() + (double) (this.getBbHeight() / 2.0F), this.getZ(), this.getNetwork());
                Collections.shuffle(otherSparks);
                iterator = otherSparks.iterator();
                while (iterator.hasNext()) {
                    otherSpark = iterator.next();
                    otherUpgrade = otherSpark.getUpgrade();
                    if (otherSpark != this && otherUpgrade != SparkUpgradeType.DOMINANT && otherUpgrade != SparkUpgradeType.RECESSIVE && otherUpgrade != SparkUpgradeType.ISOLATED) {
                        this.outgoingTransfers.add(otherSpark);
                    }
                }
            }
        }

        this.filterTransfers();
    }

    private void particlesTowards(Entity e) {
        XplatAbstractions.INSTANCE.sendToTracking(this, new BotaniaEffectPacket(EffectType.SPARK_MANA_FLOW, this.getX(), this.getY(), this.getZ(), this.getId(), e.getId(), ColorHelper.getColorValue(this.getNetwork())));
    }

    public static void particleBeam(Player player, Entity e1, Entity e2) {
        if (e1 != null && e2 != null && !e1.level().isClientSide) {
            XplatAbstractions.INSTANCE.sendToPlayer(player, new BotaniaEffectPacket(EffectType.SPARK_NET_INDICATOR, e1.getX(), e1.getY(), e1.getZ(), e1.getId(), e2.getId()));
        }

    }

    protected Item getSparkItem() {
        return ModItems.malachiteSpark;
    }

    private void dropAndKill() {
        SparkUpgradeType upgrade = this.getUpgrade();
        this.spawnAtLocation(new ItemStack(this.getSparkItem()), 0.0F);
        if (upgrade != SparkUpgradeType.NONE) {
            this.spawnAtLocation(SparkAugmentItem.getByType(upgrade), 0.0F);
        }

        this.discard();
    }

    public void remove(Entity.@NotNull RemovalReason removalReason) {
        super.remove(removalReason);
        this.notifyOthers(this.getNetwork());
    }

    public @NotNull InteractionResult interact(Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.isAlive() && !stack.isEmpty()) {
            SparkUpgradeType upgrade = this.getUpgrade();
            if (stack.getItem() instanceof WandOfTheForestItem) {
                if (!this.level().isClientSide) {
                    if (player.isShiftKeyDown()) {
                        if (upgrade != SparkUpgradeType.NONE) {
                            this.spawnAtLocation(SparkAugmentItem.getByType(upgrade), 0.0F);
                            this.setUpgrade(SparkUpgradeType.NONE);
                            this.outgoingTransfers.clear();
                            this.notifyOthers(this.getNetwork());
                        } else {
                            this.dropAndKill();
                        }
                    } else {
                        SparkHelper.getSparksAround(this.level(), this.getX(), this.getY() + (double)(this.getBbHeight() / 2.0F), this.getZ(), this.getNetwork()).forEach((s) -> {
                            particleBeam(player, this, s.entity());
                        });
                    }
                }

                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            Item item = stack.getItem();
            if (item instanceof SparkAugmentItem newUpgrade) {
                if (upgrade == SparkUpgradeType.NONE) {
                    if (!this.level().isClientSide) {
                        this.setUpgrade(newUpgrade.type);
                        stack.shrink(1);
                    }

                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }

            if (stack.is(BotaniaItems.phantomInk)) {
                if (!this.level().isClientSide) {
                    this.setInvisible(true);
                }

                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            item = stack.getItem();
            if (item instanceof DyeItem dye) {
                DyeColor color = dye.getDyeColor();
                if (color != this.getNetwork()) {
                    if (!this.level().isClientSide) {
                        this.setNetwork(color);
                        stack.shrink(1);
                    }

                    return InteractionResult.sidedSuccess(this.level().isClientSide);
                }
            }
        }

        return InteractionResult.PASS;
    }

    protected void readAdditionalSaveData(@NotNull CompoundTag cmp) {
        super.readAdditionalSaveData(cmp);
        this.setUpgrade(SparkUpgradeType.values()[cmp.getInt(TAG_UPGRADE)]);
    }

    protected void addAdditionalSaveData(@NotNull CompoundTag cmp) {
        super.addAdditionalSaveData(cmp);
        cmp.putInt(TAG_UPGRADE, this.getUpgrade().ordinal());
    }

    public SparkAttachable getAttachedTile() {
        return XplatAbstractions.INSTANCE.findSparkAttachable(this.level(), this.getAttachPos(), this.level().getBlockState(this.getAttachPos()), this.level().getBlockEntity(this.getAttachPos()), Direction.UP);
    }

    public @Nullable ManaReceiver getAttachedManaReceiver() {
        return XplatAbstractions.INSTANCE.findManaReceiver(this.level(), this.getAttachPos(), Direction.UP);
    }

    private void filterTransfers() {
        Iterator<ManaSpark> iter = this.outgoingTransfers.iterator();

        while(true) {
            ManaSpark spark;
            SparkUpgradeType upgr;
            SparkUpgradeType supgr;
            ManaReceiver arecv;
            do {
                if (!iter.hasNext()) {
                    return;
                }

                spark = iter.next();
                upgr = this.getUpgrade();
                supgr = spark.getUpgrade();
                arecv = spark.getAttachedManaReceiver();
            } while(spark != this && ((Entity)spark).isAlive() && !spark.areIncomingTransfersDone() && this.getNetwork() == spark.getNetwork() && arecv != null && !arecv.isFull() && (upgr == SparkUpgradeType.NONE && supgr == SparkUpgradeType.DOMINANT || upgr == SparkUpgradeType.RECESSIVE && (supgr == SparkUpgradeType.NONE || supgr == SparkUpgradeType.DISPERSIVE) || !(arecv instanceof ManaPool)));

            iter.remove();
        }
    }

    public Collection<ManaSpark> getOutgoingTransfers() {
        return this.outgoingTransfers;
    }

    private boolean hasTransfer(ManaSpark entity) {
        return this.outgoingTransfers.contains(entity);
    }

    public void registerTransfer(ManaSpark entity) {
        if (!this.hasTransfer(entity)) {
            this.outgoingTransfers.add(entity);
            this.filterTransfers();
        }
    }

    private void notifyOthers(DyeColor network) {
        for (ManaSpark spark : SparkHelper.getSparksAround(this.level(), this.getX(), this.getY() + (double) (this.getBbHeight() / 2.0F), this.getZ(), network)) {
            spark.updateTransfers();
        }
    }

    public SparkUpgradeType getUpgrade() {
        return SparkUpgradeType.values()[(Integer) this.entityData.get(UPGRADE)];
    }

    public void setUpgrade(SparkUpgradeType upgrade) {
        this.entityData.set(UPGRADE, upgrade.ordinal());
        this.updateTransfers();
        this.notifyOthers(this.getNetwork());
    }

    public void setNetwork(DyeColor color) {
        DyeColor previousNetwork = this.getNetwork();
        super.setNetwork(color);
        this.updateTransfers();
        this.notifyOthers(color);
        this.notifyOthers(previousNetwork);
    }

    public boolean areIncomingTransfersDone() {
        SparkAttachable attachable = this.getAttachedTile();
        return attachable != null && attachable.areIncomingTranfersDone();
    }

    static {
        UPGRADE = SynchedEntityData.defineId(EntityManaSparkPattern.class, EntityDataSerializers.INT);
    }

    public static record WandHud(EntityManaSparkPattern entity) implements WandHUD {
        public WandHud(EntityManaSparkPattern entity) {
            this.entity = entity;
        }

        public void renderHUD(GuiGraphics gui, Minecraft mc) {
            ItemStack sparkStack = new ItemStack(this.entity.getSparkItem());
            ItemStack augmentStack = SparkAugmentItem.getByType(this.entity.getUpgrade());
            DyeColor networkColor = this.entity.getNetwork();
            String colorName = networkColor.getName();
            Component networkColorName = Component.translatable("color.minecraft." + colorName).withStyle(ChatFormatting.ITALIC);
            int textColor = ColorHelper.getColorLegibleOnGrayBackground(networkColor);
            int width = 4 + Collections.max(Arrays.asList(mc.font.width(networkColorName), RenderHelper.itemWithNameWidth(mc, sparkStack), RenderHelper.itemWithNameWidth(mc, augmentStack)));
            int height = augmentStack.isEmpty() ? 30 : 50;
            int networkColorTextStart = mc.font.width(networkColorName) / 2;
            int centerX = mc.getWindow().getGuiScaledWidth() / 2;
            int centerY = mc.getWindow().getGuiScaledHeight() / 2;
            RenderHelper.renderHUDBox(gui, centerX - width / 2, centerY + 8, centerX + width / 2, centerY + 8 + height);
            RenderHelper.renderItemWithNameCentered(gui, mc, sparkStack, centerY + 10, textColor);
            RenderHelper.renderItemWithNameCentered(gui, mc, augmentStack, centerY + 28, textColor);
            gui.drawString(mc.font, networkColorName, centerX - networkColorTextStart, centerY + (augmentStack.isEmpty() ? 28 : 46), textColor);
        }

        public EntityManaSparkPattern entity() {
            return this.entity;
        }
    }
}
