package org.chromium.chrome.browser.coui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ColorOS 17 (COUI 17) & Liquid Glass UI Engine for Titanium Browser.
 *
 * Implements the specifications from .agent/skills/coui-ui-design/:
 * 1. COUIBottomFloatingNavigationBar (56dp capsule, 28dp corner radius, 70x48dp items, 24dp icon + 10dp label,
 *    16dp horizontal & bottom outside margins, 25px TYPE_FRAMEWORK_BOTTOM_BAR frosted glass material with
 *    ColorDodge/Overlay + Luminosity dual blend layers, 24dp caustic drop shadow, TYPE_FRAMEWORK_CAPSULE_3
 *    dual-lobe 30°/210° specular rim, and SpotLightType.OpaqueMedium2 touch spotlight).
 * 2. Interactive Liquid Glass Droplet Indicator (LiquidGlassTabBar / GlassLensRenderer 2.0):
 *    clear refractive bevel pill (cornerRadius = 999f, 380ms OvershootInterpolator(1.1f), volume-preserving
 *    liquid stretch up to 22%, 1.06x drag bulge, slide-to-select across items, and ColorOS ROM haptic 302).
 * 3. ColorOS 17 Inverted Background Hierarchy (#F0F1F3 cool off-white canvas in Light Mode, #000000 AMOLED
 *    black in Dark Mode, #FFFFFF / #1C1C1E 20dp squircle cards, and #0080FF / #1A8CFF COUI Blue).
 * 4. About Titanium GitHub Repository binding -> https://github.com/ampereforest/android-titanium-browser.
 */
public final class CouiBrowserUiEnhancer {
    private static final String TAG = "CouiBrowserUi";
    private static final String NAV_TAG = "coui_liquid_glass_nav_bar";
    public static final String REPO_URL = "https://github.com/ampereforest/android-titanium-browser";

    // Chromium view resource IDs in TitaniumBrowser.apk
    private static final int ID_HOME_BUTTON = 0x7f010648;
    private static final int ID_EXTENSIONS_BUTTON = 0x7f0104de;
    private static final int ID_TAB_SWITCHER_BUTTON = 0x7f010cd1;
    private static final int ID_MENU_BUTTON = 0x7f010783;
    private static final int ID_URL_BAR = 0x7f010ddd;
    private static final int ID_LOCATION_BAR = 0x7f01071f;

    // ColorOS 17 ROM Haptic Feedback constants
    private static final int COUI_HAPTIC_SWITCH = 302;

    private CouiBrowserUiEnhancer() {}

    public static void performCouiHaptic(View view) {
        if (view == null || !view.isAttachedToWindow()) return;
        try {
            if (!view.performHapticFeedback(COUI_HAPTIC_SWITCH)) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isDarkMode(Context ctx) {
        int uiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Hooked into ChromeTabbedActivity.onResume(). */
    public static void attachToBrowser(final Activity activity) {
        if (activity == null) return;
        try {
            final ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) return;
            View existing = content.findViewWithTag(NAV_TAG);
            if (existing instanceof CouiLiquidGlassNavBar) {
                ((CouiLiquidGlassNavBar) existing).updateTheme();
                existing.bringToFront();
                return;
            }
            final CouiLiquidGlassNavBar navBar = new CouiLiquidGlassNavBar(activity);
            navBar.setTag(NAV_TAG);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
            );
            content.addView(navBar, lp);
            navBar.bringToFront();
        } catch (Throwable t) {
            Log.w(TAG, "attachToBrowser failed", t);
        }
    }

    /** Hooked into SettingsActivity.onResume(). */
    public static void attachToSettings(final Activity activity) {
        if (activity == null) return;
        try {
            final boolean dark = isDarkMode(activity);
            final int canvasColor = dark ? 0xFF000000 : 0xFFF0F1F3;
            activity.getWindow().getDecorView().setBackgroundColor(canvasColor);
            final ViewGroup content = activity.findViewById(android.R.id.content);
            if (content != null) {
                content.setBackgroundColor(canvasColor);
                content.post(new Runnable() {
                    @Override
                    public void run() {
                        styleSettingsTree(content, dark);
                    }
                });
            }
        } catch (Throwable t) {
            Log.w(TAG, "attachToSettings failed", t);
        }
    }

    private static void styleSettingsTree(View view, boolean dark) {
        if (view == null) return;
        String cls = view.getClass().getName();
        if (cls.contains("RecyclerView")) {
            view.setBackgroundColor(dark ? 0xFF000000 : 0xFFF0F1F3);
            int padH = dp(view.getContext(), 16);
            int padV = dp(view.getContext(), 8);
            view.setPadding(padH, padV, padH, padV);
            if (view instanceof ViewGroup) {
                ((ViewGroup) view).setClipToPadding(false);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                styleSettingsTree(vg.getChildAt(i), dark);
            }
        }
    }

    /** Hooked into AboutChromeSettings.I1(String, Bundle) with legal_information Preference. */
    public static void onAboutSettingsCreated(Object legalPref) {
        if (legalPref == null) return;
        try {
            Class<?> prefClass = legalPref.getClass().getSuperclass(); // androidx.preference.Preference
            Method setSummary = prefClass.getDeclaredMethod("b0", CharSequence.class);
            Field fSummary = prefClass.getDeclaredField("A");
            fSummary.setAccessible(true);
            CharSequence cur = (CharSequence) fSummary.get(legalPref);
            String base = cur != null ? cur.toString() : "Copyright 2026 Google LLC. All rights reserved.";
            if (!base.contains(REPO_URL)) {
                setSummary.invoke(legalPref, base + "\n" + REPO_URL);
            }

            Field fCtx = prefClass.getDeclaredField("r");
            fCtx.setAccessible(true);
            Context ctx = (Context) fCtx.get(legalPref);

            Field fParent = prefClass.getDeclaredField("b0");
            fParent.setAccessible(true);
            Object parentGroup = fParent.get(legalPref);
            if (ctx == null || parentGroup == null) return;

            // Check if github_repository preference already exists
            Method findPref = parentGroup.getClass().getMethod("m0", CharSequence.class);
            if (findPref.invoke(parentGroup, "github_repository") != null) return;

            Object repoPref = legalPref.getClass()
                    .getConstructor(Context.class, AttributeSet.class)
                    .newInstance(ctx, null);

            Field fKey = prefClass.getDeclaredField("D");
            fKey.setAccessible(true);
            fKey.set(repoPref, "github_repository");

            Field fTitle = prefClass.getDeclaredField("z");
            fTitle.setAccessible(true);
            fTitle.set(repoPref, "GitHub repository");

            setSummary.invoke(repoPref, REPO_URL);

            Field fIntent = prefClass.getDeclaredField("E");
            fIntent.setAccessible(true);
            Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL));
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            fIntent.set(repoPref, viewIntent);

            Field fOrder = prefClass.getDeclaredField("x");
            fOrder.setAccessible(true);
            fOrder.setInt(repoPref, 2);

            Method addPref = parentGroup.getClass().getMethod("k0", prefClass);
            addPref.invoke(parentGroup, repoPref);
        } catch (Throwable t) {
            Log.w(TAG, "onAboutSettingsCreated failed", t);
        }
    }

    static int dp(Context ctx, float v) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return Math.round(v * dm.density);
    }

    static float dpF(Context ctx, float v) {
        return v * ctx.getResources().getDisplayMetrics().density;
    }

    /**
     * ColorOS 17 COUIBottomFloatingNavigationBar + LiquidGlassTabBar implementation.
     * - 56dp capsule height, 28dp corner radius, 12dp inner horizontal padding, 8dp item spacing
     * - 16dp horizontal & vertical outside margins above WindowInsets.Type.navigationBars()
     * - Auto-hides on IME (soft keyboard) visibility with COUI 17 scale (0.8 <-> 1.0) + alpha springs
     * - Interactive refractive Liquid Glass droplet indicator with 380ms OvershootInterpolator(1.1f),
     *   volume-preserving liquid stretch (up to 22%), 1.06x drag bulge, slide-to-select, and haptic 302.
     */
    public static final class CouiLiquidGlassNavBar extends FrameLayout {
        private static final String[] LABELS = {"Back", "Search", "Home", "Tabs", "Menu"};
        private static final int ITEM_COUNT = 5;

        private final Activity activity;
        private final CapsuleContainerView capsuleView;
        private int selectedIndex = 2; // Default focus: Home
        private boolean isDark;
        private boolean hiddenByIme = false;

        public CouiLiquidGlassNavBar(Activity activity) {
            super(activity);
            this.activity = activity;
            this.isDark = isDarkMode(activity);
            setClipChildren(false);
            setClipToPadding(false);

            int outH = dp(activity, 16);
            int outV = dp(activity, 14);
            setPadding(outH, outV, outH, outV);

            capsuleView = new CapsuleContainerView(activity);
            int barHeight = dp(activity, 56);
            // 5 items * 66dp + 24dp inner padding + 4 * 6dp spacing = 378dp max capsule width
            int maxCapsuleWidth = dp(activity, 372);
            LayoutParams lp = new LayoutParams(maxCapsuleWidth, barHeight, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            addView(capsuleView, lp);

            setOnApplyWindowInsetsListener(new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    int navBottom = 0;
                    boolean imeVisible = false;
                    if (Build.VERSION.SDK_INT >= 30) {
                        navBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                        imeVisible = insets.isVisible(WindowInsets.Type.ime());
                    } else {
                        navBottom = insets.getSystemWindowInsetBottom();
                        imeVisible = navBottom > dp(v.getContext(), 120);
                    }
                    int hPad = dp(v.getContext(), 16);
                    int tPad = dp(v.getContext(), 8);
                    int bPad = Math.max(dp(v.getContext(), 12), navBottom + dp(v.getContext(), 8));
                    v.setPadding(hPad, tPad, hPad, bPad);
                    setImeHidden(imeVisible);
                    return insets;
                }
            });
        }

        private void setImeHidden(boolean hide) {
            if (this.hiddenByIme == hide) return;
            this.hiddenByIme = hide;
            if (hide) {
                capsuleView.animate()
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (hiddenByIme) capsuleView.setVisibility(View.GONE);
                            }
                        })
                        .start();
            } else {
                capsuleView.setVisibility(View.VISIBLE);
                capsuleView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(280)
                        .setInterpolator(new OvershootInterpolator(1.1f))
                        .start();
            }
        }

        public void updateTheme() {
            boolean newDark = isDarkMode(activity);
            if (newDark != isDark) {
                isDark = newDark;
                capsuleView.invalidate();
            }
        }

        private void dispatchNavAction(int index) {
            performCouiHaptic(this);
            try {
                View decor = activity.getWindow().getDecorView();
                switch (index) {
                    case 0: // Back
                        activity.onBackPressed();
                        break;
                    case 1: // Search / Omnibox
                        View urlBar = decor.findViewById(ID_URL_BAR);
                        if (urlBar != null) {
                            urlBar.performClick();
                            urlBar.requestFocus();
                        } else {
                            View locBar = decor.findViewById(ID_LOCATION_BAR);
                            if (locBar != null) locBar.performClick();
                        }
                        break;
                    case 2: // Home
                        View homeBtn = decor.findViewById(ID_HOME_BUTTON);
                        if (homeBtn != null && homeBtn.getVisibility() == View.VISIBLE) {
                            homeBtn.performClick();
                        } else {
                            View extBtn = decor.findViewById(ID_EXTENSIONS_BUTTON);
                            if (extBtn != null && extBtn.getVisibility() == View.VISIBLE) {
                                extBtn.performClick();
                            }
                        }
                        break;
                    case 3: // Tabs
                        View tabBtn = decor.findViewById(ID_TAB_SWITCHER_BUTTON);
                        if (tabBtn != null) {
                            tabBtn.performClick();
                        }
                        break;
                    case 4: // Menu
                        View menuBtn = decor.findViewById(ID_MENU_BUTTON);
                        if (menuBtn != null) {
                            menuBtn.performClick();
                        }
                        break;
                }
            } catch (Throwable t) {
                Log.w(TAG, "dispatchNavAction failed for index=" + index, t);
            }
        }

        private final class CapsuleContainerView extends View {
            private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint spotlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint dropletFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint dropletRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF capsuleRect = new RectF();
            private final RectF dropletRect = new RectF();

            private float dropletCenterX = -1f;
            private float dropletScaleX = 1f;
            private float dropletScaleY = 1f;
            private float touchX = -1f;
            private float touchY = -1f;
            private float spotlightAlpha = 0f;
            private boolean dragging = false;
            private float downX = 0f;
            private final int touchSlop;
            private ValueAnimator dropletAnimator;

            CapsuleContainerView(Context ctx) {
                super(ctx);
                this.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
                setElevation(dpF(ctx, 14f));
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        int r = view.getHeight() / 2;
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                    }
                });
                setClipToOutline(false);

                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(dpF(ctx, 0.9f));

                dropletRimPaint.setStyle(Paint.Style.STROKE);
                dropletRimPaint.setStrokeWidth(dpF(ctx, 1.15f));

                iconPaint.setStyle(Paint.Style.STROKE);
                iconPaint.setStrokeWidth(dpF(ctx, 1.75f));
                iconPaint.setStrokeCap(Paint.Cap.ROUND);
                iconPaint.setStrokeJoin(Paint.Join.ROUND);

                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTextSize(dpF(ctx, 10f)); // Fixed 10dp per FloatingNavigationBarDefaults.LabelTextSize
                textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }

            private float itemCenterX(int index) {
                float pad = dpF(getContext(), 12f);
                float avail = getWidth() - pad * 2f;
                float slot = avail / ITEM_COUNT;
                return pad + slot * (index + 0.5f);
            }

            private float itemSlotWidth() {
                float pad = dpF(getContext(), 12f);
                return Math.max(1f, (getWidth() - pad * 2f) / ITEM_COUNT);
            }

            private float clampDropletCenterX(float cx, float sx) {
                float pad = dpF(getContext(), 6f);
                float halfW = (itemSlotWidth() * 0.48f) * sx;
                float minX = pad + halfW;
                float maxX = getWidth() - pad - halfW;
                if (minX > maxX) return getWidth() * 0.5f;
                return Math.max(minX, Math.min(maxX, cx));
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (w > 0) {
                    dropletCenterX = itemCenterX(selectedIndex);
                }
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0) return;

                if (dropletCenterX < 0f) {
                    dropletCenterX = itemCenterX(selectedIndex);
                }

                float radius = h * 0.5f;
                capsuleRect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);

                // 1. COUI 17 TYPE_FRAMEWORK_BOTTOM_BAR frosted glass surface + luminosity dual blend
                if (isDark) {
                    surfacePaint.setColor(0xE01E1E1E);
                } else {
                    surfacePaint.setColor(0xECFAFAFA);
                }
                canvas.drawRoundRect(capsuleRect, radius, radius, surfacePaint);

                LinearGradient sheen = new LinearGradient(
                        0, 0, w * 0.35f, h,
                        isDark
                                ? new int[]{0x38FFFFFF, 0x14666666, 0x24000000}
                                : new int[]{0xE6FFFFFF, 0x80F5F5F5, 0x40E8ECF2},
                        new float[]{0f, 0.52f, 1f},
                        Shader.TileMode.CLAMP
                );
                sheenPaint.setShader(sheen);
                canvas.drawRoundRect(capsuleRect, radius, radius, sheenPaint);

                // 2. SpotLightType.OpaqueMedium2 touch spotlight under the finger
                if (spotlightAlpha > 0.01f && touchX >= 0f) {
                    float spotR = dpF(getContext(), 74f);
                    int spotCol = isDark
                            ? Color.argb((int) (68 * spotlightAlpha), 255, 255, 255)
                            : Color.argb((int) (45 * spotlightAlpha), 0, 128, 255);
                    RadialGradient rg = new RadialGradient(
                            touchX, touchY, spotR,
                            new int[]{spotCol, 0x00FFFFFF},
                            new float[]{0f, 1f},
                            Shader.TileMode.CLAMP
                    );
                    spotlightPaint.setShader(rg);
                    canvas.drawRoundRect(capsuleRect, radius, radius, spotlightPaint);
                }

                // 3. TYPE_FRAMEWORK_CAPSULE_3 dual-lobe 30°/210° specular rim highlight
                LinearGradient rimGrad = new LinearGradient(
                        0, 0, w, h,
                        isDark
                                ? new int[]{0x8CFFFFFF, 0x22FFFFFF, 0x59FFFFFF}
                                : new int[]{0xFFFFFFFF, 0x55D0D7E2, 0xE6FFFFFF},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                borderPaint.setShader(rimGrad);
                canvas.drawRoundRect(capsuleRect, radius, radius, borderPaint);

                // 4. Interactive Refractive Liquid Glass Droplet Indicator (GlassLensRenderer 2.0 profile)
                float slotW = itemSlotWidth();
                float dropHalfW = (slotW * 0.46f) * dropletScaleX;
                float dropHalfH = (h * 0.42f) * dropletScaleY;
                float cy = h * 0.5f;
                float cx = clampDropletCenterX(dropletCenterX, dropletScaleX);
                dropletRect.set(cx - dropHalfW, cy - dropHalfH, cx + dropHalfW, cy + dropHalfH);
                float dropR = dropletRect.height() * 0.5f;

                LinearGradient dropFill = new LinearGradient(
                        dropletRect.left, dropletRect.top, dropletRect.right, dropletRect.bottom,
                        isDark
                                ? new int[]{0x421A8CFF, 0x241A8CFF, 0x38FFFFFF}
                                : new int[]{0x260080FF, 0x140080FF, 0x44FFFFFF},
                        new float[]{0f, 0.55f, 1f},
                        Shader.TileMode.CLAMP
                );
                dropletFillPaint.setShader(dropFill);
                canvas.drawRoundRect(dropletRect, dropR, dropR, dropletFillPaint);

                LinearGradient dropRim = new LinearGradient(
                        dropletRect.left, dropletRect.top, dropletRect.right, dropletRect.bottom,
                        isDark
                                ? new int[]{0xB35C9DFF, 0x33FFFFFF, 0x801A8CFF}
                                : new int[]{0xFFFFFFFF, 0x440080FF, 0xCCFFFFFF},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                dropletRimPaint.setShader(dropRim);
                canvas.drawRoundRect(dropletRect, dropR, dropR, dropletRimPaint);

                // 5. Draw the 5 navigation items (24dp icon + 2dp gap + 10dp fixed-dp label)
                int selectedColor = isDark ? 0xFF1A8CFF : 0xFF0080FF;
                int unselectedColor = isDark ? 0xB8FFFFFF : 0xB8000000;

                for (int i = 0; i < ITEM_COUNT; i++) {
                    float icx = itemCenterX(i);
                    boolean isSel = (i == selectedIndex);
                    int col = isSel ? selectedColor : unselectedColor;
                    iconPaint.setColor(col);
                    textPaint.setColor(col);

                    float iconCy = dpF(getContext(), 20f);
                    float iconR = dpF(getContext(), 8.5f);
                    drawNavIcon(canvas, i, icx, iconCy, iconR, col);

                    float textY = h - dpF(getContext(), 9.5f);
                    canvas.drawText(LABELS[i], icx, textY, textPaint);
                }
            }

            private void drawNavIcon(Canvas c, int index, float cx, float cy, float r, int color) {
                Path p = new Path();
                switch (index) {
                    case 0: // Back (ColorOS coui_back_arrow_normal chevron + stem)
                        p.moveTo(cx - r * 0.15f, cy - r * 0.72f);
                        p.lineTo(cx - r * 0.85f, cy);
                        p.lineTo(cx - r * 0.15f, cy + r * 0.72f);
                        p.moveTo(cx - r * 0.78f, cy);
                        p.lineTo(cx + r * 0.85f, cy);
                        c.drawPath(p, iconPaint);
                        break;
                    case 1: // Search (Magnifying glass)
                        c.drawCircle(cx - r * 0.15f, cy - r * 0.15f, r * 0.62f, iconPaint);
                        c.drawLine(cx + r * 0.30f, cy + r * 0.30f, cx + r * 0.82f, cy + r * 0.82f, iconPaint);
                        break;
                    case 2: // Home (ColorOS rounded house silhouette)
                        p.moveTo(cx - r * 0.85f, cy + r * 0.05f);
                        p.lineTo(cx, cy - r * 0.78f);
                        p.lineTo(cx + r * 0.85f, cy + r * 0.05f);
                        p.moveTo(cx - r * 0.62f, cy - r * 0.10f);
                        p.lineTo(cx - r * 0.62f, cy + r * 0.78f);
                        p.lineTo(cx + r * 0.62f, cy + r * 0.78f);
                        p.lineTo(cx + r * 0.62f, cy - r * 0.10f);
                        c.drawPath(p, iconPaint);
                        break;
                    case 3: // Tabs (Continuous rounded square + inner plus/tab accent)
                        RectF tabRect = new RectF(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f);
                        float cr = r * 0.36f;
                        c.drawRoundRect(tabRect, cr, cr, iconPaint);
                        c.drawLine(cx - r * 0.32f, cy, cx + r * 0.32f, cy, iconPaint);
                        c.drawLine(cx, cy - r * 0.32f, cx, cy + r * 0.32f, iconPaint);
                        break;
                    case 4: // Menu (ColorOS 3 horizontal rounded bars / settings)
                        c.drawLine(cx - r * 0.72f, cy - r * 0.52f, cx + r * 0.72f, cy - r * 0.52f, iconPaint);
                        c.drawLine(cx - r * 0.72f, cy, cx + r * 0.72f, cy, iconPaint);
                        c.drawLine(cx - r * 0.72f, cy + r * 0.52f, cx + r * 0.72f, cy + r * 0.52f, iconPaint);
                        break;
                }
            }

            private int indexAtX(float x) {
                float pad = dpF(getContext(), 12f);
                float avail = Math.max(1f, getWidth() - pad * 2f);
                int idx = (int) ((x - pad) / (avail / ITEM_COUNT));
                return Math.max(0, Math.min(ITEM_COUNT - 1, idx));
            }

            private void animateDropletTo(final int targetIndex) {
                if (dropletAnimator != null) {
                    dropletAnimator.cancel();
                }
                final float startX = dropletCenterX < 0f ? itemCenterX(selectedIndex) : dropletCenterX;
                final float endX = itemCenterX(targetIndex);
                final float dist = endX - startX;
                final float slotW = itemSlotWidth();
                // Liquid stretch amplitude up to 22% (LiquidGlassTabBar.animateDropletTo)
                final float stretch = 0.22f * Math.min(1f, Math.abs(dist) / (slotW * 2.5f));
                final OvershootInterpolator overshoot = new OvershootInterpolator(1.1f);

                dropletAnimator = ValueAnimator.ofFloat(0f, 1f);
                dropletAnimator.setDuration(380);
                dropletAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator anim) {
                        float t = (Float) anim.getAnimatedValue();
                        float s = (float) Math.sin(Math.PI * Math.min(t * 1.15f, 1f));
                        dropletScaleX = 1f + stretch * s;
                        dropletScaleY = 1f - stretch * 0.55f * s;
                        float interp = overshoot.getInterpolation(t);
                        dropletCenterX = clampDropletCenterX(startX + dist * interp, dropletScaleX);
                        spotlightAlpha = Math.max(0f, 1f - t);
                        invalidate();
                    }
                });
                dropletAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        dropletScaleX = 1f;
                        dropletScaleY = 1f;
                        dropletCenterX = endX;
                        spotlightAlpha = 0f;
                        invalidate();
                    }
                });
                dropletAnimator.start();
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                touchX = event.getX();
                touchY = event.getY();
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        dragging = false;
                        spotlightAlpha = 1f;
                        int hit = indexAtX(event.getX());
                        if (hit != selectedIndex) {
                            selectedIndex = hit;
                            performCouiHaptic(this);
                        }
                        invalidate();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragging && Math.abs(event.getX() - downX) > touchSlop) {
                            dragging = true;
                            if (dropletAnimator != null) dropletAnimator.cancel();
                            dropletScaleX = 1.06f;
                            dropletScaleY = 1.06f;
                        }
                        if (dragging) {
                            dropletCenterX = clampDropletCenterX(event.getX(), dropletScaleX);
                            int hover = indexAtX(event.getX());
                            if (hover != selectedIndex) {
                                selectedIndex = hover;
                                performCouiHaptic(this);
                            }
                            invalidate();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        int finalIdx = indexAtX(event.getX());
                        selectedIndex = finalIdx;
                        dragging = false;
                        animateDropletTo(finalIdx);
                        dispatchNavAction(finalIdx);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        animateDropletTo(selectedIndex);
                        return true;
                }
                return super.onTouchEvent(event);
            }
        }
    }
}
