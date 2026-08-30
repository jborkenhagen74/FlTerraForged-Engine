package dev.foucaultleon.flterraforged.engine.erosion;

/**
 * Thermal erosion filter that redistributes material from slopes above a configurable talus angle.
 */
public final class ThermalErosionFilter implements ErosionFilter {

    /** Creates a stateless thermal erosion filter. */
    public ThermalErosionFilter() {
    }

    /** {@inheritDoc} */
    @Override
    public void apply(
            double[] heights,
            double[] erosion,
            double[] deposition,
            int width,
            ErosionSettings settings,
            int seaLevel) {
        if (settings.thermalStrength() <= 0.0D) {
            return;
        }
        double[] delta = new double[heights.length];
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        double talus = settings.talusSlope();

        for (int z = 1; z < width - 1; z++) {
            for (int x = 1; x < width - 1; x++) {
                int index = z * width + x;
                double center = heights[index];
                if (center <= seaLevel - 4.0D) {
                    continue;
                }
                double available = settings.maximumHeightChange();
                for (int n = 0; n < 4 && available > 0.0D; n++) {
                    int neighbor = (z + dz[n]) * width + x + dx[n];
                    double difference = center - heights[neighbor];
                    if (difference <= talus) {
                        continue;
                    }
                    double transfer = Math.min(
                            available,
                            (difference - talus) * settings.thermalStrength() * 0.25D);
                    if (transfer <= 0.0D) {
                        continue;
                    }
                    delta[index] -= transfer;
                    delta[neighbor] += transfer;
                    erosion[index] += transfer;
                    deposition[neighbor] += transfer;
                    available -= transfer;
                }
            }
        }

        for (int i = 0; i < heights.length; i++) {
            heights[i] += delta[i];
        }
    }
}
