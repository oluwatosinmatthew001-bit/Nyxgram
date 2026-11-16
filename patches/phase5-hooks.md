# Phase 5 — Hook Integration Guide

## 1. AndroidManifest.xml — Add entries

  <activity android:name="com.nxygram.SchedulerActivity"
            android:exported="false"/>
  <activity android:name="com.nxygram.StorageAnalyzerActivity"
            android:exported="false"/>

  <receiver android:name="com.nxygram.MessageScheduler$ScheduledMessageReceiver"
            android:exported="false"/>
  <receiver android:name="com.nxygram.MessageScheduler$BootReceiver"
            android:exported="true">
      <intent-filter>
          <action android:name="android.intent.action.BOOT_COMPLETED"/>
      </intent-filter>
  </receiver>

  <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
  <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
  <uses-permission android:name="android.permission.VIBRATE"/>

## 2. Message Scheduler — ChatActivity.java

  On send button LONG PRESS (find the send button's onLongClick or add one):

  sendButton.setOnLongClickListener(v -> {
      String text = messageEditText.getText().toString().trim();
      if (text.isEmpty()) return false;

      SchedulerActivity.showSchedulePicker(
          context, text, dialog_id, chatName, currentAccount,
          scheduledId -> {
              if (scheduledId > 0) {
                  messageEditText.setText("");
                  Toast.makeText(context, "Message scheduled ✓", Toast.LENGTH_SHORT).show();
              }
          });
      return true; // consume long click
  });

## 3. Message Scheduler — Reschedule on app start

  In ApplicationLoader.onCreate() or LaunchActivity:
  MessageScheduler.getInstance(this).rescheduleAll();

## 4. Message Scheduler — Fire send hook

  In MessageScheduler.ScheduledMessageReceiver.onReceive(), after getting msg:

  // Telegram's send pipeline:
  org.telegram.messenger.SendMessagesHelper.getInstance(msg.accountId)
      .sendMessage(msg.messageText,  // text
                   msg.chatId,       // dialog_id
                   null,             // replyToMsg
                   null,             // replyToTopMsg
                   null,             // webPage
                   false,            // searchLinks
                   null,             // entities
                   null,             // inputContent
                   null,             // options (for scheduled — use null for immediate)
                   true,             // notify
                   0,                // scheduleDate = 0 for immediate
                   null);            // quickReplyShortcut

## 5. Custom Bubble Renderer — ChatMessageCell.java

  Add field:
  private static NxygramBubbleRenderer bubbleRenderer;

  In setMessageObject() or bind():
  if (NxygramSettings.getBool("custom_bubbles", true)) {
      if (bubbleRenderer == null)
          bubbleRenderer = new NxygramBubbleRenderer(getContext());
      setBackground(NxygramBubbleRenderer.makeDrawable(
          getContext(), messageObject.isOutgoing()));
  }

  Add to NxygramSettings:
  public static final String KEY_CUSTOM_BUBBLES = "custom_bubbles";
  public static final String KEY_BUBBLE_SHADOW  = "bubble_shadow";

  Add to NxygramSettingsActivity under Theme section:
  items.add(Item.toggle("Custom Bubble Style",
      "Nxygram-styled rounded message bubbles",
      "custom_bubbles", true));
  items.add(Item.toggle("Bubble Shadow",
      "Subtle shadow behind message bubbles",
      "bubble_shadow", true));

## 6. Story Tools — StoriesController.java

  When a story item is loaded for display (look for where TLRPC.StoryItem
  is processed for viewing):

  TLRPC.StoryItem story = /* current story */;
  boolean restricted = (story.noforwards); // Telegram's no-forward flag

  StoryToolsManager.getInstance(context).onStoryViewed(
      story.id,
      story.from_id.user_id,  // or peer_id depending on version
      UserObject.getUserName(currentUser),
      getStoryMediaUrl(story), // extract URL from story.media
      story.media instanceof TLRPC.TL_messageMediaDocument, // is video?
      story.expire_date,
      story.caption != null ? story.caption : null,
      restricted
  );

  For manual save (long-press story):
  StoryToolsManager.getInstance(context).saveStoryManual(
      story.id, userId, userName, mediaUrl, isVideo, expiresAt, caption,
      restricted,
      () -> Toast.makeText(context, "Story saved", Toast.LENGTH_SHORT).show(),
      () -> Toast.makeText(context, "Saving disabled for this story",
          Toast.LENGTH_SHORT).show()
  );

## 7. Storage Analyzer — NxygramSettingsActivity

  Replace the existing toast() for "Storage Analyzer":
  startActivity(new Intent(this, StorageAnalyzerActivity.class));

## 8. NxygramSettingsActivity — Add Phase 5 items

  In buildItems(), add under Smart Messaging section:

  items.add(Item.navigate(
      "📅 Scheduled Messages",
      "View and manage messages queued to send",
      () -> startActivity(new Intent(this, SchedulerActivity.class)
                .putExtra("account_id", 0))));

  Under Theme section add:
  items.add(Item.toggle("Custom Bubble Style",
      "Nxygram rounded message bubbles",
      "custom_bubbles", true));
  items.add(Item.toggle("Bubble Shadow",
      "Drop shadow on message bubbles",
      "bubble_shadow", true));

## 9. PrexzyApi — test connectivity on first launch

  In ApplicationLoader.onCreate(), add once:
  if (BuildConfig.DEBUG) {
      PrexzyApi.call(this)
          .prompt("Reply with just: OK")
          .onResult(r -> Log.d("Nxygram", "PrexzyApi online: " + r))
          .onError(e -> Log.e("Nxygram", "PrexzyApi error: " + e))
          .execute();
  }
