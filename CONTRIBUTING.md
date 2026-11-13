# Contributing

Use Java 21 and `./gradlew clean build`. Keep Minecraft-specific code in the rotation adapter, preserve the fixed-memory/shared-scheduler design, and do not modify MagicSpells' JAR or intercept player input to manufacture synchronization.

For behavior changes, add meaningful tests, run the runtime checks relevant to the change, and update both `docs/en/` and `docs/ko/`. Explain which checks were automated, performed on a running server, or still require a human client. Describe reproduction steps, server/MagicSpells versions, other camera or anti-cheat plugins, latency, TPS, and the smallest relevant YAML fragment in bug reports.

Do not attach credentials, private player data, whole production server folders, or third-party JARs. Contributions are licensed under GPL-3.0-only.
