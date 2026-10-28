package dev.gensomechanic.mining.client.jei;

import dev.gensomechanic.mining.content.miner.*;
import dev.gensomechanic.mining.client.ScannerClient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.*;

/** JEI pages are views of one server recipe, never additional RecipeManager entries. */
public record MiningDisplay(RecipeHolder<MiningRecipe> holder, MiningModifiers.Operation operation) {
    public record OutputView(int originalIndex, MiningModifiers.Output output) {}
    public MiningRecipe recipe() { return holder.value(); }
    /** Group matching item types without changing slots, probabilities or production order. */
    public List<OutputView> orderedOutputs() {
        Map<Item, List<OutputView>> groups = new LinkedHashMap<>();
        var outputs = operation.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            var output = outputs.get(i);
            groups.computeIfAbsent(output.stack().getItem(), item -> new ArrayList<>()).add(new OutputView(i, output));
        }
        return groups.values().stream().flatMap(List::stream).toList();
    }
    public static List<MiningDisplay> pages(RecipeHolder<MiningRecipe> holder) {
        List<MiningDisplay> pages = new ArrayList<>(); var m = holder.value().modifiers();
        pages.add(new MiningDisplay(holder, m.base()));
        m.lubricants().forEach(l -> pages.add(new MiningDisplay(holder, m.operation(l.id(), ""))));
        m.explosives().forEach(e -> pages.add(new MiningDisplay(holder, m.operation("", e.id()))));
        return List.copyOf(pages);
    }
    public MiningModifiers.Modifier modifier() { return operation.lubricant() == null ? operation.explosive() : operation.lubricant(); }
    public String kind() { return modifier() == null ? "base" : modifier().fluid() != null ? "lubricant" : "explosive"; }
    public Component veinName() { var entry = ScannerClient.miningEntry(recipe().veinId()); return entry == null ? Component.literal(recipe().veinId().toString()) : entry.name(); }
    public ResourceLocation veinIcon() { var entry = ScannerClient.miningEntry(recipe().veinId()); return entry == null ? ResourceLocation.withDefaultNamespace("compass") : entry.icon(); }
}
