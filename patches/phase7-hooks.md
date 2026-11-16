# Phase 7 — Hook Integration Guide

## 1. AndroidManifest.xml — Add activity-alias for custom icons
Inside <application>, after the main LaunchActivity:

  <!-- Default Nxygram icon -->
  <activity-alias android:name=".IconDefault"
      android:targetActivity=".LaunchActivity"
      android:enabled="true"
      android:icon="@mipmap/ic_launcher_nxygram">
      <intent-filter>
          <action android:name="android.intent.action.MAIN"/>
          <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
  </activity-alias>

  <!-- Telegram Classic icon -->
  <activity-alias android:name=".IconTelegram"
      android:targetActivity=".LaunchActivity"
      android:enabled="false"
      android:icon="@mipmap/ic_launcher_telegram">
      <intent-filter>
          <action android:name="android.intent.action.MAIN"/>
          <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
  </activity-alias>

  <!-- Midnight icon -->
  <activity-alias android:name=".IconMidnight"
      android:targetActivity=".LaunchActivity"
      android:enabled="false"
      android:icon="@mipmap/ic_launcher_midnight">
      <intent-filter>
          <action android:name="android.intent.action.MAIN"/>
          <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
  </activity-alias>

## 2. TelegramThemeImporter — ApplicationLoader.java
In onCreate(), replace:
  com.nxygram.NxygramThemeManager.applyCurrentTheme();
With:
  com.nxygram.TelegramThemeImporter.getInstance(this).applyActiveTheme();

## 3. TelegramThemeImporter — Import .attheme via bot/file picker
In any file-receive handler (e.g. when user sends .attheme via Telegram to self):
  if (fileName.endsWith(".attheme")) {
      boolean ok = TelegramThemeImporter.getInstance(context)
          .importAttheme(fileName, fileBytes);
      if (ok) Toast.makeText(context, "Theme imported!", Toast.LENGTH_SHORT).show();
  }

## 4. UserIdResolver — ProfileActivity.java
Find where "phone", "username", "bio" info rows are added to the profile layout.
After the last row:
  LinearLayout infoContainer = ...; // the vertical layout holding info rows
  View idRow = UserIdResolver.inject(context,
      userInfo.user.id,
      userInfo.user.username,
      false // not a channel
  );
  infoContainer.addView(idRow);

For channels/groups (add after member count row):
  View idRow = UserIdResolver.inject(context, -chat.id, chat.username, true);
  infoContainer.addView(idRow);

## 5. UserIdResolver — Chat toolbar subtitle
In ChatActivity, where actionBar.setSubtitle() is called:
  String subtitle = UserIdResolver.buildSubtitle(
      originalSubtitle, dialog_id, isChannel);
  actionBar.setSubtitle(subtitle);

## 6. UserIdResolver — Double-tap to copy message text
In ChatMessageCell.java, in bind() or setMessageObject():
  UserIdResolver.attachDoubleTapCopy(context,
      messageTextView, messageObject.messageText);

## 7. AutoReplyManager — MessagesController.java
In processUpdateArray(), when handling TL_updateNewMessage for private chats:
  TLRPC.Message msg = update.message;
  if (!msg.out && msg.dialog_id > 0) { // private chat, incoming
      String replyText = AutoReplyManager.getInstance(context)
          .onMessageReceived(
              msg.from_id.user_id,
              msg.message,
              msg.date,
              currentAccount
          );
      if (replyText != null) {
          SendMessagesHelper.getInstance(currentAccount)
              .sendMessage(replyText, msg.dialog_id,
                  null, null, null, false, null, null, null, true, 0, null);
      }
  }

## 8. ChatLockManager — DialogsActivity.java (chat list)
When building the dialogs list to display:
  ChatLockManager lockMgr = ChatLockManager.getInstance(context);
  List<TLRPC.Dialog> visible = new ArrayList<>();
  for (TLRPC.Dialog d : dialogs) {
      if (lockMgr.shouldShowInChatList(d.id)) {
          visible.add(d);
      }
  }
  // Add hidden folder row at top if any hidden chats exist
  if (!lockMgr.getHiddenChatIds().isEmpty() && !lockMgr.isHiddenFolderVisible()) {
      // Add a special "🔒 Hidden chats" header row
      // On tap: show PIN dialog → lockMgr.setHiddenFolderUnlocked(true) → refresh list
  }

## 9. ChatLockManager — ChatActivity.java (opening a chat)
In ChatActivity.onCreate() or onResume(), before showing the chat:
  ChatLockManager lockMgr = ChatLockManager.getInstance(context);
  if (lockMgr.isChatLocked(dialog_id)) {
      String lockType = lockMgr.getLockType(dialog_id);
      if ("biometric".equals(lockType)) {
          AppLockManager.getInstance().showBiometricPrompt(this,
              new AppLockManager.BiometricCallback() {
                  public void onSuccess() {
                      lockMgr.markUnlockedThisSession(dialog_id);
                      // proceed to show chat
                  }
                  public void onFailed() { finish(); }
                  public void onError(String e) { showPinDialog(dialog_id); }
              });
      } else {
          showPinDialog(dialog_id); // show PIN entry dialog
      }
  }

## 10. CustomNotificationManager — NotificationController.java
When building a notification for a dialog:
  String channelId = CustomNotificationManager.getInstance(context)
      .getOrCreateChannel(context, dialogId, dialogName);

  // Check if muted
  if (CustomNotificationManager.getInstance(context).isMuted(dialogId)) {
      return; // skip notification
  }

  // Hide preview if chat is locked
  String notifText = ChatLockManager.getInstance(context).isChatLocked(dialogId)
      ? "New message"
      : originalMessageText;

  // Use per-chat vibration
  ChatNotifSettings s = CustomNotificationManager.getInstance(context)
      .getSettings(dialogId);
  if (s != null && s.vibrationPattern != null) {
      notificationBuilder.setVibrate(s.vibrationPattern);
  }

## 11. IdentityTransformer — UserObject.java
In getUserName() / formatName():
  public static String getUserName(TLRPC.User user) {
      String original = /* existing logic */;
      return IdentityTransformer.getInstance(ApplicationLoader.applicationContext)
          .getLocalName(user.id, original);
  }

## 12. MultiAccountManager — account switching
Wherever UserConfig.selectedAccount changes (account switch button):
  int newAccount = ...; // the account being switched to
  UserConfig.selectedAccount = newAccount;
  MultiAccountManager.getInstance().onAccountSwitched(newAccount);

## 13. NxygramSettings — add new keys for Phase 7
Add these constants to NxygramSettings.java:
  public static final String KEY_IDENTITY_TRANSFORM  = "identity_transform_enabled";
  public static final String KEY_LOCAL_RENAME        = "local_rename_enabled";
  public static final String KEY_SHOW_USER_ID        = "show_user_id";
  public static final String KEY_DOUBLE_TAP_COPY     = "double_tap_copy";
  public static final String KEY_AUTOREPLY_AWAY      = "autoreply_away_enabled";
  public static final String KEY_AUTOREPLY_GREETING  = "autoreply_greeting_enabled";

## 14. NxygramSettingsActivity — add Phase 7 items
Add these sections to buildItems():

  // TRANSFORMATION
  L.add(Item.section("🎭 Transformation"));
  L.add(Item.toggle("Local Contact Rename","Rename contacts on your device only","local_rename_enabled",true));
  L.add(Item.toggle("Identity Transform","Custom display name and bio (your screen only)","identity_transform_enabled",false));
  L.add(Item.toggle("Show User ID on Profiles","Always show numeric ID on every profile","show_user_id",true));
  L.add(Item.toggle("Double-tap Message to Copy","Double-tap any message bubble to copy text","double_tap_copy",true));
  L.add(Item.nav("Import .attheme File","Add a real Telegram theme from a .attheme file",()->toast("Open theme file picker")));
  L.add(Item.nav("Switch App Icon","Change the launcher icon","switch_icon",()->toast("Open icon picker")));

  // AUTO-REPLY
  L.add(Item.section("🤖 Auto-Reply"));
  L.add(Item.toggle("Away Mode","Auto-reply when you're busy","autoreply_away_enabled",false));
  L.add(Item.toggle("First-Time Greeting","Welcome message to first-time DMs","autoreply_greeting_enabled",true));
  L.add(Item.nav("Away Message",AutoReplyManager.getInstance(ctx).getAwayMessage(),()->toast("Open AutoReplySettingsActivity")));
  L.add(Item.nav("Keyword Rules","Set up automatic replies for specific keywords",()->toast("Open KeywordRulesActivity")));

  // CHAT LOCK
  L.add(Item.section("🔐 Chat Lock"));
  L.add(Item.nav("Locked Chats","Manage locked and hidden chats",()->toast("Open ChatLockActivity")));
  L.add(Item.nav("Hidden Chats Folder","Chats hidden from main list","hidden_folder",()->toast("Open hidden folder")));

  // ACCOUNTS
  L.add(Item.section("👥 Multi-Account"));
  L.add(Item.nav("Account Labels","Custom names and colors per account",()->toast("Open AccountLabelsActivity")));
