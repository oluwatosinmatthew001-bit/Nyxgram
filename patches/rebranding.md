# Nxygram Rebranding Patch
# Apply these changes to the Telegram Android source

## 1. TMessagesProj/build.gradle

Find:
  applicationId "org.telegram.messenger"
Replace with:
  applicationId "com.nxygram.messenger"

Find:
  versionName "..."
Replace with:
  versionName "1.0.0"

Add to android.defaultConfig:
  resValue "string", "app_name", "Nxygram"

## 2. TMessagesProj/src/main/AndroidManifest.xml

Find:
  android:label="@string/app_name"
(already uses string resource — handled by step 1)

Find:
  package="org.telegram.messenger"
Replace with:
  package="com.nxygram.messenger"

Add inside <application>:
  <activity
      android:name="com.nxygram.NxygramSettingsActivity"
      android:label="Nxygram Settings"
      android:exported="false" />

## 3. TMessagesProj/src/main/res/values/strings.xml

Find:
  <string name="app_name">Telegram</string>
Replace with:
  <string name="app_name">Nxygram</string>

Find (if present):
  <string name="AppName">Telegram</string>
Replace with:
  <string name="AppName">Nxygram</string>

## 4. ApplicationLoader.java (init hook)
File: TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java

In onCreate(), after super.onCreate(), add:
  com.nxygram.NxygramSettings.init(this);
  com.nxygram.NxygramThemeManager.applyCurrentTheme();

## 5. MessagesController.java (Anti-Delete hook)
File: TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java

Find method: deleteMessages(...)
At the TOP of this method, before existing deletion logic, add:
  // Nxygram: Anti-Delete — mark messages as deleted in local store
  if (com.nxygram.NxygramSettings.isAntiDeleteEnabled()) {
      com.nxygram.AntiDeleteManager.getInstance(ApplicationLoader.applicationContext)
          .onMessagesDeleted(messages, dialogId, currentAccount);
  }

Find where new messages arrive (processUpdateArray or similar, handling TL_updateNewMessage):
Add BEFORE the message is processed:
  // Nxygram: Cache message for anti-delete
  if (com.nxygram.NxygramSettings.isAntiDeleteEnabled() && update.message != null) {
      TLRPC.Message msg = update.message;
      String senderName = "";
      com.nxygram.AntiDeleteManager.getInstance(ApplicationLoader.applicationContext)
          .onMessageReceived(
              msg.id,
              msg.dialog_id,
              msg.from_id != null ? msg.from_id.user_id : 0,
              senderName,
              msg.message,
              null, null,
              msg.date,
              currentAccount
          );
  }

Find where TL_updateEditMessage is handled:
Add:
  // Nxygram: Anti-Edit — save original before applying edit
  if (com.nxygram.NxygramSettings.isAntiEditEnabled()) {
      TLRPC.Message original = /* get current message from db */;
      TLRPC.Message edited   = update.message;
      if (original != null) {
          com.nxygram.AntiDeleteManager.getInstance(ApplicationLoader.applicationContext)
              .onMessageEdited(
                  original.id, original.dialog_id,
                  original.from_id != null ? original.from_id.user_id : 0,
                  "", original.message, edited.message,
                  original.date, currentAccount
              );
      }
  }

## 6. SettingsActivity.java (add Nxygram button)
File: TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java

In the settings item list, add a row for Nxygram:
  // Find where other settings rows are added (look for "Privacy" or "Notifications" rows)
  // Add:
  TextSettingsCell nxygramCell = new TextSettingsCell(context);
  nxygramCell.setText("✦ Nxygram Settings", false);
  nxygramCell.setOnClickListener(v -> {
      presentFragment(new NxygramSettingsFragment());
      // OR: startActivity(new Intent(getContext(), NxygramSettingsActivity.class));
  });

## 7. API Credentials
File: TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java

Find:
  public static int APP_ID = ...
  public static String APP_HASH = ...

These are set by GitHub Actions secrets via api_keys.xml.
Make sure BuildVars reads from resources or secrets correctly.
