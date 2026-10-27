package com.robin.ipitivi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Categories on the left, channels (with what is on now) on the right. */
public class ChannelsActivity extends Activity implements Repo.Listener {
    static final String EXTRA_START = "start";
    static final String START_FAVS = "favs";

    private RecyclerView catList, chList;
    private CatAdapter catAdapter;
    private ChAdapter chAdapter;
    private TextView emptyView;

    private final List<String> catNames = new ArrayList<>();
    private final List<List<Channel>> catLists = new ArrayList<>();
    private int selectedCat = -1;
    private String selectedName;
    private boolean restoreFocus;
    private List<Channel> lastAll;
    private Guide lastGuide;
    private int lastVersion = -1;
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Repo.I.init(this);
        Ui.init(this);
        if (START_FAVS.equals(getIntent().getStringExtra(EXTRA_START))) selectedName = "★ Favoris";
        build();
    }

    private void build() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setClipChildren(false);

        catList = new RecyclerView(this);
        catList.setLayoutManager(new LinearLayoutManager(this));
        catList.setClipToPadding(false);
        catList.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        catAdapter = new CatAdapter();
        catList.setAdapter(catAdapter);
        body.addView(catList, new LinearLayout.LayoutParams(Ui.sdp(this, 330), ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout right = new FrameLayout(this);
        chList = new RecyclerView(this);
        chList.setLayoutManager(new LinearLayoutManager(this));
        chList.setClipToPadding(false);
        chList.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        chAdapter = new ChAdapter();
        chList.setAdapter(chAdapter);
        right.addView(chList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        emptyView = Ui.text(this, "", 24, Ui.MUTED);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        right.addView(emptyView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rp.leftMargin = Ui.dp(this, 24);
        body.addView(right, rp);

        setContentView(Ui.page(this, "Chaînes",
                "OK : regarder     ·     Garder OK appuyé : ajouter ou retirer des favoris     ·     Retour : revenir",
                body, v -> finish()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Repo.I.setListener(this);
        Repo.I.load(false);
        onChanged();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Repo.I.setListener(null);
    }

    // ---- data -> screen ----

    @Override
    public void onChanged() {
        boolean dirty = Repo.I.all != lastAll || Repo.I.guide != lastGuide || Repo.I.dataVersion() != lastVersion;
        Channel keep = null;
        boolean catHadFocus = false;
        View f = getCurrentFocus();
        if (dirty && f != null) {
            if (catList.findContainingViewHolder(f) != null) {
                catHadFocus = true;
            } else {
                RecyclerView.ViewHolder vh = chList.findContainingViewHolder(f);
                if (vh != null) {
                    int p = vh.getBindingAdapterPosition();
                    if (p >= 0 && p < chAdapter.items.size()) keep = chAdapter.items.get(p);
                }
            }
        }
        if (dirty) {
            lastAll = Repo.I.all;
            lastGuide = Repo.I.guide;
            lastVersion = Repo.I.dataVersion();
            rebuildCategories();
        }
        updateEmpty();
        if (restoreFocus) {
            restoreFocus = false;
            focusChannel(Repo.I.playingChannel());
        } else if (keep != null) {
            focusChannel(keep);
        } else if (catHadFocus || (getCurrentFocus() == null && catAdapter.getItemCount() > 0)) {
            focusSelectedCategory();
        }
    }

    private void rebuildCategories() {
        catNames.clear();
        catLists.clear();
        catNames.add("★ Favoris");
        catLists.add(Repo.I.favorites());
        List<Channel> visible = new ArrayList<>();
        List<Repo.Category> cats = Repo.I.categories();
        for (Repo.Category c : cats) visible.addAll(c.channels);
        catNames.add("Toutes les chaînes");
        catLists.add(visible);
        for (Repo.Category c : cats) {
            catNames.add(c.name);
            catLists.add(c.channels);
        }
        int idx = selectedName == null ? -1 : catNames.indexOf(selectedName);
        if (idx < 0) idx = 1;
        selectedCat = idx;
        selectedName = catNames.get(idx);
        catAdapter.notifyDataSetChanged();
        chAdapter.items = catLists.get(idx);
        chAdapter.notifyDataSetChanged();
    }

    private void updateEmpty() {
        boolean none = chAdapter.items.isEmpty();
        emptyView.setVisibility(none ? View.VISIBLE : View.GONE);
        if (!none) return;
        if (selectedCat == 0) {
            emptyView.setText("Tu n'as pas encore de favoris.\nGarde la touche OK appuyée sur une chaîne\npour l'ajouter ici.");
        } else if (Repo.I.all.isEmpty()) {
            emptyView.setText(Repo.I.loading ? "Chargement…" : (Repo.I.status.isEmpty()
                    ? "Aucune liste. Ajoute-en une depuis l'accueil." : Repo.I.status));
        } else {
            emptyView.setText("Aucune chaîne ici.");
        }
    }

    private void showCategory(int i) {
        if (i < 0 || i >= catLists.size() || i == selectedCat) return;
        selectedCat = i;
        selectedName = catNames.get(i);
        for (int k = 0; k < catList.getChildCount(); k++) {
            View v = catList.getChildAt(k);
            v.setSelected(catList.getChildAdapterPosition(v) == selectedCat);
        }
        chAdapter.items = catLists.get(i);
        chAdapter.notifyDataSetChanged();
        chList.scrollToPosition(0);
        updateEmpty();
    }

    // ---- focus ----

    private void focusSelectedCategory() {
        RecyclerView.ViewHolder vh = catList.findViewHolderForAdapterPosition(selectedCat);
        if (vh != null) vh.itemView.requestFocus();
        else tryFocusCategory(6);
    }

    private void tryFocusCategory(final int tries) {
        catList.postDelayed(() -> {
            RecyclerView.ViewHolder vh = catList.findViewHolderForAdapterPosition(selectedCat);
            if (vh != null) vh.itemView.requestFocus();
            else if (tries > 0) tryFocusCategory(tries - 1);
        }, 40);
    }

    private void focusFirstChannel() {
        LinearLayoutManager lm = (LinearLayoutManager) chList.getLayoutManager();
        int pos = lm.findFirstCompletelyVisibleItemPosition();
        if (pos < 0) pos = lm.findFirstVisibleItemPosition();
        if (pos < 0) return;
        View v = lm.findViewByPosition(pos);
        if (v != null) v.requestFocus();
    }

    private void focusChannel(Channel c) {
        if (c == null) return;
        List<Channel> shown = chAdapter.items;
        int idx = -1;
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).id.equals(c.id)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;
        ((LinearLayoutManager) chList.getLayoutManager()).scrollToPositionWithOffset(idx, Ui.dp(this, 120));
        tryFocusPosition(idx, 6);
    }

    private void tryFocusPosition(final int idx, final int tries) {
        chList.postDelayed(() -> {
            RecyclerView.ViewHolder vh = chList.findViewHolderForAdapterPosition(idx);
            if (vh != null) vh.itemView.requestFocus();
            else if (tries > 0) tryFocusPosition(idx, tries - 1);
        }, 40);
    }

    private void play(int position) {
        Repo.I.playing = chAdapter.items;
        Repo.I.playingIndex = position;
        restoreFocus = true;
        startActivity(new Intent(this, PlayerActivity.class));
    }

    // ---- adapters ----

    private final class CatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return catNames.size(); }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            ChannelsActivity c = ChannelsActivity.this;
            TextView t = Ui.text(c, "", 23, Ui.TEXT);
            t.setTypeface(Ui.MEDIUM);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(Ui.sdp(c, 22), Ui.sdp(c, 16), Ui.sdp(c, 22), Ui.sdp(c, 16));
            t.setBackground(Ui.glass(c, Ui.CARD, 22));
            t.setFocusable(true);
            t.setClickable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(c, 6);
            t.setLayoutParams(lp);
            return new RecyclerView.ViewHolder(t) {};
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder h, int pos) {
            final TextView t = (TextView) h.itemView;
            t.setText(catNames.get(pos) + "  (" + catLists.get(pos).size() + ")");
            t.setSelected(pos == selectedCat);
            t.setOnFocusChangeListener((v, focused) -> {
                Ui.focusAnim(v, focused, 1.04f);
                if (focused) showCategory(h.getBindingAdapterPosition());
            });
            t.setOnClickListener(v -> focusFirstChannel());
            t.setOnKeyListener((v, keyCode, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    focusFirstChannel();
                    return true;
                }
                return false;
            });
        }
    }

    private static final class Row extends RecyclerView.ViewHolder {
        final ImageView logo;
        final TextView name, now, star;
        final View barFill, barRest;

        Row(View v, ImageView logo, TextView name, TextView now, TextView star, View fill, View rest) {
            super(v);
            this.logo = logo;
            this.name = name;
            this.now = now;
            this.star = star;
            this.barFill = fill;
            this.barRest = rest;
        }
    }

    private final class ChAdapter extends RecyclerView.Adapter<Row> {
        List<Channel> items = new ArrayList<>();

        @Override public int getItemCount() { return items.size(); }

        @Override
        public Row onCreateViewHolder(ViewGroup parent, int type) {
            ChannelsActivity c = ChannelsActivity.this;
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.sdp(c, 16), Ui.sdp(c, 12), Ui.sdp(c, 22), Ui.sdp(c, 12));
            row.setBackground(Ui.glass(c, Ui.CARD, 28));
            row.setFocusable(true);
            row.setClickable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(c, 8);
            row.setLayoutParams(lp);

            ImageView logo = new ImageView(c);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setBackground(Ui.rect(c, 0x14FFFFFF, 14, 0, 0));
            logo.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
            row.addView(logo, new LinearLayout.LayoutParams(Ui.sdp(c, 76), Ui.sdp(c, 76)));

            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tp.leftMargin = Ui.dp(c, 20);
            row.addView(texts, tp);

            TextView name = Ui.text(c, "", 25, Ui.TEXT);
            name.setTypeface(Ui.MEDIUM);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            TextView now = Ui.text(c, "", 18, Ui.MUTED);
            now.setSingleLine(true);
            now.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(now);

            LinearLayout bar = new LinearLayout(c);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setWeightSum(100f);
            View fill = new View(c);
            fill.setBackground(Ui.rect(c, 0xE6FFFFFF, 2, 0, 0));
            View rest = new View(c);
            rest.setBackground(Ui.rect(c, 0x33FFFFFF, 2, 0, 0));
            bar.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(c, 4), 0f));
            bar.addView(rest, new LinearLayout.LayoutParams(0, Ui.dp(c, 4), 100f));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(c, 4));
            bp.topMargin = Ui.dp(c, 8);
            texts.addView(bar, bp);

            TextView star = Ui.text(c, "★", 26, 0xFFFFD60A);
            row.addView(star);
            return new Row(row, logo, name, now, star, fill, rest);
        }

        @Override
        public void onBindViewHolder(final Row h, int pos) {
            final Channel ch = items.get(pos);
            h.name.setText(ch.name);
            ImageLoader.load(h.logo, ch.logo);
            h.star.setVisibility(Repo.I.isFav(ch.id) ? View.VISIBLE : View.GONE);

            long now = System.currentTimeMillis();
            Programme[] nn = Repo.I.guide.nowNext(ch.epgId, now);
            if (nn[0] != null) {
                h.now.setText(hm.format(new Date(nn[0].start)) + "   " + nn[0].title);
            } else if (nn[1] != null) {
                h.now.setText("Ensuite " + hm.format(new Date(nn[1].start)) + "   " + nn[1].title);
            } else {
                h.now.setText(ch.group);
            }
            int pct = Math.round(Guide.progress(nn[0], now) * 100f);
            ((LinearLayout.LayoutParams) h.barFill.getLayoutParams()).weight = pct;
            ((LinearLayout.LayoutParams) h.barRest.getLayoutParams()).weight = 100 - pct;
            ((View) h.barFill.getParent()).setVisibility(nn[0] != null ? View.VISIBLE : View.GONE);

            h.itemView.setOnFocusChangeListener((v, focused) -> Ui.focusAnim(v, focused, 1.03f));
            h.itemView.setOnClickListener(v -> {
                int p = h.getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION) play(p);
            });
            h.itemView.setOnLongClickListener(v -> {
                boolean added = Repo.I.toggleFav(ch.id);
                h.star.setVisibility(added ? View.VISIBLE : View.GONE);
                Toast.makeText(ChannelsActivity.this,
                        added ? "★ Ajoutée aux favoris" : "Retirée des favoris", Toast.LENGTH_SHORT).show();
                return true;
            });
            h.itemView.setOnKeyListener((v, keyCode, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    focusSelectedCategory();
                    return true;
                }
                return false;
            });
        }
    }
}
