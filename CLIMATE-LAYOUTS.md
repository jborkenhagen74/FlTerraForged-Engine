# Climate layouts

The Engine separates **terrain preset** from **macro-climate layout**. A preset controls numerical terrain/climate character; the layout controls how the large-scale climate baseline is arranged.

## `randomized` (default)

Seeded climate regions occur in all directions and are smoothly blended. This is the default for every preset, including `central_europe`. It is intended for normal Minecraft-style exploration while retaining climate continuity.

```text
preset=central_europe
climateLayout=randomized
```

## `north_south`

Adds a configurable latitude-like baseline along world Z while preserving regional noise and blending. It is an **option**, not a separate Engine implementation and not forced by the Central Europe preset.

```text
preset=central_europe
climateLayout=north_south
climateNorthSouthCenterZ=0
climateNorthSouthSpan=24000
climateNorthSouthStrength=0.78
climateNorthTemperature=0.18
climateSouthTemperature=0.80
climateNorthMoisture=0.60
climateSouthMoisture=0.36
```

Negative Z approaches the northern anchors; positive Z approaches the southern anchors. Noise, altitude, continentality, coast moderation and river moisture continue to modify the baseline, so the result is not a set of straight biome stripes.

## Central Europe terrain distribution

`central_europe` uses weighted terrain regions before subsequent blending/erosion/hydrology:

- plains: 0.22
- hills: 0.32
- valleys: 0.20
- plateaus: 0.16
- mountains: 0.10

These are selection weights, **not promised final surface percentages**. Coast/ocean/river/lake classification and blending change the final distribution. All five weights can be overridden with the corresponding `terrain*Weight` Engine configuration keys.
