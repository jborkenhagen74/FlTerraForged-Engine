# FlTerraForged Engine R39

R39 builds on R38 and closes narrow receiver-continuity gaps at lake transitions without adding a post-generation repair stage.

## Receiver continuity

- Lake authority is no longer restricted to columns already classified as `lake_shore`.
- A guaranteed river wet-core column may inherit a nearby lake level when the nearest probe ring contains at least two material-lake samples.
- Probe distances are 1, 2 and 4 blocks for non-shore connectors; explicit lake shores may additionally probe at 8 blocks.
- Lake receiver probes use the cached lake-only field and do not recursively execute full river selection.
- A river merely running beside a lake is not flattened from a single neighboring lake sample.
- Existing terrain-backed waterfall preservation remains active.

## Materialization boundary

R39 remains Minecraft-agnostic and emits continuous hydraulic semantics only. Floating-point-to-block quantization is intentionally handled by FlTerraForged R49 and its active block provider.

## Performance

The new receiver checks are limited to already-wet guaranteed river cores and explicit lake shores. Normal land and dry banks do not perform lake-neighborhood scans.
