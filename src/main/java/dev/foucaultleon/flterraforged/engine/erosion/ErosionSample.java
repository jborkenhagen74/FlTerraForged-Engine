package dev.foucaultleon.flterraforged.engine.erosion;

/**
 * Immutable result of the erosion stage at one horizontal world coordinate.
 *
 * @param baseHeight terrain height before physical erosion
 * @param erodedHeight terrain height after hydraulic and thermal erosion
 * @param gradient local gradient of the eroded terrain
 * @param erosion normalized erosion intensity in {@code [0, 1]}
 * @param sediment deposited sediment amount in blocks
 */
public record ErosionSample(
        double baseHeight,
        double erodedHeight,
        double gradient,
        double erosion,
        double sediment) {

    /**
     * Validates an erosion sample.
     *
     * @param baseHeight terrain height before physical erosion
     * @param erodedHeight terrain height after hydraulic and thermal erosion
     * @param gradient local gradient of the eroded terrain
     * @param erosion normalized erosion intensity in {@code [0, 1]}
     * @param sediment deposited sediment amount in blocks
     */
    public ErosionSample {
        if (!Double.isFinite(baseHeight)
                || !Double.isFinite(erodedHeight)
                || !Double.isFinite(gradient)
                || !Double.isFinite(erosion)
                || !Double.isFinite(sediment)) {
            throw new IllegalArgumentException("erosion sample values must be finite");
        }
        if (gradient < 0.0D || erosion < 0.0D || erosion > 1.0D || sediment < 0.0D) {
            throw new IllegalArgumentException("erosion sample contains an out-of-range value");
        }
    }

    /**
     * Returns the signed terrain-height change caused by erosion and deposition.
     *
     * @return eroded height minus base height
     */
    public double heightDelta() {
        return erodedHeight - baseHeight;
    }
}
