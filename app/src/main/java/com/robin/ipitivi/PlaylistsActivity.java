package com.robin.ipitivi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Mes listes: every playlist the user added, which one is in use, add / edit / delete. */
public class PlaylistsActivity extends Activity implements Repo.Listener {
    private LinearLayout list;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Repo.I.init(this);
        Ui.init(this);
        prefs = Repo.I.prefs();
        list = Ui.column(this);
        setContentView(Ui.page(this, "Mes listes",
                "Une « liste » est ce que ton fournisseur IPTV t'a donné : un lien web ou un compte. Tu peux en garder plusieurs.",
                Ui.scroller(this, list), v -> finish()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Repo.I.setListener(this);
        rebuild(-1);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Repo.I.setListener(null);
    }

    @Override
    public void onChanged() {
        rebuild(-2);
    }

    /** @param focusRow row to focus, -1 = the list in use (or the add button), -2 = keep what is focused. */
    private void rebuild(int focusRow) {
        int keep = -1;
        if (focusRow == -2) {
            View f = getCurrentFocus();
            if (f != null) keep = list.indexOfChild(f);
        }
        list.removeAllViews();

        TextView add = Ui.button(this, "＋   Ajouter une liste", true);
        add.setOnClickListener(v -> startActivity(new Intent(this, PlaylistEditActivity.class)));
        list.addView(add, Ui.fullWidth(this, 0));

        Source active = prefs.active();
        List<Source> all = prefs.sources();
        int activeChild = 0;
        for (final Source s : all) {
            boolean isActive = active != null && s.id.equals(active.id);
            LinearLayout row = Ui.row(this);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.text(this, s.name.isEmpty() ? "Ma liste" : s.name, 30, Ui.TEXT);
            name.setTypeface(Ui.MEDIUM);
            name.setSingleLine(true);
            texts.addView(name);
            String sub = s.kindLabel();
            TextView subView = Ui.text(this, sub, 19, Ui.MUTED);
            texts.addView(subView);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (isActive) {
                String count = Repo.I.all.isEmpty() ? (Repo.I.loading ? "chargement…" : "") : Repo.I.all.size() + " chaînes";
                TextView badge = Ui.text(this, "✓  En cours" + (count.isEmpty() ? "" : "  ·  " + count), 20, Ui.GREEN);
                badge.setTypeface(Ui.MEDIUM);
                row.addView(badge);
                activeChild = list.getChildCount();
            }
            row.setOnClickListener(v -> options(s));
            list.addView(row);
        }
        if (all.isEmpty()) {
            TextView none = Ui.text(this, "Tu n'as encore aucune liste.\nAppuie sur « Ajouter une liste » pour commencer.",
                    22, Ui.MUTED);
            list.addView(none, Ui.fullWidth(this, 24));
        }

        int target = focusRow >= 0 ? focusRow : (keep >= 0 ? keep : activeChild);
        final View t = list.getChildAt(Math.min(target, list.getChildCount() - 1));
        if (t != null) t.post(t::requestFocus);
    }

    private void options(final Source s) {
        Source active = prefs.active();
        final boolean isActive = active != null && active.id.equals(s.id);
        List<String> labels = new ArrayList<>();
        if (!isActive) labels.add("Utiliser cette liste");
        labels.add("Modifier");
        labels.add("Supprimer");
        final String[] arr = labels.toArray(new String[0]);
        Dialogs.choice(this, s.name.isEmpty() ? "Ma liste" : s.name, s.kindLabel(), arr, i -> {
            String l = arr[i];
            if (l.equals("Utiliser cette liste")) {
                Repo.I.switchTo(s.id);
                Toast.makeText(this, "Liste « " + s.name + " » en cours de chargement", Toast.LENGTH_SHORT).show();
                rebuild(-2);
            } else if (l.equals("Modifier")) {
                startActivity(new Intent(this, PlaylistEditActivity.class)
                        .putExtra(PlaylistEditActivity.EXTRA_ID, s.id));
            } else {
                Dialogs.confirm(this, "Supprimer « " + s.name + " » ?", "Oui, supprimer", () -> {
                    prefs.remove(s.id);
                    Repo.I.forget(s.id);
                    if (isActive) Repo.I.switchTo(prefs.activeId());
                    Repo.I.bump();
                    Toast.makeText(this, "Liste supprimée", Toast.LENGTH_SHORT).show();
                    rebuild(-1);
                });
            }
        });
    }
}
