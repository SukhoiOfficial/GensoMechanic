package dev.gensomechanic.metallurgy;

import com.tterrag.registrate.util.entry.FluidEntry;
import dev.gensomechanic.metallurgy.content.fluid.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import java.util.*;

public final class GensoFluids {
    public static final TagKey<Fluid> MOLTEN_METALS = TagKey.create(Registries.FLUID, GensoMetallurgy.asResource("molten_metals"));
    private static final Map<MoltenMetal, FluidEntry<BaseFlowingFluid.Flowing>> REGISTERED = new EnumMap<>(MoltenMetal.class);
    static {
        var r = GensoMetallurgy.registrate(); r.setCreativeTab(GensoCreativeModeTabs.MAIN);
        for (var metal : MoltenMetal.values()) {
            String name = "molten_" + metal.serializedName();
            REGISTERED.put(metal, r.fluid(name, GensoMetallurgy.asResource("fluid/molten_metals/" + metal.serializedName() + "/still"), GensoMetallurgy.asResource("fluid/molten_metals/" + metal.serializedName() + "/flowing"), (p,s,f) -> new MoltenMetalFluidType(p))
                .lang("Molten " + metal.englishName())
                .properties(p -> p.temperature(metal.temperature()).density(3000).viscosity(2000).lightLevel(15)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)
                    .canHydrate(false).canDrown(false).canSwim(false))
                .fluidProperties(p -> p.levelDecreasePerBlock(1).slopeFindDistance(4).tickRate(10).explosionResistance(100))
                .source(BaseFlowingFluid.Source::new).block().properties(p -> p.lightLevel(s -> 15)).lang("Molten " + metal.englishName()).build()
                .bucket((fluid,p) -> new MoltenMetalBucketItem(fluid,p,metal)).properties(p -> p.stacksTo(1))
                .model((c,p) -> p.generated(c, GensoMetallurgy.asResource("item/buckets/" + metal.serializedName())))
                .lang("Molten " + metal.englishName() + " Bucket").build().register());
        }
    }
    public static Optional<FluidEntry<BaseFlowingFluid.Flowing>> get(MoltenMetal metal) { return Optional.ofNullable(REGISTERED.get(metal)); }
    public static List<FluidEntry<BaseFlowingFluid.Flowing>> registeredFluids() { return List.copyOf(REGISTERED.values()); }
    public static boolean visible(Item item) {
        for (var e : REGISTERED.entrySet()) if (e.getValue().getBucket().orElseThrow() == item) return e.getKey().isAvailable();
        return true;
    }
    public static void register(IEventBus bus) {
        bus.addListener((RegisterCapabilitiesEvent e) -> {
            for (var fluid : REGISTERED.values()) e.registerItem(Capabilities.FluidHandler.ITEM, (stack,ctx) -> new FluidBucketWrapper(stack), fluid.getBucket().orElseThrow());
        });
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            for (var fluid : REGISTERED.values()) DispenserBlock.registerBehavior(fluid.getBucket().orElseThrow(), DispenserBlock.DISPENSER_REGISTRY.get(net.minecraft.world.item.Items.LAVA_BUCKET));
        }));
    }
    public enum MoltenMetal {
        BRASS("Brass",932), COPPER("Copper",1085), GOLD("Gold",1064), IRON("Iron",1538), NETHERITE("Netherite",3524), ZINC("Zinc",419);
        private final String name; private final int temperature;
        MoltenMetal(String name, int temperature) { this.name=name; this.temperature=temperature; }
        public String serializedName() { return name().toLowerCase(Locale.ROOT); }
        public String englishName() { return name; }
        public int temperature() { return temperature; }
        public TagKey<Item> ingotTag() { return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/" + serializedName())); }
        public boolean isAvailable() { return BuiltInRegistries.ITEM.getTag(ingotTag()).map(t -> t.size() > 0).orElse(false); }
    }
    private GensoFluids() {}
}
