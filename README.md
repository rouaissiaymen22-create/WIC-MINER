# WIC MINER

Android SHA-256 mining client prototype built with Kotlin and Jetpack Compose.

## Current version: 0.3.3

- Black-and-gold interface with Home, Miner, Pools, Safety and Settings tabs.
- Public Bitcoin reference data from mempool.space: BTC/USD, block height, recommended fee and observed pool blocks.
- Zpool SHA-256 Stratum V1 connection over TLS at `sha256.eu.mine.zpool.ca:53333`.
- TLS hostname verification, Stratum subscribe/authorize, difficulty and job notifications, extranonce updates, and share-result tracking.
- User-supplied public BTC payout address is stored locally in Android SharedPreferences. No seed phrase or private key is requested.
- User-started foreground-only SHA-256d sessions selectable for 1, 5 or 10 minutes; a share is submitted only if it meets the reported pool difficulty. Sessions can be stopped manually and automatically stop when Android reports battery temperature ≥43°C or battery level ≤15%.
- The UI reports pool-confirmed accepted/rejected shares separately from attempted hashes. A successful APK build does not prove that a pool connection or accepted share occurred.

## Zpool payout behavior

The Stratum username is the user's BTC payout address and the password is `c=BTC`. This requests BTC-denominated payout. Zpool is a multipool: it can direct SHA-256 work to compatible markets/coins and convert or pay proceeds in BTC. This setting does **not** mean the phone is necessarily mining Bitcoin blocks directly.

## Important limitations

- CPU mining on an Android phone is not economically competitive with SHA-256 ASIC miners and is not a realistic way to earn BTC.
- Mining runs only in manually started foreground sessions of up to 10 minutes. There is no background mining service. The temperature guard uses the battery temperature reported by Android; it is not a direct CPU/GPU temperature sensor.
- No profitability or payout is guaranteed. Shares may not be found during a short attempt.
- Pool-side acceptance must be confirmed from the live Stratum response; it cannot be inferred from local hashes.
- Thermal and battery load should be monitored. Stop immediately if the phone becomes unusually hot.
- Never enter a wallet seed phrase or private key.

## Build and test

- JDK 17
- Android SDK Platform 35
- Gradle 8.9
- Unit tests: `gradle --no-daemon :app:testDebugUnitTest`
- Debug APK: `gradle --no-daemon assembleDebug`
- APK output: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions runs unit tests before packaging the debug APK. The APK artifact is available from the successful workflow run.
