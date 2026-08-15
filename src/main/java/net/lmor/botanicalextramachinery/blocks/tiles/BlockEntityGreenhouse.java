package net.lmor.botanicalextramachinery.blocks.tiles;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.google.common.collect.Range;
import net.lmor.botanicalextramachinery.Items.ItemUpgrade;
import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.base.ExtraBotanicalTile;
import net.lmor.botanicalextramachinery.blocks.flowersGreenhouse.GenFlowers;
import net.lmor.botanicalextramachinery.config.LibXClientConfig;
import net.lmor.botanicalextramachinery.config.LibXServerConfig;
import net.lmor.botanicalextramachinery.util.ExportManaME;
import net.lmor.botanicalextramachinery.util.ResizeBaseItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moddingx.libx.inventory.BaseItemStackHandler;
import org.moddingx.libx.inventory.IAdvancedItemHandlerModifiable;
import vazkii.botania.client.fx.WispParticleData;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;


public class BlockEntityGreenhouse extends ExtraBotanicalTile implements IEnergyStorage,
        IInWorldGridNodeHost, IGridConnectedBlockEntity {

    private final static int FLOWER_SIZE = 7;
    private final static int FUEL_SIZE = 21;
    private final static int UPGRADE_SIZE = 8;

    private int slotLimit = 1;
    private final static int baseManaStorage = LibXServerConfig.GreenhouseSettings.manaStorage;
    private final static int baseEnergyStorage = LibXServerConfig.GreenhouseSettings.energyCapacity;
    private boolean upgrade_cost_energy;
    private boolean upgrade_gen_mana;
    private boolean upgrade_heat;
    private final static float[] heatList = new float[] {1.05f, 1.1f, 1.15f, 1.20f};
    private int count_heat = 0;
    private final static int max_count_heat = 40;
    private boolean upgrade_slot_add;
    private float upgrade_speed_craft = 0;

    private int energy = 0;
    private int energy_capacity;
    private final int energyTransfer;
    private final int energyCost;

    private final ResizeBaseItemStackHandler flowerInventory;
    private final BaseItemStackHandler fuelInventory;
    private final BaseItemStackHandler upgradeInventory;

    private final Map<Integer, ItemStack> flowerSlots = new HashMap<>();
    private final Map<Integer, ItemStack> fuelSlots = new HashMap<>();
    private static Map<Integer, List<ItemUpgrade>> availableUpgrade;

    private boolean fuelInventoryEmpty = false;

    private int greenhouseSleep;

    private static final boolean isModAppbot = ModList.get().isLoaded("appbot");

    private static final ExportManaME exportManaME = ModList.get().isLoaded("appbot") ? new ExportManaME() : null;

    public BlockEntityGreenhouse(BlockEntityType<?> blockEntityTypeIn, BlockPos pos, BlockState state) {
        super(blockEntityTypeIn, pos, state, baseManaStorage);



        energy_capacity = LibXServerConfig.GreenhouseSettings.energyCapacity;
        energyTransfer = LibXServerConfig.GreenhouseSettings.energyTransfer;
        energyCost = LibXServerConfig.GreenhouseSettings.energyCost;

        greenhouseSleep = LibXServerConfig.GreenhouseSettings.sleep;

        flowerInventory = ResizeBaseItemStackHandler.builder(FLOWER_SIZE)
                .validator(GenFlowers::isFlower, Range.closedOpen(0, FLOWER_SIZE))
                .slotLimit(getSlotLimit(), Range.closedOpen(0, FLOWER_SIZE))
                .contentsChanged((slot) -> {this.setChanged();this.setDispatchable();this.flowerChanged(slot);})
                .build();

        fuelInventory = BaseItemStackHandler.builder(FUEL_SIZE)
                .validator(this::isCheckFuelSlot, Range.closedOpen(0, 7))
                .validator((stack) -> upgrade_slot_add && isCheckFuelSlot(stack), Range.closedOpen(7, FUEL_SIZE))
                .contentsChanged((slot) -> {this.setChanged();this.setDispatchable();this.fuelChanged(slot);})
                .build();

        upgradeInventory = BaseItemStackHandler.builder(UPGRADE_SIZE)
                .validator((itemStack -> isCheckUpgradeSlot(itemStack, 0)), 0)
                .validator((itemStack -> isCheckUpgradeSlot(itemStack, 1)), 1)
                .validator((itemStack -> isCheckUpgradeSlot(itemStack, 2)), 2)
                .validator((itemStack -> isCheckUpgradeSlot(itemStack, 3)), 3)
                .validator((itemStack -> isCheckUpgradeSlot(itemStack, 4)), 4)
                .validator((itemStack -> isCheckUpgradeSlot(itemStack, 5)), 5)
                .validator((itemStack -> isCheckUpgradeSlot(itemStack, 6)), 6)
                .validator((itemStack -> isCheckUpgradeSlot(itemStack, 7)), 7)
                .defaultSlotLimit(1)
                .contentsChanged((slot) -> {this.setChanged();this.setDispatchable();this.updateUpgradeSlot(slot);})
                .build();

        this.setChangedQueued = false;

        upgrade();
    }

    //region BASE
    public int getCountHeat(){
        return count_heat;
    }

    public int getMaxCountHeat(){
        return max_count_heat;
    }

    public void updateUpgradeSlot(int slot){
        if (this.upgradeInventory.getStackInSlot(slot).isEmpty()){
            switch (slot){
                case 0 -> {
                    flowerInventory.setSlotLimits(1);
                    this.slotLimit = 1;
                    dropLimitFlowers();
                }
                case 1 -> upgrade_gen_mana = false;
                case 2 -> upgrade_slot_add = false;
                case 3 -> upgrade_cost_energy = false;
                case 4 -> {
                    upgrade_speed_craft = 0;
                    greenhouseSleep = Math.min(greenhouseSleep, LibXServerConfig.GreenhouseSettings.sleep);
                }
                case 5 -> {
                    this.setMaxEnergyStored(baseEnergyStorage);
                    this.setEnergyStored(Math.min(this.getEnergyStored(), baseEnergyStorage));
                }
                case 6 -> {
                    this.setMaxMana(baseManaStorage);
                    this.setCurrentMana(Math.min(this.getCurrentMana(), baseManaStorage));
                }
                case 7 -> {
                    count_heat = 0;
                    upgrade_heat = false;
                }
            }

        } else {
            boolean stop = false;
            for (ItemUpgrade item: getUpgradeSlot(slot)){
                if (this.upgradeInventory.getStackInSlot(slot).getItem() == item){

                    switch (slot) {
                        case 0 -> {
                            this.slotLimit = item.getValueItem();
                            flowerInventory.setSlotLimits(this.slotLimit);
                            dropLimitFlowers();
                            stop = true;
                        }
                        case 1 ->{
                            upgrade_gen_mana = true;
                            stop = true;
                        }
                        case 2 -> {
                            upgrade_slot_add = true;
                            stop = true;
                        }
                        case 3 -> {
                            upgrade_cost_energy = true;
                            stop = true;
                        }
                        case 4 -> {
                            if (this.upgradeInventory.getStackInSlot(slot).getItem() == ModItems.upgradeTickGenMana_1) upgrade_speed_craft = ModItems.upgradeTickGenMana_1.getValueItem() / 100f;
                            else if (this.upgradeInventory.getStackInSlot(slot).getItem() == ModItems.upgradeTickGenMana_2) upgrade_speed_craft = ModItems.upgradeTickGenMana_2.getValueItem() / 100f;
                            stop = true;
                        }
                        case 5 -> {
                            this.setMaxEnergyStored(Math.min(baseEnergyStorage * item.getValueItem(), Integer.MAX_VALUE));
                            this.setEnergyStored(Math.min(this.getEnergyStored(), this.getMaxEnergyStored()));
                            stop = true;
                        }
                        case 6 -> {
                            this.setMaxMana(Math.min(baseManaStorage * item.getValueItem(), Integer.MAX_VALUE));
                            this.setCurrentMana(Math.min(this.getCurrentMana(), this.getMaxMana()));
                            stop = true;
                        }
                        case 7 -> upgrade_heat = true;
                    }
                }

                if (stop) break;
            }
        }

        this.setChanged();
        this.setDispatchable();
    }

    private int getSlotLimit(){
        return this.slotLimit;
    }

    public static void upgrade(){
        availableUpgrade = new HashMap<>();

        List<ItemUpgrade> upg = new ArrayList<>();
        upg.add(ModItems.upgradeFlower_4x);
        upg.add(ModItems.upgradeFlower_16x);
        upg.add(ModItems.upgradeFlower_32x);
        upg.add(ModItems.upgradeFlower_64x);
        availableUpgrade.put(0, upg);

        upg = new ArrayList<>();
        upg.add(ModItems.upgradeGenMana);
        availableUpgrade.put(1, upg);

        upg = new ArrayList<>();
        upg.add(ModItems.upgradeSlotAdd);
        availableUpgrade.put(2, upg);

        upg = new ArrayList<>();
        upg.add(ModItems.upgradeCostEnergy);
        availableUpgrade.put(3, upg);

        upg = new ArrayList<>();
        upg.add(ModItems.upgradeTickGenMana_1);
        upg.add(ModItems.upgradeTickGenMana_2);
        availableUpgrade.put(4, upg);

        upg = new ArrayList<>();
        upg.add(ModItems.upgradeStorageEnergy_1);
        upg.add(ModItems.upgradeStorageEnergy_2);
        upg.add(ModItems.upgradeStorageEnergy_3);
        availableUpgrade.put(5, upg);

        upg = new ArrayList<>();
        upg.add(ModItems.upgradeStorageMana_1);
        upg.add(ModItems.upgradeStorageMana_2);
        upg.add(ModItems.upgradeStorageMana_3);
        availableUpgrade.put(6, upg);

        upg = new ArrayList<>();
        upg.add(ModItems.upgradeHeatGreenhouse);
        availableUpgrade.put(7, upg);

    }

    public List<ItemUpgrade> getUpgradeSlot(int slot){
        return availableUpgrade.get(slot);
    }

    public boolean isCheckUpgradeSlot(ItemStack itemStack, int slot){
        if (itemStack.isEmpty()) return false;

        for (Item upgrade: availableUpgrade.get(slot)){
            if (upgrade == itemStack.getItem()){
                return true;
            }
        }
        return false;
    }

    public boolean isCheckFuelSlot(ItemStack fuel){
                for (Integer slot: flowerSlots.keySet()){
            if (GenFlowers.availableFuelItem(this.getFlowerInventory().getStackInSlot(slot), fuel)){
                return true;
            }
        }

        return false;
    }

    public void reloadFuelSlots(){
        if (flowerSlots.isEmpty()) {
            for (int slotFlower = 0; slotFlower < this.getFlowerInventory().getSlots(); slotFlower++){
                flowerChanged(slotFlower);
            }
        }
    }

    public void flowerChanged(int slot){
        if (!this.flowerInventory.getStackInSlot(slot).isEmpty()){
            ItemStack itemStack = this.flowerInventory.getStackInSlot(slot);
            flowerSlots.put(slot, itemStack);
        } else flowerSlots.remove(slot);
    }

    public void fuelChanged(int slot){
        if (!this.fuelInventory.getStackInSlot(slot).isEmpty()){
            ItemStack itemStack = this.fuelInventory.getStackInSlot(slot);
            fuelSlots.put(slot, itemStack);
        } else fuelSlots.remove(slot);
    }

    @NotNull
    @Override
    public <X> LazyOptional<X> getCapability(@NotNull Capability<X> cap, Direction direction) {
        if (cap == ForgeCapabilities.ENERGY) {
            return LazyOptional.of(() -> this).cast();
        }
        return super.getCapability(cap, direction);
    }

    @Override
    public void tick() {
        if (this.level != null && !this.level.isClientSide) {
            if (this.getMainNode() != null && !this.getMainNode().isReady()){
                this.getMainNode().create(this.level, this.getBlockPos());
            }

            if (greenhouseSleep <= 0){
                greenhouseSleep = upgrade_speed_craft != 0 ? (int)(LibXServerConfig.GreenhouseSettings.sleep - LibXServerConfig.GreenhouseSettings.sleep * upgrade_speed_craft) : LibXServerConfig.GreenhouseSettings.sleep;
            }

            greenhouseSleep--;

            // Export available mana to AE2 grid even when not working
            if (isModAppbot && exportManaME != null&& this.getMainNode() != null && this.getMainNode().getNode() != null && this.getMainNode().isOnline()){
                // Export all available mana, not just excess
                if (this.getCurrentMana() > 0) {
                    int manaToExport = this.getCurrentMana();
                    receiveMana(exportManaME.exportManaME(manaToExport, this.getMainNode().getNode().getGrid()));
                }
            }

            if (this.getMaxMana() == this.getCurrentMana() || this.getEnergyStored() < this.energyCost || fuelSlots.isEmpty()) {
                if (count_heat > 0 && greenhouseSleep <= 0){
                    count_heat--;

                    this.setChanged();
                    this.setDispatchable();
                }
                return;
            }

            if (greenhouseSleep <= 0){
                boolean craft = false;

                for (Integer flowerSlot: flowerSlots.keySet()){
                    ItemStack flower = flowerSlots.get(flowerSlot);

                    ArrayList<Object> a = getFuelSlot(flower);
                    if (a.size() == 2 && a.get(0) != null && ((Integer)a.get(1)) > -1){
                        GenFlowers genFlowers = (GenFlowers) a.get(0);
                        int fSlot = (Integer) a.get(1);

                        int[] b = receiveManaCraft(genFlowers, flower.getCount(), this.fuelInventory.getStackInSlot(fSlot).copy());
                        if (b != null){
                            int addMana = b[0];
                            int fuelExtract = b[1];
                            int removeEnergy = b[2];

                            IAdvancedItemHandlerModifiable inventory = this.fuelInventory.getUnrestricted();
                            inventory.extractItem(fSlot, fuelExtract, false);
                            this.receiveMana(addMana);
                            this.receiveEnergy(-removeEnergy, false);

                            craft = true;
                        }
                    }
                }

                if (isModAppbot && exportManaME != null&& this.getMainNode() != null && this.getMainNode().getNode() != null && this.getMainNode().isOnline()){
                    int threshold = (int)(this.getMaxMana() * 0.95);
                    if (this.getCurrentMana() > threshold) {
                        int excessMana = this.getCurrentMana() - threshold;
                        receiveMana(exportManaME.exportManaME(excessMana, this.getMainNode().getNode().getGrid()));
                    }
                }

                if (craft && upgrade_heat) {
                    count_heat = Math.min(count_heat + 1, 40);
                } else if (upgrade_heat) {
                    count_heat = Math.max(count_heat - 1, 0);
                }

                this.setChanged();
                this.setDispatchable();
            }

        } else if (LibXClientConfig.RenderingVisualContent.all && LibXClientConfig.RenderingVisualContent.greenhouse) {
            double particleChance = (double)this.getCurrentMana() / (double)this.getMaxMana() * 0.1;
            if (Math.random() < particleChance) {
                float red = 0.0F;
                float green = 0.7764706F;
                float blue = 1.0F;

                for (int i = 0; i < 2; i++){
                    WispParticleData data = WispParticleData.wisp((float)Math.random() / 3.0F, red, green, blue, 2.0F);
                    assert this.level != null;
                    this.level.addParticle(data, (double)this.worldPosition.getX() + 0.3 + this.level.random.nextDouble() * 0.4, (double)this.worldPosition.getY() + 0.5 + this.level.random.nextDouble() * 0.25, (double)this.worldPosition.getZ() + 0.3 + this.level.random.nextDouble() * 0.4, 0.0, this.level.random.nextFloat() / 25.0F, 0.0);
                }
            }
        }
    }

    private ArrayList<Object> getFuelSlot(ItemStack flower){
        ArrayList<Object> res = new ArrayList<>();

        for (Integer fuelSlot: fuelSlots.keySet()){
            if (GenFlowers.availableFuelItem(flower, fuelSlots.get(fuelSlot))){
                res.add(GenFlowers.getFlowerIsFuel(flower, fuelSlots.get(fuelSlot)));
                res.add(fuelSlot);
                break;
            }
        }

        return res;
    }

    private int[] receiveManaCraft(GenFlowers genFlowers, int flower, ItemStack fuel){
        int manaSpace = this.getMaxMana() - this.getCurrentMana();
        if (manaSpace <= 0) return null;

        double heatMultiplier = count_heat >= 10 && count_heat <= 40 ? heatList[(count_heat / 10) - 1] : 1;
        double manaMultiplier = (upgrade_gen_mana ? 1.25d : 1d) * heatMultiplier;
        double energyMultiplier = (upgrade_gen_mana ? 2d : 1d) * (upgrade_cost_energy ? 0.5d : 1d);

        long manaPerFuel = Math.round(genFlowers.getPerMana(fuel) * manaMultiplier);
        long energyPerFuel = Math.round(this.energyCost * energyMultiplier);

        if (manaPerFuel <= 0 || energyPerFuel <= 0 || this.getEnergyStored() < energyPerFuel) return null;

        int fuelAvailable = Math.min(fuel.getCount(), flower);

        long craftableByEnergy = this.getEnergyStored() / energyPerFuel;

        // Do not allow partial consumption that would overproduce mana.
        // Previously there was a special case that allowed crafting 1 unit even if manaPerFuel > manaSpace.
        // Remove that: require at least one full unit to fit into the remaining mana space.
        long craftCount = manaSpace / manaPerFuel;
        craftCount = Math.min(craftCount, craftableByEnergy);
        craftCount = Math.min(craftCount, fuelAvailable);

        if (craftCount <= 0) return null;

        int fuelCountExtract = (int) craftCount;
        int addMana = (int) Math.min(manaSpace, manaPerFuel * craftCount);
        int removeEnergy = (int) Math.min(Integer.MAX_VALUE, energyPerFuel * craftCount);

        return new int[] {addMana, fuelCountExtract, removeEnergy};
    }

    @Override
    protected Predicate<Integer> getExtracts(Supplier<IItemHandlerModifiable> supplier) {
        return (slot) -> false;
    }

    @NotNull
    @Override
    public BaseItemStackHandler getInventory() {
        return this.fuelInventory;
    }

    public ResizeBaseItemStackHandler getFlowerInventory(){
        return this.flowerInventory;
    }

    public BaseItemStackHandler getUpgradeInventory() {
        return this.upgradeInventory;
    }

    @Override
    public int getComparatorOutput() {
        return 0;
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        super.load(nbt);

        this.getFlowerInventory().deserializeNBT(nbt.getCompound("invFlower"));
        this.getUpgradeInventory().deserializeNBT(nbt.getCompound("invUpgrade"));
        this.fuelInventoryEmpty = nbt.getBoolean("fuelInventoryEmpty");
        this.energy = nbt.getInt("energyStorage");
        this.energy_capacity = nbt.getInt("energyCapacity");
        this.count_heat = nbt.getInt("countHeat");

        if (this.getMainNode() != null) this.getMainNode().loadFromNBT(nbt);

        this.reloadFuelSlots();

        for (int i = 0; i < this.getUpgradeInventory().getSlots(); i++){
            updateUpgradeSlot(i);
        }
        for (int i = 0; i < this.getInventory().getSlots(); i++){
            fuelChanged(i);
        }

        this.setChanged();
        this.setDispatchable();
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag nbt) {
        super.saveAdditional(nbt);

        nbt.put("invFlower", this.getFlowerInventory().serializeNBT());
        nbt.put("invUpgrade", this.getUpgradeInventory().serializeNBT());
        nbt.putBoolean("fuelInventoryEmpty", this.fuelInventoryEmpty);
        nbt.putInt("energyStorage", this.energy);
        nbt.putInt("energyCapacity", this.energy_capacity);
        nbt.putInt("countHeat", this.count_heat);
        if (this.getMainNode() != null) this.getMainNode().saveToNBT(nbt);
    }

    @Override
    public void handleUpdateTag(CompoundTag nbt) {
        super.handleUpdateTag(nbt);
        if (this.level == null || this.level.isClientSide) {
            this.getFlowerInventory().deserializeNBT(nbt.getCompound("invFlower"));
            this.getUpgradeInventory().deserializeNBT(nbt.getCompound("invUpgrade"));
            this.fuelInventoryEmpty = nbt.getBoolean("fuelInventoryEmpty");
            this.energy = nbt.getInt("energyStorage");
            this.energy_capacity = nbt.getInt("energyCapacity");
            this.count_heat = nbt.getInt("countHeat");

            for (int i = 0; i < this.getUpgradeInventory().getSlots(); i++){
                updateUpgradeSlot(i);
            }
        }
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        if (this.level != null && this.level.isClientSide) {
            return super.getUpdateTag();
        } else {
            CompoundTag nbt = super.getUpdateTag();
            nbt.put("invFlower", this.getFlowerInventory().serializeNBT());
            nbt.put("invUpgrade", this.getUpgradeInventory().serializeNBT());
            nbt.putBoolean("fuelInventoryEmpty", this.fuelInventoryEmpty);
            nbt.putInt("energyStorage", this.energy);
            nbt.putInt("energyCapacity", this.energy_capacity);
            nbt.putInt("countHeat", this.count_heat);

            return nbt;
        }
    }

    public void drops(){
        if (this.level == null) return;

        IAdvancedItemHandlerModifiable inventoryUpgrade = this.getUpgradeInventory().getUnrestricted();
        IAdvancedItemHandlerModifiable inventoryFlower = this.getFlowerInventory().getUnrestricted();

        for (int i = 0; i < inventoryUpgrade.getSlots(); i++){
            ItemStack itemStack = inventoryUpgrade.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;
            ItemEntity ie = new ItemEntity(this.level, (double)this.worldPosition.getX() + 0.5, (double)this.worldPosition.getY() + 0.7, (double)this.worldPosition.getZ() + 0.5, itemStack.copy());
            this.level.addFreshEntity(ie);
        }

        for (int i = 0; i < inventoryFlower.getSlots(); i++){
            ItemStack itemStack = inventoryFlower.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;
            ItemEntity ie = new ItemEntity(this.level, (double)this.worldPosition.getX() + 0.5, (double)this.worldPosition.getY() + 0.7, (double)this.worldPosition.getZ() + 0.5, itemStack.copy());
            this.level.addFreshEntity(ie);
        }
    }

    private void dropLimitFlowers(){
        if (this.level == null) return;

        IAdvancedItemHandlerModifiable inventoryFlower = this.getFlowerInventory().getUnrestricted();
        for (int i = 0; i < flowerInventory.getSlots(); i++){
            ItemStack itemStack = inventoryFlower.getStackInSlot(i).copy();
            if (itemStack.isEmpty()) continue;

            itemStack.setCount(itemStack.getCount() - this.slotLimit);

            inventoryFlower.extractItem(i, itemStack.getCount(), false);
            ItemEntity ie = new ItemEntity(this.level, (double)this.worldPosition.getX() + 0.5, (double)this.worldPosition.getY() + 0.7, (double)this.worldPosition.getZ() + 0.5, itemStack);
            this.level.addFreshEntity(ie);
        }
    }

    public void setRemoved() {
        super.setRemoved();

        if (this.getMainNode() != null) {
            this.getMainNode().destroy();
        }
    }

    //endregion

    //region ENERGY
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!canReceive())
            return 0;

        if (maxReceive >= 0 && this.getMaxEnergyStored() == this.getEnergyStored()) return 0;

        int energyReceived = Math.min(getMaxEnergyStored() - energy, Math.min(this.energyTransfer, maxReceive));
        if (!simulate)
            energy += energyReceived;

        this.setChanged();
        this.setDispatchable();

        return energyReceived;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return this.energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return energy_capacity;
    }

    private void setEnergyStored(int val){
        energy = val;
    }

    private void setMaxEnergyStored(int val){
        energy_capacity = val;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return this.energyTransfer > 0;
    }
    //endregion

    //region AE INTEGRATION

    private boolean setChangedQueued;

    private final IManagedGridNode mainNode = ModList.get().isLoaded("appbot") ? this.createMainNode().setVisualRepresentation(this.getItemFromBlockEntity()).setInWorldNode(true).setTagName("proxy") : null;


    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, BlockEntityNodeListener.INSTANCE);
    }

    protected Item getItemFromBlockEntity() {
        return ModBlocks.greenhouse.asItem();
    }

    private void setChangedAtEndOfTick(Level level) {
        this.setChanged();
        this.setChangedQueued = false;
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction direction) {
        if (this.getMainNode() == null) return null;

        return this.getMainNode().getNode();
    }

    @Override
    public IManagedGridNode getMainNode() {
        return this.mainNode;
    }

    @Override
    public void saveChanges() {
        if (level != null) {
            if (level.isClientSide) {
                this.setChanged();
            } else {
                this.level.blockEntityChanged(this.worldPosition);
                if (!this.setChangedQueued) {
                    TickHandler.instance().addCallable(null, this::setChangedAtEndOfTick);
                    this.setChangedQueued = true;
                }
            }
        }
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }
    //endregion
}
