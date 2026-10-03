package com.nido.a05sinput;

import android.content.Context;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

/**
 * Lets users who changed Anki's or AnkiDroid's shortcuts tell the remote which key each
 * action should send. Changes are stored per target app.
 */
final class KeymapEditor {
    private static final int KEYS_PER_ROW = 8;

    private final Context context;
    private final AnkiUi ui;
    private final GlassHid runtime;
    private AnkiKeymap.Target editing;
    private LinearLayout card;

    KeymapEditor(Context context, AnkiUi ui, GlassHid runtime) {
        this.context = context;
        this.ui = ui;
        this.runtime = runtime;
    }

    PopupWindow build() {
        editing = runtime.target();
        card = ui.column();
        card.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(6));
        ui.panel(card, Palette.PAPER);
        showList();
        return ui.popup(card, 360, 420);
    }

    private void showList() {
        card.removeAllViews();
        AnkiKeymap keymap = runtime.commander.keymap();
        LinearLayout top = ui.row();
        Button target = ui.topButton("EDITING: " + targetName(editing), Palette.CORAL);
        target.setOnClickListener(v -> {
            editing = editing == AnkiKeymap.Target.DESKTOP ? AnkiKeymap.Target.ANKIDROID : AnkiKeymap.Target.DESKTOP;
            showList();
        });
        top.addView(target, ui.slot(2));
        Button reset = ui.topButton("RESET ALL", Palette.PAPER);
        reset.setOnClickListener(v -> {
            keymap.resetAll(editing);
            runtime.saveKeymap();
            showList();
        });
        top.addView(reset, ui.slot(1));
        card.addView(top, ui.rowParams(42));

        for (AnkiAction action : AnkiAction.values()) {
            if (action == AnkiAction.MORE_MENU && editing == AnkiKeymap.Target.ANKIDROID) continue;
            LinearLayout row = ui.row();
            TextView name = ui.label(action.label, 12, Palette.PAPER);
            name.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.2f));
            KeyChord chord = keymap.get(action, editing);
            String label = chord == null ? "—" : chord.label() + (keymap.isCustom(action, editing) ? " •" : "");
            Button key = ui.topButton(label, keymap.isCustom(action, editing) ? Palette.YELLOW : Palette.BLUE);
            key.setOnClickListener(v -> {
                KeyChord start = keymap.get(action, editing);
                showPicker(action, start == null ? 0 : start.modifiers, start == null ? 0x2C : start.usage);
            });
            row.addView(key, ui.slot(1));
            card.addView(row, ui.rowParams(38));
        }
        TextView note = ui.label("• = changed from the stock shortcut.", 11, Palette.PAPER);
        card.addView(note, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(24)));
    }

    /** Shows the key picker with a pending, not yet saved, choice. */
    private void showPicker(AnkiAction action, int pendingModifiers, int pendingUsage) {
        card.removeAllViews();
        AnkiKeymap keymap = runtime.commander.keymap();
        int[] modifiers = {pendingModifiers};
        int[] usage = {pendingUsage};

        TextView title = ui.label(action.label + " · " + targetName(editing), 13, Palette.YELLOW);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(28)));
        TextView preview = ui.label("", 16, Palette.GREEN);
        preview.setGravity(Gravity.CENTER);
        card.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(30)));
        Runnable update = () -> preview.setText(KeyChord.of(modifiers[0], usage[0]).label());

        LinearLayout mods = ui.row();
        String[] names = {"CTRL", "SHIFT", "ALT", "WIN"};
        int[] masks = {KeyChord.CTRL, KeyChord.SHIFT, KeyChord.ALT, KeyChord.WIN};
        for (int i = 0; i < names.length; i++) {
            int mask = masks[i];
            Button toggle = ui.topButton(names[i], (modifiers[0] & mask) != 0 ? Palette.GREEN : Palette.PAPER);
            toggle.setOnClickListener(v -> {
                showPicker(action, modifiers[0] ^ mask, usage[0]);
            });
            mods.addView(toggle, ui.slot(1));
        }
        card.addView(mods, ui.rowParams(40));

        List<HidKeys.Key> keys = HidKeys.all();
        LinearLayout row = null;
        for (int i = 0; i < keys.size(); i++) {
            if (i % KEYS_PER_ROW == 0) {
                row = ui.row();
                card.addView(row, ui.rowParams(36));
            }
            HidKeys.Key key = keys.get(i);
            Button button = ui.topButton(shortLabel(key), key.usage == usage[0] ? Palette.GREEN : Palette.PAPER);
            button.setTextSize(10);
            button.setOnClickListener(v -> showPicker(action, modifiers[0], key.usage));
            row.addView(button, ui.slot(1));
        }
        if (row != null) {
            for (int i = keys.size() % KEYS_PER_ROW; i > 0 && i < KEYS_PER_ROW; i++)
                row.addView(new TextView(context), ui.slot(1));
        }

        LinearLayout actions = ui.row();
        Button save = ui.topButton("SAVE", Palette.GREEN);
        save.setOnClickListener(v -> {
            keymap.set(action, editing, KeyChord.of(modifiers[0], usage[0]));
            runtime.saveKeymap();
            showList();
        });
        actions.addView(save, ui.slot(1));
        Button stock = ui.topButton("DEFAULT", Palette.YELLOW);
        stock.setOnClickListener(v -> {
            keymap.set(action, editing, null);
            runtime.saveKeymap();
            showList();
        });
        actions.addView(stock, ui.slot(1));
        Button back = ui.topButton("BACK", Palette.PAPER);
        back.setOnClickListener(v -> showList());
        actions.addView(back, ui.slot(1));
        card.addView(actions, ui.rowParams(42));
        update.run();
    }

    private static String shortLabel(HidKeys.Key key) {
        switch (key.label) {
            case "Backspace": return "Bksp";
            case "Page Up": return "PgUp";
            case "Page Down": return "PgDn";
            case "Insert": return "Ins";
            case "Delete": return "Del";
            case "Enter": return "Ent";
            case "Space": return "Spc";
            case "Right": return "→";
            case "Left": return "←";
            case "Down": return "↓";
            case "Up": return "↑";
            default: return key.label;
        }
    }

    private static String targetName(AnkiKeymap.Target target) {
        return target == AnkiKeymap.Target.ANKIDROID ? "ANKIDROID" : "ANKI DESKTOP";
    }
}
