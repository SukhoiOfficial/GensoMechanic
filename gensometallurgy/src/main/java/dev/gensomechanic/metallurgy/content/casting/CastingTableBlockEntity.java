// Ported from Create: Processing Additions. Copyright (c) 2026 Sukhoi. MIT.
package dev.gensomechanic.metallurgy.content.casting;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.gensomechanic.metallurgy.foundation.GoggleTooltipHelper;
import dev.gensomechanic.metallurgy.content.metallurgy.MetallurgyRecipeTypes;
import dev.gensomechanic.metallurgy.content.metallurgy.MetallurgyStoragePort;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.capabilities.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CastingTableBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int FLUID_CAPACITY = 90;
    public static final int OUTPUT_CAPACITY = 1;

    private static final int DEFAULT_PROCESSING_TIME = 200;
    private static final int RECIPE_CHECK_INTERVAL = 10;

    private SmartFluidTankBehaviour fluidTank;
    private final ItemStackHandler outputInventory;
    private MetallurgyStoragePort storagePort;
    private boolean reading;
    private int recipeCheckCooldown;
    private int processingTicks;
    private int totalProcessingTicks;
    private int fanSearchCooldown;
    private boolean fanAssisted;
    private final List<FanCandidate> cachedFans = new ArrayList<>();
    @Nullable
    private ResourceLocation activeRecipeId;

    public CastingTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        outputInventory = new ItemStackHandler(1) {
            @Override
            public int getSlotLimit(int slot) {
                return OUTPUT_CAPACITY;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                ItemStack stored = stack.copy();
                stored.setCount(Math.min(stored.getCount(), OUTPUT_CAPACITY));
                super.setStackInSlot(slot, stored);
            }

            @Override
            protected void onContentsChanged(int slot) {
                if (!reading)
                    onOutputChanged();
            }
        };
        initializeCapabilities();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        fluidTank = SmartFluidTankBehaviour.single(this, FLUID_CAPACITY);
        behaviours.add(fluidTank);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null)
            return;
        if (level.isClientSide) {
            if (activeRecipeId != null && processingTicks > 0)
                processingTicks = Math.max(0, processingTicks - (fanAssisted ? 2 : 1));
            return;
        }

        boolean currentFanAssisted = hasValidCastingAirCurrent();
        if (fanAssisted != currentFanAssisted) {
            fanAssisted = currentFanAssisted;
            setChanged();
            notifyUpdate();
        }

        if (activeRecipeId != null) {
            Optional<CastingRecipe> activeRecipe = getActiveRecipe();
            if (activeRecipe.isEmpty() || !canProcess(activeRecipe.get())) {
                stopProcessing();
                return;
            }

            int previousProcessingTicks = processingTicks;
            processingTicks -= fanAssisted ? 2 : 1;
            if (processingTicks <= 0) {
                finishProcessing(activeRecipe.get());
                stopProcessing();
                recipeCheckCooldown = 1;
            } else if (processingTicks / 20 != previousProcessingTicks / 20) {
                setChanged();
                notifyUpdate();
            }
            return;
        }

        if (recipeCheckCooldown-- > 0)
            return;
        recipeCheckCooldown = RECIPE_CHECK_INTERVAL;
        findMatchingRecipe().ifPresent(this::startProcessing);
    }

    private boolean hasValidCastingAirCurrent() {
        if (fanSearchCooldown-- <= 0) {
            refreshFanCache();
            fanSearchCooldown = Math.max(0, AllConfigs.server().kinetics.fanBlockCheckRate.get() - 1);
        }

        boolean missingFan = false;
        for (FanCandidate candidate : cachedFans) {
            if (!level.isLoaded(candidate.pos()))
                continue;

            BlockEntity blockEntity = level.getBlockEntity(candidate.pos());
            if (!(blockEntity instanceof EncasedFanBlockEntity fan)) {
                missingFan = true;
                continue;
            }

            if (isValidCastingAirCurrent(fan, candidate))
                return true;
        }

        if (missingFan)
            fanSearchCooldown = 0;
        return false;
    }

    private void refreshFanCache() {
        cachedFans.clear();
        int maxDistance = Math.max(AllConfigs.server().kinetics.fanPushDistance.get(),
            AllConfigs.server().kinetics.fanPullDistance.get());

        for (Direction directionToFan : Direction.values()) {
            for (int distance = 1; distance <= maxDistance; distance++) {
                BlockPos candidatePos = worldPosition.relative(directionToFan, distance);
                if (!level.isLoaded(candidatePos))
                    break;
                if (level.getBlockEntity(candidatePos) instanceof EncasedFanBlockEntity)
                    cachedFans.add(new FanCandidate(candidatePos, directionToFan.getOpposite(), distance));
            }
        }
    }

    private boolean isValidCastingAirCurrent(EncasedFanBlockEntity fan, FanCandidate candidate) {
        if (fan.getSpeed() == 0)
            return false;

        AirCurrent airCurrent = fan.getAirCurrent();
        if (airCurrent == null || airCurrent.direction != candidate.directionFromFan()
            || airCurrent.maxDistance <= 0)
            return false;

        float offset = candidate.distance() - 1;
        if (offset > airCurrent.maxDistance || !airCurrent.bounds.intersects(new AABB(worldPosition)))
            return false;

        FanProcessingType processingType = airCurrent.getTypeAt(offset);
        return processingType == null || processingType == AllFanProcessingTypes.SPLASHING;
    }

    private Optional<RecipeHolder<CastingRecipe>> findMatchingRecipe() {
        RecipeType<CastingRecipe> recipeType = MetallurgyRecipeTypes.CASTING.getType();
        return level.getRecipeManager()
            .getAllRecipesFor(recipeType)
            .stream()
            .filter(h -> canProcess(h.value()))
            .max(Comparator.comparingInt(recipe -> recipe.value().getFluidIngredients().get(0).amount()));
    }

    private boolean canProcess(CastingRecipe recipe) {
        if (!recipe.getIngredients().isEmpty() || recipe.getFluidIngredients().size() != 1
            || recipe.getRollableResults().size() != 1 || !recipe.getFluidResults().isEmpty()
            || !outputInventory.getStackInSlot(0).isEmpty()) {
            return false;
        }

        ProcessingOutput result = recipe.getRollableResults().get(0);
        ItemStack resultStack = result.getStack();
        if (resultStack.isEmpty() || resultStack.getCount() > OUTPUT_CAPACITY)
            return false;

        SizedFluidIngredient ingredient = recipe.getFluidIngredients().get(0);
        FluidStack stored = fluidTank.getPrimaryHandler().getFluid();
        return !stored.isEmpty() && stored.getAmount() >= ingredient.amount()
            && ingredient.test(stored);
    }

    private void startProcessing(RecipeHolder<CastingRecipe> holder) {
        CastingRecipe recipe = holder.value();
        activeRecipeId = holder.id();
        totalProcessingTicks = recipe.getProcessingDuration() > 0
            ? recipe.getProcessingDuration()
            : DEFAULT_PROCESSING_TIME;
        processingTicks = totalProcessingTicks;
        cachedFans.clear();
        fanSearchCooldown = 0;
        setChanged();
        sendData();
    }

    private void finishProcessing(CastingRecipe recipe) {
        if (!canProcess(recipe))
            return;

        List<ItemStack> rolledResults = recipe.rollResults(level.random);
        if (rolledResults.size() > 1
            || (!rolledResults.isEmpty() && rolledResults.get(0).getCount() > OUTPUT_CAPACITY)) {
            return;
        }

        int requiredAmount = recipe.getFluidIngredients().get(0).amount();
        fluidTank.getPrimaryHandler().drain(requiredAmount, IFluidHandler.FluidAction.EXECUTE);
        if (!rolledResults.isEmpty() && !rolledResults.get(0).isEmpty())
            outputInventory.setStackInSlot(0, rolledResults.get(0));
        fluidTank.sendDataImmediately();
        setChanged();
        notifyUpdate();
    }

    private Optional<CastingRecipe> getActiveRecipe() {
        if (level == null || activeRecipeId == null)
            return Optional.empty();
        Optional<? extends Recipe<?>> recipe = level.getRecipeManager().byKey(activeRecipeId).map(RecipeHolder::value);
        return recipe.filter(CastingRecipe.class::isInstance).map(CastingRecipe.class::cast);
    }

    private void stopProcessing() {
        if (activeRecipeId == null && processingTicks == 0)
            return;
        activeRecipeId = null;
        processingTicks = 0;
        totalProcessingTicks = 0;
        cachedFans.clear();
        fanSearchCooldown = 0;
        setChanged();
        sendData();
    }

    private void onOutputChanged() {
        if (!hasLevel())
            return;
        setChanged();
        if (!level.isClientSide)
            notifyUpdate();
    }

    public SmartFluidTankBehaviour getFluidTank() {
        return fluidTank;
    }

    public ItemStack getOutput() {
        return outputInventory.getStackInSlot(0);
    }

    public boolean hasOutput() {
        return !getOutput().isEmpty();
    }

    private boolean isProcessing() {
        return activeRecipeId != null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        GoggleTooltipHelper.add(tooltip,
            Component.translatable("gensometallurgy.goggles.casting_table.status")
                .withStyle(ChatFormatting.WHITE));

        FluidStack stored = fluidTank == null ? FluidStack.EMPTY : fluidTank.getPrimaryHandler().getFluid();
        Component fluidName = stored.isEmpty()
            ? Component.translatable("gensometallurgy.goggles.empty")
            : stored.getHoverName();
        MutableComponent fluidRow = Component.empty()
            .append(fluidName.copy().withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" " + stored.getAmount() + "mB/" + FLUID_CAPACITY + "mB")
                .withStyle(ChatFormatting.BLUE));
        GoggleTooltipHelper.add(tooltip, fluidRow, 1);

        if (isProcessing()) {
            Component progress = GoggleTooltipHelper.progressBar(processingTicks, totalProcessingTicks, 0xFFFFFF);
            GoggleTooltipHelper.add(tooltip,
                Component.translatable("gensometallurgy.goggles.casting_table.progress", progress)
                    .withStyle(ChatFormatting.GRAY), 1);
        }
        if (fanAssisted) {
            GoggleTooltipHelper.add(tooltip,
                Component.translatable("gensometallurgy.goggles.casting_table.fan")
                    .withStyle(ChatFormatting.GRAY), 1);
        }
        return true;
    }

    public ItemStack takeOutput() {
        return outputInventory.extractItem(0, OUTPUT_CAPACITY, false);
    }

    private void initializeCapabilities() {
        storagePort = new MetallurgyStoragePort(outputInventory, new OutputLockedFluidHandler(),
            () -> MetallurgyStoragePort.isPresent(this));
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        var type = dev.gensomechanic.metallurgy.MetallurgyBlockEntities.CASTING_TABLE.get();
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.storagePort.isValid() ? be.storagePort.items() : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> be.storagePort.isValid() ? be.storagePort.fluids() : null);
    }
    @Override public void clearRemoved() {
        super.clearRemoved();
        if (storagePort != null) storagePort.invalidate();
        initializeCapabilities();
        if (level != null) level.invalidateCapabilities(worldPosition);
    }

    @Override
    public void invalidate() {
        storagePort.invalidate();
        if (level != null) level.invalidateCapabilities(worldPosition);
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        storagePort.invalidate();
        super.onChunkUnloaded();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("OutputInventory", outputInventory.serializeNBT(registries));
        tag.putInt("RecipeCheckCooldown", recipeCheckCooldown);
        tag.putInt("ProcessingTicks", processingTicks);
        tag.putInt("TotalProcessingTicks", totalProcessingTicks);
        tag.putBoolean("FanAssisted", fanAssisted);
        if (activeRecipeId != null)
            tag.putString("ActiveRecipe", activeRecipeId.toString());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        reading = true;
        try {
            if (tag.contains("OutputInventory"))
                outputInventory.deserializeNBT(registries, tag.getCompound("OutputInventory"));
        } finally {
            reading = false;
        }
        recipeCheckCooldown = tag.getInt("RecipeCheckCooldown");
        processingTicks = tag.getInt("ProcessingTicks");
        totalProcessingTicks = tag.getInt("TotalProcessingTicks");
        fanAssisted = tag.getBoolean("FanAssisted");
        activeRecipeId = tag.contains("ActiveRecipe")
            ? ResourceLocation.tryParse(tag.getString("ActiveRecipe"))
            : null;
    }

    private record FanCandidate(BlockPos pos, Direction directionFromFan, int distance) {
        private FanCandidate {
            pos = pos.immutable();
        }
    }

    private class OutputLockedFluidHandler implements IFluidHandler {
        private IFluidHandler delegate() {
            return fluidTank.getPrimaryHandler();
        }

        @Override
        public int getTanks() {
            return delegate().getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return delegate().getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return delegate().getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return delegate().isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return hasOutput() ? 0 : delegate().fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return isProcessing() ? FluidStack.EMPTY : delegate().drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return isProcessing() ? FluidStack.EMPTY : delegate().drain(maxDrain, action);
        }
    }
}
