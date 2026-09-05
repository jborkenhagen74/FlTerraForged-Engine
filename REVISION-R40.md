# FlTerraForged Engine R40

R40 changes hydrology composition at river mouths instead of adding another Minecraft-side repair pass.

## Final stage order

`continent -> terrain -> erosion -> river -> wet-core continuity -> receiver overlay -> climate/classification`

The drainage topology and the surface actually incised by rivers now both use the same post-erosion terrain. Previously the rivermap generator used the pre-erosion base lookup while river incision used the post-erosion lookup.

## Receiving-water ownership

`ReceivingWaterOverlay` runs after river shaping and restores receiver-owned geometry from the already preserved `Cell.heightErosion` value. No second terrain/erosion sample is executed.

- Open ocean restores the natural post-erosion marine floor and clears river semantics.
- Material lakes re-apply their basin level and lake-owned bed after river shaping.
- A narrow wet river connector in the immediate lake-shore band may be promoted to the lake level only when the preserved terrain is physically below that level.
- River approach alignment may raise an over-incised mouth but may never deepen it further.
- Genuine waterfall approaches remain handled by the existing wet-core logic.

## Materializer boundary

The Engine still returns continuous geometry only. Minecraft block rounding, full-block water-cell reservation and variable-height provider geometry remain responsibilities of FlTerraForged R50 and its configured materializer.

There is no post-generation water refill or terrain reconstruction pass.
