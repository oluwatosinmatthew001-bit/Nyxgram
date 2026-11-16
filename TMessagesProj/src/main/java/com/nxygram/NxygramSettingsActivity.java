package com.nxygram;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * NxygramSettingsActivity — FINAL COMPLETE VERSION
 * All features from Phases 1-6 wired into one screen.
 *
 * Register in AndroidManifest.xml:
 *   <activity android:name="com.nxygram.NxygramSettingsActivity"
 *             android:exported="false" />
 */
public class NxygramSettingsActivity extends AppCompatActivity {

    private static final int TYPE_SECTION  = 0;
    private static final int TYPE_TOGGLE   = 1;
    private static final int TYPE_NAVIGATE = 2;
    private static final int TYPE_INFO     = 3;

    static class Item {
        int type; String title, subtitle, key; boolean def; Runnable action;
        static Item section(String t) { Item i=new Item(); i.type=TYPE_SECTION; i.title=t; return i; }
        static Item toggle(String t,String s,String k,boolean d) { Item i=new Item(); i.type=TYPE_TOGGLE; i.title=t; i.subtitle=s; i.key=k; i.def=d; return i; }
        static Item nav(String t,String s,Runnable a) { Item i=new Item(); i.type=TYPE_NAVIGATE; i.title=t; i.subtitle=s; i.action=a; return i; }
        static Item info(String t,String s) { Item i=new Item(); i.type=TYPE_INFO; i.title=t; i.subtitle=s; return i; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NxygramSettings.init(this);
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        getWindow().getDecorView().setBackgroundColor(t.colorBackground);
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new Adapter(this, buildItems()));
        setContentView(rv);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("✦ Nxygram");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private List<Item> buildItems() {
        List<Item> L = new ArrayList<>();
        Context ctx = this;

        // APPEARANCE
        L.add(Item.section("🎨 Appearance & Themes"));
        L.add(Item.nav("Theme Studio","Active: "+NxygramSettings.getThemeName()+" — tap to change",()->startActivity(new Intent(ctx,ThemeStudioActivity.class))));
        L.add(Item.toggle("AMOLED Black Mode","True black for OLED screens",NxygramSettings.KEY_THEME_AMOLED,false));
        L.add(Item.toggle("Custom Bubble Style","Nxygram rounded message bubbles","custom_bubbles",true));
        L.add(Item.toggle("Bubble Drop Shadow","Subtle shadow behind bubbles","bubble_shadow",true));
        L.add(Item.nav("Corner Radius","Bubble roundness: "+NxygramSettings.getCornerRadius()+"dp",()->toast("Open ThemeStudio → corner section")));

        // GHOST MODE
        L.add(Item.section("👻 Ghost Mode & Privacy"));
        L.add(Item.toggle("Ghost Mode","Master switch for all ghost controls",NxygramSettings.KEY_GHOST_MODE,false));
        L.add(Item.toggle("Hide Typing Indicator","Others won't see 'typing…'",NxygramSettings.KEY_GHOST_TYPING,false));
        L.add(Item.toggle("Hide Online Status","Appear offline while using Nxygram",NxygramSettings.KEY_GHOST_ONLINE,false));
        L.add(Item.toggle("Hide Recording Indicator","No indicator when recording voice",NxygramSettings.KEY_GHOST_RECORDING,false));
        L.add(Item.toggle("Hide Read Receipts","Don't send read receipts (1-on-1 chats)",NxygramSettings.KEY_GHOST_READ_RECEIPTS,false));
        L.add(Item.toggle("Hide Story Views","Don't report story views to senders","ghost_story_views",false));
        L.add(Item.info("ℹ️ Limitation","These suppress client-side calls only. Telegram's server may still briefly mark you online when you send a message."));

        // VIEW ONCE & SCREENSHOT
        L.add(Item.section("🔓 View Once & Screenshot"));
        L.add(Item.toggle("View Once Bypass","Save and re-open view-once photos and videos","view_once_bypass",true));
        L.add(Item.toggle("Allow Screenshots","Screenshots anywhere in Nxygram (clears FLAG_SECURE)","allow_screenshots",true));
        L.add(Item.toggle("Bypass Forward Restriction","Forward from channels/chats that restrict forwarding","bypass_forward_restriction",true));
        L.add(Item.toggle("Bypass Copy Restriction","Copy text from restricted chats","bypass_copy_restriction",true));
        L.add(Item.nav("View Once Gallery","Browse saved view-once photos and videos",()->toast("Open ViewOnceGalleryActivity")));
        L.add(Item.info("ℹ️ Client-side only","These work on your device only. The sender still sees the message was opened once."));

        // ANTI-DELETE
        L.add(Item.section("🛡️ Anti-Delete & Anti-Edit"));
        L.add(Item.toggle("Anti-Delete","Preserve deleted messages locally",NxygramSettings.KEY_ANTI_DELETE,true));
        L.add(Item.toggle("Anti-Edit","Save original text before edits",NxygramSettings.KEY_ANTI_EDIT,true));
        L.add(Item.toggle("Notify on Delete/Edit","Alert when a saved message changes",NxygramSettings.KEY_ANTI_DELETE_NOTIFY,true));
        L.add(Item.nav("View Deleted Messages","Browse preserved deleted and edited messages",()->{
            Intent i=new Intent(ctx,DeletedMessagesActivity.class);
            i.putExtra("chat_id",0L); i.putExtra("account_id",0); startActivity(i);
        }));
        L.add(Item.nav("Clear Anti-Delete History","Remove all locally saved messages",()->{
            AntiDeleteManager.getInstance(ctx).clearAll(0); toast("Cleared");
        }));

        // TRANSLATOR
        L.add(Item.section("🌐 Universal Translator"));
        L.add(Item.toggle("Enable Translator","Powered by Nxygram AI — no API key needed",NxygramSettings.KEY_TRANSLATOR,false));
        L.add(Item.toggle("Translate Incoming","Auto-translate received messages",NxygramSettings.KEY_TRANS_INCOMING,true));
        L.add(Item.toggle("Translate Outgoing","Translate your messages before sending",NxygramSettings.KEY_TRANS_OUTGOING,false));
        L.add(Item.toggle("Preview Before Sending","Always confirm translation before send",NxygramSettings.KEY_TRANS_PREVIEW,true));
        L.add(Item.toggle("Reply Language Matching","Auto-translate replies into sender's language",NxygramSettings.KEY_TRANS_REPLY_MATCH,true));
        L.add(Item.toggle("Group Translation","Translate messages from all group members",NxygramSettings.KEY_TRANS_GROUP,true));
        L.add(Item.nav("Target Language","Translate to: "+NxygramSettings.getTransTargetLang().toUpperCase(),()->toast("Open LanguagePickerActivity")));

        // AI
        L.add(Item.section("🤖 AI Assistant"));
        L.add(Item.toggle("AI Assistant","Smart writing, grammar, tone, web search — all built-in",NxygramSettings.KEY_AI_ASSISTANT,false));
        L.add(Item.toggle("Smart Reply Suggestions","3-tap reply chips above message input",NxygramSettings.KEY_SMART_REPLY,true));
        L.add(Item.toggle("Conversation Summarizer","Summarize long chats with one tap",NxygramSettings.KEY_AI_SUMMARIZER,false));
        L.add(Item.toggle("Grammar Correction","AI grammar fix chip in input bar",NxygramSettings.KEY_AI_GRAMMAR,false));
        L.add(Item.toggle("Tone Changer","Rewrite as Formal/Casual/Shorter/Friendlier",NxygramSettings.KEY_AI_TONE,false));
        L.add(Item.nav("Open AI Chat","Chat with Nxygram AI, translate, search the web",()->startActivity(new Intent(ctx,NxygramAiChatActivity.class))));
        L.add(Item.info("🔍 Web Search","Toggle web search inside the AI Chat screen for live results."));

        // REVERSE CHAT
        L.add(Item.section("⇅ Reverse Chat"));
        L.add(Item.toggle("Reverse Chat (Global)","Your sent messages appear as if from the recipient","reverse_chat_global",false));
        L.add(Item.info("Per-chat toggle","Tap ⇅ in any chat toolbar to toggle for that chat only. Messages sent while ON stay reversed permanently. New messages after OFF are normal."));

        // APP LOCK
        L.add(Item.section("🔒 App Lock"));
        L.add(Item.toggle("App Lock","Require PIN or biometrics to open Nxygram",NxygramSettings.KEY_APP_LOCK,false));
        L.add(Item.nav("Lock Type","Current: "+NxygramSettings.getAppLockType(),()->toast("Open AppLockTypeActivity")));
        L.add(Item.nav("Auto-lock Timer","Lock after "+NxygramSettings.getAppLockTimer()+"s in background",()->toast("Open TimerPickerActivity")));

        // SMART MESSAGING
        L.add(Item.section("💬 Smart Messaging"));
        L.add(Item.toggle("Draft Manager","Persist and restore drafts across all chats",NxygramSettings.KEY_DRAFT_MANAGER,true));
        L.add(Item.toggle("Message Scheduler","Long-press Send to schedule messages",NxygramSettings.KEY_MSG_SCHEDULER,true));
        L.add(Item.nav("Scheduled Messages","View and cancel queued messages",()->startActivity(new Intent(ctx,SchedulerActivity.class).putExtra("account_id",0))));
        L.add(Item.toggle("Quick Forward","Long-press to quickly forward to recent chats",NxygramSettings.KEY_QUICK_FORWARD,true));

        // ANTI-SPAM
        L.add(Item.section("🚫 Anti-Spam"));
        L.add(Item.toggle("Anti-Spam Filter","Flag suspicious messages automatically",NxygramSettings.KEY_ANTI_SPAM,true));
        L.add(Item.toggle("Filter Suspicious Links","Warn before opening risky domain links",NxygramSettings.KEY_ANTI_SPAM_LINKS,true));

        // STORY TOOLS
        L.add(Item.section("📸 Story Tools"));
        L.add(Item.toggle("Story Tools","Master switch for story features",NxygramSettings.KEY_STORY_TOOLS,true));
        L.add(Item.toggle("Auto-Save Stories","Save permitted stories locally",NxygramSettings.KEY_STORY_SAVE,false));
        L.add(Item.info("ℹ️ Restriction respected","Stories with saving disabled are never saved automatically."));

        // MEDIA & STORAGE
        L.add(Item.section("📦 Media & Storage"));
        L.add(Item.toggle("Auto Download Manager","Control what media downloads automatically",NxygramSettings.KEY_MEDIA_AUTO_DOWNLOAD,true));
        L.add(Item.toggle("Video Compression","Compress videos before sending",NxygramSettings.KEY_MEDIA_COMPRESS_VIDEO,false));
        L.add(Item.nav("Storage Analyzer","Per-chat storage breakdown with clear options",()->startActivity(new Intent(ctx,StorageAnalyzerActivity.class))));

        // ABOUT
        L.add(Item.section("ℹ️ About Nxygram"));
        L.add(Item.info("Nxygram","Your chats. Your style. Your world."));
        L.add(Item.info("Version","1.0.0"));
        L.add(Item.info("AI Powered by","prexzyapis.com"));
        L.add(Item.info("Based on","Telegram Android — GPL-2.0"));
        L.add(Item.nav("Reset All Settings","Restore all defaults",()->{
            NxygramSettings.resetAll(); toast("Reset complete"); recreate();
        }));

        return L;
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    static class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final Context ctx; final List<Item> items;
        Adapter(Context c,List<Item> i){ctx=c;items=i;}
        @Override public int getItemViewType(int p){return items.get(p).type;}
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,int type){
            switch(type){
                case TYPE_SECTION: return new SVH(mkSection(ctx));
                case TYPE_TOGGLE:  return new TVH(mkToggle(ctx));
                case TYPE_NAVIGATE:return new NVH(mkNav(ctx));
                default:           return new IVH(mkInfo(ctx));
            }
        }
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h,int pos){
            Item item=items.get(pos);
            NxygramThemeManager.NxygramTheme t=NxygramThemeManager.getCurrentTheme();
            if(h instanceof SVH){((SVH)h).tv.setText(item.title);}
            else if(h instanceof TVH){
                TVH vh=(TVH)h;
                vh.title.setText(item.title); vh.sub.setText(item.subtitle);
                vh.sw.setOnCheckedChangeListener(null);
                vh.sw.setChecked(NxygramSettings.getBool(item.key,item.def));
                vh.sw.setOnCheckedChangeListener((b,on)->{
                    NxygramSettings.setBool(item.key,on);
                    if(NxygramSettings.KEY_THEME_AMOLED.equals(item.key))
                        NxygramThemeManager.applyCurrentTheme();
                });
                vh.title.setTextColor(t.colorText);
                vh.sub.setTextColor(t.colorTextSecondary);
            } else if(h instanceof NVH){
                NVH vh=(NVH)h;
                vh.title.setText(item.title); vh.sub.setText(item.subtitle);
                vh.title.setTextColor(t.colorText); vh.sub.setTextColor(t.colorTextSecondary);
                vh.itemView.setOnClickListener(v->{if(item.action!=null)item.action.run();});
            } else if(h instanceof IVH){
                ((IVH)h).title.setText(item.title); ((IVH)h).sub.setText(item.subtitle);
            }
        }
        @Override public int getItemCount(){return items.size();}
    }

    static class SVH extends RecyclerView.ViewHolder{TextView tv;SVH(View v){super(v);tv=v.findViewWithTag("title");}}
    static class TVH extends RecyclerView.ViewHolder{TextView title,sub;Switch sw;TVH(View v){super(v);title=v.findViewWithTag("title");sub=v.findViewWithTag("sub");sw=v.findViewWithTag("sw");}}
    static class NVH extends RecyclerView.ViewHolder{TextView title,sub;NVH(View v){super(v);title=v.findViewWithTag("title");sub=v.findViewWithTag("sub");}}
    static class IVH extends RecyclerView.ViewHolder{TextView title,sub;IVH(View v){super(v);title=v.findViewWithTag("title");sub=v.findViewWithTag("sub");}}

    private static View mkSection(Context ctx){
        NxygramThemeManager.NxygramTheme t=NxygramThemeManager.getCurrentTheme();
        TextView tv=new TextView(ctx);tv.setTag("title");
        tv.setPadding(dp(ctx,16),dp(ctx,20),dp(ctx,16),dp(ctx,6));
        tv.setTextSize(11);tv.setTextColor(t.colorTextSecondary);
        tv.setAllCaps(true);tv.setLetterSpacing(0.1f);
        LinearLayout ll=new LinearLayout(ctx);ll.setBackgroundColor(t.colorBackground);ll.addView(tv);return ll;
    }
    private static View mkToggle(Context ctx){
        NxygramThemeManager.NxygramTheme t=NxygramThemeManager.getCurrentTheme();
        LinearLayout ll=new LinearLayout(ctx);ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setPadding(dp(ctx,16),dp(ctx,14),dp(ctx,16),dp(ctx,14));ll.setBackgroundColor(t.colorSurface);
        LinearLayout text=new LinearLayout(ctx);text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        TextView title=new TextView(ctx);title.setTag("title");title.setTextSize(15);
        TextView sub=new TextView(ctx);sub.setTag("sub");sub.setTextSize(12);sub.setPadding(0,dp(ctx,2),0,0);
        text.addView(title);text.addView(sub);
        Switch sw=new Switch(ctx);sw.setTag("sw");
        ll.addView(text);ll.addView(sw);return ll;
    }
    private static View mkNav(Context ctx){
        NxygramThemeManager.NxygramTheme t=NxygramThemeManager.getCurrentTheme();
        LinearLayout ll=new LinearLayout(ctx);ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setPadding(dp(ctx,16),dp(ctx,14),dp(ctx,16),dp(ctx,14));ll.setBackgroundColor(t.colorSurface);
        LinearLayout text=new LinearLayout(ctx);text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        TextView title=new TextView(ctx);title.setTag("title");title.setTextSize(15);
        TextView sub=new TextView(ctx);sub.setTag("sub");sub.setTextSize(12);
        text.addView(title);text.addView(sub);
        TextView arrow=new TextView(ctx);arrow.setText("›");arrow.setTextSize(22);arrow.setTextColor(t.colorTextSecondary);
        ll.addView(text);ll.addView(arrow);return ll;
    }
    private static View mkInfo(Context ctx){
        NxygramThemeManager.NxygramTheme t=NxygramThemeManager.getCurrentTheme();
        LinearLayout ll=new LinearLayout(ctx);ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(ctx,16),dp(ctx,10),dp(ctx,16),dp(ctx,10));ll.setBackgroundColor(t.colorBackground);
        TextView title=new TextView(ctx);title.setTag("title");title.setTextSize(13);title.setTextColor(0xFF9E9EBB);
        TextView sub=new TextView(ctx);sub.setTag("sub");sub.setTextSize(12);sub.setTextColor(0xFF666680);sub.setPadding(0,dp(ctx,2),0,0);
        ll.addView(title);ll.addView(sub);return ll;
    }
    private static int dp(Context ctx,int dp){return(int)(dp*ctx.getResources().getDisplayMetrics().density);}
    private void toast(String m){Toast.makeText(this,m,Toast.LENGTH_SHORT).show();}
}
