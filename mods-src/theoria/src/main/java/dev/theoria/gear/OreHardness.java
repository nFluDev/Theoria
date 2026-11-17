package dev.theoria.gear;

import dev.theoria.Theoria;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * docs/05 §7: "cevherin sertliği kademeyle artar". Vanilla ore hardness is baked into every block state at construction
 * ({@code BlockStateBase.destroySpeed}, final) — the access transformer opens it and this writes the table's value into all states of
 * each gated vanilla block, on both sides, once, at common setup. Blocks with a null hardness (obsidian, ancient debris) keep vanilla's.
 * Our own ores get their hardness at registration and are skipped here (their states already carry it).
 */
final class OreHardness {
    private OreHardness() {}

    static int apply() {
        int blocks = 0;
        for (GearData.Gate gate : GearData.table().gates()) {
            for (GearData.GateBlock gb : gate.blocks()) {
                if (gb.hardness() == null || gb.id().startsWith("theoria:")) continue;
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(gb.id()));
                if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                    throw new IllegalStateException("gear.json gates vanilla block " + gb.id() + " but it does not exist");
                }
                float hardness = gb.hardness().floatValue();
                for (var state : block.getStateDefinition().getPossibleStates()) state.destroySpeed = hardness; // public non-final through accesstransformer.cfg
                blocks++;
            }
        }
        Theoria.LOGGER.info("Gear: ore hardness applied to {} vanilla blocks", blocks);
        return blocks;
    }
}
