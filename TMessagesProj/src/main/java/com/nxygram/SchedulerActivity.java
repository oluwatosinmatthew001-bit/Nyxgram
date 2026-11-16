package com.nxygram;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * SchedulerActivity
 * Shows all pending scheduled messages and lets user cancel them.
 *
 * Also provides the static showSchedulePicker() helper used from ChatActivity
 * to pick a date/time before scheduling.
 *
 * Register in AndroidManifest:
 *   <activity android:name="com.nxygram.SchedulerActivity" android:exported="false"/>
 *
 * Launch from NxygramSettings or chat toolbar:
 *   startActivity(new Intent(context, SchedulerActivity.class)
 *       .putExtra("account_id", currentAccount));
 *
 * Schedule from ChatActivity send button long-press:
 *   SchedulerActivity.showSchedulePicker(context, messageText, chatId, recipientName,
 *       accountId, scheduledId -> { if (scheduledId > 0) showToast("Message scheduled"); });
 */
public class SchedulerActivity extends AppCompatActivity {

    private int accountId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NxygramSettings.init(this);
        accountId = getIntent().getIntExtra("account_id", 0);

        setContentView(buildUI());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("📅 Scheduled Messages");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    // ─── UI ───────────────────────────────────────────────────────────────────

    private View buildUI() {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.colorBackground);

        List<MessageScheduler.ScheduledMessage> pending =
                MessageScheduler.getInstance(this).getPending(accountId);

        if (pending.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No scheduled messages.\n\nLong-press the send button in any chat to schedule a message.");
            empty.setTextColor(t.colorTextSecondary);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(32), dp(60), dp(32), dp(60));
            root.addView(empty);
            return root;
        }

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new ScheduledAdapter(this, pending));
        root.addView(rv);

        return root;
    }

    // ─── Static picker helper ─────────────────────────────────────────────────

    public interface ScheduleCallback { void onScheduled(long scheduledId); }

    /**
     * Show date + time picker, then schedule the message.
     * Call from ChatActivity on send button long-press.
     */
    public static void showSchedulePicker(Context context,
                                           String messageText,
                                           long chatId,
                                           String recipientName,
                                           int accountId,
                                           ScheduleCallback callback) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, 1); // default: 1 hour from now

        // Pick date
        DatePickerDialog datePicker = new DatePickerDialog(context,
                (view, year, month, day) -> {
                    cal.set(Calendar.YEAR,  year);
                    cal.set(Calendar.MONTH, month);
                    cal.set(Calendar.DAY_OF_MONTH, day);

                    // Pick time
                    TimePickerDialog timePicker = new TimePickerDialog(context,
                            (tv, hour, minute) -> {
                                cal.set(Calendar.HOUR_OF_DAY, hour);
                                cal.set(Calendar.MINUTE,      minute);
                                cal.set(Calendar.SECOND,      0);

                                long timeMs = cal.getTimeInMillis();
                                if (timeMs <= System.currentTimeMillis()) {
                                    Toast.makeText(context,
                                            "Please pick a future time",
                                            Toast.LENGTH_SHORT).show();
                                    callback.onScheduled(-1);
                                    return;
                                }

                                // Confirm dialog
                                String formatted = DateFormat.format(
                                        "MMM dd, yyyy 'at' hh:mm a", cal).toString();
                                new AlertDialog.Builder(context)
                                        .setTitle("Schedule Message")
                                        .setMessage("Send to " + recipientName
                                                + "\n\n\"" + messageText + "\"\n\n"
                                                + "At: " + formatted)
                                        .setPositiveButton("Schedule", (d, w) -> {
                                            long id = MessageScheduler.getInstance(context)
                                                    .schedule(chatId, accountId, messageText,
                                                            null, timeMs, recipientName);
                                            if (id > 0) {
                                                Toast.makeText(context,
                                                        "Scheduled for " + formatted,
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                            callback.onScheduled(id);
                                        })
                                        .setNegativeButton("Cancel", (d, w) -> callback.onScheduled(-1))
                                        .show();
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE), true);
                    timePicker.show();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));

        datePicker.getDatePicker().setMinDate(System.currentTimeMillis());
        datePicker.show();
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    class ScheduledAdapter extends RecyclerView.Adapter<ScheduledAdapter.VH> {
        private final Context ctx;
        private final List<MessageScheduler.ScheduledMessage> items;

        ScheduledAdapter(Context ctx, List<MessageScheduler.ScheduledMessage> items) {
            this.ctx   = ctx;
            this.items = items;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(makeItemView(ctx));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            MessageScheduler.ScheduledMessage m = items.get(pos);
            NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

            h.recipient.setText("To: " + (m.recipientName != null
                    ? m.recipientName : "Chat " + m.chatId));
            h.text.setText(m.messageText != null ? m.messageText : "[Media]");
            h.time.setText("⏰ " + DateFormat.format("MMM dd, yyyy 'at' hh:mm a",
                    new Date(m.scheduledTime)));
            h.recipient.setTextColor(t.colorPrimary);
            h.text.setTextColor(t.colorText);
            h.time.setTextColor(t.colorTextSecondary);

            h.cancelBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(ctx)
                        .setTitle("Cancel Scheduled Message")
                        .setMessage("Remove this scheduled message?")
                        .setPositiveButton("Cancel Message", (d, w) -> {
                            MessageScheduler.getInstance(ctx).cancel(m.id);
                            items.remove(pos);
                            notifyItemRemoved(pos);
                            Toast.makeText(ctx, "Cancelled", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Keep", null)
                        .show();
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView recipient, text, time, cancelBtn;
            VH(View v) {
                super(v);
                recipient = v.findViewWithTag("recipient");
                text      = v.findViewWithTag("text");
                time      = v.findViewWithTag("time");
                cancelBtn = v.findViewWithTag("cancel");
            }
        }
    }

    private static View makeItemView(Context ctx) {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        int d = (int) ctx.getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(t.colorSurface);
        card.setPadding(16*d, 12*d, 16*d, 12*d);

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView recipient = new TextView(ctx); recipient.setTag("recipient");
        recipient.setTextSize(14);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        recipient.setLayoutParams(rp);

        TextView cancel = new TextView(ctx); cancel.setTag("cancel");
        cancel.setText("✕ Cancel");
        cancel.setTextSize(12);
        cancel.setTextColor(0xFFFF5252);
        cancel.setPadding(8*d, 4*d, 4*d, 4*d);

        header.addView(recipient);
        header.addView(cancel);

        TextView text = new TextView(ctx); text.setTag("text");
        text.setTextSize(14);
        text.setPadding(0, 4*d, 0, 4*d);
        text.setMaxLines(2);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView time = new TextView(ctx); time.setTag("time");
        time.setTextSize(12);

        View div = new View(ctx);
        div.setBackgroundColor(t.colorDivider);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        card.addView(header);
        card.addView(text);
        card.addView(time);
        card.addView(div);
        return card;
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }
}
