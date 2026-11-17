# Family LAN patches

This tree is [GeyserMC/Geyser](https://github.com/GeyserMC/Geyser) plus the patches used on the family Paper 26.3 LAN world.

## What changed

- **PS5 splitscreen** — second controller joins as its own Floodgate player (`SplitscreenServerSession`, `SubClientPacketHandler`, `SplitscreenEntities`, lenient sub-client login).
- **Bedrock guest mode** — optional guest login path (`BedrockGuest`).
- **Java hand order** — main hand first, then offhand, including eating while looking at a block (`JavaHandUse`, inventory / interact translators).
- **Offhand torch delay** — short cooldown so torches do not double-place.
- **Offhand HUD signal** — tip-text payload for a companion resource pack that draws a Java-style offhand slot.
- **Unridden minecarts** — do not forward Java rail velocity to Bedrock for carts the player is not riding.
- **Anvil display** — Bedrock anvil cost shown under the console refuse threshold.

## Build

```bash
./gradlew :spigot:shadowJar
```

Output: `bootstrap/spigot/build/libs/Geyser-Spigot.jar`

Needs JDK 24+ (this LAN host builds with JDK 24 locally / runs Paper on Java 25).

## Upstream

`upstream` should point at `https://github.com/GeyserMC/Geyser.git`. Rebase or merge from upstream when Paper/Geyser versions move.
