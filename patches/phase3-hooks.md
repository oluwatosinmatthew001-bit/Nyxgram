# Phase 3 — Hook Integration Guide

## 1. Reverse Chat v2 — ChatActivity.java

### A. Where messages are rendered (ChatMessageCell or adapter)

Find the section that sets outgoing vs incoming layout:
  boolean isOutgoing = messageObject.isOutgoing();

REPLACE WITH:
  boolean isOutgoing = messageObject.isOutgoing();
  ReverseChatManager.RenderMode renderMode = ReverseChatManager.getInstance()
      .getRenderMode(isOutgoing, messageObject.getId(), dialog_id);

  // Treat REVERSED_OUTGOING as incoming for layout purposes
  boolean layoutAsIncoming = renderMode == ReverseChatManager.RenderMode.NORMAL_INCOMING
                          || renderMode == ReverseChatManager.RenderMode.REVERSED_OUTGOING;

  if (layoutAsIncoming) {
      // LEFT side layout
      String displayName;
      Bitmap displayAvatar;
      if (renderMode == ReverseChatManager.RenderMode.REVERSED_OUTGOING) {
          // Show recipient's identity on YOUR outgoing message
          displayName   = currentChat != null ? currentChat.title
                        : currentUser != null ? UserObject.getUserName(currentUser)
                        : "Them";
          displayAvatar = recipientAvatarBitmap; // avatar of the other person
      } else {
          displayName   = senderName;
          displayAvatar = senderAvatarBitmap;
      }
      // Apply left-side bubble with displayName and displayAvatar
      // This uses the same incoming layout code path you already have
  } else {
      // Right side layout (normal outgoing)
  }

### B. Mark message as reversed when sending

In SendMessagesHelper.java or ChatActivity.java, find where a message
is enqueued/sent and add BEFORE the actual send call:

  if (ReverseChatManager.getInstance().isEnabled(dialog_id)) {
      // messageObject.id may be negative (pending) — that's fine,
      // Telegram updates it once confirmed. Mark both pending and real IDs.
      ReverseChatManager.getInstance().markMessageReversed(
          messageObject.getId(), dialog_id);
  }

### C. Toolbar toggle button

In ChatActivity, inside the action bar menu (onCreateOptionsMenu or equivalent):
  MenuItem item = menu.add(0, MENU_REVERSE_CHAT, 0, "⇅ Reverse Chat");
  item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

In onOptionsItemSelected:
  case MENU_REVERSE_CHAT:
      boolean nowOn = ReverseChatManager.getInstance().isEnabled(dialog_id);
      ReverseChatManager.getInstance().setEnabled(dialog_id, !nowOn);
      chatAdapter.notifyDataSetChanged();
      Toast.makeText(context,
          nowOn ? "Reverse Chat OFF — new messages appear normally"
                : "Reverse Chat ON — your messages appear as theirs",
          Toast.LENGTH_SHORT).show();
      return true;

### D. NxygramSettingsActivity — add toggle
Already handled in NxygramSettingsActivity. Also add per-chat context menu item
in the chat long-press menu (ChatListActivity):
  "⇅ Reverse this chat"
  → ReverseChatManager.getInstance().setEnabled(chatId, !current)

---

## 2. API Key Auto-Prompt

### A. When Translation is first enabled (NxygramSettingsActivity)
In the TRANSLATOR toggle's onCheckedChanged, when checked = true:
  if (checked) {
      ApiKeyDialog.ensureReady(context, ApiKeyDialog.Feature.TRANSLATION, () -> {
          NxygramSettings.setTranslator(true);
          Toast.makeText(context, "Translation ready", Toast.LENGTH_SHORT).show();
      });
  }

### B. When AI Assistant is first enabled
  if (checked) {
      ApiKeyDialog.ensureReady(context, ApiKeyDialog.Feature.AI, () -> {
          NxygramSettings.setAiAssistant(true);
      });
  }

### C. When Translation is used but key is missing (TranslationManager)
In TranslationManager.translateIncoming() / translateOutgoing(), if the
API call returns 401/403:
  mainHandler.post(() ->
      ApiKeyDialog.showForService(context,
          findService(NxygramSettings.getTransProvider()), null));

---

## 3. AI Assistant Bar — ChatActivity.java

### A. Add bar to layout
In ChatActivity, find where the message input bar (chatActivityEnterView) is added.
Add the AI bar above it:

  AiAssistantBar aiBar = new AiAssistantBar(context, messageEditText, dialog_id);
  inputContainer.addView(aiBar.getView(), 0); // index 0 = above input

### B. Trigger on new message
When a new message is received in the chat and displayed, add:
  if (NxygramSettings.isAiAssistant() && messageObject.isFromUser()) {
      aiBar.onNewMessage(messageObject.messageText);
  }

### C. Trigger on user typing
Add a TextWatcher to the message input EditText:
  messageEditText.addTextChangedListener(new TextWatcher() {
      public void onTextChanged(CharSequence s, int start, int before, int count) {
          if (s.length() > 10) aiBar.onUserTyping(s.toString());
          else if (s.length() == 0) aiBar.hide();
      }
      public void beforeTextChanged(...) {}
      public void afterTextChanged(...) {}
  });

---

## 4. Anti-Spam — MessagesController.java

In processUpdateArray(), when handling TL_updateNewMessage:
  if (NxygramSettings.isAntiSpam() && update.message != null) {
      AntiSpamManager.SpamResult spam = AntiSpamManager.getInstance(context)
          .check(update.message.message,
                 update.message.from_id != null ? update.message.from_id.user_id : 0,
                 update.message.dialog_id);
      if (spam.isSpam) {
          // Option 1: Mark message with a spam badge (add field to local DB)
          // Option 2: Move to a hidden spam folder
          // Option 3: Show a warning notification
          // Recommended: add "spamScore" field to message metadata and
          // show a yellow warning badge in ChatMessageCell
          update.message.flags |= NXYGRAM_FLAG_SPAM; // define your own flag
          notifySpam(update.message, spam);
      }
  }

## 5. Link Safety — AlertsCreator.java or browser launch point
In Telegram's link-open handler (usually in AlertsCreator or Browser.java):
  String warning = AntiSpamManager.getInstance(context).getLinkWarning(url);
  if (warning != null) {
      new AlertDialog.Builder(context)
          .setTitle("Suspicious Link")
          .setMessage(warning)
          .setPositiveButton("Open Anyway", (d,w) -> openUrl(url))
          .setNegativeButton("Cancel", null)
          .show();
  } else {
      openUrl(url);
  }

---

## 6. DeepL Translation Provider — TranslationManager.java
Add this inner class to TranslationManager.java:

  public static class DeepLProvider implements TranslationProvider {
      @Override public String getId() { return "deepl"; }

      @Override
      public void translate(String text, String src, String tgt, TranslationCallback cb) {
          try {
              String apiKey = ApiKeyManager.getInstance(context).getKey(KEY_DEEPL_KEY);
              // DeepL free API endpoint:
              String url = "https://api-free.deepl.com/v2/translate";
              // Build form body
              String body = "auth_key=" + apiKey
                  + "&text=" + java.net.URLEncoder.encode(text, "UTF-8")
                  + "&target_lang=" + tgt.toUpperCase()
                  + (src.equals("auto") ? "" : "&source_lang=" + src.toUpperCase());
              // POST and parse response...
              // response JSON: {"translations":[{"detected_source_language":"EN","text":"..."}]}
          } catch (Exception e) { cb.onFailure(e.getMessage()); }
      }
      @Override public void detectLanguage(String text, DetectionCallback cb) {
          translate(text, "auto", "EN", (t, lang) -> cb.onDetected(lang, 0.9f), cb::onFailure);
      }
  }

Then in getActiveProvider(), add:
  case "deepl": return new DeepLProvider();
