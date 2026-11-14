# Nxygram implementation roadmap and feature checklist

This file is the tracking source for the staged Nxygram fork. Checklist entries are deliberately not marked complete until implementation and relevant verification are performed. The source remains an unofficial derivative of the Telegram Android client; retain upstream notices and the GPL-2.0 license.

## Repository audit (baseline: `master` at `e1a4bc3cfe0e537ff4bb6bf058e289ca2b7638b8`)

- [x] Confirm repository is a fork of `DrKLO/Telegram`; preserve upstream app functionality.
- [x] Identify Android code layout: `TMessagesProj` library and native CMake code, `TMessagesProj_App` application, other store/standalone app variants, `TMessagesProj_AppTests`, `TMessagesProj_Modules`, and `buildSrc` Gradle plugins.
- [x] Inspect root Gradle setup, app/library Gradle configuration, module settings, `BuildVars`, app manifests, settings fragments, `SharedConfig`, message/storage/controller locations, `TranslateController`, and instrumentation test module.
- [x] Verify configured base application ID: `APP_PACKAGE=com.nxygram.messenger` in root `gradle.properties`. Note: `TMessagesProj_App` applies `.beta` to debug and `.web` to standalone builds, so variant application IDs differ.
- [x] Identify existing translator: `TranslateController` performs language detection and Telegram server translation, persists translated text in message custom parameters, and supports per-dialog auto-translation. It is not equivalent to the requested independent, user-consented provider pipeline/outgoing-preview feature.
- [x] Identify existing app lock/passcode/biometric machinery in `SharedConfig`, `LaunchActivity`, and `PasscodeView`; avoid replacing those security-sensitive flows.
- [ ] Audit every variant's manifest/resources and verify all submodules are initialized before claiming a reproducible build.
- [ ] Run a baseline Windows build and instrumentation/unit tests; this GitHub editing environment does not provide an Android SDK/emulator, so local verification must be performed on the user's Windows workstation or CI.

## Build and configuration baseline

- Android Gradle Plugin: `8.13.2`; Gradle wrapper is checked in (verify exact distribution in `gradle/wrapper/gradle-wrapper.properties` locally).
- Android Studio per upstream README: `2025.1.4`; compile/target SDK 36; build tools 36.0.0; NDK `27.2.12479018`; CMake `3.22.1`; min SDK 23; Java source/target 8.
- Application and library native build target: `tmessages.49`; many native dependencies are git submodules listed in `.gitmodules`.
- API ID/hash are sourced from `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`; forks must obtain their own Telegram API credentials before a distributable service build. Do not copy upstream keys into a public fork.
- Firebase/Google services files exist in the source tree, but fork builds need project-specific Firebase configuration and application IDs for push services.
- Translation and optional AI providers need provider-specific credentials only if a cloud integration is later implemented. Do not store credentials in this settings class or commit them.
- Release signing configuration references `TMessagesProj/config/release.keystore`; replace placeholder credentials/keystore before production release. The repository's checked-in values are not production secrets.

## Phase 1 — Nxygram foundation

- [x] Add app-module label override for `Nxygram` / `Nxygram Beta` without rewriting upstream's large multilingual string resources.
- [x] Add isolated, schema-versioned `NxygramSettings` preference storage; feature toggles are independently keyed globally or per account and default off.
- [x] Add six initial palette data presets (Midnight, Ocean, Royal, Rose, Emerald, Pure) and preserve mandatory outgoing translation preview as an invariant.
- [ ] Connect Theme Studio selections to Telegram's theme/color system and build the editable, previewable theme UI.
- [ ] Integrate a reachable Nxygram feature control-center screen into existing settings navigation; add explanatory copy, reset, and persistence UI.
- [ ] Create and apply a distinct Nxygram adaptive/legacy launcher icon, splash treatment, and onboarding branding. Upstream explicitly says not to reuse Telegram's paper-plane logo.
- [ ] Replace visible product branding in owned app-facing strings while preserving protocol terminology, upstream notices, and third-party attribution; review legal naming requirements.
- [ ] Build/test app labels, icons, splash, onboarding, settings persistence, light/dark mode, and account switching.

## Phase 2 — Messaging improvements

- [ ] Smart Reply suggestions based on supported local/consented processing.
- [ ] Message scheduler and scheduled-message manager, using Telegram's send helpers and scheduler semantics.
- [ ] Draft Manager; preserve Telegram drafts and existing draft migration behavior.
- [ ] Pin Manager and actual pin/unpin operations.
- [ ] Quick Forward, bulk message actions, and Saved Message categories.
- [ ] Local message bookmarks/categories with persistence and cleanup.
- [ ] Advanced search, using existing search and database architecture.
- [ ] Local message statistics, chat export for eligible data, and cleanup tools.
- [ ] Custom chat tabs/organization without breaking server-synced folders.
- [ ] Exercise private/group/channel/bot messaging and scheduled send behaviors.

## Phase 3 — Privacy and security

- [ ] Ghost Reader and per-chat privacy controls only where Telegram protocol/server behavior permits; explain server-visible limits.
- [ ] Local received-message and edit-history capture only when a message/version is actually available locally; never promise recovery before receipt.
- [ ] Local retention controls, deletion/clear-history UI, and bounded storage.
- [ ] Reuse Telegram app lock, biometric, and lifecycle integrations where safe; add protected local chat-lock metadata and recovery.
- [ ] Anti-spam rules with explicit false-positive controls; do not silently discard messages.
- [ ] Privacy dashboard and disclosures for local retention and external providers.
- [ ] Never bypass view-once or screenshot protections.

## Phase 4 — Universal Translator

- [ ] Provider interface and per-account/per-chat language/preferences.
- [ ] Incoming per-message translation for all group members, private chats, channels, bots where permitted; preserve original server content.
- [ ] Language detection, captions, supported transcription, formatting/entity preservation, excluded chats/users, glossary, dictionary, and history controls.
- [ ] Outgoing translation with preview always shown before any message is sent; edit/restore original and explicit confirmation.
- [ ] Reply target language matching, one target language per group message, and configured fallback.
- [ ] External provider disclosure, informed consent, secure credentials, failure handling, and offline option where available.
- [ ] Tests for mixed-language groups, provider failures/offline, preview cancellation/edit/confirm, and persistence.

## Phase 5 — Media and customization

- [ ] Story Manager limited to content the user is permitted to retain; preserve access controls.
- [ ] Media queue/progress and settings integrated with `DownloadController` / `FileLoader` without destabilizing Telegram transfers.
- [ ] Storage analyzer and cache controls integrated with existing storage facilities.
- [ ] Custom emoji/reaction presentation only within Telegram supported capabilities.
- [ ] Profile Designer applies local presentation only; no server changes without explicit user action.
- [ ] Notification controls build on existing notification channels and per-dialog settings.
- [ ] Fonts, wallpaper, density, icon packs, and profile themes with reset and migration behavior.

## Phase 6 — AI, backup and completion

- [ ] Optional AI features, each disabled by default; no message content leaves the device without explicit, informed consent.
- [ ] Provider selection/credentials via secure Android storage, never app resources/logs/source.
- [ ] Conversation summarizer, writing tools, and voice transcription only after provider/privacy model review.
- [ ] Settings backup/restore with schema validation, safe import limits, and secrets excluded by default.
- [ ] Regression, lifecycle, migration, light/dark, multiple screen-size, and supported Android-version test matrix.
- [ ] Build debug APK and place it in a documented output location when toolchain/CI is available.
- [ ] Document incomplete items, protocol/Android restrictions, credentials required, and exact tested results.

## Safety and implementation guardrails

- Keep `MessagesController`, `MessagesStorage`, `SendMessagesHelper`, `NotificationsController`, `SharedConfig`, `TranslateController`, and account/session management intact unless a specific, tested integration requires changes.
- Nxygram-only settings use a separate preference namespace and schema version; do not repurpose Telegram keys.
- External translator/AI processing must be opt-in and disclosed before transmitting text.
- Do not claim a setting toggle works until it is wired to the feature implementation and tested.
- No release APK can be reported as built from repository inspection alone; record actual build command, toolchain, and result when run.
