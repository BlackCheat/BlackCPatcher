# Task Tracker

- [ ] Add `smali` and `baksmali` dependencies to `build.gradle.kts`
- [ ] Create `BinaryAxmlPatcher.kt`
- [ ] Rewrite `ManifestPatcher.kt` to use the binary patcher
- [ ] Create `DexSmaliEngine.kt`
- [ ] Update `SmaliHooker.kt` to simplify hooks and remove `.locals` shifting
- [ ] Update `ApkInjectorEngine.kt` to reorder manifest reading and call `DexSmaliEngine`
- [ ] Run Gradle tests and assembleDebug
- [ ] Create walkthrough artifact