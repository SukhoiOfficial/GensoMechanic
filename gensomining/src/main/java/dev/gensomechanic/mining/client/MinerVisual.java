package dev.gensomechanic.mining.client;

import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import dev.gensomechanic.mining.content.miner.*;
import net.minecraft.core.Direction;
import java.util.function.Consumer;

/** Native shader clock and phase; no local angle accumulator or global renderer patches. */
public final class MinerVisual extends KineticBlockEntityVisual<MechanicalMinerBlockEntity> implements SimpleTickableVisual {
    private final RotatingInstance shaft, drill, fan;
    public MinerVisual(VisualizationContext context, MechanicalMinerBlockEntity be, float pt) {
        super(context, be, pt);
        shaft = instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(GensoPartialModels.SHAFT)).createInstance();
        drill = instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(GensoPartialModels.DRILL)).createInstance();
        fan = instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(GensoPartialModels.FAN_VISUAL)).createInstance();
        update(pt); updateLight(pt);
    }
    @Override public void update(float pt) {
        Direction facing = MinerStructure.facing(blockEntity.getBlockState());
        float yaw = (float) Math.toRadians(-90 * MinerStructure.turns(facing));
        for (var instance : new RotatingInstance[]{shaft, drill}) {
            instance.rotation.identity().rotateY(yaw);
            instance.setup(blockEntity).setPosition(getVisualPosition()).setChanged();
        }
        var position = getVisualPosition().offset(MinerStructure.offset(3, facing));
        Direction axis = facing.getClockWise();
        fan.rotation.identity().rotateY(yaw);
        fan.setPosition(position).setRotationAxis(axis.getStepX(), 0, axis.getStepZ())
                .setRotationalSpeed(-blockEntity.getSpeed() * RotatingInstance.SPEED_MULTIPLIER)
                .setRotationOffset(-KineticBlockEntityRenderer.getRotationOffsetForPosition(blockEntity, blockEntity.getBlockPos(), Direction.Axis.Y)).setChanged();
    }
    @Override public void tick(Context context) { applyOverstressEffect(blockEntity, shaft, drill, fan); }
    @Override public void updateLight(float pt) { relight(shaft, drill, fan); }
    @Override protected void _delete() { shaft.delete(); drill.delete(); fan.delete(); }
    @Override public void collectCrumblingInstances(Consumer<Instance> consumer) { consumer.accept(shaft); consumer.accept(drill); consumer.accept(fan); }
}
