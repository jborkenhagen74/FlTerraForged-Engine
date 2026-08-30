package dev.foucaultleon.flterraforged.engine.erosion;

import dev.foucaultleon.flterraforged.engine.internal.Maths;

/**
 * Hydraulic erosion filter using deterministic virtual droplets over a padded height field.
 *
 * <p>Droplets follow the local bilinear height gradient, carry sediment while descending and
 * deposit material when their carrying capacity drops. The source height field is sampled before
 * the simulation and droplet contributions are accumulated independently, which keeps overlapping
 * erosion regions deterministic and avoids order-dependent shared world state.</p>
 */
public final class HydraulicErosionFilter implements ErosionFilter {

    private final long seed;
    private final int paddedOriginX;
    private final int paddedOriginZ;

    /**
     * Creates a hydraulic filter for one padded region.
     *
     * @param seed deterministic world/region seed
     * @param paddedOriginX padded region origin X
     * @param paddedOriginZ padded region origin Z
     */
    public HydraulicErosionFilter(long seed, int paddedOriginX, int paddedOriginZ) {
        this.seed = seed;
        this.paddedOriginX = paddedOriginX;
        this.paddedOriginZ = paddedOriginZ;
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
        double[] source = heights.clone();
        int minGlobalX = paddedOriginX + settings.erosionRadius();
        int minGlobalZ = paddedOriginZ + settings.erosionRadius();
        int spacing = settings.launchSpacing();
        int maxGlobalX = paddedOriginX + width - 3 - settings.erosionRadius() - (spacing - 1);
        int maxGlobalZ = paddedOriginZ + width - 3 - settings.erosionRadius() - (spacing - 1);
        int firstX = alignedCeil(minGlobalX, spacing);
        int firstZ = alignedCeil(minGlobalZ, spacing);

        for (int launchZ = firstZ; launchZ <= maxGlobalZ; launchZ += spacing) {
            for (int launchX = firstX; launchX <= maxGlobalX; launchX += spacing) {
                long hash = mix(seed, launchX, launchZ);
                double offsetX = unit(hash) * Math.max(1.0D, spacing - 1.0D);
                double offsetZ = unit(mix64(hash ^ 0x9E3779B97F4A7C15L)) * Math.max(1.0D, spacing - 1.0D);
                double localX = launchX - paddedOriginX + offsetX;
                double localZ = launchZ - paddedOriginZ + offsetZ;
                simulateDroplet(source, erosion, deposition, width, localX, localZ, settings, seaLevel);
            }
        }

        double maxDelta = settings.maximumHeightChange();
        for (int i = 0; i < heights.length; i++) {
            double landMask = landMask(source[i], seaLevel);
            double eroded = Math.min(maxDelta, erosion[i] * settings.hydraulicStrength() * landMask);
            double deposited = Math.min(maxDelta, deposition[i] * settings.depositionStrength() * landMask);
            heights[i] = source[i] - eroded + deposited;
            erosion[i] = eroded;
            deposition[i] = deposited;
        }
    }

    private static void simulateDroplet(
            double[] source,
            double[] erosion,
            double[] deposition,
            int width,
            double startX,
            double startZ,
            ErosionSettings settings,
            int seaLevel) {
        double x = startX;
        double z = startZ;
        double dirX = 0.0D;
        double dirZ = 0.0D;
        double speed = 1.0D;
        double water = 1.0D;
        double sediment = 0.0D;

        for (int life = 0; life < settings.maxDropletLifetime(); life++) {
            HeightGradient current = sample(source, width, x, z);
            dirX = dirX * settings.inertia() - current.gradientX() * (1.0D - settings.inertia());
            dirZ = dirZ * settings.inertia() - current.gradientZ() * (1.0D - settings.inertia());
            double length = Math.hypot(dirX, dirZ);
            if (length < 1.0E-10D) {
                break;
            }
            dirX /= length;
            dirZ /= length;

            double nextX = x + dirX;
            double nextZ = z + dirZ;
            if (nextX < 1.0D || nextZ < 1.0D || nextX >= width - 2.0D || nextZ >= width - 2.0D) {
                break;
            }

            HeightGradient next = sample(source, width, nextX, nextZ);
            double deltaHeight = next.height() - current.height();
            double capacity = Math.max(
                    -deltaHeight * speed * water * settings.sedimentCapacity(),
                    settings.minimumSedimentCapacity());

            if (deltaHeight > 0.0D || sediment > capacity) {
                double amount = deltaHeight > 0.0D
                        ? Math.min(deltaHeight, sediment)
                        : (sediment - capacity) * settings.depositionRate();
                if (amount > 0.0D) {
                    deposit(deposition, width, x, z, amount);
                    sediment -= amount;
                }
            } else if (deltaHeight < 0.0D && current.height() > seaLevel - 4.0D) {
                double amount = Math.min(
                        (capacity - sediment) * settings.erosionRate(),
                        -deltaHeight);
                if (amount > 0.0D) {
                    erode(erosion, width, x, z, amount, settings.erosionRadius());
                    sediment += amount;
                }
            }

            speed = Math.sqrt(Math.max(0.0D, speed * speed - deltaHeight * settings.gravity()));
            water *= 1.0D - settings.evaporationRate();
            x = nextX;
            z = nextZ;
            if (water < 0.02D) {
                break;
            }
        }

        if (sediment > 0.0D && x >= 1.0D && z >= 1.0D && x < width - 2.0D && z < width - 2.0D) {
            deposit(deposition, width, x, z, sediment * 0.35D);
        }
    }

    private static HeightGradient sample(double[] heights, int width, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        int index = z0 * width + x0;
        double h00 = heights[index];
        double h10 = heights[index + 1];
        double h01 = heights[index + width];
        double h11 = heights[index + width + 1];
        double top = h00 + (h10 - h00) * fx;
        double bottom = h01 + (h11 - h01) * fx;
        double height = top + (bottom - top) * fz;
        double gradientX = (h10 - h00) * (1.0D - fz) + (h11 - h01) * fz;
        double gradientZ = (h01 - h00) * (1.0D - fx) + (h11 - h10) * fx;
        return new HeightGradient(height, gradientX, gradientZ);
    }

    private static void erode(double[] target, int width, double x, double z, double amount, int radius) {
        int centerX = (int) Math.floor(x);
        int centerZ = (int) Math.floor(z);
        double totalWeight = 0.0D;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double distance = Math.hypot(dx, dz);
                if (distance <= radius) {
                    totalWeight += 1.0D - distance / (radius + 0.5D);
                }
            }
        }
        if (totalWeight <= 0.0D) {
            return;
        }
        for (int dz = -radius; dz <= radius; dz++) {
            int pz = centerZ + dz;
            if (pz < 0 || pz >= width) {
                continue;
            }
            for (int dx = -radius; dx <= radius; dx++) {
                int px = centerX + dx;
                if (px < 0 || px >= width) {
                    continue;
                }
                double distance = Math.hypot(dx, dz);
                if (distance > radius) {
                    continue;
                }
                double weight = (1.0D - distance / (radius + 0.5D)) / totalWeight;
                target[pz * width + px] += amount * weight;
            }
        }
    }

    private static void deposit(double[] target, int width, double x, double z, double amount) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        int index = z0 * width + x0;
        target[index] += amount * (1.0D - fx) * (1.0D - fz);
        target[index + 1] += amount * fx * (1.0D - fz);
        target[index + width] += amount * (1.0D - fx) * fz;
        target[index + width + 1] += amount * fx * fz;
    }

    private static double landMask(double height, int seaLevel) {
        return Maths.smooth((height - (seaLevel - 4.0D)) / 12.0D);
    }

    private static int alignedCeil(int value, int spacing) {
        int floor = Math.floorDiv(value, spacing) * spacing;
        return floor < value ? floor + spacing : floor;
    }

    private static long mix(long seed, int x, int z) {
        long value = seed;
        value ^= (long) x * 0x9E3779B185EBCA87L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        return mix64(value);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private record HeightGradient(double height, double gradientX, double gradientZ) {
    }
}
