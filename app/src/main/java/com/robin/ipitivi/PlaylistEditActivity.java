package com.robin.ipitivi;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Add or change a list: a name, then either a web link (M3U) or a server account (Xtream). */
public class PlaylistEditActivity extends Activity {
    static final String EXTRA_ID = "id";

    private Prefs prefs;
    private Source editing;
    private String type = Source.M3U;
    private EditText fName, fM3u, fEpg, fServer, fUser, fPass;
    private View m3uBox, xtreamBox;
    private LinearLayout cardLink, cardAccount;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Repo.I.init(this);
        Ui.init(this);
        prefs = Repo.I.prefs();
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id != null) {
            for (Source s : prefs.sources()) if (s.id.equals(id)) editing = s;
        }
        if (editing != null) type = editing.type;

        LinearLayout col = Ui.column(this);

        col.addView(label("Nom de la liste"), Ui.fullWidth(this, 0));
        fName = field("Par exemple : Ma maison", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                editing != null ? editing.name : defaultName());
        col.addView(fName, Ui.fullWidth(this, 8));

        col.addView(label("Comment ton fournisseur t'a-t-il donné l'accès ?"), Ui.fullWidth(this, 26));
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setClipChildren(false);
        cardLink = choiceCard("Un lien web", "Une adresse qui se termine souvent par .m3u");
        cardAccount = choiceCard("Un compte", "Un serveur, un identifiant et un mot de passe (Xtream)");
        cardLink.setOnClickListener(v -> setType(Source.M3U));
        cardAccount.setOnClickListener(v -> setType(Source.XTREAM));
        choices.addView(cardLink, cardParams(0, 8));
        choices.addView(cardAccount, cardParams(8, 0));
        col.addView(choices, Ui.fullWidth(this, 10));

        LinearLayout m3u = Ui.column(this);
        m3u.setPadding(0, 0, 0, 0);
        m3u.addView(label("Adresse du lien"), Ui.fullWidth(this, 22));
        fM3u = field("http://…", InputType.TYPE_TEXT_VARIATION_URI, editing != null ? editing.m3u : "");
        m3u.addView(fM3u, Ui.fullWidth(this, 8));
        m3u.addView(label("Guide des programmes (facultatif)"), Ui.fullWidth(this, 22));
        fEpg = field("http://… (laisse vide si tu n'en as pas)", InputType.TYPE_TEXT_VARIATION_URI,
                editing != null ? editing.epg : "");
        m3u.addView(fEpg, Ui.fullWidth(this, 8));
        m3uBox = m3u;
        col.addView(m3uBox);

        LinearLayout xt = Ui.column(this);
        xt.setPadding(0, 0, 0, 0);
        xt.addView(label("Adresse du serveur"), Ui.fullWidth(this, 22));
        fServer = field("http://exemple.com:8080", InputType.TYPE_TEXT_VARIATION_URI,
                editing != null ? editing.server : "");
        xt.addView(fServer, Ui.fullWidth(this, 8));
        xt.addView(label("Identifiant"), Ui.fullWidth(this, 22));
        fUser = field("Ton identifiant", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                editing != null ? editing.user : "");
        xt.addView(fUser, Ui.fullWidth(this, 8));
        xt.addView(label("Mot de passe"), Ui.fullWidth(this, 22));
        fPass = field("Ton mot de passe", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, editing != null ? editing.pass : "");
        xt.addView(fPass, Ui.fullWidth(this, 8));
        xtreamBox = xt;
        col.addView(xtreamBox);

        TextView save = Ui.button(this, "Enregistrer", true);
        save.setOnClickListener(v -> save());
        col.addView(save, Ui.fullWidth(this, 34));
        TextView cancel = Ui.button(this, "Annuler", false);
        cancel.setOnClickListener(v -> finish());
        col.addView(cancel, Ui.fullWidth(this, 14));

        setContentView(Ui.page(this, editing != null ? "Modifier la liste" : "Ajouter une liste",
                "Tape avec le clavier à l'écran, puis appuie sur Suivant. Tu pourras tout modifier plus tard.",
                Ui.scroller(this, col), v -> finish()));
        setType(type);
        fName.requestFocus();
    }

    private String defaultName() {
        int n = prefs.sources().size() + 1;
        return n == 1 ? "Ma liste" : "Ma liste " + n;
    }

    private void setType(String t) {
        type = t;
        boolean x = Source.XTREAM.equals(t);
        xtreamBox.setVisibility(x ? View.VISIBLE : View.GONE);
        m3uBox.setVisibility(x ? View.GONE : View.VISIBLE);
        cardAccount.setSelected(x);
        cardLink.setSelected(!x);
    }

    private void save() {
        String name = fName.getText().toString().trim();
        if (name.isEmpty()) name = defaultName();
        String m3u = fM3u.getText().toString().trim();
        String epg = fEpg.getText().toString().trim();
        String server = fServer.getText().toString().trim();
        String user = fUser.getText().toString().trim();
        String pass = fPass.getText().toString().trim();
        if (Source.XTREAM.equals(type)) {
            if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Il manque le serveur, l'identifiant ou le mot de passe", Toast.LENGTH_LONG).show();
                return;
            }
        } else if (!m3u.startsWith("http://") && !m3u.startsWith("https://")) {
            Toast.makeText(this, "Le lien doit commencer par http:// ou https://", Toast.LENGTH_LONG).show();
            return;
        }
        String id = editing != null ? editing.id : SourceStore.newId();
        Source s = new Source(id, name, type, m3u, epg, server, user, pass);
        Source active = prefs.active();
        boolean makeActive = editing == null || (active != null && active.id.equals(id));
        prefs.upsert(s);
        if (makeActive) Repo.I.switchTo(id);
        Repo.I.bump();
        Toast.makeText(this, "Liste enregistrée", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---- view helpers ----

    private TextView label(String s) {
        TextView t = Ui.text(this, s, 21, Ui.TEXT);
        t.setTypeface(Ui.MEDIUM);
        return t;
    }

    private EditText field(String hint, int inputType, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0x80FFFFFF);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(23 * Ui.scale);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        e.setText(value);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setBackground(Ui.glass(this, Ui.CARD_HI, 24));
        e.setPadding(Ui.sdp(this, 24), Ui.sdp(this, 16), Ui.sdp(this, 24), Ui.sdp(this, 16));
        return e;
    }

    private LinearLayout choiceCard(String title, String desc) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setFocusable(true);
        c.setClickable(true);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.glassFocus(this, 30));
        bg.addState(new int[] {android.R.attr.state_selected},
                Ui.glassPane(this, 0x990A84FF, 0x590A84FF, 30, 0xE6FFFFFF, 2.5f));
        bg.addState(new int[] {}, Ui.glassRest(this, 30));
        c.setBackground(bg);
        c.setPadding(Ui.sdp(this, 26), Ui.sdp(this, 20), Ui.sdp(this, 26), Ui.sdp(this, 20));
        TextView t = Ui.text(this, title, 26, Ui.TEXT);
        t.setTypeface(Ui.MEDIUM);
        TextView d = Ui.text(this, desc, 18, Ui.MUTED);
        c.addView(t);
        c.addView(d);
        c.setOnFocusChangeListener((v, f) -> Ui.focusAnim(v, f, 1.04f));
        return c;
    }

    private LinearLayout.LayoutParams cardParams(int leftDp, int rightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        p.leftMargin = Ui.dp(this, leftDp);
        p.rightMargin = Ui.dp(this, rightDp);
        return p;
    }
}
