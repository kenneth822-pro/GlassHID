package com.nido.a05sinput;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared builders for the Anki remote: neo-brutalist on paper, or outlined on OLED black. */
final class AnkiUi {
    private final Context context;
    private final NeoUi neo;
    boolean oled;

    AnkiUi(Context context, NeoUi neo, boolean oled) {
        this.context = context;
        this.neo = neo;
        this.oled = oled;
    }

    int dp(int value) {
        return neo.dp(value);
    }

    int background() {
        return oled ? Color.BLACK : Palette.PAPER;
    }

    int textColor(int accent) {
        return oled ? accent : Palette.INK;
    }

    /** Large review button (Flip, grades). */
    Button mainButton(String label, int accent) {
        if (!oled) return neo.button(label, accent);
        Button button = plainButton(label, accent);
        button.setBackground(states(Color.rgb(10, 10, 10), accent, dp(2), dp(8)));
        return button;
    }

    /** Header and panel button. */
    Button topButton(String label, int accent) {
        Button button;
        if (!oled) {
            button = neo.button(label, accent);
        } else {
            button = plainButton(label, accent);
            button.setTextSize(11);
            GradientDrawable pressed = shape(Color.rgb(28, 28, 28), accent, dp(1), dp(6));
            GradientDrawable normal = shape(Color.rgb(14, 14, 14), Color.rgb(45, 45, 45), dp(1), dp(6));
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed}, pressed);
            states.addState(new int[]{}, normal);
            button.setBackground(states);
        }
        button.setSingleLine(true);
        return button;
    }

    TextView label(String value, int sp, int accent) {
        TextView view = neo.text(value, sp);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(textColor(accent));
        return view;
    }

    /** Background for touch surfaces (scroll strip, tap pad, swipe pad). */
    void surface(View view, boolean pressed) {
        if (oled) {
            view.setBackground(shape(pressed ? Color.rgb(18, 32, 54) : Color.rgb(8, 14, 24),
                    pressed ? Color.rgb(60, 110, 190) : Color.rgb(30, 60, 110), dp(2), dp(8)));
        } else {
            view.setBackground(neo.rounded(pressed ? Color.rgb(104, 157, 222) : Palette.BLUE));
        }
    }

    /** Popup card background. */
    void panel(View view, int accent) {
        if (oled) view.setBackground(shape(Color.rgb(6, 6, 6), Color.rgb(60, 60, 60), dp(1), dp(10)));
        else view.setBackground(neo.background(accent));
    }

    /** Status chip background; green when a host is live. */
    void chip(TextView view, boolean live) {
        if (oled) {
            int accent = live ? Palette.GREEN : Palette.YELLOW;
            view.setTextColor(accent);
            view.setBackground(shape(Color.rgb(14, 14, 14), live ? accent : Color.rgb(45, 45, 45), dp(1), dp(6)));
        } else {
            view.setTextColor(Palette.INK);
            view.setBackground(neo.rounded(live ? Palette.GREEN : Palette.PAPER));
        }
    }

    LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    /** Equal-width slot in a header row. */
    LinearLayout.LayoutParams slot(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    /** Review button cell with a small gutter. */
    LinearLayout.LayoutParams cell(int width, int height, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    LinearLayout.LayoutParams rowParams(int heightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp));
        params.bottomMargin = dp(4);
        return params;
    }

    /** A scrollable popup card that never grows taller than {@code maxHeightDp}. */
    PopupWindow popup(LinearLayout card, int widthDp, int maxHeightDp) {
        int maxHeight = dp(maxHeightDp);
        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(card);
        PopupWindow popup = new PopupWindow(scroll, dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(neo.rounded(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));
        return popup;
    }

    private Button plainButton(String label, int accent) {
        Button button = new Button(context);
        button.setText(label);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(accent);
        button.setTransformationMethod(null);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setStateListAnimator(null);
        return button;
    }

    private StateListDrawable states(int fill, int accent, int stroke, int radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, shape(Color.rgb(28, 28, 28), accent, stroke, radius));
        states.addState(new int[]{}, shape(fill, accent, stroke, radius));
        return states;
    }

    private static GradientDrawable shape(int fill, int stroke, int strokeWidth, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setStroke(strokeWidth, stroke);
        shape.setCornerRadius(radius);
        return shape;
    }
}
