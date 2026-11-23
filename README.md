# WIC MINER

Independent Android application built with Kotlin and Jetpack Compose.

## Current prototype — v0.2.0
- Black and gold interface with Home, Cloud, Pools, Safety and Settings tabs.
- Public Bitcoin reference data from the mempool.space REST API:
  - BTC/USD reference price
  - Latest block height
  - Recommended fastest transaction fee
  - Mining-pool block observations over the trailing 24 hours
- Manual refresh and visible update timestamp.
- No fabricated personal hashrate, wallet balance or BTC rewards.
- No local Bitcoin proof-of-work and no background mining service.
- No seed phrase collection.
- Cloud provider connections and personal pool-account integrations are not connected yet.

## Build
- JDK 17
- Android SDK Platform 35
- Gradle 8.9
- Run: `gradle --no-daemon assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## Data source
Public Bitcoin network and mining-pool observations are retrieved from the mempool.space REST API. These are network-wide public statistics; they do not represent this user's mining activity or earnings.

## Safety and limitations
A phone cannot economically mine Bitcoin. WIC MINER does not run mining workloads. Cloud-mining providers are third parties; no contracts are sold or endorsed in this app. Any future provider integration must use verifiable read-only data and clearly disclose fees, payout evidence, and withdrawal conditions. Never enter a wallet seed phrase or send funds to an unknown service.
