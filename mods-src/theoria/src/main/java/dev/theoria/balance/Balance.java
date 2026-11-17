package dev.theoria.balance;

/**
 * Phase 2 entry point: the balance core. Called once from the mod constructor, on both sides
 * (attribute ceilings must agree; the hooks themselves only ever fire on the server).
 */
public final class Balance {
    private Balance() {}

    public static void init() {
        TechnicalCaps.apply();
        TierRegistry.init();
        MobScaling.init();
        DamageHooks.init();
        TierCommand.init();
        BalanceChecks.init();
    }
}
