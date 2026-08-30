package dev.foucaultleon.flterraforged.engine.erosion;

final class ErosionTile {

    private final int originX;
    private final int originZ;
    private final int width;
    private final double[] baseHeights;
    private final double[] heights;
    private final double[] erosion;
    private final double[] deposition;

    ErosionTile(
            int originX,
            int originZ,
            int width,
            double[] baseHeights,
            double[] heights,
            double[] erosion,
            double[] deposition) {
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.baseHeights = baseHeights;
        this.heights = heights;
        this.erosion = erosion;
        this.deposition = deposition;
    }

    ErosionSample sample(int x, int z, double maxDelta) {
        int localX = x - originX;
        int localZ = z - originZ;
        int index = localZ * width + localX;
        double gradientX = (heights[index + 1] - heights[index - 1]) * 0.5D;
        double gradientZ = (heights[index + width] - heights[index - width]) * 0.5D;
        double normalizedErosion = Math.min(1.0D, erosion[index] / Math.max(1.0E-9D, maxDelta));
        return new ErosionSample(
                baseHeights[index],
                heights[index],
                Math.hypot(gradientX, gradientZ),
                normalizedErosion,
                Math.max(0.0D, deposition[index]));
    }
}
