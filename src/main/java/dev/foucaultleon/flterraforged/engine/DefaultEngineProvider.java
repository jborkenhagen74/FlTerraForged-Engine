package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineApiVersion;
import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import dev.foucaultleon.flterraforged.engine.api.EngineId;
import dev.foucaultleon.flterraforged.engine.api.EngineProvider;
import dev.foucaultleon.flterraforged.engine.api.TerrainEngine;

/** ServiceLoader entry point for the default FlTerraForged terrain engine. */
public final class DefaultEngineProvider implements EngineProvider {

    /** Stable identifier of the default engine provider. */
    public static final EngineId ID = EngineId.of("flterraforged", "default");

    /** Version of this engine implementation. */
    public static final String VERSION = "0.1.0-SNAPSHOT-r28";

    /** Creates the ServiceLoader provider. */
    public DefaultEngineProvider() {
    }

    /** {@inheritDoc} */
    @Override
    public EngineId id() {
        return ID;
    }

    /** {@inheritDoc} */
    @Override
    public String displayName() {
        return "FlTerraForged Default Engine";
    }

    /** {@inheritDoc} */
    @Override
    public String engineVersion() {
        return VERSION;
    }

    /** {@inheritDoc} */
    @Override
    public EngineApiVersion apiVersion() {
        return EngineApiVersion.CURRENT;
    }

    /** {@inheritDoc} */
    @Override
    public TerrainEngine create(EngineConfig config) {
        return new DefaultTerrainEngine(EngineSettings.from(config));
    }
}
