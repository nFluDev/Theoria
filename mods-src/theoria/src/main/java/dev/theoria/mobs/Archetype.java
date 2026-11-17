package dev.theoria.mobs;

import dev.theoria.mobs.archetype.AssassinMob;
import dev.theoria.mobs.archetype.CasterMob;
import dev.theoria.mobs.archetype.ChargerMob;
import dev.theoria.mobs.archetype.DrakeMob;
import dev.theoria.mobs.archetype.FlyerMob;
import dev.theoria.mobs.archetype.GiantMob;
import dev.theoria.mobs.archetype.HumanoidMob;
import dev.theoria.mobs.archetype.PredatorMob;
import dev.theoria.mobs.archetype.SentinelMob;
import dev.theoria.mobs.archetype.SerpentMob;
import dev.theoria.mobs.archetype.ShooterMob;
import dev.theoria.mobs.archetype.SwarmMob;
import java.util.Locale;
import net.minecraft.world.entity.EntityType;

/** The 12 archetypes (docs/05 §4.2 "Teknik"): one entity class each; a species only chooses parameters. */
public enum Archetype {
    HUMANOID(HumanoidMob::new), PREDATOR(PredatorMob::new), SWARM(SwarmMob::new), CHARGER(ChargerMob::new), SHOOTER(ShooterMob::new), CASTER(CasterMob::new),
    FLYER(FlyerMob::new), SERPENT(SerpentMob::new), GIANT(GiantMob::new), SENTINEL(SentinelMob::new), ASSASSIN(AssassinMob::new), DRAKE(DrakeMob::new);

    private final EntityType.EntityFactory<TheoriaMob> factory;

    Archetype(EntityType.EntityFactory<TheoriaMob> factory) {
        this.factory = factory;
    }

    public EntityType.EntityFactory<TheoriaMob> factory() {
        return factory;
    }

    public static Archetype byName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
