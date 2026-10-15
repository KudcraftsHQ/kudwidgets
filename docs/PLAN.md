# Kud Widgets — build plan

Android home-screen widgets for Hammas's own dashboards (kudtrading and board), the phone sibling of
trading-bar and board-bar on the Mac. Glance at a number, tap to open the dashboard. Released like
ntfy Kudcrafts: tag → signed APK on GitHub Releases → Obtainium.

This file is the contract between the threads building it. Keep it current; record what changed and why.

## Decisions (Hammas, 2026-10-14)

- Name **Kud Widgets**, app id `com.kudcrafts.widgets`, repo `KudcraftsHQ/kudwidgets`, **public**.
  (Repo renamed from `kud-widgets` to `kudwidgets` on 2026-10-14 at Hammas's request; the old URL redirects.
  The local checkout stays `~/Projects/kud-widgets`. The OAuth client id `kud-widgets` is a server contract and is unchanged.)
- Look follows his home screen: kudmascot night theme (see Design). No green highlight pills anywhere.
- Trading ships as **two widgets**: merged total, and split per business.
- **Sign-in is OAuth for both sources.** No pasted keys, ever.
- Refresh every 15 minutes (WorkManager) plus refresh on tap / app open, plus **live updates**: a foreground
  service (`live/LiveService.kt`, type `specialUse`) holds the sources' event streams open — kudtrading
  `/api/webhooks/stream` and `/api/ads/stream`, board `/api/p/<product>/admin/events` — and refreshes a source
  within seconds of an event that moves its numbers (at most one refresh per source per 20s). Its ongoing
  notification shows today's numbers (hidden on the lock screen). On by default, switchable in the app; needs the
  battery-optimisation exemption to survive Doze. (Chosen over the ntfy push nudge on 2026-10-15.)

## Sources to read first

| What | Where |
|---|---|
| Release pattern to copy | `~/Projects/ntfy-android/.github/workflows/kc-release.yaml`, signing block in `app/build.gradle` (flavor `fdroid`) |
| Another Obtainium-released app of ours (Compose, minSdk 26) | `~/Projects/kudmascot/android`, `.github/workflows/release.yml` |
| trading-bar (data, counting rules, formatting `695K`, popover) | `~/.t3/worktrees/kudtrading/feat-trading-bar/bar/` (merged as kudtrading PR #626), endpoint `src/app/api/menubar/overview/route.ts`, shape `src/lib/menubar/core/overview.ts` |
| board-bar (data, device sign-in, funnel, OpenAI balance) | `~/Projects/board` `origin/main`: `bar/README.md`, `bar/Sources/BoardBar/*.swift`, `server/routes/device.ts`, `server/routes/balances.ts` |
| Design references (local only, git-ignored: they show private apps) | `docs/reference/`: `approved-trading-look.jpg` is the Trading look he approved; `home-screen-*.jpg` his launcher; `kudmascot-night-moon.png` the wallpaper |

## Design

One fixed night theme. It ignores the system light/dark setting on purpose.

| Token | Value | Use |
|---|---|---|
| surface | `#121013`, corner radius ≈ 22dp, opaque | every widget (matches his prayer-times widget) |
| cream | `#FEF8E8` (kudmascot) | all text, curves, fills; never pure white |
| dim text | cream at ~55–60% | labels, timestamps |
| up | `#79D3A0` | only the small "▲ 12% vs yesterday" line |
| down / warning | `#F0A58C` text, `#C8674B` fill, surface `#1D1413` | "▼ 4% vs yesterday"; OpenAI credit when low |

- **Header row on every widget:** small cream mascot face (16dp) · bold source name · dim qualifier · right-aligned
  data time (`11:05`). The face is a small drawable in the kudmascot style.
- **Charts are the background**, drawn to a bitmap behind real text: area fill = cream gradient ~30% → 0%,
  no axes, no grid. Text owns the top-left; curves own the bottom and right. Chart ink ≤ ~⅓ opacity except the
  main line (cream, ~2.2dp) and the "now" dot (cream ring on surface, not green).
- **Numbers** in the trading-bar/board-bar short format (`5.4M`, `695K`), Asia/Jakarta days.
- **Grid:** his Nova launcher is 5 columns. Sizes below are in cells.

### Widgets

| Widget | Size | Layout | Tap |
|---|---|---|---|
| Trading · total | 5×2 | Header "Trading · today". Big total revenue, under it "▲ n% vs yesterday" (vs yesterday's running total at the same time). Background: today's running revenue curve to "now" with fill, vertical hairline + dot at now, yesterday's full-day running curve dashed (~40% cream) to the right edge, tiny "yesterday" label. Bottom-right: Orders · ROAS · Contrib as plain label-over-value columns | trading overview |
| Trading · by business | 5×3 | Header "Trading · today · total 5.4M". One band per business, separated by a hairline: letter chip (first letter, like trading-bar) + revenue, delta line, its own curve + dashed yesterday + now dot, bottom-right Orders · ROAS · Contrib. Two businesses fit; more → show top two + "+n" | the band's business dashboard |
| Board · today | 5×1 | Header "Board · today · F 620K · T 75K" (per product, first letter). Big IDR revenue left, "Contrib 240K" right (plain, no pill). Faint hourly revenue area between them | board |
| Board · funnel | 3×1 | Leads · Selfie · Created · Paid columns over a faint band that narrows left to right | board funnel |
| OpenAI credit | 2×1 | "$41" + "low · tap to top up" when under threshold. Liquid-like fill at the bottom showing how much is left; the whole widget turns the warning surface when low or the collector is stale | top-up link from board |

Approved mock of all of the above: see `docs/reference/approved-trading-look.jpg` (has green pills that were
then removed) and the decisions above.

**States every widget must draw:** loading (first run), fresh, stale (dim the numbers, show age "38m ago"),
signed out / token refused ("Sign in", tap opens the app's sign-in), source error (last numbers + age).

**Resizing:** when made smaller, drop the stats first, keep the number. **Per-widget options** (configure
activity / reconfigure on long-press): period (today, yesterday, 7 days, month), business or product, tap URL.

**The app itself** is the gallery: each widget rendered live with real data and a "pin to home screen"
button; a Sources screen with sign-in state (who, until when, sign out); a last-refresh log.

## Data

| Source | Request | Notes |
|---|---|---|
| kudtrading | `GET https://trading.kudcrafts.com/api/menubar/overview?preset=today\|yesterday\|last7\|this_month`, `Authorization: Bearer <key>` | Businesses + combined, KPIs + hourly/daily series. Yesterday's curve = a second read with `preset=yesterday`. Ad spend already includes PPN; do no math the server doesn't |
| board | `GET https://board.kudcrafts.com/api/board/overview?lite=1&preset=…`, `Authorization: Bearer bct_…` | Same as board-bar. Funnel and OpenAI balance as board-bar reads them |

All counting rules stay on the servers, so phone and Mac bars agree.

## Sign-in (OAuth)

**board — works today, no server change.** RFC 8628 device flow, exactly as board-bar:
`POST /api/board/device/code` with `client_id=board-cli` and `device_name="Kud Widgets · <phone model>"`,
open the verification URL (with the user code) in a Custom Tab, poll `POST /api/board/device/token` with
`grant_type=urn:ietf:params:oauth:grant-type:device_code`. Ask for **read** scope when approving. Token
`bct_…`, one year, revocable in board → Settings → CLI devices.

**kudtrading — needs the kudtrading PR (thread "kudtrading: Kud Widgets sign-in").** The app uses the
existing `kudtrading login` flow (authorization code + PKCE S256, `src/lib/cli-auth.ts`,
`src/routes/cli/authorize.tsx`, `POST /api/cli/auth/token`), with these additions:

| Contract | Value |
|---|---|
| Client | `kud-widgets` (sent as `client_id` / however the CLI flow names its client; the PR documents the exact parameter) |
| Redirect URI | exactly `com.kudcrafts.widgets:/oauth/callback` (custom scheme, RFC 8252), allowed only for that client |
| Key lifetime | 365 days for this client (CLI keeps 30) |
| Key power | **read-only**: the API auth chokepoint refuses any non-GET/HEAD request made with it (403) |
| Key name | "Kud Widgets · <device name>", visible and revocable in Admin → API Keys |

Until that PR is merged and deployed, build everything else; keep the trading sign-in behind the contract
above and test it against the PR's branch logic (unit tests), not production.

Tokens are stored encrypted (Android Keystore). A 401/403 flips the widget to "Sign in".

## Release

Copy `kc-release.yaml` (as `.github/workflows/release.yaml`):

- PRs and manual runs: unit tests + debug APK artifact.
- Tags `v<major>.<minor>.<patch>`: `versionName` from the tag, `versionCode = major*10000 + minor*100 + patch`;
  signed with `KC_KEYSTORE_BASE64`, `KC_KEYSTORE_PASSWORD`, `KC_KEY_ALIAS`, `KC_KEY_PASSWORD`; refuse to publish
  unsigned; `apksigner verify`; upload `kudwidgets-<tag>.apk` + `SHA256SUMS` to the GitHub Release.
- Tags containing `-` are prereleases (marked pre-release; Obtainium skips them).
- Same signing key as ntfy Kudcrafts (the later push nudge relies on a signature permission between the two
  apps). Keystore: `~/Projects/secrets/ntfy-kudcrafts/release.jks`, store password in `password.txt` there,
  key password = store password; find the alias with `keytool -list`. Set the four repo secrets with
  `gh secret set`. Never commit or print any of it.
- Obtainium: add `https://github.com/KudcraftsHQ/kudwidgets`.

## Build order

| Phase | Ships | Done when |
|---|---|---|
| 1 | Skeleton, one placeholder widget, release workflow, secrets | `v0.0.1` release has a signed APK that installs |
| 2 | Trading: OAuth (behind the contract), both Trading widgets, 15-min refresh, tap to open | Numbers match trading-bar |
| 3 | Board: device sign-in, Board · today, Funnel, OpenAI credit | Numbers match board-bar |
| 4 | Gallery app, per-widget options, all states, polish | Every state has a screenshot (`docs/screenshots/`) |
| later | Push nudge via ntfy Kudcrafts | not now |

Stack: Kotlin, Jetpack Glance, WorkManager, OkHttp, kotlinx.serialization, DataStore, AndroidX Browser
(Custom Tabs), minSdk 26, target/compile 36.

## As built (deviations and specifics, with why)

- **Release workflow** is `.github/workflows/release.yaml` (phase 1). Tags `v*`; PRs also upload the debug APK and
  the widget screenshots as artifacts.
- **"vs yesterday at the same time"** = today's running total against yesterday's running total at the fetch time,
  with yesterday's current hour counted pro rata (10:30 → hours 0–9 plus half of hour 10). Hour buckets are all the
  server gives; comparing to whole hours would swing the number at every :00. No line when yesterday had nothing by
  then. Computed at fetch time, not draw time, so a stale widget doesn't drift.
- **ROAS** on widgets is one decimal, truncated (`5.6`), like the mock; trading-bar's popover keeps `5,60×`.
- **Stale** = older than 30 minutes (two missed refreshes). **Source error** keeps the last numbers and shows their
  age in the header in the down colour. 401/403 flips to "Sign in" without touching the cached numbers.
- **Text over charts**: the chart bitmap erases (feathered, ~85%) its own ink under the headline block and the
  stats block, so no curve can run through a number. Bands in the by-business widget have no "yesterday" label
  (it crowded the stats); the total widget names the dashes.
- **Tap** goes through a no-UI trampoline that queues a refresh and then opens the dashboard
  (`/` for the total, `/<slug>/overview` for a band). Signed-out widgets open the app's sign-in.
- **Screenshots** come from Robolectric (native graphics) rendering the real Glance RemoteViews to PNG
  (`app/build/screenshots`); hammas-dev has no KVM, so no emulator. Fixtures use made-up businesses (the repo is
  public).
- **Trading sign-in** sends `client_id=kud-widgets` on both the authorize URL and the token exchange, plus
  `device_name` (manufacturer + model) on the authorize URL; the server names the key "Kud Widgets · <device>".
  The redirect lands on `MainActivity` (singleTask), which closes the Custom Tab. The PKCE verifier is kept
  encrypted across the round trip, since the process can die while the tab is open. Sign-out revokes the key on
  the server (`POST /api/cli/auth/revoke`, allowed for read-only keys) before forgetting it. Contract:
  KudcraftsHQ/trading-backoffice#630. 401 and 403 both flip a widget to "Sign in" (403 on a GET means the
  key can't see reports; `read_only_key` would be our bug, since the app never writes).

- **Board** reads one `GET /api/board/overview?lite=1&preset=today` per refresh; Board · today, Funnel and
  OpenAI credit all draw from that one cached read. Funnel = leads · selfies · created · paid summed over every
  product's hourly series (what board-bar's funnel chart sums). Revenue is IDR only, never summed with USD.
  A product that failed (`errorCode`) is left out of the "F 620K · T 75K" line.
- **OpenAI liquid level** = available ÷ `granted` (the collector reports total credit bought); without it the
  full mark is 4× `warnBelow`. Warm surface when `low`, `stale`, a collector `problem`, or the read itself is stale.
  Tap opens the balance's `topUpUrl`.
- **Board sign-in** shows the user code in the app while board's approval page is open in a Custom Tab, and polls
  per RFC 8628 (slow_down adds 5 s). Sign-out calls `POST /api/board/cli/logout` (revokes the token) first.
- **Board · today's area** draws finished hours only (the hour in progress is partial and would read as a dip) and
  stays below the header row. Funnel and Board taps open board's home: board has no funnel URL of its own yet.
- All whole-widget bitmaps clip to the 22dp corners themselves: not every launcher clips children to the outline.

- **Gallery** renders each widget with the same Glance content the launcher gets (composed to RemoteViews and
  inflated in the app, taps swallowed), at its home-screen size. Signed out, it shows each widget's "Sign in"
  state. The app sits on the wallpaper's night purple (`#231E26`) so widgets and cards read as on the home screen.
- **Per-widget options** (`ConfigureActivity`; on add below Android 12, otherwise optional, and on long-press ›
  reconfigure): period (today / yesterday / 7 days / month) on Trading · total, Trading · by business, Board · today
  and Funnel; business on Trading · total; product on Board · today and Funnel; tap URL (https only) on all.
  OpenAI credit has only the tap URL. Each period has its own cache; a refresh reads only the periods placed widgets
  use, plus today. Only today compares with yesterday. 7 days and month draw one slot per day across the whole
  window (the month's later days stay empty); an unknown filter (a business or product that went away) falls back
  to all, never to an empty widget.

- **Charts never stretch** (found on Hammas's phone, 2026-10-14: his launcher reports widgets ~55% of their real
  height, and it also picks Responsive layouts by that reported size, so neither Exact nor a height ladder can
  know the real height). Every chart is a band pinned to the widget's bottom, sized in dp from the width (which
  launchers report right), shown with a uniform centre-crop: a circle stays a circle in any real box. Text stays
  anchored top-left and stats bottom-right; what fits is decided by width, not height. The Trading time axis stops
  left of the stats column; Board · today's area lives between the number and Contrib (the hours so far spread
  across it); the "now" ring is drawn after any text erase so it is never dimmed. Band bitmaps clip their own
  bottom corners. `Screenshots.hisPhoneMisreportedSizes` renders every widget for the reported size inside the
  real box. Debug builds carry `DemoSeeder` (sample data, network paused) for on-device screenshots.

## Rules for whoever builds this

- hammas-dev had no JDK or Android SDK; they are now installed user-space (JDK 21 at `~/.local/share/jdk/current`,
  SDK at `~/.local/share/android-sdk`; see README › Building). Originally: install them user-space (JDK 21 + Android cmdline-tools under
  `~/.local/share/`), no sudo. Gradle is heavy on a 16 GB box: one build at a time, no parallel daemons.
- Scratch files go under `$TMPDIR`, never `/tmp`.
- Unit-test the parts that can be wrong: overview parsing, formatting, delta vs yesterday at the same hour,
  state transitions, PKCE, token expiry handling.
- PR descriptions say what changed and how it was checked. Attach widget screenshots where feasible.
- This repo is new and nothing depends on it, so phase 1 may commit to `main` and tag `v0.0.1` directly so
  Hammas can install it. From phase 2 on, work through PRs.
