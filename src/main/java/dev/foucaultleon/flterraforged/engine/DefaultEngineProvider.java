package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineApiVersion;
import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import dev.foucaultleon.flterraforged.engine.api.EngineId;
import dev.foucaultleon.flterraforged.engine.api.EngineProvider;
import dev.foucaultleon.flterraforged.engine.api.TerrainEngine;

/** ServiceLoader entry point for the default FlTerraForged terrain engine. */
public final class DefaultEngineProvider implements EngineProvider {

    public static final EngineId ID = EngineId.of("flterraforged", "default");
    public static final String VERSION = "0.1.0-SNAPSHOT";

    @Override
    public EngineId id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "FlTerraForged Default Engine";
    }

    @Override
    public String engineVersion() {
        return VERSION;
    }

    @Override
    public EngineApiVersion apiVersion() {
        return EngineApiVersion.CURRENT;
    }

    @Override
    public TerrainEngine create(EngineConfig config) {
        return new DefaultTerrainEngine(EngineSettings.from(config));
    }
}
