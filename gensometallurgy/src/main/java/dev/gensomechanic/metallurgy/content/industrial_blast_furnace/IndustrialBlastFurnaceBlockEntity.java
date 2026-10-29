// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.content.industrial_blast_furnace;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import com.simibubi.create.foundation.utility.SameSizeCombinedInvWrapper;
import dev.gensomechanic.metallurgy.GensoMetallurgy;
import dev.gensomechanic.metallurgy.foundation.GoggleTooltipHelper;
import dev.gensomechanic.metallurgy.content.metallurgy.MetallurgyRecipeTypes;
import dev.gensomechanic.metallurgy.content.metallurgy.MetallurgyStoragePort;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.capabilities.*;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlock.BOTTOM;
import static dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlock.SHAPE;
import static dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlock.TOP;
import static dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlock.WINDOW;
import static dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlock.WINDOW_CONNECTION;

public class IndustrialBlastFurnaceBlockEntity extends SmartBlockEntity
    implements IMultiBlockEntityContainer.Inventory, IHaveGoggleInformation {

    public static final int MAX_WIDTH = 3;
    public static final int HEIGHT_PER_WIDTH = 3;
    public static final int FLUID_CAPACITY_PER_BLOCK = 1000;
    public static final int MAX_FLUID_TYPES = 9;
    private static final int DEFAULT_PROCESSING_TIME = 200;
    private static final int RECIPE_CHECK_INTERVAL = 10;
    private static final double WALL_INSET = 3 / 16.0;
    private static final double FLOOR_HEIGHT = 4 / 16.0;
    private static final Set<ResourceLocation> FAILED_HEAT_PROVIDERS = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED_HEAT_CHECKS = ConcurrentHashMap.newKeySet();

    private boolean updateConnectivity;
    private boolean readingStorage;
    private boolean windowPreference;
    private BlockPos controller;
    private BlockPos lastKnownPos;
    private int width;
    private int height;
    private int smeltingRecipeCheckCooldown;
    private final SmeltingTask smeltingTask = new SmeltingTask();
    private final ProcessingState fusingProcess = new ProcessingState();
    private HeatDisplayState heatDisplayState = HeatDisplayState.UNHEATED;
    private String customHeatCondition = "";
    private final LerpedFloat heatGauge = LerpedFloat.linear();
    private long heatProbeCacheTick = Long.MIN_VALUE;
    private List<HeatProbe> cachedHeatProbes = List.of();

    private final ItemStackHandler localItemInventory;
    private final SharedCapacityFluidTank localFluidInventory;
    private final IndustrialBlastFurnaceFluidHandler fluidInventory;
    private StructureStorage storage;
    private MetallurgyStoragePort storagePort;
    private boolean storageCapabilitiesValid = true;

    public IndustrialBlastFurnaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        width = 1;
        height = 1;

        localItemInventory = new ItemStackHandler(1) {
            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                ItemStack stored = stack.copy();
                stored.setCount(Math.min(stored.getCount(), 1));
                super.setStackInSlot(slot, stored);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onLoad() {
                ItemStack stored = getStackInSlot(0);
                if (stored.getCount() > 1)
                    stored.setCount(1);
            }

            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                onItemContentsChanged();
            }
        };
        localFluidInventory = new SharedCapacityFluidTank(FLUID_CAPACITY_PER_BLOCK, MAX_FLUID_TYPES,
            this::onFluidContentsChanged);
        fluidInventory = new IndustrialBlastFurnaceFluidHandler(this::getConnectedFluidInventories,
            () -> getTotalBlockCount() * FLUID_CAPACITY_PER_BLOCK, MAX_FLUID_TYPES);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    protected void updateConnectivity() {
        updateConnectivity = false;
        if (level == null || level.isClientSide || !isController())
            return;
        ConnectivityHandler.formMulti(this);
        // A detached single block does not receive notifyMultiUpdated from Create.
        if (isController())
            normalizeWindowLayout();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null)
            return;

        if (lastKnownPos == null)
            lastKnownPos = getBlockPos();
        else if (!lastKnownPos.equals(worldPosition)) {
            removeController(true);
            lastKnownPos = worldPosition;
            return;
        }

        if (updateConnectivity)
            updateConnectivity();
        if (level.isClientSide) {
            if (isController()) {
                heatGauge.chase(getHeatGaugeTarget(), .125f, Chaser.EXP);
                heatGauge.tickChaser();
            }
            tickClientProcessingProgress();
            return;
        }

        if (isController()) {
            updateHeatDisplayState();
            tickRecipeProcessing();
        }
    }

    private void tickClientProcessingProgress() {
        if (smeltingTask.processingTicks > 0)
            smeltingTask.processingTicks--;
        if (isController() && fusingProcess.processingTicks > 0)
            fusingProcess.processingTicks--;
    }

    private void tickRecipeProcessing() {
        List<IndustrialBlastFurnaceBlockEntity> parts = getConnectedParts();
        if (parts.size() != getTotalBlockCount())
            return;

        tickSmeltingTasks(parts);
        tickFusingProcess(parts);
        scheduleSmeltingTasks(parts);
    }

    private void tickSmeltingTasks(List<IndustrialBlastFurnaceBlockEntity> parts) {
        for (IndustrialBlastFurnaceBlockEntity owner : parts) {
            SmeltingTask task = owner.smeltingTask;
            if (!task.isActive())
                continue;

            Optional<BlastFurnaceSmeltingRecipe> activeRecipe =
                getRecipe(task.activeRecipeId, BlastFurnaceSmeltingRecipe.class);
            if (activeRecipe.isEmpty() || !hasRequiredHeat(activeRecipe.get())
                || !validateExactItemClaims(parts, task.itemClaims)) {
                clearSmeltingTask(owner);
                continue;
            }

            if (task.processingTicks > 0) {
                task.processingTicks--;
                if (task.processingTicks > 0 && task.processingTicks % 20 == 0)
                    markTaskChanged(owner);
            }
            if (task.processingTicks > 0)
                continue;

            if (!task.outputsRolled) {
                task.pendingItemOutputs = rollItemOutputs(activeRecipe.get(), 1);
                task.outputsRolled = true;
                markTaskChanged(owner);
            }

            if (consumeExactInputs(activeRecipe.get(), 1, task.itemClaims, parts).isEmpty()) {
                clearSmeltingTask(owner);
                continue;
            }

            Optional<RecipeTransaction> transaction = createFixedBatchTransaction(activeRecipe.get(), 1,
                task.itemClaims, task.pendingItemOutputs, parts);
            if (transaction.isEmpty())
                continue;

            commitRecipeTransaction(transaction.get());
            clearSmeltingTask(owner);
            smeltingRecipeCheckCooldown = 0;
            fusingProcess.recipeCheckCooldown = 0;
        }
    }

    private void tickFusingProcess(List<IndustrialBlastFurnaceBlockEntity> parts) {
        if (fusingProcess.activeRecipeId != null) {
            Optional<BlastFurnaceFusingRecipe> activeRecipe =
                getRecipe(fusingProcess.activeRecipeId, BlastFurnaceFusingRecipe.class);
            if (activeRecipe.isEmpty() || !hasRequiredHeat(activeRecipe.get())
                || !validateExactItemClaims(parts, fusingProcess.itemClaims)) {
                stopFusingProcessing();
                return;
            }

            if (fusingProcess.processingTicks > 0) {
                fusingProcess.processingTicks--;
                if (fusingProcess.processingTicks > 0 && fusingProcess.processingTicks % 20 == 0) {
                    setChanged();
                    notifyUpdate();
                }
            }
            if (fusingProcess.processingTicks > 0)
                return;

            if (!fusingProcess.outputsRolled) {
                fusingProcess.pendingItemOutputs =
                    rollItemOutputs(activeRecipe.get(), fusingProcess.parallelBatches);
                fusingProcess.outputsRolled = true;
                setChanged();
                sendData();
            }

            if (consumeExactInputs(activeRecipe.get(), fusingProcess.parallelBatches,
                fusingProcess.itemClaims, parts).isEmpty()) {
                stopFusingProcessing();
                return;
            }

            Optional<RecipeTransaction> transaction = createFixedBatchTransaction(activeRecipe.get(),
                fusingProcess.parallelBatches, fusingProcess.itemClaims,
                fusingProcess.pendingItemOutputs, parts);
            if (transaction.isEmpty())
                return;

            commitRecipeTransaction(transaction.get());
            stopFusingProcessing();
            fusingProcess.recipeCheckCooldown = 1;
            smeltingRecipeCheckCooldown = 0;
            return;
        }

        if (fusingProcess.recipeCheckCooldown-- > 0)
            return;
        fusingProcess.recipeCheckCooldown = RECIPE_CHECK_INTERVAL;

        Set<BlockPos> excludedItems = collectSmeltingClaimPositions(parts);
        RecipeType<BlastFurnaceFusingRecipe> recipeType = MetallurgyRecipeTypes.BLAST_FURNACE_FUSING.getType();
        findMatchingRecipe(recipeType, getTotalBlockCount(), excludedItems)
            .ifPresent(this::startFusingProcessing);
    }

    private void scheduleSmeltingTasks(List<IndustrialBlastFurnaceBlockEntity> parts) {
        if (smeltingRecipeCheckCooldown-- > 0)
            return;
        smeltingRecipeCheckCooldown = RECIPE_CHECK_INTERVAL;

        Set<BlockPos> unavailableItems = collectSmeltingClaimPositions(parts);
        for (ItemClaim claim : fusingProcess.itemClaims)
            unavailableItems.add(claim.pos());

        RecipeType<BlastFurnaceSmeltingRecipe> recipeType = MetallurgyRecipeTypes.BLAST_FURNACE_SMELTING.getType();
        while (true) {
            Optional<RecipeStart<BlastFurnaceSmeltingRecipe>> matching =
                findMatchingRecipe(recipeType, 1, unavailableItems);
            if (matching.isEmpty() || matching.get().itemClaims().isEmpty())
                return;

            RecipeStart<BlastFurnaceSmeltingRecipe> start = matching.get();
            IndustrialBlastFurnaceBlockEntity owner = findPart(parts, start.itemClaims().get(0).pos());
            if (owner == null || owner.smeltingTask.isActive())
                return;

            owner.smeltingTask.start(recipeId(start.recipe()), start.itemClaims(), getProcessingDuration(start.recipe()));
            markTaskChanged(owner);
            for (ItemClaim claim : start.itemClaims())
                unavailableItems.add(claim.pos());
        }
    }

    private <T extends IndustrialBlastFurnaceRecipe> Optional<RecipeStart<T>> findMatchingRecipe(
        RecipeType<T> recipeType, int maxParallel, Set<BlockPos> excludedItems) {
        if (level == null)
            return Optional.empty();

        RecipeStart<T> best = null;
        int bestSpecificity = -1;
        for (RecipeHolder<T> holder : level.getRecipeManager().getAllRecipesFor(recipeType)) {
            T recipe = holder.value();
            Optional<RecipeStart<T>> simulated = simulateRecipeStart(recipe, maxParallel, excludedItems);
            if (simulated.isEmpty())
                continue;
            RecipeStart<T> start = simulated.get();
            int specificity = recipe.getIngredients().size() + recipe.getFluidIngredients().size();
            if (specificity < bestSpecificity)
                continue;
            if (specificity == bestSpecificity && best != null
                && start.parallelBatches() <= best.parallelBatches())
                continue;
            bestSpecificity = specificity;
            best = start;
        }
        return Optional.ofNullable(best);
    }

    private <T extends IndustrialBlastFurnaceRecipe> Optional<T> getRecipe(@Nullable ResourceLocation recipeId,
                                                                         Class<T> recipeClass) {
        if (level == null || recipeId == null)
            return Optional.empty();
        Optional<? extends Recipe<?>> recipe = level.getRecipeManager().byKey(recipeId).map(RecipeHolder::value);
        return recipe.filter(recipeClass::isInstance).map(recipeClass::cast);
    }

    private void startFusingProcessing(RecipeStart<BlastFurnaceFusingRecipe> start) {
        BlastFurnaceFusingRecipe recipe = start.recipe();
        fusingProcess.activeRecipeId = recipeId(recipe);
        fusingProcess.parallelBatches = start.parallelBatches();
        fusingProcess.totalProcessingTicks = getProcessingDuration(recipe);
        fusingProcess.processingTicks = fusingProcess.totalProcessingTicks;
        fusingProcess.itemClaims = copyClaims(start.itemClaims());
        fusingProcess.pendingItemOutputs.clear();
        fusingProcess.outputsRolled = false;
        setChanged();
        sendData();
    }

    private ResourceLocation recipeId(IndustrialBlastFurnaceRecipe recipe) {
        return level.getRecipeManager().getRecipes().stream().filter(h -> h.value() == recipe)
            .map(RecipeHolder::id).findFirst().orElseThrow();
    }

    private int getProcessingDuration(IndustrialBlastFurnaceRecipe recipe) {
        return recipe.getProcessingDuration() > 0 ? recipe.getProcessingDuration() : DEFAULT_PROCESSING_TIME;
    }

    private void stopFusingProcessing() {
        if (!fusingProcess.isActive())
            return;
        fusingProcess.clear();
        setChanged();
        sendData();
    }

    private void clearRecipeProcessingState() {
        smeltingTask.clear();
        smeltingRecipeCheckCooldown = 0;
        fusingProcess.clear();
    }

    private boolean hasRequiredHeat(IndustrialBlastFurnaceRecipe recipe) {
        if (level == null)
            return false;
        HeatCondition requirement = recipe.getRequiredHeat();
        if (requirement == HeatCondition.NONE)
            return true;

        List<HeatProbe> probes = getHeatProbes();
        if (probes.size() != width * width)
            return false;
        for (HeatProbe probe : probes)
            if (!probe.satisfies(requirement, recipe))
                return false;
        return true;
    }

    private List<HeatProbe> getHeatProbes() {
        if (level == null)
            return List.of();
        long gameTime = level.getGameTime();
        if (heatProbeCacheTick == gameTime)
            return cachedHeatProbes;

        List<HeatProbe> probes = new ArrayList<>(width * width);
        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos heaterPos = worldPosition.offset(xOffset, -1, zOffset);
                probes.add(resolveHeatProbe(heaterPos));
            }
        }
        cachedHeatProbes = List.copyOf(probes);
        heatProbeCacheTick = gameTime;
        return cachedHeatProbes;
    }

    private HeatProbe resolveHeatProbe(BlockPos heaterPos) {
        BlockState heaterState = level.getBlockState(heaterPos);
        List<String> externalConditions =
            java.util.List.of();

        HeatLevel heatLevel = null;
        String nativeCustomCondition = "";
        if (heaterState.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) {
            try {
                heatLevel = heaterState.getValue(BlazeBurnerBlock.HEAT_LEVEL);
                if (!isCreateHeatLevel(heatLevel))
                    nativeCustomCondition = getHeatLevelName(heatLevel);
            } catch (RuntimeException exception) {
                logHeatFailure(heaterState, "reading its native heat level", exception);
            }
        }

        if (heatLevel == null) {
            float registeredHeat = BoilerHeater.NO_HEAT;
            try {
                registeredHeat = BoilerHeater.findHeat(level, heaterPos, heaterState);
            } catch (RuntimeException exception) {
                logHeatFailure(heaterState, "calling its BoilerHeater provider", exception);
            }

            if (Float.isFinite(registeredHeat)) {
                if (registeredHeat >= 2)
                    heatLevel = HeatLevel.SEETHING;
                else if (registeredHeat > BoilerHeater.PASSIVE_HEAT)
                    heatLevel = HeatLevel.KINDLED;
                else if (registeredHeat == BoilerHeater.PASSIVE_HEAT)
                    heatLevel = HeatLevel.SMOULDERING;
            } else if (registeredHeat == Float.POSITIVE_INFINITY) {
                heatLevel = HeatLevel.SEETHING;
            }
        }

        if (heatLevel == null) {
            try {
                heatLevel = BasinBlockEntity.getHeatLevelOf(heaterState);
            } catch (RuntimeException exception) {
                logHeatFailure(heaterState, "using Create's basin heat fallback", exception);
                heatLevel = HeatLevel.NONE;
            }
        }
        return new HeatProbe(heatLevel, nativeCustomCondition, externalConditions);
    }

    private void updateHeatDisplayState() {
        HeatDisplaySnapshot snapshot = calculateHeatDisplayState();
        if (snapshot.state() == heatDisplayState
            && snapshot.customCondition().equals(customHeatCondition))
            return;

        heatDisplayState = snapshot.state();
        customHeatCondition = snapshot.customCondition();
        setChanged();
        notifyUpdate();
    }

    private HeatDisplaySnapshot calculateHeatDisplayState() {
        List<HeatProbe> probes = getHeatProbes();
        if (probes.size() != width * width || probes.isEmpty())
            return new HeatDisplaySnapshot(HeatDisplayState.UNHEATED, "");

        boolean allActive = true;
        boolean allHeated = true;
        boolean allSuperheated = true;
        boolean hasSpecialHeat = false;
        String uniformCondition = null;
        boolean uniform = true;

        for (HeatProbe probe : probes) {
            allActive &= probe.isActive();
            allHeated &= probe.satisfies(HeatCondition.HEATED, null);
            allSuperheated &= probe.satisfies(HeatCondition.SUPERHEATED, null);

            String displayCondition = probe.displayCondition();
            if (!displayCondition.isEmpty()) {
                hasSpecialHeat |= !isCreateConditionName(displayCondition);
                if (uniformCondition == null)
                    uniformCondition = displayCondition;
                else if (!uniformCondition.equalsIgnoreCase(displayCondition))
                    uniform = false;
            } else {
                uniform = false;
            }
        }

        if (!allActive)
            return new HeatDisplaySnapshot(HeatDisplayState.UNHEATED, "");
        if (uniform && uniformCondition != null && !isCreateConditionName(uniformCondition))
            return new HeatDisplaySnapshot(HeatDisplayState.CUSTOM, uniformCondition);
        if (allSuperheated)
            return new HeatDisplaySnapshot(HeatDisplayState.SUPERHEATED, "");
        if (allHeated)
            return new HeatDisplaySnapshot(HeatDisplayState.HEATED, "");
        if (hasSpecialHeat)
            return new HeatDisplaySnapshot(HeatDisplayState.MIXED_SPECIAL, "");
        return new HeatDisplaySnapshot(HeatDisplayState.LOW_HEATED, "");
    }

    private static boolean isCreateHeatLevel(HeatLevel heatLevel) {
        return heatLevel == HeatLevel.NONE || heatLevel == HeatLevel.SMOULDERING
            || heatLevel == HeatLevel.FADING || heatLevel == HeatLevel.KINDLED
            || heatLevel == HeatLevel.SEETHING;
    }

    private static boolean isCreateConditionName(String condition) {
        return HeatCondition.NONE.name().equalsIgnoreCase(condition)
            || HeatCondition.HEATED.name().equalsIgnoreCase(condition)
            || HeatCondition.SUPERHEATED.name().equalsIgnoreCase(condition);
    }

    private static String getHeatLevelName(HeatLevel heatLevel) {
        try {
            return heatLevel.getSerializedName();
        } catch (RuntimeException ignored) {
            return heatLevel.name().toLowerCase(Locale.ROOT);
        }
    }

    private static void logHeatFailure(BlockState heaterState, String operation, RuntimeException exception) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(heaterState.getBlock());
        if (!FAILED_HEAT_PROVIDERS.add(blockId))
            return;
        GensoMetallurgy.LOGGER.warn(
            "Ignoring incompatible heat source {} after an error while {}", blockId, operation, exception);
    }

    private static void logHeatCheckFailure(HeatCondition requirement, HeatLevel heatLevel,
                                            RuntimeException exception) {
        String key = requirement.name() + ':' + heatLevel.name();
        if (!FAILED_HEAT_CHECKS.add(key))
            return;
        GensoMetallurgy.LOGGER.warn(
            "Treating incompatible heat relationship {} <- {} as unavailable",
            requirement.name(), heatLevel.name(), exception);
    }

    private <T extends IndustrialBlastFurnaceRecipe> Optional<RecipeStart<T>> simulateRecipeStart(
        T recipe, int maxParallel, Set<BlockPos> excludedItems) {
        if (!hasRequiredHeat(recipe))
            return Optional.empty();
        if (recipe.getIngredients().isEmpty() && recipe.getFluidIngredients().isEmpty())
            return Optional.empty();

        List<IndustrialBlastFurnaceBlockEntity> parts = getConnectedParts();
        if (parts.size() != getTotalBlockCount())
            return Optional.empty();

        List<ItemStack> inputItems = new ArrayList<>(parts.size());
        for (IndustrialBlastFurnaceBlockEntity part : parts)
            inputItems.add(part.localItemInventory.getStackInSlot(0).copy());
        List<FluidStack> inputFluids = copyFluids(fluidInventory.getFluids());
        List<ItemStack> pendingItemOutputs = new ArrayList<>();
        List<FluidStack> pendingFluidOutputs = new ArrayList<>();
        List<ItemClaim> itemClaims = new ArrayList<>();
        Set<BlockPos> eligibleItems = new HashSet<>();
        for (IndustrialBlastFurnaceBlockEntity part : parts) {
            if (!excludedItems.contains(part.worldPosition))
                eligibleItems.add(part.worldPosition);
        }

        int batches = 0;
        int parallelLimit = Math.min(Math.max(0, maxParallel), getTotalBlockCount());
        while (batches < parallelLimit) {
            List<ItemStack> nextInputItems = copyItems(inputItems);
            List<FluidStack> nextInputFluids = copyFluids(inputFluids);
            List<ItemStack> nextItemOutputs = copyItems(pendingItemOutputs);
            List<FluidStack> nextFluidOutputs = copyFluids(pendingFluidOutputs);
            List<ItemClaim> nextItemClaims = copyClaims(itemClaims);

            if (!consumeItemInputs(nextInputItems, recipe.getIngredients(), parts, eligibleItems, nextItemClaims)
                || !consumeFluidInputs(nextInputFluids, recipe.getFluidIngredients()))
                break;

            appendIndividualItems(nextItemOutputs, getMaximumItemOutputs(recipe));
            for (FluidStack output : recipe.getFluidResults()) {
                if (!output.isEmpty())
                    mergeFluid(nextFluidOutputs, output);
            }

            if (countOccupiedItems(nextInputItems) + nextItemOutputs.size() > parts.size())
                break;
            List<FluidStack> finalFluids = combineFluids(nextInputFluids, nextFluidOutputs);
            if (finalFluids.size() > MAX_FLUID_TYPES
                || getFluidAmount(finalFluids) > fluidInventory.getCapacity())
                break;

            inputItems = nextInputItems;
            inputFluids = nextInputFluids;
            pendingItemOutputs = nextItemOutputs;
            pendingFluidOutputs = nextFluidOutputs;
            itemClaims = nextItemClaims;
            batches++;
        }

        if (batches == 0)
            return Optional.empty();
        return Optional.of(new RecipeStart<>(recipe, batches, itemClaims));
    }

    private Optional<RecipeTransaction> createFixedBatchTransaction(IndustrialBlastFurnaceRecipe recipe,
                                                                     int batches,
                                                                     List<ItemClaim> itemClaims,
                                                                     List<ItemStack> itemOutputs,
                                                                     List<IndustrialBlastFurnaceBlockEntity> parts) {
        Optional<RecipeInputs> consumed = consumeExactInputs(recipe, batches, itemClaims, parts);
        if (consumed.isEmpty())
            return Optional.empty();

        List<ItemStack> finalItems = consumed.get().items();
        List<ItemStack> individualOutputs = copyItems(itemOutputs);
        if (countOccupiedItems(finalItems) + individualOutputs.size() > parts.size())
            return Optional.empty();
        for (ItemStack output : individualOutputs) {
            int emptySlot = findEmptyItemSlot(finalItems);
            if (emptySlot == -1)
                return Optional.empty();
            finalItems.set(emptySlot, output.copy());
        }

        List<FluidStack> pendingFluidOutputs = new ArrayList<>();
        for (int batch = 0; batch < batches; batch++) {
            for (FluidStack output : recipe.getFluidResults()) {
                if (!output.isEmpty())
                    mergeFluid(pendingFluidOutputs, output);
            }
        }
        List<FluidStack> finalFluids = combineFluids(consumed.get().fluids(), pendingFluidOutputs);
        if (finalFluids.size() > MAX_FLUID_TYPES
            || getFluidAmount(finalFluids) > fluidInventory.getCapacity())
            return Optional.empty();

        return Optional.of(new RecipeTransaction(parts, finalItems, finalFluids, batches));
    }

    private Optional<RecipeInputs> consumeExactInputs(IndustrialBlastFurnaceRecipe recipe, int batches,
                                                       List<ItemClaim> itemClaims,
                                                       List<IndustrialBlastFurnaceBlockEntity> parts) {
        if (batches <= 0 || parts.size() != getTotalBlockCount())
            return Optional.empty();
        if (itemClaims.size() != recipe.getIngredients().size() * batches
            || !validateExactItemClaims(parts, itemClaims))
            return Optional.empty();

        List<ItemStack> inputItems = new ArrayList<>(parts.size());
        for (IndustrialBlastFurnaceBlockEntity part : parts)
            inputItems.add(part.localItemInventory.getStackInSlot(0).copy());
        List<FluidStack> inputFluids = copyFluids(fluidInventory.getFluids());
        Set<BlockPos> eligibleItems = new HashSet<>();
        for (ItemClaim claim : itemClaims)
            eligibleItems.add(claim.pos());
        if (eligibleItems.size() != itemClaims.size())
            return Optional.empty();

        for (int batch = 0; batch < batches; batch++) {
            if (!consumeItemInputs(inputItems, recipe.getIngredients(), parts, eligibleItems, null)
                || !consumeFluidInputs(inputFluids, recipe.getFluidIngredients()))
                return Optional.empty();
        }
        return Optional.of(new RecipeInputs(inputItems, inputFluids));
    }

    private List<ItemStack> rollItemOutputs(IndustrialBlastFurnaceRecipe recipe, int batches) {
        List<ItemStack> outputs = new ArrayList<>();
        for (int batch = 0; batch < batches; batch++)
            appendIndividualItems(outputs, recipe.rollResults(level.random));
        return outputs;
    }

    private static boolean validateExactItemClaims(List<IndustrialBlastFurnaceBlockEntity> parts,
                                                   List<ItemClaim> itemClaims) {
        Set<BlockPos> seen = new HashSet<>();
        for (ItemClaim claim : itemClaims) {
            if (!seen.add(claim.pos()))
                return false;
            IndustrialBlastFurnaceBlockEntity part = findPart(parts, claim.pos());
            if (part == null)
                return false;
            ItemStack current = part.localItemInventory.getStackInSlot(0);
            if (current.getCount() != claim.stack().getCount()
                || !ItemStack.isSameItemSameComponents(current, claim.stack()))
                return false;
        }
        return true;
    }

    @Nullable
    private static IndustrialBlastFurnaceBlockEntity findPart(List<IndustrialBlastFurnaceBlockEntity> parts,
                                                            BlockPos pos) {
        for (IndustrialBlastFurnaceBlockEntity part : parts) {
            if (part.worldPosition.equals(pos))
                return part;
        }
        return null;
    }

    private static Set<BlockPos> collectSmeltingClaimPositions(List<IndustrialBlastFurnaceBlockEntity> parts) {
        Set<BlockPos> positions = new HashSet<>();
        for (IndustrialBlastFurnaceBlockEntity part : parts) {
            if (!part.smeltingTask.isActive())
                continue;
            for (ItemClaim claim : part.smeltingTask.itemClaims)
                positions.add(claim.pos());
        }
        return positions;
    }

    private static void markTaskChanged(IndustrialBlastFurnaceBlockEntity owner) {
        owner.setChanged();
        owner.notifyUpdate();
    }

    private static void clearSmeltingTask(IndustrialBlastFurnaceBlockEntity owner) {
        if (!owner.smeltingTask.isActive())
            return;
        owner.smeltingTask.clear();
        markTaskChanged(owner);
    }

    private void commitRecipeTransaction(RecipeTransaction transaction) {
        List<IndustrialBlastFurnaceBlockEntity> parts = transaction.parts();
        for (IndustrialBlastFurnaceBlockEntity part : parts)
            part.readingStorage = true;
        try {
            for (int i = 0; i < parts.size(); i++)
                parts.get(i).localItemInventory.setStackInSlot(0, transaction.items().get(i));

            for (IndustrialBlastFurnaceBlockEntity part : parts)
                part.localFluidInventory.clear();
            for (FluidStack stored : transaction.fluids()) {
                int remaining = stored.getAmount();
                for (IndustrialBlastFurnaceBlockEntity part : parts) {
                    FluidStack offered = stored.copy();
                    offered.setAmount(remaining);
                    remaining -= part.localFluidInventory.fill(offered, IFluidHandler.FluidAction.EXECUTE);
                    if (remaining == 0)
                        break;
                }
            }
        } finally {
            for (IndustrialBlastFurnaceBlockEntity part : parts)
                part.readingStorage = false;
        }

        for (IndustrialBlastFurnaceBlockEntity part : parts) {
            part.setChanged();
            part.notifyUpdate();
        }
    }

    private static int findMatchingItem(List<ItemStack> items, Ingredient ingredient,
                                        List<IndustrialBlastFurnaceBlockEntity> parts,
                                        Set<BlockPos> eligibleItems) {
        for (int i = 0; i < items.size(); i++) {
            if (eligibleItems.contains(parts.get(i).worldPosition)
                && !items.get(i).isEmpty() && ingredient.test(items.get(i)))
                return i;
        }
        return -1;
    }

    private static boolean consumeItemInputs(List<ItemStack> items, List<Ingredient> ingredients,
                                             List<IndustrialBlastFurnaceBlockEntity> parts,
                                             Set<BlockPos> eligibleItems,
                                             @Nullable List<ItemClaim> consumedClaims) {
        for (Ingredient ingredient : ingredients) {
            int matchingSlot = findMatchingItem(items, ingredient, parts, eligibleItems);
            if (matchingSlot == -1)
                return false;
            if (consumedClaims != null)
                consumedClaims.add(new ItemClaim(parts.get(matchingSlot).worldPosition, items.get(matchingSlot)));
            items.set(matchingSlot, ItemStack.EMPTY);
        }
        return true;
    }

    private static boolean consumeFluidInputs(List<FluidStack> fluids, List<SizedFluidIngredient> ingredients) {
        for (SizedFluidIngredient ingredient : ingredients) {
            int amountRequired = ingredient.amount();
            for (FluidStack stored : fluids) {
                if (!ingredient.ingredient().test(stored))
                    continue;
                int consumed = Math.min(amountRequired, stored.getAmount());
                stored.shrink(consumed);
                amountRequired -= consumed;
                if (amountRequired == 0)
                    break;
            }
            if (amountRequired != 0)
                return false;
            fluids.removeIf(FluidStack::isEmpty);
        }
        return true;
    }

    private static List<ItemStack> getMaximumItemOutputs(IndustrialBlastFurnaceRecipe recipe) {
        List<ItemStack> outputs = new ArrayList<>();
        for (ProcessingOutput output : recipe.getRollableResults()) {
            if (output.getChance() > 0 && !output.getStack().isEmpty())
                outputs.add(output.getStack().copy());
        }
        return outputs;
    }

    private static void appendIndividualItems(List<ItemStack> target, List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            for (int i = 0; i < output.getCount(); i++) {
                ItemStack singleItem = output.copy();
                singleItem.setCount(1);
                target.add(singleItem);
            }
        }
    }

    private static int countOccupiedItems(List<ItemStack> items) {
        int occupied = 0;
        for (ItemStack item : items) {
            if (!item.isEmpty())
                occupied++;
        }
        return occupied;
    }

    private static List<ItemStack> copyItems(List<ItemStack> items) {
        List<ItemStack> copies = new ArrayList<>(items.size());
        for (ItemStack item : items)
            copies.add(item.copy());
        return copies;
    }

    private static List<FluidStack> copyFluids(List<FluidStack> fluids) {
        List<FluidStack> copies = new ArrayList<>(fluids.size());
        for (FluidStack fluid : fluids)
            copies.add(fluid.copy());
        return copies;
    }

    private static List<FluidStack> combineFluids(List<FluidStack> stored, List<FluidStack> outputs) {
        List<FluidStack> combined = copyFluids(stored);
        for (FluidStack output : outputs)
            mergeFluid(combined, output);
        return combined;
    }

    private static int findEmptyItemSlot(List<ItemStack> items) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty())
                return i;
        }
        return -1;
    }

    private static void mergeFluid(List<FluidStack> fluids, FluidStack output) {
        for (FluidStack stored : fluids) {
            if (!FluidStack.isSameFluidSameComponents(stored, output))
                continue;
            stored.grow(output.getAmount());
            return;
        }
        fluids.add(output.copy());
    }

    private static int getFluidAmount(List<FluidStack> fluids) {
        int amount = 0;
        for (FluidStack fluid : fluids)
            amount += fluid.getAmount();
        return amount;
    }

    private void onItemContentsChanged() {
        if (readingStorage || !hasLevel())
            return;
        setChanged();
        if (!level.isClientSide) {
            notifyUpdate();
            IndustrialBlastFurnaceBlockEntity controllerBE = getControllerBE();
            if (controllerBE != null) {
                controllerBE.smeltingRecipeCheckCooldown = 0;
                controllerBE.fusingProcess.recipeCheckCooldown = 0;
                if (controllerBE != this)
                    controllerBE.setChanged();
            }
        }
    }

    private void onFluidContentsChanged() {
        if (readingStorage || !hasLevel())
            return;
        setChanged();
        if (!level.isClientSide) {
            notifyUpdate();
            IndustrialBlastFurnaceBlockEntity controllerBE = getControllerBE();
            if (controllerBE != null) {
                controllerBE.fusingProcess.recipeCheckCooldown = 0;
                if (controllerBE != this) {
                    controllerBE.setChanged();
                    controllerBE.notifyUpdate();
                }
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        BlockPos previousController = controller;
        int previousWidth = width;
        int previousHeight = height;

        readingStorage = true;
        try {
            updateConnectivity = tag.contains("Uninitialized");
            controller = tag.contains("Controller") ? NbtUtils.readBlockPos(tag, "Controller").orElse(null) : null;
            lastKnownPos = tag.contains("LastKnownPos") ? NbtUtils.readBlockPos(tag, "LastKnownPos").orElse(null) : null;
            width = Math.max(1, tag.getInt("Width"));
            height = Math.max(1, tag.getInt("Height"));
            windowPreference = tag.getBoolean("WindowPreference") || getBlockState().getValue(WINDOW);
            smeltingRecipeCheckCooldown = tag.getInt("SmeltingRecipeCheckCooldown");
            smeltingTask.read(tag.getCompound("SmeltingTask"), registries);
            fusingProcess.read(tag, "Fusing", registries);
            heatDisplayState = HeatDisplayState.byName(tag.getString("HeatDisplayState"));
            customHeatCondition = tag.getString("CustomHeatCondition");
            heatProbeCacheTick = Long.MIN_VALUE;
            cachedHeatProbes = List.of();

            if (tag.contains("ItemInventory"))
                localItemInventory.deserializeNBT(registries, tag.getCompound("ItemInventory"));
            if (tag.contains("FluidInventory")) {
                localFluidInventory.deserializeNBT(registries, tag.getCompound("FluidInventory"));

            }
        } finally {
            readingStorage = false;
        }

        invalidateStorageCapabilities();

        if (clientPacket && (!Objects.equals(previousController, controller)
            || previousWidth != width || previousHeight != height)) {
            if (hasLevel())
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
            invalidateRenderBoundingBox();
        }
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (updateConnectivity)
            tag.putBoolean("Uninitialized", true);
        if (lastKnownPos != null)
            tag.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
        if (!isController() && controller != null)
            tag.put("Controller", NbtUtils.writeBlockPos(controller));
        tag.putInt("Width", width);
        tag.putInt("Height", height);
        tag.putBoolean("WindowPreference", windowPreference);
        tag.put("ItemInventory", localItemInventory.serializeNBT(registries));
        tag.put("FluidInventory", localFluidInventory.serializeNBT(registries));
        tag.putInt("SmeltingRecipeCheckCooldown", smeltingRecipeCheckCooldown);
        tag.put("SmeltingTask", smeltingTask.write(registries));
        fusingProcess.write(tag, "Fusing", registries);
        tag.putString("HeatDisplayState", heatDisplayState.name());
        if (!customHeatCondition.isEmpty())
            tag.putString("CustomHeatCondition", customHeatCondition);
        super.write(tag, registries, clientPacket);
    }

    @Override
    public void initialize() {
        super.initialize();
        sendData();
        if (level != null && level.isClientSide)
            invalidateRenderBoundingBox();
    }

    @Override
    public BlockPos getController() {
        return isController() ? worldPosition : controller;
    }

    @Override
    public IndustrialBlastFurnaceBlockEntity getControllerBE() {
        if (isController())
            return this;
        if (!hasLevel())
            return null;
        if (!level.hasChunkAt(controller)) return null;
        BlockEntity blockEntity = level.getBlockEntity(controller);
        return blockEntity instanceof IndustrialBlastFurnaceBlockEntity furnace ? furnace : null;
    }

    @Override
    public boolean isController() {
        return controller == null || worldPosition.equals(controller);
    }

    @Override
    public void setController(BlockPos controller) {
        if (level != null && level.isClientSide && !isVirtual())
            return;
        if (controller.equals(this.controller))
            return;
        windowPreference |= getBlockState().getValue(WINDOW);
        clearRecipeProcessingState();
        this.controller = controller;
        invalidateStorageCapabilities();
        setChanged();
        notifyUpdate();
    }

    @Override
    public void removeController(boolean keepContents) {
        if (level == null || level.isClientSide)
            return;

        windowPreference |= getBlockState().getValue(WINDOW);
        clearRecipeProcessingState();
        updateConnectivity = true;
        controller = null;
        width = 1;
        height = 1;

        BlockState state = getBlockState();
        if (IndustrialBlastFurnaceBlock.isFurnace(state)) {
            state = state.setValue(TOP, true)
                .setValue(BOTTOM, true)
                .setValue(SHAPE, IndustrialBlastFurnaceBlock.Shape.PLAIN)
                .setValue(WINDOW, false)
                .setValue(WINDOW_CONNECTION, IndustrialBlastFurnaceBlock.WindowConnection.SINGLE);
            level.setBlock(worldPosition, state, 22);
        }
        invalidateStorageCapabilities();
        setChanged();
        sendData();
    }

    @Override
    public BlockPos getLastKnownPos() {
        return lastKnownPos;
    }

    @Override
    public void preventConnectivityUpdate() {
        updateConnectivity = false;
    }

    @Override
    public void notifyMultiUpdated() {
        updateBlockState();
        if (isController())
            normalizeWindowLayout();
        invalidateStorageCapabilities();
        setChanged();
    }

    public void toggleLayerWindows() {
        if (level == null || level.isClientSide)
            return;

        IndustrialBlastFurnaceBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null)
            return;

        int layer = worldPosition.getY() - controllerBE.worldPosition.getY();
        if (layer < 0 || layer >= controllerBE.height)
            return;
        if (layer == 0)
            return;

        boolean[] openLayers = controllerBE.collectOpenWindowLayers();
        openLayers[layer] = !openLayers[layer];
        controllerBE.applyWindowLayout(openLayers);
    }

    private void normalizeWindowLayout() {
        if (level == null || level.isClientSide || !isController())
            return;
        applyWindowLayout(collectOpenWindowLayers());
    }

    private boolean[] collectOpenWindowLayers() {
        boolean[] openLayers = new boolean[height];
        if (level == null)
            return openLayers;

        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width && !openLayers[yOffset]; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos partPos = worldPosition.offset(xOffset, yOffset, zOffset);
                    if (!level.hasChunkAt(partPos)) continue;
                    IndustrialBlastFurnaceBlockEntity part = ConnectivityHandler.partAt(getType(), level, partPos);
                    if (part == null || !getController().equals(part.getController()))
                        continue;
                    if (part.windowPreference || part.getBlockState().getValue(WINDOW)) {
                        openLayers[yOffset] = true;
                        break;
                    }
                }
            }
        }
        return openLayers;
    }

    private void applyWindowLayout(boolean[] openLayers) {
        if (level == null || level.isClientSide)
            return;
        if (openLayers.length > 0)
            openLayers[0] = false;

        for (int yOffset = 0; yOffset < height; yOffset++) {
            boolean connectsBelow = yOffset > 0 && openLayers[yOffset - 1];
            boolean connectsAbove = yOffset + 1 < height && openLayers[yOffset + 1];
            IndustrialBlastFurnaceBlock.WindowConnection connection =
                getWindowConnection(connectsBelow, connectsAbove);

            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos partPos = worldPosition.offset(xOffset, yOffset, zOffset);
                    if (!level.hasChunkAt(partPos)) continue;
                    IndustrialBlastFurnaceBlockEntity part = ConnectivityHandler.partAt(getType(), level, partPos);
                    if (part == null || !getController().equals(part.getController()))
                        continue;

                    part.windowPreference = openLayers[yOffset];
                    part.setChanged();
                    boolean window = openLayers[yOffset] && isWindowPosition(xOffset, zOffset);
                    BlockState state = part.getBlockState();
                    BlockState updated = state.setValue(WINDOW, window)
                        .setValue(WINDOW_CONNECTION,
                            window ? connection : IndustrialBlastFurnaceBlock.WindowConnection.SINGLE);
                    if (!updated.equals(state))
                        level.setBlock(partPos, updated, 22);
                }
            }
        }
    }

    private boolean isWindowPosition(int xOffset, int zOffset) {
        if (width == 1 || width == 2)
            return true;
        int center = width / 2;
        return xOffset == center && (zOffset == 0 || zOffset == width - 1)
            || zOffset == center && (xOffset == 0 || xOffset == width - 1);
    }

    private static IndustrialBlastFurnaceBlock.WindowConnection getWindowConnection(boolean below, boolean above) {
        if (below && above)
            return IndustrialBlastFurnaceBlock.WindowConnection.MIDDLE;
        if (below)
            return IndustrialBlastFurnaceBlock.WindowConnection.TOP;
        if (above)
            return IndustrialBlastFurnaceBlock.WindowConnection.BOTTOM;
        return IndustrialBlastFurnaceBlock.WindowConnection.SINGLE;
    }

    public void tryCollectItem(ItemEntity itemEntity) {
        if (level == null || level.isClientSide || !itemEntity.isAlive() || itemEntity.getItem().isEmpty())
            return;

        IndustrialBlastFurnaceBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null || !controllerBE.isInsideChamber(itemEntity))
            return;

        ItemStack stack = itemEntity.getItem();
        int originalCount = stack.getCount();
        ItemStack remainder = controllerBE.insertItem(stack.copy());
        if (remainder.getCount() >= originalCount)
            return;

        if (remainder.isEmpty())
            itemEntity.discard();
        else
            itemEntity.setItem(remainder);
    }

    private boolean isInsideChamber(ItemEntity itemEntity) {
        double minX = worldPosition.getX() + WALL_INSET;
        double maxX = worldPosition.getX() + width - WALL_INSET;
        double minY = worldPosition.getY() + FLOOR_HEIGHT;
        double maxY = worldPosition.getY() + height;
        double minZ = worldPosition.getZ() + WALL_INSET;
        double maxZ = worldPosition.getZ() + width - WALL_INSET;
        return itemEntity.getX() > minX && itemEntity.getX() < maxX
            && itemEntity.getY() >= minY && itemEntity.getY() < maxY
            && itemEntity.getZ() > minZ && itemEntity.getZ() < maxZ;
    }

    private ItemStack insertItem(ItemStack stack) {
        // Falling items enter the chamber directly, independently of the queried layer's port rules.
        StructureStorage current = getStorage();
        return current == null ? stack : ItemHandlerHelper.insertItemStacked(current.items, stack, false);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        var type = dev.gensomechanic.metallurgy.MetallurgyBlockEntities.INDUSTRIAL_BLAST_FURNACE.get();
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> {
            var port = be.port(); return port == null ? null : port.items();
        });
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> {
            var port = be.port(); return port == null ? null : port.fluids();
        });
    }

    private MetallurgyStoragePort port() {
        StructureStorage current = getStorage();
        if (current == null) return null;
        if (storagePort == null || !storagePort.isValid()) {
            IItemHandler items = isTopLayer() ? current.items : new ExtractionOnlyItemHandler(current.items);
            storagePort = new MetallurgyStoragePort(items, current.fluids,
                () -> storage == current && current.isValid());
        }
        return storagePort;
    }

    @Nullable
    private StructureStorage getStorage() {
        if (!storageCapabilitiesValid || !MetallurgyStoragePort.isPresent(this)) {
            invalidateStorageCapabilities();
            return null;
        }
        if (storage != null && storage.isValid())
            return storage;
        BlockPos controllerPos = getController();
        if (!level.hasChunkAt(controllerPos))
            return null;
        if (!(level.getBlockEntity(controllerPos) instanceof IndustrialBlastFurnaceBlockEntity main)
            || !main.isController() || !MetallurgyStoragePort.isPresent(main))
            return null;

        List<IndustrialBlastFurnaceBlockEntity> parts = new ArrayList<>(main.getTotalBlockCount());
        for (int yOffset = 0; yOffset < main.height; yOffset++) {
            for (int xOffset = 0; xOffset < main.width; xOffset++) {
                for (int zOffset = 0; zOffset < main.width; zOffset++) {
                    BlockPos partPos = controllerPos.offset(xOffset, yOffset, zOffset);
                    // Capability queries must not load missing chunks or expose a partial combined inventory.
                    if (!level.hasChunkAt(partPos)
                        || !(level.getBlockEntity(partPos) instanceof IndustrialBlastFurnaceBlockEntity part)
                        || !part.storageCapabilitiesValid || !MetallurgyStoragePort.isPresent(part)
                        || !controllerPos.equals(part.getController()))
                        return null;
                    parts.add(part);
                }
            }
        }
        if (!parts.contains(this))
            return null;
        for (IndustrialBlastFurnaceBlockEntity part : parts)
            part.invalidateStorageCapabilities();
        StructureStorage created = main.new StructureStorage(parts);
        for (IndustrialBlastFurnaceBlockEntity part : parts)
            part.storage = created;
        return created;
    }

    private boolean isTopLayer() {
        IndustrialBlastFurnaceBlockEntity controllerBE = getControllerBE();
        return controllerBE != null
            && worldPosition.getY() == controllerBE.worldPosition.getY() + controllerBE.height - 1;
    }

    private void invalidateStorageCapabilities() {
        if (storage != null)
            storage.invalidate();
        if (storagePort != null)
            storagePort.invalidate();
        storagePort = null;
        if (level != null) level.invalidateCapabilities(worldPosition);
    }



    @Override
    public void invalidate() {
        storageCapabilitiesValid = false;
        invalidateStorageCapabilities();
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        invalidateStorageCapabilities();
        super.onChunkUnloaded();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        storageCapabilitiesValid = true;
        invalidateStorageCapabilities();
    }

    /** One storage generation shared by the current members, with a separate external port per part. */
    private final class StructureStorage {
        private final List<IndustrialBlastFurnaceBlockEntity> parts;
        private final int originalWidth = width;
        private final int originalHeight = height;
        private final IItemHandler items;
        private final IFluidHandler fluids = fluidInventory;
        private boolean valid = true;

        private StructureStorage(List<IndustrialBlastFurnaceBlockEntity> parts) {
            this.parts = List.copyOf(parts);
            items = SameSizeCombinedInvWrapper.create(parts.stream()
                .map(part -> part.localItemInventory).toArray(IItemHandlerModifiable[]::new));
        }

        private boolean isValid() {
            if (!valid)
                return false;
            if (width != originalWidth || height != originalHeight || !isController()) {
                invalidate();
                return false;
            }
            for (IndustrialBlastFurnaceBlockEntity part : parts) {
                if (part.storage != this || !part.storageCapabilitiesValid || !MetallurgyStoragePort.isPresent(part)
                    || !worldPosition.equals(part.getController())) {
                    invalidate();
                    return false;
                }
            }
            return true;
        }

        private void invalidate() {
            if (!valid)
                return;
            valid = false;
            for (IndustrialBlastFurnaceBlockEntity part : parts) {
                if (part.storage != this)
                    continue;
                part.storage = null;
                MetallurgyStoragePort oldPort = part.storagePort;
                part.storagePort = null;
                if (oldPort != null) oldPort.invalidate();
                if (part.level != null) part.level.invalidateCapabilities(part.worldPosition);
            }
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        if (isController())
            return super.createRenderBoundingBox()
                .expandTowards(width - 1, height - 1, width - 1)
                .inflate(1 / 16.0, 0, 1 / 16.0);
        return super.createRenderBoundingBox();
    }

    @Override
    public boolean hasInventory() {
        return true;
    }

    public ItemStack getLocalItem() {
        return localItemInventory.getStackInSlot(0);
    }

    public ItemStack removeLocalItem() {
        return localItemInventory.extractItem(0, 1, false);
    }

    /** Preserves shared fluid contents in the remaining capacity before this block disappears. */
    public void prepareFluidStorageForRemoval() {
        if (level == null || level.isClientSide)
            return;
        IndustrialBlastFurnaceBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null)
            return;
        controllerBE.redistributeFluids(worldPosition);
    }

    public IndustrialBlastFurnaceFluidHandler getFluidInventory() {
        return fluidInventory;
    }

    private List<SharedCapacityFluidTank> getConnectedFluidInventories() {
        List<SharedCapacityFluidTank> inventories = new ArrayList<>(getTotalBlockCount());
        for (IndustrialBlastFurnaceBlockEntity part : getConnectedParts())
            inventories.add(part.localFluidInventory);
        return inventories;
    }

    private List<IndustrialBlastFurnaceBlockEntity> getConnectedParts() {
        List<IndustrialBlastFurnaceBlockEntity> parts = new ArrayList<>(getTotalBlockCount());
        if (level == null)
            return parts;

        BlockPos expectedController = getController();
        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos partPos = worldPosition.offset(xOffset, yOffset, zOffset);
                    if (!level.hasChunkAt(partPos)) continue;
                    IndustrialBlastFurnaceBlockEntity part = ConnectivityHandler.partAt(getType(), level, partPos);
                    if (part != null && expectedController.equals(part.getController()))
                        parts.add(part);
                }
            }
        }
        return parts;
    }



    private void redistributeFluids(@Nullable BlockPos excludedPos) {
        List<IndustrialBlastFurnaceBlockEntity> parts = getConnectedParts();
        if (parts.isEmpty())
            return;

        List<FluidStack> storedFluids = fluidInventory.getFluids();
        for (IndustrialBlastFurnaceBlockEntity part : parts)
            part.readingStorage = true;
        try {
            for (IndustrialBlastFurnaceBlockEntity part : parts)
                part.localFluidInventory.clear();

            for (FluidStack stored : storedFluids) {
                int remaining = stored.getAmount();
                for (IndustrialBlastFurnaceBlockEntity part : parts) {
                    if (part.worldPosition.equals(excludedPos))
                        continue;
                    FluidStack offered = stored.copy();
                    offered.setAmount(remaining);
                    remaining -= part.localFluidInventory.fill(offered, IFluidHandler.FluidAction.EXECUTE);
                    if (remaining == 0)
                        break;
                }
            }
        } finally {
            for (IndustrialBlastFurnaceBlockEntity part : parts)
                part.readingStorage = false;
        }

        for (IndustrialBlastFurnaceBlockEntity part : parts) {
            part.setChanged();
            part.notifyUpdate();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        IndustrialBlastFurnaceBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null)
            return false;

        List<IndustrialBlastFurnaceBlockEntity> parts = controllerBE.getConnectedParts();
        if (parts.isEmpty())
            return false;

        MutableComponent title = Component.translatable(
            "gensometallurgy.goggles.blast_furnace.status",
            controllerBE.getHeatDisplayComponent()).withStyle(ChatFormatting.WHITE);
        GoggleTooltipHelper.add(tooltip, title);

        Map<BlockPos, ProcessingProgress> activeProgress = controllerBE.collectActiveItemProgress(parts);
        List<ItemTooltipEntry> itemEntries = new ArrayList<>();
        for (IndustrialBlastFurnaceBlockEntity part : parts) {
            ItemStack stack = part.localItemInventory.getStackInSlot(0);
            if (!stack.isEmpty()) {
                itemEntries.add(new ItemTooltipEntry(part.worldPosition, stack.copy(),
                    activeProgress.get(part.worldPosition)));
            }
        }
        itemEntries.sort(Comparator
            .comparing((ItemTooltipEntry entry) -> entry.progress() == null)
            .thenComparingInt(entry -> entry.progress() == null
                ? Integer.MAX_VALUE : Math.max(0, entry.progress().remainingTicks()))
            .thenComparingLong(entry -> entry.pos().asLong()));

        GoggleTooltipHelper.add(tooltip,
            Component.translatable("gensometallurgy.goggles.blast_furnace.item_container",
                Component.translatable("gensometallurgy.goggles.blast_furnace.item_amount",
                    itemEntries.size(), controllerBE.getTotalBlockCount()).withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.WHITE));

        int shownItems = Math.min(3, itemEntries.size());
        for (int i = 0; i < shownItems; i++) {
            ItemTooltipEntry entry = itemEntries.get(i);
            MutableComponent row = Component.empty()
                .append(entry.stack().getHoverName().copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" … ").withStyle(ChatFormatting.DARK_GRAY));
            if (entry.progress() == null) {
                row.append(GoggleTooltipHelper.unavailableMark());
            } else {
                row.append(GoggleTooltipHelper.progressBar(entry.progress().remainingTicks(),
                    entry.progress().totalTicks(), controllerBE.getHeatDisplayColor()));
            }
            GoggleTooltipHelper.add(tooltip, row, 1);
        }
        if (itemEntries.size() > shownItems) {
            GoggleTooltipHelper.add(tooltip,
                Component.translatable("gensometallurgy.goggles.blast_furnace.more_items",
                    itemEntries.size() - shownItems).withStyle(ChatFormatting.DARK_GRAY), 1);
        }

        List<FluidStack> fluids = controllerBE.fluidInventory.getFluids();
        fluids.sort(Comparator.comparingInt(FluidStack::getAmount).reversed()
            .thenComparing(stack -> BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString()));
        GoggleTooltipHelper.add(tooltip,
            Component.translatable("gensometallurgy.goggles.blast_furnace.fluid_container",
                Component.translatable("gensometallurgy.goggles.blast_furnace.fluid_amount",
                    controllerBE.fluidInventory.getTotalFluidAmount(), controllerBE.fluidInventory.getCapacity())
                    .withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.WHITE));

        int shownFluids = Math.min(3, fluids.size());
        for (int i = 0; i < shownFluids; i++) {
            FluidStack fluid = fluids.get(i);
            MutableComponent row = Component.empty()
                .append(fluid.getHoverName().copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" " + fluid.getAmount() + "mB").withStyle(ChatFormatting.BLUE));
            GoggleTooltipHelper.add(tooltip, row, 1);
        }
        if (fluids.size() > shownFluids) {
            GoggleTooltipHelper.add(tooltip,
                Component.translatable("gensometallurgy.goggles.blast_furnace.more_fluids",
                    fluids.size() - shownFluids).withStyle(ChatFormatting.DARK_GRAY), 1);
        }
        return true;
    }

    private Map<BlockPos, ProcessingProgress> collectActiveItemProgress(
        List<IndustrialBlastFurnaceBlockEntity> parts) {
        Map<BlockPos, ProcessingProgress> progress = new HashMap<>();
        for (IndustrialBlastFurnaceBlockEntity part : parts) {
            SmeltingTask task = part.smeltingTask;
            if (!task.isActive())
                continue;
            ProcessingProgress taskProgress =
                new ProcessingProgress(task.processingTicks, task.totalProcessingTicks);
            for (ItemClaim claim : task.itemClaims)
                progress.putIfAbsent(claim.pos(), taskProgress);
        }

        if (fusingProcess.activeRecipeId != null) {
            ProcessingProgress fusingProgress =
                new ProcessingProgress(fusingProcess.processingTicks, fusingProcess.totalProcessingTicks);
            for (ItemClaim claim : fusingProcess.itemClaims)
                progress.putIfAbsent(claim.pos(), fusingProgress);
        }
        return progress;
    }

    private MutableComponent getHeatDisplayComponent() {
        return switch (heatDisplayState) {
            case UNHEATED -> Component.translatable(
                "gensometallurgy.goggles.heat.unheated").withStyle(ChatFormatting.DARK_GRAY);
            case LOW_HEATED -> Component.translatable(
                "gensometallurgy.goggles.heat.low_heated").withStyle(ChatFormatting.YELLOW);
            case HEATED -> Component.translatable(
                "gensometallurgy.goggles.heat.heated").withStyle(ChatFormatting.GOLD);
            case SUPERHEATED -> Component.translatable(
                "gensometallurgy.goggles.heat.superheated")
                .withStyle(style -> style.withColor(0x7FC1EE));
            case MIXED_SPECIAL -> Component.translatable(
                "gensometallurgy.goggles.heat.mixed_special").withStyle(ChatFormatting.LIGHT_PURPLE);
            case CUSTOM -> getCustomHeatDisplayComponent();
        };
    }

    private MutableComponent getCustomHeatDisplayComponent() {
        String serialized = customHeatCondition.toLowerCase(Locale.ROOT);
        String fallback = serialized.replace('_', ' ');
        if (fallback.isBlank())
            return Component.translatable("gensometallurgy.goggles.heat.special")
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        return Component.translatableWithFallback("create.recipe.heat_requirement." + serialized, fallback)
            .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    private int getHeatDisplayColor() {
        return switch (heatDisplayState) {
            case UNHEATED -> ChatFormatting.DARK_GRAY.getColor();
            case LOW_HEATED -> ChatFormatting.YELLOW.getColor();
            case HEATED -> ChatFormatting.GOLD.getColor();
            case SUPERHEATED -> 0x7FC1EE;
            case CUSTOM, MIXED_SPECIAL -> ChatFormatting.LIGHT_PURPLE.getColor();
        };
    }

    public int getTotalBlockCount() {
        return width * width * height;
    }

    public float getHeatGaugeValue(float partialTicks) {
        return heatGauge.getValue(partialTicks);
    }

    private float getHeatGaugeTarget() {
        return switch (heatDisplayState) {
            case UNHEATED -> 0;
            case LOW_HEATED -> .25f;
            case HEATED -> .6f;
            case SUPERHEATED -> 1;
            case CUSTOM, MIXED_SPECIAL -> .75f;
        };
    }

    private void updateBlockState() {
        if (level == null)
            return;
        BlockState state = getBlockState();
        if (!IndustrialBlastFurnaceBlock.isFurnace(state))
            return;

        state = state.setValue(BOTTOM, getController().getY() == worldPosition.getY())
            .setValue(TOP, getController().getY() + height - 1 == worldPosition.getY())
            .setValue(SHAPE, calculateShape());
        level.setBlock(worldPosition, state, 6);
    }

    private IndustrialBlastFurnaceBlock.Shape calculateShape() {
        IndustrialBlastFurnaceBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null || width == 1)
            return IndustrialBlastFurnaceBlock.Shape.PLAIN;

        int xOffset = worldPosition.getX() - controllerBE.worldPosition.getX();
        int zOffset = worldPosition.getZ() - controllerBE.worldPosition.getZ();
        int maxOffset = width - 1;

        if (xOffset == 0) {
            if (zOffset == 0)
                return IndustrialBlastFurnaceBlock.Shape.NW;
            if (zOffset == maxOffset)
                return IndustrialBlastFurnaceBlock.Shape.SW;
            return IndustrialBlastFurnaceBlock.Shape.WEST;
        }
        if (xOffset == maxOffset) {
            if (zOffset == 0)
                return IndustrialBlastFurnaceBlock.Shape.NE;
            if (zOffset == maxOffset)
                return IndustrialBlastFurnaceBlock.Shape.SE;
            return IndustrialBlastFurnaceBlock.Shape.EAST;
        }
        if (zOffset == 0)
            return IndustrialBlastFurnaceBlock.Shape.NORTH;
        if (zOffset == maxOffset)
            return IndustrialBlastFurnaceBlock.Shape.SOUTH;
        return IndustrialBlastFurnaceBlock.Shape.INNER;
    }

    @Override
    public Direction.Axis getMainConnectionAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public int getMaxLength(Direction.Axis longAxis, int width) {
        return longAxis == Direction.Axis.Y ? width * HEIGHT_PER_WIDTH : MAX_WIDTH;
    }

    @Override
    public int getMaxWidth() {
        return MAX_WIDTH;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void setHeight(int height) {
        if (this.height != height)
            invalidateStorageCapabilities();
        this.height = height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public void setWidth(int width) {
        if (this.width != width)
            invalidateStorageCapabilities();
        this.width = width;
    }

    private record RecipeTransaction(List<IndustrialBlastFurnaceBlockEntity> parts, List<ItemStack> items,
                                     List<FluidStack> fluids, int batches) {
    }

    private record RecipeInputs(List<ItemStack> items, List<FluidStack> fluids) {
    }

    private record RecipeStart<T extends IndustrialBlastFurnaceRecipe>(T recipe, int parallelBatches,
                                                                     List<ItemClaim> itemClaims) {
    }

    private record ProcessingProgress(int remainingTicks, int totalTicks) {
    }

    private record ItemTooltipEntry(BlockPos pos, ItemStack stack, @Nullable ProcessingProgress progress) {
    }

    private record HeatDisplaySnapshot(HeatDisplayState state, String customCondition) {
    }

    private record HeatProbe(HeatLevel createLevel, String nativeCustomCondition,
                             List<String> externalConditions) {
        private boolean isActive() {
            return !externalConditions.isEmpty() || createLevel != HeatLevel.NONE;
        }

        private boolean satisfies(HeatCondition requirement, @Nullable IndustrialBlastFurnaceRecipe recipe) {
            if (!externalConditions.isEmpty()) {
                for (String sourceCondition : externalConditions) {
                    if (sourceCondition.equalsIgnoreCase(requirement.name()))
                        return true;
                }
                return false;
            }

            try {
                return requirement.testBlazeBurner(createLevel);
            } catch (RuntimeException exception) {
                logHeatCheckFailure(requirement, createLevel, exception);
                return false;
            }
        }

        private String displayCondition() {
            if (externalConditions.size() == 1)
                return externalConditions.get(0);
            return nativeCustomCondition;
        }

        private HeatProbe {
            nativeCustomCondition = nativeCustomCondition == null ? "" : nativeCustomCondition;
            externalConditions = List.copyOf(externalConditions);
        }
    }

    private enum HeatDisplayState {
        UNHEATED,
        LOW_HEATED,
        HEATED,
        SUPERHEATED,
        CUSTOM,
        MIXED_SPECIAL;

        private static HeatDisplayState byName(String name) {
            if (name == null || name.isBlank())
                return UNHEATED;
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return UNHEATED;
            }
        }
    }

    private record ItemClaim(BlockPos pos, ItemStack stack) {
        private ItemClaim {
            pos = pos.immutable();
            stack = stack.copy();
            stack.setCount(Math.min(1, stack.getCount()));
        }
    }

    private static final class ExtractionOnlyItemHandler implements IItemHandler {
        private final IItemHandler delegate;

        private ExtractionOnlyItemHandler(IItemHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    private static List<ItemClaim> copyClaims(List<ItemClaim> claims) {
        List<ItemClaim> copies = new ArrayList<>(claims.size());
        for (ItemClaim claim : claims)
            copies.add(new ItemClaim(claim.pos(), claim.stack()));
        return copies;
    }

    private static ListTag writeItemClaims(List<ItemClaim> claims, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemClaim claim : claims) {
            CompoundTag claimTag = new CompoundTag();
            claimTag.put("Pos", NbtUtils.writeBlockPos(claim.pos()));
            claimTag.put("Stack", claim.stack().save(registries));
            list.add(claimTag);
        }
        return list;
    }

    private static List<ItemClaim> readItemClaims(ListTag list, HolderLookup.Provider registries) {
        List<ItemClaim> claims = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag claimTag = list.getCompound(i);
            if (!claimTag.contains("Pos") || !claimTag.contains("Stack"))
                continue;
            ItemStack stack = ItemStack.parseOptional(registries, claimTag.getCompound("Stack"));
            if (!stack.isEmpty())
                claims.add(new ItemClaim(NbtUtils.readBlockPos(claimTag, "Pos").orElse(null), stack));
        }
        return claims;
    }

    private static ListTag writeItemStacks(List<ItemStack> stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty())
                list.add(stack.save(registries));
        }
        return list;
    }

    private static List<ItemStack> readItemStacks(ListTag list, HolderLookup.Provider registries) {
        List<ItemStack> stacks = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty())
                appendIndividualItems(stacks, List.of(stack));
        }
        return stacks;
    }

    private static final class SmeltingTask {
        private int processingTicks;
        private int totalProcessingTicks;
        private boolean outputsRolled;
        @Nullable
        private ResourceLocation activeRecipeId;
        private List<ItemClaim> itemClaims = new ArrayList<>();
        private List<ItemStack> pendingItemOutputs = new ArrayList<>();

        private boolean isActive() {
            return activeRecipeId != null;
        }

        private void start(ResourceLocation recipeId, List<ItemClaim> claims, int duration) {
            activeRecipeId = recipeId;
            processingTicks = duration;
            totalProcessingTicks = duration;
            outputsRolled = false;
            itemClaims = copyClaims(claims);
            pendingItemOutputs.clear();
        }

        private void clear() {
            processingTicks = 0;
            totalProcessingTicks = 0;
            outputsRolled = false;
            activeRecipeId = null;
            itemClaims.clear();
            pendingItemOutputs.clear();
        }

        private void read(CompoundTag tag, HolderLookup.Provider registries) {
            clear();
            processingTicks = tag.getInt("ProcessingTicks");
            totalProcessingTicks = tag.getInt("TotalProcessingTicks");
            outputsRolled = tag.getBoolean("OutputsRolled");
            activeRecipeId = tag.contains("ActiveRecipe")
                ? ResourceLocation.tryParse(tag.getString("ActiveRecipe"))
                : null;
            itemClaims = readItemClaims(tag.getList("ItemClaims", Tag.TAG_COMPOUND), registries);
            pendingItemOutputs = readItemStacks(tag.getList("PendingItemOutputs", Tag.TAG_COMPOUND), registries);
            if (activeRecipeId == null || itemClaims.isEmpty())
                clear();
        }

        private CompoundTag write(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("ProcessingTicks", processingTicks);
            tag.putInt("TotalProcessingTicks", totalProcessingTicks);
            tag.putBoolean("OutputsRolled", outputsRolled);
            if (activeRecipeId != null)
                tag.putString("ActiveRecipe", activeRecipeId.toString());
            tag.put("ItemClaims", writeItemClaims(itemClaims, registries));
            tag.put("PendingItemOutputs", writeItemStacks(pendingItemOutputs, registries));
            return tag;
        }
    }

    private static final class ProcessingState {
        private int recipeCheckCooldown;
        private int processingTicks;
        private int totalProcessingTicks;
        private int parallelBatches;
        private boolean outputsRolled;
        @Nullable
        private ResourceLocation activeRecipeId;
        private List<ItemClaim> itemClaims = new ArrayList<>();
        private List<ItemStack> pendingItemOutputs = new ArrayList<>();

        private boolean isActive() {
            return activeRecipeId != null || processingTicks != 0 || totalProcessingTicks != 0;
        }

        private void clear() {
            recipeCheckCooldown = 0;
            processingTicks = 0;
            totalProcessingTicks = 0;
            parallelBatches = 0;
            outputsRolled = false;
            activeRecipeId = null;
            itemClaims.clear();
            pendingItemOutputs.clear();
        }

        private void read(CompoundTag tag, String prefix, HolderLookup.Provider registries) {
            recipeCheckCooldown = tag.getInt(prefix + "RecipeCheckCooldown");
            processingTicks = tag.getInt(prefix + "ProcessingTicks");
            totalProcessingTicks = tag.getInt(prefix + "TotalProcessingTicks");
            parallelBatches = tag.getInt(prefix + "ParallelBatches");
            outputsRolled = tag.getBoolean(prefix + "OutputsRolled");
            activeRecipeId = tag.contains(prefix + "ActiveRecipe")
                ? ResourceLocation.tryParse(tag.getString(prefix + "ActiveRecipe"))
                : null;
            itemClaims = readItemClaims(tag.getList(prefix + "ItemClaims", Tag.TAG_COMPOUND), registries);
            pendingItemOutputs = readItemStacks(tag.getList(prefix + "PendingItemOutputs", Tag.TAG_COMPOUND), registries);
            if (activeRecipeId != null) {
                parallelBatches = Math.max(1, parallelBatches);
            } else {
                processingTicks = 0;
                totalProcessingTicks = 0;
                parallelBatches = 0;
                outputsRolled = false;
                itemClaims.clear();
                pendingItemOutputs.clear();
            }
        }

        private void write(CompoundTag tag, String prefix, HolderLookup.Provider registries) {
            tag.putInt(prefix + "RecipeCheckCooldown", recipeCheckCooldown);
            tag.putInt(prefix + "ProcessingTicks", processingTicks);
            tag.putInt(prefix + "TotalProcessingTicks", totalProcessingTicks);
            tag.putInt(prefix + "ParallelBatches", parallelBatches);
            tag.putBoolean(prefix + "OutputsRolled", outputsRolled);
            if (activeRecipeId != null)
                tag.putString(prefix + "ActiveRecipe", activeRecipeId.toString());
            tag.put(prefix + "ItemClaims", writeItemClaims(itemClaims, registries));
            tag.put(prefix + "PendingItemOutputs", writeItemStacks(pendingItemOutputs, registries));
        }
    }
}

