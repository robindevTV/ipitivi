package com.robin.ipitivi;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Calendar;
import java.util.List;

/** Home: one big "Reprendre" button, four big cards, nothing else to understand. */
public class MainActivity extends Activity implements Repo.Listener {
    private View hero;
    private TextView heroLabel, heroTitle, heroSub;
    private TextView subChannels, subFavs, subLists, subCats, statusView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Repo.I.init(this);
        Ui.init(this);
        build();
    }

    private void build() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(Ui.wallpaper());
        page.setPadding(Ui.dp(this, 72), Ui.dp(this, 44), Ui.dp(this, 72), Ui.dp(this, 32));
        page.setClipChildren(false);
        page.setClipToPadding(false);

        // header: greeting + name on the left, clock on the right
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView hello = Ui.text(this, greeting(), 24, Ui.MUTED);
        hello.setTypeface(Ui.MEDIUM);
        left.addView(hello);
        TextView name = Ui.text(this, "Ipitivi", 62, Ui.TEXT);
        name.setTypeface(Ui.LIGHT);
        left.addView(name);
        head.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(58 * Ui.scale);
        clock.setTextColor(Ui.TEXT);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        right.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextSize(21 * Ui.scale);
        date.setTextColor(Ui.MUTED);
        date.setGravity(Gravity.END);
        right.addView(date);
        head.addView(right);
        page.addView(head);

        // hero
        hero = card(0, 0, 40);
        ((LinearLayout) hero).setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(Ui.sdp(this, 40), Ui.sdp(this, 30), Ui.sdp(this, 40), Ui.sdp(this, 30));
        heroLabel = Ui.text(this, "", 17, 0xCCFFFFFF);
        heroLabel.setTypeface(Ui.MEDIUM);
        heroLabel.setLetterSpacing(0.12f);
        heroTitle = Ui.text(this, "", 46, Ui.TEXT);
        heroTitle.setTypeface(Ui.LIGHT);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroSub = Ui.text(this, "", 21, 0xCCFFFFFF);
        ((LinearLayout) hero).addView(heroLabel);
        ((LinearLayout) hero).addView(heroTitle);
        ((LinearLayout) hero).addView(heroSub);
        hero.setOnClickListener(v -> onHero());
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Ui.dp(this, 30);
        page.addView(hero, hp);

        // four cards
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        subChannels = tile(row, "▶", "Chaînes", () -> startActivity(new Intent(this, ChannelsActivity.class)));
        subFavs = tile(row, "★", "Favoris", () -> startActivity(new Intent(this, ChannelsActivity.class)
                .putExtra(ChannelsActivity.EXTRA_START, ChannelsActivity.START_FAVS)));
        subLists = tile(row, "☰", "Mes listes", () -> startActivity(new Intent(this, PlaylistsActivity.class)));
        subCats = tile(row, "◧", "Catégories", () -> startActivity(new Intent(this, CategoriesActivity.class)));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = Ui.dp(this, 26);
        page.addView(row, rp);

        // footer
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        statusView = Ui.text(this, "", 18, Ui.MUTED);
        statusView.setSingleLine(true);
        statusView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        foot.addView(statusView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView settings = Ui.button(this, "Réglages", false);
        settings.setOnClickListener(v -> settings());
        foot.addView(settings);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.topMargin = Ui.dp(this, 22);
        page.addView(foot, fp);

        setContentView(page);
    }

    /** A rounded card that is focusable and lifts when focused. */
    private LinearLayout card(int top, int bottom, float radius) {
        LinearLayout c = new LinearLayout(this);
        c.setBackground(Ui.cardBg(this, top, bottom, radius));
        c.setFocusable(true);
        c.setClickable(true);
        c.setOnFocusChangeListener((v, f) -> Ui.focusAnim(v, f, 1.06f));
        return c;
    }

    /** One of the four cards; returns its small grey caption so it can be updated. */
    private TextView tile(LinearLayout row, String symbol, String label, final Runnable action) {
        LinearLayout t = card(0, 0, 34);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Ui.sdp(this, 18), Ui.sdp(this, 28), Ui.sdp(this, 18), Ui.sdp(this, 26));
        TextView s = Ui.text(this, symbol, 46, Ui.TEXT);
        s.setGravity(Gravity.CENTER);
        TextView l = Ui.text(this, label, 28, Ui.TEXT);
        l.setTypeface(Ui.MEDIUM);
        l.setGravity(Gravity.CENTER);
        l.setSingleLine(true);
        TextView sub = Ui.text(this, "", 18, Ui.MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setSingleLine(true);
        t.addView(s);
        t.addView(l);
        t.addView(sub);
        t.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = Ui.dp(this, 10);
        p.rightMargin = Ui.dp(this, 10);
        row.addView(t, p);
        return sub;
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 5) return "Bonsoir";
        if (h < 12) return "Bonjour";
        if (h < 18) return "Bon après-midi";
        return "Bonsoir";
    }

    // ---- lifecycle ----

    @Override
    protected void onStart() {
        super.onStart();
        Repo.I.setListener(this);
        Repo.I.load(false);
        onChanged();
        if (getCurrentFocus() == null) hero.requestFocus();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Repo.I.setListener(null);
    }

    @Override
    public void onChanged() {
        Prefs p = Repo.I.prefs();
        Source src = p.active();
        boolean configured = p.configured();
        int channels = Repo.I.all.size();

        if (!configured) {
            heroLabel.setText("BIENVENUE");
            heroTitle.setText("Ajoute ta première liste");
            heroSub.setText("Appuie sur OK pour commencer");
        } else if (channels == 0) {
            boolean busy = Repo.I.loading;
            heroLabel.setText("LISTE « " + src.name.toUpperCase() + " »");
            heroTitle.setText(busy ? "Chargement…" : "Oups, un petit problème");
            heroSub.setText(busy ? "Quelques secondes de patience" : "Appuie sur OK pour voir tes listes");
        } else {
            Channel last = Repo.I.lastChannel();
            if (last != null) {
                heroLabel.setText("REPRENDRE");
                heroTitle.setText(last.name);
            } else {
                heroLabel.setText("REGARDER");
                heroTitle.setText("Choisir une chaîne");
            }
            heroSub.setText("Appuie sur OK");
        }

        subChannels.setText(channels > 0 ? channels + " chaînes" : "—");
        int favs = Repo.I.favorites().size();
        subFavs.setText(favs == 0 ? "Aucun pour l'instant" : favs + (favs > 1 ? " chaînes" : " chaîne"));
        int lists = p.sources().size();
        subLists.setText(lists == 0 ? "Aucune" : lists + (lists > 1 ? " listes" : " liste"));
        int cats = channels > 0 ? Repo.I.categories().size() : 0;
        subCats.setText(cats > 0 ? cats + " affichées" : "—");

        if (!configured) {
            statusView.setText("");
        } else if (channels == 0 && !Repo.I.loading) {
            statusView.setText(Repo.I.status);
        } else {
            statusView.setText("Liste en cours : " + src.name + (Repo.I.status.isEmpty() ? "" : "  ·  " + Repo.I.status));
        }
    }

    // ---- actions ----

    private void onHero() {
        Prefs p = Repo.I.prefs();
        if (!p.configured()) {
            startActivity(new Intent(this, PlaylistEditActivity.class));
            return;
        }
        if (Repo.I.all.isEmpty()) {
            if (Repo.I.loading) {
                Toast.makeText(this, "Chargement en cours, un instant…", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(this, PlaylistsActivity.class));
            }
            return;
        }
        Channel last = Repo.I.lastChannel();
        List<Channel> vis = Repo.I.visibleChannels();
        int idx = -1;
        if (last != null) {
            for (int i = 0; i < vis.size(); i++) {
                if (vis.get(i).id.equals(last.id)) {
                    idx = i;
                    break;
                }
            }
        }
        if (idx >= 0) {
            Repo.I.playing = vis;
            Repo.I.playingIndex = idx;
            startActivity(new Intent(this, PlayerActivity.class));
        } else {
            startActivity(new Intent(this, ChannelsActivity.class));
        }
    }

    private void settings() {
        Dialogs.choice(this, "Réglages", null,
                new String[] {"Taille du texte", "Recharger les chaînes"}, i -> {
                    if (i == 0) {
                        textSize();
                    } else {
                        Repo.I.load(true);
                        Toast.makeText(this, "Rechargement…", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void textSize() {
        final float[] values = {1f, 1.25f, 1.5f};
        Dialogs.choice(this, "Taille du texte", "Choisis ce qui est le plus confortable à lire",
                new String[] {"Normale", "Grande", "Très grande"}, i -> {
                    Repo.I.prefs().setTextScale(values[i]);
                    Ui.init(this);
                    recreate();
                });
    }
}
