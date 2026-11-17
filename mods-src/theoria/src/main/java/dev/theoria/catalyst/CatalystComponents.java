package dev.theoria.catalyst;

import com.mojang.serialization.Codec;
import dev.theoria.core.TheoriaRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Registered pieces of the catalyst system that are neither items nor blocks: data components and the custom combat attributes (ADR-006 Karar 3, 5). */
public final class CatalystComponents {
    /** The catalysts an item carries (ADR-006 Karar 2). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Catalysts>> CATALYSTS = TheoriaRegistries.DATA_COMPONENTS.registerComponentType(
            "catalysts", b -> b.persistent(Catalysts.CODEC).networkSynchronized(Catalysts.STREAM_CODEC));
    /** On a catalyst ITEM: its level I..V. Different levels never stack because the component differs. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LEVEL = TheoriaRegistries.DATA_COMPONENTS.registerComponentType(
            "catalyst_level", b -> b.persistent(Codec.intRange(1, 64)).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** On a graded catalyst item (Menzil Gücü): the tier of the ingot it was made from. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GRADE = TheoriaRegistries.DATA_COMPONENTS.registerComponentType(
            "catalyst_grade", b -> b.persistent(Codec.intRange(0, 64)).networkSynchronized(ByteBufCodecs.VAR_INT));

    // Custom attributes (ADR-006 Karar 5): the Status screen of Phase 10 adds to the same ones, one pipeline. Soft caps are applied by the consumers (agg_caps), not here.
    public static final DeferredHolder<Attribute, Attribute> CRIT_CHANCE = attribute("crit_chance");
    public static final DeferredHolder<Attribute, Attribute> CRIT_DAMAGE = attribute("crit_damage");
    public static final DeferredHolder<Attribute, Attribute> LIFE_STEAL = attribute("life_steal");
    public static final DeferredHolder<Attribute, Attribute> THORNS = attribute("thorns");
    public static final DeferredHolder<Attribute, Attribute> FOOD_SATURATION = attribute("food_saturation");

    private CatalystComponents() {}

    private static DeferredHolder<Attribute, Attribute> attribute(String name) {
        return TheoriaRegistries.ATTRIBUTES.register(name, () -> new RangedAttribute("attribute.name.theoria." + name, 0.0, 0.0, 1024.0).setSyncable(true));
    }

    /** Touch to load the class (and so register the fields) from the mod constructor. */
    static void init() {}
}
