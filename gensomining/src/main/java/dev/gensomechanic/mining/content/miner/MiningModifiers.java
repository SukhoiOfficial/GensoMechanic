package dev.gensomechanic.mining.content.miner;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.common.conditions.ConditionalOps;

/** Immutable, validated choices. Operations are interned so one tick cannot change its capacity cost. */
public final class MiningModifiers {
    public static final Codec<Integer> INTEGER = Codec.DOUBLE.comapFlatMap(n ->
            Double.isFinite(n) && n == Math.rint(n) && n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE
                    ? DataResult.success(n.intValue()) : DataResult.error(() -> "Expected a 32-bit integer"), Integer::doubleValue);
    public static Codec<Double> number(double min, double max) {
        return Codec.DOUBLE.validate(n -> Double.isFinite(n) && n >= min && n <= max
                ? DataResult.success(n) : DataResult.error(() -> "Expected finite number in " + min + ".." + max));
    }
    public static final Codec<String> ID = Codec.STRING.validate(s -> s.matches("[a-z0-9_.-]{1,64}")
            ? DataResult.success(s) : DataResult.error(() -> "Invalid mining entry ID: " + s));
    public record Output(String id, ItemStack stack, double chance, boolean stackRoll) {
        public Output { stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
        public Output withChance(double chance) { return new Output(id, stack, Math.clamp(chance, 0, 1), stackRoll); }
        public ItemStack roll(RandomSource random) {
            if (chance <= 0) return ItemStack.EMPTY;
            if (chance >= 1) return stack();
            if (stackRoll) return random.nextFloat() < chance ? stack() : ItemStack.EMPTY;
            int n = 0;
            for (int i = 0; i < stack.getCount(); i++) if (random.nextFloat() < chance) n++;
            return stack.copyWithCount(n);
        }
    }
    public static final Codec<Output> OUTPUT = RecordCodecBuilder.<Output>create(i -> i.group(
            ID.optionalFieldOf("result_id", "").forGetter(Output::id),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(o -> o.stack.getItem()),
            INTEGER.optionalFieldOf("count", 1).forGetter(o -> o.stack.getCount()),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(o -> o.stack.getComponentsPatch()),
            number(0, 1).optionalFieldOf("chance", 1d).forGetter(Output::chance),
            Codec.STRING.validate(s -> Set.of("stack", "per_item").contains(s) ? DataResult.success(s)
                    : DataResult.error(() -> "roll_mode must be stack or per_item"))
                    .optionalFieldOf("roll_mode", "per_item").forGetter(o -> o.stackRoll ? "stack" : "per_item")
    ).apply(i, (id, item, count, patch, chance, mode) -> {
        ItemStack stack = new ItemStack(item, count); stack.applyComponents(patch);
        return new Output(id, stack, chance, mode.equals("stack"));
    })).validate(o -> !o.stack.isEmpty() && o.stack.getCount() <= o.stack.getMaxStackSize()
            ? DataResult.success(o) : DataResult.error(() -> "Output count must be 1..item stack limit"));
    public record ChanceAdjustment(String result, double multiply, double add) {
        static final Codec<ChanceAdjustment> CODEC = RecordCodecBuilder.create(i -> i.group(
                ID.fieldOf("result").forGetter(ChanceAdjustment::result),
                number(0, 1e6).optionalFieldOf("multiply", 1d).forGetter(ChanceAdjustment::multiply),
                number(-1, 1).optionalFieldOf("add", 0d).forGetter(ChanceAdjustment::add)
        ).apply(i, ChanceAdjustment::new));
    }
    public record Effects(double time, double stress, double capacityAdd, List<ChanceAdjustment> chances, List<Output> additional) {
        static final Codec<Effects> CODEC = RecordCodecBuilder.create(i -> i.group(
                number(1e-6, 1e6).optionalFieldOf("time_multiplier", 1d).forGetter(Effects::time),
                number(0, 1e6).optionalFieldOf("stress_multiplier", 1d).forGetter(Effects::stress),
                number(-1e6, 1e6).optionalFieldOf("capacity_cost_add", 0d).forGetter(Effects::capacityAdd),
                ChanceAdjustment.CODEC.listOf(0, 9).optionalFieldOf("chance_adjustments", List.of()).forGetter(Effects::chances),
                OUTPUT.listOf(0, 9).optionalFieldOf("additional_results", List.of()).forGetter(Effects::additional)
        ).apply(i, Effects::new));
    }
    public record ItemInput(Ingredient ingredient, int count) {
        static final Codec<ItemInput> CODEC = RecordCodecBuilder.<ItemInput>create(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(ItemInput::ingredient),
                INTEGER.fieldOf("count").forGetter(ItemInput::count)
        ).apply(i, ItemInput::new)).validate(v -> v.count > 0 && v.count <= 64 ? DataResult.success(v)
                : DataResult.error(() -> "Explosive count must be 1..64"));
    }
    private static final Codec<SizedFluidIngredient> FLUID_INPUT = RecordCodecBuilder.<SizedFluidIngredient>create(i -> i.group(
            net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_INGREDIENT_TYPES.byNameCodec().fieldOf("type").forGetter(f -> f.ingredient().getType()),
            net.neoforged.neoforge.fluids.crafting.FluidIngredient.MAP_CODEC_NONEMPTY.forGetter(SizedFluidIngredient::ingredient),
            INTEGER.fieldOf("amount").forGetter(SizedFluidIngredient::amount)
    ).apply(i, (type, ingredient, amount) -> new SizedFluidIngredient(ingredient, amount))).validate(f -> f.amount() >= 1 && f.amount() <= 1000
            ? DataResult.success(f) : DataResult.error(() -> "Lubricant amount must be 1..1000"));
    public record Modifier(String id, int priority, SizedFluidIngredient fluid, ItemInput item, Effects effects) {
        public boolean accepts(FluidStack stack) { return fluid != null && fluid.ingredient().test(stack); }
        public boolean accepts(ItemStack stack) { return item != null && item.ingredient.test(stack); }
        public boolean available(FluidStack f, ItemStack s) {
            return fluid != null ? fluid.test(f) : accepts(s) && s.getCount() >= item.count;
        }
        public Ingredient ingredient() { return item.ingredient; }
        public int count() { return item.count; }
        static final Codec<Modifier> FLUID = RecordCodecBuilder.create(i -> i.group(
                ID.fieldOf("id").forGetter(Modifier::id), INTEGER.optionalFieldOf("priority", 0).forGetter(Modifier::priority),
                FLUID_INPUT
                        .fieldOf("input").forGetter(Modifier::fluid), Effects.CODEC.fieldOf("effects").forGetter(Modifier::effects)
        ).apply(i, (id, priority, fluid, effects) -> new Modifier(id, priority, fluid, null, effects)));
        static final Codec<Modifier> ITEM = RecordCodecBuilder.create(i -> i.group(
                ID.fieldOf("id").forGetter(Modifier::id), INTEGER.optionalFieldOf("priority", 0).forGetter(Modifier::priority),
                ItemInput.CODEC.fieldOf("input").forGetter(Modifier::item), Effects.CODEC.fieldOf("effects").forGetter(Modifier::effects)
        ).apply(i, (id, priority, item, effects) -> new Modifier(id, priority, null, item, effects)));
    }
    public record Choices(List<Modifier> lubricants, List<Modifier> explosives) {
        public static final Choices EMPTY = new Choices(List.of(), List.of());
        public static final Codec<Choices> CODEC = RecordCodecBuilder.create(i -> i.group(
                ConditionalOps.decodeListWithElementConditions(Modifier.FLUID)
                        .validate(list -> list.size() <= 32 ? DataResult.success(list)
                                : DataResult.error(() -> "At most 32 active lubricants are supported"))
                        .optionalFieldOf("lubricants", List.of()).forGetter(Choices::lubricants),
                Modifier.ITEM.listOf(0, 32).optionalFieldOf("explosives", List.of()).forGetter(Choices::explosives)
        ).apply(i, Choices::new));
    }
    public record Operation(Modifier lubricant, Modifier explosive, double duration, float stress, double capacityCost, List<Output> outputs) {
        public String lubricantId() { return lubricant == null ? "" : lubricant.id; }
        public String explosiveId() { return explosive == null ? "" : explosive.id; }
        public List<ItemStack> roll(RandomSource random) { return outputs.stream().map(o -> o.roll(random)).filter(s -> !s.isEmpty()).toList(); }
    }
    private final List<Output> baseOutputs;
    private final Choices choices;
    private final Map<String, Operation> operations = new HashMap<>();
    public MiningModifiers(List<Output> base, Choices choices, int duration, double stress) {
        List<Output> normalized = new ArrayList<>();
        for (int index = 0; index < base.size(); index++) {
            Output o = base.get(index); normalized.add(o.id.isEmpty() ? new Output("result_" + index, o.stack, o.chance, o.stackRoll) : o);
        }
        base = List.copyOf(normalized);
        this.baseOutputs = base; this.choices = choices;
        Set<String> ids = new HashSet<>();
        for (Output o : base) if (!ids.add(o.id)) throw new IllegalArgumentException("Duplicate result_id: " + o.id);
        for (List<Modifier> list : List.of(choices.lubricants, choices.explosives)) {
            Set<String> seen = new HashSet<>();
            for (Modifier m : list) {
                if (!seen.add(m.id)) throw new IllegalArgumentException("Duplicate modifier ID: " + m.id);
                Set<String> adjusted = new HashSet<>();
                for (var c : m.effects.chances) if (!ids.contains(c.result) || !adjusted.add(c.result))
                    throw new IllegalArgumentException("Unknown or repeated result adjustment: " + c.result);
            }
        }
        List<Modifier> fluids = new ArrayList<>(choices.lubricants); fluids.addFirst(null);
        List<Modifier> items = new ArrayList<>(choices.explosives); items.addFirst(null);
        for (Modifier l : fluids) for (Modifier e : items) {
            List<Modifier> selected = new ArrayList<>(); if (l != null) selected.add(l); if (e != null) selected.add(e);
            double time = duration, impact = stress, cost = 1;
            for (Modifier m : selected) { time *= m.effects.time; impact *= m.effects.stress; cost += m.effects.capacityAdd; }
            check(time, 1, 72e6); check(impact, 0, 16384); check(cost, 1e-6, 1e6);
            List<Output> outputs = new ArrayList<>();
            for (Output output : base) {
                double multiplier = 1, add = 0;
                for (Modifier m : selected) for (var c : m.effects.chances) if (c.result.equals(output.id)) { multiplier *= c.multiply; add += c.add; }
                outputs.add(output.withChance(output.chance * multiplier + add));
            }
            selected.forEach(m -> outputs.addAll(m.effects.additional));
            if (outputs.size() > 9) throw new IllegalArgumentException("Combined output count exceeds 9");
            Operation op = new Operation(l, e, time, (float) impact, cost, List.copyOf(outputs));
            operations.put(key(op.lubricantId(), op.explosiveId()), op);
        }
    }
    private static void check(double n, double min, double max) {
        if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException("Invalid combined mining value " + n + " (" + min + ".." + max + ")");
    }
    private static String key(String l, String e) { return l + "\n" + e; }
    public List<Modifier> lubricants() { return choices.lubricants; }
    public List<Modifier> explosives() { return choices.explosives; }
    public List<Output> baseOutputs() { return baseOutputs; }
    public Operation base() { return operation("", ""); }
    public Operation operation(String l, String e) { return operations.get(key(l, e)); }
    private static String select(List<Modifier> list, FluidStack f, ItemStack s) {
        return list.stream().filter(m -> m.available(f, s)).sorted(Comparator.comparingInt(Modifier::priority).reversed().thenComparing(Modifier::id))
                .map(Modifier::id).findFirst().orElse("");
    }
    public Operation select(FluidStack f, ItemStack s) { return operation(select(lubricants(), f, s), select(explosives(), f, s)); }
}
