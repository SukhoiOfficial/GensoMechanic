package dev.gensomechanic.metallurgy.client;

import dev.gensomechanic.metallurgy.*;
import dev.gensomechanic.metallurgy.client.compat.MetallurgyJadeClient;
import dev.gensomechanic.metallurgy.compat.jade.MetallurgyJadePlugin;
import dev.gensomechanic.metallurgy.content.industrial_blast_furnace.IndustrialBlastFurnaceBlockEntity;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.impl.WailaClientRegistration;

/** Optional Jade path: native accessor, actual server provider, registry-aware client views. */
final class MetallurgyJadeClientChecks {
    private static CompletableFuture<List<List<ViewGroup<CompoundTag>>>> pending;
    private static boolean done;
    static boolean advance() {
        if(done)return true;
        var mc=Minecraft.getInstance();
        if(pending==null) {
            var server=mc.getSingleplayerServer();
            pending=server.submit(()->{
                var level=server.overworld();var pos=level.getSharedSpawnPos().above(10);
                level.getChunkAt(pos);level.setBlockAndUpdate(pos,MetallurgyBlocks.INDUSTRIAL_BLAST_FURNACE.getDefaultState());
                var furnace=(IndustrialBlastFurnaceBlockEntity)level.getBlockEntity(pos);
                for(int i=0;i<3;i++)furnace.tick();
                var accessor=WailaClientRegistration.instance().blockAccessor().level(level)
                        .player(server.getPlayerList().getPlayers().getFirst()).serverData(new CompoundTag())
                        .serverConnected(true).showDetails(true).hit(new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false))
                        .blockState(furnace.getBlockState()).blockEntity(furnace).build();
                var storage=Objects.requireNonNull(level.getCapability(Capabilities.FluidHandler.BLOCK,pos,(Direction)null));
                List<List<ViewGroup<CompoundTag>>> snapshots=new ArrayList<>();
                snapshots.add(snapshot(accessor));
                storage.fill(new FluidStack((net.minecraft.world.level.material.Fluid) GensoFluids.get(GensoFluids.MoltenMetal.COPPER).orElseThrow().getSource(),250),IFluidHandler.FluidAction.EXECUTE);
                snapshots.add(snapshot(accessor));
                storage.fill(new FluidStack((net.minecraft.world.level.material.Fluid) GensoFluids.get(GensoFluids.MoltenMetal.ZINC).orElseThrow().getSource(),250),IFluidHandler.FluidAction.EXECUTE);
                snapshots.add(snapshot(accessor));level.removeBlock(pos,false);
                return List.copyOf(snapshots);
            });
            return false;
        }
        if(!pending.isDone())return false;
        var pos=mc.player.blockPosition();
        var accessor=WailaClientRegistration.instance().blockAccessor().level(mc.level).player(mc.player)
                .serverData(new CompoundTag()).serverConnected(true).showDetails(true)
                .hit(new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)).blockState(mc.level.getBlockState(pos)).build();
        var snapshots=pending.join();
        for(int i=0;i<snapshots.size();i++) {
            var groups=MetallurgyJadeClient.Fluids.INSTANCE.getClientGroups(accessor,snapshots.get(i));
            MetallurgyClientChecks.require(groups.size()==1,"Jade should report one shared group");
            var group=groups.getFirst();
            MetallurgyClientChecks.require(group.views.size()==(i==2?2:1),"Jade fluid rows do not match storage");
            for(var view:group.views) {
                float ratio=i==0?0:.25f;
                MetallurgyClientChecks.require(Math.abs(view.ratio-ratio)<.0001,"Jade fill ratio is not based on shared 1000 mB capacity");
                if(i<2) {
                    var nativeView = snownee.jade.api.view.FluidView.readDefault(snapshots.get(i).getFirst().views.getFirst(), accessor.nbtOps());
                    MetallurgyClientChecks.require(Objects.equals(view.overrideText, nativeView.overrideText),
                            "Empty/single fluid should keep native Jade text");
                }
                else {
                    MetallurgyClientChecks.require(view.overrideText!=null,"Multi-fluid row lacks amount-only text");
                    MetallurgyClientChecks.require(view.overrideText.getString().contains(view.current),"Multi-fluid row lost its own amount");
                }
            }
            if(i==2) {
                MetallurgyClientChecks.require(group.title!=null,"Multi-fluid group lacks shared title");
                var text=group.title.getString();
                MetallurgyClientChecks.require(text.contains(snownee.jade.util.FluidTextHelper.getUnicodeMillibuckets(500, true))
                        && text.contains(snownee.jade.util.FluidTextHelper.getUnicodeMillibuckets(1000, true)),
                        "Shared title lost total/capacity: "+text);
            }
        }
        done=true;GensoMetallurgy.LOGGER.info("METALLURGY JADE CLIENT PASS: actual server provider, native accessors and client registry decoding, empty/single defaults, two amount-only rows with shared 500/1000 capacity");
        return true;
    }
    private static List<ViewGroup<CompoundTag>> snapshot(BlockAccessor accessor) {
        return MetallurgyJadePlugin.Fluids.INSTANCE.getGroups(accessor).stream().map(source->{
            var copy=new ViewGroup<>(source.views.stream().map(CompoundTag::copy).toList());
            copy.getExtraData().merge(source.getExtraData().copy());return copy;
        }).toList();
    }
}
