# Phase 2 — Hook Integration Guide
# Apply these changes to the Telegram Android source

## 1. AndroidManifest.xml
Add inside <application>:

  <activity
      android:name="com.nxygram.AppLockActivity"
      android:exported="false"
      android:windowSoftInputMode="stateHidden"
      android:excludeFromRecents="true" />

  <activity
      android:name="com.nxygram.DeletedMessagesActivity"
      android:exported="false" />

  <activity
      android:name="com.nxygram.ThemeStudioActivity"
      android:exported="false" />

## 2. App Lock — LaunchActivity.java
(The main entry activity — may be named LaunchActivity or MainActivity)

In onResume(), add:
  AppLockManager.getInstance().checkLock(this, new AppLockManager.LockCallback() {
      public void onUnlocked() { /* continue normally */ }
      public void onLockRequired() { /* AppLockActivity is already launched by checkLock */ }
  });

In onStop() or onPause(), add:
  AppLockManager.getInstance().onAppBackgrounded();

## 3. App Lock — build.gradle dependencies
Add to TMessagesProj/build.gradle dependencies:
  implementation "androidx.biometric:biometric:1.1.0"

## 4. Reverse Chat — ChatActivity.java
Search for where chatListView (RecyclerListView) is created.

AFTER the adapter is set, add:
  ReverseChatManager.getInstance().applyToRecyclerView(chatListView, dialog_id);

In the message adapter's onBindViewHolder, at the END, add:
  ReverseChatManager.getInstance().applyToItemView(holder.itemView, dialog_id);

After sending/receiving message, where the list scrolls, add:
  ReverseChatManager.getInstance().onNewMessage(chatListView, dialog_id);

In the chat toolbar menu (usually in createMenu or onOptionsItemSelected), add:
  MenuItem reverseItem = menu.add("⇅ Reverse Chat");
  reverseItem.setOnMenuItemClickListener(item -> {
      ReverseChatManager.getInstance().toggleChatReverse(chatListView, dialog_id);
      return true;
  });

## 5. Translation Preview — ChatActivity.java
Find where sendMessage() is called after the user taps send.

Replace:
  sendMessage(text);

With:
  if (NxygramSettings.isTranslatorEnabled() && NxygramSettings.isTransOutgoing()) {
      TranslationManager.getInstance().translateOutgoing(
          context, dialog_id, text, replyingMessageObject,
          new TranslationManager.TranslationCallback() {
              public void onSuccess(String translated, String fromLang) {
                  TranslationPreviewDialog.show(
                      context, text, translated, fromLang,
                      NxygramSettings.getTransTargetLang(),
                      new TranslationPreviewDialog.Callback() {
                          public void onConfirm(String toSend) { sendMessage(toSend); }
                          public void onCancel() { /* user cancelled */ }
                      });
              }
              public void onFailure(String err) {
                  // Translation failed — send original
                  sendMessage(text);
              }
          });
  } else {
      sendMessage(text);
  }

## 6. Settings Screen — link ThemeStudio and DeletedMessages
In NxygramSettingsActivity.java, the navigate items already have:
  "View Deleted Messages" → open DeletedMessagesActivity
  "Current Theme" → open ThemeStudioActivity

Replace the toast() calls with real Intent launches:
  case "View Deleted Messages":
      Intent i = new Intent(this, DeletedMessagesActivity.class);
      i.putExtra("chat_id", 0L);  // 0 = global view, or pass current chatId
      i.putExtra("account_id", currentAccount);
      startActivity(i);
      break;

  case "Theme":
      startActivity(new Intent(this, ThemeStudioActivity.class));
      break;

## 7. Reverse Chat — NxygramSettingsActivity.java
Add this item to the settings list (in buildItems()):

  items.add(Item.section("💬 Chat Display"));
  items.add(Item.toggle(
      "Reverse Chat Mode",
      "Show newest messages at the top, oldest at the bottom",
      "reverse_chat_enabled", false));
  items.add(Item.info(
      "Per-chat toggle",
      "Tap the ⇅ icon in any chat toolbar to reverse that chat individually"));
