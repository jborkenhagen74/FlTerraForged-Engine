package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainClassificationSettings;
import java.util.Objects;

/**
 * Applies the single resolved water field after river shaping.
 *
 * <p>The wrapper no longer contains separate lake/ocean write paths. Instead
 * {@link ResolvedWaterResolver} evaluates every valid hydraulic candidate and returns one immutable
 * owner/bed/surface decision. This makes river mouths, lakes and marine receivers consume the same
 * final continuous state before climate projection or Minecraft materialization.</p>
 */
public final class ReceivingWaterOverlay implements CellLookup {

    private final CellLookup riverTerrain;
    private final ResolvedWaterResolver resolver;

    /**
     * Creates the final receiver-ownership stage.
     *
     * @param world immutable world context
     * @param receivers cached river-map provider containing immutable lake fields
     * @param riverTerrain fully shaped river/wet-core delegate that preserves {@code heightErosion}
     * @param classification coordinated final terrain-classification thresholds
     */
    public ReceivingWaterOverlay(
            EngineContext world,
            RiverModel receivers,
            CellLookup riverTerrain,
            TerrainClassificationSettings classification) {
        this.riverTerrain = Objects.requireNonNull(riverTerrain, "riverTerrain");
        this.resolver = new ResolvedWaterResolver(
                Objects.requireNonNull(world, "world"),
                Objects.requireNonNull(receivers, "receivers"),
                Objects.requireNonNull(classification, "classification"));
    }

    /** {@inheritDoc} */
    @Override
    public void lookup(int x, int z, Cell target) {
        Objects.requireNonNull(target, "target");
        riverTerrain.lookup(x, z, target);
        applyResolved(resolver.resolve(x, z, target), target);
    }

    private static void applyResolved(ResolvedWaterField resolved, Cell target) {
        target.height = resolved.bedHeight();
        target.riverMask = resolved.mask();

        switch (resolved.owner()) {
            case DRY -> {
                // The shaped delegate already owns dry-bank and shore metadata. Only the final bed
                // is authoritative here, so do not erase useful dry transition semantics.
            }
            case RIVER -> {
                target.lake = false;
                target.riverDistance = resolved.lateralDistance();
                target.riverWidth = resolved.width();
                target.riverDepth = resolved.depth();
                target.riverWaterSurfaceHeight = resolved.waterSurfaceHeight();
                target.riverFlow = resolved.flow();
            }
            case LAKE -> {
                target.lake = true;
                target.lakeShore = false;
                target.riverDistance = resolved.lateralDistance();
                target.riverWidth = resolved.width();
                target.riverDepth = resolved.depth();
                target.riverWaterSurfaceHeight = resolved.waterSurfaceHeight();
                target.riverFlow = 0.0D;
            }
            case OCEAN -> {
                target.lake = false;
                target.lakeShore = false;
                target.riverDistance = Double.POSITIVE_INFINITY;
                target.riverWidth = 0.0D;
                target.riverDepth = 0.0D;
                target.riverWaterSurfaceHeight = Double.NaN;
                target.riverFlow = Double.NaN;
            }
        }
    }
}
