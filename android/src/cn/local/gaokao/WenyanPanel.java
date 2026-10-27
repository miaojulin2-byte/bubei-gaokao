package cn.local.gaokao;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * First Wenyan learning surface. It deliberately uses its own pack format instead
 * of the Japanese episode.json model. State and first attempts still go through
 * StudyStore's atomic roundAnswer transaction.
 */
final class WenyanPanel extends android.content.ContextWrapper {
    final MainActivity host;

    static final int INK = 0xff1f2933, MUTED = 0xff66737c, ORANGE = 0xffff7900,
            GREEN = 0xff0f9d58, TEAL = 0xff009980, AMBER = 0xffd99a00,
            RED = 0xffef3340, CARD = 0x9ef8f4ec;
    static final String SESSION_KIND = "wenyan", REVIEW_KIND = "wenyan-review";

    LinearLayout root, dock;
    ScrollView scroll;
    boolean revealNext, fromLibrary;
    String textId = "quanxue";
    Pack pack;
    Session session;
    ReviewSession review;
    StudyStore store;
    SharedPreferences prefs;
    String page = "home";

    static final String[] DEFAULT_IDS = {
            "quanxue-ex01", "quanxue-ex02", "quanxue-ex03", "quanxue-ex05",
            "quanxue-ex07", "quanxue-ex11", "quanxue-ex14", "quanxue-ex16",
            "quanxue-ex18", "quanxue-ex19"
    };

    static final class Sentence {
        String id, section, text;
    }

    static final class Knowledge {
        String id, sentenceId, type, surface, context, gloss, explanation, modern = "", pattern = "", pinyin = "";
    }

    static final class Exercise {
        String id, type, prompt, explanation, skill;
        final ArrayList<String> options = new ArrayList<>();
        final ArrayList<String> kpIds = new ArrayList<>();
        final ArrayList<String> sentenceIds = new ArrayList<>();
        int answerIndex;
    }

    static final class Pack {
        String textId, title, author, book, stage, revision;
        final LinkedHashMap<String, Sentence> sentences = new LinkedHashMap<>();
        final LinkedHashMap<String, Knowledge> knowledge = new LinkedHashMap<>();
        final LinkedHashMap<String, Exercise> exercises = new LinkedHashMap<>();
        final ArrayList<String> exerciseOrder = new ArrayList<>();
    }

    static final class Session {
        final ArrayList<String> ids = new ArrayList<>();
        final ArrayList<String> wrongIds = new ArrayList<>();
        final ArrayList<String> answered = new ArrayList<>();
        int index, selected = -1, firstCorrect;
        boolean feedback, detail, correct, hinted, corrected;
        String revision;

        JSONObject encode() {
            try {
                JSONObject o = new JSONObject();
                o.put("revision", revision);
                o.put("ids", new JSONArray(ids));
                o.put("index", index);
                o.put("selected", selected);
                o.put("feedback", feedback);
                o.put("detail", detail);
                o.put("correct", correct);
                o.put("hinted", hinted);
                o.put("corrected", corrected);
                o.put("firstCorrect", firstCorrect);
                o.put("wrongIds", new JSONArray(wrongIds));
                o.put("answered", new JSONArray(answered));
                return o;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        static Session decode(String raw, Pack pack) throws Exception {
            if (raw == null) return null;
            JSONObject o = new JSONObject(raw);
            if (!pack.revision.equals(o.optString("revision"))) return null;
            Session s = new Session();
            s.revision = pack.revision;
            JSONArray ids = o.getJSONArray("ids");
            for (int i = 0; i < ids.length(); i++) {
                String id = ids.getString(i);
                if (!pack.exercises.containsKey(id) || s.ids.contains(id)) return null;
                s.ids.add(id);
            }
            s.index = o.optInt("index", 0);
            if (s.index < 0 || s.index >= s.ids.size()) return null;
            s.selected = o.optInt("selected", -1);
            s.feedback = o.optBoolean("feedback", false);
            s.detail = o.optBoolean("detail", false);
            s.correct = o.optBoolean("correct", false);
            s.hinted = o.optBoolean("hinted", false);
            s.corrected = o.optBoolean("corrected", false);
            s.firstCorrect = o.optInt("firstCorrect", 0);
            JSONArray wrong = o.optJSONArray("wrongIds");
            if (wrong != null) for (int i = 0; i < wrong.length(); i++) {
                String id = wrong.getString(i);
                if (pack.exercises.containsKey(id) && !s.wrongIds.contains(id)) s.wrongIds.add(id);
            }
            JSONArray answered = o.optJSONArray("answered");
            if (answered != null) for (int i = 0; i < answered.length(); i++) {
                String id = answered.getString(i);
                if (pack.exercises.containsKey(id) && !s.answered.contains(id)) s.answered.add(id);
            }
            if (s.firstCorrect < 0 || s.firstCorrect > s.ids.size()) s.firstCorrect = 0;
            return s;
        }
    }

    static final class RubySpan extends ReplacementSpan {
        final String ruby;
        final int rubyColor;
        final float scale;

        RubySpan(String ruby, int rubyColor, float scale) {
            this.ruby = ruby;
            this.rubyColor = rubyColor;
            this.scale = scale;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float baseSize = paint.getTextSize();
            float baseWidth = paint.measureText(text, start, end);
            Paint.FontMetricsInt baseMetrics = paint.getFontMetricsInt();
            paint.setTextSize(baseSize * scale);
            float rubyWidth = paint.measureText(ruby);
            Paint.FontMetricsInt rubyMetrics = paint.getFontMetricsInt();
            paint.setTextSize(baseSize);
            if (fm != null) {
                fm.ascent = baseMetrics.ascent - (rubyMetrics.bottom - rubyMetrics.top) - 2;
                fm.top = fm.ascent - 1;
                fm.descent = baseMetrics.descent;
                fm.bottom = baseMetrics.bottom;
            }
            return (int) Math.ceil(Math.max(baseWidth, rubyWidth)) + 2;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, Paint paint) {
            float baseSize = paint.getTextSize();
            int oldColor = paint.getColor();
            boolean oldBold = paint.isFakeBoldText();
            float baseWidth = paint.measureText(text, start, end);
            Paint.FontMetricsInt baseMetrics = paint.getFontMetricsInt();
            paint.setTextSize(baseSize * scale);
            paint.setColor(rubyColor);
            float rubyWidth = paint.measureText(ruby);
            float total = Math.max(baseWidth, rubyWidth) + 2;
            float rubyBaseline = y + baseMetrics.ascent - 2;
            canvas.drawText(ruby, x + (total - rubyWidth) / 2f, rubyBaseline, paint);
            paint.setTextSize(baseSize);
            paint.setColor(ORANGE);
            paint.setFakeBoldText(true);
            canvas.drawText(text, start, end, x + (total - baseWidth) / 2f, y, paint);
            paint.setColor(oldColor);
            paint.setFakeBoldText(oldBold);
        }
    }

    static final class ReviewSession {
        final ArrayList<String> ids = new ArrayList<>();
        int index, rating = -1;
        boolean feedback, corrected;
        String revision;

        JSONObject encode() {
            try {
                JSONObject o = new JSONObject();
                o.put("revision", revision);
                o.put("ids", new JSONArray(ids));
                o.put("index", index);
                o.put("rating", rating);
                o.put("feedback", feedback);
                o.put("corrected", corrected);
                return o;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        static ReviewSession decode(String raw, Pack pack) throws Exception {
            if (raw == null) return null;
            JSONObject o = new JSONObject(raw);
            if (!pack.revision.equals(o.optString("revision"))) return null;
            ReviewSession s = new ReviewSession();
            s.revision = pack.revision;
            JSONArray ids = o.getJSONArray("ids");
            for (int i = 0; i < ids.length(); i++) {
                String id = ids.getString(i);
                if (!pack.knowledge.containsKey(id) || s.ids.contains(id)) return null;
                s.ids.add(id);
            }
            s.index = o.optInt("index", 0);
            if (s.index < 0 || s.index >= s.ids.size()) return null;
            s.rating = o.optInt("rating", -1);
            s.feedback = o.optBoolean("feedback", false);
            s.corrected = o.optBoolean("corrected", false);
            return s;
        }
    }

    WenyanPanel(MainActivity host, String mode) {
        this(host, mode, "quanxue");
    }

    WenyanPanel(MainActivity host, String mode, String textId) {
        super(host);
        this.host = host;
        this.textId = textId == null ? "quanxue" : textId;
        this.fromLibrary = "text".equals(mode);
        store = new StudyStore(this);
        prefs = getSharedPreferences("wenyan", MODE_PRIVATE);
        try {
            loadPack(this.textId);
        } catch (Exception e) {
            error("无法读取《劝学》内容包：" + e.getMessage());
            host.home();
            return;
        }
        if ("review".equals(mode)) {
            ReviewSession saved = savedReview();
            if (saved != null) {
                review = saved;
                showReviewQuestion();
            } else {
                showReview();
            }
        } else if ("learn".equals(mode)) {
            Session saved = savedLearn();
            if (saved != null) {
                session = saved;
                showQuestion();
            } else {
                startDefault();
            }
        } else if ("text".equals(mode)) {
            showText();
        } else {
            host.home();
        }
    }

    String readAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(host.subject+"/"+name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }

    void loadLibraryText(String selectedId) throws Exception {
        JSONObject root = new JSONObject(readAsset("wenyan-library.json"));
        JSONArray texts = root.getJSONArray("texts");
        JSONObject source = null;
        for (int i = 0; i < texts.length(); i++) {
            JSONObject t = texts.getJSONObject(i);
            if (selectedId.equals(t.optString("id"))) { source = t; break; }
        }
        if (source == null) throw new IllegalStateException("篇目不存在：" + selectedId);
        pack = new Pack();
        pack.textId = selectedId;
        pack.title = source.optString("title");
        pack.author = source.optString("author");
        pack.book = source.optString("book");
        pack.stage = source.optString("stage");
        pack.revision = "library-2026-10-20";
        int n = 0;
        JSONArray sections = source.optJSONArray("sections");
        if (sections != null) for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.getJSONObject(i);
            String label = section.optString("label");
            JSONArray paragraphs = section.optJSONArray("paragraphs");
            if (paragraphs == null) continue;
            for (int j = 0; j < paragraphs.length(); j++) {
                JSONArray sentences = paragraphs.getJSONObject(j).optJSONArray("sentences");
                if (sentences == null) continue;
                for (int k = 0; k < sentences.length(); k++) {
                    Sentence sentence = new Sentence();
                    sentence.id = selectedId + "-s" + (++n);
                    sentence.section = label;
                    sentence.text = sentences.getString(k);
                    pack.sentences.put(sentence.id, sentence);
                }
            }
        }
        if (pack.sentences.isEmpty()) throw new IllegalStateException("篇目没有原文：" + selectedId);
    }

    void loadPack(String selectedId) throws Exception {
        if (!"quanxue".equals(selectedId) && !"shishuo".equals(selectedId)
                && !"chibi-fu".equals(selectedId) && !"liuguo-lun".equals(selectedId)) {
            loadLibraryText(selectedId);
            return;
        }
        JSONObject o = new JSONObject(readAsset(selectedId + "-pack.json"));
        pack = new Pack();
        pack.textId = o.optString("text_id");
        pack.title = o.optString("title");
        pack.author = o.optString("author");
        pack.book = o.optString("book");
        pack.stage = o.optString("stage");
        pack.revision = o.optString("content_revision");
        JSONArray sentences = o.getJSONArray("sentences");
        for (int i = 0; i < sentences.length(); i++) {
            JSONObject item = sentences.getJSONObject(i);
            Sentence s = new Sentence();
            s.id = item.getString("id");
            s.section = item.optString("section");
            s.text = item.getString("text");
            pack.sentences.put(s.id, s);
        }
        JSONArray kps = o.getJSONArray("knowledge_points");
        for (int i = 0; i < kps.length(); i++) {
            JSONObject item = kps.getJSONObject(i);
            Knowledge k = new Knowledge();
            k.id = item.getString("id");
            k.sentenceId = item.optString("sentence_id");
            k.type = item.optString("type");
            k.surface = item.optString("surface");
            k.context = item.optString("context");
            k.gloss = item.optString("gloss");
            k.explanation = item.optString("explanation");
            k.modern = item.optString("modern_meaning");
            k.pattern = item.optString("pattern");
            JSONObject pronunciation = item.optJSONObject("pronunciation");
            if (pronunciation != null) k.pinyin = pronunciation.optString("pinyin");
            pack.knowledge.put(k.id, k);
        }
        JSONArray exercises = o.getJSONArray("exercises");
        for (int i = 0; i < exercises.length(); i++) {
            JSONObject item = exercises.getJSONObject(i);
            Exercise e = new Exercise();
            e.id = item.getString("id");
            e.type = item.optString("type");
            e.prompt = item.optString("prompt");
            e.explanation = item.optString("explanation");
            e.skill = item.optString("skill");
            e.answerIndex = item.optInt("answer_index", -1);
            JSONArray options = item.optJSONArray("options");
            if (options != null) for (int j = 0; j < options.length(); j++) e.options.add(options.getString(j));
            JSONArray kpIds = item.optJSONArray("knowledge_point_ids");
            if (kpIds != null) for (int j = 0; j < kpIds.length(); j++) e.kpIds.add(kpIds.getString(j));
            JSONArray sentenceIds = item.optJSONArray("sentence_ids");
            if (sentenceIds != null) for (int j = 0; j < sentenceIds.length(); j++) e.sentenceIds.add(sentenceIds.getString(j));
            if (e.options.size() < 2 || e.answerIndex < 0 || e.answerIndex >= e.options.size()) continue;
            pack.exercises.put(e.id, e);
            pack.exerciseOrder.add(e.id);
        }
        if (pack.sentences.isEmpty() || pack.knowledge.isEmpty() || pack.exercises.isEmpty()) {
            throw new IllegalStateException("内容包为空");
        }
    }

    Runnable externalBack;

    void back() {
        if(externalBack!=null){host.goBack(externalBack);return;}
        if (host.wenyanPanel == this) host.wenyanPanel = null;
        if (fromLibrary) host.showTextLibrary(); else host.home();
    }

    void backFromButton() {
        if(externalBack!=null){host.goBack(externalBack);return;}
        if (host.wenyanPanel == this) host.wenyanPanel = null;
        if (fromLibrary) host.goBack(host::showTextLibrary); else host.goBack(host::home);
    }

    void showHome() {
        page = "home";
        session = null;
        review = null;
        shell(true);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        Button plan = action("", this::showStats);
        plan.setContentDescription("学习计划");
        plan.setCompoundDrawablesWithIntrinsicBounds(new UiIcon(3, dp(36), INK), null, null, null);
        root.addView(plan, new LinearLayout.LayoutParams(dp(48), dp(48)));
        addSpacer(85);
        TextView title = text("温故而知新", 30, INK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        View flex = new View(this);
        root.addView(flex, new LinearLayout.LayoutParams(1, 0, 1));

        final Session savedLearn = savedLearn();
        final ReviewSession savedReview = savedReview();
        int reviewCount = reviewCount();
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(cards, new LinearLayout.LayoutParams(-1, dp(104)));
        homeCard(cards, "Learn",
                savedLearn == null ? "10" : (savedLearn.index + 1) + "/" + savedLearn.ids.size(),
                () -> {
                    if (savedLearn != null) {
                        session = savedLearn;
                        showQuestion();
                    } else {
                        startDefault();
                    }
                });
        homeCard(cards, "Review",
                savedReview == null ? String.valueOf(reviewCount) : (savedReview.index + 1) + "/" + savedReview.ids.size(),
                () -> {
                    if (savedReview != null) {
                        review = savedReview;
                        showReviewQuestion();
                    } else {
                        showReview();
                    }
                });
        addSpacer(24);
        bottomNav();
    }

    Session savedLearn() {
        try {
            Session s = Session.decode(store.round(SESSION_KIND), pack);
            if (s != null) return s;
        } catch (Exception ignored) {
            try {
                store.round(SESSION_KIND, null);
            } catch (Exception ignoredAgain) {
            }
        }
        return null;
    }

    ReviewSession savedReview() {
        try {
            ReviewSession s = ReviewSession.decode(store.round(REVIEW_KIND), pack);
            if (s != null) return s;
        } catch (Exception ignored) {
            try {
                store.round(REVIEW_KIND, null);
            } catch (Exception ignoredAgain) {
            }
        }
        return null;
    }

    int reviewCount() {
        try (Cursor c = store.getReadableDatabase().rawQuery(
                "SELECT count(*) FROM state WHERE stage>0 AND suspended=0 AND due<=?",
                new String[]{String.valueOf(System.currentTimeMillis())})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    void homeCard(LinearLayout row, String label, String value, Runnable run) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(18), dp(14), dp(18), dp(12));
        GradientDrawable cardBg = bg(0xeeffffff, 16);
        cardBg.setStroke(dp(1), 0x12000000);
        card.setBackground(cardBg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(dp(5), 0, dp(5), 0);
        row.addView(card, lp);
        TextView title = text(label, 20, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title);
        TextView number = text(value, 15, ORANGE);
        number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        number.setPadding(0, dp(8), 0, 0);
        card.addView(number);
        card.setContentDescription(label + " " + value);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> run.run());
    }

    void bottomNav() {
        dock.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        navCell(row, "今日", 0, page.equals("home"), this::showHome);
        navCell(row, "我的内容", 1, page.equals("text"), this::showText);
        navCell(row, "仪表盘", 2, page.equals("stats"), this::showStats);
        dock.addView(row, new LinearLayout.LayoutParams(-1, dp(58)));
    }

    void navCell(LinearLayout row, String label, int iconKind, boolean selected, Runnable run) {
        int color = selected ? ORANGE : MUTED;
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new UiIcon(iconKind, dp(27), color));
        icon.setContentDescription(label);
        icon.setClickable(true);
        icon.setFocusable(true);
        icon.setOnClickListener(v -> run.run());
        row.addView(icon, new LinearLayout.LayoutParams(0, dp(58), 1));
    }

    GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    void startDefault() {
        // The daily goal is knowledge points, not questions: include questions
        // until their distinct knowledge points reach the daily quota.
        if (pack.exercises.isEmpty()) { showText(); return; }
        int goal = Math.max(1, prefs.getInt("daily", 10));
        ArrayList<String> ids = new ArrayList<>();
        LinkedHashSet<String> covered = new LinkedHashSet<>();
        for (String id : pack.exerciseOrder) {
            Exercise e = pack.exercises.get(id);
            if (e == null || "recitation_fill".equals(e.type)) continue;
            int before = covered.size();
            for (String kp : e.kpIds) if (pack.knowledge.containsKey(kp)) covered.add(kp);
            if (covered.size() > before) ids.add(id);
            if (covered.size() >= goal) break;
        }
        startSession(ids);
    }

    void startSession(List<String> ids) {
        LinkedHashSet<String> unique = new LinkedHashSet<>(ids);
        if (unique.isEmpty()) {
            error("没有可学习的题目。");
            return;
        }
        session = new Session();
        session.revision = pack.revision;
        session.ids.addAll(unique);
        try {
            store.round(SESSION_KIND, session.encode().toString());
        } catch (Exception e) {
            error("无法保存学习进度：" + e.getMessage());
            return;
        }
        showQuestion();
    }

    void showQuestion() {
        if (session == null || session.index >= session.ids.size()) {
            showSummary();
            return;
        }
        Exercise q = pack.exercises.get(session.ids.get(session.index));
        if (q == null) {
            showSummary();
            return;
        }
        page = "study";
        revealNext = session.feedback;
        shell(true);
        Knowledge kp = firstKnowledge(q);
        topbar((session.index + 1) + "/" + session.ids.size(), kp == null ? "" : kp.id, false);
        addSpacer(12);
        showTarget(q, kp, !session.detail);
        if (!session.detail) {
            addSpacer(24);
            TextView prompt = text(q.prompt, 15, INK);
            prompt.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            prompt.setLineSpacing(dp(3), 1f);
            root.addView(prompt, new LinearLayout.LayoutParams(-1, -2));
            addSpacer(8);
        }
        if (session.feedback && !session.detail) {
            renderOptions(q, true);
        } else if (session.feedback) {
            renderFeedback(q, kp);
        } else {
            renderOptions(q, false);
        }
        renderDock(q);
    }

    void topbar(String progress, String targetId, boolean reviewPage) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(16), 0);
        TextView back = text("‹", 28, INK);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setFocusable(true);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> backFromButton());
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(48)));
        TextView count = text(progress, 13, MUTED);
        count.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(count, new LinearLayout.LayoutParams(-2, dp(48)));
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        boolean starred = targetId != null && !targetId.isEmpty()
                && prefs.getBoolean("star:" + targetId, false);
        iconText(bar, "↶", "撤销本次作答", () -> {
            if (reviewPage) {
                if (review != null && review.feedback && review.rating == 2 && !review.corrected) markReviewWrong();
                else Toast.makeText(this, "没有可撤销的作答", Toast.LENGTH_SHORT).show();
            } else if (session != null && session.feedback && session.correct && !session.corrected) {
                markWrong();
            } else {
                Toast.makeText(this, "没有可撤销的作答", Toast.LENGTH_SHORT).show();
            }
        });
        iconText(bar, starred ? "★" : "☆", "收藏", () -> {
            if (targetId == null || targetId.isEmpty()) return;
            boolean now = !prefs.getBoolean("star:" + targetId, false);
            prefs.edit().putBoolean("star:" + targetId, now).apply();
            if (reviewPage) showReviewQuestion();
            else showQuestion();
        });
        iconText(bar, "⋯", "更多", this::showMore);
        host.frame.addView(bar, 0);
    }

    TextView iconText(LinearLayout bar, String symbol, String desc, Runnable run) {
        int size = symbol.equals("注") || symbol.equals("诵") ? 16 : symbol.equals("⋯") ? 19 : 18;
        TextView v = text(symbol, size, MUTED);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setMinWidth(0);
        v.setMinimumWidth(0);
        v.setPadding(0, 0, 0, 0);
        v.setClickable(true);
        v.setFocusable(true);
        v.setContentDescription(desc);
        v.setOnClickListener(x -> run.run());
        bar.addView(v, new LinearLayout.LayoutParams(dp(42), dp(48)));
        return v;
    }

    void showTarget(Exercise q, Knowledge kp, boolean showContext) {
        if (!showContext) return;
        LinearLayout card = card();
        studyCardBackground(card);
        if (kp != null && kp.type != null && !kp.type.isEmpty()) {
            card.addView(cardTag(kp.type));
        }
        SpannableStringBuilder ctx = new SpannableStringBuilder();
        for (String sid : q.sentenceIds) {
            Sentence sentence = pack.sentences.get(sid);
            if (sentence == null) continue;
            if (ctx.length() > 0) ctx.append("\n");
            appendHighlighted(ctx, sentence.text, kp == null ? "" : kp.surface,
                    kp == null ? "" : kp.pinyin);
        }
        if (ctx.length() == 0) ctx.append(firstSentenceText(q));
        TextView sentence = serif(ctx, 17, INK);
        sentence.setLineSpacing(dp(5), 1f);
        sentence.setPadding(0, dp(8), 0, 0);
        card.addView(sentence);
        TextView source = text("《" + pack.title + "》 · " + pack.author, 11, MUTED);
        source.setPadding(0, dp(8), 0, 0);
        card.addView(source);
    }

    String joinedSentenceText(Exercise q) {
        StringBuilder b = new StringBuilder();
        for (String sid : q.sentenceIds) {
            Sentence s = pack.sentences.get(sid);
            if (s == null) continue;
            if (b.length() > 0) b.append("\n");
            b.append(s.text);
        }
        return b.toString();
    }

    String firstSentenceText(Exercise q) {
        for (String sid : q.sentenceIds) {
            Sentence s = pack.sentences.get(sid);
            if (s != null) return s.text;
        }
        return "";
    }

    String sectionOf(Exercise q) {
        for (String sid : q.sentenceIds) {
            Sentence s = pack.sentences.get(sid);
            if (s != null && s.section != null && !s.section.isEmpty()) return s.section;
        }
        return "";
    }

    void showNote(String targetId) {
        Knowledge kp = targetId == null ? null : pack.knowledge.get(targetId);
        if (kp == null) {
            new AlertDialog.Builder(this).setTitle("注释")
                    .setMessage("《" + pack.title + "》 · " + pack.author)
                    .setPositiveButton("关闭", null).show();
            return;
        }
        String message = kp.type + " · " + kp.surface
                + (kp.pinyin == null || kp.pinyin.isEmpty() ? "" : "  " + kp.pinyin)
                + "\n出处：《" + pack.title + "》 · " + pack.author;
        new AlertDialog.Builder(this).setTitle("注释").setMessage(message)
                .setPositiveButton("关闭", null).show();
    }

    void showMore() {
        String[] items = {"重新开始本组", "查看原文", "学习记录", "返回首页"};
        new AlertDialog.Builder(this).setTitle("更多").setItems(items, (d, which) -> {
            if (which == 0) {
                if (page.equals("review")) showReview();
                else startDefault();
            } else if (which == 1) showText();
            else if (which == 2) showStats();
            else backFromButton();
        }).show();
    }

    void header(String titleText, String rightText, Runnable back) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(16), 0);
        TextView b = text("‹", 32, INK);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription("返回");
        b.setOnClickListener(v -> back.run());
        bar.addView(b, new LinearLayout.LayoutParams(dp(44), dp(48)));
        TextView title = text(titleText, 14, INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        if (rightText != null && !rightText.isEmpty()) {
            TextView p = text(rightText, 13, MUTED);
            p.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            bar.addView(p, new LinearLayout.LayoutParams(-2, dp(48)));
        }
        host.frame.addView(bar, 0);
    }

    void appendHighlighted(SpannableStringBuilder out, String text, String surface, String pinyin) {
        int base = out.length();
        out.append(text);
        if (surface == null || surface.isEmpty() || surface.length() > 12) return;
        boolean rubyApplied = false;
        for (String target : surface.split("/")) {
            if (target.isEmpty()) continue;
            int from = 0;
            while (from < text.length()) {
                int at = text.indexOf(target, from);
                if (at < 0) break;
                if (!rubyApplied && pinyin != null && !pinyin.isEmpty() && target.equals(surface)) {
                    out.setSpan(new RubySpan(pinyin, MUTED, 0.42f),
                            base + at, base + at + target.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    rubyApplied = true;
                } else {
                    out.setSpan(new ForegroundColorSpan(ORANGE),
                            base + at, base + at + target.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new StyleSpan(Typeface.BOLD),
                            base + at, base + at + target.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                from = at + target.length();
            }
        }
    }

    void renderOptions(Exercise q, boolean reveal) {
        for (int i = 0; i < q.options.size(); i++) {
            final int index = i;
            int fill = 0x73f8f4ec;
            if (reveal) {
                if (i == q.answerIndex) fill = 0xa6cfe8dc;
                else if (i == session.selected && !session.correct) fill = 0xa6f4cfcf;
                else fill = 0x86f8f4ec;
            }
            Button b = option(q.options.get(i), fill);
            b.setEnabled(!reveal);
            if (!reveal) b.setOnClickListener(v -> submit(index, false));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.topMargin = dp(9);
            root.addView(b, p);
        }
    }

    Button option(String text, int fill) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(INK);
        b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setMinHeight(dp(56));
        b.setIncludeFontPadding(false);
        GradientDrawable g = bg(fill, 14);
        g.setStroke(dp(1), 0x12000000);
        b.setBackground(g);
        b.setStateListAnimator(null);
        return b;
    }

    void renderFeedback(Exercise q, Knowledge kp) {
        addSpacer(14);
        if (!session.correct) {
            TextView answer = text("正确答案：" + q.options.get(q.answerIndex), 14, INK);
            answer.setLineSpacing(dp(3), 1f);
            answer.setPadding(0, 0, 0, dp(4));
            root.addView(answer, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout meaning = card();
        if (kp != null) {
            meaning.addView(cardTag(kp.type));
            TextView gloss = serif(kp.gloss, 22, INK);
            gloss.setTypeface(Typeface.SERIF, Typeface.BOLD);
            gloss.setPadding(0, dp(6), 0, 0);
            meaning.addView(gloss);
            TextView explanation = text(kp.explanation, 14, INK);
            explanation.setLineSpacing(dp(4), 1f);
            explanation.setPadding(0, dp(8), 0, 0);
            meaning.addView(explanation);
            if (kp.modern != null && !kp.modern.isEmpty()) {
                TextView modern = text("今义：" + kp.modern, 12, MUTED);
                modern.setPadding(0, dp(6), 0, 0);
                meaning.addView(modern);
            }
            if (kp.pattern != null && !kp.pattern.isEmpty()) {
                TextView pattern = text("句式：" + kp.pattern, 12, MUTED);
                pattern.setPadding(0, dp(4), 0, 0);
                meaning.addView(pattern);
            }
        } else {
            TextView explanation = text(q.explanation, 15, INK);
            explanation.setLineSpacing(dp(4), 1f);
            meaning.addView(explanation);
        }
        LinearLayout example = card();
        example.addView(cardTag("原句"));
        TextView sentence = serif(joinedSentenceText(q), 17, INK);
        sentence.setLineSpacing(dp(4), 1f);
        sentence.setPadding(0, dp(6), 0, 0);
        example.addView(sentence);
        TextView source = text("《" + pack.title + "》 · " + pack.author, 12, MUTED);
        source.setPadding(0, dp(6), 0, 0);
        example.addView(source);
    }

    void renderDock(Exercise q) {
        dock.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        if (!session.feedback) {
            dockAction(row, "看答案", ORANGE, () -> submit(-1, true));
        } else if (!session.detail) {
            dockAction(row, "继续", ORANGE, this::continueDetail);
        } else {
            boolean last = session.index + 1 >= session.ids.size();
            dockAction(row, last ? "查看小结" : "下一个", ORANGE, this::next);
            if (session.correct && !session.corrected) dockAction(row, "记错了", MUTED, this::markWrong);
        }
        dock.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    void dockAction(LinearLayout row, String label, int color, Runnable run) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        TextView t = text(label, 15, INK);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        cell.addView(t, new LinearLayout.LayoutParams(-1, dp(46)));
        View line = new View(this);
        line.setBackground(bg(color, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), dp(3));
        lp.bottomMargin = dp(8);
        cell.addView(line, lp);
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setOnClickListener(v -> run.run());
        row.addView(cell, new LinearLayout.LayoutParams(0, dp(62), 1));
    }

    void continueDetail() {
        if (session == null || !session.feedback || session.detail) return;
        session.detail = true;
        try {
            store.round(SESSION_KIND, session.encode().toString());
        } catch (Exception e) {
            error("无法保存学习进度：" + e.getMessage());
            return;
        }
        showQuestion();
    }

    void submit(int selected, boolean hint) {
        if (session == null || session.feedback) return;
        Exercise q = pack.exercises.get(session.ids.get(session.index));
        if (q == null) return;
        boolean correct = !hint && selected == q.answerIndex;
        session.selected = selected;
        session.feedback = true;
        session.detail = false;
        session.correct = correct;
        session.hinted = hint;
        session.corrected = false;
        if (!session.answered.contains(q.id)) {
            session.answered.add(q.id);
            if (correct) session.firstCorrect++;
        }
        if (!correct && !session.wrongIds.contains(q.id)) session.wrongIds.add(q.id);
        String answer = hint ? "hint" : selected >= 0 ? q.options.get(selected) : "";
        record(q, correct ? 2 : 0, hint, answer, false, correct);
        showQuestion();
    }

    void markWrong() {
        if (session == null || !session.feedback || !session.detail || !session.correct || session.corrected) return;
        Exercise q = pack.exercises.get(session.ids.get(session.index));
        if (q == null) return;
        session.corrected = true;
        session.correct = false;
        session.hinted = false;
        session.feedback = false;
        session.detail = false;
        session.selected = -1;
        if (!session.wrongIds.contains(q.id)) session.wrongIds.add(q.id);
        record(q, 0, false, "correction", true, false);
        showQuestion();
    }

    void next() {
        if (session == null || !session.feedback) return;
        session.index++;
        session.feedback = false;
        session.detail = false;
        session.correct = false;
        session.hinted = false;
        session.corrected = false;
        session.selected = -1;
        try {
            store.round(SESSION_KIND, session.encode().toString());
        } catch (Exception e) {
            error("无法保存学习进度：" + e.getMessage());
            return;
        }
        showQuestion();
    }

    void record(Exercise q, int rating, boolean hinted, String answer, boolean correction, boolean completed) {
        String cue = q.sentenceIds.isEmpty() ? "" : q.sentenceIds.get(0);
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (q.kpIds.isEmpty()) targets.add(q.id);
        for (String id : q.kpIds) if (pack.knowledge.containsKey(id)) targets.add(id);
        try {
            for (String kpId : targets) {
                boolean ok = store.roundAnswer(
                        UUID.randomUUID().toString(), kpId, q.type, rating, hinted,
                        answer, cue, SESSION_KIND, session.encode().toString(),
                        completed && !hinted, correction, System.currentTimeMillis());
                if (!ok) {
                    error("这次作答未写入：事件编号重复。");
                    return;
                }
            }
        } catch (Exception e) {
            error("无法保存这次作答：" + e.getMessage());
        }
    }

    void showSummary() {
        page = "summary";
        int total = session == null ? 0 : session.ids.size();
        int first = session == null ? 0 : session.firstCorrect;
        ArrayList<String> wrong = session == null ? new ArrayList<>() : new ArrayList<>(session.wrongIds);
        try {
            store.round(SESSION_KIND, null);
        } catch (Exception e) {
            error("无法清理学习组：" + e.getMessage());
        }
        session = null;
        shell(false);
        addSpacer(30);
        TextView title = label("本组完成", 26, INK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        addSpacer(16);
        TextView score = label("首答正确 " + first + " / " + total, 20, INK);
        score.setGravity(Gravity.CENTER);
        TextView wrongLine = label("错题 " + wrong.size() + " 题", 14, MUTED);
        wrongLine.setGravity(Gravity.CENTER);
        addSpacer(22);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        if (!wrong.isEmpty()) {
            dockAction(actions, "再练错题 · " + wrong.size() + " 题", ORANGE, () -> startSession(wrong));
        }
        dockAction(actions, "返回首页", MUTED, this::backFromButton);
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(62)));
        addSpacer(12);
    }

    void showReview() {
        page = "review";
        review = null;
        ArrayList<String> due = reviewIds(true);
        if (due.isEmpty()) {
            ArrayList<String> learned = reviewIds(false);
            shell(false);
            header("复习", "", this::backFromButton);
            addSpacer(50);
            TextView title = text(learned.isEmpty() ? "还没有学过的知识点" : "暂无到期知识点", 20, INK);
            title.setGravity(Gravity.CENTER);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            root.addView(title, new LinearLayout.LayoutParams(-1, -2));
            addSpacer(20);
            if (learned.isEmpty()) {
                primary("去学习", this::startDefault);
            } else {
                primary("提前复习 · " + learned.size() + " 个已学", () -> startReview(learned));
            }
            return;
        }
        startReview(due);
    }

    ArrayList<String> reviewIds(boolean dueOnly) {
        ArrayList<String> ids = new ArrayList<>();
        String sql = "SELECT id FROM state WHERE stage>0 AND suspended=0"
                + (dueOnly ? " AND due<=?" : "") + " ORDER BY due LIMIT 20";
        String[] args = dueOnly ? new String[]{String.valueOf(System.currentTimeMillis())} : null;
        try (Cursor c = store.getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                if (pack.knowledge.containsKey(id) && !ids.contains(id)) ids.add(id);
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    void startReview(List<String> ids) {
        review = new ReviewSession();
        review.revision = pack.revision;
        review.ids.addAll(new LinkedHashSet<>(ids));
        try {
            store.round(REVIEW_KIND, review.encode().toString());
        } catch (Exception e) {
            error("无法保存复习进度：" + e.getMessage());
            return;
        }
        showReviewQuestion();
    }

    void showReviewQuestion() {
        if (review == null || review.index >= review.ids.size()) {
            showReviewSummary();
            return;
        }
        Knowledge kp = pack.knowledge.get(review.ids.get(review.index));
        if (kp == null) {
            review.index++;
            showReviewQuestion();
            return;
        }
        page = "review";
        revealNext = review.feedback;
        shell(true);
        topbar((review.index + 1) + "/" + review.ids.size(), kp.id, true);
        addSpacer(12);
        LinearLayout card = card();
        studyCardBackground(card);
        card.addView(cardTag(kp.type));
        Sentence sentence = pack.sentences.get(kp.sentenceId);
        if (sentence != null) {
            SpannableStringBuilder ctx = new SpannableStringBuilder();
            appendHighlighted(ctx, sentence.text, kp.surface, kp.pinyin);
            TextView sentenceView = serif(ctx, 17, INK);
            sentenceView.setLineSpacing(dp(5), 1f);
            sentenceView.setPadding(0, dp(8), 0, 0);
            card.addView(sentenceView);
        } else if (kp.surface != null && !kp.surface.isEmpty()) {
            TextView surface = serif(kp.surface, 20, INK);
            surface.setPadding(0, dp(8), 0, 0);
            card.addView(surface);
        }
        TextView source = text("《" + pack.title + "》 · " + pack.author, 11, MUTED);
        source.setPadding(0, dp(8), 0, 0);
        card.addView(source);
        if (review.feedback) {
            renderReviewFeedback(kp);
        }
        renderReviewDock(kp);
    }

    void renderReviewFeedback(Knowledge kp) {
        addSpacer(14);
        LinearLayout card = card();
        card.addView(cardTag("释义"));
        TextView gloss = serif(kp.gloss, 22, INK);
        gloss.setTypeface(Typeface.SERIF, Typeface.BOLD);
        gloss.setPadding(0, dp(6), 0, 0);
        card.addView(gloss);
        TextView explanation = text(kp.explanation, 14, INK);
        explanation.setLineSpacing(dp(4), 1f);
        explanation.setPadding(0, dp(8), 0, 0);
        card.addView(explanation);
    }

    void renderReviewDock(Knowledge kp) {
        dock.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        if (!review.feedback) {
            dockAction(row, "能解释", MUTED, () -> assessReview(2));
            dockAction(row, "有点模糊", MUTED, () -> assessReview(1));
            dockAction(row, "忘了", MUTED, () -> assessReview(0));
        } else {
            boolean last = review.index + 1 >= review.ids.size();
            dockAction(row, last ? "完成复习" : "下一个", ORANGE, this::nextReview);
            if (review.rating == 2 && !review.corrected) {
                dockAction(row, "记错了", MUTED, this::markReviewWrong);
            }
        }
        dock.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    void assessReview(int rating) {
        if (review == null || review.feedback) return;
        Knowledge kp = pack.knowledge.get(review.ids.get(review.index));
        if (kp == null) return;
        review.feedback = true;
        review.rating = rating;
        review.corrected = false;
        recordReview(kp, rating, false, rating == 2);
        showReviewQuestion();
    }

    void markReviewWrong() {
        if (review == null || !review.feedback || review.rating != 2 || review.corrected) return;
        Knowledge kp = pack.knowledge.get(review.ids.get(review.index));
        if (kp == null) return;
        review.corrected = true;
        review.rating = -1;
        review.feedback = false;
        recordReview(kp, 0, true, false);
        showReviewQuestion();
    }

    void nextReview() {
        if (review == null || !review.feedback) return;
        review.index++;
        review.feedback = false;
        review.rating = -1;
        review.corrected = false;
        try {
            store.round(REVIEW_KIND, review.encode().toString());
        } catch (Exception e) {
            error("无法保存复习进度：" + e.getMessage());
            return;
        }
        showReviewQuestion();
    }

    void recordReview(Knowledge kp, int rating, boolean correction, boolean completed) {
        try {
            boolean ok = store.roundAnswer(
                    UUID.randomUUID().toString(), kp.id, "review_self", rating, false,
                    "self-" + rating, kp.sentenceId, REVIEW_KIND, review.encode().toString(),
                    completed && !correction, correction, System.currentTimeMillis());
            if (!ok) error("这次复习未写入：事件编号重复。");
        } catch (Exception e) {
            error("无法保存这次复习：" + e.getMessage());
        }
    }

    void showReviewSummary() {
        int total = review == null ? 0 : review.ids.size();
        try {
            store.round(REVIEW_KIND, null);
        } catch (Exception e) {
            error("无法清理复习组：" + e.getMessage());
        }
        review = null;
        page = "reviewSummary";
        shell(false);
        addSpacer(60);
        TextView title = text("复习完成", 26, INK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView count = text("本轮复习 " + total + " 个知识点", 14, MUTED);
        count.setGravity(Gravity.CENTER);
        count.setPadding(0, dp(12), 0, 0);
        root.addView(count, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(24);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        dockAction(actions, "再复习一轮", ORANGE, this::showReview);
        dockAction(actions, "返回首页", MUTED, this::backFromButton);
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    void showText() {
        page = "text";
        shell(false);
        header(WordLexicon.title(pack.title) + "原译文", "", this::backFromButton);
        addSpacer(8);
        LinearLayout article=card();article.setTag("full-text-card");
        article.setPadding(dp(22),dp(24),dp(22),dp(26));
        ArrayList<String> paragraphs=readingParagraphs();
        ArrayList<String> displayParagraphs=readerDisplayParagraphs(paragraphs);
        for(int i=0;i<displayParagraphs.size();i++){
            TextView body=serif("　　"+displayParagraphs.get(i),18,INK);
            body.setLineSpacing(dp(8),1f);body.setTextIsSelectable(true);
            if(i>0)body.setPadding(0,dp(12),0,0);
            article.addView(body,new LinearLayout.LayoutParams(-1,-2));
        }
        try{
            JSONObject translations=new JSONObject(readAsset("reading-translations.json"));String key=pack.textId;
            JSONObject translated=translations.getJSONObject("texts").optJSONObject(key);
            if(translated==null)translated=translations.getJSONObject("texts").optJSONObject("textbook-"+key);
            if(translated!=null){StringBuilder original=new StringBuilder();for(String part:paragraphs)original.append(part);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(original.toString().getBytes("UTF-8")))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(hash.toString().equals(translated.getString("original_sha256"))){
                    addSpacer(16);LinearLayout translation=card();translation.setTag("full-translation-card");translation.setPadding(dp(22),dp(24),dp(22),dp(26));TextView heading=text("译文",15,MUTED);heading.setPadding(0,0,0,dp(16));translation.addView(heading);
                    JSONArray ps=translated.getJSONArray("paragraphs");for(int n=0;n<ps.length();n++){TextView body=serif("　　"+ps.getString(n),18,INK);body.setLineSpacing(dp(8),1f);body.setTextIsSelectable(true);if(n>0)body.setPadding(0,dp(12),0,0);translation.addView(body,new LinearLayout.LayoutParams(-1,-2));}
                }
            }
        }catch(Exception error){android.util.Log.w("WenyanReader","Translation unavailable",error);}
        if(host.hasLearningPack(pack.textId)){
            TextView start=text("篇目练习  ›",13,ORANGE);start.setGravity(Gravity.END);
            start.setPadding(0,dp(18),0,dp(12));start.setContentDescription("开始学习"+pack.title);
            start.setOnClickListener(v->host.openWenyan("learn",pack.textId));root.addView(start);
        }
        // embedded text page has no bottom navigation
    }

    private ArrayList<String> readerDisplayParagraphs(ArrayList<String> original){
        StringBuilder full=new StringBuilder();for(String part:original)full.append(part);
        // Anthology section citations close the preceding chapter, even when PDF extraction attached them to the next sentence.
        ArrayList<String> result=new ArrayList<>();java.util.regex.Matcher m=java.util.regex.Pattern.compile("[（(]《[^》]+》[）)]").matcher(full);int from=0;
        while(m.find()){String part=full.substring(from,m.end()).trim();if(!part.isEmpty())result.add(part);from=m.end();}
        if(result.size()>=2){if(from<full.length())result.add(full.substring(from));return result;}return original;
    }
    private String readerVolume(){if(host.libraryTexts!=null)for(int n=0;n<host.libraryTexts.length();n++){JSONObject t=host.libraryTexts.optJSONObject(n);if(pack.textId.equals(t.optString("id"))){String volume=t.optString("volume");for(int k=0;k<5;k++)if(WordVolumes.NAMES[k].equals(volume))return volume;}}return "拓展篇目";}

    private ArrayList<String> readingParagraphs(){
        ArrayList<String> paragraphs=new ArrayList<>();
        try{
            if(host.libraryTexts!=null)for(int n=0;n<host.libraryTexts.length();n++){
                JSONObject source=host.libraryTexts.getJSONObject(n);if(!pack.textId.equals(source.optString("id")))continue;
                JSONArray sections=source.getJSONArray("sections");StringBuilder full=new StringBuilder();
                for(int i=0;i<sections.length();i++){JSONArray ps=sections.getJSONObject(i).getJSONArray("paragraphs");
                    for(int j=0;j<ps.length();j++){String body=ps.getJSONObject(j).getString("text");paragraphs.add(body);full.append(body);}}
                if(source.optBoolean("textbook")){
                    JSONObject layout=new JSONObject(readAsset("paragraph-layout.json")).getJSONObject("texts").optJSONObject(pack.textId);
                    if(layout!=null){
                        StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(full.toString().getBytes("UTF-8")))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                        if(hash.toString().equals(layout.optString("text_sha256"))){JSONArray starts=layout.getJSONArray("starts");paragraphs.clear();
                            for(int i=0;i<starts.length();i++){int from=starts.getInt(i),to=i+1<starts.length()?starts.getInt(i+1):full.length();paragraphs.add(full.substring(from,to));}}
                    }
                }
                return paragraphs;
            }
        }catch(Exception e){android.util.Log.w("WenyanReader","Using original paragraph layout",e);if(!paragraphs.isEmpty())return paragraphs;}
        String last="";StringBuilder body=new StringBuilder();
        for(Sentence sentence:pack.sentences.values()){
            if(body.length()>0&&!last.equals(sentence.section)){paragraphs.add(body.toString());body.setLength(0);}
            body.append(sentence.text);last=sentence.section;
        }
        if(body.length()>0)paragraphs.add(body.toString());return paragraphs;
    }

    void showStats() {
        page = "stats";
        shell(true);
        header("学习记录", "", this::backFromButton);
        addSpacer(16);
        int today = store.count("SELECT count(*) FROM events WHERE at>=?", midnight());
        int first = store.count("SELECT count(*) FROM first_attempt WHERE rowid>?", 0);
        int started = store.count("SELECT count(*) FROM state WHERE started>?", 0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row, new LinearLayout.LayoutParams(-1, dp(104)));
        statCard(row, "今日作答", today);
        statCard(row, "累计首答", first);
        statCard(row, "开始学习", started);
        bottomNav();
    }

    void statCard(LinearLayout row, String label, int value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(10), dp(8), dp(10));
        GradientDrawable cardBg = bg(0xeeffffff, 16);
        cardBg.setStroke(dp(1), 0x12000000);
        card.setBackground(cardBg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(card, lp);
        TextView number = text(String.valueOf(value), 22, ORANGE);
        number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        card.addView(number);
        TextView name = text(label, 11, MUTED);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(4), 0, 0);
        card.addView(name);
    }

    void shell(boolean withDock) {
        String hostPage;
        if (page.equals("text")) hostPage = "text";
        else if (page.equals("summary") || page.equals("reviewSummary")) hostPage = "summary";
        else if (page.equals("stats")) hostPage = "stats";
        else if (page.equals("home")) hostPage = "home";
        else hostPage = "study";
        host.wenyanReveal = revealNext;
        revealNext = false;
        host.screen(hostPage);
        root = host.root;
        dock = host.dock;
        scroll = (ScrollView) root.getParent();
        if (!withDock) dock.setVisibility(View.GONE);
        host.backAction = this::back;
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    GradientDrawable bg(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    Button action(String text, Runnable run) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(INK);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18000000), null, null));
        b.setStateListAnimator(null);
        b.setMinHeight(dp(48));
        b.setOnClickListener(v -> run.run());
        return b;
    }

    TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    void studyCardBackground(LinearLayout card) {
        GradientDrawable g = bg(0x4df8f4ec, 16);
        g.setStroke(dp(1), 0x12000000);
        card.setBackground(g);
    }

    LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable cardBg = bg(CARD, 16);
        cardBg.setStroke(dp(1), 0x12000000);
        card.setBackground(cardBg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        root.addView(card, lp);
        return card;
    }

    TextView cardTag(String value) {
        TextView t = text(value, 11, ORANGE);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    TextView serif(CharSequence value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(3), 1f);
        t.setTypeface(Typeface.SERIF);
        return t;
    }

    TextView label(String value, int size, int color) {
        TextView t = text(value, size, color);
        root.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    void note(String value) {
        TextView t = label(value, 11, 0xff4b5a63);
        t.setShadowLayer(dp(2), 0, dp(1), 0xccffffff);
    }

    void addSpacer(int height) {
        View v = new View(this);
        root.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    void primary(String value, Runnable run) {
        Button b = action(value, run);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(bg(ORANGE, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(10);
        root.addView(b, p);
    }

    void secondary(String value, Runnable run) {
        Button b = action(value, run);
        b.setTextColor(INK);
        b.setBackground(bg(0xccffffff, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(9);
        root.addView(b, p);
    }

    Knowledge firstKnowledge(Exercise q) {
        for (String id : q.kpIds) {
            Knowledge k = pack.knowledge.get(id);
            if (k != null) return k;
        }
        return null;
    }

    void error(String message) {
        new AlertDialog.Builder(this)
                .setTitle("暂时无法完成")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    long midnight() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

}
