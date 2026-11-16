package com.nxygram;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * MessageScheduler
 * ══════════════════════════════════════════════════════════════
 * Schedule messages to be sent at a specific future time.
 * Persists in SQLite, fires via AlarmManager.
 *
 * Hook points:
 *   - ChatActivity send button long-press → show DateTimePicker → schedule()
 *   - On app start → MessageScheduler.getInstance(ctx).rescheduleAll()
 *   - BroadcastReceiver BOOT_COMPLETED → rescheduleAll()
 *
 * Register in AndroidManifest.xml:
 *   <receiver android:name="com.nxygram.MessageScheduler$ScheduledMessageReceiver"
 *             android:exported="false"/>
 *   <receiver android:name="com.nxygram.MessageScheduler$BootReceiver"
 *             android:exported="true">
 *       <intent-filter>
 *           <action android:name="android.intent.action.BOOT_COMPLETED"/>
 *       </intent-filter>
 *   </receiver>
 *   <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
 *   <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
 */
public class MessageScheduler {

    private static MessageScheduler instance;
    private final Context context;
    private final DbHelper dbHelper;

    public static synchronized MessageScheduler getInstance(Context context) {
        if (instance == null)
            instance = new MessageScheduler(context.getApplicationContext());
        return instance;
    }

    private MessageScheduler(Context context) {
        this.context  = context;
        this.dbHelper = new DbHelper(context);
    }

    // ─── Data model ───────────────────────────────────────────────────────────

    public static class ScheduledMessage {
        public long   id;
        public long   chatId;
        public int    accountId;
        public String messageText;
        public String mediaPath;       // null if text only
        public long   scheduledTime;   // Unix ms
        public boolean sent;
        public String recipientName;   // display only
    }

    // ─── Database ─────────────────────────────────────────────────────────────

    private static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context ctx) { super(ctx, "nxygram_scheduled.db", null, 1); }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS scheduled_messages ("
                    + "id             INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "chat_id        INTEGER NOT NULL,"
                    + "account_id     INTEGER DEFAULT 0,"
                    + "message_text   TEXT,"
                    + "media_path     TEXT,"
                    + "scheduled_time INTEGER NOT NULL,"
                    + "sent           INTEGER DEFAULT 0,"
                    + "recipient_name TEXT"
                    + ")");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }

    // ─── Schedule a message ───────────────────────────────────────────────────

    /**
     * Schedule a message to send at scheduledTimeMs (Unix milliseconds).
     * Returns the scheduled message ID, or -1 on failure.
     */
    public long schedule(long chatId, int accountId, String text,
                          String mediaPath, long scheduledTimeMs, String recipientName) {
        if (scheduledTimeMs <= System.currentTimeMillis()) return -1;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("chat_id",        chatId);
        cv.put("account_id",     accountId);
        cv.put("message_text",   text);
        cv.put("media_path",     mediaPath);
        cv.put("scheduled_time", scheduledTimeMs);
        cv.put("sent",           0);
        cv.put("recipient_name", recipientName);
        long id = db.insert("scheduled_messages", null, cv);

        if (id > 0) setAlarm(id, scheduledTimeMs);
        return id;
    }

    public void cancel(long scheduledId) {
        // Cancel alarm
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ScheduledMessageReceiver.class);
        intent.putExtra("scheduled_id", scheduledId);
        PendingIntent pi = PendingIntent.getBroadcast(context, (int) scheduledId,
                intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null && am != null) am.cancel(pi);

        // Mark cancelled in DB
        dbHelper.getWritableDatabase().delete("scheduled_messages",
                "id=?", new String[]{String.valueOf(scheduledId)});
    }

    public List<ScheduledMessage> getPending(int accountId) {
        List<ScheduledMessage> list = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().query(
                "scheduled_messages", null,
                "sent=0 AND account_id=?",
                new String[]{String.valueOf(accountId)},
                null, null, "scheduled_time ASC");
        while (c.moveToNext()) list.add(fromCursor(c));
        c.close();
        return list;
    }

    public List<ScheduledMessage> getPendingForChat(long chatId, int accountId) {
        List<ScheduledMessage> list = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().query(
                "scheduled_messages", null,
                "sent=0 AND chat_id=? AND account_id=?",
                new String[]{String.valueOf(chatId), String.valueOf(accountId)},
                null, null, "scheduled_time ASC");
        while (c.moveToNext()) list.add(fromCursor(c));
        c.close();
        return list;
    }

    /** Call on app start and after BOOT_COMPLETED to re-register all pending alarms. */
    public void rescheduleAll() {
        Cursor c = dbHelper.getReadableDatabase().query(
                "scheduled_messages", null, "sent=0", null, null, null, null);
        while (c.moveToNext()) {
            ScheduledMessage m = fromCursor(c);
            if (m.scheduledTime > System.currentTimeMillis()) {
                setAlarm(m.id, m.scheduledTime);
            } else {
                // Overdue — send immediately
                fireSend(m);
            }
        }
        c.close();
    }

    // ─── Alarm ────────────────────────────────────────────────────────────────

    private void setAlarm(long scheduledId, long timeMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ScheduledMessageReceiver.class);
        intent.putExtra("scheduled_id", scheduledId);
        PendingIntent pi = PendingIntent.getBroadcast(context, (int) scheduledId,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, timeMs, pi);
        }
    }

    // ─── Fire send ────────────────────────────────────────────────────────────

    void fireSend(ScheduledMessage msg) {
        // Mark as sent in DB
        ContentValues cv = new ContentValues();
        cv.put("sent", 1);
        dbHelper.getWritableDatabase().update("scheduled_messages", cv,
                "id=?", new String[]{String.valueOf(msg.id)});

        // Hook into Telegram's send pipeline:
        // SendMessagesHelper.getInstance(msg.accountId)
        //     .sendMessage(msg.messageText, msg.chatId, null, null, null, null, null, null, 0);
        //
        // This is called from the BroadcastReceiver below.
        // The actual Telegram API call must be made in the receiver where
        // AccountInstance / SendMessagesHelper is available.
        android.util.Log.d("NxygramScheduler",
                "FIRE scheduled msg id=" + msg.id
                + " chatId=" + msg.chatId
                + " text=" + msg.messageText);
    }

    // ─── BroadcastReceiver ────────────────────────────────────────────────────

    public static class ScheduledMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            long scheduledId = intent.getLongExtra("scheduled_id", -1);
            if (scheduledId < 0) return;

            MessageScheduler scheduler = MessageScheduler.getInstance(context);
            Cursor c = scheduler.dbHelper.getReadableDatabase().query(
                    "scheduled_messages", null,
                    "id=? AND sent=0",
                    new String[]{String.valueOf(scheduledId)},
                    null, null, null);

            if (c.moveToFirst()) {
                ScheduledMessage msg = scheduler.fromCursor(c);
                c.close();
                scheduler.fireSend(msg);

                // TODO: hook into Telegram's SendMessagesHelper here:
                // org.telegram.messenger.SendMessagesHelper
                //     .getInstance(msg.accountId)
                //     .sendMessage(msg.messageText, msg.chatId, ...);

                // Show notification that message was sent
                showSentNotification(context, msg);
            } else {
                c.close();
            }
        }

        private void showSentNotification(Context context, ScheduledMessage msg) {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager)
                            context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            String channelId = "nxygram_scheduled";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(
                        channelId, "Scheduled Messages",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(ch);
            }

            android.app.Notification notif =
                    new android.app.Notification.Builder(context, channelId)
                            .setSmallIcon(android.R.drawable.ic_dialog_email)
                            .setContentTitle("Scheduled message sent")
                            .setContentText("To: " + (msg.recipientName != null
                                    ? msg.recipientName : "chat " + msg.chatId))
                            .setAutoCancel(true)
                            .build();
            nm.notify((int) msg.id, notif);
        }
    }

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                MessageScheduler.getInstance(context).rescheduleAll();
            }
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private ScheduledMessage fromCursor(Cursor c) {
        ScheduledMessage m    = new ScheduledMessage();
        m.id                  = c.getLong(c.getColumnIndexOrThrow("id"));
        m.chatId              = c.getLong(c.getColumnIndexOrThrow("chat_id"));
        m.accountId           = c.getInt(c.getColumnIndexOrThrow("account_id"));
        m.messageText         = c.getString(c.getColumnIndexOrThrow("message_text"));
        m.mediaPath           = c.getString(c.getColumnIndexOrThrow("media_path"));
        m.scheduledTime       = c.getLong(c.getColumnIndexOrThrow("scheduled_time"));
        m.sent                = c.getInt(c.getColumnIndexOrThrow("sent")) == 1;
        m.recipientName       = c.getString(c.getColumnIndexOrThrow("recipient_name"));
        return m;
    }
}
