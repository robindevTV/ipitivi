package com.robin.ipitivi;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Plain-language pop-ups: a title and big buttons, nothing else. */
final class Dialogs {
    private Dialogs() {}

    interface OnIndex { void run(int index); }

    interface OnText { void run(String text); }

    private static Dialog base(Activity a, String title, String message, LinearLayout[] boxOut) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.glassPane(a, 0xE6262A48, 0xE61A1C32, 40, 0x59FFFFFF, 1));
        box.setPadding(Ui.dp(a, 36), Ui.dp(a, 32), Ui.dp(a, 36), Ui.dp(a, 28));
        TextView t = Ui.text(a, title, 28, Ui.TEXT);
        t.setTypeface(Ui.LIGHT);
        t.setGravity(Gravity.CENTER);
        box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (message != null) {
            TextView m = Ui.text(a, message, 18, Ui.MUTED);
            m.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mp.topMargin = Ui.dp(a, 8);
            box.addView(m, mp);
        }
        d.setContentView(box);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().setDimAmount(0.7f);
        boxOut[0] = box;
        return d;
    }

    private static void addButton(Activity a, LinearLayout box, final Dialog d, String label, boolean primary,
                                  final Runnable action) {
        TextView b = Ui.button(a, label, primary);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                if (action != null) action.run();
            }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(a, 12);
        box.addView(b, p);
    }

    private static void open(Activity a, Dialog d) {
        d.show();
        d.getWindow().setLayout(Ui.dp(a, 620), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Buttons for each label (the first is highlighted) and a last "Annuler"; calls back with the index. */
    static void choice(final Activity a, String title, String message, String[] labels, final OnIndex cb) {
        LinearLayout[] holder = new LinearLayout[1];
        final Dialog d = base(a, title, message, holder);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            addButton(a, holder[0], d, labels[i], i == 0, new Runnable() {
                @Override public void run() { cb.run(idx); }
            });
        }
        addButton(a, holder[0], d, "Annuler", false, null);
        open(a, d);
        if (holder[0].getChildCount() > 1) {
            View first = holder[0].getChildAt(message != null ? 2 : 1);
            if (first != null) first.requestFocus();
        }
    }

    /** A yes/no question; the "yes" button is the first, "Annuler" the last. */
    static void confirm(Activity a, String question, String yesLabel, final Runnable yes) {
        choice(a, question, null, new String[] {yesLabel}, new OnIndex() {
            @Override public void run(int i) { yes.run(); }
        });
    }

    /** One text field with Enregistrer / Annuler. */
    static void input(final Activity a, String title, String initial, final OnText cb) {
        LinearLayout[] holder = new LinearLayout[1];
        final Dialog d = base(a, title, null, holder);
        final EditText e = new EditText(a);
        e.setText(initial);
        e.setSingleLine(true);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(24 * Ui.scale);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setBackground(Ui.glass(a, Ui.CARD_HI, 22));
        e.setPadding(Ui.dp(a, 22), Ui.dp(a, 14), Ui.dp(a, 22), Ui.dp(a, 14));
        e.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = Ui.dp(a, 18);
        holder[0].addView(e, ep);
        addButton(a, holder[0], d, "Enregistrer", true, new Runnable() {
            @Override public void run() { cb.run(e.getText().toString().trim()); }
        });
        addButton(a, holder[0], d, "Annuler", false, null);
        open(a, d);
        e.requestFocus();
    }
}
