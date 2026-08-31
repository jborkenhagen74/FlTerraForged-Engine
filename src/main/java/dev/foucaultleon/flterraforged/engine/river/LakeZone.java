package dev.foucaultleon.flterraforged.engine.river;

/** Internal semantic zone of a depression-filled inland-water basin. */
public enum LakeZone {
    /** Sample does not belong to an inland-water basin. */
    NONE,
    /** Dry or intermittently wet transition around a basin. */
    SHORE,
    /** Shallow water transition that should still materialize as water. */
    SHALLOW,
    /** Stable inner lake/pond body with guaranteed water depth. */
    CORE
}
