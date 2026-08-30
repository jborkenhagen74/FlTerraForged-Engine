package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineApiVersion;
import dev.foucaultleon.flterraforged.engine.api.EngineCapabilities;
import dev.foucaultleon.flterraforged.engine.api.EngineCapability;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.TerrainEngine;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;

/** Immutable engine instance configured independently from a Minecraft world. */
public final class DefaultTerrainEngine implements TerrainEngine {

    private static final EngineCapabilities CAPABILITIES = EngineCapabilities.of(
            EngineCapability.FRACTIONAL_HEIGHT,
            EngineCapability.SLOPE,
            EngineCapability.EROSION,
            EngineCapability.CONTINENTALNESS,
            EngineCapability.CLIMATE,
            EngineCapability.RIVERS,
            EngineCapability.TERRAIN_TYPE);

    private final EngineSettings settings;

    public DefaultTerrainEngine(EngineSettings settings) {
        this.settings = settings;
    }

    @Override
    public EngineApiVersion apiVersion() {
        return EngineApiVersion.CURRENT;
    }

    @Override
    public EngineCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public TerrainWorld openWorld(EngineContext context) {
        return new DefaultTerrainWorld(context, settings);
    }
}
