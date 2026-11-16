# Phase 4 — PrexzyApi Migration
# Replace all old AI/translation provider calls with PrexzyApi

## 1. Files REPLACED in this phase (use new versions)
   - TranslationManager.java    → v2 (uses PrexzyApi)
   - NxygramAiManager.java      → v2 (uses PrexzyApi)
   - AiAssistantBar.java        → v2 (uses PrexzyApi, adds web search chip)

## 2. Files REMOVED (no longer needed)
   - ApiKeyManager.java         → delete or keep for future optional features
   - ApiKeyDialog.java          → delete (no external keys needed)
   - The DeepL / Google / LibreTranslate / OpenAI provider classes
     inside the old TranslationManager.java

## 3. NxygramSettings — remove provider selection settings
   These are no longer needed since there's one provider:
   - KEY_TRANS_PROVIDER   → remove
   - KEY_AI_PROVIDER      → remove
   The API key dialog calls can be removed from NxygramSettingsActivity.

## 4. NxygramSettingsActivity — update settings

   REMOVE these items from buildItems():
     - "Translation Provider" navigate row
     - "AI Provider" navigate row
     - Any API key configuration rows

   ADD these items:
     // AI Chat
     items.add(Item.navigate(
         "✦ Open AI Assistant",
         "Chat with Nxygram AI, translate, search the web",
         () -> startActivity(new Intent(this, NxygramAiChatActivity.class))));

## 5. AndroidManifest.xml — add new activity
   <activity
       android:name="com.nxygram.NxygramAiChatActivity"
       android:exported="false"
       android:windowSoftInputMode="adjustResize" />

## 6. Internet permission (should already exist in Telegram source)
   Confirm in AndroidManifest.xml:
   <uses-permission android:name="android.permission.INTERNET" />

## 7. How PrexzyApi is called throughout the app

   TRANSLATION (incoming):
     TranslationManager.getInstance().translateIncoming(ctx, chatId, text, callback);
     → internally calls PrexzyApi with prompt: "Translate to English: {text}"

   TRANSLATION (outgoing preview):
     TranslationManager.getInstance().translateOutgoing(ctx, chatId, text, null, callback);
     → returns translation to TranslationPreviewDialog — user confirms before send

   SMART REPLY:
     aiBar.onNewMessage(messageText);
     → PrexzyApi: prompt asks for 3 reply suggestions as JSON array

   GRAMMAR FIX:
     PrexzyApi.fixGrammar(ctx, text, onResult, onError);

   TONE CHANGE:
     PrexzyApi.changeTone(ctx, text, "formal and polite", onResult, onError);

   WEB SEARCH:
     PrexzyApi.webSearch(ctx, query, onResult, onError);
     → passes web_search=true in POST body

   SUMMARIZE:
     PrexzyApi.summarize(ctx, conversationText, onResult, onError);

   GENERAL CHAT with memory:
     PrexzyApi.chat(ctx, message, stateString, onResult, onError);

   IMAGE ANALYSIS:
     PrexzyApi.analyzeMedia(ctx, base64Image, "What's in this image?", onResult, onError);

## 8. API request format sent to prexzyapis.com
   POST https://prexzyapis.com/ai/askgpt5
   Content-Type: application/json

   {
     "prompt": "...",          // required
     "state": "...",           // optional — conversation history
     "media": "base64...",     // optional — image/file
     "model": "...",           // optional — model override
     "web_search": true        // optional — enable live web results
   }

## 9. Quick test after integration
   Run this in a debug build to test the API connection:

   PrexzyApi.call(context)
       .prompt("Say hello in 5 languages")
       .onResult(r -> Log.d("Nxygram", "API OK: " + r))
       .onError(e -> Log.e("Nxygram", "API FAIL: " + e))
       .execute();

   Expected: response with hello in 5 languages, confirming API is live.
