package dev.gensomechanic.mining.client;

import java.util.Random;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.logistics.depot.DepotRenderer;
import dev.gensomechanic.mining.content.miner.MechanicalMinerBlockEntity;
import dev.gensomechanic.mining.content.miner.MinerStructure;
import dev.gensomechanic.mining.content.miner.MiningRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class MinerItemDisplay {
    // Create depot: surface Y=13, render origin Y=15. Our supplied plate surface is Y=4.
    public static final double OUTPUT_ORIGIN_Y = 6 / 16d;
    static ItemStack output(MechanicalMinerBlockEntity be) {
        for (int slot = 0; slot < MiningRecipe.MAX_OUTPUTS; slot++) {
            ItemStack stack = be.outputInSlot(slot);
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }
    static void render(MechanicalMinerBlockEntity be, ItemStack stack, boolean stacked, double z,
                       PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!stacked) {
            render(be.getLevel(), stack, false, z, pose, buffers, light, overlay);
            return;
        }
        int turns = MinerStructure.turns(MinerStructure.facing(be.getBlockState()));
        Vec3 displacement = new Vec3(0, 0, z - .5).yRot((float) Math.toRadians(-90 * turns));
        Vec3 worldPosition = Vec3.atLowerCornerOf(be.getBlockPos()).add(.5, OUTPUT_ORIGIN_Y, .5).add(displacement);
        renderOutput(stack, z, turns * 90, worldPosition, pose, buffers, light, overlay);
    }
    public static void render(net.minecraft.world.level.Level level, ItemStack stack, boolean stacked, double z,
                       PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (stack.isEmpty()) return;
        if (stacked) {
            renderOutput(stack, z, 0, Vec3.ZERO, pose, buffers, light, overlay);
            return;
        }
        var renderer = Minecraft.getInstance().getItemRenderer();
        var model = renderer.getModel(stack, level, null, 0);
        boolean block = model.isGui3d(), upright = BeltHelper.isItemUpright(stack);
        pose.pushPose();
        pose.translate(.5, 4 / 16d + (block || upright ? .25 : .008), z);
        if (!block && !upright) pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(.5f, .5f, .5f);
        renderer.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
        pose.popPose();
    }
    private static void renderOutput(ItemStack stack, double z, float undoMachineYaw, Vec3 worldPosition,
                                     PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        pose.translate(.5, OUTPUT_ORIGIN_Y, z);
        // Depot's upright camera calculation is in world space; cancel only the machine yaw.
        // The translated origin still follows the tray and its animation.
        pose.mulPose(Axis.YP.rotationDegrees(undoMachineYaw));
        DepotRenderer.renderItem(pose, buffers, light, overlay, stack, 0, new Random(0), worldPosition, false);
        pose.popPose();
    }
    private MinerItemDisplay() {}
}
