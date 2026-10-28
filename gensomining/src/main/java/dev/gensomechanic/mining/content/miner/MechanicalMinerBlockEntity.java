package dev.gensomechanic.mining.content.miner;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.gensomechanic.mining.content.vein.*;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Server-authoritative cycle state. Input payment and output rolls each happen exactly once. */
public final class MechanicalMinerBlockEntity extends KineticBlockEntity {
    public enum Status { INCOMPLETE, NO_RECIPE, STOPPED, OVERSTRESSED, OUTPUT_FULL, MINING }
    private final MinerPresentation presentation = new MinerPresentation();
    private SmartFluidTankBehaviour lubricant;
    private boolean assembled, paid, dirty, rubbleVisible, planning, rockBackground;
    private double progress, efficiency = 1, duration, capacityCost = 1;
    private float stress;
    private int capacity, outputVersion;
    private long preparedTick = Long.MIN_VALUE;
    private dev.gensomechanic.mining.content.vein.VeinQuery plannedQuery;
    private java.util.Optional<VeinGenerator.Occurrence> plannedVein;
    private String fingerprint = "", paidLubricant = "", paidExplosive = "";
    private VeinCapacityTracker.Key veinIdentity;
    private String veinSignature = "";
    private RecipeHolder<MiningRecipe> recipe;
    private MiningModifiers.Operation operation;
    private ResourceLocation displayBlock;
    private Component veinName = Component.empty();
    private Status status = Status.INCOMPLETE;
    private long portEpoch;
    private int checkedOutputVersion = -1;
    private MiningModifiers.Operation checkedOperation;
    private boolean resultsFit;
    private static java.util.function.Consumer<MechanicalMinerBlockEntity> effects = be -> {};
    private final ItemStackHandler explosive = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return acceptsExplosive(stack); }
        @Override protected void onContentsChanged(int slot) { changed(); }
    };
    private final ItemStackHandler output = new MinerOutputInventory() {
        @Override protected void onContentsChanged(int slot) { outputVersion++; changed(); }
        @Override protected void onLoad() { outputVersion++; }
    };
    public MechanicalMinerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); setLazyTickRate(20); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        lubricant = SmartFluidTankBehaviour.single(this, 1000); behaviours.add(lubricant);
    }
    private void changed() { dirty = true; setChanged(); }
    public void revokePorts() {
        portEpoch++;
        invalidateCapabilities();
        if (level == null) return;
        Direction facing = MinerStructure.facing(getBlockState());
        for (int part : new int[]{MinerStructure.LUBRICANT, MinerStructure.LUBRICANT_TANK, MinerStructure.EXPLOSIVE, MinerStructure.OUTPUT}) {
            BlockPos port = worldPosition.offset(MinerStructure.offset(part, facing));
            if (level.hasChunkAt(port)) level.invalidateCapabilities(port);
        }
    }
    public long portEpoch() { return portEpoch; }
    public boolean live() {
        return level != null && !isRemoved() && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this
                && !MinerStructure.editing(level, worldPosition) && MinerStructure.complete(level, worldPosition, MinerStructure.facing(getBlockState()));
    }
    @Override public void tick() {
        super.tick();
        if (level == null || isRemoved()) return;
        if (level.isClientSide) { effects.accept(this); return; }
        ServerLevel server = (ServerLevel) level;
        var tracker = VeinManager.capacity(server);
        tracker.register(worldPosition, this::prepare, this::eligible, () -> capacityCost, () -> veinIdentity);
        long tick = server.getServer().getTickCount();
        if (preparedTick != tick) prepare();
        Status previous = status; status = currentStatus();
        if (status == Status.MINING && tracker.activeThisTick(worldPosition, tick)) {
            efficiency = VeinCapacityTracker.efficiencyLimit(capacity, tracker.occupiedCapacity(veinIdentity));
            double work = processingSpeed(getSpeed()) * efficiency;
            while (work > 1e-9 && canFitResults()) {
                // Another combination needs a new stress/capacity snapshot before doing any work.
                if (!paid && (select() != operation || !pay())) break;
                double step = Math.min(work, Math.max(0, duration - progress));
                progress += step; work -= step;
                if (step > 0 && !presentation.hasWorked()) { presentation.worked(); dirty = true; }
                if (progress + 1e-9 < duration) break;
                boolean empty = outputEmpty();
                for (ItemStack stack : operation.roll(server.random)) {
                    if (!ItemHandlerHelper.insertItemStacked(output, stack, false).isEmpty())
                        throw new IllegalStateException("Mining output reservation failed");
                }
                presentation.produced(animationTime(), empty, outputEmpty());
                resetCycle();
            }
            changed();
        }
        if (previous != status || dirty || animationTime() % 20 == 0) { dirty = false; sendData(); }
    }
    private void resetCycle() { progress = 0; paid = false; paidLubricant = paidExplosive = ""; }
    private void prepare() {
        if (!(level instanceof ServerLevel server) || isRemoved() || MinerStructure.editing(level, worldPosition)) return;
        preparedTick = server.getServer().getTickCount();
        boolean complete = live();
        if (assembled != complete) { assembled = complete; revokePorts(); notifyUpdate(); }
        var query=VeinManager.query(server);
        if(plannedQuery!=query){plannedQuery=query;plannedVein=null;}
        var ready=plannedVein==null?dev.gensomechanic.mining.content.vein.VeinPlanningScheduler.miner(server,new ChunkPos(worldPosition))
            :java.util.Optional.of(query.metadata(plannedVein));
        ready.ifPresent(value->plannedVein=value);
        if (planning != ready.isEmpty()) { planning = ready.isEmpty(); dirty = true; }
        if (planning) { rubbleVisible = false; return; }
        var occurrence = ready.orElseThrow();
        var target = MiningTarget.resolve(server, worldPosition, occurrence);
        veinIdentity = target == null ? null : target.identity();
        rockBackground = target != null && target.background();
        String nextVein = target == null ? "" : target.signature();
        if (!nextVein.equals(veinSignature)) { resetCycle(); veinSignature = nextVein; dirty = true; }
        var selected = target == null ? null : MiningRecipes.forServer(server.getServer()).get(target.id());
        if (selected != recipe || selected == null && !fingerprint.isEmpty()) {
            String next = selected == null ? "" : selected.id() + "|" + selected.value().fingerprint(server.registryAccess());
            if (!next.equals(fingerprint)) resetCycle();
            fingerprint = next; recipe = selected;
            displayBlock = selected == null ? null : selected.value().displayBlock();
            dirty = true;
        }
        boolean preview = assembled && target != null && recipe != null;
        if (rubbleVisible != preview) { rubbleVisible = preview; dirty = true; }
        capacity = target == null ? 0 : target.capacity();
        veinName = target == null ? Component.empty() : target.name();
        MiningModifiers.Operation next = null;
        if (recipe != null) {
            next = paid ? recipe.value().modifiers().operation(paidLubricant, paidExplosive) : select();
            if (next == null) { resetCycle(); next = select(); }
        }
        if (next != operation) {
            operation = next; duration = next == null ? 0 : next.duration(); capacityCost = next == null ? 1 : next.capacityCost(); dirty = true;
        }
        float impact = operation == null ? 0 : operation.stress();
        // Re-evaluate even while overstressed: a new lubricant can restore the network.
        if (stress != impact || lastStressApplied != impact) {
            stress = impact;
            if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
            else lastStressApplied = impact;
            notifyUpdate();
        }
    }
    private MiningModifiers.Operation select() { return recipe == null ? null : recipe.value().modifiers().select(lubricantFluid(), explosiveStack()); }
    private boolean pay() {
        var l = operation.lubricant(); var e = operation.explosive();
        FluidStack fluid = lubricantFluid(); ItemStack item = explosiveStack();
        if (l != null && !l.available(fluid, item) || e != null && !e.available(fluid, item)) return false;
        paid = true; paidLubricant = operation.lubricantId(); paidExplosive = operation.explosiveId();
        if (l != null) lubricant.getPrimaryHandler().drain(l.fluid().amount(), FluidAction.EXECUTE);
        if (e != null) presentation.consumed(animationTime(), explosive.extractItem(0, e.count(), false));
        changed(); return true;
    }
    private Status currentStatus() {
        if (planning || !assembled || !live()) return Status.INCOMPLETE;
        if (operation == null) return Status.NO_RECIPE;
        if (isOverStressed()) return Status.OVERSTRESSED;
        if (getSpeed() == 0) return Status.STOPPED;
        return canFitResults() ? Status.MINING : Status.OUTPUT_FULL;
    }
    private boolean eligible() { return currentStatus() == Status.MINING; }
    public boolean canFitResults() {
        if (operation == null) return false;
        if (checkedOperation == operation && checkedOutputVersion == outputVersion) return resultsFit;
        checkedOperation = operation; checkedOutputVersion = outputVersion; resultsFit = false;
        ItemStackHandler simulated = new MinerOutputInventory();
        for (int i = 0; i < 9; i++) simulated.setStackInSlot(i, outputInSlot(i));
        for (var result : operation.outputs()) if (result.chance() > 0 && !ItemHandlerHelper.insertItemStacked(simulated, result.stack(), false).isEmpty()) return false;
        return resultsFit = true;
    }
    @Override public float calculateStressApplied() { return lastStressApplied = stress; }
    public static int processingSpeed(float rpm) { return rpm == 0 ? 0 : Mth.clamp((int) Math.abs(rpm / 16), 1, 512); }
    public boolean acceptsExplosive(ItemStack stack) {
        return level instanceof ServerLevel server && MiningRecipes.forServer(server.getServer()).values().stream()
                .flatMap(r -> r.value().modifiers().explosives().stream()).anyMatch(m -> m.accepts(stack));
    }
    public boolean acceptsLubricant(FluidStack stack) {
        return level instanceof ServerLevel server && MiningRecipes.forServer(server.getServer()).values().stream()
                .flatMap(r -> r.value().modifiers().lubricants().stream()).anyMatch(m -> m.accepts(stack));
    }
    public ItemStack insertExplosive(ItemStack stack, boolean simulate) { return explosive.insertItem(0, stack, simulate); }
    public ItemStack extractExplosive(int count, boolean simulate) { return explosive.extractItem(0, count, simulate); }
    public ItemStack extractOutput(int slot, int count, boolean simulate) {
        return presentation.outputLocked(animationTime()) ? ItemStack.EMPTY : output.extractItem(slot, count, simulate);
    }
    public ItemStack outputInSlot(int slot) { return output.getStackInSlot(slot).copy(); }
    public ItemStack explosiveStack() { return explosive.getStackInSlot(0).copy(); }
    public FluidStack lubricantFluid() { return lubricant.getPrimaryHandler().getFluid().copy(); }
    public int fillLubricant(FluidStack stack, FluidAction action) { return acceptsLubricant(stack) ? lubricant.getPrimaryHandler().fill(stack, action) : 0; }
    public FluidStack drainLubricant(int amount, FluidAction action) { return lubricant.getPrimaryHandler().drain(amount, action); }
    public FluidStack drainLubricant(FluidStack stack, FluidAction action) { return lubricant.getPrimaryHandler().drain(stack, action); }
    private boolean outputEmpty() { for (int i = 0; i < 9; i++) if (!output.getStackInSlot(i).isEmpty()) return false; return true; }
    public void dropOutput() {
        if (level == null || level.isClientSide) return;
        revokePorts();
        ItemStack item = explosive.getStackInSlot(0); explosive.setStackInSlot(0, ItemStack.EMPTY); drop(item);
        for (int i = 0; i < 9; i++) { item = output.getStackInSlot(i); output.setStackInSlot(i, ItemStack.EMPTY); drop(item); }
    }
    private void drop(ItemStack stack) { Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack); }
    @Override public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide && !MinerStructure.editing(level, worldPosition)
                && MinerStructure.loaded(level, worldPosition) && !live()) level.destroyBlock(worldPosition, true);
    }
    @Override public void invalidate() {
        if (level instanceof ServerLevel server) VeinManager.unregisterMiner(server, worldPosition);
        revokePorts(); super.invalidate();
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Assembled", assembled); tag.putBoolean("Paid", paid);
        tag.putDouble("Progress", progress); tag.putDouble("Duration", duration); tag.putDouble("Efficiency", efficiency);
        tag.putDouble("CapacityCost", capacityCost); tag.putFloat("RecipeStress", stress);
        tag.putString("LubricantChoice", paidLubricant); tag.putString("ExplosiveChoice", paidExplosive);
        tag.put("Explosive", explosive.serializeNBT(registries)); tag.put("Output", output.serializeNBT(registries));
        tag.put("Presentation", presentation.write(registries)); tag.putString("Status", status.name());
        tag.putString("VeinName", Component.Serializer.toJson(veinName, registries));
        tag.putBoolean("RubbleVisible", rubbleVisible);
        tag.putBoolean("RockBackground", rockBackground);
        if (displayBlock != null) tag.putString("DisplayBlock", displayBlock.toString());
        if (!clientPacket) { tag.putString("Fingerprint", fingerprint); tag.putString("VeinSignature", veinSignature); }
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        assembled = tag.getBoolean("Assembled"); paid = tag.getBoolean("Paid");
        progress = bounded(tag.getDouble("Progress"), 0, 72000512, 0);
        duration = bounded(tag.getDouble("Duration"), 0, 72e6, 0);
        efficiency = bounded(tag.getDouble("Efficiency"), 0, 1, 1);
        capacityCost = bounded(tag.getDouble("CapacityCost"), 1e-6, 1e6, 1);
        stress = (float) bounded(tag.getFloat("RecipeStress"), 0, 16384, 0);
        paidLubricant = tag.getString("LubricantChoice"); paidExplosive = tag.getString("ExplosiveChoice");
        explosive.deserializeNBT(registries, tag.getCompound("Explosive")); output.deserializeNBT(registries, tag.getCompound("Output"));
        presentation.read(tag.getCompound("Presentation"), registries);
        rubbleVisible = tag.getBoolean("RubbleVisible");
        rockBackground = tag.getBoolean("RockBackground");
        displayBlock = ResourceLocation.tryParse(tag.getString("DisplayBlock"));
        try { status = Status.valueOf(tag.getString("Status")); } catch (IllegalArgumentException ignored) { status = Status.INCOMPLETE; }
        if (tag.contains("VeinName")) veinName = Objects.requireNonNullElse(Component.Serializer.fromJson(tag.getString("VeinName"), registries), Component.empty());
        if (!clientPacket) { fingerprint = tag.getString("Fingerprint"); veinSignature = tag.getString("VeinSignature"); recipe = null; operation = null; preparedTick = Long.MIN_VALUE; revokePorts(); }
    }
    private static double bounded(double n, double min, double max, double fallback) { return Double.isFinite(n) && n >= min && n <= max ? n : fallback; }
    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        super.addToGoggleTooltip(tooltip, sneaking);
        if (status != Status.STOPPED) CreateLang.text("").add(Component.translatable("miner.gensomining.status." + status.name().toLowerCase(Locale.ROOT))).style(ChatFormatting.GRAY).forGoggles(tooltip);
        if (duration > 0) {
            CreateLang.text("").add(Component.translatable(rockBackground ? "miner.gensomining.rock_background" : "miner.gensomining.vein", veinName)).forGoggles(tooltip);
            CreateLang.text("").add(Component.translatable("miner.gensomining.progress", Component.literal(Math.clamp((int)(100 * progress / duration), 0, 100) + "%").withStyle(ChatFormatting.GREEN), Component.literal(Math.round(efficiency * 100) + "%").withStyle(ChatFormatting.GREEN))).style(ChatFormatting.WHITE).forGoggles(tooltip);
            CreateLang.text("").add(Component.translatable("miner.gensomining.capacity_cost", Component.literal(MiningNumbers.format(capacityCost)).withStyle(ChatFormatting.GREEN))).style(ChatFormatting.WHITE).forGoggles(tooltip);
            if (paid && (!paidLubricant.isEmpty() || !paidExplosive.isEmpty())) CreateLang.text("").add(Component.translatable("miner.gensomining.modifiers", Component.translatable("miner.gensomining.modifiers." +
                    (!paidLubricant.isEmpty() && !paidExplosive.isEmpty() ? "both" : paidLubricant.isEmpty() ? "explosive" : "lubricant")).withStyle(ChatFormatting.GREEN))).style(ChatFormatting.WHITE).forGoggles(tooltip);
        }
        containedFluidTooltip(tooltip, sneaking, lubricant.getCapability());
        if (!explosiveStack().isEmpty()) CreateLang.text("").add(Component.translatable("miner.gensomining.explosive_stock", explosiveStack().getHoverName(), explosiveStack().getCount())).forGoggles(tooltip);
        return true;
    }
    public MinerPresentation presentation() { return presentation; }
    public long animationTime() { return level == null ? 0 : level.getGameTime(); }
    public boolean rubbleVisible() { return assembled && rubbleVisible; }
    public boolean assembled() { return assembled; }
    public SmartFluidTankBehaviour lubricantTank() { return lubricant; }
    public Status status() { return status; }
    public double progress() { return progress; }
    public double efficiency() { return efficiency; }
    public double capacityCost() { return capacityCost; }
    public double miningDuration() { return duration; }
    public boolean cycleStarted() { return paid; }
    public int outputVersion() { return outputVersion; }
    public BlockState displayState() { return displayBlock != null ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(displayBlock).defaultBlockState()
            : level != null && level.hasChunkAt(worldPosition.below(2)) ? level.getBlockState(worldPosition.below(2)) : Blocks.AIR.defaultBlockState(); }
    public static void registerClientEffects(java.util.function.Consumer<MechanicalMinerBlockEntity> handler) { effects = handler; }
    @Override protected AABB createRenderBoundingBox() { return MinerStructure.bounds(worldPosition).inflate(.3); }
}
