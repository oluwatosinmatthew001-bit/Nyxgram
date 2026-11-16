# ✦ Nxygram — Complete Source Package
### Your chats. Your style. Your world.
> Fully customized Android Telegram client — 38 Java files, 7 feature phases.

---

## Contents

```
Nxygram-Complete/
├── README.md                          ← this file
├── .github/workflows/
│   ├── build.yml                      ← auto-builds debug APK on every push
│   └── release.yml                    ← signed release APK on git tag
├── NxygramCore/src/main/java/com/nxygram/
│   ├── PrexzyApi.java                 ← central AI engine (prexzyapis.com)
│   ├── NxygramSettings.java           ← all feature toggles + persistence
│   ├── NxygramSettingsActivity.java   ← complete settings screen
│   ├── NxygramThemeManager.java       ← 6 built-in themes
│   ├── TelegramThemeImporter.java     ← import .attheme, Telegram themes, custom icons
│   ├── ThemeStudioActivity.java       ← visual theme picker
│   ├── AntiDeleteManager.java         ← deleted + edited message preservation
│   ├── DeletedMessagesActivity.java   ← viewer for preserved messages
│   ├── TranslationManager.java        ← auto-translation (no key needed)
│   ├── TranslationPreviewDialog.java  ← confirm before sending translation
│   ├── ViewOnceBypass.java            ← save view-once + screenshot unlock
│   ├── GhostModeManager.java          ← typing/online/receipt suppression
│   ├── ReverseChatManager.java        ← your messages appear from recipient
│   ├── AppLockManager.java            ← PIN + biometric lock logic
│   ├── AppLockActivity.java           ← lock screen UI
│   ├── NxygramAiManager.java          ← AI features wrapper
│   ├── AiAssistantBar.java            ← chip bar above input
│   ├── NxygramAiChatActivity.java     ← full AI chat + web search
│   ├── AntiSpamManager.java           ← spam filter + link safety
│   ├── DraftManager.java              ← multi-chat draft history
│   ├── MessageScheduler.java          ← schedule messages via AlarmManager
│   ├── SchedulerActivity.java         ← view + cancel scheduled messages
│   ├── NxygramBubbleRenderer.java     ← custom bubble drawing
│   ├── StorageAnalyzer.java           ← storage scan engine
│   ├── StorageAnalyzerActivity.java   ← visual storage breakdown
│   ├── StoryToolsManager.java         ← story save + tracking
│   ├── ApiKeyManager.java             ← AES-256-GCM key storage
│   ├── ApiKeyDialog.java              ← key collection UI
│   ├── AutoReplyManager.java          ← away mode, greetings, keyword rules
│   ├── AutoReplySettingsActivity.java ← full auto-reply UI
│   ├── UserIdResolver.java            ← always show user ID, double-tap copy
│   ├── GlobalSearchManager.java       ← search across all chats
│   ├── GlobalSearchActivity.java      ← global search UI + AI search
│   ├── ChatLockManager.java           ← per-chat PIN lock + hidden folder
│   ├── ChatLockActivity.java          ← manage locked + hidden chats
│   ├── CustomNotificationManager.java ← per-contact sounds, vibration, LED
│   ├── IdentityTransformer.java       ← local contact rename, avatar overlays
│   └── MultiAccountManager.java       ← per-account labels, colors, themes
└── patches/
    ├── rebranding.md                  ← Phase 1: app name, ID, strings
    ├── manifest-additions.xml         ← ALL activities, receivers, permissions
    ├── settings-additions.md          ← missing KEY_* constants to add
    ├── phase2-hooks.md                ← AppLock, ThemeStudio hooks
    ├── phase3-hooks.md                ← AI bar, AntiSpam hooks
    ├── phase4-migration.md            ← PrexzyApi migration guide
    ├── phase5-hooks.md                ← Scheduler, bubbles, stories hooks
    └── phase7-hooks.md                ← Themes, UserID, AutoReply, ChatLock hooks
```

---

## Build with GitHub Actions (zero local setup)

### Step 1 — GitHub Secrets
Repo → Settings → Secrets and variables → Actions

| Secret | Value |
|---|---|
| `TELEGRAM_API_ID` | From my.telegram.org |
| `TELEGRAM_API_HASH` | From my.telegram.org |

For release APK also add:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w 0 keystore.jks` |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

### Step 2 — Copy files into your Telegram source repo

```
NxygramCore/src/main/java/com/nxygram/
  → TMessagesProj/src/main/java/com/nxygram/   (create folder)

.github/workflows/
  → .github/workflows/   (in your repo root)
```

### Step 3 — Apply patches

**TMessagesProj/build.gradle**
```
applicationId "org.telegram.messenger"  →  "com.nxygram.messenger"
versionName "..."                        →  "1.0.0"
```
Add to dependencies:
```gradle
implementation "androidx.biometric:biometric:1.1.0"
```

**TMessagesProj/src/main/res/values/strings.xml**
```xml
<string name="app_name">Telegram</string>  →  <string name="app_name">Nxygram</string>
```

**TMessagesProj/src/main/AndroidManifest.xml**
- Change `package="org.telegram.messenger"` → `package="com.nxygram.messenger"`
- Paste everything from `patches/manifest-additions.xml` inside `<application>`

**TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java**
Add at top of `onCreate()`:
```java
com.nxygram.NxygramSettings.init(this);
com.nxygram.TelegramThemeImporter.getInstance(this).applyActiveTheme();
com.nxygram.MessageScheduler.getInstance(this).rescheduleAll();
```

**NxygramSettings.java** — add missing constants from `patches/settings-additions.md`

### Step 4 — Wire feature hooks
Each `patches/phase*.md` file has exact copy-paste code for every hook location in Telegram's source.

### Step 5 — Push and get APK
```bash
git add .
git commit -m "Add Nxygram"
git push
```
GitHub Actions tab → latest run → Artifacts → download APK.

Release APK: `git tag v1.0.0 && git push origin v1.0.0`

---

## AI Engine — PrexzyApi

All AI features use one endpoint. No external API keys needed.

```
POST https://prexzyapis.com/ai/askgpt5
{
  "prompt":     "...",   // required
  "state":      "...",   // optional — conversation history
  "media":      "...",   // optional — base64 image
  "model":      "...",   // optional — model override
  "web_search": true     // optional — live web results
}
```

---

## Feature Summary (38 files)

| # | Feature | File(s) |
|---|---|---|
| 1 | Settings engine | NxygramSettings |
| 2 | 6 built-in themes | NxygramThemeManager |
| 3 | Theme Studio UI | ThemeStudioActivity |
| 4 | .attheme import + Telegram themes + custom icons | TelegramThemeImporter |
| 5 | Anti-Delete + Anti-Edit | AntiDeleteManager |
| 6 | Deleted messages viewer | DeletedMessagesActivity |
| 7 | Universal Translator | TranslationManager |
| 8 | Translation preview dialog | TranslationPreviewDialog |
| 9 | View-once bypass + screenshot + forward bypass | ViewOnceBypass |
| 10 | Ghost mode (typing/online/receipts) | GhostModeManager |
| 11 | Reverse Chat | ReverseChatManager |
| 12 | App Lock (PIN + biometric) | AppLockManager + AppLockActivity |
| 13 | AI engine | PrexzyApi |
| 14 | AI manager (smart reply, grammar, tone, summarize) | NxygramAiManager |
| 15 | AI chip bar above input | AiAssistantBar |
| 16 | Full AI chat screen + web search | NxygramAiChatActivity |
| 17 | Anti-spam + link safety | AntiSpamManager |
| 18 | Draft manager | DraftManager |
| 19 | Message scheduler | MessageScheduler + SchedulerActivity |
| 20 | Custom bubble renderer | NxygramBubbleRenderer |
| 21 | Storage analyzer | StorageAnalyzer + StorageAnalyzerActivity |
| 22 | Story tools | StoryToolsManager |
| 23 | Encrypted API key storage | ApiKeyManager + ApiKeyDialog |
| 24 | Auto-reply (away, greeting, keywords) | AutoReplyManager + AutoReplySettingsActivity |
| 25 | User ID on every profile + double-tap copy | UserIdResolver |
| 26 | Global search across all chats | GlobalSearchManager + GlobalSearchActivity |
| 27 | Per-chat PIN lock + hidden folder | ChatLockManager + ChatLockActivity |
| 28 | Per-contact notification sounds + vibration | CustomNotificationManager |
| 29 | Local contact rename + avatar overlays | IdentityTransformer |
| 30 | Multi-account labels + colors + themes | MultiAccountManager |
| 31 | Main settings screen | NxygramSettingsActivity |
| 32 | GitHub Actions CI/CD | build.yml + release.yml |

---

## Hook locations quick reference

| Feature | File to edit | What to add |
|---|---|---|
| Init | ApplicationLoader.java | NxygramSettings.init(), applyActiveTheme(), rescheduleAll() |
| App Lock | LaunchActivity.java | AppLockManager.checkLock() in onResume() |
| Anti-Delete | MessagesController.java | AntiDeleteManager.onMessageReceived() before delete |
| Ghost typing | MessagesController.java | Wrap setTyping with GhostModeManager.shouldSuppressTyping() |
| Ghost online | MessagesController.java | Wrap updateStatus with shouldSuppressOnline() |
| Translation incoming | ChatMessageCell.java | TranslationManager.translateIncoming() on bind |
| Translation outgoing | ChatActivity.java | Replace sendMessage() with preview flow |
| Reverse Chat | ChatMessageCell.java | ReverseChatManager.getRenderMode() in bind |
| Reverse mark sent | SendMessagesHelper.java | markMessageReversed() on send if enabled |
| View-once | PhotoViewer.java | ViewOnceBypass.onViewOnceOpened() before display |
| Screenshots | Every Activity.onResume() | ViewOnceBypass.applyScreenshotPolicy() |
| Forward bypass | ChatActivity.java | Wrap noforwards check with shouldBypassForwardRestriction() |
| Bubble style | ChatMessageCell.java | setBackground(NxygramBubbleRenderer.makeDrawable()) |
| AI bar | ChatActivity.java | Add AiAssistantBar above message input |
| Scheduler | ChatActivity.java | sendButton.setOnLongClickListener() → showSchedulePicker() |
| Stories | StoriesController.java | StoryToolsManager.onStoryViewed() on display |
| Draft save | ChatActivity.onPause() | DraftManager.saveDraft() |
| Draft restore | ChatActivity.onResume() | DraftManager.getDraft() → set input |
| Auto-reply | MessagesController.java | AutoReplyManager.onMessageReceived() on new private msg |
| User ID | ProfileActivity.java | UserIdResolver.inject() after info rows |
| User ID subtitle | ChatActivity.java | UserIdResolver.buildSubtitle() in actionBar.setSubtitle() |
| Double-tap copy | ChatMessageCell.java | UserIdResolver.attachDoubleTapCopy() on message view |
| Chat lock | DialogsActivity.java | ChatLockManager.shouldShowInChatList() filter |
| Chat lock open | ChatActivity.onCreate() | ChatLockManager.isChatLocked() check |
| Notifications | NotificationController.java | CustomNotificationManager.getOrCreateChannel() |
| Contact rename | UserObject.java | IdentityTransformer.getLocalName() in getUserName() |
| Theme import | ApplicationLoader.java | TelegramThemeImporter.applyActiveTheme() |
| Account switch | LaunchActivity.java | MultiAccountManager.onAccountSwitched() |
| Settings entry | SettingsActivity.java | Add row → start NxygramSettingsActivity |

---

## Legal
Built from DrKLO/Telegram — GPL-2.0.
Nxygram is not affiliated with Telegram Messenger Inc.
Source must be provided on distribution per GPL-2.0.
