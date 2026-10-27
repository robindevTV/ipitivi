package com.robin.ipitivi;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Catégories: hide the ones you never watch, rename them, put your favourites first. */
public class CategoriesActivity extends Activity implements Repo.Listener {
    private static final String L_HIDE = "Masquer cette catégorie";
    private static final String L_SHOW = "Afficher cette catégorie";
    private static final String L_RENAME = "Renommer";
    private static final String L_UP = "Monter";
    private static final String L_DOWN = "Descendre";

    private LinearLayout list;
    private Prefs prefs;
    private Source src;
    private CategoryPrefs cp;
    private int shownCount = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Repo.I.init(this);
        Ui.init(this);
        prefs = Repo.I.prefs();
        list = Ui.column(this);
        setContentView(Ui.page(this, "Catégories",
                "Appuie sur OK sur une catégorie pour la masquer, la renommer ou la déplacer. Une catégorie masquée disparaît de la liste des chaînes.",
                Ui.scroller(this, list), v -> finish()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Repo.I.setListener(this);
        Repo.I.load(false);
        rebuild(-1);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Repo.I.setListener(null);
    }

    @Override
    public void onChanged() {
        // The list finished loading while this screen was open: draw it once.
        if (Repo.I.all.size() != shownCount) rebuild(-1);
    }

    /** @param focusIndex entry to focus after drawing, or -1 for the first one. */
    private void rebuild(int focusIndex) {
        list.removeAllViews();
        src = prefs.active();
        shownCount = Repo.I.all.size();
        if (src == null || Repo.I.all.isEmpty()) {
            String msg = src == null ? "Ajoute d'abord une liste depuis l'accueil."
                    : (Repo.I.loading ? "Chargement de la liste…" : "Aucune chaîne chargée pour l'instant.");
            list.addView(Ui.text(this, msg, 24, Ui.MUTED), Ui.fullWidth(this, 20));
            return;
        }
        Map<String, List<Channel>> groups = Repo.I.groups();
        cp = prefs.categories(src.id);
        cp.sync(groups.keySet());

        if (cp.hiddenCount() > 0) {
            TextView all = Ui.button(this, "Tout afficher  (" + cp.hiddenCount() + " masquées)", false);
            all.setOnClickListener(v -> {
                cp.showAll();
                save();
                rebuild(0);
            });
            list.addView(all, Ui.fullWidth(this, 0));
        }
        int firstRow = list.getChildCount();
        for (int i = 0; i < cp.entries().size(); i++) {
            final int index = i;
            final CategoryPrefs.Entry e = cp.entries().get(i);
            List<Channel> chans = groups.get(e.original);
            int n = chans == null ? 0 : chans.size();

            LinearLayout row = Ui.row(this);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.text(this, e.display(), 27, e.hidden ? Ui.MUTED : Ui.TEXT);
            name.setTypeface(Ui.MEDIUM);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(name);
            String sub = n + (n > 1 ? " chaînes" : " chaîne");
            if (!e.label.isEmpty()) sub += "  ·  d'origine : " + e.original;
            texts.addView(Ui.text(this, sub, 17, Ui.MUTED));
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView state = Ui.text(this, e.hidden ? "Masquée" : "Affichée", 20, e.hidden ? Ui.ORANGE : Ui.GREEN);
            state.setTypeface(Ui.MEDIUM);
            row.addView(state);
            row.setOnClickListener(v -> options(index));
            list.addView(row);
        }
        int target = focusIndex >= 0 ? firstRow + focusIndex : firstRow;
        final View t = list.getChildAt(Math.min(target, list.getChildCount() - 1));
        if (t != null) t.post(t::requestFocus);
    }

    private void save() {
        prefs.saveCategories(src.id, cp);
        Repo.I.bump();
    }

    private void options(final int index) {
        final CategoryPrefs.Entry e = cp.entries().get(index);
        List<String> labels = new ArrayList<>();
        labels.add(e.hidden ? L_SHOW : L_HIDE);
        labels.add(L_RENAME);
        if (index > 0) labels.add(L_UP);
        if (index < cp.entries().size() - 1) labels.add(L_DOWN);
        final String[] arr = labels.toArray(new String[0]);
        Dialogs.choice(this, e.display(), null, arr, i -> {
            String l = arr[i];
            if (l.equals(L_HIDE) || l.equals(L_SHOW)) {
                e.hidden = !e.hidden;
                save();
                rebuild(index);
            } else if (l.equals(L_RENAME)) {
                Dialogs.input(this, "Nouveau nom (vide = nom d'origine)", e.display(), text -> {
                    e.label = (text.isEmpty() || text.equals(e.original)) ? "" : text;
                    save();
                    rebuild(index);
                    Toast.makeText(this, "Catégorie renommée", Toast.LENGTH_SHORT).show();
                });
            } else if (l.equals(L_UP)) {
                int to = cp.move(index, -1);
                save();
                rebuild(to);
            } else {
                int to = cp.move(index, +1);
                save();
                rebuild(to);
            }
        });
    }
}
