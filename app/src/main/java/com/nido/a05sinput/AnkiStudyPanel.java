package com.nido.a05sinput;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.Locale;

/** Study tools: session and daily stats, 14-day chart, goal, focus timer, and answer nudge. */
final class AnkiStudyPanel {
    private final Context context;
    private final AnkiUi ui;
    private final StudyCoach coach;
    private final GlassHid runtime;

    AnkiStudyPanel(Context context, AnkiUi ui, StudyCoach coach, GlassHid runtime) {
        this.context = context;
        this.ui = ui;
        this.coach = coach;
        this.runtime = runtime;
    }

    PopupWindow build() {
        LinearLayout card = ui.column();
        card.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(6));
        ui.panel(card, Palette.YELLOW);

        TextView title = ui.label("STUDY", 15, Palette.YELLOW);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(28)));

        TextView summary = ui.label("", 12, Palette.PAPER);
        summary.setLineSpacing(ui.dp(2), 1f);
        card.addView(summary, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        StudyViews.HistoryBars bars = new StudyViews.HistoryBars(context,
                ui.oled ? 0xFF2E4A70 : 0xFF6E8FBF, Palette.GREEN, ui.textColor(Palette.PAPER));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(96));
        barParams.setMargins(0, ui.dp(6), 0, ui.dp(8));
        card.addView(bars, barParams);

        TextView goal = stepper(card, "Daily goal", () -> coach.setGoal(StudyCoach.step(StudyCoach.GOAL_STEPS, coach.goal(), -1)),
                () -> coach.setGoal(StudyCoach.step(StudyCoach.GOAL_STEPS, coach.goal(), 1)));
        TextView focus = stepper(card, "Focus block", () -> coach.setFocusMinutes(StudyCoach.step(StudyCoach.FOCUS_STEPS, coach.focusMinutes(), -1)),
                () -> coach.setFocusMinutes(StudyCoach.step(StudyCoach.FOCUS_STEPS, coach.focusMinutes(), 1)));
        TextView rest = stepper(card, "Break", () -> coach.setBreakMinutes(StudyCoach.step(StudyCoach.BREAK_STEPS, coach.breakMinutes(), -1)),
                () -> coach.setBreakMinutes(StudyCoach.step(StudyCoach.BREAK_STEPS, coach.breakMinutes(), 1)));
        TextView nudge = stepper(card, "Answer nudge", () -> coach.setNudgeSeconds(StudyCoach.step(StudyCoach.NUDGE_STEPS, coach.nudgeSeconds(), -1)),
                () -> coach.setNudgeSeconds(StudyCoach.step(StudyCoach.NUDGE_STEPS, coach.nudgeSeconds(), 1)));

        LinearLayout controls = ui.row();
        Button focusButton = ui.topButton("", Palette.GREEN);
        focusButton.setOnClickListener(v -> {
            if (coach.focusPhase() == FocusTimer.Phase.OFF) coach.startFocus();
            else coach.stopFocus();
        });
        controls.addView(focusButton, ui.slot(1));
        Button newSession = ui.topButton("NEW SESSION", Palette.BLUE);
        newSession.setOnClickListener(v -> coach.newSession());
        controls.addView(newSession, ui.slot(1));
        card.addView(controls, ui.rowParams(42));

        Button clear = ui.topButton("CLEAR HISTORY", Palette.CORAL);
        boolean[] armed = {false};
        clear.setOnClickListener(v -> {
            if (!armed[0]) {
                armed[0] = true;
                clear.setText("TAP AGAIN TO CLEAR ALL HISTORY");
            } else {
                armed[0] = false;
                coach.clearHistory();
                clear.setText("CLEAR HISTORY");
            }
        });
        card.addView(clear, ui.rowParams(40));

        Runnable refresh = () -> {
            summary.setText(summaryText());
            bars.setDays(coach.history().lastDays(LocalDate.now(), 14), coach.goal());
            goal.setText(coach.goal() == 0 ? "OFF" : String.valueOf(coach.goal()));
            focus.setText(coach.focusMinutes() == 0 ? "OFF" : coach.focusMinutes() + " min");
            rest.setText(coach.breakMinutes() + " min");
            nudge.setText(coach.nudgeSeconds() == 0 ? "OFF" : coach.nudgeSeconds() + " s");
            focusButton.setText(coach.focusPhase() == FocusTimer.Phase.OFF ? "START FOCUS"
                    : coach.focusPhase() == FocusTimer.Phase.FOCUS ? "STOP · " + clock(coach.focusRemainingMs())
                    : "BREAK · " + clock(coach.focusRemainingMs()));
        };
        refresh.run();
        StudyCoach.Listener listener = new StudyCoach.Listener() {
            @Override public void onCoachChanged() {
                refresh.run();
            }

            @Override public void onCoachAlert(String message) {
                refresh.run();
            }
        };
        card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                coach.addListener(listener);
                refresh.run();
            }

            @Override public void onViewDetachedFromWindow(View view) {
                coach.removeListener(listener);
            }
        });
        return ui.popup(card, 340, 420);
    }

    private TextView stepper(LinearLayout card, String label, Runnable minus, Runnable plus) {
        LinearLayout row = ui.row();
        TextView name = ui.label(label, 12, Palette.PAPER);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        name.setGravity(Gravity.CENTER_VERTICAL);
        Button less = ui.topButton("−", Palette.CORAL);
        less.setOnClickListener(v -> minus.run());
        row.addView(less, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(38)));
        TextView value = ui.label("", 12, Palette.YELLOW);
        value.setGravity(Gravity.CENTER);
        row.addView(value, new LinearLayout.LayoutParams(ui.dp(70), LinearLayout.LayoutParams.MATCH_PARENT));
        Button more = ui.topButton("+", Palette.GREEN);
        more.setOnClickListener(v -> plus.run());
        row.addView(more, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(38)));
        card.addView(row, ui.rowParams(40));
        return value;
    }

    private String summaryText() {
        ReviewSession session = coach.session();
        StringBuilder text = new StringBuilder();
        if (session.total() > 0) {
            text.append("This session: ").append(session.total()).append(" cards in ")
                    .append(Math.max(1, session.activeMs() / 60_000)).append(" min");
            double pace = session.cardsPerMinute();
            if (pace > 0) text.append(String.format(Locale.US, " · %.1f / min", pace));
            text.append("\nAgain ").append(session.count(1)).append(" · Hard ").append(session.count(2))
                    .append(" · Good ").append(session.count(3)).append(" · Easy ").append(session.count(4))
                    .append(" · ").append(Math.round(session.againRate() * 100)).append("% again");
        } else {
            text.append("No reviews in this session yet.");
        }
        text.append("\nToday ").append(coach.todayTotal());
        if (coach.goal() > 0) text.append(" of ").append(coach.goal());
        text.append(" · Streak ").append(coach.streak()).append(coach.streak() == 1 ? " day" : " days")
                .append(" · Best ").append(coach.history().bestDay());
        AnkiLiveInfo info = runtime.liveInfo();
        if (info != null && info.connected()) {
            text.append("\nAnki desktop: ");
            if (info.reviewing()) {
                text.append(info.deck.isEmpty() ? "reviewing" : info.deck).append(" · ")
                        .append(info.newCount).append(" new · ").append(info.learnCount).append(" learning · ")
                        .append(info.reviewCount).append(" due");
            } else {
                text.append("not reviewing");
            }
            if (info.reviewedToday >= 0) text.append(" · ").append(info.reviewedToday).append(" reviewed today");
        }
        text.append("\nCounts come from remote presses that reached Anki.");
        return text.toString();
    }

    private static String clock(long ms) {
        long seconds = (ms + 999) / 1000;
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
}
