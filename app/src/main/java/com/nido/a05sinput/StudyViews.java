package com.nido.a05sinput;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;

/** Small custom views for the study coach. */
final class StudyViews {
    private StudyViews() {
    }

    /** A progress ring for the daily goal. */
    static final class GoalRing extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private float fraction;

        GoalRing(Context context, int trackColor, int progressColor) {
            super(context);
            float density = context.getResources().getDisplayMetrics().density;
            for (Paint paint : new Paint[]{track, progress}) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3 * density);
                paint.setStrokeCap(Paint.Cap.ROUND);
            }
            track.setColor(trackColor);
            progress.setColor(progressColor);
        }

        void setFraction(float value) {
            float clamped = Math.max(0f, Math.min(1f, value));
            if (clamped != fraction) {
                fraction = clamped;
                invalidate();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            float inset = track.getStrokeWidth();
            float size = Math.min(getWidth(), getHeight()) - inset * 2;
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;
            bounds.set(left, top, left + size, top + size);
            canvas.drawArc(bounds, 0, 360, false, track);
            if (fraction > 0) canvas.drawArc(bounds, -90, 360 * fraction, false, progress);
        }
    }

    /** Bars for the last days of reviews, with today on the right and an optional goal line. */
    static final class HistoryBars extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint today = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint goalLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float density;
        private int[] totals = new int[0];
        private String[] labels = new String[0];
        private int goal;

        HistoryBars(Context context, int barColor, int todayColor, int textColor) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            bar.setColor(barColor);
            today.setColor(todayColor);
            goalLine.setColor(todayColor);
            goalLine.setStrokeWidth(1.5f * density);
            text.setColor(textColor);
            text.setTextSize(9 * density);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void setDays(List<StudyHistory.Day> days, int goal) {
            totals = new int[days.size()];
            labels = new String[days.size()];
            for (int i = 0; i < days.size(); i++) {
                totals[i] = days.get(i).total();
                labels[i] = days.get(i).date.getDayOfWeek().name().substring(0, 1);
            }
            this.goal = goal;
            setContentDescription("Reviews per day for the last " + days.size() + " days");
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (totals.length == 0) return;
            float labelSpace = 14 * density;
            float valueSpace = 12 * density;
            float chartTop = valueSpace;
            float chartBottom = getHeight() - labelSpace;
            int max = Math.max(goal, 1);
            for (int total : totals) max = Math.max(max, total);
            float slot = getWidth() / (float) totals.length;
            float barWidth = slot * 0.62f;
            for (int i = 0; i < totals.length; i++) {
                float x = i * slot + (slot - barWidth) / 2f;
                float height = (chartBottom - chartTop) * totals[i] / max;
                rect.set(x, chartBottom - Math.max(height, totals[i] > 0 ? 2 * density : 0),
                        x + barWidth, chartBottom);
                canvas.drawRoundRect(rect, 2 * density, 2 * density, i == totals.length - 1 ? today : bar);
                canvas.drawText(labels[i], x + barWidth / 2f, getHeight() - 3 * density, text);
                if (totals[i] > 0 && (i == totals.length - 1 || totals[i] == max))
                    canvas.drawText(String.valueOf(totals[i]), x + barWidth / 2f, rect.top - 2 * density, text);
            }
            if (goal > 0) {
                float y = chartBottom - (chartBottom - chartTop) * goal / max;
                canvas.drawLine(0, y, getWidth(), y, goalLine);
            }
        }
    }
}
