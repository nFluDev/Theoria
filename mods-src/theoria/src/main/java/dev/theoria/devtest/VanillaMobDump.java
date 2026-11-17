package dev.theoria.devtest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * The only producer of data/vanilla_mobs.csv: dumps default attributes + XP of every living entity type to
 * build/dev-out/vanilla_mobs.csv; install with {@code python3 -I tools/check_vanilla_mobs.py --install}.
 * Re-run after every Minecraft version change.
 */
final class VanillaMobDump {
    private static final List<Holder<Attribute>> ATTRS = List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS, Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED, Attributes.FOLLOW_RANGE,
            Attributes.KNOCKBACK_RESISTANCE, Attributes.ATTACK_KNOCKBACK, Attributes.SCALE);

    private VanillaMobDump() {}

    @SuppressWarnings("unchecked")
    static void run(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        List<String> rows = new ArrayList<>();
        rows.add("id,category,class,max_health,attack_damage,armor,armor_toughness,movement_speed,flying_speed,follow_range,"
                + "knockback_resistance,attack_knockback,scale,xp_min,xp_max,width,height,fire_immune,note");
        List<String> body = new ArrayList<>();
        int count = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!DefaultAttributes.hasSupplier(type)) continue;
            AttributeSupplier sup = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
            StringBuilder sb = new StringBuilder();
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            String note = "";
            String xpMin = "", xpMax = "", cls = "";
            try {
                Entity e = type.create(level, EntitySpawnReason.COMMAND);
                if (e != null) {
                    cls = e.getClass().getSimpleName();
                    if (e instanceof LivingEntity le) {
                        int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
                        for (int i = 0; i < 64; i++) {
                            int xp = le.getExperienceReward(level, null);
                            mn = Math.min(mn, xp);
                            mx = Math.max(mx, xp);
                        }
                        xpMin = Integer.toString(mn);
                        xpMax = Integer.toString(mx);
                    }
                    e.discard();
                } else {
                    note = "not-creatable";
                }
            } catch (Throwable t) {
                note = "create-failed:" + t.getClass().getSimpleName();
            }
            sb.append(id).append(',').append(type.getCategory().getName()).append(',').append(cls);
            for (Holder<Attribute> a : ATTRS) {
                sb.append(',');
                if (sup.hasAttribute(a)) sb.append(fmt(sup.getBaseValue(a)));
            }
            sb.append(',').append(xpMin).append(',').append(xpMax)
              .append(',').append(fmt(type.getWidth())).append(',').append(fmt(type.getHeight()))
              .append(',').append(type.fireImmune()).append(',').append(note);
            body.add(sb.toString());
            count++;
        }
        body.sort(String::compareTo);
        rows.addAll(body);
        Dev.Report.write("vanilla_mobs.csv", rows);
        new Dev.Report("dump_vanilla_mobs").line("dumped %d living entity types with default attributes", count).write();
        h.assertTrue(count > 50, "expected > 50 living entity types");
        h.succeed();
    }

    static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return Long.toString((long) v);
        return String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "");
    }
}
