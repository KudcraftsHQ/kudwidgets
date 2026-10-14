# Kud Widgets

Android home-screen widgets for the Kudcrafts dashboards (kudtrading and board): glance at today's
numbers, tap to open the dashboard. The phone sibling of trading-bar and board-bar.

Install and update with [Obtainium](https://github.com/ImranR98/Obtainium): add
`https://github.com/KudcraftsHQ/kudwidgets`.

Plan and design: [`docs/PLAN.md`](docs/PLAN.md).

## Building

JDK 21 and the Android SDK (platform 36, build-tools 36). On hammas-dev they live user-space:

```bash
export JAVA_HOME=~/.local/share/jdk/current ANDROID_HOME=~/.local/share/android-sdk
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

Releases: push a tag `v<major>.<minor>.<patch>`; `.github/workflows/release.yaml` builds, signs and publishes
`kudwidgets-<tag>.apk`.
