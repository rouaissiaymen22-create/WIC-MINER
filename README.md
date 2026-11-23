# WIC MINER

Independent Android Bitcoin mining project built with Kotlin and Jetpack Compose.

## Current prototype — v0.3.0
- Black and gold interface with Home, Miner, Pools, Safety and Settings tabs.
- Public Bitcoin reference data from mempool.space: BTC/USD, block height, recommended fee and observed pool blocks.
- Manual five-second local SHA-256d throughput test against a synthetic 80-byte header.
- Displays measured hashes and speed from the device; no fabricated personal hashrate or earnings.
- **Important:** the current local test is a benchmark only. It is not connected to Bitcoin, does not process a pool job, does not submit shares, and cannot earn BTC.
- No background mining service and no wallet seed phrase collection.

## Planned next stages
1. Implement and test Bitcoin Stratum job handling and block-header construction against Signet.
2. Verify share submission and pool-side acceptance on Signet.
3. Add explicit user-configured pool and payout address, thermal/battery safeguards, and manual start/stop.
4. Keep Bitcoin Mainnet disabled until Signet tests pass.

## Build
- JDK 17
- Android SDK Platform 35
- Gradle 8.9
- Run: `gradle --no-daemon assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## Safety and limitations
Bitcoin SHA-256 mining is economically dominated by specialized ASIC hardware. CPU/mobile-phone mining is not a realistic way to earn BTC. The app must show only measured device performance and pool-confirmed share data. Never enter a wallet seed phrase or private key into this app.
