# FlTerraForged Engine R38

R38 builds directly on R37 and keeps the waterfall-preserving wet-core model.

## Hydraulic receiver authority

- Open ocean is authoritative at a river mouth and resolves to the configured world sea level.
- Material lakes are authoritative over an incoming river when the river reaches the lake shore.
- A terrain-backed upstream head is retained as a waterfall instead of being flattened into the receiving body.
- Receiver checks run only on already-wet river samples or lake-shore candidates; normal land sampling performs no neighborhood scan.

## Provider boundary

The Engine continues to expose semantic continuous terrain only. Minecraft block states, partial blocks, waterlogging and variable block heights remain owned by the FlTerraForged materializer layer.

## Performance

R38 adds no chunk access, no Minecraft executor work and no global generation lock. Lake-neighbor probes are bounded and are only executed at lake-shore river candidates.
