package dev.theoria.gear;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global loot modifier: drops every generated stack that carries an item of {@code items} (an item tag). D6: vanilla netherite gear and its
 * upgrade template are removed from every loot source (bastion/ruined-portal chests, other mods' tables) — netherite itself stays as the universal catalyst.
 * Data: {@code data/theoria/loot_modifiers/remove_netherite_gear.json}; tag {@code theoria:removed_gear} (generated).
 */
public final class RemoveItemsLootModifier extends LootModifier {
    public static final MapCodec<RemoveItemsLootModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i)
            .and(TagKey.hashedCodec(Registries.ITEM).fieldOf("items").forGetter(m -> m.items))
            .apply(i, RemoveItemsLootModifier::new));

    private final TagKey<Item> items;

    public RemoveItemsLootModifier(Optional<Holder<LootItemCondition>> condition, int priority, TagKey<Item> items) {
        super(condition, priority);
        this.items = items;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        generatedLoot.removeIf(stack -> stack.is(items));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
