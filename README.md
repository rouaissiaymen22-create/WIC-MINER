# WIC MINER

Independent Android application built with Kotlin and Jetpack Compose.

## Prototype status — v0.1.0
- Black and gold interface with Home, Mining, Safety and Settings tabs.
- No fabricated hashrate, wallet balance or BTC rewards.
- No local Bitcoin proof-of-work or background mining service.
- No seed phrase collection and no network permission in this starter.
- External ASIC and mining-pool integrations are not connected yet.

## Build locally
- Android Studio with JDK 17
- Android SDK Platform 35
- Gradle 8.9
- Run: `gradle assembleDebug`

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Important
A phone cannot economically mine Bitcoin. Real BTC rewards require an actual ASIC/mining-pool contribution and independently verifiable pool records. This prototype is an app shell only; it does not mine and does not generate rewards.
