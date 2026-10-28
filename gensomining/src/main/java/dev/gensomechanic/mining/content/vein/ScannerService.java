package dev.gensomechanic.mining.content.vein;

import dev.gensomechanic.mining.GensoMining;
import dev.gensomechanic.mining.content.miner.MiningContent;
import dev.gensomechanic.mining.network.MiningNetwork;
import dev.gensomechanic.mining.network.ScannerOverlayPayload;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = GensoMining.MOD_ID)
public final class ScannerService {
    public static final int SCAN_TICKS = 50;
    public static final int ANALYSIS_TICKS = 30;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue COOLDOWN = BUILDER.comment("Geological scanner cooldown in ticks.").defineInRange("scannerCooldown", 100, 1, 1200);
    public static final ModConfigSpec CONFIG = BUILDER.build();
    private static final class Pending {
        final ServerPlayer player; final ResourceKey<Level> dimension; final long due;
        final UUID scan; List<Component> lines; List<ScannerOverlayPayload.Mark> marks;
        final String generation; VeinScan result;
        final double x,z;final long time;final ScanSelection target;
        final net.minecraft.core.BlockPos origin;
        final Map<Long,Optional<VeinGenerator.Occurrence>> answers=new HashMap<>();
        final int minX,maxX,maxZ;int cx,cz;
        boolean analysing;
        Pending(ServerPlayer player, long due, ScanSelection target) {
            this.player=player;this.dimension=player.level().dimension();this.due=due;
            this.generation=VeinManager.signature(player.serverLevel());this.scan=UUID.randomUUID();
            this.x=player.getX();this.z=player.getZ();this.time=player.level().getDayTime();this.target=target;
            this.origin=player.blockPosition().immutable();
            var bounds=VeinScan.bounds(x,z,VeinScan.RANGE);minX=bounds.minX();maxX=bounds.maxX();cx=minX;
            cz=bounds.minZ();maxZ=bounds.maxZ();
        }
        void compute(long deadline){
            if(result!=null)return;
            for(int count=0;cz<=maxZ&&count<64&&System.nanoTime()<deadline;count++){
                var p=new net.minecraft.world.level.ChunkPos(cx,cz);
                if(VeinScan.intersects(x,z,VeinScan.RANGE,p)){
                    var ready=VeinManager.query(player.serverLevel()).tryFindUntil(p,deadline);if(ready.isEmpty())continue;
                    answers.put(p.toLong(),ready.get());
                }
                if(++cx>maxX){cx=minX;cz++;}
            }
            if(cz<=maxZ)return;
            result=VeinScan.scan(x,z,target,p->answers.getOrDefault(p.toLong(),Optional.empty()));
            lines=ScannerReport.create(player.serverLevel(),origin,result,time);
            marks=ScannerOverlayPayload.marks(result);answers.clear();
        }
    }

    private record Session(ServerPlayer player, UUID token, InteractionHand hand, int slot, ItemStack stack, ResourceKey<Level> dimension, long expires) {}
    private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    public static MiningNetwork.Catalog catalogue(MinecraftServer server) {
        Map<ResourceLocation,String> layouts=new HashMap<>();
        server.getAllLevels().forEach(level->layouts.put(level.dimension().location(),VeinManager.signature(level)));
        return new MiningNetwork.Catalog(VeinManager.definitions(server).values().stream().sorted(Comparator.comparing(v -> v.id().toString()))
                .map(v -> VeinPublicMetadata.entry(VeinManager.generator(server),v)).toList(), VeinManager.signature(server),layouts,
                VeinManager.backgrounds(server).stream().map(RockBackgroundDefinition::metadata).toList());
    }
    @SubscribeEvent public static void synchronize(OnDatapackSyncEvent event) {
        dev.gensomechanic.mining.content.miner.MiningRecipes.forServer(event.getPlayerList().getServer());
        if(event.getPlayer()!=null) ArchiveService.invalidate(event.getPlayer());
        else event.getPlayerList().getPlayers().forEach(ArchiveService::invalidate);
        var packet = catalogue(event.getPlayerList().getServer());
        if (event.getPlayer() != null) PacketDistributor.sendToPlayer(event.getPlayer(), packet);
        else for (var player : event.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(player, packet);
    }
    public static UUID open(ServerPlayer player, InteractionHand hand) {
        return open(player, hand, false);
    }
    public static UUID openArchive(ServerPlayer player, InteractionHand hand) { return open(player, hand, true); }
    private static UUID open(ServerPlayer player, InteractionHand hand, boolean archive) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(archive ? MiningContent.ARCHIVE.get() : MiningContent.SCANNER.get()) || !player.isAlive()) return null;
        UUID token = UUID.randomUUID();
        SESSIONS.put(player.getUUID(), new Session(player, token, hand, player.getInventory().selected, stack, player.level().dimension(), player.serverLevel().getGameTime() + 12000));
        ArchiveService.invalidate(player);
        PacketDistributor.sendToPlayer(player, catalogue(player.server));
        PacketDistributor.sendToPlayer(player, new MiningNetwork.Open(token, normalize(player.server, archive ? ScannerTarget.archive(stack) : ScannerTarget.selection(stack), archive), archive));
        return token;
    }
    private static boolean valid(Session session) {
        ServerPlayer player = session.player;
        return player.isAlive() && !player.isRemoved() && player.level().dimension().equals(session.dimension)
                && player.level().getGameTime() <= session.expires && player.getItemInHand(session.hand) == session.stack
                && (session.stack.is(MiningContent.SCANNER.get()) || session.stack.is(MiningContent.ARCHIVE.get())) && (session.hand == InteractionHand.OFF_HAND || player.getInventory().selected == session.slot);
    }
    public static boolean configure(ServerPlayer player, UUID token, ResourceLocation target) {
        return configureSelection(player, token, ScanSelection.of(target));
    }
    public static boolean configureSelection(ServerPlayer player, UUID token, ScanSelection selection) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(token) || session.player != player) return false;
        SESSIONS.remove(player.getUUID());
        if (!valid(session) || selection.type().filter(id -> !VeinManager.definitions(player.server).containsKey(id)).isPresent()) return false;
        boolean archive = session.stack.is(MiningContent.ARCHIVE.get());
        var normalized = normalize(player.server, selection, archive);
        if (archive) session.stack.set(ScannerTarget.ARCHIVE, normalized); else ScannerTarget.select(session.stack, normalized);
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); return true;
    }
    public static ScanSelection normalize(MinecraftServer server, ScanSelection selection, boolean archive) {
        if (selection.all()) return selection;
        var definition = selection.type().map(id -> VeinManager.definitions(server).get(id)).orElse(null);
        return definition == null ? (archive ? ScanSelection.ALL : selection) : ScanSelection.of(definition.searchId());
    }
    public static boolean start(ServerPlayer player, InteractionHand hand) {
        var level = player.serverLevel();
        if (!player.isAlive() || !player.getItemInHand(hand).is(MiningContent.SCANNER.get())
                || player.getCooldowns().isOnCooldown(MiningContent.SCANNER.get()) || PENDING.containsKey(player.getUUID())) return false;
        ScanSelection target = normalize(player.server, ScannerTarget.selection(player.getItemInHand(hand)), false);
        if (!target.all() && (target.type().isEmpty() || !VeinManager.definitions(player.server).containsKey(target.type().get()))) {
            player.displayClientMessage(Component.translatable("scanner.gensomining.no_target").withStyle(ChatFormatting.RED), true); return false;
        }
        var pending = new Pending(player, level.getGameTime() + SCAN_TICKS, target);
        PENDING.put(player.getUUID(), pending);
        PacketDistributor.sendToPlayer(player, new ScannerOverlayPayload(pending.scan, level.dimension().location(), ScannerOverlayPayload.Stage.START, List.of(), pending.generation, 0, 1));
        player.getCooldowns().addCooldown(MiningContent.SCANNER.get(), COOLDOWN.get());
        player.displayClientMessage(Component.translatable("scanner.gensomining.scanning").withStyle(ChatFormatting.WHITE), true);
        var origin = player.position().add(0, .15, 0);
        PacketDistributor.sendToPlayersNear(level, null, origin.x, origin.y, origin.z, 64, new MiningNetwork.Wave(origin));
        return true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        VeinPlanningScheduler.pump(event.getServer(),false);
        var iterator = PENDING.values().iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next(); ServerPlayer player = pending.player;
            if (player.getServer() != event.getServer()) continue;
            if (!player.isAlive() || player.isRemoved() || !player.level().dimension().equals(pending.dimension)) { iterator.remove(); continue; }
            if (!pending.generation.equals(VeinManager.signature(player.serverLevel()))) { iterator.remove(); continue; }
            if (player.level().getGameTime() < pending.due) continue;
            if (!pending.analysing) {
                pending.analysing = true;
                player.displayClientMessage(Component.translatable("scanner.gensomining.analysing").withStyle(ChatFormatting.WHITE), true);
            }
            if (player.level().getGameTime() < pending.due + ANALYSIS_TICKS || pending.result==null) continue;
            iterator.remove(); player.displayClientMessage(Component.empty(), true);
            ExplorationData.get(player).discover(pending.result, pending.generation);
            var definitions = VeinManager.definitions(player.server);
            var marks = pending.marks.stream().map(m -> { var d = definitions.get(m.vein()); return new ScannerOverlayPayload.Mark(m.chunk(), m.vein(), d.name(), m.centre(), d.mapColor()); }).toList();
            for (var fragment : ScannerOverlayPayload.fragments(pending.scan, pending.dimension.location(), pending.generation, marks, player.registryAccess())) PacketDistributor.sendToPlayer(player, fragment);
            pending.lines.forEach(line -> player.displayClientMessage(line, false));
        }
        SESSIONS.values().removeIf(s -> s.player.getServer() == event.getServer() && !valid(s));
    }
    static boolean pumpOne(MinecraftServer server,long deadline) {
        for(var id:new ArrayList<>(PENDING.keySet())) {
            var pending=PENDING.get(id);
            if(pending.player.server!=server||pending.result!=null)continue;
            if(!pending.player.isAlive()||pending.player.isRemoved()||!pending.player.level().dimension().equals(pending.dimension)
                ||!pending.generation.equals(VeinManager.signature(pending.player.serverLevel()))) {PENDING.remove(id);continue;}
            pending.compute(deadline);PENDING.remove(id);PENDING.put(id,pending);return true;
        }
        return false;
    }
    private static void cancel(UUID id) { PENDING.remove(id); SESSIONS.remove(id); }
    static boolean running(UUID id){return PENDING.containsKey(id);}
    private static void clearOverlay(ServerPlayer player) {
        cancel(player.getUUID());

    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { cancel(e.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) clearOverlay(p); }
    @SubscribeEvent public static void died(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) clearOverlay(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {
        PENDING.values().removeIf(p -> p.player.getServer() == e.getServer()); SESSIONS.values().removeIf(s -> s.player.getServer() == e.getServer());
    }
    private ScannerService() {}
}

