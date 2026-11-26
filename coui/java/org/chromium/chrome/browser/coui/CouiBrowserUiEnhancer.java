package org.chromium.chrome.browser.coui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.BlurMaskFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.SurfaceControl;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * ColorOS 17 (COUI 17) & Liquid Glass UI Engine for Titanium Browser (Round 2).
 *
 * Implements:
 * 1. 3-Piece Floating Liquid Glass Navigation Bar (1:1 ColorOS 17 Gallery parity):
 *    - Detached Left Glass Circle (56x56dp): Back
 *    - Floating Center Frosted Glass Pill (56dp tall, 12dp side gaps): 3 items (Search, Home, Tabs),
 *      filled solid black active icon + bold label (no permanent blob at rest), clean outline inactive icons,
 *      transient frosted glass sliding feedback strictly during active touch/drag.
 *    - Detached Right Glass Circle (56x56dp): Menu (opens bottom-right anchored ColorOS 17 Glass Flyout Menu;
 *      long-press opens Extensions).
 *    - Real 120fps SurfaceFlinger hardware blur over Chromium's live SurfaceView via
 *      SurfaceControl.Builder.setEffectLayer() + setBackgroundBlurRadius() + setCornerRadius()
 *      (InstaGlass VideoBlur technique), with clean bright frosted glass tint & specular rim.
 * 2. Bottom-Right Anchored ColorOS 17 Frosted Liquid Glass Flyout Menu:
 *    - 20dp squircle frosted glass card anchored directly above the bottom-right Menu glass circle,
 *      with SurfaceControl hardware blur, spring/bounce scale/fade animation from bottom-right pivot,
 *      quick action row (Forward, Bookmark, Download, Reload) and 10 browser actions wired natively
 *      to ChromeTabbedActivity.R2 (onMenuOrKeyboardAction).
 * 3. 100% ColorOS 17 Settings UI Parity:
 *    - Seamless status bar matching #F0F1F3 (Light) / #000000 (Dark) with proper icon contrast.
 *    - Floating 40dp Liquid Glass circular back button (coui_back_arrow_normal vector, ambient shadow, rim).
 *    - Scroll-under progressive frosted top-bar blur (RenderNode + Blur + AGSL vertical smoothstep feather)
 *      so preference cards scroll smoothly underneath the back button and title with zero hard seam.
 *    - Unified 20dp #FFFFFF / #1C1C1E squircle grouped cards, 16dp-inset 1dp dividers, text-only rows,
 *      COUI '>' chevrons, 44x24dp #0080FF/#D8DADC switches with ROM haptic 302, and COUI overscroll.
 */
public final class CouiBrowserUiEnhancer {
    private static final String TAG = "CouiBrowserUi";
    private static final String NAV_TAG = "coui_liquid_glass_nav_bar";
    private static final String FLYOUT_TAG = "coui_glass_flyout_overlay";
    private static final String SETTINGS_BACK_TAG = "coui_settings_back_btn";
    private static final String SETTINGS_TOP_BLUR_TAG = "coui_settings_top_blur";
    private static final String OVERSCROLL_TAG = "coui_overscroll_attached";
    private static final String SWITCH_TAG = "coui_switch_styled";
    public static final String REPO_URL = "https://github.com/ampereforest/android-titanium-browser";

    // Chromium view resource IDs in TitaniumBrowser.apk
    private static final int ID_HOME_BUTTON = 0x7f010648;
    private static final int ID_TOOLBAR_BUTTONS = 0x7f010d6f;
    private static final int ID_EXTENSIONS_BUTTON = 0x7f0104de;
    private static final int ID_EXTENSIONS_CONTAINER = 0x7f010514;
    private static final int ID_TAB_SWITCHER_BUTTON = 0x7f010cd1;
    private static final int ID_FAKE_TAB_SWITCHER_BUTTON = 0x7f01051d;
    private static final int ID_OPTIONAL_BUTTON = 0x7f010908;
    private static final int ID_OPTIONAL_CONTAINER = 0x7f010909;
    private static final int ID_MENU_BUTTON = 0x7f010783;
    private static final int ID_MENU_BUTTON_CONTAINER = 0x7f010784;
    private static final int ID_MENU_BUTTON_WRAPPER = 0x7f010785;
    private static final int ID_URL_BAR = 0x7f010ddd;
    private static final int ID_LOCATION_BAR = 0x7f01071f;
    private static final int ID_LOCATION_BAR_BG = 0x7f010721;
    private static final int ID_LOCATION_BAR_STATUS = 0x7f010726;
    private static final int ID_LOCATION_BAR_STATUS_ICON = 0x7f010727;
    private static final int ID_LOCATION_BAR_STATUS_ICON_VIEW = 0x7f010729;
    private static final int ID_LOCATION_BAR_ATTACHMENTS = 0x7f010720;
    private static final int ID_DELETE_BUTTON = 0x7f0103cf;
    private static final int ID_NAVIGATE_BUTTON = 0x7f010853;
    private static final int ID_MIC_BUTTON = 0x7f0107b8;
    private static final int ID_LENS_BUTTON = 0x7f0106f4;
    private static final int ID_FUSEBOX_PLUS_BUTTON = 0x7f0105c0;
    private static final int ID_FUSEBOX_REQUEST_TYPE = 0x7f0105c4;
    private static final int ID_FUSEBOX_CHIP = 0x7f0105b1;
    private static final int ID_CONTROL_CONTAINER = 0x7f010366;
    private static final int ID_TOOLBAR = 0x7f010d6a;
    private static final int ID_TOOLBAR_CONTAINER = 0x7f010d71;
    private static final int ID_TOOLBAR_HAIRLINE = 0x7f010d74;
    private static final int ID_TOOLBAR_BOTTOM_HAIRLINE = 0x7f010d6e;
    private static final int ID_TAB_SWITCHER_HOLDER = 0x7f010cd7;

    // Round 13: Tab Switcher (Hub + TabListRecyclerView + TabGridView) resource IDs
    private static final int ID_HUB_MAIN_CONTAINER = 0x7f010658;
    private static final int ID_HUB_PANE_HOST = 0x7f01065a;
    private static final int ID_HUB_PANE_HOST_CONTAINER = 0x7f01065b;
    private static final int ID_HUB_TOOLBAR = 0x7f01065c;
    private static final int ID_HUB_NEW_TAB_BUTTON_WRAP = 0x7f010d6c;
    private static final int ID_HUB_NEW_TAB_BUTTON = 0x7f010d6d;
    private static final int ID_HUB_NEW_TAB_TEXT_BTN = 0x7f010d6b;
    private static final int ID_HUB_PANE_SWITCHER = 0x7f01094f;
    private static final int ID_HUB_PANE_SWITCHER_CARD = 0x7f010950;
    private static final int ID_TAB_LIST_CONTAINER = 0x7f010ca1;
    private static final int ID_TAB_LIST_RECYCLER_VIEW = 0x7f010cb2;
    private static final int ID_TAB_CARD_VIEW = 0x7f010257;
    private static final int ID_TAB_CARD_SECONDARY = 0x7f010259;
    private static final int ID_TAB_CARD_BORDER = 0x7f01019b;
    private static final int ID_TAB_CARD_CONSTRAINT = 0x7f01030e;
    private static final int ID_TAB_TITLE = 0x7f010cda;
    private static final int ID_TAB_THUMBNAIL = 0x7f010cd9;
    private static final int ID_TAB_CLOSE_BTN = 0x7f010076;

    private static final ColorStateList CSL_TAB_LIGHT_UNFOCUSED = ColorStateList.valueOf(0xFFFFFFFF);
    private static final ColorStateList CSL_TAB_LIGHT_FOCUSED = ColorStateList.valueOf(0xFF191919);
    private static final ColorStateList CSL_TAB_DARK_UNFOCUSED = ColorStateList.valueOf(0xFF1C1C1E);
    private static final ColorStateList CSL_TAB_DARK_FOCUSED = ColorStateList.valueOf(0xFFF0F1F3);
    private static final ColorStateList CSL_CLOSE_LIGHT_UNFOCUSED = ColorStateList.valueOf(0xFF555555);
    private static final ColorStateList CSL_CLOSE_LIGHT_FOCUSED = ColorStateList.valueOf(0xFFFFFFFF);
    private static final ColorStateList CSL_CLOSE_DARK_UNFOCUSED = ColorStateList.valueOf(0xB3FFFFFF);
    private static final ColorStateList CSL_CLOSE_DARK_FOCUSED = ColorStateList.valueOf(0xFF191919);

    // Round 12: New Tab Page (NTP) & Search Engine Settings resource IDs
    private static final int ID_NTP_CONTENT = 0x7f0108a6;
    private static final int ID_NTP_SCROLLVIEW = 0x7f0108bb;
    private static final int ID_FEED_STREAM_RV = 0x7f01052c;
    private static final int ID_NTP_SEARCH_BOX_BG = 0x7f010b07;
    private static final int ID_NTP_SEARCH_BOX_CONTAINER = 0x7f010b08;
    private static final int ID_NTP_SEARCH_BOX_TEXT = 0x7f010b0d;
    private static final int ID_NTP_MV_TILES_CONTAINER = 0x7f010845;
    private static final int ID_NTP_MV_TILES_LAYOUT = 0x7f010846;
    private static final int ID_NTP_TILE_ICON_BG = 0x7f010d3b;
    private static final int ID_NTP_TILE_TITLE = 0x7f010d3c;
    private static final int ID_SE_RADIO_BUTTON = 0x7f010a53;
    private static final int ID_SE_NAME = 0x7f01084a;
    private static final int ID_SE_URL = 0x7f010ddc;
    private static final int ID_SE_CARD_CONTAINER = 0x7f010307;
    private static final String SETTINGS_OBSERVER_TAG = "coui_settings_frag_observer";
    private static final String RADIO_TAG = "coui_radio_styled";

    // Chromium native menu item IDs dispatched via ChromeTabbedActivity.R2(int, boolean, Bundle, Lpo9)
    private static final int MENU_NEW_TAB = 0x7f01087b;
    private static final int MENU_NEW_INCOGNITO = 0x7f01086e;
    private static final int MENU_BOOKMARKS = 0x7f0100df;
    private static final int MENU_HISTORY = 0x7f0108d6;
    private static final int MENU_DOWNLOADS = 0x7f010442;
    private static final int MENU_EXTENSIONS = 0x7f01074d; // manage_extensions_menu_id -> chrome://extensions/
    private static final int MENU_SHARE = 0x7f010b8e;
    private static final int MENU_FIND_IN_PAGE = 0x7f010544;
    private static final int MENU_DESKTOP_SITE = 0x7f010a8a;
    private static final int MENU_SETTINGS = 0x7f0109ce;
    private static final int MENU_FORWARD = 0x7f010590;
    private static final int MENU_BOOKMARK_PAGE = 0x7f0101e2;
    private static final int MENU_DOWNLOAD_PAGE = 0x7f01043e;
    private static final int MENU_RELOAD = 0x7f010a7d;

    // Settings view resource IDs in TitaniumBrowser.apk
    private static final int ID_EDGE_TO_EDGE_BASE = 0x7f010461;
    private static final int ID_SETTINGS_TOOLBAR = 0x7f01006c;
    private static final int ID_SETTINGS_APP_BAR = 0x7f010104;
    private static final int ID_SETTINGS_ACTIVITY = 0x7f010b78;
    private static final int ID_SETTINGS_CONTENT = 0x7f010b79;
    private static final int ID_SEARCH_BOX = 0x7f010b05;
    private static final int ID_SEARCH_BOX_ICON = 0x7f010b15;
    private static final int ID_SEARCH_QUERY_CONTAINER = 0x7f010b1e;
    private static final int ID_SEARCH_BACK_ICON = 0x7f01018d;
    private static final int ID_SEARCH_EDIT_TEXT = 0x7f010b1d;
    private static final int ID_ADDRESS_BAR_RADIO_GROUP = 0x7f0100be;
    private static final int ID_ADDRESS_BAR_TOP = 0x7f0100bf;
    private static final int ID_ADDRESS_BAR_BOTTOM = 0x7f0100bd;
    private static final int ID_RADIO_BUTTON = 0x7f010a4b;
    private static final int ID_RADIO_PRIMARY = 0x7f0109ee;
    private static final int ID_RADIO_DESC = 0x7f0103d8;

    // ColorOS 17 ROM Haptic Feedback constants
    private static final int COUI_HAPTIC_SWITCH = 302;
    private static final int COUI_HAPTIC_OVERSCROLL = 307;
    private static final int COUI_HAPTIC_SLIDER = 308;

    private static final Paint DIVIDER_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint CHEVRON_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SWITCH_TRACK_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SWITCH_THUMB_SHADOW_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SWITCH_THUMB_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF SWITCH_RECT = new RectF();
    private static final Path CHEVRON_PATH = new Path();
    private static Field sSwitchThumbPosField;
    private static boolean sSwitchThumbPosResolved = false;
    private static java.lang.ref.WeakReference<CouiLiquidGlassNavBar> sActiveNavBar;

    static {
        DIVIDER_PAINT.setStyle(Paint.Style.FILL);
        CHEVRON_PAINT.setStyle(Paint.Style.FILL);
        SWITCH_TRACK_PAINT.setStyle(Paint.Style.FILL);
        SWITCH_THUMB_SHADOW_PAINT.setStyle(Paint.Style.FILL);
        SWITCH_THUMB_PAINT.setStyle(Paint.Style.FILL);
    }

    /**
     * Smali hook (LocationBarLayout.E(View, boolean)):
     * Suppresses Chromium 156's Fusebox '+' button (0x7f0105c0), request-type chip (0x7f0105c4),
     * activation chip (0x7f0105b1), attachments strip (0x7f010720), blue '>' navigate pill (0x7f010853),
     * mic & lens buttons so typing a long query stays ultra-clean: [Search Icon] + [Single-line URL] + [X].
     */
    public static boolean shouldAllowOmniboxButton(View v, boolean wantVisible) {
        if (!wantVisible || v == null) return false;
        int id = v.getId();
        if (id == ID_NAVIGATE_BUTTON
                || id == ID_FUSEBOX_PLUS_BUTTON
                || id == ID_FUSEBOX_REQUEST_TYPE
                || id == ID_FUSEBOX_CHIP
                || id == ID_LOCATION_BAR_ATTACHMENTS
                || id == ID_MIC_BUTTON
                || id == ID_LENS_BUTTON) {
            return false;
        }
        return true;
    }

    /**
     * Smali hook (SwitchCompat.onDraw(Canvas)):
     * Renders EVERY SwitchCompat & MaterialSwitch across Titanium Settings & sub-settings as an
     * authentic borderless ColorOS 17 44x24dp pill switch (#E2E4E8/#48484A off, #0080FF/#1A8CFF on,
     * crisp 20dp white circular thumb with 2dp inset, subtle shadow, and 1.26x mid-toggle liquid stretch).
     */
    public static boolean drawCouiSwitch(CompoundButton sw, Canvas canvas) {
        if (sw == null || canvas == null) return false;
        try {
            Context ctx = sw.getContext();
            boolean dark = isDarkMode(ctx);
            int w = sw.getWidth();
            int h = sw.getHeight();
            if (w <= 0 || h <= 0) return false;

            float pos = sw.isChecked() ? 1.0f : 0.0f;
            if (!sSwitchThumbPosResolved) {
                sSwitchThumbPosResolved = true;
                try {
                    Class<?> sc = Class.forName("androidx.appcompat.widget.SwitchCompat");
                    sSwitchThumbPosField = sc.getDeclaredField("Q");
                    sSwitchThumbPosField.setAccessible(true);
                } catch (Throwable ignored) {}
            }
            if (sSwitchThumbPosField != null) {
                try {
                    pos = Math.max(0f, Math.min(1f, sSwitchThumbPosField.getFloat(sw)));
                } catch (Throwable ignored) {}
            }
            if (sw.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                pos = 1.0f - pos;
            }

            float trackW = dpF(ctx, 44f);
            float trackH = dpF(ctx, 24f);
            float inset = dpF(ctx, 2.2f);
            float thumbD = trackH - inset * 2f; // ~19.6dp crisp white circle matching ColorOS 17

            float cx = w - trackW * 0.5f;
            if (w < trackW + dpF(ctx, 2f)) cx = w * 0.5f;
            float cy = h * 0.5f;
            float left = cx - trackW * 0.5f;
            float top = cy - trackH * 0.5f;
            float right = cx + trackW * 0.5f;
            float bottom = cy + trackH * 0.5f;
            float trackR = trackH * 0.5f;

            float alphaScale = sw.isEnabled() ? 1.0f : 0.42f;
            int offCol = dark ? 0xFF48484A : 0xFFE2E4E8;
            int onCol = dark ? 0xFF1A8CFF : 0xFF0080FF;
            int trackCol = blendArgb(offCol, onCol, pos);
            if (alphaScale < 0.99f) {
                trackCol = (trackCol & 0x00FFFFFF) | (Math.round(Color.alpha(trackCol) * alphaScale) << 24);
            }

            SWITCH_RECT.set(left, top, right, bottom);
            SWITCH_TRACK_PAINT.setColor(trackCol);
            canvas.drawRoundRect(SWITCH_RECT, trackR, trackR, SWITCH_TRACK_PAINT);

            // ColorOS 17 liquid thumb stretch (1.26x horizontal stretch peaking at mid-toggle)
            float stretch = 1.0f + 0.26f * (float) Math.sin(Math.PI * pos);
            float thumbW = thumbD * stretch;
            float travel = (trackW - inset * 2f) - thumbW;
            float thumbLeft = left + inset + travel * pos;
            float thumbTop = top + inset;
            float thumbR = thumbD * 0.5f;

            // Subtle ambient thumb shadow
            SWITCH_RECT.set(thumbLeft, thumbTop + dpF(ctx, 0.8f), thumbLeft + thumbW, thumbTop + thumbD + dpF(ctx, 0.8f));
            SWITCH_THUMB_SHADOW_PAINT.setColor(Color.argb(Math.round((dark ? 64 : 34) * alphaScale), 0, 0, 0));
            canvas.drawRoundRect(SWITCH_RECT, thumbR, thumbR, SWITCH_THUMB_SHADOW_PAINT);

            // Crisp white circular/pill thumb
            SWITCH_RECT.set(thumbLeft, thumbTop, thumbLeft + thumbW, thumbTop + thumbD);
            SWITCH_THUMB_PAINT.setColor(Color.argb(Math.round(255 * alphaScale), 255, 255, 255));
            canvas.drawRoundRect(SWITCH_RECT, thumbR, thumbR, SWITCH_THUMB_PAINT);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int blendArgb(int c0, int c1, float t) {
        int a = Math.round(Color.alpha(c0) + (Color.alpha(c1) - Color.alpha(c0)) * t);
        int r = Math.round(Color.red(c0) + (Color.red(c1) - Color.red(c0)) * t);
        int g = Math.round(Color.green(c0) + (Color.green(c1) - Color.green(c0)) * t);
        int b = Math.round(Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * t);
        return Color.argb(a, r, g, b);
    }

    private CouiBrowserUiEnhancer() {}

    public static void performCouiHaptic(View view) {
        performCouiHapticCode(view, COUI_HAPTIC_SWITCH);
    }

    public static void performCouiHapticCode(View view, int code) {
        if (view == null || !view.isAttachedToWindow()) return;
        try {
            if (!view.performHapticFeedback(code)) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isDarkMode(Context ctx) {
        int uiMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    static int dp(Context ctx, float v) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return Math.round(v * dm.density);
    }

    static float dpF(Context ctx, float v) {
        return v * ctx.getResources().getDisplayMetrics().density;
    }

    /**
     * Applies native ColorOS 17 hardware backdrop blur & G2 material params to a floating glass View,
     * extracted 1:1 from OppoGallery2.apk (COUIMaterialBlurEffect + COUIMaterialEffect + OplusMaterialUtil).
     * Returns true if OplusViewBackgroundRenderEffect succeeded on this ROM.
     */
    static boolean applyOplusLiquidGlass(View view, boolean isDark, boolean isCircle, float radiusPx) {
        if (view == null || Build.VERSION.SDK_INT < 31) return false;
        boolean renderOk = false;
        Context ctx = view.getContext();
        try {
            Class<?> oplusViewCls = Class.forName("com.oplus.view.OplusView");
            Method setShadowClip = oplusViewCls.getMethod("setShadowClippingEnabled", View.class, boolean.class);
            setShadowClip.invoke(null, view, true);
        } catch (Throwable ignored) {}

        try {
            float blurPx = dpF(ctx, 25f); // TYPE_FRAMEWORK_BOTTOM_BAR = 25dp blur
            RenderEffect blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.MIRROR);
            int c1 = isDark ? 0x80666666 : 0xB3666666;
            BlendMode b1 = isDark ? BlendMode.OVERLAY : BlendMode.COLOR_DODGE;
            int c2 = isDark ? 0xB3262626 : 0x8CF5F5F5;
            BlendMode b2 = BlendMode.LUMINOSITY;
            RenderEffect r1 = RenderEffect.createColorFilterEffect(new BlendModeColorFilter(c1, b1), blur);
            RenderEffect r2 = RenderEffect.createColorFilterEffect(new BlendModeColorFilter(c2, b2), r1);

            Class<?> bgEffectCls = Class.forName("com.oplus.view.OplusViewBackgroundRenderEffect");
            Method setBgEffect = bgEffectCls.getMethod("setBackgroundRenderEffect", RenderEffect.class, View.class);
            setBgEffect.invoke(null, r2, view);
            renderOk = true;
        } catch (Throwable t) {
            Log.w(TAG, "OplusViewBackgroundRenderEffect unavailable", t);
        }

        if (radiusPx > 0f) {
            try {
                Class<?> utilCls = Class.forName("com.oplus.view.material.OplusMaterialUtil");
                Class<?> cornerCls = Class.forName("com.oplus.view.material.OplusMaterialCornerParams");
                Constructor<?> cornerCtor = cornerCls.getConstructor(float.class, float.class);
                Object cornerParams = cornerCtor.newInstance(radiusPx, isCircle ? 2.0f : 1.0f);
                Method setCorner = utilCls.getMethod("setCornerParams", View.class, cornerCls);
                setCorner.invoke(null, view, cornerParams);

                Class<?> edgeCls = Class.forName("com.oplus.view.material.OplusMaterialEdgeParams");
                Constructor<?> edgeCtor = edgeCls.getConstructor(int.class, float.class, float.class, float.class);
                Object edgeParams = edgeCtor.newInstance(1, dpF(ctx, 2.2f), isDark ? 0.72f : 0.28f, 0.0f);
                Method setEdge = utilCls.getMethod("setEdgeParams", View.class, edgeCls);
                setEdge.invoke(null, view, edgeParams);

                Class<?> shadowCls = Class.forName("com.oplus.view.material.OplusMaterialShadowParams");
                Constructor<?> shadowCtor = shadowCls.getConstructor(int.class, float.class, float.class, float.class);
                Object shadowParams = shadowCtor.newInstance(1, isDark ? 0.03f : 0.10f, 1.0f, 1.0f);
                Method setShadow = utilCls.getMethod("setShadowParams", View.class, shadowCls);
                setShadow.invoke(null, view, shadowParams);
            } catch (Throwable ignored) {}
        }

        if (renderOk) {
            view.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }
        return renderOk;
    }

    /** Draws stock ColorOS 17 coui_back_arrow_normal (24x24 viewport: M10.1,4.4L2.5,12M2.5,12L10.1,19.6M2.5,12L21.5,12.001). */
    static void drawCouiBackArrow(Canvas canvas, float cx, float cy, float sizePx, Paint paint) {
        float scale = sizePx / 24.0f;
        float ox = cx - 12.0f * scale;
        float oy = cy - 12.0f * scale;
        Path p = new Path();
        p.moveTo(ox + 10.1f * scale, oy + 4.4f * scale);
        p.lineTo(ox + 2.5f * scale, oy + 12.0f * scale);
        p.lineTo(ox + 10.1f * scale, oy + 19.6f * scale);
        p.moveTo(ox + 3.0f * scale, oy + 12.0f * scale);
        p.lineTo(ox + 21.5f * scale, oy + 12.0f * scale);
        canvas.drawPath(p, paint);
    }

    // =========================================================================
    // Round 11: Edge-to-edge status bar frosted blur for the tabbed browser
    // =========================================================================

    /** Last status bar top inset (px) dropped from EdgeToEdgeBaseLayout for ChromeTabbedActivity. */
    private static volatile int sTopInsetPx = 0;
    private static volatile Class<?> sTabbedActivityClass;
    private static final String TABBED_ACTIVITY_CLASS = "org.chromium.chrome.browser.ChromeTabbedActivity";

    static boolean isTabbedBrowser(Context ctx) {
        Activity a = unwrapActivity(ctx);
        if (a == null) return false;
        Class<?> ac = a.getClass();
        if (ac == sTabbedActivityClass) return true;
        for (Class<?> c = ac; c != null && c != Activity.class; c = c.getSuperclass()) {
            if (TABBED_ACTIVITY_CLASS.equals(c.getName())) {
                sTabbedActivityClass = ac;
                return true;
            }
        }
        return false;
    }

    private static int systemStatusBarHeight(Context ctx) {
        try {
            int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
            return id != 0 ? ctx.getResources().getDimensionPixelSize(id) : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Smali hook (Lo05.c, Chromium's edge-to-edge insets listener) replacing
     * EdgeToEdgeBaseLayout.setPadding(l, t, r, b). For ChromeTabbedActivity the top (status bar) inset is
     * dropped so the compositor SurfaceView + control_container start at y = 0 and web content can scroll
     * (and be hardware-blurred) behind the transparent status bar. The inset is re-applied as top padding
     * on toolbar_container, and added to the top controls height by {@link #extraTopControlsHeight}.
     */
    public static void onEdgeToEdgePadding(View base, int l, int t, int r, int b) {
        int top = t;
        try {
            if (t > 0 && base != null && isTabbedBrowser(base.getContext())) {
                if (sTopInsetPx != t) {
                    int sys = systemStatusBarHeight(base.getContext());
                    Log.i(TAG, "edge-to-edge top inset " + sTopInsetPx + " -> " + t + " (system dimen " + sys + ")");
                    sTopInsetPx = t;
                }
                top = 0;
                View tc = base.findViewById(ID_TOOLBAR_CONTAINER);
                if (tc != null && tc.getPaddingTop() != t) {
                    tc.setPadding(tc.getPaddingLeft(), t, tc.getPaddingRight(), tc.getPaddingBottom());
                }
            }
        } catch (Throwable ignored) {}
        if (base != null) base.setPadding(l, top, r, b);
    }

    /** Smali hook added to ToolbarControlContainer.k() (top controls height). */
    public static int extraTopControlsHeight(View controlContainer) {
        try {
            if (controlContainer == null || !isTabbedBrowser(controlContainer.getContext())) return 0;
            int t = sTopInsetPx;
            if (t <= 0) t = systemStatusBarHeight(controlContainer.getContext());
            return Math.max(0, t);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Makes EdgeToEdgeBaseLayout's status bar paint (field C) transparent for the tabbed browser. */
    private static void clearEdgeToEdgeStatusBarPaint(Activity activity) {
        try {
            View e2e = activity.findViewById(ID_EDGE_TO_EDGE_BASE);
            if (e2e == null) return;
            Field f = e2e.getClass().getDeclaredField("C");
            f.setAccessible(true);
            Object p = f.get(e2e);
            if (p instanceof Paint && ((Paint) p).getColor() != Color.TRANSPARENT) {
                ((Paint) p).setColor(Color.TRANSPARENT);
                e2e.invalidate();
            }
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // Round 12: Smali hooks for BrandedColorScheme, StatusBar contrast, and
    //           Echo-Music directional auto-hide of top & bottom Liquid Glass bars
    // =========================================================================

    /**
     * Smali hook (Lula.b / ThemeUtils.getBrandedColorScheme):
     * Neutralizes website <meta name="theme-color"> dark/light overrides so URL emphasis spans
     * and omnibox icons ALWAYS match our Light/Dark Liquid Glass pill (0 = LIGHT_BRANDED_THEME
     * with dark text on light glass; 1 = DARK_BRANDED_THEME with light text on dark glass).
     * Fixes white-on-white URL text on keybox.tryigit.dev & github.com in Light & Dark modes.
     */
    public static int getBrandedColorScheme(Context ctx) {
        try {
            if (ctx != null && isDarkMode(ctx)) {
                return 1; // DARK_BRANDED_THEME -> crisp white domain + subtle light path
            }
        } catch (Throwable ignored) {}
        return 0; // LIGHT_BRANDED_THEME -> crisp dark domain + subtle grey path
    }

    /**
     * Smali hook (Lz2f.h / StatusBarColorController.setStatusBarColor):
     * Prevents dark website theme-color from flipping status bar icons white on our light glass
     * header (and vice versa in Dark Mode) by pinning the luminance-evaluated status bar color
     * to our Light (#FFECEDF0) / Dark (#FF101012) header tint for ChromeTabbedActivity.
     */
    public static int overrideStatusBarColor(Activity activity, int color) {
        try {
            if (activity != null && isTabbedBrowser(activity)) {
                clearEdgeToEdgeStatusBarPaint(activity);
                return isDarkMode(activity) ? 0xFF101012 : 0xFFECEDF0;
            }
        } catch (Throwable ignored) {}
        return color;
    }

    /**
     * Smali hook (Lw05.m / EdgeToEdgeSystemBarColorHelper):
     * Keeps EdgeToEdgeBaseLayout's status bar fill paint (field C) 100% transparent on the tabbed
     * browser so edge-to-edge SurfaceControl blur behind the status bar is never occluded, while
     * Lw05.m still uses Ld71.r (from overrideStatusBarColor) to set APPEARANCE_LIGHT_STATUS_BARS.
     */
    public static int overrideEdgeToEdgeStatusBarFill(Window window, int color) {
        try {
            if (window != null && isTabbedBrowser(window.getContext())) {
                return Color.TRANSPARENT;
            }
        } catch (Throwable ignored) {}
        return color;
    }

    /**
     * Smali hook (Law1.x / BrowserControlsManager.notifyControlOffsetChanged):
     * Receives Chromium's live compositor scroll offset (0 at top down to -topControlsHeight)
     * and drives Echo-Music directional auto-hide for BOTH top header and bottom 3-piece nav bar.
     */
    public static void onBrowserControlsOffset(View controlContainer, int topControlOffsetPx) {
        if (controlContainer == null) return;
        try {
            Activity act = unwrapActivity(controlContainer.getContext());
            if (act == null) return;
            ViewGroup content = act.findViewById(android.R.id.content);
            if (content == null) return;
            View nav = content.findViewWithTag(NAV_TAG);
            if (nav instanceof CouiLiquidGlassNavBar) {
                ((CouiLiquidGlassNavBar) nav).onChromiumControlsOffset(controlContainer, topControlOffsetPx);
            }
        } catch (Throwable ignored) {}
    }

    private static CouiLiquidGlassNavBar resolveActiveNavBar(View hintView) {
        if (hintView != null) {
            Activity act = unwrapActivity(hintView.getContext());
            if (act != null) {
                ViewGroup content = act.findViewById(android.R.id.content);
                if (content != null) {
                    View nav = content.findViewWithTag(NAV_TAG);
                    if (nav instanceof CouiLiquidGlassNavBar) return (CouiLiquidGlassNavBar) nav;
                }
            }
            View root = hintView.getRootView();
            if (root != null) {
                View nav = root.findViewWithTag(NAV_TAG);
                if (nav instanceof CouiLiquidGlassNavBar) return (CouiLiquidGlassNavBar) nav;
            }
        }
        java.lang.ref.WeakReference<CouiLiquidGlassNavBar> ref = sActiveNavBar;
        CouiLiquidGlassNavBar nav = (ref != null) ? ref.get() : null;
        return (nav != null && nav.isAttachedToWindow()) ? nav : null;
    }

    /**
     * Smali hook (GestureListenerManagerImpl.f(FFF) — native Viz/Blink scroll offset in physical px).
     * Immune to Android 15+ InputOnViz (InputVizard) touch transfer; fires on EVERY webpage scroll frame!
     */
    public static void onWebScrollChanged(View containerView, int newScrollYPx, int oldScrollYPx) {
        if (newScrollYPx == oldScrollYPx) return;
        try {
            if (containerView != null && !containerView.isAttachedToWindow()) return;
            CouiLiquidGlassNavBar nav = resolveActiveNavBar(containerView);
            if (nav != null) {
                nav.onWebPageScrollChanged(newScrollYPx, oldScrollYPx);
            }
        } catch (Throwable t) {
            Log.w(TAG, "onWebScrollChanged err", t);
        }
    }

    /** Smali hook (GestureListenerManagerImpl.onScrollBegin(Z)) — native scroll start & direction. */
    public static void onWebScrollBegin(View containerView, boolean isDirectionUp) {
        try {
            if (containerView != null && !containerView.isAttachedToWindow()) return;
            CouiLiquidGlassNavBar nav = resolveActiveNavBar(containerView);
            if (nav != null) {
                nav.onWebPageScrollBegin(isDirectionUp);
            }
        } catch (Throwable ignored) {}
    }

    /** Smali hook (GestureListenerManagerImpl.updateOnTouchDown()) — native touch down even under InputVizard. */
    public static void onWebTouchDown(View containerView) {
        try {
            if (containerView != null && !containerView.isAttachedToWindow()) return;
            CouiLiquidGlassNavBar nav = resolveActiveNavBar(containerView);
            if (nav != null) {
                nav.onWebPageTouchState(true);
            }
        } catch (Throwable ignored) {}
    }

    /** Smali hook (GestureListenerManagerImpl.updateOnTouchUp()) — native touch up even under InputVizard. */
    public static void onWebTouchUp(View containerView) {
        try {
            if (containerView != null && !containerView.isAttachedToWindow()) return;
            CouiLiquidGlassNavBar nav = resolveActiveNavBar(containerView);
            if (nav != null) {
                nav.onWebPageTouchState(false);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Round 12 + Round 13: Ensures Tab Switcher container (ID_TAB_SWITCHER_HOLDER) clears the status bar
     * by 1.30x AND restyles the entire Tab Switcher (Hub toolbar, '+' button, '[count | grid]' switcher,
     * 'Search your tabs' bar, and focused vs. unfocused tab cards) to 100% ColorOS 17 COUI neutral tokens
     * with ZERO Material You blue tint.
     */
    private static void applyTabSwitcherTopInset(Activity activity, View root, boolean isDark) {
        if (activity == null || root == null) return;
        View tabHolder = root.findViewById(ID_TAB_SWITCHER_HOLDER);
        if (tabHolder == null) return;
        int sb = sTopInsetPx > 0 ? sTopInsetPx : systemStatusBarHeight(activity);
        if (sb <= 0) sb = dp(activity, 36);
        int wantTop = Math.round(sb * 1.30f);
        if (tabHolder.getPaddingTop() != wantTop) {
            tabHolder.setPadding(tabHolder.getPaddingLeft(), wantTop,
                    tabHolder.getPaddingRight(), tabHolder.getPaddingBottom());
            if (tabHolder instanceof ViewGroup) {
                ((ViewGroup) tabHolder).setClipToPadding(false);
            }
        }
        boolean visible = (tabHolder.getVisibility() == View.VISIBLE
                && tabHolder instanceof ViewGroup && ((ViewGroup) tabHolder).getChildCount() > 0);
        int wantBg = isDark ? 0xFF000000 : 0xFFF0F1F3;
        if (visible) {
            Drawable bg = tabHolder.getBackground();
            if (!(bg instanceof ColorDrawable) || ((ColorDrawable) bg).getColor() != wantBg) {
                tabHolder.setBackgroundColor(wantBg);
            }
            styleTabSwitcherColors(activity, tabHolder, isDark);
        } else if (tabHolder.getBackground() != null) {
            tabHolder.setBackground(null);
        }
    }

    /**
     * Round 13: Eliminates all blue Material You dynamic colors inside the Tab Switcher:
     * 1. Seamless #F0F1F3 (Light) / #000000 (Dark) canvas across Hub containers, toolbar & search_box_background.
     * 2. Top-left '+' button: COUI 14dp squircle (#FFFFFF / #1C1C1E) with crisp #191919 / #E6FFFFFF '+' icon.
     * 3. Top-center '[count | grid]' pane switcher: neutral #E3E4E7 / #1C1C1E track, #FFFFFF / #2C2C2E active pill,
     *    and #191919 / #E6FFFFFF icons (preserving TabSwitcherDrawable stroke+number via setTintList).
     * 4. 'Search your tabs' pill: COUI 24dp neutral #E3E4E7 / #1C1C1E pill with #7F7F7F / #8AFFFFFF hint & lens.
     * 5. Tab grid cards (res/GV.xml):
     *    - Light Mode: Unfocused = #FFFFFF card & header, #191919 title, #555555 'X';
     *                  Focused/active = #191919 dark charcoal/black card & header, #FFFFFF title & 'X'.
     *    - Dark Mode:  Unfocused = #1C1C1E dark grey card & header, #E6FFFFFF title, #B3FFFFFF 'X';
     *                  Focused/active = #F0F1F3 bright COUI neutral card & header, #191919 title & 'X'.
     */
    static void styleTabSwitcherColors(Activity activity, View tabHolder, boolean isDark) {
        if (activity == null || tabHolder == null) return;
        int canvasCol = isDark ? 0xFF000000 : 0xFFF0F1F3;
        int[] canvasIds = {
            ID_HUB_MAIN_CONTAINER,
            ID_HUB_PANE_HOST_CONTAINER,
            ID_HUB_PANE_HOST,
            ID_HUB_TOOLBAR,
            ID_TAB_LIST_CONTAINER,
            ID_TAB_LIST_RECYCLER_VIEW
        };
        for (int id : canvasIds) {
            View v = tabHolder.findViewById(id);
            if (v != null) {
                v.setBackgroundTintList(null);
                Drawable bg = v.getBackground();
                if (!(bg instanceof ColorDrawable) || ((ColorDrawable) bg).getColor() != canvasCol) {
                    v.setBackgroundColor(canvasCol);
                }
            }
        }

        View hubToolbar = tabHolder.findViewById(ID_HUB_TOOLBAR);
        if (hubToolbar instanceof ViewGroup) {
            ViewGroup htg = (ViewGroup) hubToolbar;
            for (int i = 0; i < htg.getChildCount(); i++) {
                View c = htg.getChildAt(i);
                if (c instanceof ViewGroup && c.getId() == View.NO_ID) {
                    c.setBackgroundTintList(null);
                    Drawable bg = c.getBackground();
                    if (bg != null && (!(bg instanceof ColorDrawable) || ((ColorDrawable) bg).getColor() != canvasCol)) {
                        c.setBackgroundColor(canvasCol);
                    }
                }
            }
            // Clear the hardcoded @android:color/white search_box_background (0x7f010b07) inside hub_toolbar
            View sbBg = hubToolbar.findViewById(ID_NTP_SEARCH_BOX_BG);
            if (sbBg != null) {
                sbBg.setBackgroundTintList(null);
                Drawable bg = sbBg.getBackground();
                if (!(bg instanceof ColorDrawable) || ((ColorDrawable) bg).getColor() != canvasCol) {
                    sbBg.setBackgroundColor(canvasCol);
                }
            }

            // 1. Top-left '+' New Tab button (0x7f010d6d ChromeImageButton AND 0x7f010d6b Button inside 0x7f010d6c)
            //    In res/Io.xml, HubToolbarView uses 0x7f010d6b (Button with compound drawable '+' and tinted
            //    rounded-square background) or 0x7f010d6d (ChromeImageButton). Style BOTH with a clean COUI
            //    14dp squircle (#FFFFFF / #1C1C1E) and crisp #191919 / #E6FFFFFF '+' icon!
            View newTabWrap = hubToolbar.findViewById(ID_HUB_NEW_TAB_BUTTON_WRAP);
            if (newTabWrap != null) {
                newTabWrap.setBackgroundTintList(null);
                if (newTabWrap.getBackground() != null) {
                    newTabWrap.setBackground(null);
                }
            }
            int plusIconCol = isDark ? 0xE6FFFFFF : 0xFF191919;
            ColorStateList plusIconCsl = ColorStateList.valueOf(plusIconCol);
            View[] newTabBtns = {
                hubToolbar.findViewById(ID_HUB_NEW_TAB_BUTTON),
                hubToolbar.findViewById(ID_HUB_NEW_TAB_TEXT_BTN)
            };
            for (View btn : newTabBtns) {
                if (btn == null) continue;
                String btnTag = "coui_hub_add3_" + (isDark ? "d" : "l");
                btn.setBackgroundTintList(null);
                if (!btnTag.equals(btn.getTag())) {
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.RECTANGLE);
                    gd.setCornerRadius(dpF(activity, 14f));
                    gd.setColor(isDark ? 0xFF1C1C1E : 0xFFFFFFFF);
                    gd.setStroke(Math.max(1, dp(activity, 0.75f)), isDark ? 0x1AFFFFFF : 0x12000000);
                    btn.setBackground(gd);
                    btn.setTag(btnTag);
                }
                if (btn instanceof ImageView) {
                    ImageView iv = (ImageView) btn;
                    iv.clearColorFilter();
                    iv.setImageTintList(null);
                    Drawable curD = iv.getDrawable();
                    if (curD instanceof CouiPlusIconDrawable) {
                        ((CouiPlusIconDrawable) curD).setDarkMode(isDark);
                    } else {
                        iv.setImageDrawable(new CouiPlusIconDrawable(activity, isDark));
                        iv.setScaleType(ImageView.ScaleType.CENTER);
                    }
                } else if (btn instanceof TextView) {
                    TextView tv = (TextView) btn;
                    tv.setTextColor(plusIconCol);
                    tv.setCompoundDrawableTintList(plusIconCsl);
                    Drawable[] cds = tv.getCompoundDrawables();
                    if (cds != null) {
                        for (Drawable d : cds) {
                            if (d != null) {
                                d.setTintList(plusIconCsl);
                                d.setColorFilter(plusIconCol, PorterDuff.Mode.SRC_IN);
                            }
                        }
                    }
                    Drawable[] cdrs = tv.getCompoundDrawablesRelative();
                    if (cdrs != null) {
                        for (Drawable d : cdrs) {
                            if (d != null) {
                                d.setTintList(plusIconCsl);
                                d.setColorFilter(plusIconCol, PorterDuff.Mode.SRC_IN);
                            }
                        }
                    }
                }
            }

            // 2. Top-center '[count | grid]' pane switcher pill (0x7f010950 + 0x7f01094f TabLayout)
            View switcherCard = hubToolbar.findViewById(ID_HUB_PANE_SWITCHER_CARD);
            View switcherTab = hubToolbar.findViewById(ID_HUB_PANE_SWITCHER);
            int trackCol = isDark ? 0xFF1C1C1E : 0xFFE3E4E7;
            int indCol = isDark ? 0xFF2C2C2E : 0xFFFFFFFF;
            int iconCol = isDark ? 0xE6FFFFFF : 0xFF191919;
            ColorStateList iconCsl = ColorStateList.valueOf(iconCol);
            ColorStateList indCsl = ColorStateList.valueOf(indCol);

            View outerTrack = (switcherCard != null) ? switcherCard : switcherTab;
            if (outerTrack != null) {
                String trackTag = "coui_hub_sw_" + (isDark ? "d" : "l");
                outerTrack.setBackgroundTintList(null);
                if (!trackTag.equals(outerTrack.getTag())) {
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.RECTANGLE);
                    gd.setCornerRadius(dpF(activity, 16f));
                    gd.setColor(trackCol);
                    outerTrack.setBackground(gd);
                    outerTrack.setTag(trackTag);
                }
            }
            if (switcherCard != null && switcherTab != null) {
                switcherTab.setBackgroundTintList(null);
                if (switcherTab.getBackground() != null) {
                    switcherTab.setBackground(null);
                }
            }
            if (switcherTab != null) {
                try {
                    Method setIndCol = switcherTab.getClass().getMethod("setSelectedTabIndicatorColor", int.class);
                    setIndCol.invoke(switcherTab, indCol);
                } catch (Throwable ignored) {}
                try {
                    Method getInd = switcherTab.getClass().getMethod("getTabSelectedIndicator");
                    Object indObj = getInd.invoke(switcherTab);
                    if (indObj instanceof Drawable) {
                        Drawable ind = (Drawable) indObj;
                        ind.setTintList(indCsl);
                        ind.setColorFilter(indCol, PorterDuff.Mode.SRC_IN);
                    }
                } catch (Throwable ignored) {}
                try {
                    Method setIconTint = switcherTab.getClass().getMethod("setTabIconTint", ColorStateList.class);
                    setIconTint.invoke(switcherTab, iconCsl);
                } catch (Throwable ignored) {}
                if (switcherTab instanceof ViewGroup) {
                    stylePaneSwitcherChildrenRecursive((ViewGroup) switcherTab, iconCsl, indCsl);
                }
            }

            // 3. 'Search your tabs' pill (0x7f010b05 + 0x7f010b0d)
            View searchBox = hubToolbar.findViewById(ID_SEARCH_BOX);
            if (searchBox != null) {
                searchBox.setBackgroundTintList(null);
                styleCouiSearchBox(searchBox, isDark);
                View searchTv = searchBox.findViewById(ID_NTP_SEARCH_BOX_TEXT);
                if (searchTv instanceof TextView) {
                    TextView tv = (TextView) searchTv;
                    int hintCol = isDark ? 0x8AFFFFFF : 0xFF7F7F7F;
                    tv.setTextColor(isDark ? 0xE6FFFFFF : 0xFF191919);
                    tv.setHintTextColor(hintCol);
                }
            }
        }

        // Hide any toolbar_bottom_hairline / shadow ImageView inside tabHolder so no 1px divider line
        // cuts across the top of the tab cards below 'Search your tabs'
        if (tabHolder instanceof ViewGroup) {
            hideHairlinesRecursive((ViewGroup) tabHolder);
        }

        // 4. Tab Grid Cards inside tab_list_recycler_view (0x7f010cb2) + cushioned COUI overscroll
        View tabRv = tabHolder.findViewById(ID_TAB_LIST_RECYCLER_VIEW);
        if (tabRv instanceof ViewGroup) {
            ViewGroup rvg = (ViewGroup) tabRv;
            rvg.setOverScrollMode(View.OVER_SCROLL_NEVER);
            attachCouiOverscroll(rvg);
            int count = rvg.getChildCount();
            // First pass: check how many cards are detected as focused; if multiple cards exist,
            // verify each card's focused vs unfocused state from Chromium's incoming tint/text luminance.
            for (int i = 0; i < count; i++) {
                View cardItem = rvg.getChildAt(i);
                if (cardItem != null && cardItem.getVisibility() == View.VISIBLE) {
                    styleTabGridCardItem(cardItem, isDark);
                }
            }
        }
    }

    private static void hideHairlinesRecursive(ViewGroup vg) {
        int maxHairH = dp(vg.getContext(), 6);
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            int id = c.getId();
            boolean isHairline = (id == ID_TOOLBAR_BOTTOM_HAIRLINE || id == ID_TOOLBAR_HAIRLINE)
                    || (c.getHeight() > 0 && c.getHeight() <= maxHairH && c.getWidth() > dp(vg.getContext(), 200))
                    || (c instanceof ImageView && id != ID_HUB_NEW_TAB_BUTTON
                        && (vg.getId() == ID_HUB_TOOLBAR || vg.getId() == ID_HUB_MAIN_CONTAINER
                            || vg.getId() == ID_HUB_PANE_HOST_CONTAINER || vg.getId() == ID_HUB_PANE_HOST));
            if (isHairline) {
                if (c.getAlpha() != 0f) c.setAlpha(0f);
                if (c.getScaleY() != 0f) c.setScaleY(0f);
                if (c.getBackground() != null) c.setBackground(null);
                if (c instanceof ImageView && ((ImageView) c).getDrawable() != null) {
                    ((ImageView) c).setImageDrawable(null);
                }
            } else if (c instanceof ViewGroup && id != ID_TAB_LIST_RECYCLER_VIEW) {
                hideHairlinesRecursive((ViewGroup) c);
            }
        }
    }

    /** Crisp official Microsoft Fluent UI 24dp '+' vector icon for the Tab Switcher top-left New Tab button. */
    private static final class CouiPlusIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int sizePx;
        private boolean isDark;

        CouiPlusIconDrawable(Context ctx, boolean isDark) {
            this.isDark = isDark;
            this.sizePx = dp(ctx, 22f);
            paint.setStyle(Paint.Style.FILL);
        }

        void setDarkMode(boolean dark) {
            if (this.isDark != dark) {
                this.isDark = dark;
                invalidateSelf();
            }
        }

        @Override public int getIntrinsicWidth() { return sizePx; }
        @Override public int getIntrinsicHeight() { return sizePx; }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            paint.setColor(isDark ? 0xE6FFFFFF : 0xFF191919);
            FluentIcons.drawCentered(canvas, FluentIcons.ADD, b.exactCenterX(), b.exactCenterY(), sizePx, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static void stylePaneSwitcherChildrenRecursive(ViewGroup vg, ColorStateList iconCsl, ColorStateList indCsl) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c.getBackgroundTintList() != null && c.getBackgroundTintList() != indCsl) {
                c.setBackgroundTintList(indCsl);
            }
            if (c instanceof ImageView) {
                ImageView iv = (ImageView) c;
                iv.clearColorFilter();
                iv.setImageTintList(iconCsl);
                Drawable d = iv.getDrawable();
                if (d != null) {
                    d.setTintList(iconCsl);
                }
            } else if (c instanceof TextView) {
                ((TextView) c).setTextColor(iconCsl);
            } else if (c instanceof ViewGroup) {
                stylePaneSwitcherChildrenRecursive((ViewGroup) c, iconCsl, indCsl);
            }
        }
    }

    private static float colorLuminance(int color) {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f;
    }

    private static void styleTabGridCardItem(View itemView, boolean isDark) {
        View cardView = itemView.findViewById(ID_TAB_CARD_VIEW);
        if (cardView == null) return;
        TextView titleTv = itemView.findViewById(ID_TAB_TITLE);
        ImageView closeBtn = itemView.findViewById(ID_TAB_CLOSE_BTN);
        View thumbView = itemView.findViewById(ID_TAB_THUMBNAIL);
        View borderView = itemView.findViewById(ID_TAB_CARD_BORDER);
        View secView = itemView.findViewById(ID_TAB_CARD_SECONDARY);

        ColorStateList curTint = cardView.getBackgroundTintList();
        boolean isOurTint = (curTint == CSL_TAB_LIGHT_UNFOCUSED
                || curTint == CSL_TAB_LIGHT_FOCUSED
                || curTint == CSL_TAB_DARK_UNFOCUSED
                || curTint == CSL_TAB_DARK_FOCUSED);

        boolean isFocused;
        if (!isOurTint && curTint != null) {
            // Chromium's TabGridViewBinder just bound or updated this card's selection state!
            // In Light Mode, Chromium sets selected tab card background to dark blue (#415578, lum ~0.33 < 0.55)
            // vs unselected light grey-blue (#DCE1EC, lum ~0.88).
            // In Dark Mode, Chromium sets selected tab card background to bright accent blue (lum > 0.38)
            // vs unselected dark surface (#282A2F, lum ~0.17).
            int defCol = curTint.getDefaultColor();
            float bgLum = colorLuminance(defCol);
            if (!isDark) {
                isFocused = (bgLum < 0.55f);
            } else {
                isFocused = (bgLum > 0.38f);
            }
            cardView.setTag(isFocused ? "coui_tab_focused" : "coui_tab_unfocused");
        } else if (titleTv != null) {
            int curTextCol = titleTv.getCurrentTextColor();
            boolean isOurTextCol = (curTextCol == 0xFF191919 || curTextCol == 0xFFFFFFFF || curTextCol == 0xE6FFFFFF);
            if (!isOurTextCol) {
                // Chromium updated title text color on selection change
                float tLum = colorLuminance(curTextCol);
                isFocused = !isDark ? (tLum > 0.55f) : (tLum < 0.45f);
                cardView.setTag(isFocused ? "coui_tab_focused" : "coui_tab_unfocused");
            } else {
                isFocused = "coui_tab_focused".equals(cardView.getTag());
            }
        } else {
            isFocused = "coui_tab_focused".equals(cardView.getTag());
        }

        ColorStateList wantCardCsl = isDark
                ? (isFocused ? CSL_TAB_DARK_FOCUSED : CSL_TAB_DARK_UNFOCUSED)
                : (isFocused ? CSL_TAB_LIGHT_FOCUSED : CSL_TAB_LIGHT_UNFOCUSED);
        int wantTitleCol = isDark
                ? (isFocused ? 0xFF191919 : 0xE6FFFFFF)
                : (isFocused ? 0xFFFFFFFF : 0xFF191919);
        ColorStateList wantCloseCsl = isDark
                ? (isFocused ? CSL_CLOSE_DARK_FOCUSED : CSL_CLOSE_DARK_UNFOCUSED)
                : (isFocused ? CSL_CLOSE_LIGHT_FOCUSED : CSL_CLOSE_LIGHT_UNFOCUSED);

        if (cardView.getBackgroundTintList() != wantCardCsl) {
            cardView.setBackgroundTintList(wantCardCsl);
        }
        Drawable cbg = cardView.getBackground();
        if (cbg != null) {
            cbg.setTintList(wantCardCsl);
        }
        if (borderView != null) {
            if (borderView.getBackgroundTintList() != wantCardCsl) {
                borderView.setBackgroundTintList(wantCardCsl);
            }
            if (borderView instanceof ImageView) {
                ImageView biv = (ImageView) borderView;
                if (biv.getImageTintList() != wantCardCsl) {
                    biv.setImageTintList(wantCardCsl);
                }
            }
        }
        if (secView != null && secView.getBackgroundTintList() != wantCardCsl) {
            secView.setBackgroundTintList(wantCardCsl);
        }
        if (titleTv != null && titleTv.getCurrentTextColor() != wantTitleCol) {
            titleTv.setTextColor(wantTitleCol);
        }
        if (closeBtn != null) {
            closeBtn.clearColorFilter();
            closeBtn.setImageTintList(null);
            int wantCloseCol = wantCloseCsl.getDefaultColor();
            Drawable cd = closeBtn.getDrawable();
            if (cd instanceof FluentIcons.FluentDrawable) {
                ((FluentIcons.FluentDrawable) cd).setColor(wantCloseCol);
            } else {
                closeBtn.setImageDrawable(new FluentIcons.FluentDrawable(
                        closeBtn.getContext(), FluentIcons.DISMISS, 16.5f, wantCloseCol));
                closeBtn.setScaleType(ImageView.ScaleType.CENTER);
            }
        }
        if (thumbView != null) {
            int thumbPlaceholderCol = isDark
                    ? (isFocused ? 0xFFE3E4E7 : 0xFF262629)
                    : (isFocused ? 0xFF28282B : 0xFFF3F4F6);
            ColorStateList thumbCsl = ColorStateList.valueOf(thumbPlaceholderCol);
            thumbView.setBackgroundTintList(thumbCsl);
            // Neutralize any internal blue Paint inside TabThumbnailView when thumbnail is a placeholder
            neutralizeTabThumbnailBluePaints(thumbView, thumbPlaceholderCol);
        }
    }

    private static void neutralizeTabThumbnailBluePaints(View thumbView, int neutralCol) {
        try {
            Class<?> cls = thumbView.getClass();
            for (Field f : cls.getDeclaredFields()) {
                if (Paint.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object val = f.get(thumbView);
                    if (val instanceof Paint) {
                        Paint p = (Paint) val;
                        int c = p.getColor();
                        if (Color.alpha(c) > 0 && (Color.blue(c) > Color.red(c) + 10 || Color.blue(c) > Color.green(c) + 10)) {
                            p.setColor(neutralCol);
                            thumbView.invalidate();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Styles the New Tab Page (NTP) to match Titanium Settings 1:1 ColorOS 17 COUI neutral tokens:
     * Canvas #F0F1F3 (Light) / #000000 (Dark), Search pill & Shortcuts card #FFFFFF / #1C1C1E,
     * Tile icon circles #F0F1F3 / #2C2C2E — zero Material You lavender/blue tint.
     */
    private static void styleNtpColors(Activity activity, View root, boolean isDark) {
        if (activity == null || root == null) return;
        View ntpContent = root.findViewById(ID_NTP_CONTENT);
        View ntpScroll = root.findViewById(ID_NTP_SCROLLVIEW);
        View feedRv = root.findViewById(ID_FEED_STREAM_RV);
        if (ntpContent == null && ntpScroll == null && feedRv == null) return;

        int canvasCol = isDark ? 0xFF000000 : 0xFFF0F1F3;
        int cardCol = isDark ? 0xFF1C1C1E : 0xFFFFFFFF;
        int tileCircleCol = isDark ? 0xFF2C2C2E : 0xFFF0F1F3;
        int titleCol = isDark ? 0xE6FFFFFF : 0xFF191919;
        int hintCol = isDark ? 0x8AFFFFFF : 0xFF7F7F7F;
        int strokeCol = isDark ? 0x1AFFFFFF : 0x0D000000;

        View[] canvases = {ntpContent, ntpScroll, feedRv};
        for (View v : canvases) {
            if (v == null) continue;
            Drawable bg = v.getBackground();
            if (!(bg instanceof ColorDrawable) || ((ColorDrawable) bg).getColor() != canvasCol) {
                v.setBackgroundColor(canvasCol);
            }
            if (v instanceof ScrollView || v.getClass().getName().contains("RecyclerView")) {
                v.setOverScrollMode(View.OVER_SCROLL_NEVER);
                if (v instanceof ViewGroup) attachCouiOverscroll((ViewGroup) v);
            }
            // Also paint immediate parent NativePage container if it holds a Material tonal fill
            ViewParent p = v.getParent();
            if (p instanceof View) {
                View pv = (View) p;
                int pid = pv.getId();
                if (pid != android.R.id.content && pid != ID_EDGE_TO_EDGE_BASE) {
                    Drawable pbg = pv.getBackground();
                    if (pbg != null && (!(pbg instanceof ColorDrawable) || ((ColorDrawable) pbg).getColor() != canvasCol)) {
                        pv.setBackgroundColor(canvasCol);
                    }
                }
            }
        }

        // Clean pure #FFFFFF / #1C1C1E NTP search pill (zero inner Material You lavender/grey box!)
        View searchBox = ntpContent != null ? ntpContent.findViewById(ID_SEARCH_BOX) : root.findViewById(ID_SEARCH_BOX);
        View searchBoxBg = ntpContent != null ? ntpContent.findViewById(ID_NTP_SEARCH_BOX_BG) : root.findViewById(ID_NTP_SEARCH_BOX_BG);
        View searchBoxCont = ntpContent != null ? ntpContent.findViewById(ID_NTP_SEARCH_BOX_CONTAINER) : root.findViewById(ID_NTP_SEARCH_BOX_CONTAINER);
        View searchBoxShadow = ntpContent != null ? ntpContent.findViewById(0x7f010b0b) : root.findViewById(0x7f010b0b);
        if (searchBoxCont != null && searchBoxCont.getBackground() != null) {
            searchBoxCont.setBackground(null);
        }
        if (searchBoxShadow != null && searchBoxShadow.getBackground() != null) {
            searchBoxShadow.setBackground(null);
        }
        if (searchBoxBg != null && searchBoxBg != searchBox && searchBoxBg.getBackground() != null) {
            searchBoxBg.setBackground(null);
        }
        String pillTag = "coui_ntp_pill2_" + (isDark ? "d" : "l");
        View pillTarget = (searchBox != null) ? searchBox : searchBoxBg;
        if (pillTarget != null) {
            if (pillTarget instanceof ViewGroup) {
                stripNtpSearchBoxChildrenBg((ViewGroup) pillTarget);
            }
            if (!pillTag.equals(pillTarget.getTag())) {
                GradientDrawable pill = new GradientDrawable();
                pill.setShape(GradientDrawable.RECTANGLE);
                pill.setCornerRadius(dpF(activity, 28f));
                pill.setColor(cardCol);
                pill.setStroke(Math.max(1, dp(activity, 0.75f)), strokeCol);
                pillTarget.setBackground(pill);
                pillTarget.setTag(pillTag);
            }
        }
        View searchText = ntpContent != null ? ntpContent.findViewById(ID_NTP_SEARCH_BOX_TEXT) : root.findViewById(ID_NTP_SEARCH_BOX_TEXT);
        if (searchText instanceof TextView) {
            TextView tv = (TextView) searchText;
            if (tv.getCurrentHintTextColor() != hintCol) tv.setHintTextColor(hintCol);
            if (tv.getCurrentTextColor() != titleCol) tv.setTextColor(titleCol);
        }
        if (searchBox instanceof ViewGroup) {
            tintNtpSearchIcons((ViewGroup) searchBox, isDark ? 0xB3FFFFFF : 0xFF686D74);
        }

        // Most Visited shortcuts card: 20dp squircle #FFFFFF / #1C1C1E with neutral #F0F1F3 / #2C2C2E icon circles
        View mvCont = ntpContent != null ? ntpContent.findViewById(ID_NTP_MV_TILES_CONTAINER) : root.findViewById(ID_NTP_MV_TILES_CONTAINER);
        if (mvCont != null) {
            String mvTag = "coui_ntp_mv_" + (isDark ? "d" : "l");
            if (!mvTag.equals(mvCont.getTag())) {
                GradientDrawable card = new GradientDrawable();
                card.setShape(GradientDrawable.RECTANGLE);
                card.setCornerRadius(dpF(activity, 20f));
                card.setColor(cardCol);
                mvCont.setBackground(card);
                mvCont.setTag(mvTag);
            }
            if (mvCont instanceof ViewGroup) {
                styleNtpTilesRecursive((ViewGroup) mvCont, activity, isDark, tileCircleCol, titleCol);
            }
        }
    }

    private static void stripNtpSearchBoxChildrenBg(ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            Drawable bg = c.getBackground();
            if (bg != null && !(bg instanceof RippleDrawable)) {
                c.setBackground(null);
            }
            if (c instanceof ViewGroup) {
                stripNtpSearchBoxChildrenBg((ViewGroup) c);
            }
        }
    }

    private static void tintNtpSearchIcons(ViewGroup vg, int tint) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c instanceof ImageView) {
                ((ImageView) c).setColorFilter(tint, PorterDuff.Mode.SRC_IN);
            } else if (c instanceof ViewGroup) {
                tintNtpSearchIcons((ViewGroup) c, tint);
            }
        }
    }

    private static void styleNtpTilesRecursive(ViewGroup vg, Context ctx, boolean isDark, int circleCol, int titleCol) {
        String circleTag = "coui_tile_bg_" + (isDark ? "d" : "l");
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            int id = c.getId();
            if (id == ID_NTP_TILE_ICON_BG) {
                if (!circleTag.equals(c.getTag())) {
                    GradientDrawable gd = new GradientDrawable();
                    gd.setShape(GradientDrawable.OVAL);
                    gd.setColor(circleCol);
                    c.setBackground(gd);
                    if (c instanceof ImageView) {
                        ((ImageView) c).setImageDrawable(null);
                    }
                    c.setTag(circleTag);
                }
            } else if (id == ID_NTP_TILE_TITLE && c instanceof TextView) {
                TextView tv = (TextView) c;
                if (tv.getCurrentTextColor() != titleCol) {
                    tv.setTextColor(titleCol);
                }
            } else if (c instanceof ViewGroup) {
                styleNtpTilesRecursive((ViewGroup) c, ctx, isDark, circleCol, titleCol);
            }
        }
    }

    /** Enforces crisp Light/Dark status bar icon contrast + transparent status bar on every frame/navigation. */
    private static void enforceBrowserStatusBarAppearance(Activity activity, boolean isDark, boolean isFullscreen) {
        if (activity == null || isFullscreen) return;
        Window w = activity.getWindow();
        if (w == null) return;
        if (w.getStatusBarColor() != Color.TRANSPARENT) {
            w.setStatusBarColor(Color.TRANSPARENT);
        }
        clearEdgeToEdgeStatusBarPaint(activity);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController wic = w.getInsetsController();
            if (wic != null) {
                int flag = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                int want = isDark ? 0 : flag;
                if ((wic.getSystemBarsAppearance() & flag) != want) {
                    wic.setSystemBarsAppearance(want, flag);
                }
            }
        }
    }

    /** Ensures no white-on-white or dark-on-dark UrlEmphasisColorSpan ever renders inside url_bar. */
    private static void enforceUrlBarSpanContrast(TextView tv, boolean isDark) {
        CharSequence cs = tv.getText();
        if (!(cs instanceof Spannable) || cs.length() == 0) return;
        Spannable sp = (Spannable) cs;
        ForegroundColorSpan[] spans = sp.getSpans(0, sp.length(), ForegroundColorSpan.class);
        if (spans == null || spans.length == 0) return;
        int primaryCol = isDark ? 0xF2FFFFFF : 0xFF191919;
        int secondaryCol = isDark ? 0x99FFFFFF : 0xFF757575;
        for (ForegroundColorSpan s : spans) {
            int col = s.getForegroundColor();
            float lum = (0.299f * Color.red(col) + 0.587f * Color.green(col) + 0.114f * Color.blue(col)) / 255f;
            boolean badContrast = (!isDark && lum > 0.52f) || (isDark && lum < 0.45f);
            if (badContrast) {
                int start = sp.getSpanStart(s);
                int end = sp.getSpanEnd(s);
                int flags = sp.getSpanFlags(s);
                if (start >= 0 && end > start) {
                    // Preserve domain-vs-scheme/path hierarchy: higher-alpha spans are primary host, lower are dimmed
                    int alpha = Color.alpha(col);
                    int replacement = (alpha >= 220) ? primaryCol : secondaryCol;
                    sp.removeSpan(s);
                    sp.setSpan(new ForegroundColorSpan(replacement), start, end, flags);
                }
            }
        }
    }

    /** Hooked into ChromeTabbedActivity.onResume(). */
    public static void attachToBrowser(final Activity activity) {
        if (activity == null) return;
        try {
            Window w = activity.getWindow();
            if (w != null) {
                boolean dark = isDarkMode(activity);
                w.setNavigationBarColor(Color.TRANSPARENT);
                // Round 11: fully transparent status bar — the frosted glass header + SurfaceControl blur
                // now extend edge-to-edge from y = 0 (see onEdgeToEdgePadding / extraTopControlsHeight).
                w.setStatusBarColor(Color.TRANSPARENT);
                if (Build.VERSION.SDK_INT >= 29) {
                    w.setStatusBarContrastEnforced(false);
                }
                clearEdgeToEdgeStatusBarPaint(activity);
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowInsetsController wic = w.getInsetsController();
                    if (wic != null) {
                        int flag = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                        wic.setSystemBarsAppearance(dark ? 0 : flag, flag);
                    }
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    w.setNavigationBarContrastEnforced(false);
                }
            }
            final ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) return;
            hideTopToolbarButtons(w != null ? w.getDecorView() : content);

            View existing = content.findViewWithTag(NAV_TAG);
            final CouiLiquidGlassNavBar navBar;
            if (existing instanceof CouiLiquidGlassNavBar) {
                navBar = (CouiLiquidGlassNavBar) existing;
                navBar.updateTheme();
                existing.bringToFront();
            } else {
                navBar = new CouiLiquidGlassNavBar(activity);
                navBar.setTag(NAV_TAG);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
                );
                content.addView(navBar, lp);
                navBar.bringToFront();
            }
            sActiveNavBar = new java.lang.ref.WeakReference<>(navBar);
            installScrollTouchObserver(activity, navBar);
        } catch (Throwable t) {
            Log.w(TAG, "attachToBrowser failed", t);
        }
    }

    /** Wraps Activity Window.Callback once to observe vertical scroll touches for reliable auto-hide. */
    private static void installScrollTouchObserver(Activity activity, CouiLiquidGlassNavBar navBar) {
        if (activity == null || navBar == null) return;
        Window w = activity.getWindow();
        if (w == null) return;
        Window.Callback cur = w.getCallback();
        if (cur == null) return;
        if (cur instanceof CouiScrollTouchCallback) {
            ((CouiScrollTouchCallback) cur).navBar = navBar;
            return;
        }
        w.setCallback(new CouiScrollTouchCallback(cur, navBar));
    }

    private static final class CouiScrollTouchCallback implements Window.Callback {
        private final Window.Callback base;
        CouiLiquidGlassNavBar navBar;

        CouiScrollTouchCallback(Window.Callback base, CouiLiquidGlassNavBar navBar) {
            this.base = base;
            this.navBar = navBar;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            try {
                if (navBar != null && event != null) {
                    navBar.onWindowTouchEvent(event);
                }
            } catch (Throwable t) {
                Log.w(TAG, "onWindowTouchEvent err", t);
            }
            return base.dispatchTouchEvent(event);
        }

        @Override public boolean dispatchKeyEvent(KeyEvent e) { return base.dispatchKeyEvent(e); }
        @Override public boolean dispatchKeyShortcutEvent(KeyEvent e) { return base.dispatchKeyShortcutEvent(e); }
        @Override public boolean dispatchTrackballEvent(MotionEvent e) { return base.dispatchTrackballEvent(e); }
        @Override public boolean dispatchGenericMotionEvent(MotionEvent e) { return base.dispatchGenericMotionEvent(e); }
        @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent e) { return base.dispatchPopulateAccessibilityEvent(e); }
        @Override public View onCreatePanelView(int f) { return base.onCreatePanelView(f); }
        @Override public boolean onCreatePanelMenu(int f, Menu m) { return base.onCreatePanelMenu(f, m); }
        @Override public boolean onPreparePanel(int f, View v, Menu m) { return base.onPreparePanel(f, v, m); }
        @Override public boolean onMenuOpened(int f, Menu m) { return base.onMenuOpened(f, m); }
        @Override public boolean onMenuItemSelected(int f, MenuItem item) { return base.onMenuItemSelected(f, item); }
        @Override public void onWindowAttributesChanged(WindowManager.LayoutParams a) { base.onWindowAttributesChanged(a); }
        @Override public void onContentChanged() { base.onContentChanged(); }
        @Override public void onWindowFocusChanged(boolean hasFocus) { base.onWindowFocusChanged(hasFocus); }
        @Override public void onAttachedToWindow() { base.onAttachedToWindow(); }
        @Override public void onDetachedFromWindow() { base.onDetachedFromWindow(); }
        @Override public void onPanelClosed(int f, Menu m) { base.onPanelClosed(f, m); }
        @Override public boolean onSearchRequested() { return base.onSearchRequested(); }
        @Override public boolean onSearchRequested(SearchEvent e) { return base.onSearchRequested(e); }
        @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback cb) { return base.onWindowStartingActionMode(cb); }
        @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback cb, int type) { return base.onWindowStartingActionMode(cb, type); }
        @Override public void onActionModeStarted(ActionMode mode) { base.onActionModeStarted(mode); }
        @Override public void onActionModeFinished(ActionMode mode) { base.onActionModeFinished(mode); }
    }

    /**
     * Permanently hides redundant top toolbar buttons (Home, Extensions, Tab counter, Three-dots menu)
     * using scaleX=0, scaleY=0, alpha=0, and translationY=-400dp so ToolbarPhone scroll-up resets
     * (which overwrite translationY/alpha on U/W) can NEVER make [2] or 3-dots reappear,
     * while programmatic performClick() continues to work 100%.
     */
    static void hideTopToolbarButtons(View root) {
        if (root == null) return;
        float offY = -dpF(root.getContext(), 400f);
        int[] ids = {
            ID_HOME_BUTTON,
            ID_TOOLBAR_BUTTONS,
            ID_EXTENSIONS_BUTTON,
            ID_EXTENSIONS_CONTAINER,
            ID_TAB_SWITCHER_BUTTON,
            ID_FAKE_TAB_SWITCHER_BUTTON,
            ID_OPTIONAL_BUTTON,
            ID_OPTIONAL_CONTAINER,
            ID_MENU_BUTTON,
            ID_MENU_BUTTON_WRAPPER,
            ID_MENU_BUTTON_CONTAINER
        };
        for (int id : ids) {
            View v = root.findViewById(id);
            if (v != null) {
                // Round 11: scale 0.001 (not 0) keeps the view matrix INVERTIBLE, so touch hit-testing honours
                // the -400dp translation; with scale 0 Android fell back to the layout bounds and the invisible
                // Home / tab-stack / menu buttons could still swallow taps on the ends of the search pill.
                if (v.getAlpha() != 0f) v.setAlpha(0f);
                if (v.getScaleX() != 0.001f) v.setScaleX(0.001f);
                if (v.getScaleY() != 0.001f) v.setScaleY(0.001f);
                if (v.getTranslationY() != offY) v.setTranslationY(offY);
            }
        }
        // Hide top toolbar hairlines so the frosted glass address bar floats cleanly
        // Round 11: pin hairlines at alpha 0 (Chromium re-shows them; toggling GONE forced relayouts at 12Hz)
        // (Chromium re-animates their alpha, so scaleY = 0 is what actually keeps them from drawing.)
        View hairline = root.findViewById(ID_TOOLBAR_HAIRLINE);
        if (hairline != null) {
            if (hairline.getScaleY() != 0f) hairline.setScaleY(0f);
            if (hairline.getAlpha() != 0f) hairline.setAlpha(0f);
        }
        View botHairline = root.findViewById(ID_TOOLBAR_BOTTOM_HAIRLINE);
        if (botHairline != null) {
            if (botHairline.getScaleY() != 0f) botHairline.setScaleY(0f);
            if (botHairline.getAlpha() != 0f) botHairline.setAlpha(0f);
        }
        // Collapse any bottom chin scrim if present so the 3-piece bar floats over web content
        View bottomContainer = root.findViewById(0x7f0101f6); // id/bottom_container
        if (bottomContainer != null) {
            bottomContainer.setBackground(null);
        }
    }

    private static final String HEADER_GLASS_TAG = "coui_header_glass";

    /** Non-interactive full-bleed view hosting the header glass drawable inside control_container. */
    static final class CouiHeaderGlassView extends View {
        final CouiTopHeaderGlassDrawable glass;

        CouiHeaderGlassView(Context ctx, CouiTopHeaderGlassDrawable glass) {
            super(ctx);
            this.glass = glass;
            setTag(HEADER_GLASS_TAG);
            setBackground(glass);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        /**
         * Never influences control_container's wrap_content height: report 0 on the AT_MOST/UNSPECIFIED pass,
         * take the exact size in FrameLayout's match_parent re-measure pass (parent height already final).
         */
        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec);
            int h = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightSpec) : 0;
            setMeasuredDimension(w, h);
        }
    }

    static CouiTopHeaderGlassDrawable ensureHeaderGlassView(Activity activity, View controlContainer,
            boolean isDark, boolean glassActive) {
        if (!(controlContainer instanceof ViewGroup)) return null;
        ViewGroup cc = (ViewGroup) controlContainer;
        View first = cc.findViewWithTag(HEADER_GLASS_TAG);
        if (first instanceof CouiHeaderGlassView && first.getParent() == cc) {
            CouiTopHeaderGlassDrawable g = ((CouiHeaderGlassView) first).glass;
            g.updateState(isDark, glassActive);
            if (first.getHeight() != cc.getHeight() && !first.isLayoutRequested()) {
                first.requestLayout(); // parent re-sized without a match_parent re-measure pass
            }
            return g;
        }
        if (first != null && first.getParent() instanceof ViewGroup) ((ViewGroup) first.getParent()).removeView(first);
        CouiTopHeaderGlassDrawable g = new CouiTopHeaderGlassDrawable(activity, isDark, glassActive);
        // Appended LAST (Chromium addresses control_container children by index, e.g. getChildAt(0) ==
        // toolbar_container) and pushed BEHIND its siblings with translationZ = -1 (FrameLayout draws and
        // dispatches touches in Z order; this view is not clickable either way).
        CouiHeaderGlassView gv = new CouiHeaderGlassView(activity, g);
        gv.setTranslationZ(-1f);
        cc.addView(gv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return g;
    }

    private static long sLastPillAuditMs = 0L;

    /**
     * Clears every non-ripple background (ColorDrawable / GradientDrawable / any opaque fill) on all
     * descendants of the location bar pill so the glass capsule is uniformly translucent behind both
     * the icon and the URL text. RippleDrawables are kept (transparent at rest, needed for press feedback).
     */
    static void stripOpaqueChildBackgrounds(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            Drawable bg = c.getBackground();
            if (bg != null && !(bg instanceof android.graphics.drawable.RippleDrawable)
                    && !(bg instanceof CouiAddressBarGlassDrawable)) {
                c.setBackground(null);
            }
            if (c instanceof ViewGroup) {
                stripOpaqueChildBackgrounds((ViewGroup) c);
            }
        }
    }

    private static Field sToolbarBgField;
    private static Field sLocationBarDrawableField;
    private static boolean sToolbarFieldsResolved = false;

    /**
     * Styles Titanium Browser's top header bar (ID_CONTROL_CONTAINER) as a full-width frosted
     * Liquid Glass bar matching the bottom 3-piece nav bar (ZERO side-corner leaks or clipped gaps),
     * and styles the inner address pill (ID_LOCATION_BAR) to 1:1 ColorOS 17 File Manager / Settings
     * search pill parity (44dp capsule, luminous elevated glass with Kyant0/AndroidLiquidGlass 45°
     * specular rim, exact ColorOS 17 search magnifying-glass icon on the left, NO Cancel button).
     */
    static void styleTopAddressBarGlass(Activity activity, View root, boolean isDark, boolean realBlur) {
        if (activity == null || root == null) return;
        try {
            boolean inTabSwitcher = false;
            View tabHolder = root.findViewById(ID_TAB_SWITCHER_HOLDER);
            if (tabHolder != null && tabHolder.getVisibility() == View.VISIBLE
                    && tabHolder instanceof ViewGroup && ((ViewGroup) tabHolder).getChildCount() > 0) {
                inTabSwitcher = true;
            }
            boolean isFullscreen = false;
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsets wi = root.getRootWindowInsets();
                if (wi != null && !wi.isVisible(WindowInsets.Type.statusBars())) {
                    isFullscreen = true;
                }
            }

            View urlBar = root.findViewById(ID_URL_BAR);
            boolean focused = (urlBar != null && urlBar.hasFocus());
            boolean glassHeaderActive = realBlur && !focused && !inTabSwitcher && !isFullscreen;

            // Round 12: Enforce status bar icon contrast (fixes white icons on keybox.tryigit.dev),
            // apply 1.30x statusBarHeight top inset to Tab Switcher, and style NTP to 1:1 COUI tokens.
            enforceBrowserStatusBarAppearance(activity, isDark, isFullscreen);
            applyTabSwitcherTopInset(activity, root, isDark);
            styleNtpColors(activity, root, isDark);

            // 1. Full-width Frosted Liquid Glass Top Header Backdrop on ID_CONTROL_CONTAINER
            //    (Round 12: no longer hard-pinned at y=0; CouiLiquidGlassNavBar drives Echo-Music auto-hide!)
            View controlContainer = root.findViewById(ID_CONTROL_CONTAINER);
            CouiTopHeaderGlassDrawable headerGlass = null;
            if (controlContainer != null) {
                headerGlass = ensureHeaderGlassView(activity, controlContainer, isDark, glassHeaderActive);
                if (controlContainer.getBackground() != null) {
                    controlContainer.setBackground(null);
                }
                if (!inTabSwitcher && !isFullscreen) {
                    if (controlContainer.getVisibility() == View.INVISIBLE) {
                        controlContainer.setVisibility(View.VISIBLE);
                    }
                }
            }
            View toolbarContainer = root.findViewById(ID_TOOLBAR_CONTAINER);
            if (toolbarContainer != null) {
                if (toolbarContainer.getBackground() != null) {
                    toolbarContainer.setBackground(null);
                }
                int wantTop = extraTopControlsHeight(controlContainer != null ? controlContainer : toolbarContainer);
                if (toolbarContainer.getPaddingTop() != wantTop) {
                    toolbarContainer.setPadding(toolbarContainer.getPaddingLeft(), wantTop,
                            toolbarContainer.getPaddingRight(), toolbarContainer.getPaddingBottom());
                }
                if (!inTabSwitcher && !isFullscreen && Math.abs(toolbarContainer.getTranslationY()) > 0.5f) {
                    toolbarContainer.setTranslationY(0f);
                }
            }
            View toolbar = root.findViewById(ID_TOOLBAR);
            if (toolbar != null) {
                if (toolbar.getBackground() != null) {
                    toolbar.setBackground(null);
                }
                if (!inTabSwitcher && !isFullscreen) {
                    if (Math.abs(toolbar.getTranslationY()) > 0.5f) {
                        toolbar.setTranslationY(0f);
                    }
                    if (toolbar.getVisibility() == View.INVISIBLE) {
                        toolbar.setVisibility(View.VISIBLE);
                    }
                }
                if (!sToolbarFieldsResolved) {
                    sToolbarFieldsResolved = true;
                    try {
                        sToolbarBgField = toolbar.getClass().getDeclaredField("s0");
                        sToolbarBgField.setAccessible(true);
                    } catch (Throwable ignored) {}
                    try {
                        sLocationBarDrawableField = toolbar.getClass().getDeclaredField("t0");
                        sLocationBarDrawableField.setAccessible(true);
                    } catch (Throwable ignored) {}
                }
                if (sToolbarBgField != null) {
                    Object bgObj = sToolbarBgField.get(toolbar);
                    if (bgObj instanceof ColorDrawable) {
                        ColorDrawable cd = (ColorDrawable) bgObj;
                        if (cd.getColor() != Color.TRANSPARENT) {
                            cd.setColor(Color.TRANSPARENT);
                        }
                    }
                }
                if (sLocationBarDrawableField != null) {
                    Object lbObj = sLocationBarDrawableField.get(toolbar);
                    if (lbObj instanceof Drawable) {
                        Drawable d = (Drawable) lbObj;
                        if (d.getAlpha() != 0) {
                            d.setAlpha(0);
                        }
                    }
                }
            }
            View locBarBg = root.findViewById(ID_LOCATION_BAR_BG);
            if (locBarBg != null && locBarBg.getAlpha() != 0f) {
                locBarBg.setAlpha(0f);
            }

            // 2. Inner 40dp ColorOS 17 Settings Liquid Glass Search Pill (ID_LOCATION_BAR) — 1:1 with
            //    settings-search-example.jpg: 40dp capsule whose top sits 4.3dp below the status bar.
            View locBar = root.findViewById(ID_LOCATION_BAR);
            if (locBar != null) {
                ViewGroup.LayoutParams rawLp = locBar.getLayoutParams();
                int targetH = dp(activity, 40);
                int targetHPad = dp(activity, 14);
                int targetTop = Math.round(dpF(activity, 4.3f));
                if (rawLp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) rawLp;
                    int targetMarginH = dp(activity, 16);
                    boolean lpChanged = false;
                    if (mlp.height != targetH) {
                        mlp.height = targetH;
                        lpChanged = true;
                    }
                    if (mlp.leftMargin != targetMarginH) {
                        mlp.leftMargin = targetMarginH;
                        lpChanged = true;
                    }
                    if (mlp.rightMargin != targetMarginH) {
                        mlp.rightMargin = targetMarginH;
                        lpChanged = true;
                    }
                    if (mlp.topMargin != targetTop || mlp.bottomMargin != 0) {
                        mlp.topMargin = targetTop;
                        mlp.bottomMargin = 0;
                        lpChanged = true;
                    }
                    if (mlp instanceof FrameLayout.LayoutParams) {
                        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) mlp;
                        if ((flp.gravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                            flp.gravity = (flp.gravity & ~Gravity.VERTICAL_GRAVITY_MASK) | Gravity.TOP;
                            lpChanged = true;
                        }
                    }
                    if (lpChanged) {
                        locBar.setLayoutParams(mlp);
                    }
                }
                // Start padding 5.5dp puts the lens center at x=122 like settings-search-example.jpg
                int targetStartPad = Math.round(dpF(activity, 5.5f));
                if (locBar.getPaddingLeft() != targetStartPad || locBar.getPaddingRight() != targetHPad
                        || locBar.getPaddingTop() != 0 || locBar.getPaddingBottom() != 0) {
                    locBar.setPadding(targetStartPad, 0, targetHPad, 0);
                }

                Drawable curBg = locBar.getBackground();
                if (curBg instanceof CouiAddressBarGlassDrawable) {
                    ((CouiAddressBarGlassDrawable) curBg).updateState(isDark, glassHeaderActive);
                } else {
                    CouiAddressBarGlassDrawable glassBg = new CouiAddressBarGlassDrawable(activity, isDark, glassHeaderActive);
                    locBar.setBackground(glassBg);
                    // Round 11: no RenderNode elevation shadow (it darkens the translucent glass from below).
                    // The soft ambient shadow is drawn OUTSIDE the capsule by CouiTopHeaderGlassDrawable.
                    locBar.setElevation(0f);
                    locBar.setOutlineProvider(null);
                    locBar.setClipToOutline(false);
                }
                long nowAudit = SystemClock.uptimeMillis();
                if (nowAudit - sLastPillAuditMs >= 1000L && locBar instanceof ViewGroup) {
                    sLastPillAuditMs = nowAudit;
                    stripOpaqueChildBackgrounds((ViewGroup) locBar);
                }
                // Feed the pill rect (in control_container coordinates) to the header for its drop shadow.
                if (controlContainer != null && headerGlass != null && locBar.getWidth() > 0) {
                    int px = 0, py = 0;
                    View cur = locBar;
                    while (cur != null && cur != controlContainer) {
                        px += cur.getLeft();
                        py += cur.getTop();
                        ViewParent pp = cur.getParent();
                        cur = (pp instanceof View) ? (View) pp : null;
                    }
                    if (cur == controlContainer) {
                        headerGlass.setPillRect(
                                px, py, px + locBar.getWidth(), py + locBar.getHeight(), locBar.getVisibility() == View.VISIBLE);
                    }
                }
                // NTP: Chromium fades the location bar CONTENTS out while the page's own search box is on screen.
                // Mirror that effective alpha on the capsule + shadow so no empty glass pill floats at the top.
                float contentAlpha = 1f;
                if (urlBar != null) {
                    if (urlBar.getVisibility() != View.VISIBLE) {
                        contentAlpha = 0f;
                    } else {
                        for (View c = urlBar; c != null && c != locBar; ) {
                            contentAlpha *= c.getAlpha();
                            if (c.getVisibility() != View.VISIBLE) contentAlpha = 0f;
                            ViewParent pp = c.getParent();
                            c = (pp instanceof View) ? (View) pp : null;
                        }
                    }
                }
                if (locBar.getBackground() instanceof CouiAddressBarGlassDrawable) {
                    ((CouiAddressBarGlassDrawable) locBar.getBackground()).setContentAlpha(contentAlpha);
                }
                if (headerGlass != null) {
                    // The shadow belongs to the capsule as rendered: include locBar's own alpha and its ancestors'
                    // (Chromium sets location_bar alpha 0 on the NTP while the page's fake search box is showing).
                    float pillA = contentAlpha;
                    for (View c = locBar; c != null && c != controlContainer; ) {
                        pillA *= c.getAlpha();
                        if (c.getVisibility() != View.VISIBLE) pillA = 0f;
                        ViewParent pp = c.getParent();
                        c = (pp instanceof View) ? (View) pp : null;
                    }
                    headerGlass.setPillAlpha(pillA);
                }

                // 3. Replace Chromium's 2-slider status icon with exact ColorOS 17 search magnifying-glass icon
                //    (1:1 file-manager-address.jpg & titanium-settings-address.jpg) while preserving click-to-site-info!
                //    Force dead-center vertical alignment on both status container & url_bar inside the 44dp pill!
                View statusIconV = locBar.findViewById(ID_LOCATION_BAR_STATUS_ICON);
                if (statusIconV instanceof ImageView) {
                    ImageView iv = (ImageView) statusIconV;
                    Drawable curIc = iv.getDrawable();
                    if (!(curIc instanceof CouiSearchGlassIconDrawable)) {
                        iv.setImageDrawable(new CouiSearchGlassIconDrawable(activity, isDark));
                        iv.setImageTintList(null);
                        iv.setScaleType(ImageView.ScaleType.CENTER);
                        iv.setVisibility(View.VISIBLE);
                    } else {
                        ((CouiSearchGlassIconDrawable) curIc).setDarkMode(isDark);
                    }
                    if (iv.getPaddingTop() != 0 || iv.getPaddingBottom() != 0) {
                        iv.setPadding(iv.getPaddingLeft(), 0, iv.getPaddingRight(), 0);
                    }
                    if (iv.getTranslationY() != 0f) iv.setTranslationY(0f);
                    // Round 11: the icon used to sit at the BOTTOM of its 44dp row (y 206..284 vs pill center
                    // 225). Center it in its parent LinearLayout (location_bar_status_icon_view) exactly.
                    ViewGroup.LayoutParams ilp = iv.getLayoutParams();
                    if (ilp instanceof LinearLayout.LayoutParams) {
                        LinearLayout.LayoutParams l = (LinearLayout.LayoutParams) ilp;
                        if (l.gravity != Gravity.CENTER_VERTICAL || l.topMargin != 0 || l.bottomMargin != 0) {
                            l.gravity = Gravity.CENTER_VERTICAL;
                            l.topMargin = 0;
                            l.bottomMargin = 0;
                            iv.setLayoutParams(l);
                        }
                    } else if (ilp instanceof FrameLayout.LayoutParams) {
                        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) ilp;
                        if ((l.gravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.CENTER_VERTICAL
                                || l.topMargin != 0 || l.bottomMargin != 0) {
                            l.gravity = (l.gravity & ~Gravity.VERTICAL_GRAVITY_MASK) | Gravity.CENTER_VERTICAL;
                            l.topMargin = 0;
                            l.bottomMargin = 0;
                            iv.setLayoutParams(l);
                        }
                    }
                    ViewParent ivp = iv.getParent();
                    if (ivp instanceof LinearLayout) {
                        LinearLayout ll = (LinearLayout) ivp;
                        // StatusIconView (location_bar_status_icon_view) keeps Chromium's fixed 56dp height
                        // (182px) inside our 40dp pill, which pushed the centered icon 26px low. Make it
                        // fill the pill height so the icon is optically dead-center with the URL text.
                        ViewGroup.LayoutParams plp = ll.getLayoutParams();
                        if (plp != null && plp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                            plp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                            if (plp instanceof LinearLayout.LayoutParams) {
                                ((LinearLayout.LayoutParams) plp).gravity = Gravity.CENTER_VERTICAL;
                            }
                            ll.setLayoutParams(plp);
                        }
                        if ((ll.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.CENTER_VERTICAL) {
                            ll.setGravity((ll.getGravity() & ~Gravity.VERTICAL_GRAVITY_MASK) | Gravity.CENTER_VERTICAL);
                        }
                        if (ll.getPaddingTop() != 0 || ll.getPaddingBottom() != 0) {
                            ll.setPadding(ll.getPaddingLeft(), 0, ll.getPaddingRight(), 0);
                        }
                        if (ll.getTranslationY() != 0f) ll.setTranslationY(0f);
                        // Shrink the 8dp icon->text holding space to 2.5dp: text starts at x=183 (1:1 target)
                        int wantGap = Math.round(dpF(activity, 2.5f));
                        for (int ci = 0; ci < ll.getChildCount(); ci++) {
                            View gap = ll.getChildAt(ci);
                            if (gap == iv || gap instanceof ImageView || gap instanceof ViewGroup) continue;
                            ViewGroup.LayoutParams glp = gap.getLayoutParams();
                            if (glp != null && glp.width != wantGap) {
                                glp.width = wantGap;
                                gap.setLayoutParams(glp);
                            }
                        }
                    }
                }
                View statusContainer = locBar.findViewById(ID_LOCATION_BAR_STATUS);
                if (statusContainer != null) {
                    if (statusContainer.getVisibility() != View.VISIBLE) {
                        statusContainer.setVisibility(View.VISIBLE);
                    }
                    if (statusContainer.getPaddingTop() != 0 || statusContainer.getPaddingBottom() != 0) {
                        statusContainer.setPadding(statusContainer.getPaddingLeft(), 0, statusContainer.getPaddingRight(), 0);
                    }
                    ViewGroup.LayoutParams slp = statusContainer.getLayoutParams();
                    if (slp instanceof FrameLayout.LayoutParams) {
                        FrameLayout.LayoutParams fslp = (FrameLayout.LayoutParams) slp;
                        if (fslp.height != ViewGroup.LayoutParams.MATCH_PARENT
                                || (fslp.gravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.CENTER_VERTICAL) {
                            fslp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                            fslp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                            statusContainer.setLayoutParams(fslp);
                        }
                    } else if (slp instanceof LinearLayout.LayoutParams) {
                        LinearLayout.LayoutParams lslp = (LinearLayout.LayoutParams) slp;
                        if (lslp.gravity != Gravity.CENTER_VERTICAL || lslp.topMargin != 0 || lslp.bottomMargin != 0) {
                            lslp.gravity = Gravity.CENTER_VERTICAL;
                            lslp.topMargin = 0;
                            lslp.bottomMargin = 0;
                            statusContainer.setLayoutParams(lslp);
                        }
                    }
                    // Round 11: StatusView keeps Chromium's fixed 56dp (182px) LayoutParams height inside the
                    // 40dp pill (LocationBarLayout params, neither Frame nor LinearLayout), which dropped the
                    // icon 26px below the pill center. Force it to fill the pill whatever the params type.
                    slp = statusContainer.getLayoutParams();
                    if (slp != null && slp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                        slp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        if (slp instanceof ViewGroup.MarginLayoutParams) {
                            ((ViewGroup.MarginLayoutParams) slp).topMargin = 0;
                            ((ViewGroup.MarginLayoutParams) slp).bottomMargin = 0;
                        }
                        statusContainer.setLayoutParams(slp);
                    }
                }
                // 4. Clean up long-query omnibox input (entering-search.jpg):
                //    - Completely suppress Chromium 156 Fusebox '+' (0x7f0105c0), request-type (0x7f0105c4),
                //      activation chip (0x7f0105b1), attachments (0x7f010720), blue '>' navigate pill (0x7f010853),
                //      mic (0x7f0107b8), and lens (0x7f0106f4) so they NEVER peek under left icon or overlap 'X'.
                int[] suppressOmniboxIds = {
                    ID_FUSEBOX_PLUS_BUTTON,
                    ID_FUSEBOX_REQUEST_TYPE,
                    ID_FUSEBOX_CHIP,
                    ID_LOCATION_BAR_ATTACHMENTS,
                    ID_NAVIGATE_BUTTON,
                    ID_MIC_BUTTON,
                    ID_LENS_BUTTON
                };
                for (int sid : suppressOmniboxIds) {
                    View sv = locBar.findViewById(sid);
                    if (sv != null) {
                        if (sv.getVisibility() != View.GONE) sv.setVisibility(View.GONE);
                        if (sv.getAlpha() != 0f) sv.setAlpha(0f);
                        if (sv.getScaleX() != 0.001f) sv.setScaleX(0.001f);
                        if (sv.getScaleY() != 0.001f) sv.setScaleY(0.001f);
                    }
                }
                // Style single clean Fluent 'X' clear button (delete_button 0x7f0103cf) centered vertically on right
                View delBtn = locBar.findViewById(ID_DELETE_BUTTON);
                if (delBtn instanceof ImageView) {
                    ImageView div = (ImageView) delBtn;
                    int xCol = isDark ? 0xB3FFFFFF : 0xFF686D74;
                    div.clearColorFilter();
                    div.setImageTintList(null);
                    Drawable dd = div.getDrawable();
                    if (dd instanceof FluentIcons.FluentDrawable) {
                        ((FluentIcons.FluentDrawable) dd).setColor(xCol);
                    } else {
                        div.setImageDrawable(new FluentIcons.FluentDrawable(activity, FluentIcons.DISMISS, 18f, xCol));
                        div.setScaleType(ImageView.ScaleType.CENTER);
                    }
                    if (div.getPaddingTop() != 0 || div.getPaddingBottom() != 0) {
                        div.setPadding(dp(activity, 6), 0, dp(activity, 2), 0);
                    }
                    ViewGroup.LayoutParams dlp = div.getLayoutParams();
                    if (dlp != null) {
                        int wantW = dp(activity, 32);
                        boolean dChanged = false;
                        if (dlp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                            dlp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                            dChanged = true;
                        }
                        if (dlp.width != wantW) {
                            dlp.width = wantW;
                            dChanged = true;
                        }
                        if (dlp instanceof ViewGroup.MarginLayoutParams) {
                            ViewGroup.MarginLayoutParams dmlp = (ViewGroup.MarginLayoutParams) dlp;
                            if (dmlp.topMargin != 0 || dmlp.bottomMargin != 0 || dmlp.rightMargin != 0) {
                                dmlp.topMargin = 0;
                                dmlp.bottomMargin = 0;
                                dmlp.rightMargin = 0;
                                dChanged = true;
                            }
                        }
                        if (dChanged) div.setLayoutParams(dlp);
                    }
                }
                if (urlBar instanceof TextView) {
                    TextView tv = (TextView) urlBar;
                    int wantCol = isDark ? 0xF2FFFFFF : 0xFF191919;
                    int wantHint = isDark ? 0x80FFFFFF : 0xFF7F7F7F;
                    if (tv.getCurrentTextColor() != wantCol) tv.setTextColor(wantCol);
                    if (tv.getCurrentHintTextColor() != wantHint) tv.setHintTextColor(wantHint);
                    enforceUrlBarSpanContrast(tv, isDark);
                    // Enforce single-line horizontally scrolling input so long queries NEVER wrap or clip!
                    if (tv.getMaxLines() != 1) {
                        tv.setSingleLine(true);
                        tv.setMaxLines(1);
                    }
                    tv.setHorizontallyScrolling(true);
                    if (focused) {
                        if (tv.getEllipsize() != null) tv.setEllipsize(null);
                    } else if (tv.getEllipsize() != TextUtils.TruncateAt.END) {
                        tv.setEllipsize(TextUtils.TruncateAt.END);
                    }
                    if (tv.getIncludeFontPadding()) {
                        tv.setIncludeFontPadding(false);
                    }
                    if ((tv.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.CENTER_VERTICAL) {
                        tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    }
                    int wantRightPad = (delBtn != null && delBtn.getVisibility() == View.VISIBLE)
                            ? dp(activity, 6) : dp(activity, 6);
                    if (tv.getPaddingTop() != 0 || tv.getPaddingBottom() != 0 || tv.getPaddingRight() != wantRightPad) {
                        tv.setPadding(tv.getPaddingLeft(), 0, wantRightPad, 0);
                    }
                    ViewGroup.LayoutParams ulp = tv.getLayoutParams();
                    if (ulp != null && ulp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                        ulp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        if (ulp instanceof ViewGroup.MarginLayoutParams) {
                            ((ViewGroup.MarginLayoutParams) ulp).topMargin = 0;
                            ((ViewGroup.MarginLayoutParams) ulp).bottomMargin = 0;
                        }
                        tv.setLayoutParams(ulp);
                    }
                    float wantSizePx = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_SP, 15.5f, activity.getResources().getDisplayMetrics());
                    if (Math.abs(tv.getTextSize() - wantSizePx) > 0.5f) {
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, wantSizePx);
                    }
                }
            }
        } catch (Throwable t) {
            if (!sStyleErrLogged) { sStyleErrLogged = true; Log.w(TAG, "styleTopAddressBarGlass failed", t); }
        }
    }
    private static boolean sStyleErrLogged = false;

    /**
     * Round 11: Edge-to-edge frosted Liquid Glass backdrop for the whole top header (ID_CONTROL_CONTAINER),
     * spanning y = 0 (behind the transparent status bar) down to the bottom of the toolbar. Paired with
     * SurfaceViewBlurController's full-bleed hardware blur slot, so scrolling web content frosts seamlessly
     * behind BOTH the status bar icons and the search pill (settings-search-example.jpg).
     * Also draws the pill's soft ambient drop shadow strictly OUTSIDE the capsule (clipOutPath), so the
     * translucent glass itself is never darkened. All shaders/paths are cached; zero per-frame allocs.
     */
    static final class CouiTopHeaderGlassDrawable extends Drawable {
        private final Context ctx;
        private boolean isDark;
        private boolean realBlur;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        // HWUI ignores BlurMaskFilter on this path, so the ambient shadow is rasterized ONCE per pill size
        // into an ALPHA_8 bitmap with a software Canvas (where BlurMaskFilter always works) and blitted.
        private static android.graphics.Bitmap sShadowBmp;
        private static int sShadowW = -1, sShadowH = -1;
        private static int sShadowPad = 0;
        private final Rect pill = new Rect();
        private final RectF shadowRect = new RectF();
        private final Path pillClip = new Path();
        private boolean pillVisible = false;
        private int cachedW = -1, cachedH = -1;
        private boolean cachedDark = false;

        CouiTopHeaderGlassDrawable(Context ctx, boolean isDark, boolean realBlur) {
            this.ctx = ctx;
            this.isDark = isDark;
            this.realBlur = realBlur;

        }

        void updateState(boolean dark, boolean blur) {
            if (this.isDark != dark || this.realBlur != blur) {
                this.isDark = dark;
                this.realBlur = blur;
                invalidateSelf();
            }
        }

        private float pillAlpha = 1f;

        void setPillAlpha(float a) {
            a = Math.max(0f, Math.min(1f, a));
            if (Math.abs(a - pillAlpha) > 0.01f) {
                pillAlpha = a;
                invalidateSelf();
            }
        }

        void setPillRect(int l, int t, int r, int b, boolean visible) {
            if (pill.left == l && pill.top == t && pill.right == r && pill.bottom == b && pillVisible == visible) return;
            pill.set(l, t, r, b);
            pillVisible = visible;
            pillClip.rewind();
            float rad = (b - t) * 0.5f;
            pillClip.addRoundRect(l, t, r, b, rad, rad, Path.Direction.CW);
            invalidateSelf();
        }

        /** Rasterizes (once per pill size) a soft 10dp ambient capsule shadow mask: inset 5dp, ALPHA_8. */
        private static android.graphics.Bitmap shadowBitmap(Context ctx, int pw, int ph) {
            if (sShadowBmp != null && sShadowW == pw && sShadowH == ph) return sShadowBmp;
            try {
                float blur = dpF(ctx, 13f);
                int pad = (int) Math.ceil(blur * 1.6f);
                android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                        pw + pad * 2, ph + pad * 2, android.graphics.Bitmap.Config.ALPHA_8);
                Canvas sc = new Canvas(bmp);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(0xFF000000);
                p.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
                float inset = dpF(ctx, 5f);
                float r = ph * 0.5f - dpF(ctx, 2f);
                sc.drawRoundRect(pad + inset, pad + dpF(ctx, 2f), pad + pw - inset, pad + ph - dpF(ctx, 2f), r, r, p);
                sShadowBmp = bmp;
                sShadowW = pw;
                sShadowH = ph;
                sShadowPad = pad;
                return bmp;
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int w = b.width();
            int h = b.height();
            if (w <= 0 || h <= 0) return;

            // Round 12: On NTP (pillAlpha == 0), paint seamless COUI Settings canvas (#F0F1F3 / #000000)
            // with zero contrasting top band or hairline so the entire NTP canvas matches Settings 1:1.
            if (pillAlpha <= 0.01f) {
                fillPaint.setColor(isDark ? 0xFF000000 : 0xFFF0F1F3);
                canvas.drawRect(b, fillPaint);
                return;
            }

            // Luminous frost over the 16dp hardware blur (same tint across status bar + header: no seam).
            if (realBlur) {
                fillPaint.setColor(isDark ? 0xA6101012 : 0xB8ECEDF0);
            } else {
                fillPaint.setColor(isDark ? 0xF2141416 : 0xF5F5F7FA);
            }
            canvas.drawRect(b, fillPaint);

            if (w != cachedW || h != cachedH || isDark != cachedDark) {
                cachedW = w;
                cachedH = h;
                cachedDark = isDark;
                sheenPaint.setShader(new LinearGradient(
                        0f, b.top, 0f, b.bottom,
                        isDark
                                ? new int[]{0x26121214, 0x0A121214, 0x00000000}
                                : new int[]{0x33FFFFFF, 0x00FFFFFF, 0x0E1A2030},
                        new float[]{0f, 0.42f, 1f},
                        Shader.TileMode.CLAMP
                ));
            }
            canvas.drawRect(b, sheenPaint);

            // Soft diffuse ambient drop shadow under the floating search capsule (outside the glass only).
            if (pillVisible && pillAlpha > 0.01f && !pill.isEmpty()) {
                android.graphics.Bitmap bmp = shadowBitmap(ctx, pill.width(), pill.height());
                if (bmp != null) {
                    int save = canvas.save();
                    canvas.clipOutPath(pillClip);
                    shadowPaint.setColor(isDark ? 0xB3000000 : 0xFF1E2836);
                    shadowPaint.setAlpha(Math.round((isDark ? 0x80 : 0x18) * pillAlpha));
                    canvas.drawBitmap(bmp, pill.left - sShadowPad, pill.top - sShadowPad + dpF(ctx, 3.5f), shadowPaint);
                    canvas.restoreToCount(save);
                }
            }

            // Delicate bottom frosted glass divider hairline (0.5dp)
            float stroke = Math.max(1f, dpF(ctx, 0.5f));
            linePaint.setColor(isDark ? 0x14FFFFFF : 0x0C000000);
            canvas.drawRect(b.left, b.bottom - stroke, b.right, b.bottom, linePaint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Round 11: 1:1 ColorOS 17 Settings search capsule (settings-search-example.jpg): luminous translucent
     * white glass (dark glass in dark mode), crisp bright top + bottom specular rims (Kyant0/AndroidLiquidGlass
     * HighlightStyle.Default), whisper outer contour. Cached shaders (zero per-frame allocs).
     */
    static final class CouiAddressBarGlassDrawable extends Drawable {
        private final Context ctx;
        private boolean isDark;
        private boolean realBlur;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private LinearGradient cachedSheen;
        private LinearGradient cachedRim;
        private int cachedW = -1, cachedH = -1;
        private boolean cachedDark = false;

        private float contentAlpha = 1f;

        CouiAddressBarGlassDrawable(Context ctx, boolean isDark, boolean realBlur) {
            this.ctx = ctx;
            this.isDark = isDark;
            this.realBlur = realBlur;
            rimPaint.setStyle(Paint.Style.STROKE);
        }

        void setContentAlpha(float a) {
            a = Math.max(0f, Math.min(1f, a));
            if (Math.abs(a - contentAlpha) > 0.01f) {
                contentAlpha = a;
                invalidateSelf();
            }
        }

        private static int scaleAlpha(int color, float a) {
            return (color & 0x00FFFFFF) | (Math.round((color >>> 24) * a) << 24);
        }

        void updateState(boolean dark, boolean blur) {
            if (this.isDark != dark || this.realBlur != blur) {
                this.isDark = dark;
                this.realBlur = blur;
                invalidateSelf();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            int w = b.width();
            int h = b.height();
            if (w <= 0 || h <= 0) return;

            if (contentAlpha <= 0.01f) return;
            rf.set(b.left, b.top, b.right, b.bottom);
            float r = rf.height() * 0.5f;
            final float ca = contentAlpha;

            if (realBlur) {
                fillPaint.setColor(scaleAlpha(isDark ? 0x8C2C2C31 : 0xE0FFFFFF, ca));
            } else {
                fillPaint.setColor(scaleAlpha(isDark ? 0xF0262629 : 0xFFF9F9FA, ca));
            }
            canvas.drawRoundRect(rf, r, r, fillPaint);

            if (w != cachedW || h != cachedH || isDark != cachedDark) {
                cachedW = w;
                cachedH = h;
                cachedDark = isDark;
                // Gentle top-lit vertical sheen (brighter top half, neutral bottom)
                cachedSheen = new LinearGradient(
                        0f, rf.top, 0f, rf.bottom,
                        isDark
                                ? new int[]{0x14FFFFFF, 0x04FFFFFF, 0x08FFFFFF}
                                : new int[]{0x26FFFFFF, 0x00FFFFFF, 0x14FFFFFF},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                // Bright specular rim at top AND bottom edges, fading out at mid-height
                cachedRim = new LinearGradient(
                        0f, rf.top, 0f, rf.bottom,
                        isDark
                                ? new int[]{0x4DFFFFFF, 0x0DFFFFFF, 0x0DFFFFFF, 0x2EFFFFFF}
                                : new int[]{0xFFFFFFFF, 0x66FFFFFF, 0x66FFFFFF, 0xF2FFFFFF},
                        new float[]{0f, 0.32f, 0.68f, 1f},
                        Shader.TileMode.CLAMP
                );
            }
            sheenPaint.setShader(cachedSheen);
            sheenPaint.setAlpha(Math.round(255 * ca));
            canvas.drawRoundRect(rf, r, r, sheenPaint);

            // Whisper outer contour so the capsule reads on very light pages
            float hair = dpF(ctx, 0.5f);
            rf.inset(hair * 0.5f, hair * 0.5f);
            rimPaint.setShader(null);
            rimPaint.setStrokeWidth(hair);
            rimPaint.setColor(scaleAlpha(isDark ? 0x1FFFFFFF : 0x07000000, ca));
            canvas.drawRoundRect(rf, r - hair * 0.5f, r - hair * 0.5f, rimPaint);

            // Inner specular rim (~1px at 520dpi)
            float rimW = dpF(ctx, 0.85f);
            rf.inset(hair * 0.5f + rimW * 0.5f, hair * 0.5f + rimW * 0.5f);
            rimPaint.setStrokeWidth(rimW);
            rimPaint.setShader(cachedRim);
            rimPaint.setAlpha(Math.round(255 * ca));
            float ri = Math.max(0f, r - hair - rimW * 0.5f);
            canvas.drawRoundRect(rf, ri, ri, rimPaint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Official Microsoft Fluent UI 24dp search magnifying glass (fluent:search-24-regular)
     * rendered cleanly centered at 19.5dp optical size inside the 40dp address pill.
     */
    static final class CouiSearchGlassIconDrawable extends Drawable {
        private boolean isDark;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int sizePx;
        private final float glyphPx;

        CouiSearchGlassIconDrawable(Context ctx, boolean isDark) {
            this.isDark = isDark;
            this.sizePx = dp(ctx, 20f);
            this.glyphPx = dpF(ctx, 19.5f);
            paint.setStyle(Paint.Style.FILL);
        }

        void setDarkMode(boolean dark) {
            if (this.isDark != dark) {
                this.isDark = dark;
                invalidateSelf();
            }
        }

        @Override public int getIntrinsicWidth() { return sizePx; }
        @Override public int getIntrinsicHeight() { return sizePx; }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            paint.setColor(isDark ? 0xB3FFFFFF : 0xFF686D74);
            FluentIcons.drawCentered(canvas, FluentIcons.SEARCH_REGULAR, b.exactCenterX(), b.exactCenterY(), glyphPx, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // =========================================================================
    // TASK 3: ColorOS 17 Settings UI Engine (SettingsActivity + Ldc3 + Lec3)
    // =========================================================================

    /** Hooked into Ldc3.<init>(Context) — configures ColorOS 17 20dp squircle card geometry. */
    public static void onContainmentControllerInit(Object dc3, Context ctx) {
        if (dc3 == null || ctx == null) return;
        try {
            boolean dark = isDarkMode(ctx);
            Class<?> cls = dc3.getClass();
            setFloatField(cls, dc3, "a", dpF(ctx, 20f)); // 20dp outer continuous squircle radius
            setFloatField(cls, dc3, "b", 0f);            // 0dp inner radius so adjacent rows fuse seamlessly
            setIntField(cls, dc3, "c", 0);               // 0px vertical gap between adjacent rows in same card
            int marginH = dp(ctx, 16);
            setIntField(cls, dc3, "d", marginH);         // 16dp horizontal card margin
            setIntField(cls, dc3, "i", marginH);         // 16dp current horizontal margin
            setIntField(cls, dc3, "e", dp(ctx, 12));     // 12dp bottom spacing between cards
            setIntField(cls, dc3, "f", dp(ctx, 15));     // 15dp single-line vertical padding
            setIntField(cls, dc3, "g", dp(ctx, 13));     // 13dp two-line vertical padding
            setIntField(cls, dc3, "h", dark ? 0xFF1C1C1E : 0xFFFFFFFF); // Pure #FFFFFF / #1C1C1E card surface
        } catch (Throwable t) {
            Log.w(TAG, "onContainmentControllerInit failed", t);
        }
    }

    private static void setFloatField(Class<?> cls, Object obj, String name, float val) throws Exception {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        f.setFloat(obj, val);
    }

    private static void setIntField(Class<?> cls, Object obj, String name, int val) throws Exception {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        f.setInt(obj, val);
    }

    /** Hooked into SettingsActivity.onResume(). */
    public static void attachToSettings(final Activity activity) {
        if (activity == null) return;
        try {
            final boolean dark = isDarkMode(activity);
            final int canvasColor = dark ? 0xFF000000 : 0xFFF0F1F3;
            styleSettingsWindow(activity, dark, canvasColor);
            styleSettingsActivityChrome(activity, dark, canvasColor);
            final ViewGroup content = activity.findViewById(android.R.id.content);
            if (content != null) {
                content.setBackgroundColor(canvasColor);
                content.post(new Runnable() {
                    @Override
                    public void run() {
                        styleSettingsWindow(activity, dark, canvasColor);
                        styleSettingsActivityChrome(activity, dark, canvasColor);
                        styleSettingsTree(activity, content, dark, canvasColor);
                    }
                });
                content.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        styleSettingsWindow(activity, dark, canvasColor);
                        styleSettingsActivityChrome(activity, dark, canvasColor);
                        styleSettingsTree(activity, content, dark, canvasColor);
                    }
                }, 140);
                installSettingsFragmentObserver(activity);
            }
        } catch (Throwable t) {
            Log.w(TAG, "attachToSettings failed", t);
        }
    }

    /**
     * Round 12: Lightweight throttled observer on SettingsActivity's FragmentContainerView so
     * sub-screens that use ListView (such as SearchEngineSettings) or dynamically resize AppBarLayout
     * automatically receive COUI top inset clearance, overscroll, radio styling, dividers, and chevrons
     * without requiring onResume().
     */
    private static void installSettingsFragmentObserver(final Activity activity) {
        final View fragContent = activity.findViewById(ID_SETTINGS_CONTENT);
        if (fragContent == null || SETTINGS_OBSERVER_TAG.equals(fragContent.getTag())) return;
        fragContent.setTag(SETTINGS_OBSERVER_TAG);
        final long[] lastSync = {0L};
        fragContent.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                long now = SystemClock.uptimeMillis();
                if (now - lastSync[0] < 90L) return true;
                lastSync[0] = now;
                try {
                    boolean dark = isDarkMode(activity);
                    int canvasColor = dark ? 0xFF000000 : 0xFFF0F1F3;
                    styleSettingsTree(activity, fragContent, dark, canvasColor);
                    View root = activity.findViewById(ID_SETTINGS_ACTIVITY);
                    if (root != null) {
                        View blur = root.findViewWithTag(SETTINGS_TOP_BLUR_TAG);
                        if (blur instanceof CouiTopProgressiveBlurView) {
                            ((CouiTopProgressiveBlurView) blur).syncHeight();
                        }
                    }
                    // Keep Settings toolbar back button & title styling synced across fragment transitions
                    View tb = activity.findViewById(ID_SETTINGS_TOOLBAR);
                    if (tb instanceof ViewGroup) {
                        ViewGroup tbg = (ViewGroup) tb;
                        for (int i = 0; i < tbg.getChildCount(); i++) {
                            View c = tbg.getChildAt(i);
                            if (c instanceof ImageButton && c.getVisibility() != View.INVISIBLE) {
                                c.setVisibility(View.INVISIBLE);
                                c.setAlpha(0f);
                                c.setClickable(false);
                            } else if (c instanceof TextView && !(c instanceof EditText)) {
                                ((TextView) c).setTextColor(dark ? 0xE6FFFFFF : 0xFF191919);
                            } else if (c.getClass().getName().contains("ActionMenuView") && c.getVisibility() != View.GONE) {
                                c.setVisibility(View.GONE);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
                return true;
            }
        });
    }

    private static void styleSettingsWindow(Activity activity, boolean dark, int canvasColor) {
        Window w = activity.getWindow();
        if (w == null) return;
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setBackgroundDrawable(new ColorDrawable(canvasColor));
        View decor = w.getDecorView();
        decor.setBackgroundColor(canvasColor);
        w.setStatusBarColor(canvasColor);
        w.setNavigationBarColor(canvasColor);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController ctrl = w.getInsetsController();
            if (ctrl != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ctrl.setSystemBarsAppearance(dark ? 0 : mask, mask);
            }
        }
        // Neutralize EdgeToEdgeBaseLayout (0x7f010461) internal Paint colors that paint #E6E8F0 across y=0..statusBarH!
        styleEdgeToEdgeBaseLayout(activity, canvasColor);
        // Neutralize any Android 15+ DecorView statusBarBackground scrim view
        neutralizeStatusBarScrimViews(activity, decor, canvasColor);
    }

    private static void styleEdgeToEdgeBaseLayout(Activity activity, int canvasColor) {
        if (activity == null) return;
        View e2e = activity.findViewById(ID_EDGE_TO_EDGE_BASE);
        if (e2e == null) return;
        e2e.setBackgroundColor(canvasColor);
        try {
            Class<?> cls = e2e.getClass();
            while (cls != null && cls != ViewGroup.class && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(e2e);
                    if (val instanceof Paint) {
                        Paint p = (Paint) val;
                        if (p.getColor() != canvasColor) {
                            p.setColor(canvasColor);
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
            // Also try public setter setStatusBarColor / setNavBarColor if present
            for (Method m : e2e.getClass().getMethods()) {
                String mn = m.getName();
                if (("setStatusBarColor".equals(mn) || "setNavBarColor".equals(mn)
                        || "setStatusBarDividerColor".equals(mn) || "setNavBarDividerColor".equals(mn))
                        && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == int.class) {
                    m.invoke(e2e, canvasColor);
                }
            }
            e2e.invalidate();
        } catch (Throwable t) {
            Log.w(TAG, "styleEdgeToEdgeBaseLayout failed", t);
        }
    }

    private static Activity unwrapActivity(Context ctx) {
        Context c = ctx;
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static void neutralizeStatusBarScrimViews(Activity activity, View root, int canvasColor) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) root;
        int maxSbH = dp(activity, 64);
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child == null) continue;
            int id = child.getId();
            if (id == android.R.id.statusBarBackground) {
                child.setBackgroundColor(canvasColor);
                continue;
            }
            if (id == android.R.id.navigationBarBackground) {
                child.setBackgroundColor(canvasColor);
                continue;
            }
            // Catch any thin edge-to-edge status bar overlay strip sitting at y=0..statusBarHeight
            if (id != android.R.id.content
                    && id != ID_SETTINGS_APP_BAR
                    && id != ID_SETTINGS_CONTENT
                    && id != ID_SETTINGS_TOOLBAR
                    && !SETTINGS_TOP_BLUR_TAG.equals(child.getTag())
                    && !SETTINGS_BACK_TAG.equals(child.getTag())
                    && child.getTop() == 0
                    && child.getHeight() > 0
                    && child.getHeight() <= maxSbH
                    && !(child instanceof TextView)
                    && !(child instanceof ImageView)) {
                Drawable bg = child.getBackground();
                boolean alreadySet = (bg instanceof ColorDrawable && ((ColorDrawable) bg).getColor() == canvasColor);
                if (!alreadySet) {
                    Log.i(TAG, "Neutralized statusBar scrim view: " + child.getClass().getName()
                            + " id=0x" + Integer.toHexString(id) + " h=" + child.getHeight()
                            + " bg=" + (bg != null ? bg.getClass().getSimpleName() : "null"));
                    child.setBackgroundColor(canvasColor);
                }
            }
            // Also inspect direct children of android.R.id.content and CoordinatorLayout
            if (id == android.R.id.content || id == ID_SETTINGS_ACTIVITY
                    || child.getClass().getName().contains("FitWindowsLinearLayout")
                    || child.getClass().getName().contains("ActionBarOverlayLayout")) {
                neutralizeStatusBarScrimViews(activity, child, canvasColor);
            }
        }
    }

    private static int getSettingsAppBarBottom(Activity activity) {
        if (activity == null) return 0;
        View appBar = activity.findViewById(ID_SETTINGS_APP_BAR);
        if (appBar != null && appBar.getBottom() > 0) {
            return appBar.getBottom();
        }
        // AppBarLayout sits inside CoordinatorLayout (below status bar) and contains Toolbar (56dp) + SearchBox (~68dp)
        return dp(activity, 128);
    }

    private static void styleSettingsActivityChrome(final Activity activity, final boolean dark, final int canvasColor) {
        final View rootView = activity.findViewById(ID_SETTINGS_ACTIVITY);
        if (rootView != null) {
            rootView.setBackgroundColor(canvasColor);
            // Strip R8-obfuscated CoordinatorLayout.G (mStatusBarBackground) and public method if present
            try {
                Field fSb = rootView.getClass().getDeclaredField("G");
                fSb.setAccessible(true);
                fSb.set(rootView, null);
                rootView.invalidate();
            } catch (Throwable ignored) {}
            try {
                Method setSbBg = rootView.getClass().getMethod("setStatusBarBackground", Drawable.class);
                setSbBg.invoke(rootView, (Drawable) null);
            } catch (Throwable ignored) {}
        }

        final View fragContent = activity.findViewById(ID_SETTINGS_CONTENT);
        if (fragContent != null) {
            fragContent.setBackgroundColor(canvasColor);
            // Replace CoordinatorLayout.LayoutParams with a fresh (MATCH_PARENT, MATCH_PARENT) instance
            // to cleanly strip R8-obfuscated AppBarLayout$ScrollingViewBehavior so content extends to y=0!
            ViewGroup.LayoutParams flp = fragContent.getLayoutParams();
            if (flp != null) {
                boolean hasBehavior = false;
                for (Field f : flp.getClass().getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(flp);
                        if (val != null && val.getClass().getName().contains("Behavior")) {
                            hasBehavior = true;
                            f.set(flp, null);
                        }
                    } catch (Throwable ignored) {}
                }
                if (hasBehavior) {
                    try {
                        ViewGroup.LayoutParams cleanLp = (ViewGroup.LayoutParams) flp.getClass()
                                .getConstructor(int.class, int.class)
                                .newInstance(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                        fragContent.setLayoutParams(cleanLp);
                        Log.i(TAG, "Cleared FragmentContainerView ScrollingViewBehavior for scroll-under blur");
                    } catch (Throwable t) {
                        fragContent.requestLayout();
                    }
                }
            }
        }

        final View appBar = activity.findViewById(ID_SETTINGS_APP_BAR);
        if (appBar != null) {
            appBar.setBackground(null);
            appBar.setElevation(0f);
            appBar.setOutlineProvider(null);
            appBar.setWillNotDraw(true);
            // Strip R8-obfuscated AppBarLayout.M (statusBarForeground), B (liftOnScrollColor), z (liftOnScroll)
            try {
                Field fM = appBar.getClass().getDeclaredField("M");
                fM.setAccessible(true);
                if (fM.get(appBar) != null) {
                    fM.set(appBar, null);
                    Log.i(TAG, "Cleared AppBarLayout.M statusBarForeground");
                }
            } catch (Throwable ignored) {}
            try {
                Field fN = appBar.getClass().getDeclaredField("N");
                fN.setAccessible(true);
                fN.set(appBar, null);
            } catch (Throwable ignored) {}
            try {
                Field fB = appBar.getClass().getDeclaredField("B");
                fB.setAccessible(true);
                fB.set(appBar, null);
            } catch (Throwable ignored) {}
            try {
                Field fZ = appBar.getClass().getDeclaredField("z");
                fZ.setAccessible(true);
                fZ.setBoolean(appBar, false);
            } catch (Throwable ignored) {}
            try {
                Method setLift = appBar.getClass().getMethod("setLiftOnScroll", boolean.class);
                setLift.invoke(appBar, false);
            } catch (Throwable ignored) {}
            try {
                Method setSbFg = appBar.getClass().getMethod("setStatusBarForeground", Drawable.class);
                setSbFg.invoke(appBar, (Drawable) null);
            } catch (Throwable ignored) {}
            appBar.invalidate();
        }

        // Install / update scroll-under progressive frosted top-bar blur ABOVE FragmentContainerView and BELOW AppBarLayout
        if (rootView instanceof ViewGroup && appBar != null && fragContent != null) {
            ViewGroup coord = (ViewGroup) rootView;
            View existingBlur = coord.findViewWithTag(SETTINGS_TOP_BLUR_TAG);
            final CouiTopProgressiveBlurView blurView;
            if (existingBlur instanceof CouiTopProgressiveBlurView) {
                blurView = (CouiTopProgressiveBlurView) existingBlur;
                blurView.setDarkMode(dark);
            } else {
                blurView = new CouiTopProgressiveBlurView(activity, fragContent, appBar, dark);
                blurView.setTag(SETTINGS_TOP_BLUR_TAG);
                int blurH = getSettingsAppBarBottom(activity) + dp(activity, 28);
                ViewGroup.LayoutParams blp = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, blurH);
                // Add AFTER FragmentContainerView so blur renders ON TOP of scrolling cards, then bring AppBarLayout on top!
                coord.addView(blurView, blp);
            }
            blurView.bringToFront();
            appBar.bringToFront();
            blurView.syncHeight();
        }

        View toolbarView = activity.findViewById(ID_SETTINGS_TOOLBAR);
        if (toolbarView instanceof ViewGroup) {
            final ViewGroup toolbar = (ViewGroup) toolbarView;
            toolbar.setBackground(null);
            toolbar.setElevation(0f);

            // Reserve exact horizontal space for our 40dp floating glass back button (16dp margin + 40dp btn + 12dp gap = 68dp)
            try {
                Method setInset = toolbar.getClass().getMethod("setContentInsetStartWithNavigation", int.class);
                setInset.invoke(toolbar, dp(activity, 68));
            } catch (Throwable ignored) {}
            try {
                Method setRel = toolbar.getClass().getMethod("setContentInsetsRelative", int.class, int.class);
                setRel.invoke(toolbar, dp(activity, 68), dp(activity, 16));
            } catch (Throwable ignored) {}
            try {
                Method setTitleStart = toolbar.getClass().getMethod("setTitleMarginStart", int.class);
                setTitleStart.invoke(toolbar, 0);
            } catch (Throwable ignored) {}

            // Hide built-in MaterialToolbar nav ImageButton & overflow ActionMenuView; style title TextView
            View stockNavBtn = null;
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                View child = toolbar.getChildAt(i);
                String cName = child.getClass().getName();
                if (child instanceof TextView && !(child instanceof EditText)) {
                    TextView tv = (TextView) child;
                    tv.setTextColor(dark ? 0xE6FFFFFF : 0xFF191919);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
                    tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                } else if (cName.contains("ActionMenuView")) {
                    child.setVisibility(View.GONE);
                } else if (child instanceof ImageButton) {
                    stockNavBtn = child;
                    child.setVisibility(View.INVISIBLE);
                    child.setAlpha(0f);
                    child.setClickable(false);
                }
            }

            // Install / update our authentic 40dp ColorOS 17 Liquid Glass circular back button in Toolbar's parent FrameLayout
            ViewParent tp = toolbar.getParent();
            if (tp instanceof FrameLayout) {
                FrameLayout holder = (FrameLayout) tp;
                holder.setClipChildren(false);
                holder.setClipToPadding(false);
                View existingBtn = holder.findViewWithTag(SETTINGS_BACK_TAG);
                CouiSettingsBackButtonView backBtn;
                if (existingBtn instanceof CouiSettingsBackButtonView) {
                    backBtn = (CouiSettingsBackButtonView) existingBtn;
                    backBtn.setDarkMode(dark);
                    backBtn.setStockNavButton(stockNavBtn);
                } else {
                    backBtn = new CouiSettingsBackButtonView(activity, dark, stockNavBtn);
                    backBtn.setTag(SETTINGS_BACK_TAG);
                    int size = dp(activity, 40);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            size, size, Gravity.START | Gravity.CENTER_VERTICAL);
                    lp.leftMargin = dp(activity, 16);
                    holder.addView(backBtn, lp);
                }
                // Hide our circular back button only when the full-width active search query bar is open
                View searchQuery = activity.findViewById(ID_SEARCH_QUERY_CONTAINER);
                boolean inSearch = (searchQuery != null && searchQuery.getVisibility() == View.VISIBLE);
                backBtn.setVisibility(inSearch ? View.GONE : View.VISIBLE);
                backBtn.bringToFront();
            }
        }

        // Style "Search settings" pill box (ID_SEARCH_BOX & ID_SEARCH_QUERY_CONTAINER)
        styleCouiSearchBox(activity.findViewById(ID_SEARCH_BOX), dark);
        styleCouiSearchBox(activity.findViewById(ID_SEARCH_QUERY_CONTAINER), dark);
    }

    private static void styleCouiSearchBox(View box, boolean dark) {
        if (box == null) return;
        Context ctx = box.getContext();
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(dpF(ctx, 24f));
        pill.setColor(dark ? 0xFF1C1C1E : 0xFFE3E4E7);
        box.setBackground(pill);
        box.setMinimumHeight(dp(ctx, 44));

        if (box instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) box;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View c = vg.getChildAt(i);
                if (c instanceof ImageView) {
                    ((ImageView) c).setColorFilter(dark ? 0x8AFFFFFF : 0xFF7F7F7F, PorterDuff.Mode.SRC_IN);
                } else if (c instanceof TextView) {
                    TextView tv = (TextView) c;
                    tv.setTextColor(dark ? 0x8AFFFFFF : 0xFF7F7F7F);
                    tv.setHintTextColor(dark ? 0x8AFFFFFF : 0xFF7F7F7F);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
                    tv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                }
            }
        }
    }

    private static void applySettingsRecyclerPadding(View rv, Activity activity) {
        Context ctx = rv.getContext();
        Activity act = (activity != null) ? activity : unwrapActivity(ctx);
        int topPad = (act != null ? getSettingsAppBarBottom(act) : dp(ctx, 132)) + dp(ctx, 8);
        int botPad = dp(ctx, 28);
        int oldTop = rv.getPaddingTop();
        if (rv.getPaddingLeft() != 0 || rv.getPaddingRight() != 0
                || Math.abs(oldTop - topPad) > dp(ctx, 2)
                || rv.getPaddingBottom() < botPad) {
            rv.setPadding(0, topPad, 0, botPad);
            // If paddingTop grew from initial 0 after LinearLayoutManager's first pass, scroll back to top
            // so the first section ("Basics" / "Search engine") sits cleanly below the Search bar!
            if (oldTop < dp(ctx, 60) && topPad > oldTop) {
                try {
                    rv.scrollBy(0, -(topPad - oldTop));
                } catch (Throwable ignored) {}
            }
        }
        if (rv instanceof ViewGroup) {
            ((ViewGroup) rv).setClipToPadding(false);
        }
    }

    private static void styleSettingsTree(Activity activity, View view, boolean dark, int canvasColor) {
        if (view == null) return;
        String cls = view.getClass().getName();
        if (cls.contains("RecyclerView")) {
            view.setBackgroundColor(canvasColor);
            applySettingsRecyclerPadding(view, activity);
            if (view instanceof ViewGroup) {
                ViewGroup rv = (ViewGroup) view;
                rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
                attachCouiOverscroll(rv);
                for (int i = 0; i < rv.getChildCount(); i++) {
                    stylePreferenceItemView(rv.getChildAt(i), dark, null);
                }
            }
        } else if (view instanceof ListView) {
            styleSettingsListView(activity, (ListView) view, dark, canvasColor);
            return;
        } else if (view instanceof ScrollView || cls.contains("NestedScrollView")) {
            view.setBackgroundColor(canvasColor);
            applySettingsRecyclerPadding(view, activity);
            view.setOverScrollMode(View.OVER_SCROLL_NEVER);
            if (view instanceof ViewGroup) {
                attachCouiOverscroll((ViewGroup) view);
            }
        }
        styleCouiControlsRecursive(view, dark);
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                styleSettingsTree(activity, vg.getChildAt(i), dark, canvasColor);
            }
        }
    }

    /** Styles RadioButtonWithDescriptionLayout, CheckBoxes, SeekBars, and Buttons anywhere inside Settings. */
    private static void styleCouiControlsRecursive(View v, boolean dark) {
        if (v == null) return;
        Context ctx = v.getContext();
        String cls = v.getClass().getName();

        if (cls.contains("RadioButtonWithDescriptionLayout") && v instanceof ViewGroup) {
            styleCouiRadioGroupCard((ViewGroup) v, dark);
            return;
        }
        if (v instanceof RadioButton) {
            styleCouiRadioButton((RadioButton) v, dark);
        } else if (v instanceof CheckBox) {
            styleCouiCheckBox((CheckBox) v, dark);
        } else if (v instanceof SeekBar) {
            styleCouiSeekBar((SeekBar) v, dark);
        } else if (v instanceof Button && !(v instanceof CompoundButton)) {
            styleCouiButton((Button) v, dark);
        }
    }

    /**
     * Full ColorOS 17 refactor for RadioButtonWithDescriptionLayout cards across ALL sub-settings
     * (Address bar Top/Bottom, Appearance/Theme, Homepage, Cookies, WebRTC, Site settings, Do Not Track):
     * - Eliminates excessive horizontal margins that shrink cards toward center (address-bar-settings.jpg).
     * - Replaces M3 double-ring radio buttons with authentic borderless ColorOS 17 #0080FF/#1A8CFF radios.
     * - Applies uniform 16dp horizontal / 14dp vertical padding, 16sp Medium titles, 13sp muted descriptions,
     *   and thin 0.5dp-0.75dp 16dp-inset hairline dividers between options inside the same card.
     */
    private static void styleCouiRadioGroupCard(ViewGroup group, boolean dark) {
        Context ctx = group.getContext();
        // Zero any extra margins/padding on RadioButtonWithDescriptionLayout itself so its parent card
        // spans the full screen width with exact 16dp left/right gutters!
        ViewGroup.LayoutParams glp = group.getLayoutParams();
        if (glp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams gmlp = (ViewGroup.MarginLayoutParams) glp;
            if (gmlp.leftMargin != 0 || gmlp.rightMargin != 0 || gmlp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                gmlp.leftMargin = 0;
                gmlp.rightMargin = 0;
                gmlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                group.setLayoutParams(gmlp);
            }
        }
        if (group.getPaddingLeft() != 0 || group.getPaddingRight() != 0) {
            group.setPadding(0, group.getPaddingTop(), 0, group.getPaddingBottom());
        }

        // Also enforce exact 16dp horizontal margin on the parent card item inside RecyclerView
        ViewParent p = group.getParent();
        if (p instanceof View) {
            View cardRoot = (View) p;
            int wantMargin = dp(ctx, 16);
            ViewGroup.LayoutParams clp = cardRoot.getLayoutParams();
            if (clp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams cmlp = (ViewGroup.MarginLayoutParams) clp;
                if (cmlp.leftMargin != wantMargin || cmlp.rightMargin != wantMargin
                        || cmlp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                    cmlp.leftMargin = wantMargin;
                    cmlp.rightMargin = wantMargin;
                    cmlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    cardRoot.setLayoutParams(cmlp);
                }
            }
            if (cardRoot.getPaddingLeft() != 0 || cardRoot.getPaddingRight() != 0) {
                cardRoot.setPadding(0, cardRoot.getPaddingTop(), 0, cardRoot.getPaddingBottom());
            }
        }

        // Count visible option rows so we draw hairline dividers strictly between adjacent rows
        List<View> visibleRows = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            View row = group.getChildAt(i);
            if (row != null && row.getVisibility() == View.VISIBLE) {
                visibleRows.add(row);
            }
        }

        int padH = dp(ctx, 16);
        int padV = dp(ctx, 14);
        int minH = dp(ctx, 54);
        int radioEndMargin = dp(ctx, 14);
        for (int idx = 0; idx < visibleRows.size(); idx++) {
            View row = visibleRows.get(idx);
            ViewGroup.LayoutParams rlp = row.getLayoutParams();
            if (rlp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams rmlp = (ViewGroup.MarginLayoutParams) rlp;
                if (rmlp.leftMargin != 0 || rmlp.rightMargin != 0 || rmlp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                    rmlp.leftMargin = 0;
                    rmlp.rightMargin = 0;
                    rmlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    row.setLayoutParams(rmlp);
                }
            }
            if (row.getPaddingLeft() != padH || row.getPaddingRight() != padH
                    || row.getPaddingTop() != padV || row.getPaddingBottom() != padV) {
                row.setPadding(padH, padV, padH, padV);
            }
            if (row.getMinimumHeight() < minH) {
                row.setMinimumHeight(minH);
            }

            RadioButton rb = row.findViewById(ID_RADIO_BUTTON);
            if (rb == null) rb = findRadioButtonRecursive(row);
            if (rb != null) {
                styleCouiRadioButton(rb, dark);
                ViewGroup.LayoutParams blp = rb.getLayoutParams();
                if (blp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams bmlp = (ViewGroup.MarginLayoutParams) blp;
                    if (bmlp.getMarginEnd() != radioEndMargin || bmlp.getMarginStart() != 0) {
                        bmlp.setMarginStart(0);
                        bmlp.setMarginEnd(radioEndMargin);
                        rb.setLayoutParams(bmlp);
                    }
                }
            }

            // Style primary title & description typography inside RadioButtonWithDescription
            View prim = row.findViewById(ID_RADIO_PRIMARY);
            if (prim == null) prim = row.findViewById(0x7f010d49);
            if (prim instanceof TextView) {
                TextView tv = (TextView) prim;
                tv.setTextColor(dark ? 0xE6FFFFFF : 0xFF191919);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
                tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
            View desc = row.findViewById(ID_RADIO_DESC);
            if (desc instanceof TextView) {
                TextView tv = (TextView) desc;
                tv.setTextColor(dark ? 0x73FFFFFF : 0xFF8C8C8C);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                tv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            }

            boolean drawDiv = (idx + 1 < visibleRows.size());
            Drawable fg = row.getForeground();
            if (fg instanceof CouiListRowOverlayDrawable) {
                ((CouiListRowOverlayDrawable) fg).update(dark, drawDiv, false);
            } else {
                row.setForeground(new CouiListRowOverlayDrawable(ctx, dark, drawDiv, false));
            }
        }
    }

    private static RadioButton findRadioButtonRecursive(View v) {
        if (v instanceof RadioButton) return (RadioButton) v;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                RadioButton r = findRadioButtonRecursive(vg.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private static void styleCouiCheckBox(CheckBox cb, boolean dark) {
        if (cb == null) return;
        String tag = "coui_cb_" + (dark ? "d" : "l");
        if (tag.equals(cb.getTag())) return;
        cb.setTag(tag);
        cb.setButtonTintList(null);
        cb.setButtonDrawable(new CouiCheckBoxDrawable(cb.getContext(), dark));
    }

    private static final class CouiCheckBoxDrawable extends Drawable {
        private final Context ctx;
        private final boolean isDark;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final int sizePx;
        private boolean checked = false;

        CouiCheckBoxDrawable(Context ctx, boolean isDark) {
            this.ctx = ctx;
            this.isDark = isDark;
            this.sizePx = dp(ctx, 22);
        }

        @Override public int getIntrinsicWidth() { return sizePx; }
        @Override public int getIntrinsicHeight() { return sizePx; }
        @Override public boolean isStateful() { return true; }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean now = false;
            if (state != null) {
                for (int s : state) {
                    if (s == android.R.attr.state_checked) { now = true; break; }
                }
            }
            if (now != checked) { checked = now; invalidateSelf(); return true; }
            return false;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            float half = dpF(ctx, 9.5f);
            float rad = dpF(ctx, 5.5f);
            rf.set(cx - half, cy - half, cx + half, cy + half);
            if (checked) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(isDark ? 0xFF1A8CFF : 0xFF0080FF);
                canvas.drawRoundRect(rf, rad, rad, p);
                p.setColor(0xFFFFFFFF);
                FluentIcons.drawCentered(canvas, FluentIcons.CHECKMARK, cx, cy, dpF(ctx, 14f), p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dpF(ctx, 1.6f));
                p.setColor(isDark ? 0xFF48484A : 0xFFC4C7CC);
                rf.inset(dpF(ctx, 0.8f), dpF(ctx, 0.8f));
                canvas.drawRoundRect(rf, rad, rad, p);
            }
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static void styleCouiSeekBar(SeekBar sb, boolean dark) {
        if (sb == null) return;
        String tag = "coui_sb_" + (dark ? "d" : "l");
        if (tag.equals(sb.getTag())) return;
        sb.setTag(tag);
        int accent = dark ? 0xFF1A8CFF : 0xFF0080FF;
        sb.setProgressTintList(ColorStateList.valueOf(accent));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(dark ? 0x26FFFFFF : 0x16000000));
        sb.setThumbTintList(ColorStateList.valueOf(accent));
    }

    private static void styleCouiButton(Button btn, boolean dark) {
        if (btn == null) return;
        String tag = "coui_btn_" + (dark ? "d" : "l");
        if (tag.equals(btn.getTag())) return;
        btn.setTag(tag);
        btn.setAllCaps(false);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int accent = dark ? 0xFF1A8CFF : 0xFF0080FF;
        ColorStateList bgTint = btn.getBackgroundTintList();
        if (bgTint != null) {
            int defCol = bgTint.getDefaultColor();
            // Convert M3 slate-blue filled buttons to vibrant ColorOS 17 capsule pill buttons
            if (Color.alpha(defCol) > 120 && colorLuminance(defCol) < 0.72f) {
                btn.setBackgroundTintList(ColorStateList.valueOf(accent));
                btn.setTextColor(0xFFFFFFFF);
            }
        } else {
            int tc = btn.getCurrentTextColor();
            if (Color.blue(tc) > Color.red(tc) + 15) {
                btn.setTextColor(accent);
            }
        }
    }

    /**
     * Round 12: Styles Settings sub-screens backed by ListView (notably SearchEngineSettings):
     * - Applies exact top clearance below AppBarLayout (+8dp) with clipToPadding=false so item 0
     *   ("Google") is 100% visible below "<- Search engine" and never clipped behind the header.
     * - Styles RadioButtons to authentic ColorOS 17 #0080FF/#1A8CFF circles + ROM haptic 302.
     * - Styles names/URLs to COUI 16sp Medium / 13sp muted typography, draws 16dp-inset 1dp
     *   card dividers between fused items, and draws a native COUI '>' chevron on action rows
     *   ("Manage search engines and site search").
     */
    private static void styleSettingsListView(Activity activity, ListView lv, boolean dark, int canvasColor) {
        Context ctx = lv.getContext();
        Activity act = (activity != null) ? activity : unwrapActivity(ctx);
        Drawable bg = lv.getBackground();
        if (!(bg instanceof ColorDrawable) || ((ColorDrawable) bg).getColor() != canvasColor) {
            lv.setBackgroundColor(canvasColor);
        }
        if (lv.getDivider() != null || lv.getDividerHeight() != 0) {
            lv.setDivider(null);
            lv.setDividerHeight(0);
        }
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        lv.setClipToPadding(false);
        attachCouiOverscroll(lv);

        int appBarBot = (act != null) ? getSettingsAppBarBottom(act) : dp(ctx, 96);
        int topPad = appBarBot + dp(ctx, 8);
        int botPad = dp(ctx, 28);
        int oldTop = lv.getPaddingTop();
        if (lv.getPaddingLeft() != 0 || lv.getPaddingRight() != 0
                || Math.abs(oldTop - topPad) > dp(ctx, 2)
                || lv.getPaddingBottom() < botPad) {
            lv.setPadding(0, topPad, 0, botPad);
            if (oldTop < dp(ctx, 40) && topPad > oldTop) {
                try {
                    lv.setSelectionFromTop(0, 0);
                } catch (Throwable ignored) {}
            }
        }

        int childCount = lv.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View row = lv.getChildAt(i);
            if (row == null || row.getVisibility() != View.VISIBLE) continue;

            View cardTarget = row.findViewById(ID_SE_CARD_CONTAINER);
            if (cardTarget == null) cardTarget = row;
            // Remove extra outer row horizontal padding so Ldc3's 16dp card margin aligns 1:1 with Settings main
            if (row != cardTarget && (row.getPaddingLeft() != 0 || row.getPaddingRight() != 0)) {
                row.setPadding(0, row.getPaddingTop(), 0, row.getPaddingBottom());
            }

            RadioButton rb = row.findViewById(ID_SE_RADIO_BUTTON);
            TextView nameTv = row.findViewById(ID_SE_NAME);
            TextView urlTv = row.findViewById(ID_SE_URL);

            if (rb != null) {
                styleCouiRadioButton(rb, dark);
            }
            if (nameTv != null) {
                nameTv.setTextColor(dark ? 0xE6FFFFFF : 0xFF191919);
                nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
                nameTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
            if (urlTv != null) {
                urlTv.setTextColor(dark ? 0x73FFFFFF : 0xFF8C8C8C);
                urlTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                urlTv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            }

            // Style action rows without RadioButton (e.g., "Manage search engines and site search"):
            // hide Chromium's stock Material ImageView chevron so only our single crisp COUI '>' renders!
            boolean isActionRow = (rb == null && row.isClickable());
            if (isActionRow) {
                styleActionRowTextRecursive(row, dark);
            }

            // Draw 16dp-inset 1dp COUI divider between ANY two adjacent fused rows inside the same card
            boolean hasDividerBelow = false;
            if (i + 1 < childCount) {
                View next = lv.getChildAt(i + 1);
                if (next != null && next.getVisibility() == View.VISIBLE) {
                    int gap = next.getTop() - row.getBottom();
                    if (gap <= dp(ctx, 3)) {
                        hasDividerBelow = true;
                    }
                }
            }

            Drawable curFg = cardTarget.getForeground();
            if (curFg instanceof CouiListRowOverlayDrawable) {
                ((CouiListRowOverlayDrawable) curFg).update(dark, hasDividerBelow, isActionRow);
            } else {
                cardTarget.setForeground(new CouiListRowOverlayDrawable(ctx, dark, hasDividerBelow, isActionRow));
            }
        }
    }

    private static void styleActionRowTextRecursive(View v, boolean dark) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            tv.setTextColor(dark ? 0xE6FFFFFF : 0xFF191919);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        } else if (v instanceof ImageView) {
            // Hide Chromium's built-in chevron arrow to eliminate double '>>' chevron
            if (v.getVisibility() != View.INVISIBLE) {
                v.setVisibility(View.INVISIBLE);
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                styleActionRowTextRecursive(vg.getChildAt(i), dark);
            }
        }
    }

    private static void styleCouiRadioButton(final RadioButton rb, boolean dark) {
        if (rb == null) return;
        String wantTag = RADIO_TAG + "_" + (dark ? "d" : "l");
        if (wantTag.equals(rb.getTag())) return;
        rb.setTag(wantTag);
        rb.setButtonTintList(null);
        rb.setButtonDrawable(new CouiRadioDrawable(rb.getContext(), dark));
    }

    /** Crisp 1:1 ColorOS 17 Radio Button drawable (#0080FF/#1A8CFF checked + white center dot). */
    private static final class CouiRadioDrawable extends Drawable {
        private final Context ctx;
        private final boolean isDark;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int sizePx;
        private boolean checked = false;

        CouiRadioDrawable(Context ctx, boolean isDark) {
            this.ctx = ctx;
            this.isDark = isDark;
            this.sizePx = dp(ctx, 22);
        }

        @Override public int getIntrinsicWidth() { return sizePx; }
        @Override public int getIntrinsicHeight() { return sizePx; }
        @Override public boolean isStateful() { return true; }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean nowChecked = false;
            if (state != null) {
                for (int s : state) {
                    if (s == android.R.attr.state_checked) {
                        nowChecked = true;
                        break;
                    }
                }
            }
            if (nowChecked != checked) {
                checked = nowChecked;
                invalidateSelf();
                return true;
            }
            return false;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            float r = dpF(ctx, 9.5f);
            if (checked) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(isDark ? 0xFF1A8CFF : 0xFF0080FF);
                canvas.drawCircle(cx, cy, r, p);
                p.setColor(0xFFFFFFFF);
                canvas.drawCircle(cx, cy, dpF(ctx, 3.8f), p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dpF(ctx, 1.6f));
                p.setColor(isDark ? 0xFF48484A : 0xFFC4C7CC);
                canvas.drawCircle(cx, cy, r - dpF(ctx, 0.8f), p);
            }
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Draws 16dp-inset 1dp COUI card divider and/or right '>' chevron as a lightweight row foreground. */
    private static final class CouiListRowOverlayDrawable extends Drawable {
        private final Context ctx;
        private boolean isDark;
        private boolean drawDivider;
        private boolean drawChevron;
        private final Paint divPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chevPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path chevPath = new Path();

        CouiListRowOverlayDrawable(Context ctx, boolean isDark, boolean drawDivider, boolean drawChevron) {
            this.ctx = ctx;
            this.isDark = isDark;
            this.drawDivider = drawDivider;
            this.drawChevron = drawChevron;
            divPaint.setStyle(Paint.Style.FILL);
            chevPaint.setStyle(Paint.Style.STROKE);
            chevPaint.setStrokeCap(Paint.Cap.ROUND);
            chevPaint.setStrokeJoin(Paint.Join.ROUND);
            chevPaint.setStrokeWidth(dpF(ctx, 1.85f));
        }

        void update(boolean dark, boolean divider, boolean chevron) {
            if (this.isDark != dark || this.drawDivider != divider || this.drawChevron != chevron) {
                this.isDark = dark;
                this.drawDivider = divider;
                this.drawChevron = chevron;
                invalidateSelf();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            if (drawDivider) {
                float inset = dpF(ctx, 16f);
                float h = Math.max(1f, dpF(ctx, 0.75f));
                divPaint.setColor(isDark ? 0x24FFFFFF : 0x14000000);
                if (b.right - inset > b.left + inset) {
                    canvas.drawRect(b.left + inset, b.bottom - h, b.right - inset, b.bottom, divPaint);
                }
            }
            if (drawChevron) {
                chevPaint.setStyle(Paint.Style.FILL);
                chevPaint.setColor(isDark ? 0x66FFFFFF : 0xFFB2B2B2);
                float cy = b.exactCenterY();
                float cx = b.right - dpF(ctx, 20f);
                FluentIcons.drawCentered(canvas, FluentIcons.CHEVRON_RIGHT, cx, cy, dpF(ctx, 16f), chevPaint);
            }
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Hooked into Lec3.i(Canvas, RecyclerView) (ItemDecoration.onDrawOver).
     * Styles visible preference rows and draws 16dp-inset 1dp ColorOS 17 horizontal dividers
     * plus crisp ColorOS 17 right chevrons '>' on clickable non-switch rows.
     */
    public static void onSettingsDrawOver(Object ec3, Canvas canvas, ViewGroup recyclerView) {
        if (ec3 == null || canvas == null || recyclerView == null) return;
        try {
            Context ctx = recyclerView.getContext();
            boolean dark = isDarkMode(ctx);
            int canvasColor = dark ? 0xFF000000 : 0xFFF0F1F3;
            recyclerView.setBackgroundColor(canvasColor);
            Activity act = unwrapActivity(ctx);
            applySettingsRecyclerPadding(recyclerView, act);
            attachCouiOverscroll(recyclerView);

            // Re-keep top bar chrome (status bar, back button + progressive blur) locked across fragment transitions
            if (act != null) {
                styleEdgeToEdgeBaseLayout(act, canvasColor);
                View root = act.findViewById(ID_SETTINGS_ACTIVITY);
                if (root != null) {
                    View tb = act.findViewById(ID_SETTINGS_TOOLBAR);
                    if (tb instanceof ViewGroup) {
                        ViewGroup tbg = (ViewGroup) tb;
                        for (int j = 0; j < tbg.getChildCount(); j++) {
                            View c = tbg.getChildAt(j);
                            if (c instanceof ImageButton && c.getVisibility() != View.INVISIBLE) {
                                c.setVisibility(View.INVISIBLE);
                                c.setAlpha(0f);
                                c.setClickable(false);
                            } else if (c instanceof TextView && !(c instanceof EditText)) {
                                ((TextView) c).setTextColor(dark ? 0xE6FFFFFF : 0xFF191919);
                            } else if (c.getClass().getName().contains("ActionMenuView") && c.getVisibility() != View.GONE) {
                                c.setVisibility(View.GONE);
                            }
                        }
                    }
                    View blur = root.findViewWithTag(SETTINGS_TOP_BLUR_TAG);
                    if (blur != null) blur.invalidate();
                }
            }

            Field fList = ec3.getClass().getDeclaredField("b");
            fList.setAccessible(true);
            ArrayList<?> styleList = (ArrayList<?>) fList.get(ec3);

            Method getAdapterPos = recyclerView.getClass().getMethod("P", View.class);

            float dividerInset = dpF(ctx, 16f);
            float dividerHeight = Math.max(1f, dpF(ctx, 0.75f));
            DIVIDER_PAINT.setColor(dark ? 0x24FFFFFF : 0x14000000);

            CHEVRON_PAINT.setStyle(Paint.Style.FILL);
            CHEVRON_PAINT.setColor(dark ? 0x66FFFFFF : 0xFFB2B2B2);
            float chevSize = dpF(ctx, 16f);
            float chevRightMargin = dpF(ctx, 20f);

            int childCount = recyclerView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = recyclerView.getChildAt(i);
                if (child == null || child.getVisibility() != View.VISIBLE) continue;

                Object bc3 = null;
                int pos = (Integer) getAdapterPos.invoke(null, child);
                if (styleList != null && pos >= 0 && pos < styleList.size()) {
                    bc3 = styleList.get(pos);
                }

                RowKind kind = stylePreferenceItemView(child, dark, bc3);

                if (kind == RowKind.CARD_ROW || kind == RowKind.CARD_ROW_WITH_SWITCH) {
                    // Check if this card row is followed by another row inside the same card (bottomRadius == 0f)
                    boolean hasBottomDivider = false;
                    if (bc3 != null) {
                        Field fBotR = bc3.getClass().getDeclaredField("b");
                        fBotR.setAccessible(true);
                        float botR = fBotR.getFloat(bc3);
                        hasBottomDivider = (botR == 0f);
                    }
                    if (hasBottomDivider) {
                        float left = child.getLeft() + dividerInset;
                        float right = child.getRight() - dividerInset;
                        float bottom = child.getBottom() + child.getTranslationY();
                        if (right > left) {
                            canvas.drawRect(left, bottom - dividerHeight, right, bottom, DIVIDER_PAINT);
                        }
                    }

                    // Draw official Microsoft Fluent UI '>' right chevron on clickable non-switch preference rows
                    if (kind == RowKind.CARD_ROW && child.isClickable()) {
                        float cy = (child.getTop() + child.getBottom()) * 0.5f + child.getTranslationY();
                        float cx = child.getRight() - chevRightMargin;
                        FluentIcons.drawCentered(canvas, FluentIcons.CHEVRON_RIGHT, cx, cy, chevSize, CHEVRON_PAINT);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "onSettingsDrawOver failed", t);
        }
    }

    private enum RowKind {
        CATEGORY_HEADER,
        CARD_ROW,
        CARD_ROW_WITH_SWITCH,
        OTHER
    }

    private static RowKind stylePreferenceItemView(View itemView, boolean dark, Object bc3) {
        if (itemView == null) return RowKind.OTHER;
        Context ctx = itemView.getContext();
        int bgCol = -1;
        if (bc3 != null) {
            try {
                Field fBg = bc3.getClass().getDeclaredField("h");
                fBg.setAccessible(true);
                bgCol = fBg.getInt(bc3);
            } catch (Throwable ignored) {}
        }

        // Enforce uniform 16dp horizontal card margins across ALL Settings cards (prevents narrow/centered
        // cards such as Address bar Top/Bottom radio group in address-bar-settings.jpg!)
        if (bgCol != 0) {
            int wantMarginH = dp(ctx, 16);
            ViewGroup.LayoutParams ilp = itemView.getLayoutParams();
            if (ilp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams imlp = (ViewGroup.MarginLayoutParams) ilp;
                if (imlp.leftMargin != wantMarginH || imlp.rightMargin != wantMarginH) {
                    imlp.leftMargin = wantMarginH;
                    imlp.rightMargin = wantMarginH;
                    itemView.setLayoutParams(imlp);
                }
            }
        }

        // Handle RadioButtonWithDescriptionLayout preference cards (Address bar, Theme, Cookies, WebRTC, etc.)
        if (itemView instanceof ViewGroup) {
            View radioGroup = itemView.findViewById(ID_ADDRESS_BAR_RADIO_GROUP);
            if (radioGroup == null) {
                radioGroup = findViewByClassNameContains((ViewGroup) itemView, "RadioButtonWithDescriptionLayout");
            }
            if (radioGroup instanceof ViewGroup) {
                styleCouiRadioGroupCard((ViewGroup) radioGroup, dark);
                return RowKind.OTHER;
            }
        }

        // Strip left icon views (android.R.id.icon & icon_frame) for 1:1 text-only ColorOS 17 rows
        View iconView = itemView.findViewById(android.R.id.icon);
        if (iconView != null && iconView.getVisibility() != View.GONE) {
            iconView.setVisibility(View.GONE);
            ViewParent parent = iconView.getParent();
            if (parent instanceof View && ((View) parent).getId() != itemView.getId() && !(parent instanceof RelativeLayout)) {
                View pv = (View) parent;
                if (pv.getWidth() < dp(ctx, 72)) {
                    pv.setVisibility(View.GONE);
                }
            }
        }
        hideIconFramesRecursive(itemView);
        styleCouiControlsRecursive(itemView, dark);

        TextView titleTv = itemView.findViewById(android.R.id.title);
        TextView summaryTv = itemView.findViewById(android.R.id.summary);
        CompoundButton toggle = findCompoundButton(itemView);

        // Category header row (PreferenceCategory has bgCol == 0)
        if (bgCol == 0 || (titleTv != null && !itemView.isClickable() && toggle == null && summaryTv == null)) {
            if (titleTv != null) {
                titleTv.setTextColor(dark ? 0x8AFFFFFF : 0xFF7F7F7F);
                titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                titleTv.setAllCaps(false);
            }
            int padH = dp(ctx, 16);
            itemView.setPadding(padH, dp(ctx, 10), padH, dp(ctx, 6));
            return RowKind.CATEGORY_HEADER;
        }

        // Standard preference row inside a 20dp squircle card
        if (titleTv != null) {
            titleTv.setTextColor(dark ? 0xE6FFFFFF : 0xFF191919);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            titleTv.setAllCaps(false);
        }
        if (summaryTv != null) {
            summaryTv.setTextColor(dark ? 0x73FFFFFF : 0xFF8C8C8C);
            summaryTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            summaryTv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }

        if (toggle != null && toggle.getVisibility() == View.VISIBLE) {
            if (toggle instanceof RadioButton) {
                styleCouiRadioButton((RadioButton) toggle, dark);
            } else if (toggle instanceof CheckBox) {
                styleCouiCheckBox((CheckBox) toggle, dark);
            } else {
                styleCouiSwitch(toggle, dark);
            }
            int padL = dp(ctx, 16);
            int padR = dp(ctx, 16);
            if (itemView.getPaddingLeft() != padL || itemView.getPaddingRight() != padR) {
                itemView.setPadding(padL, itemView.getPaddingTop(), padR, itemView.getPaddingBottom());
            }
            return RowKind.CARD_ROW_WITH_SWITCH;
        }

        if (titleTv != null) {
            int padL = dp(ctx, 16);
            int padR = dp(ctx, 36); // Reserve right space for the Fluent '>' chevron
            if (itemView.getPaddingLeft() != padL || itemView.getPaddingRight() != padR) {
                itemView.setPadding(padL, itemView.getPaddingTop(), padR, itemView.getPaddingBottom());
            }
            return RowKind.CARD_ROW;
        }

        return RowKind.OTHER;
    }

    private static View findViewByClassNameContains(ViewGroup vg, String token) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c.getClass().getName().contains(token)) return c;
            if (c instanceof ViewGroup) {
                View f = findViewByClassNameContains((ViewGroup) c, token);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static void hideIconFramesRecursive(View v) {
        if (!(v instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) v;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            String clsName = c.getClass().getName();
            if (clsName.contains("PreferenceImageView")) {
                c.setVisibility(View.GONE);
                ViewParent p = c.getParent();
                if (p instanceof LinearLayout && ((ViewGroup) p).getChildCount() == 1) {
                    ((View) p).setVisibility(View.GONE);
                }
            } else if (c instanceof ViewGroup) {
                hideIconFramesRecursive(c);
            }
        }
    }

    private static CompoundButton findCompoundButton(View v) {
        if (v instanceof CompoundButton) return (CompoundButton) v;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                CompoundButton found = findCompoundButton(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Styles any SwitchCompat / MaterialSwitch into a 1:1 ColorOS 17 44x24dp pill switch
     * (#0080FF checked / #D8DADC light or #38383A dark unchecked track, 18dp white circular thumb with 3dp inset)
     * and attaches ROM haptic 302 on toggle.
     */
    private static void styleCouiSwitch(final CompoundButton sw, boolean dark) {
        if (sw == null) return;
        Object tag = sw.getTag();
        String expectedTag = SWITCH_TAG + "_" + (dark ? "d" : "l");
        if (expectedTag.equals(tag)) return;
        sw.setTag(expectedTag);

        Context ctx = sw.getContext();
        int trackW = dp(ctx, 44);
        int trackH = dp(ctx, 24);
        int inset = dp(ctx, 3);
        int thumbD = dp(ctx, 18);

        GradientDrawable trackOn = new GradientDrawable();
        trackOn.setShape(GradientDrawable.RECTANGLE);
        trackOn.setCornerRadius(trackH * 0.5f);
        trackOn.setSize(trackW, trackH);
        trackOn.setColor(dark ? 0xFF1A8CFF : 0xFF0080FF);

        GradientDrawable trackOff = new GradientDrawable();
        trackOff.setShape(GradientDrawable.RECTANGLE);
        trackOff.setCornerRadius(trackH * 0.5f);
        trackOff.setSize(trackW, trackH);
        trackOff.setColor(dark ? 0xFF38383A : 0xFFD8DADC);

        StateListDrawable trackStates = new StateListDrawable();
        trackStates.addState(new int[]{android.R.attr.state_checked}, trackOn);
        trackStates.addState(new int[]{}, trackOff);

        GradientDrawable thumbCircle = new GradientDrawable();
        thumbCircle.setShape(GradientDrawable.OVAL);
        thumbCircle.setSize(thumbD, thumbD);
        thumbCircle.setColor(0xFFFFFFFF);
        LayerDrawable thumbLayered = new LayerDrawable(new Drawable[]{thumbCircle});
        thumbLayered.setLayerInset(0, inset, inset, inset, inset);

        try {
            Method setTrack = sw.getClass().getMethod("setTrackDrawable", Drawable.class);
            Method setThumb = sw.getClass().getMethod("setThumbDrawable", Drawable.class);
            setTrack.invoke(sw, trackStates);
            setThumb.invoke(sw, thumbLayered);
            try {
                Method setTrackTint = sw.getClass().getMethod("setTrackTintList", ColorStateList.class);
                Method setThumbTint = sw.getClass().getMethod("setThumbTintList", ColorStateList.class);
                setTrackTint.invoke(sw, (ColorStateList) null);
                setThumbTint.invoke(sw, (ColorStateList) null);
            } catch (Throwable ignored) {}
            try {
                Method setSwitchMinW = sw.getClass().getMethod("setSwitchMinWidth", int.class);
                setSwitchMinW.invoke(sw, trackW);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        sw.setMinWidth(trackW);
        sw.setMinimumWidth(trackW);
        sw.setMinHeight(trackH);
        sw.setMinimumHeight(trackH);
    }

    /**
     * Attaches authentic ColorOS 17 SpringOverScroller RK4 cushioned vertical overscroll + edge haptic 307
     * to Settings RecyclerViews, ListViews, ScrollViews, Tab Switcher, and NTP scroll containers.
     * Matches overflow-coloros.mp4 (~500-580ms viscous cushioned rebound, smooth progressive drag resistance,
     * 120Hz Choreographer-stepped via libtitanium_rive.so RK4 solver, 0% idle CPU when settled).
     */
    private static void attachCouiOverscroll(final ViewGroup rv) {
        if (rv == null || OVERSCROLL_TAG.equals(rv.getTag())) return;
        rv.setTag(OVERSCROLL_TAG);
        rv.setOnTouchListener(new View.OnTouchListener() {
            private float lastY = 0f;
            private float overscrollY = 0f;
            private boolean isOverscrolling = false;
            private boolean springRunning = false;
            private long lastFrameNanos = 0L;
            private final float[] rk4State = new float[3]; // [pos, vel, tmpPos]

            private final Choreographer.FrameCallback springCallback = new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    if (!springRunning) return;
                    if (!rv.isAttachedToWindow()) {
                        springRunning = false;
                        return;
                    }
                    float dt = (lastFrameNanos == 0L)
                            ? 0.008f
                            : Math.max(0.004f, Math.min(0.024f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f));
                    lastFrameNanos = frameTimeNanos;
                    boolean active = TitaniumRiveEngine.stepOverscrollRk4(rk4State, dt);
                    overscrollY = rk4State[0];
                    applyChildTranslationY(rv, overscrollY);
                    if (active) {
                        Choreographer.getInstance().postFrameCallback(this);
                    } else {
                        springRunning = false;
                        overscrollY = 0f;
                        applyChildTranslationY(rv, 0f);
                    }
                }
            };

            private void cancelSpring() {
                if (springRunning) {
                    springRunning = false;
                    Choreographer.getInstance().removeFrameCallback(springCallback);
                }
            }

            private void startSpringBack() {
                cancelSpring();
                if (Math.abs(overscrollY) <= 0.5f) {
                    overscrollY = 0f;
                    applyChildTranslationY(rv, 0f);
                    return;
                }
                rk4State[0] = overscrollY;
                rk4State[1] = 0f;
                rk4State[2] = overscrollY;
                lastFrameNanos = 0L;
                springRunning = true;
                Choreographer.getInstance().postFrameCallback(springCallback);
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastY = event.getRawY();
                        cancelSpring();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float curY = event.getRawY();
                        float dy = curY - lastY;
                        lastY = curY;
                        boolean atTop = !rv.canScrollVertically(-1);
                        boolean atBottom = !rv.canScrollVertically(1);
                        if ((atTop && dy > 0 && overscrollY >= 0)
                                || (atBottom && dy < 0 && overscrollY <= 0)
                                || isOverscrolling) {
                            if (!isOverscrolling && Math.abs(dy) > 1.5f) {
                                isOverscrolling = true;
                                performCouiHapticCode(rv, COUI_HAPTIC_OVERSCROLL);
                            }
                            // Exact ColorOS 17 progressive drag resistance: 0.40 * (1 - |offset| / screenH)
                            float screenH = Math.max(dpF(rv.getContext(), 600f),
                                    rv.getResources().getDisplayMetrics().heightPixels);
                            float ratio = Math.min(Math.abs(overscrollY) / screenH, 0.92f);
                            float factor = 0.40f * (1.0f - ratio);
                            overscrollY += dy * factor;
                            if ((atTop && !atBottom && overscrollY < 0f)
                                    || (atBottom && !atTop && overscrollY > 0f)) {
                                overscrollY = 0f;
                                isOverscrolling = false;
                            }
                            applyChildTranslationY(rv, overscrollY);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isOverscrolling = false;
                        if (Math.abs(overscrollY) > 0.5f) {
                            startSpringBack();
                        } else {
                            overscrollY = 0f;
                            applyChildTranslationY(rv, 0f);
                        }
                        break;
                }
                return false;
            }
        });
    }

    private static void applyChildTranslationY(ViewGroup rv, float ty) {
        for (int i = 0; i < rv.getChildCount(); i++) {
            View c = rv.getChildAt(i);
            if (c != null) c.setTranslationY(ty);
        }
        rv.invalidate();
    }

    /**
     * Authentic 40dp ColorOS 17 Liquid Glass Circular Back Button View for Settings Toolbar.
     * Matches coloros-settings.jpg & manage-esim-backbutton-blur.mp4 1:1.
     */
    public static final class CouiSettingsBackButtonView extends View {
        private final Activity activity;
        private boolean isDark;
        private View stockNavBtn;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF circleRect = new RectF();

        public CouiSettingsBackButtonView(final Activity activity, boolean isDark, View stockNavBtn) {
            super(activity);
            this.activity = activity;
            this.isDark = isDark;
            this.stockNavBtn = stockNavBtn;
            setClickable(true);
            setFocusable(true);
            setContentDescription("Navigate up");
            setElevation(dpF(activity, 7f));
            if (Build.VERSION.SDK_INT >= 28) {
                setOutlineSpotShadowColor(isDark ? 0x55000000 : 0x26000000);
                setOutlineAmbientShadowColor(isDark ? 0x33000000 : 0x16000000);
            }
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    int d = Math.min(view.getWidth(), view.getHeight());
                    outline.setOval(0, 0, d, d);
                    outline.setAlpha(1.0f);
                }
            });
            setClipToOutline(false);

            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(dpF(activity, 0.9f));

            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeWidth(dpF(activity, 1.85f));
            arrowPaint.setStrokeCap(Paint.Cap.ROUND);
            arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setDarkMode(boolean dark) {
            if (this.isDark != dark) {
                this.isDark = dark;
                invalidate();
            }
        }

        public void setStockNavButton(View btn) {
            this.stockNavBtn = btn;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float cx = w * 0.5f;
            float cy = h * 0.5f;
            float r = Math.min(w, h) * 0.5f - dpF(getContext(), 0.6f);
            circleRect.set(cx - r, cy - r, cx + r, cy + r);

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(isDark ? 0xD8242426 : 0xEBFFFFFF);
            canvas.drawCircle(cx, cy, r, fillPaint);

            LinearGradient sheen = new LinearGradient(
                    0, 0, w * 0.7f, h,
                    isDark
                            ? new int[]{0x2EFFFFFF, 0x0AFFFFFF, 0x16000000}
                            : new int[]{0x66FFFFFF, 0x18FFFFFF, 0x14DCE3EE},
                    new float[]{0f, 0.52f, 1f},
                    Shader.TileMode.CLAMP
            );
            sheenPaint.setShader(sheen);
            canvas.drawCircle(cx, cy, r, sheenPaint);

            LinearGradient rimGrad = new LinearGradient(
                    0, 0, w, h,
                    isDark
                            ? new int[]{0x68FFFFFF, 0x1CFFFFFF, 0x42FFFFFF}
                            : new int[]{0xFFFFFFFF, 0x26C2CBD8, 0xCCFFFFFF},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );
            rimPaint.setShader(rimGrad);
            canvas.drawCircle(cx, cy, r, rimPaint);

            arrowPaint.setStyle(Paint.Style.FILL);
            arrowPaint.setColor(isDark ? 0xE6FFFFFF : 0xFF191919);
            FluentIcons.drawCentered(canvas, FluentIcons.BACK, cx, cy, dpF(getContext(), 21f), arrowPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    animate().scaleX(0.90f).scaleY(0.90f).setDuration(110).start();
                    return true;
                case MotionEvent.ACTION_UP:
                    animate().scaleX(1f).scaleY(1f).setDuration(230)
                            .setInterpolator(new OvershootInterpolator(1.25f)).start();
                    performCouiHaptic(this);
                    if (stockNavBtn != null) {
                        try {
                            stockNavBtn.callOnClick();
                            return true;
                        } catch (Throwable ignored) {}
                    }
                    activity.onBackPressed();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    animate().scaleX(1f).scaleY(1f).setDuration(180).start();
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    /**
     * Scroll-under progressive frosted top-bar blur for SettingsActivity.
     * Captures the top strip of FragmentContainerView into a 0.5x RenderNode, applies real GPU Gaussian blur
     * + AGSL vertical smoothstep alpha feather (API 33+) + smooth #F0F1F3/#000000 canvas fade upward from
     * below the back-button baseline with zero hard bottom seam (matches manage-esim-backbutton-blur.mp4).
     */
    public static final class CouiTopProgressiveBlurView extends View {
        private static final float SCALE = 0.5f;
        private static final String FEATHER_AGSL =
                "uniform shader content;\n"
                + "uniform float uHeight;\n"
                + "uniform float uFadeStart;\n"
                + "half4 main(float2 coord) {\n"
                + "    half4 c = content.eval(coord);\n"
                + "    float t = clamp((uHeight - coord.y) / max(1.0, (uHeight - uFadeStart)), 0.0, 1.0);\n"
                + "    float alpha = t * t * (3.0 - 2.0 * t);\n"
                + "    return c * alpha;\n"
                + "}\n";

        private final View sourceView;
        private final View appBarView;
        private boolean isDark;
        private final RenderNode backdropNode = new RenderNode("coui-settings-top-blur");
        private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] srcLoc = new int[2];
        private final int[] myLoc = new int[2];
        private boolean isRecording = false;
        private boolean effectFailed = false;
        private int lastEffectW = -1;
        private int lastEffectH = -1;

        private final ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!isRecording && isShown() && getWidth() > 0 && getHeight() > 0) {
                    recordBackdrop();
                }
                return true;
            }
        };

        private final View.OnLayoutChangeListener appBarLayoutListener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int orr, int ob) {
                syncHeight();
            }
        };

        public CouiTopProgressiveBlurView(Context ctx, View sourceView, View appBarView, boolean isDark) {
            super(ctx);
            this.sourceView = sourceView;
            this.appBarView = appBarView;
            this.isDark = isDark;
            setClickable(false);
            setFocusable(false);
        }

        public void setDarkMode(boolean dark) {
            if (this.isDark != dark) {
                this.isDark = dark;
                invalidate();
            }
        }

        public void syncHeight() {
            int barBottom = appBarView != null ? appBarView.getBottom() : 0;
            if (barBottom <= 0) {
                Context ctx = getContext();
                int resId = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
                int sb = resId > 0 ? ctx.getResources().getDimensionPixelSize(resId) : dp(ctx, 36);
                barBottom = sb + dp(ctx, 56);
            }
            int targetH = barBottom + dp(getContext(), 28);
            ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp != null && lp.height != targetH) {
                lp.height = targetH;
                setLayoutParams(lp);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            if (appBarView != null) {
                appBarView.addOnLayoutChangeListener(appBarLayoutListener);
            }
            syncHeight();
        }

        @Override
        protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            if (appBarView != null) {
                appBarView.removeOnLayoutChangeListener(appBarLayoutListener);
            }
            super.onDetachedFromWindow();
        }

        private void recordBackdrop() {
            if (effectFailed || Build.VERSION.SDK_INT < 31 || sourceView == null || !sourceView.isShown()) return;
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            int rw = Math.max(1, Math.round(w * SCALE));
            int rh = Math.max(1, Math.round(h * SCALE));
            isRecording = true;
            try {
                sourceView.getLocationInWindow(srcLoc);
                getLocationInWindow(myLoc);
                backdropNode.setPosition(0, 0, rw, rh);
                Canvas rec = backdropNode.beginRecording(rw, rh);
                try {
                    rec.drawColor(isDark ? 0xFF000000 : 0xFFF0F1F3);
                    rec.scale(SCALE, SCALE);
                    rec.translate(srcLoc[0] - myLoc[0], srcLoc[1] - myLoc[1]);
                    sourceView.draw(rec);
                } finally {
                    backdropNode.endRecording();
                }

                if (rw != lastEffectW || rh != lastEffectH) {
                    float blurPx = dpF(getContext(), 15f) * SCALE;
                    RenderEffect eff = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP);
                    if (Build.VERSION.SDK_INT >= 33) {
                        try {
                            RuntimeShader feather = new RuntimeShader(FEATHER_AGSL);
                            feather.setFloatUniform("uHeight", (float) rh);
                            feather.setFloatUniform("uFadeStart", rh * 0.42f);
                            eff = RenderEffect.createChainEffect(
                                    RenderEffect.createRuntimeShaderEffect(feather, "content"), eff);
                        } catch (Throwable ignored) {}
                    }
                    backdropNode.setRenderEffect(eff);
                    lastEffectW = rw;
                    lastEffectH = rh;
                }
            } catch (Throwable t) {
                effectFailed = true;
                Log.w(TAG, "CouiTopProgressiveBlurView recordBackdrop failed", t);
            } finally {
                isRecording = false;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            if (!effectFailed && Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated()
                    && backdropNode.hasDisplayList()) {
                canvas.save();
                canvas.scale(1f / SCALE, 1f / SCALE);
                canvas.drawRenderNode(backdropNode);
                canvas.restore();
            }

            // Smooth progressive #F0F1F3 / #000000 canvas fade upward from bottom feather edge
            // Balanced translucency so blurred cards remain clearly visible gliding behind back button & title
            int[] cols = isDark
                    ? new int[]{0xD8000000, 0x94000000, 0x38000000, 0x00000000}
                    : new int[]{0xD8F0F1F3, 0x94F0F1F3, 0x38F0F1F3, 0x00F0F1F3};
            float[] stops = new float[]{0.0f, 0.45f, 0.78f, 1.0f};
            scrimPaint.setShader(new LinearGradient(0f, 0f, 0f, h, cols, stops, Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, w, h, scrimPaint);
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

    // =========================================================================
    // TASK 3 & 4: Real SurfaceView Hardware Blur + ColorOS 17 3-Piece Liquid
    //             Glass Navigation Bar + Bottom-Right Anchored Glass Flyout Menu
    // =========================================================================

    /**
     * Real-time 120fps SurfaceFlinger hardware blur over Chromium's SurfaceView(s),
     * adapted from InstaGlass VideoBlur (SurfaceControl.Builder.setEffectLayer +
     * SurfaceControl.Transaction.setBackgroundBlurRadius + setCornerRadius + setCrop).
     * Drives 4 independent floating glass regions:
     *   0 = Left Detached Circle (Back)
     *   1 = Center Floating Pill (Search, Home, Tabs)
     *   2 = Right Detached Circle (Menu)
     *   3 = Bottom-Right Anchored Glass Flyout Menu Card
     */
    static final class SurfaceViewBlurController {
        static final int SLOT_LEFT = 0;
        static final int SLOT_CENTER = 1;
        static final int SLOT_RIGHT = 2;
        static final int SLOT_FLYOUT = 3;
        static final int SLOT_ADDRESS_BAR = 4;
        private static final int SLOT_COUNT = 5;

        private static final Method M_SET_EFFECT_LAYER;
        private static final Method M_SET_BLUR_RADIUS;
        private static final Method M_SET_BLUR_RADIUS_PARAMS;
        private static final Method M_SET_COLOR_TRANSFORM;
        private static final Method M_SET_CORNER_RADIUS;
        static final boolean HIDDEN_API_AVAILABLE;

        // Vibrancy 3x3 saturation matrix (s = 1.36x) + luminous brightness lift (+0.11) for SurfaceFlinger
        private static final float[] VIBRANT_LIGHT_MATRIX = buildSatMatrix3x3(1.36f);
        private static final float[] VIBRANT_LIGHT_OFFSET = new float[]{0.11f, 0.11f, 0.11f};
        private static final float[] VIBRANT_DARK_MATRIX = buildSatMatrix3x3(1.30f);
        private static final float[] VIBRANT_DARK_OFFSET = new float[]{0.03f, 0.03f, 0.03f};
        private static final float[] IDENTITY_MATRIX = new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
        private static final float[] ZERO_OFFSET = new float[]{0f, 0f, 0f};

        private static float[] buildSatMatrix3x3(float s) {
            float inv = 1.0f - s;
            float r = 0.213f * inv;
            float g = 0.715f * inv;
            float b = 0.072f * inv;
            // Column-major / row-major symmetric luminance matrix for SurfaceControl.setColorTransform
            return new float[]{
                r + s, r,     r,
                g,     g + s, g,
                b,     b,     b + s
            };
        }

        /**
         * Builds ColorOS 17's 18-float OplusBlurParam array matching com.oplus.graphics.OplusBlurParam:
         * [0]=blurType(3=QUALITY_KAWASE), [1]=tileMode(2=MIRROR), [2]=zoomFactor(1.0),
         * [3]=blendMode(5=GLOW_COLORDODGE light / 2=OVERLAY dark),
         * [4..7]=blendColor(rgba), [8..11]=mixColor(rgba), [12..15]=blurColor(rgba),
         * [16]=smoothCornerWeight(2.0), [17]=smoothCornerType(0).
         */
        private static float[] buildOplusBlurParam(boolean dark, boolean isFlyout) {
            float[] p = new float[18];
            p[0] = 3.0f; // BLUR_TYPE_QUALITY_KAWASE
            p[1] = 2.0f; // BLUR_TILE_MODE_MIRROR
            p[2] = 1.0f; // mZoomFactor
            if (!dark) {
                p[3] = isFlyout ? 4.0f : 5.0f; // GLOW_OVERLAY for flyout, GLOW_COLORDODGE for floating nav
                // mBlendColor (RGBA)
                p[4] = isFlyout ? 0.93f : 0.42f;
                p[5] = isFlyout ? 0.94f : 0.43f;
                p[6] = isFlyout ? 0.96f : 0.45f;
                p[7] = isFlyout ? 0.56f : 0.48f;
                // mMixColor (RGBA) — luminous crisp lift preserving underlying color saturation
                p[8] = 0.96f;
                p[9] = 0.97f;
                p[10] = 0.99f;
                p[11] = isFlyout ? 0.46f : 0.28f;
            } else {
                p[3] = 2.0f; // BLUR_BLEND_MODE_OVERLAY
                p[4] = 0.26f; p[5] = 0.26f; p[6] = 0.28f; p[7] = 0.46f;
                p[8] = 0.14f; p[9] = 0.14f; p[10] = 0.16f; p[11] = isFlyout ? 0.60f : 0.48f;
            }
            p[16] = 2.0f; // DEFAULT_SMOOTH_CORNER_WEIGHT
            p[17] = 0.0f; // CORNER_TYPE
            return p;
        }

        static {
            Method mEffect = null;
            Method mBlur = null;
            Method mBlurParams = null;
            Method mColorTx = null;
            Method mCorner = null;
            try {
                mEffect = SurfaceControl.Builder.class.getDeclaredMethod("setEffectLayer");
                mEffect.setAccessible(true);
            } catch (Throwable t) {
                Log.w(TAG, "SurfaceControl.Builder.setEffectLayer unavailable: " + t);
            }
            try {
                mBlur = SurfaceControl.Transaction.class.getDeclaredMethod(
                        "setBackgroundBlurRadius", SurfaceControl.class, int.class);
                mBlur.setAccessible(true);
            } catch (Throwable t) {
                Log.w(TAG, "SurfaceControl.Transaction.setBackgroundBlurRadius unavailable: " + t);
            }
            try {
                mBlurParams = SurfaceControl.Transaction.class.getDeclaredMethod(
                        "setBackgroundBlurRadius", SurfaceControl.class, int.class, float[].class);
                mBlurParams.setAccessible(true);
            } catch (Throwable ignored) {}
            try {
                mColorTx = SurfaceControl.Transaction.class.getDeclaredMethod(
                        "setColorTransform", SurfaceControl.class, float[].class, float[].class);
                mColorTx.setAccessible(true);
            } catch (Throwable ignored) {}
            try {
                mCorner = SurfaceControl.Transaction.class.getDeclaredMethod(
                        "setCornerRadius", SurfaceControl.class, float.class);
                mCorner.setAccessible(true);
            } catch (Throwable t) {
                Log.w(TAG, "SurfaceControl.Transaction.setCornerRadius unavailable: " + t);
            }
            M_SET_EFFECT_LAYER = mEffect;
            M_SET_BLUR_RADIUS = mBlur;
            M_SET_BLUR_RADIUS_PARAMS = mBlurParams;
            M_SET_COLOR_TRANSFORM = mColorTx;
            M_SET_CORNER_RADIUS = mCorner;
            HIDDEN_API_AVAILABLE = (mEffect != null && mBlur != null && mCorner != null);
            Log.i(TAG, "SurfaceViewBlurController hiddenApi=" + HIDDEN_API_AVAILABLE
                    + " oplusBlurParam=" + (mBlurParams != null)
                    + " colorTransform=" + (mColorTx != null));
        }

        private static final class SlotLayer {
            final SurfaceControl parent;
            final SurfaceControl child;
            final Rect crop = new Rect();
            float cornerRadius = -1f;
            int blurRadius = -1;
            boolean darkApplied = false;
            boolean visible = false;

            SlotLayer(SurfaceControl parent, SurfaceControl child) {
                this.parent = parent;
                this.child = child;
            }
        }

        private final Activity activity;
        private final View hostView;
        private final View[] slotViews = new View[SLOT_COUNT];
        // cornerDp: > 0f = fixed dp radius; == 0f = auto capsule/circle (height*0.5f); < 0f = 0px square full-bleed
        private final float[] slotCornerDp = new float[SLOT_COUNT];
        private final int[] slotBlurDp = new int[SLOT_COUNT];
        private final WeakHashMap<SurfaceView, SlotLayer[]> surfaceLayers = new WeakHashMap<>();
        private final List<SurfaceView> scratchSurfaces = new ArrayList<>();
        private final int[] svLoc = new int[2];
        private final int[] vLoc = new int[2];
        private final Rect tmpCrop = new Rect();
        private final float[] svScale = new float[2];
        private final float[] vScale = new float[2];
        private long lastTreeScanMs = 0L;
        private SurfaceView cachedPrimarySv = null;
        private boolean failed = false;
        private boolean hasActiveBlur = false;
        private boolean blurSuppressed = false;
        private boolean suppressDirty = false;
        private boolean darkMode = false;
        private boolean oplusParamsFailed = false;
        private boolean colorTxFailed = false;
        private boolean loggedFirstActive = false;

        SurfaceViewBlurController(Activity activity, View hostView) {
            this.activity = activity;
            this.hostView = hostView;
            this.darkMode = isDarkMode(activity);
        }

        void setDarkMode(boolean dark) {
            this.darkMode = dark;
        }

        void setSlot(int slot, View view, float cornerDp, int blurDp) {
            if (slot < 0 || slot >= SLOT_COUNT) return;
            slotViews[slot] = view;
            slotCornerDp[slot] = cornerDp;
            slotBlurDp[slot] = blurDp;
        }

        void setBlurSuppressed(boolean suppressed) {
            if (this.blurSuppressed != suppressed) {
                this.blurSuppressed = suppressed;
                this.suppressDirty = true;
            }
        }

        boolean isBlurActive() {
            return HIDDEN_API_AVAILABLE && !failed && !blurSuppressed && hasActiveBlur;
        }

        void update() {
            if (failed || !HIDDEN_API_AVAILABLE || Build.VERSION.SDK_INT < 31) return;
            try {
                updateInternal();
            } catch (Throwable t) {
                failed = true;
                hasActiveBlur = false;
                Log.w(TAG, "SurfaceViewBlurController failed, disabling", t);
                releaseAll();
            }
        }

        private void collectSurfaceViews(View v, List<SurfaceView> out) {
            if (v == null || v.getVisibility() != View.VISIBLE) return;
            if (v instanceof SurfaceView) {
                out.add((SurfaceView) v);
                return;
            }
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    collectSurfaceViews(vg.getChildAt(i), out);
                }
            }
        }

        private void computeScale(View v, float[] outScale) {
            float sx = 1f;
            float sy = 1f;
            View cur = v;
            while (cur != null) {
                sx *= cur.getScaleX();
                sy *= cur.getScaleY();
                ViewParent p = cur.getParent();
                cur = (p instanceof View) ? (View) p : null;
            }
            outScale[0] = (sx > 0.01f) ? sx : 1f;
            outScale[1] = (sy > 0.01f) ? sy : 1f;
        }

        private SurfaceControl createEffectLayer(SurfaceControl parent, int slot) throws Exception {
            SurfaceControl.Builder b = new SurfaceControl.Builder()
                    .setName("coui-glass-blur-" + slot)
                    .setParent(parent);
            M_SET_EFFECT_LAYER.invoke(b);
            return b.build();
        }

        private void releaseSlotLayer(SlotLayer sl) {
            if (sl == null) return;
            try {
                new SurfaceControl.Transaction().reparent(sl.child, null).apply();
            } catch (Throwable ignored) {}
            try {
                sl.child.release();
            } catch (Throwable ignored) {}
        }

        void releaseAll() {
            for (SlotLayer[] arr : surfaceLayers.values()) {
                if (arr != null) {
                    for (SlotLayer sl : arr) releaseSlotLayer(sl);
                }
            }
            surfaceLayers.clear();
            hasActiveBlur = false;
        }

        private void updateInternal() throws Exception {
            Window w = activity.getWindow();
            if (w == null) return;
            android.view.AttachedSurfaceControl rootSc = hostView.getRootSurfaceControl();
            if (rootSc == null) return;

            // Round 14: Fast O(1) suppression path during video playback, fullscreen, or Tab Switcher.
            // Immediately clears backgroundBlurRadius=0 and hides all SurfaceControl blur layers so
            // SurfaceFlinger drops out of 60fps RenderEngine GPU Gaussian blur into zero-GPU HWC overlay!
            if (blurSuppressed) {
                if (!suppressDirty && !hasActiveBlur) {
                    return;
                }
                SurfaceControl.Transaction hideTx = null;
                for (SlotLayer[] arr : surfaceLayers.values()) {
                    if (arr == null) continue;
                    for (int slot = 0; slot < SLOT_COUNT; slot++) {
                        SlotLayer sl = arr[slot];
                        if (sl != null && (sl.visible || sl.blurRadius != 0)) {
                            if (hideTx == null) hideTx = new SurfaceControl.Transaction();
                            M_SET_BLUR_RADIUS.invoke(hideTx, sl.child, 0);
                            hideTx.setVisibility(sl.child, false);
                            sl.visible = false;
                            sl.blurRadius = 0;
                        }
                    }
                }
                if (hideTx != null) {
                    rootSc.applyTransactionOnDraw(hideTx);
                }
                suppressDirty = false;
                if (hasActiveBlur) {
                    hasActiveBlur = false;
                    for (View v : slotViews) {
                        if (v != null) v.invalidate();
                    }
                }
                return;
            }
            suppressDirty = false;

            long nowMs = SystemClock.uptimeMillis();
            boolean primaryHealthy = (cachedPrimarySv != null
                    && cachedPrimarySv.isAttachedToWindow()
                    && cachedPrimarySv.isShown()
                    && cachedPrimarySv.getWidth() > 0
                    && cachedPrimarySv.getHeight() > 0);
            long scanIntervalMs = primaryHealthy ? 2000L : 500L;
            boolean needScan = scratchSurfaces.isEmpty() || (nowMs - lastTreeScanMs >= scanIntervalMs);
            if (!needScan) {
                for (int i = 0; i < scratchSurfaces.size(); i++) {
                    SurfaceView sv = scratchSurfaces.get(i);
                    if (!sv.isAttachedToWindow() || sv.getVisibility() != View.VISIBLE) {
                        needScan = true;
                        break;
                    }
                }
            }
            if (needScan) {
                lastTreeScanMs = nowMs;
                scratchSurfaces.clear();
                collectSurfaceViews(w.getDecorView(), scratchSurfaces);

                // Clean up stale SurfaceViews no longer in the tree
                Iterator<Map.Entry<SurfaceView, SlotLayer[]>> it = surfaceLayers.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<SurfaceView, SlotLayer[]> e = it.next();
                    if (!scratchSurfaces.contains(e.getKey())) {
                        for (SlotLayer sl : e.getValue()) releaseSlotLayer(sl);
                        it.remove();
                    }
                }
            }

            // Round 14: Select strictly ONE primary active SurfaceView (largest valid area; topmost wins ties).
            // Prevents double-stacking coui-glass-blur-0..4 across 2 Chromium SurfaceViews (6+ GPU blur passes!).
            SurfaceView primarySv = null;
            SurfaceControl primaryParentSc = null;
            long bestArea = -1L;
            for (int si = 0; si < scratchSurfaces.size(); si++) {
                SurfaceView sv = scratchSurfaces.get(si);
                SurfaceControl parentSc = sv.getSurfaceControl();
                if (parentSc != null && parentSc.isValid()
                        && sv.isShown() && sv.getWidth() > 0 && sv.getHeight() > 0
                        && sv.getRootSurfaceControl() == rootSc) {
                    long area = (long) sv.getWidth() * (long) sv.getHeight();
                    if (area >= bestArea) {
                        bestArea = area;
                        primarySv = sv;
                        primaryParentSc = parentSc;
                    }
                }
            }
            cachedPrimarySv = primarySv;

            // Allocate SurfaceControl.Transaction LAZILY only when a layer actually changes (ZERO steady-scroll JNI allocs!)
            SurfaceControl.Transaction tx = null;
            boolean anyActive = false;

            // Hide any blur layers belonging to non-primary or invalid SurfaceViews
            for (Map.Entry<SurfaceView, SlotLayer[]> e : surfaceLayers.entrySet()) {
                if (e.getKey() == primarySv) continue;
                SlotLayer[] arr = e.getValue();
                if (arr != null) {
                    for (int slot = 0; slot < SLOT_COUNT; slot++) {
                        SlotLayer sl = arr[slot];
                        if (sl != null && (sl.visible || sl.blurRadius != 0)) {
                            if (tx == null) tx = new SurfaceControl.Transaction();
                            M_SET_BLUR_RADIUS.invoke(tx, sl.child, 0);
                            tx.setVisibility(sl.child, false);
                            sl.visible = false;
                            sl.blurRadius = 0;
                        }
                    }
                }
            }

            if (primarySv != null && primaryParentSc != null) {
                SlotLayer[] arr = surfaceLayers.get(primarySv);
                if (arr == null) {
                    arr = new SlotLayer[SLOT_COUNT];
                    surfaceLayers.put(primarySv, arr);
                }

                primarySv.getLocationInWindow(svLoc);
                computeScale(primarySv, svScale);

                for (int slot = 0; slot < SLOT_COUNT; slot++) {
                    View target = slotViews[slot];
                    SlotLayer sl = arr[slot];

                    boolean targetVisible = (target != null && target.isShown()
                            && target.getAlpha() > 0.05f
                            && target.getWidth() > 0 && target.getHeight() > 0);
                    if (!targetVisible) {
                        if (sl != null && (sl.visible || sl.blurRadius != 0)) {
                            if (tx == null) tx = new SurfaceControl.Transaction();
                            M_SET_BLUR_RADIUS.invoke(tx, sl.child, 0);
                            tx.setVisibility(sl.child, false);
                            sl.visible = false;
                            sl.blurRadius = 0;
                        }
                        continue;
                    }

                    target.getLocationInWindow(vLoc);
                    computeScale(target, vScale);
                    float tw = target.getWidth() * vScale[0];
                    float th = target.getHeight() * vScale[1];
                    int left = Math.round((vLoc[0] - svLoc[0]) / svScale[0]);
                    int top = Math.round((vLoc[1] - svLoc[1]) / svScale[1]);
                    int right = Math.round((vLoc[0] + tw - svLoc[0]) / svScale[0]);
                    int bottom = Math.round((vLoc[1] + th - svLoc[1]) / svScale[1]);
                    tmpCrop.set(left, top, right, bottom);

                    boolean overlaps = tmpCrop.intersects(0, 0, primarySv.getWidth(), primarySv.getHeight());
                    if (!overlaps) {
                        if (sl != null && (sl.visible || sl.blurRadius != 0)) {
                            if (tx == null) tx = new SurfaceControl.Transaction();
                            M_SET_BLUR_RADIUS.invoke(tx, sl.child, 0);
                            tx.setVisibility(sl.child, false);
                            sl.visible = false;
                            sl.blurRadius = 0;
                        }
                        continue;
                    }

                    if (sl == null || sl.parent != primaryParentSc) {
                        if (sl != null) releaseSlotLayer(sl);
                        sl = new SlotLayer(primaryParentSc, createEffectLayer(primaryParentSc, slot));
                        arr[slot] = sl;
                    }

                    float cornerPx;
                    if (slotCornerDp[slot] < 0f) {
                        cornerPx = 0f; // Full-bleed edge-to-edge header strip (ZERO corner leaks!)
                    } else if (slotCornerDp[slot] > 0f) {
                        cornerPx = dpF(activity, slotCornerDp[slot]) * Math.min(vScale[0], vScale[1]);
                    } else {
                        cornerPx = Math.min(tw, th) * 0.5f;
                    }
                    float scaledCorner = cornerPx / svScale[1];
                    int blurPx = dp(activity, slotBlurDp[slot] > 0 ? slotBlurDp[slot] : 22);

                    if (!sl.visible || !tmpCrop.equals(sl.crop)
                            || Math.abs(scaledCorner - sl.cornerRadius) > 0.5f
                            || blurPx != sl.blurRadius
                            || sl.darkApplied != darkMode) {
                        if (tx == null) tx = new SurfaceControl.Transaction();
                        tx.setCrop(sl.child, tmpCrop);
                        tx.setLayer(sl.child, 2 + slot);
                        M_SET_CORNER_RADIUS.invoke(tx, sl.child, scaledCorner);
                        boolean appliedCustomized = false;
                        if (M_SET_BLUR_RADIUS_PARAMS != null && !oplusParamsFailed && slot != SLOT_ADDRESS_BAR) {
                            try {
                                float[] bp = buildOplusBlurParam(darkMode, slot == SLOT_FLYOUT);
                                M_SET_BLUR_RADIUS_PARAMS.invoke(tx, sl.child, blurPx, bp);
                                appliedCustomized = true;
                            } catch (Throwable t) {
                                oplusParamsFailed = true;
                                Log.w(TAG, "OplusBlurParam surface blur fallback", t);
                            }
                        }
                        if (!appliedCustomized) {
                            M_SET_BLUR_RADIUS.invoke(tx, sl.child, blurPx);
                        }
                        if (M_SET_COLOR_TRANSFORM != null && !colorTxFailed && slot != SLOT_ADDRESS_BAR) {
                            try {
                                M_SET_COLOR_TRANSFORM.invoke(tx, sl.child,
                                        darkMode ? VIBRANT_DARK_MATRIX : VIBRANT_LIGHT_MATRIX,
                                        darkMode ? VIBRANT_DARK_OFFSET : VIBRANT_LIGHT_OFFSET);
                            } catch (Throwable t) {
                                colorTxFailed = true;
                                Log.w(TAG, "SurfaceControl.setColorTransform fallback", t);
                            }
                        }
                        tx.setVisibility(sl.child, true);
                        sl.crop.set(tmpCrop);
                        sl.cornerRadius = scaledCorner;
                        sl.blurRadius = blurPx;
                        sl.darkApplied = darkMode;
                        sl.visible = true;
                    }
                    anyActive = true;
                }
            }

            if (tx != null) {
                rootSc.applyTransactionOnDraw(tx);
            }

            boolean prevActive = hasActiveBlur;
            hasActiveBlur = anyActive;
            if (anyActive && !loggedFirstActive) {
                loggedFirstActive = true;
                Log.i(TAG, "SurfaceViewBlurController ACTIVE (primary 1/" + scratchSurfaces.size() + " SurfaceView)");
            }
            if (prevActive != hasActiveBlur) {
                for (View v : slotViews) {
                    if (v != null) v.invalidate();
                }
            }
        }
    }

    /**
     * Dispatches any Chromium menu item ID natively via ChromeTabbedActivity.R2(int, boolean, Bundle, Lpo9).
     */
    static boolean dispatchChromiumMenuAction(Activity activity, int menuId) {
        if (activity == null) return false;
        try {
            Class<?> cls = activity.getClass();
            while (cls != null && cls != Activity.class) {
                for (Method m : cls.getDeclaredMethods()) {
                    if ("R2".equals(m.getName())) {
                        Class<?>[] p = m.getParameterTypes();
                        if (p.length == 4 && p[0] == int.class && p[1] == boolean.class
                                && p[2] == Bundle.class) {
                            m.setAccessible(true);
                            Object res = m.invoke(activity, menuId, Boolean.TRUE, null, null);
                            return (res instanceof Boolean) ? (Boolean) res : true;
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable t) {
            Log.w(TAG, "dispatchChromiumMenuAction failed for id=0x" + Integer.toHexString(menuId), t);
        }
        return false;
    }

    /**
     * 1:1 ColorOS 17 Gallery 3-Piece Floating Liquid Glass Bottom Navigation Bar:
     * - Left Detached Glass Circle (56x56dp): Back
     * - Center Floating Frosted Glass Pill (height=56dp, 12dp side gaps):
     *   3 items only (Search, Home, Tabs) — selected item renders as filled solid black icon + bold label
     *   with ZERO permanent blob at rest; transient frosted glass lens appears strictly while pressing/sliding.
     * - Right Detached Glass Circle (56x56dp): Menu (tap opens bottom-right anchored ColorOS 17 Glass Flyout Menu;
     *   long-press opens Extensions).
     */
    public static final class CouiLiquidGlassNavBar extends FrameLayout {
        private static final String[] CENTER_LABELS = {"Search", "Home", "Tabs"};
        private static final int CENTER_ITEM_COUNT = 3;

        private static final int ACTION_BACK = 0;
        private static final int ACTION_SEARCH = 1;
        private static final int ACTION_HOME = 2;
        private static final int ACTION_TABS = 3;
        private static final int ACTION_MENU = 4;
        private static final int ACTION_EXTENSIONS = 5;

        private final Activity activity;
        private final SurfaceViewBlurController blurController;
        private final AudioManager audioManager;
        private final AccessoryCircleView leftCircleView;
        private final CenterCapsuleView centerPillView;
        private final AccessoryCircleView rightCircleView;
        private CouiGlassFlyoutOverlay flyoutOverlay;
        private int selectedCenterIndex = 1; // Default active item: Home
        private boolean isDark;
        private boolean hiddenByIme = false;
        private boolean hiddenByFullscreen = false;
        private int currentBottomPadPx;

        private View cachedControlContainer;
        private View cachedTabHolder;
        private TextView cachedUrlBar;
        private long lastToolbarSyncMs = 0L;
        private long lastUserInteractionMs = SystemClock.uptimeMillis();
        private long lastVideoCheckMs = 0L;
        private boolean cachedMediaActive = false;
        private boolean cachedIsMediaUrl = false;
        private String lastObservedUrl = "";
        private boolean lastUrlFocused = false;
        private boolean lastFullscreenState = false;
        private boolean lastFlyoutOpen = false;
        private final Runnable mediaBlurRecheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAttachedToWindow() && cachedMediaActive && !hiddenByFullscreen) {
                    invalidate();
                }
            }
        };

        // Generalized 60-120fps Hardware RenderNode Vibrant Backdrop Blur over BOTH Tab Switcher
        // (ID_TAB_SWITCHER_HOLDER) AND New Tab Page (NTP native view hierarchy) — fixes missing blur on NTP!
        // Chained GPU saturation (1.40x) + luminance lift (+13%) ColorMatrix + 13dp Gaussian blur.
        private static final float VIEW_BLUR_SCALE = 0.5f;
        private final RenderNode viewBackdropNode = (Build.VERSION.SDK_INT >= 31)
                ? new RenderNode("coui-view-backdrop-blur") : null;
        private final int[] backdropSourceLoc = new int[2];
        private final int[] pieceLoc = new int[2];
        private final Path pieceClipPath = new Path();
        private boolean inTabSwitcher = false;
        private boolean inNtp = false;
        private View activeBackdropSource = null;
        private boolean isRecordingViewBackdrop = false;
        private boolean viewBackdropValid = false;
        private boolean viewBackdropDirty = true;
        private long lastViewBackdropMs = 0L;
        private int lastViewScrollHash = 0;
        private int lastViewBlurW = -1;
        private int lastViewBlurH = -1;
        private boolean lastViewBlurDark = false;

        boolean isInViewBackdropMode() {
            return inTabSwitcher || inNtp;
        }

        boolean isInTabSwitcher() {
            return inTabSwitcher;
        }

        /** Finds the full-screen NTP native page container (sibling of navBar, never ancestor). */
        private View findActiveNtpContainer(View decor) {
            if (decor == null) return null;
            View ntp = decor.findViewById(ID_NTP_CONTENT);
            if (ntp == null || !ntp.isShown() || ntp.getWidth() <= 0 || ntp.getHeight() <= 0) {
                return null;
            }
            // Walk up to the highest ViewGroup wrapping ntp_scrollview / feed_stream_recycler_view
            // inside the browser content frame WITHOUT crossing into android.R.id.content (which holds navBar).
            View best = ntp;
            View scroll = decor.findViewById(ID_NTP_SCROLLVIEW);
            if (scroll != null && scroll.isShown() && scroll.getHeight() >= ntp.getHeight()) {
                best = scroll;
            }
            View feed = decor.findViewById(ID_FEED_STREAM_RV);
            if (feed != null && feed.isShown() && feed.getHeight() >= best.getHeight()) {
                best = feed;
            }
            View cur = best;
            for (int depth = 0; depth < 6; depth++) {
                ViewParent p = cur.getParent();
                if (!(p instanceof View)) break;
                View pv = (View) p;
                int pid = pv.getId();
                if (pid == android.R.id.content || pid == ID_EDGE_TO_EDGE_BASE || pv == decor) {
                    break;
                }
                if (isAncestorOf(pv, this)) {
                    break;
                }
                if (pv.getWidth() >= best.getWidth() && pv.getHeight() >= best.getHeight()) {
                    best = pv;
                }
                cur = pv;
            }
            return best;
        }

        private static boolean isAncestorOf(View candidate, View target) {
            ViewParent p = target != null ? target.getParent() : null;
            while (p != null) {
                if (p == candidate) return true;
                p = p.getParent();
            }
            return false;
        }

        private static ColorMatrixColorFilter createVibrancyColorFilter(boolean dark) {
            // Kyant0/AndroidLiquidGlass ColorControls matrix: saturation=1.40x, luminance lift=+0.13 (light) / +0.04 (dark)
            float sat = dark ? 1.32f : 1.42f;
            float contrast = dark ? 1.04f : 1.03f;
            float brightness = dark ? 0.04f : 0.13f;
            float invSat = 1.0f - sat;
            float r = 0.213f * invSat;
            float g = 0.715f * invSat;
            float b = 0.072f * invSat;
            float t = (0.5f - contrast * 0.5f + brightness) * 255f;
            float cr = contrast * r;
            float cg = contrast * g;
            float cb = contrast * b;
            float cs = contrast * sat;
            ColorMatrix cm = new ColorMatrix(new float[]{
                cr + cs, cg,      cb,      0f, t,
                cr,      cg + cs, cb,      0f, t,
                cr,      cg,      cb + cs, 0f, t,
                0f,      0f,      0f,      1f, 0f
            });
            return new ColorMatrixColorFilter(cm);
        }

        private static boolean isVideoMediaUrl(String raw) {
            if (raw == null || raw.isEmpty()) return false;
            int end = raw.length();
            int q = raw.indexOf('?');
            if (q >= 0 && q < end) end = q;
            int h = raw.indexOf('#');
            if (h >= 0 && h < end) end = h;
            if (end < 4) return false;
            String s = raw.substring(Math.max(0, end - 6), end).toLowerCase(java.util.Locale.US);
            return s.endsWith(".mp4") || s.endsWith(".mkv") || s.endsWith(".webm")
                    || s.endsWith(".mov") || s.endsWith(".m4v") || s.endsWith(".m3u8")
                    || s.endsWith(".mpd") || s.endsWith(".avi") || s.endsWith(".ogv")
                    || s.endsWith(".3gp") || s.endsWith(".ts");
        }

        private static boolean hasKeepScreenOnView(View v, int depth) {
            if (v == null || depth > 12 || v.getVisibility() != View.VISIBLE) return false;
            if (v.getKeepScreenOn()) return true;
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                int n = vg.getChildCount();
                for (int i = 0; i < n; i++) {
                    if (hasKeepScreenOnView(vg.getChildAt(i), depth + 1)) return true;
                }
            }
            return false;
        }

        private int computeViewBackdropScrollHash(View source) {
            if (source == null) return 0;
            int h = source.getWidth() * 31 + source.getHeight();
            h = h * 31 + source.getScrollY();
            if (inTabSwitcher) {
                View tabRv = source.findViewById(ID_TAB_LIST_RECYCLER_VIEW);
                if (tabRv instanceof ViewGroup) {
                    ViewGroup rvg = (ViewGroup) tabRv;
                    int n = rvg.getChildCount();
                    h = h * 31 + n;
                    if (n > 0) {
                        View c0 = rvg.getChildAt(0);
                        if (c0 != null) {
                            h = h * 31 + c0.getTop();
                            h = h * 31 + Math.round(c0.getTranslationY());
                        }
                        View cLast = rvg.getChildAt(n - 1);
                        if (cLast != null) {
                            h = h * 31 + cLast.getBottom();
                        }
                    }
                }
            } else if (inNtp) {
                View ntpContent = source.findViewById(ID_NTP_CONTENT);
                if (ntpContent != null) {
                    h = h * 31 + ntpContent.getTop();
                    h = h * 31 + ntpContent.getScrollY();
                    h = h * 31 + Math.round(ntpContent.getTranslationY());
                }
                View mv = source.findViewById(ID_NTP_MV_TILES_CONTAINER);
                if (mv != null) {
                    h = h * 31 + mv.getTop();
                    h = h * 31 + mv.getHeight();
                }
            }
            return h;
        }

        private void setFullscreenHidden(boolean hide) {
            if (this.hiddenByFullscreen == hide) return;
            this.hiddenByFullscreen = hide;
            if (hide) {
                if (flyoutOverlay != null && flyoutOverlay.isOpen()) {
                    flyoutOverlay.dismiss();
                }
                setVisibility(View.GONE);
            } else {
                setVisibility(View.VISIBLE);
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            lastUserInteractionMs = SystemClock.uptimeMillis();
            if (isInViewBackdropMode()) {
                viewBackdropDirty = true;
            }
            return super.dispatchTouchEvent(ev);
        }

        void markViewBackdropDirty() {
            viewBackdropDirty = true;
        }

        void setMenuMorphProgress(float morph) {
            rightCircleView.setMenuMorphProgress(morph);
        }

        private void recordViewBackdrop(View source) {
            if (Build.VERSION.SDK_INT < 31 || viewBackdropNode == null || isRecordingViewBackdrop || source == null) {
                return;
            }
            int w = source.getWidth();
            int h = source.getHeight();
            if (w <= 0 || h <= 0) {
                viewBackdropValid = false;
                return;
            }
            int rw = Math.max(1, Math.round(w * VIEW_BLUR_SCALE));
            int rh = Math.max(1, Math.round(h * VIEW_BLUR_SCALE));
            isRecordingViewBackdrop = true;
            try {
                source.getLocationInWindow(backdropSourceLoc);
                viewBackdropNode.setPosition(0, 0, rw, rh);
                Canvas rec = viewBackdropNode.beginRecording(rw, rh);
                try {
                    rec.drawColor(isDark ? 0xFF000000 : 0xFFF0F1F3);
                    rec.scale(VIEW_BLUR_SCALE, VIEW_BLUR_SCALE);
                    source.draw(rec);
                } finally {
                    viewBackdropNode.endRecording();
                }
                if (rw != lastViewBlurW || rh != lastViewBlurH || isDark != lastViewBlurDark) {
                    float blurPx = dpF(activity, 13f) * VIEW_BLUR_SCALE;
                    RenderEffect blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP);
                    RenderEffect vibrant = RenderEffect.createColorFilterEffect(createVibrancyColorFilter(isDark), blur);
                    viewBackdropNode.setRenderEffect(vibrant);
                    lastViewBlurW = rw;
                    lastViewBlurH = rh;
                    lastViewBlurDark = isDark;
                }
                viewBackdropValid = true;
                viewBackdropDirty = false;
                lastViewBackdropMs = SystemClock.uptimeMillis();
                leftCircleView.invalidate();
                centerPillView.invalidate();
                rightCircleView.invalidate();
                if (flyoutOverlay != null && (flyoutOverlay.isOpen() || flyoutOverlay.isAnimating())) {
                    flyoutOverlay.invalidateCard();
                }
            } catch (Throwable t) {
                viewBackdropValid = false;
            } finally {
                isRecordingViewBackdrop = false;
            }
        }

        boolean drawViewBackdropBlur(Canvas canvas, View pieceView, RectF clipRect, float radiusPx) {
            if (!isInViewBackdropMode() || !viewBackdropValid || Build.VERSION.SDK_INT < 31
                    || viewBackdropNode == null || !viewBackdropNode.hasDisplayList()
                    || !canvas.isHardwareAccelerated() || pieceView == null) {
                return false;
            }
            pieceView.getLocationInWindow(pieceLoc);
            float sx = pieceView.getScaleX();
            float sy = pieceView.getScaleY();
            if (sx <= 0.05f || sy <= 0.05f) return false;
            int save = canvas.save();
            pieceClipPath.rewind();
            pieceClipPath.addRoundRect(clipRect, radiusPx, radiusPx, Path.Direction.CW);
            canvas.clipPath(pieceClipPath);
            canvas.scale(1f / sx, 1f / sy);
            canvas.translate(backdropSourceLoc[0] - pieceLoc[0], backdropSourceLoc[1] - pieceLoc[1]);
            canvas.scale(1f / VIEW_BLUR_SCALE, 1f / VIEW_BLUR_SCALE);
            canvas.drawRenderNode(viewBackdropNode);
            canvas.restoreToCount(save);
            return true;
        }

        // Synchronized Scroll Auto-Hide (Top Address Bar + Bottom Floating Navigation Pill):
        // - Dual-signal: Window.Callback touch vertical drag + Chromium BrowserControlsManager offset
        // - Lower cumulative downward scroll trigger threshold (~26dp) so moderate reading scrolls reliably hide BOTH bars
        // - Upward micro-jitter hysteresis deadzone (~12dp) so lifting a thumb NEVER cancels hiding
        // - Immune to Chromium's post-finger-up snap-back animation bouncing rawTopOffsetPx back to 0!
        private static final float HIDE_SCROLL_THRESHOLD_DP = 26f;
        private static final float UP_JITTER_DEADZONE_DP = 12f;
        private static final float REVEAL_SCROLL_THRESHOLD_DP = 28f;

        private boolean barsHiddenByScroll = false;
        private int lastRawControlsOffset = 0;
        private float controlsAccumDownPx = 0f;
        private float controlsAccumUpPx = 0f;
        private float currentTopTransY = 0f;
        private float currentBotTransY = 0f;
        private ValueAnimator barsAutoHideAnim;

        // Raw touch gesture tracking (via CouiScrollTouchCallback)
        private boolean touchIgnoreGesture = false;
        private boolean isFingerDown = false;
        private boolean touchLockedVertical = false;
        private float touchStartX = 0f;
        private float touchStartY = 0f;
        private float touchLastY = 0f;
        private float touchAccumDownPx = 0f;
        private float touchAccumUpPx = 0f;
        private int lastScrollDir = 0; // -1 = down (reading), +1 = up (toward top)
        private long lastHideTriggerMs = 0L;
        private long lastTouchUpMs = 0L;
        private float webAccumDownPx = 0f;
        private float webAccumUpPx = 0f;
        private boolean webFingerDown = false;
        private int lastEffYAtWebUpdate = Integer.MIN_VALUE;
        private final int[] hitLoc = new int[2];

        void onWebPageTouchState(boolean down) {
            long now = SystemClock.uptimeMillis();
            webFingerDown = down;
            if (down) {
                lastUserInteractionMs = now;
                webAccumDownPx = 0f;
                webAccumUpPx = 0f;
            } else {
                lastTouchUpMs = now;
                webAccumDownPx = 0f;
                webAccumUpPx = 0f;
            }
        }

        void onWebPageScrollBegin(boolean isDirectionUp) {
            long now = SystemClock.uptimeMillis();
            lastUserInteractionMs = now;
            webAccumDownPx = 0f;
            webAccumUpPx = 0f;
        }

        /**
         * Primary Viz/Blink webpage scroll signal (physical px), compensated for Chromium's top-controls
         * viewport expansion via effectiveScrollY = newScrollYPx - lastRawControlsOffset:
         * - effDelta > 0 => scrolling DOWN into content -> hide BOTH bars at >= 26dp.
         * - effDelta < 0 => scrolling UP toward top -> absorb <= 12dp thumb-lift micro-jitter, reveal at >= 28dp.
         */
        void onWebPageScrollChanged(int newScrollYPx, int oldScrollYPx) {
            int effY = newScrollYPx - lastRawControlsOffset;
            int effDelta;
            if (lastEffYAtWebUpdate == Integer.MIN_VALUE) {
                effDelta = newScrollYPx - oldScrollYPx;
            } else {
                effDelta = effY - lastEffYAtWebUpdate;
            }
            lastEffYAtWebUpdate = effY;
            if (effDelta == 0) return;

            // Ignore huge instantaneous jumps (e.g. tab switch, anchor jump, page reload reset)
            if (Math.abs(effDelta) > dp(activity, 500)) {
                webAccumDownPx = 0f;
                webAccumUpPx = 0f;
                if (newScrollYPx <= 4 && lastRawControlsOffset >= -4) {
                    lastScrollDir = 1;
                    setBarsHiddenByScroll(false, "web-reset-top");
                }
                return;
            }

            long now = SystemClock.uptimeMillis();
            lastUserInteractionMs = now;
            Window w = activity.getWindow();
            View decor = (w != null) ? w.getDecorView() : null;
            if (shouldForceShowBars(decor)) {
                webAccumDownPx = 0f;
                webAccumUpPx = 0f;
                setBarsHiddenByScroll(false, "web-forceShow");
                return;
            }

            float hideThresh = dpF(activity, HIDE_SCROLL_THRESHOLD_DP);
            float deadzone = dpF(activity, UP_JITTER_DEADZONE_DP);
            float revealThresh = dpF(activity, REVEAL_SCROLL_THRESHOLD_DP);

            if (effDelta > 0) {
                // Scrolling DOWN into webpage content
                webAccumDownPx += effDelta;
                if (webAccumDownPx > dpF(activity, 6f)) {
                    webAccumUpPx = 0f;
                }
                if (webAccumDownPx >= hideThresh) {
                    lastScrollDir = -1;
                    lastHideTriggerMs = now;
                    webAccumDownPx = 0f;
                    webAccumUpPx = 0f;
                    setBarsHiddenByScroll(true, "web-down(effY=" + effY + ")");
                }
            } else {
                // Scrolling UP toward top of page: enforce 12dp micro-jitter hysteresis deadzone
                int upDelta = -effDelta;
                webAccumUpPx += upDelta;
                if (webAccumUpPx > deadzone) {
                    webAccumDownPx = 0f;
                }
                if (barsHiddenByScroll && (webAccumUpPx >= revealThresh || (newScrollYPx <= 4 && webAccumUpPx >= deadzone))) {
                    lastScrollDir = 1;
                    webAccumUpPx = 0f;
                    webAccumDownPx = 0f;
                    setBarsHiddenByScroll(false, "web-up(effY=" + effY + ")");
                }
            }
        }

        private boolean isPointInsideView(View v, float rawX, float rawY) {
            if (v == null || !v.isShown() || v.getWidth() <= 0 || v.getHeight() <= 0) return false;
            v.getLocationOnScreen(hitLoc);
            return rawX >= hitLoc[0] && rawX <= hitLoc[0] + v.getWidth()
                    && rawY >= hitLoc[1] && rawY <= hitLoc[1] + v.getHeight();
        }

        void onWindowTouchEvent(MotionEvent ev) {
            if (ev == null) return;
            long now = SystemClock.uptimeMillis();
            int action = ev.getActionMasked();
            float rx = ev.getRawX();
            float ry = ev.getRawY();
            if (isInViewBackdropMode()) {
                viewBackdropDirty = true;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                lastUserInteractionMs = now;
                isFingerDown = true;
                touchLockedVertical = false;
                touchStartX = rx;
                touchStartY = ry;
                touchLastY = ry;
                touchAccumDownPx = 0f;
                touchAccumUpPx = 0f;
                Window w = activity.getWindow();
                View decor = (w != null) ? w.getDecorView() : null;
                boolean onChromeUi = isPointInsideView(this, rx, ry)
                        || (flyoutOverlay != null && flyoutOverlay.isOpen())
                        || isPointInsideView(cachedControlContainer, rx, ry)
                        || shouldForceShowBars(decor);
                touchIgnoreGesture = onChromeUi;
                return;
            }
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                // Multi-touch (pinch-to-zoom) should not toggle bars
                touchIgnoreGesture = true;
                return;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float netDy = ry - touchStartY;
                if (Math.abs(netDy) > dpF(activity, 12f)) {
                    Log.i(TAG, "touchSwipe netDy=" + Math.round(netDy) + " ign=" + touchIgnoreGesture
                            + " locked=" + touchLockedVertical + " hidden=" + barsHiddenByScroll
                            + " rawTop=" + lastRawControlsOffset);
                }
                isFingerDown = false;
                lastTouchUpMs = now;
                touchLockedVertical = false;
                touchAccumDownPx = 0f;
                touchAccumUpPx = 0f;
                return;
            }
            if (action != MotionEvent.ACTION_MOVE || touchIgnoreGesture) return;

            float dy = ry - touchLastY;
            touchLastY = ry;
            float totalDx = Math.abs(rx - touchStartX);
            float totalDy = Math.abs(ry - touchStartY);
            if (!touchLockedVertical) {
                float slop = dpF(activity, 7f);
                if (totalDx > slop && totalDx > totalDy * 1.15f) {
                    touchIgnoreGesture = true;
                    return;
                }
                if (totalDy > slop && totalDy >= totalDx) {
                    touchLockedVertical = true;
                    // Include the initial slop movement so moderate scrolls trigger promptly
                    dy = ry - touchStartY;
                } else {
                    return;
                }
            }

            Window w = activity.getWindow();
            View decor = (w != null) ? w.getDecorView() : null;
            if (shouldForceShowBars(decor)) {
                touchAccumDownPx = 0f;
                touchAccumUpPx = 0f;
                setBarsHiddenByScroll(false, "touch-forceShow");
                return;
            }

            float hideThresh = dpF(activity, HIDE_SCROLL_THRESHOLD_DP);
            float deadzone = dpF(activity, UP_JITTER_DEADZONE_DP);
            float revealThresh = dpF(activity, REVEAL_SCROLL_THRESHOLD_DP);

            if (dy < 0f) {
                // Finger swiping UP = webpage scrolling DOWN
                touchAccumDownPx += (-dy);
                if (touchAccumDownPx > dpF(activity, 6f)) {
                    touchAccumUpPx = 0f;
                }
                if (touchAccumDownPx >= hideThresh) {
                    lastScrollDir = -1;
                    lastHideTriggerMs = now;
                    touchAccumDownPx = 0f;
                    touchAccumUpPx = 0f;
                    setBarsHiddenByScroll(true, "touch-down");
                }
            } else if (dy > 0f) {
                // Finger swiping DOWN = webpage scrolling UP (absorb <=12dp upward thumb-lift jitter!)
                touchAccumUpPx += dy;
                if (touchAccumUpPx > deadzone) {
                    touchAccumDownPx = 0f;
                }
                if (barsHiddenByScroll && touchAccumUpPx >= revealThresh) {
                    lastScrollDir = 1;
                    touchAccumUpPx = 0f;
                    touchAccumDownPx = 0f;
                    setBarsHiddenByScroll(false, "touch-up");
                }
            }
        }

        /** Exact underdamped spring curve matching Compose spring(dampingRatio = 0.88f, stiffness = 460f). */
        private static final Interpolator ECHO_SPRING_INTERPOLATOR = new Interpolator() {
            @Override
            public float getInterpolation(float input) {
                if (input <= 0f) return 0f;
                if (input >= 1f) return 1f;
                double t = input * 0.35;
                double wn = Math.sqrt(460.0);
                double zeta = 0.88;
                double wd = wn * Math.sqrt(1.0 - zeta * zeta);
                double env = Math.exp(-zeta * wn * t);
                double val = 1.0 - env * (Math.cos(wd * t) + (zeta / Math.sqrt(1.0 - zeta * zeta)) * Math.sin(wd * t));
                return (float) val;
            }
        };

        private boolean shouldForceShowBars(View decor) {
            if (decor == null) return true;
            if (flyoutOverlay != null && flyoutOverlay.isOpen()) return true;
            View tabHolder = (cachedTabHolder != null && cachedTabHolder.isAttachedToWindow())
                    ? cachedTabHolder : decor.findViewById(ID_TAB_SWITCHER_HOLDER);
            if (tabHolder != null && tabHolder.getVisibility() == View.VISIBLE
                    && tabHolder instanceof ViewGroup && ((ViewGroup) tabHolder).getChildCount() > 0) {
                return true;
            }
            View urlBar = (cachedUrlBar != null && cachedUrlBar.isAttachedToWindow())
                    ? cachedUrlBar : decor.findViewById(ID_URL_BAR);
            if (urlBar != null && urlBar.hasFocus()) return true;
            View ntp = decor.findViewById(ID_NTP_CONTENT);
            if (ntp != null && ntp.isShown()) return true;
            return false;
        }

        /** Called on every Chromium BrowserControlsManager.notifyControlOffsetChanged() scroll frame. */
        void onChromiumControlsOffset(View controlContainer, int rawTopOffsetPx) {
            cachedControlContainer = controlContainer;
            long now = SystemClock.uptimeMillis();
            if (rawTopOffsetPx != lastRawControlsOffset) {
                lastUserInteractionMs = now;
            }
            Window w = activity.getWindow();
            View decor = (w != null) ? w.getDecorView() : null;

            if (shouldForceShowBars(decor)) {
                controlsAccumDownPx = 0f;
                controlsAccumUpPx = 0f;
                lastRawControlsOffset = rawTopOffsetPx;
                setBarsHiddenByScroll(false, "controls-forceShow");
                if (Math.abs(controlContainer.getTranslationY() - currentTopTransY) > 0.5f) {
                    controlContainer.setTranslationY(currentTopTransY);
                }
                return;
            }

            int delta = rawTopOffsetPx - lastRawControlsOffset;
            lastRawControlsOffset = rawTopOffsetPx;

            // If the user's most recent scroll gesture was DOWNWARD (lastScrollDir < 0) and their finger is
            // NOT actively scrolling up, ANY positive delta (or bounce back to rawTopOffsetPx == 0)
            // from Chromium's BrowserControlsManager is a synthetic compositor snap-back and MUST NOT cancel hiding!
            boolean activeFinger = isFingerDown || webFingerDown;
            boolean syntheticSnapBack = barsHiddenByScroll
                    && lastScrollDir < 0
                    && !activeFinger;

            // Only auto-reveal at scroll-top (rawTopOffsetPx >= -2) when NOT a synthetic post-downward snap-back
            if (rawTopOffsetPx >= -2) {
                controlsAccumDownPx = 0f;
                controlsAccumUpPx = 0f;
                if (!syntheticSnapBack && lastScrollDir >= 0) {
                    setBarsHiddenByScroll(false, "controls-top");
                }
                if (Math.abs(controlContainer.getTranslationY() - currentTopTransY) > 0.5f) {
                    controlContainer.setTranslationY(currentTopTransY);
                }
                return;
            }

            float hideThresh = dpF(activity, HIDE_SCROLL_THRESHOLD_DP);
            float deadzone = dpF(activity, UP_JITTER_DEADZONE_DP);
            float revealThresh = dpF(activity, REVEAL_SCROLL_THRESHOLD_DP);
            int headerH = controlContainer.getHeight();

            if (delta < 0) {
                // Scrolling down
                controlsAccumDownPx += (-delta);
                if (controlsAccumDownPx > dpF(activity, 6f)) {
                    controlsAccumUpPx = 0f;
                }
                if (!barsHiddenByScroll && (controlsAccumDownPx >= hideThresh
                        || (headerH > 0 && rawTopOffsetPx <= -(headerH - dp(activity, 12))))) {
                    controlsAccumDownPx = 0f;
                    controlsAccumUpPx = 0f;
                    lastScrollDir = -1;
                    lastHideTriggerMs = now;
                    setBarsHiddenByScroll(true, "controls-down");
                }
            } else if (delta > 0 && !syntheticSnapBack && (activeFinger || lastScrollDir > 0)) {
                // Scrolling up: enforce 12dp micro-jitter deadzone so finger lift never cancels hide
                controlsAccumUpPx += delta;
                if (controlsAccumUpPx > deadzone) {
                    controlsAccumDownPx = 0f;
                }
                if (barsHiddenByScroll && controlsAccumUpPx >= revealThresh) {
                    controlsAccumUpPx = 0f;
                    controlsAccumDownPx = 0f;
                    lastScrollDir = 1;
                    setBarsHiddenByScroll(false, "controls-up");
                }
            }

            if (Math.abs(controlContainer.getTranslationY() - currentTopTransY) > 0.5f) {
                controlContainer.setTranslationY(currentTopTransY);
            }
        }

        void setBarsHiddenByScroll(boolean hide) {
            setBarsHiddenByScroll(hide, "direct");
        }

        void setBarsHiddenByScroll(boolean hide, String reason) {
            if (this.barsHiddenByScroll == hide) return;
            this.barsHiddenByScroll = hide;
            Log.i(TAG, "autoHide -> " + hide + " (" + reason + ") rawTop=" + lastRawControlsOffset);
            if (hide && flyoutOverlay != null && flyoutOverlay.isOpen()) {
                flyoutOverlay.dismiss();
            }
            final View cc = cachedControlContainer;
            if (!hide && cc != null && cc.getVisibility() == View.INVISIBLE) {
                cc.setVisibility(View.VISIBLE);
            }
            int headerH = (cc != null && cc.getHeight() > 0) ? cc.getHeight() : dp(activity, 108);
            int navH = (getHeight() > 0) ? getHeight() : (dp(activity, 58 + 10) + currentBottomPadPx);

            final float startTop = currentTopTransY;
            final float endTop = hide ? -(headerH + dpF(activity, 12f)) : 0f;
            final float startBot = currentBotTransY;
            final float endBot = hide ? (navH + dpF(activity, 16f)) : 0f;

            if (barsAutoHideAnim != null && barsAutoHideAnim.isRunning()) {
                barsAutoHideAnim.cancel();
            }
            barsAutoHideAnim = ValueAnimator.ofFloat(0f, 1f);
            barsAutoHideAnim.setDuration(hide ? 260L : 340L);
            barsAutoHideAnim.setInterpolator(hide ? new DecelerateInterpolator(1.6f) : ECHO_SPRING_INTERPOLATOR);
            barsAutoHideAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator anim) {
                    float f = (Float) anim.getAnimatedValue();
                    currentTopTransY = startTop + (endTop - startTop) * f;
                    currentBotTransY = startBot + (endBot - startBot) * f;
                    if (cachedControlContainer != null) {
                        cachedControlContainer.setTranslationY(currentTopTransY);
                    }
                    setTranslationY(currentBotTransY);
                    blurController.update();
                }
            });
            barsAutoHideAnim.start();
        }

        private final ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                Window w = activity.getWindow();
                if (w != null) {
                    View decor = w.getDecorView();
                    long now = SystemClock.uptimeMillis();
                    boolean stateChanged = false;

                    if (cachedControlContainer == null || !cachedControlContainer.isAttachedToWindow()) {
                        cachedControlContainer = decor.findViewById(ID_CONTROL_CONTAINER);
                        if (cachedControlContainer != null) {
                            blurController.setSlot(SurfaceViewBlurController.SLOT_ADDRESS_BAR, cachedControlContainer, -1f, 16);
                            stateChanged = true;
                        }
                    }
                    if (cachedTabHolder == null || !cachedTabHolder.isAttachedToWindow()) {
                        cachedTabHolder = decor.findViewById(ID_TAB_SWITCHER_HOLDER);
                    }
                    if (cachedUrlBar == null || !cachedUrlBar.isAttachedToWindow()) {
                        View uv = decor.findViewById(ID_URL_BAR);
                        cachedUrlBar = (uv instanceof TextView) ? (TextView) uv : null;
                    }

                    View tabHolder = cachedTabHolder;
                    boolean nowInTabSwitcher = (tabHolder != null && tabHolder.getVisibility() == View.VISIBLE
                            && tabHolder instanceof ViewGroup && ((ViewGroup) tabHolder).getChildCount() > 0);
                    if (nowInTabSwitcher != inTabSwitcher) {
                        inTabSwitcher = nowInTabSwitcher;
                        stateChanged = true;
                        if (inTabSwitcher) {
                            selectedCenterIndex = 2; // Tabs active
                            viewBackdropDirty = true;
                            lastViewScrollHash = 0;
                        } else {
                            viewBackdropValid = false;
                            viewBackdropDirty = true;
                            if (selectedCenterIndex == 2) selectedCenterIndex = 1; // Return to Home
                        }
                        leftCircleView.invalidate();
                        centerPillView.invalidate();
                        rightCircleView.invalidate();
                        if (flyoutOverlay != null) flyoutOverlay.invalidateCard();
                    }

                    // Detect New Tab Page (NTP) so BOTH bottom nav bar & flyout menu get real vibrant frosted glass blur on NTP!
                    View ntpContainer = !inTabSwitcher ? findActiveNtpContainer(decor) : null;
                    boolean nowInNtp = (ntpContainer != null);
                    if (nowInNtp != inNtp) {
                        inNtp = nowInNtp;
                        stateChanged = true;
                        viewBackdropValid = false;
                        viewBackdropDirty = true;
                        lastViewScrollHash = 0;
                        leftCircleView.invalidate();
                        centerPillView.invalidate();
                        rightCircleView.invalidate();
                        if (flyoutOverlay != null) flyoutOverlay.invalidateCard();
                    }
                    activeBackdropSource = inTabSwitcher ? tabHolder : (inNtp ? ntpContainer : null);

                    boolean isFullscreen = false;
                    if (Build.VERSION.SDK_INT >= 30) {
                        WindowInsets wi = decor.getRootWindowInsets();
                        if (wi != null && !wi.isVisible(WindowInsets.Type.statusBars())) {
                            isFullscreen = true;
                        }
                    }
                    if (isFullscreen != lastFullscreenState) {
                        lastFullscreenState = isFullscreen;
                        setFullscreenHidden(isFullscreen);
                        stateChanged = true;
                    }

                    boolean urlFocused = (cachedUrlBar != null && cachedUrlBar.hasFocus());
                    if (urlFocused != lastUrlFocused) {
                        lastUrlFocused = urlFocused;
                        if (urlFocused) lastUserInteractionMs = now;
                        stateChanged = true;
                    }
                    if (cachedUrlBar != null) {
                        CharSequence cs = cachedUrlBar.getText();
                        String urlStr = (cs != null) ? cs.toString() : "";
                        if (!urlStr.equals(lastObservedUrl)) {
                            lastObservedUrl = urlStr;
                            cachedIsMediaUrl = isVideoMediaUrl(urlStr);
                            lastUserInteractionMs = now;
                            stateChanged = true;
                        }
                    }

                    boolean flyoutOpen = (flyoutOverlay != null && (flyoutOverlay.isOpen() || flyoutOverlay.isAnimating()));
                    if (flyoutOpen != lastFlyoutOpen) {
                        lastFlyoutOpen = flyoutOpen;
                        if (flyoutOpen) {
                            lastUserInteractionMs = now;
                            if (isInViewBackdropMode()) viewBackdropDirty = true;
                        }
                        stateChanged = true;
                    }

                    if (stateChanged || (now - lastVideoCheckMs >= 350L)) {
                        lastVideoCheckMs = now;
                        boolean winKeepOn = (w.getAttributes() != null
                                && (w.getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
                        boolean viewKeepOn = !winKeepOn && !isInViewBackdropMode() && !cachedIsMediaUrl
                                && hasKeepScreenOnView(decor, 0);
                        boolean audioPlaying = false;
                        if (!cachedIsMediaUrl && !winKeepOn && !viewKeepOn && audioManager != null) {
                            try {
                                audioPlaying = audioManager.isMusicActive();
                            } catch (Throwable ignored) {}
                        }
                        cachedMediaActive = cachedIsMediaUrl || winKeepOn || viewKeepOn || audioPlaying;
                    }

                    // Suppress SurfaceControl GPU blur during video playback, fullscreen, Tab Switcher, or NTP
                    // (Tab Switcher & NTP use real-time hardware RenderNode vibrant frosted blur instead!)
                    boolean suppressBlur = isFullscreen
                            || isInViewBackdropMode()
                            || (!flyoutOpen && !urlFocused && (
                                    cachedIsMediaUrl
                                    || (cachedMediaActive && (now - lastUserInteractionMs >= 1200L))
                               ));
                    if (cachedMediaActive && !suppressBlur && !flyoutOpen && !urlFocused && !isInViewBackdropMode() && !isFullscreen) {
                        removeCallbacks(mediaBlurRecheckRunnable);
                        long waitMs = Math.max(100L, 1250L - (now - lastUserInteractionMs));
                        postDelayed(mediaBlurRecheckRunnable, waitMs);
                    }
                    blurController.setDarkMode(isDark);
                    blurController.setBlurSuppressed(suppressBlur);

                    if (barsHiddenByScroll && shouldForceShowBars(decor)) {
                        setBarsHiddenByScroll(false);
                    }
                    if (cachedControlContainer != null
                            && Math.abs(cachedControlContainer.getTranslationY() - currentTopTransY) > 0.5f) {
                        cachedControlContainer.setTranslationY(currentTopTransY);
                    }
                    if (Math.abs(getTranslationY() - currentBotTransY) > 0.5f) {
                        setTranslationY(currentBotTransY);
                    }

                    boolean prevBlurActive = blurController.isBlurActive();
                    blurController.update();
                    if (prevBlurActive != blurController.isBlurActive()) {
                        stateChanged = true;
                    }

                    long syncInterval = (urlFocused || isInViewBackdropMode()) ? 120L : 500L;
                    if (stateChanged || (now - lastToolbarSyncMs >= syncInterval)) {
                        lastToolbarSyncMs = now;
                        hideTopToolbarButtons(decor);
                        styleTopAddressBarGlass(activity, decor, isDark, blurController.isBlurActive());
                    }

                    // Drive RenderNode vibrant backdrop blur for BOTH Tab Switcher AND NTP strictly on
                    // entry / scroll / menu open / touch (ZERO 120fps idle loop -> 0% idle CPU!)
                    if (isInViewBackdropMode() && activeBackdropSource != null && !isRecordingViewBackdrop && isShown()) {
                        int scrollHash = computeViewBackdropScrollHash(activeBackdropSource);
                        boolean scrollChanged = (scrollHash != lastViewScrollHash);
                        boolean recentTouch = (now - lastUserInteractionMs < 450L);
                        if (!viewBackdropValid || viewBackdropDirty || scrollChanged
                                || (recentTouch && (now - lastViewBackdropMs >= 16L))) {
                            lastViewScrollHash = scrollHash;
                            recordViewBackdrop(activeBackdropSource);
                        }
                    }
                    return true;
                }
                blurController.update();
                return true;
            }
        };

        public CouiLiquidGlassNavBar(final Activity activity) {
            super(activity);
            this.activity = activity;
            this.isDark = isDarkMode(activity);
            this.blurController = new SurfaceViewBlurController(activity, this);
            AudioManager am = null;
            try {
                am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            } catch (Throwable ignored) {}
            this.audioManager = am;
            setBackground(null);
            setClipChildren(false);
            setClipToPadding(false);

            int padH = dp(activity, 16);
            int padT = dp(activity, 10);
            currentBottomPadPx = dp(activity, 24);
            setPadding(padH, padT, padH, currentBottomPadPx);

            int barHeight = dp(activity, 58);
            int circleSize = dp(activity, 58);
            int sideGap = dp(activity, 12);

            // 1. Detached Left Glass Circle (Back)
            leftCircleView = new AccessoryCircleView(activity, true);
            LayoutParams leftLp = new LayoutParams(circleSize, barHeight, Gravity.START | Gravity.BOTTOM);
            addView(leftCircleView, leftLp);

            // 2. Floating Center Glass Pill (3 items: Search, Home, Tabs)
            centerPillView = new CenterCapsuleView(activity);
            LayoutParams centerLp = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    barHeight,
                    Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM
            );
            centerLp.leftMargin = circleSize + sideGap;
            centerLp.rightMargin = circleSize + sideGap;
            addView(centerPillView, centerLp);

            // 3. Detached Right Glass Circle (Menu)
            rightCircleView = new AccessoryCircleView(activity, false);
            LayoutParams rightLp = new LayoutParams(circleSize, barHeight, Gravity.END | Gravity.BOTTOM);
            addView(rightCircleView, rightLp);

            // Calibrated crisp frosted Liquid Glass blur radius (10dp = ~32px at 520dpi)
            // matching navmenu-blur-in-gallery.mp4 so shapes & colors underneath remain softly legible!
            blurController.setSlot(SurfaceViewBlurController.SLOT_LEFT, leftCircleView, 0f, 10);
            blurController.setSlot(SurfaceViewBlurController.SLOT_CENTER, centerPillView, 0f, 10);
            blurController.setSlot(SurfaceViewBlurController.SLOT_RIGHT, rightCircleView, 0f, 10);

            setOnApplyWindowInsetsListener(new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    int navBottom;
                    boolean imeVisible;
                    boolean fullscreenNow = false;
                    if (Build.VERSION.SDK_INT >= 30) {
                        navBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                        imeVisible = insets.isVisible(WindowInsets.Type.ime());
                        fullscreenNow = !insets.isVisible(WindowInsets.Type.statusBars());
                    } else {
                        navBottom = insets.getSystemWindowInsetBottom();
                        imeVisible = navBottom > dp(v.getContext(), 120);
                    }
                    int hPad = dp(v.getContext(), 16);
                    int tPad = dp(v.getContext(), 10);
                    currentBottomPadPx = Math.max(dp(v.getContext(), 18), navBottom + dp(v.getContext(), 6));
                    v.setPadding(hPad, tPad, hPad, currentBottomPadPx);
                    setImeHidden(imeVisible);
                    lastFullscreenState = fullscreenNow;
                    setFullscreenHidden(fullscreenNow);
                    if (flyoutOverlay != null) {
                        flyoutOverlay.updateAnchorMargin(currentBottomPadPx + dp(v.getContext(), 58 + 10));
                    }
                    return insets;
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(mediaBlurRecheckRunnable);
            getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            blurController.releaseAll();
            super.onDetachedFromWindow();
        }

        private void setImeHidden(boolean hide) {
            if (this.hiddenByIme == hide) return;
            this.hiddenByIme = hide;
            if (hide && flyoutOverlay != null && flyoutOverlay.isOpen()) {
                flyoutOverlay.dismiss();
            }
            View[] pieces = {leftCircleView, centerPillView, rightCircleView};
            for (final View piece : pieces) {
                if (hide) {
                    piece.animate()
                            .scaleX(0.8f)
                            .scaleY(0.8f)
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    if (hiddenByIme) piece.setVisibility(View.GONE);
                                }
                            })
                            .start();
                } else {
                    piece.setVisibility(View.VISIBLE);
                    piece.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(280)
                            .setInterpolator(new OvershootInterpolator(1.1f))
                            .start();
                }
            }
        }

        public void updateTheme() {
            boolean newDark = isDarkMode(activity);
            if (newDark != isDark) {
                isDark = newDark;
                leftCircleView.invalidate();
                centerPillView.invalidate();
                rightCircleView.invalidate();
                if (flyoutOverlay != null) {
                    flyoutOverlay.updateTheme(isDark);
                }
            }
        }

        private void toggleGlassFlyoutMenu() {
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) return;
            if (flyoutOverlay == null) {
                View existing = content.findViewWithTag(FLYOUT_TAG);
                if (existing instanceof CouiGlassFlyoutOverlay) {
                    flyoutOverlay = (CouiGlassFlyoutOverlay) existing;
                    flyoutOverlay.setNavBar(this);
                } else {
                    flyoutOverlay = new CouiGlassFlyoutOverlay(activity, isDark, blurController, this);
                    flyoutOverlay.setTag(FLYOUT_TAG);
                    content.addView(flyoutOverlay, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                }
            } else {
                flyoutOverlay.setNavBar(this);
            }
            flyoutOverlay.updateTheme(isDark);
            flyoutOverlay.updateAnchorMargin(currentBottomPadPx + dp(activity, 58 + 10));
            flyoutOverlay.bringToFront();
            if (flyoutOverlay.isOpen()) {
                flyoutOverlay.dismiss();
            } else {
                flyoutOverlay.show();
            }
        }

        private void dispatchNavAction(int action) {
            performCouiHaptic(this);
            try {
                View decor = activity.getWindow().getDecorView();
                hideTopToolbarButtons(decor);
                if (action != ACTION_MENU && flyoutOverlay != null && flyoutOverlay.isOpen()) {
                    flyoutOverlay.dismiss();
                }
                switch (action) {
                    case ACTION_BACK:
                        activity.onBackPressed();
                        break;
                    case ACTION_SEARCH:
                        View urlBar = decor.findViewById(ID_URL_BAR);
                        if (urlBar != null) {
                            urlBar.performClick();
                            urlBar.requestFocus();
                        } else {
                            View locBar = decor.findViewById(ID_LOCATION_BAR);
                            if (locBar != null) locBar.performClick();
                        }
                        break;
                    case ACTION_HOME:
                        View homeBtn = decor.findViewById(ID_HOME_BUTTON);
                        if (homeBtn != null) {
                            homeBtn.performClick();
                        }
                        break;
                    case ACTION_TABS:
                        View tabBtn = decor.findViewById(ID_TAB_SWITCHER_BUTTON);
                        if (tabBtn != null) {
                            tabBtn.performClick();
                        }
                        break;
                    case ACTION_MENU:
                        toggleGlassFlyoutMenu();
                        break;
                    case ACTION_EXTENSIONS:
                        if (!dispatchChromiumMenuAction(activity, MENU_EXTENSIONS)) {
                            toggleGlassFlyoutMenu();
                        }
                        break;
                }
            } catch (Throwable t) {
                Log.w(TAG, "dispatchNavAction failed for action=" + action, t);
            }
        }

        /**
         * Detached 56dp x 56dp Liquid Glass Circle (Left = Back, Right = Menu).
         * Uses bright frosted glass finish over SurfaceViewBlurController's real 120fps SurfaceControl blur.
         */
        private final class AccessoryCircleView extends View {
            private final boolean isBack;
            private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint spotlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF circleRect = new RectF();
            private float touchX = -1f;
            private float touchY = -1f;
            private float spotlightAlpha = 0f;
            private float menuMorph = 0f; // 0 = ≡ (Fluent navigation), 1 = × (Fluent dismiss) driven by Rive C++ state machine
            private boolean longPressFired = false;
            private LinearGradient cachedSheen;
            private LinearGradient cachedRim;
            private int cachedW = -1, cachedH = -1;
            private boolean cachedDark = false;
            private final Paint iconFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isBack) {
                        longPressFired = true;
                        dispatchNavAction(ACTION_EXTENSIONS);
                    }
                }
            };

            void setMenuMorphProgress(float m) {
                if (!isBack && Math.abs(menuMorph - m) > 0.002f) {
                    menuMorph = m;
                    invalidate();
                }
            }

            AccessoryCircleView(Context ctx, boolean isBack) {
                super(ctx);
                this.isBack = isBack;
                setBackground(null);
                setElevation(dpF(ctx, 12f));
                if (Build.VERSION.SDK_INT >= 28) {
                    setOutlineSpotShadowColor(0x38000000);
                    setOutlineAmbientShadowColor(0x20000000);
                }
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        int d = Math.min(view.getWidth(), view.getHeight());
                        outline.setOval(0, 0, d, d);
                        outline.setAlpha(1.0f);
                    }
                });
                setClipToOutline(false);

                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(dpF(ctx, 1.0f));

                iconPaint.setStyle(Paint.Style.STROKE);
                iconPaint.setStrokeWidth(dpF(ctx, 2.0f));
                iconPaint.setStrokeCap(Paint.Cap.ROUND);
                iconPaint.setStrokeJoin(Paint.Join.ROUND);
                iconFillPaint.setStyle(Paint.Style.FILL);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0) return;

                float radius = Math.min(w, h) * 0.5f;
                circleRect.set(0.75f, 0.75f, w - 0.75f, h - 0.75f);

                // 1. Vibrant luminous frosted Liquid Glass fill over OplusBlurParam/SurfaceControl blur
                //    OR live vibrant RenderNode backdrop blur over Tab Switcher & NTP!
                boolean viewBlur = drawViewBackdropBlur(canvas, this, circleRect, radius);
                boolean realBlur = viewBlur || (!isInViewBackdropMode() && blurController.isBlurActive());
                if (realBlur) {
                    // Airy luminous glaze (lets saturated pastel colors glow through like gallery-navigation-menu.jpg!)
                    surfacePaint.setColor(isDark ? 0x641C1C20 : 0x56FFFFFF);
                } else {
                    surfacePaint.setColor(isDark ? 0xD81E1E20 : 0xEBF8F9FB);
                }
                canvas.drawRoundRect(circleRect, radius, radius, surfacePaint);

                if (w != cachedW || h != cachedH || isDark != cachedDark) {
                    cachedW = w;
                    cachedH = h;
                    cachedDark = isDark;
                    cachedSheen = new LinearGradient(
                            0, 0, w * 0.55f, h,
                            isDark
                                    ? new int[]{0x2CFFFFFF, 0x08FFFFFF, 0x10000000}
                                    : new int[]{0x66FFFFFF, 0x1AFFFFFF, 0x16FFFFFF},
                            new float[]{0f, 0.52f, 1f},
                            Shader.TileMode.CLAMP
                    );
                    cachedRim = new LinearGradient(
                            0, 0, w, h,
                            isDark
                                    ? new int[]{0x84FFFFFF, 0x26FFFFFF, 0x58FFFFFF}
                                    : new int[]{0xFFFFFFFF, 0x62FFFFFF, 0xF2FFFFFF},
                            new float[]{0f, 0.5f, 1f},
                            Shader.TileMode.CLAMP
                    );
                }
                sheenPaint.setShader(cachedSheen);
                canvas.drawRoundRect(circleRect, radius, radius, sheenPaint);

                // 2. Touch spotlight
                if (spotlightAlpha > 0.01f && touchX >= 0f) {
                    float spotR = dpF(getContext(), 44f);
                    int spotCol = Color.argb((int) ((isDark ? 68 : 64) * spotlightAlpha), 255, 255, 255);
                    RadialGradient rg = new RadialGradient(
                            touchX, touchY, spotR,
                            new int[]{spotCol, 0x00FFFFFF},
                            new float[]{0f, 1f},
                            Shader.TileMode.CLAMP
                    );
                    spotlightPaint.setShader(rg);
                    canvas.drawRoundRect(circleRect, radius, radius, spotlightPaint);
                }

                // 3. Subtle outer hairline contour + crisp luminous specular rim
                borderPaint.setShader(null);
                borderPaint.setStrokeWidth(dpF(getContext(), 0.6f));
                borderPaint.setColor(isDark ? 0x28FFFFFF : 0x14000000);
                canvas.drawRoundRect(circleRect, radius, radius, borderPaint);

                circleRect.inset(dpF(getContext(), 0.8f), dpF(getContext(), 0.8f));
                borderPaint.setStrokeWidth(dpF(getContext(), 1.15f));
                borderPaint.setShader(cachedRim);
                canvas.drawRoundRect(circleRect, radius - dpF(getContext(), 0.8f), radius - dpF(getContext(), 0.8f), borderPaint);

                // 4. Official Microsoft Fluent UI 24dp Icons + Rive C++ interactive ≡ <-> × morph
                int fgCol = isDark ? 0xF5FFFFFF : 0xFF141414;
                iconFillPaint.setColor(fgCol);
                iconPaint.setColor(fgCol);
                float cx = w * 0.5f;
                float cy = h * 0.5f;
                if (isBack) {
                    FluentIcons.drawCentered(canvas, FluentIcons.BACK, cx, cy, dpF(getContext(), 23.0f), iconFillPaint);
                } else if (menuMorph <= 0.01f) {
                    FluentIcons.drawCentered(canvas, FluentIcons.MENU_REGULAR, cx, cy, dpF(getContext(), 23.0f), iconFillPaint);
                } else if (menuMorph >= 0.99f) {
                    FluentIcons.drawCentered(canvas, FluentIcons.DISMISS, cx, cy, dpF(getContext(), 21.5f), iconFillPaint);
                } else {
                    // Smooth vector morph driven by libtitanium_rive.so state machine: ≡ bars rotate & converge into ×
                    float t = Math.max(0f, Math.min(1f, menuMorph));
                    float halfLen = dpF(getContext(), 9.0f - 1.2f * t);
                    float dy = dpF(getContext(), 5.8f * (1.0f - t));
                    float angle = 45.0f * t;
                    iconPaint.setStrokeWidth(dpF(getContext(), 1.85f));
                    iconPaint.setAlpha(255);
                    // Top bar -> '\'
                    int s1 = canvas.save();
                    canvas.translate(cx, cy - dy);
                    canvas.rotate(angle);
                    canvas.drawLine(-halfLen, 0f, halfLen, 0f, iconPaint);
                    canvas.restoreToCount(s1);
                    // Bottom bar -> '/'
                    int s2 = canvas.save();
                    canvas.translate(cx, cy + dy);
                    canvas.rotate(-angle);
                    canvas.drawLine(-halfLen, 0f, halfLen, 0f, iconPaint);
                    canvas.restoreToCount(s2);
                    // Middle bar contracts & fades out cleanly
                    float midScale = Math.max(0f, 1.0f - t * 1.45f);
                    if (midScale > 0.02f) {
                        iconPaint.setAlpha(Math.round(255 * midScale));
                        canvas.drawLine(cx - halfLen * midScale, cy, cx + halfLen * midScale, cy, iconPaint);
                    }
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                touchX = event.getX();
                touchY = event.getY();
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        longPressFired = false;
                        spotlightAlpha = 1f;
                        animate().scaleX(0.91f).scaleY(0.91f).setDuration(120).start();
                        if (!isBack) {
                            postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                        }
                        invalidate();
                        return true;
                    case MotionEvent.ACTION_UP:
                        removeCallbacks(longPressRunnable);
                        spotlightAlpha = 0f;
                        animate().scaleX(1f).scaleY(1f).setDuration(240)
                                .setInterpolator(new OvershootInterpolator(1.25f)).start();
                        invalidate();
                        if (!longPressFired) {
                            dispatchNavAction(isBack ? ACTION_BACK : ACTION_MENU);
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        removeCallbacks(longPressRunnable);
                        spotlightAlpha = 0f;
                        animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                        invalidate();
                        return true;
                }
                return super.onTouchEvent(event);
            }
        }

        /**
         * Floating Center Frosted Glass Pill Capsule (height = 56dp, 3 items: Search, Home, Tabs).
         * Matches navmenu-gallery.jpg 1:1:
         * - Bright airy frosted Liquid Glass over SurfaceViewBlurController's real hardware SurfaceControl blur.
         * - ZERO permanent circle/pill blob at rest behind the selected tab.
         * - Selected item: filled solid #191919 black icon + bold label (#F2FFFFFF dark).
         * - Unselected items: crisp outline #666666 icon + regular label (#99FFFFFF dark).
         * - Transient sliding glass highlight strictly while actively pressing/dragging, fading smoothly to 0 on release.
         */
        private final class CenterCapsuleView extends View {
            private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint spotlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint dropletFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint dropletRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint iconStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint iconFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint iconCutoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF capsuleRect = new RectF();
            private final RectF dropletRect = new RectF();

            private float dropletCenterX = -1f;
            private float dropletScaleX = 1f;
            private float dropletScaleY = 1f;
            private float dropletAlpha = 0f; // 0 at rest — NO permanent circle/pill behind active tab!
            private float touchX = -1f;
            private float touchY = -1f;
            private float spotlightAlpha = 0f;
            private boolean dragging = false;
            private float downX = 0f;
            private final int touchSlop;
            private ValueAnimator dropletAnimator;
            private LinearGradient cachedSheen;
            private LinearGradient cachedRim;
            private int cachedW = -1, cachedH = -1;
            private boolean cachedDark = false;

            CenterCapsuleView(Context ctx) {
                super(ctx);
                this.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
                setBackground(null);
                setElevation(dpF(ctx, 12f));
                if (Build.VERSION.SDK_INT >= 28) {
                    setOutlineSpotShadowColor(0x38000000);
                    setOutlineAmbientShadowColor(0x20000000);
                }
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        int r = view.getHeight() / 2;
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                        outline.setAlpha(1.0f);
                    }
                });
                setClipToOutline(false);

                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(dpF(ctx, 1.0f));

                dropletRimPaint.setStyle(Paint.Style.STROKE);
                dropletRimPaint.setStrokeWidth(dpF(ctx, 1.0f));

                iconStrokePaint.setStyle(Paint.Style.STROKE);
                iconStrokePaint.setStrokeCap(Paint.Cap.ROUND);
                iconStrokePaint.setStrokeJoin(Paint.Join.ROUND);

                iconFillPaint.setStyle(Paint.Style.FILL);

                iconCutoutPaint.setStyle(Paint.Style.STROKE);
                iconCutoutPaint.setStrokeCap(Paint.Cap.ROUND);
                iconCutoutPaint.setStrokeJoin(Paint.Join.ROUND);

                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTextSize(dpF(ctx, 10.5f));
                textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }

            private float itemCenterX(int index) {
                float pad = dpF(getContext(), 10f);
                float avail = getWidth() - pad * 2f;
                float slot = avail / CENTER_ITEM_COUNT;
                return pad + slot * (index + 0.5f);
            }

            private float itemSlotWidth() {
                float pad = dpF(getContext(), 10f);
                return Math.max(1f, (getWidth() - pad * 2f) / CENTER_ITEM_COUNT);
            }

            private float clampDropletCenterX(float cx, float sx) {
                float pad = dpF(getContext(), 5f);
                float halfW = (itemSlotWidth() * 0.46f) * sx;
                float minX = pad + halfW;
                float maxX = getWidth() - pad - halfW;
                if (minX > maxX) return getWidth() * 0.5f;
                return Math.max(minX, Math.min(maxX, cx));
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (w > 0 && h > 0) {
                    dropletCenterX = itemCenterX(selectedCenterIndex);
                }
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0) return;

                if (dropletCenterX < 0f) {
                    dropletCenterX = itemCenterX(selectedCenterIndex);
                }

                float radius = h * 0.5f;
                capsuleRect.set(0.75f, 0.75f, w - 0.75f, h - 0.75f);

                // 1. Vibrant luminous frosted Liquid Glass capsule fill
                //    (supports SurfaceControl OplusBlurParam over webpages & vibrant RenderNode blur over Tab Switcher + NTP!)
                boolean viewBlur = drawViewBackdropBlur(canvas, this, capsuleRect, radius);
                boolean realBlur = viewBlur || (!isInViewBackdropMode() && blurController.isBlurActive());
                if (realBlur) {
                    surfacePaint.setColor(isDark ? 0x641C1C20 : 0x56FFFFFF);
                } else {
                    surfacePaint.setColor(isDark ? 0xD81E1E20 : 0xEBF8F9FB);
                }
                canvas.drawRoundRect(capsuleRect, radius, radius, surfacePaint);

                if (w != cachedW || h != cachedH || isDark != cachedDark) {
                    cachedW = w;
                    cachedH = h;
                    cachedDark = isDark;
                    cachedSheen = new LinearGradient(
                            0, 0, w * 0.45f, h,
                            isDark
                                    ? new int[]{0x2CFFFFFF, 0x08FFFFFF, 0x10000000}
                                    : new int[]{0x66FFFFFF, 0x1AFFFFFF, 0x16FFFFFF},
                            new float[]{0f, 0.52f, 1f},
                            Shader.TileMode.CLAMP
                    );
                    cachedRim = new LinearGradient(
                            0, 0, w, h,
                            isDark
                                    ? new int[]{0x84FFFFFF, 0x26FFFFFF, 0x58FFFFFF}
                                    : new int[]{0xFFFFFFFF, 0x62FFFFFF, 0xF2FFFFFF},
                            new float[]{0f, 0.5f, 1f},
                            Shader.TileMode.CLAMP
                    );
                }
                sheenPaint.setShader(cachedSheen);
                canvas.drawRoundRect(capsuleRect, radius, radius, sheenPaint);

                // 2. Touch spotlight under finger
                if (spotlightAlpha > 0.01f && touchX >= 0f) {
                    float spotR = dpF(getContext(), 60f);
                    int spotCol = Color.argb((int) ((isDark ? 64 : 58) * spotlightAlpha), 255, 255, 255);
                    RadialGradient rg = new RadialGradient(
                            touchX, touchY, spotR,
                            new int[]{spotCol, 0x00FFFFFF},
                            new float[]{0f, 1f},
                            Shader.TileMode.CLAMP
                    );
                    spotlightPaint.setShader(rg);
                    canvas.drawRoundRect(capsuleRect, radius, radius, spotlightPaint);
                }

                // 3. Subtle outer hairline contour + crisp luminous specular rim highlight
                borderPaint.setShader(null);
                borderPaint.setStrokeWidth(dpF(getContext(), 0.6f));
                borderPaint.setColor(isDark ? 0x28FFFFFF : 0x14000000);
                canvas.drawRoundRect(capsuleRect, radius, radius, borderPaint);

                capsuleRect.inset(dpF(getContext(), 0.8f), dpF(getContext(), 0.8f));
                borderPaint.setStrokeWidth(dpF(getContext(), 1.15f));
                borderPaint.setShader(cachedRim);
                canvas.drawRoundRect(capsuleRect, radius - dpF(getContext(), 0.8f), radius - dpF(getContext(), 0.8f), borderPaint);

                // 4. Transient Liquid Glass press/drag pill highlight ONLY during active touch (0 alpha at rest!)
                if (dropletAlpha > 0.01f) {
                    float slotW = itemSlotWidth();
                    float dropHalfW = (slotW * 0.46f) * dropletScaleX;
                    float dropHalfH = (h * 0.42f) * dropletScaleY;
                    float cy = h * 0.5f;
                    float cx = clampDropletCenterX(dropletCenterX, dropletScaleX);
                    dropletRect.set(cx - dropHalfW, cy - dropHalfH, cx + dropHalfW, cy + dropHalfH);
                    float dropR = dropletRect.height() * 0.5f;

                    int aFill = Math.round((isDark ? 56 : 115) * dropletAlpha);
                    dropletFillPaint.setShader(null);
                    dropletFillPaint.setColor(Color.argb(aFill, 255, 255, 255));
                    canvas.drawRoundRect(dropletRect, dropR, dropR, dropletFillPaint);

                    int aRim = Math.round((isDark ? 96 : 190) * dropletAlpha);
                    dropletRimPaint.setShader(null);
                    dropletRimPaint.setColor(Color.argb(aRim, 255, 255, 255));
                    canvas.drawRoundRect(dropletRect, dropR, dropR, dropletRimPaint);
                }

                // 5. Draw 3 center items (Search, Home, Tabs) with official Microsoft Fluent UI 24dp icons
                //    (*-24-filled active / *-24-regular default) and balanced ColorOS 17 Gallery vertical padding
                //    (labels raised cleanly away from bottom edge, matching gallery-navigation-menu.jpg 1:1!)
                int selectedColor = isDark ? 0xFAFFFFFF : 0xFF141414;
                int unselectedColor = isDark ? 0xB3FFFFFF : 0xFF4B4E54;
                float iconSizePx = dpF(getContext(), 22.0f);
                float iconCy = dpF(getContext(), 20.0f); // Tighter icon-to-label gap + balanced top/bottom padding
                float textY = h - dpF(getContext(), 11.4f); // ~11.4dp bottom clearance matching gallery-navigation-menu.jpg

                for (int i = 0; i < CENTER_ITEM_COUNT; i++) {
                    float icx = itemCenterX(i);
                    boolean isSel = (i == selectedCenterIndex);
                    int col = isSel ? selectedColor : unselectedColor;
                    iconFillPaint.setColor(col);

                    textPaint.setColor(col);
                    textPaint.setFakeBoldText(isSel);

                    int fluentId;
                    if (i == 0) {
                        fluentId = isSel ? FluentIcons.SEARCH_FILLED : FluentIcons.SEARCH_REGULAR;
                    } else if (i == 1) {
                        fluentId = isSel ? FluentIcons.HOME_FILLED : FluentIcons.HOME_REGULAR;
                    } else {
                        fluentId = isSel ? FluentIcons.TABS_FILLED : FluentIcons.TABS_REGULAR;
                    }
                    FluentIcons.drawCentered(canvas, fluentId, icx, iconCy, iconSizePx, iconFillPaint);
                    canvas.drawText(CENTER_LABELS[i], icx, textY, textPaint);
                }
            }

            private int indexAtX(float x) {
                float pad = dpF(getContext(), 10f);
                float avail = Math.max(1f, getWidth() - pad * 2f);
                int idx = (int) ((x - pad) / (avail / CENTER_ITEM_COUNT));
                return Math.max(0, Math.min(CENTER_ITEM_COUNT - 1, idx));
            }

            private void animateDropletRelease(final int targetIndex) {
                if (dropletAnimator != null) {
                    dropletAnimator.cancel();
                }
                final float startX = dropletCenterX < 0f ? itemCenterX(selectedCenterIndex) : dropletCenterX;
                final float endX = itemCenterX(targetIndex);
                final float dist = endX - startX;
                final float startAlpha = dropletAlpha;
                final float slotW = itemSlotWidth();
                final float stretch = 0.20f * Math.min(1f, Math.abs(dist) / (slotW * 2.0f));
                final OvershootInterpolator overshoot = new OvershootInterpolator(1.05f);

                dropletAnimator = ValueAnimator.ofFloat(0f, 1f);
                dropletAnimator.setDuration(300);
                dropletAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator anim) {
                        float t = (Float) anim.getAnimatedValue();
                        float s = (float) Math.sin(Math.PI * Math.min(t * 1.15f, 1f));
                        dropletScaleX = 1f + stretch * s;
                        dropletScaleY = 1f - stretch * 0.45f * s;
                        dropletCenterX = clampDropletCenterX(startX + dist * overshoot.getInterpolation(t), dropletScaleX);
                        // Smoothly fade transient touch lens back to 0f so NO static circle remains at rest!
                        dropletAlpha = startAlpha * Math.max(0f, 1f - t);
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
                        dropletAlpha = 0f;
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
                        if (dropletAnimator != null) dropletAnimator.cancel();
                        spotlightAlpha = 1f;
                        dropletAlpha = 1f;
                        int hit = indexAtX(event.getX());
                        dropletCenterX = itemCenterX(hit);
                        if (hit != selectedCenterIndex) {
                            selectedCenterIndex = hit;
                            performCouiHaptic(this);
                        }
                        invalidate();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragging && Math.abs(event.getX() - downX) > touchSlop) {
                            dragging = true;
                            dropletScaleX = 1.06f;
                            dropletScaleY = 1.05f;
                        }
                        if (dragging) {
                            dropletAlpha = 1f;
                            dropletCenterX = clampDropletCenterX(event.getX(), dropletScaleX);
                            int hover = indexAtX(event.getX());
                            if (hover != selectedCenterIndex) {
                                selectedCenterIndex = hover;
                                performCouiHaptic(this);
                            }
                            invalidate();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        int finalIdx = indexAtX(event.getX());
                        selectedCenterIndex = finalIdx;
                        dragging = false;
                        animateDropletRelease(finalIdx);
                        dispatchNavAction(finalIdx + 1);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        animateDropletRelease(selectedCenterIndex);
                        return true;
                }
                return super.onTouchEvent(event);
            }
        }
    }

    /**
     * ColorOS 17 Frosted Liquid Glass Flyout Menu anchored directly from the bottom-right Menu circle.
     * Matches correct-flyout.mp4: 20dp squircle frosted glass card, SurfaceControl hardware blur behind card,
     * bright specular rim, soft diffuse drop shadow, spring/bounce open & close from bottom-right Menu pivot.
     */
    public static final class CouiGlassFlyoutOverlay extends FrameLayout {
        private static final class MenuItemSpec {
            final int id;
            final String label;
            final int iconType;

            MenuItemSpec(int id, String label, int iconType) {
                this.id = id;
                this.label = label;
                this.iconType = iconType;
            }
        }

        private static final int ICON_NEW_TAB = 1;
        private static final int ICON_INCOGNITO = 2;
        private static final int ICON_BOOKMARKS = 3;
        private static final int ICON_HISTORY = 4;
        private static final int ICON_DOWNLOADS = 5;
        private static final int ICON_EXTENSIONS = 6;
        private static final int ICON_SHARE = 7;
        private static final int ICON_FIND = 8;
        private static final int ICON_DESKTOP = 9;
        private static final int ICON_SETTINGS = 10;
        private static final int ICON_FORWARD = 11;
        private static final int ICON_STAR = 12;
        private static final int ICON_SAVE = 13;
        private static final int ICON_RELOAD = 14;

        private static final MenuItemSpec[] MENU_ITEMS = {
            new MenuItemSpec(MENU_NEW_TAB, "New tab", ICON_NEW_TAB),
            new MenuItemSpec(MENU_NEW_INCOGNITO, "New Incognito tab", ICON_INCOGNITO),
            new MenuItemSpec(MENU_BOOKMARKS, "Bookmarks", ICON_BOOKMARKS),
            new MenuItemSpec(MENU_HISTORY, "History", ICON_HISTORY),
            new MenuItemSpec(MENU_DOWNLOADS, "Downloads", ICON_DOWNLOADS),
            new MenuItemSpec(MENU_EXTENSIONS, "Extensions", ICON_EXTENSIONS),
            new MenuItemSpec(MENU_SHARE, "Share", ICON_SHARE),
            new MenuItemSpec(MENU_FIND_IN_PAGE, "Find in page", ICON_FIND),
            new MenuItemSpec(MENU_DESKTOP_SITE, "Desktop site", ICON_DESKTOP),
            new MenuItemSpec(MENU_SETTINGS, "Settings", ICON_SETTINGS)
        };

        private final Activity activity;
        private final SurfaceViewBlurController blurController;
        private CouiLiquidGlassNavBar navBar;
        private final GlassFlyoutCardView cardView;
        private final LinearLayout listContainer;
        private final LinearLayout quickBar;
        private final View topDivider;
        private boolean isDark;
        private boolean open = false;
        private boolean animRunning = false;
        private long lastFrameNanos = 0L;
        private final TitaniumRiveEngine.MenuStateMachine stateMachine = new TitaniumRiveEngine.MenuStateMachine();
        private final float[] smOut = new float[5]; // [scale, transYFrac, alpha, iconMorph, rowCascade]

        private final Choreographer.FrameCallback menuSpringTick = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (!animRunning) return;
                float dt = (lastFrameNanos == 0L)
                        ? 0.008f
                        : Math.max(0.004f, Math.min(0.032f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f));
                lastFrameNanos = frameTimeNanos;

                boolean stillActive = stateMachine.advance(dt, smOut);
                float sc = Math.max(0.05f, smOut[0]);
                float ty = smOut[1] * dpF(activity, 18f);
                float al = smOut[2];
                float morph = smOut[3];

                int cw = cardView.getWidth();
                int ch = cardView.getHeight();
                if (cw > 0 && ch > 0) {
                    // Bottom-right anchor directly above the Menu circle (pivotX ~88%, pivotY = 100%)
                    cardView.setPivotX(cw * 0.88f);
                    cardView.setPivotY(ch);
                }
                cardView.setScaleX(sc);
                cardView.setScaleY(sc);
                cardView.setTranslationY(ty);
                cardView.setAlpha(al);
                cardView.invalidate();
                if (navBar != null) {
                    navBar.setMenuMorphProgress(morph);
                }
                if (blurController != null) {
                    blurController.update();
                }

                if (stillActive) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    animRunning = false;
                    if (!open) {
                        setVisibility(View.GONE);
                        if (navBar != null) navBar.setMenuMorphProgress(0f);
                        if (blurController != null) blurController.update();
                    } else {
                        cardView.setScaleX(1f);
                        cardView.setScaleY(1f);
                        cardView.setTranslationY(0f);
                        cardView.setAlpha(1f);
                        if (navBar != null) navBar.setMenuMorphProgress(1f);
                    }
                }
            }
        };

        private void startMenuSpringLoop() {
            if (animRunning) {
                Choreographer.getInstance().removeFrameCallback(menuSpringTick);
            }
            lastFrameNanos = 0L;
            animRunning = true;
            Choreographer.getInstance().postFrameCallback(menuSpringTick);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animRunning) {
                animRunning = false;
                Choreographer.getInstance().removeFrameCallback(menuSpringTick);
            }
            stateMachine.release();
            super.onDetachedFromWindow();
        }

        public boolean isAnimating() {
            return animRunning;
        }

        public CouiGlassFlyoutOverlay(Activity activity, boolean isDark, SurfaceViewBlurController blurController) {
            this(activity, isDark, blurController, null);
        }

        public CouiGlassFlyoutOverlay(Activity activity, boolean isDark, SurfaceViewBlurController blurController,
                CouiLiquidGlassNavBar navBar) {
            super(activity);
            this.activity = activity;
            this.isDark = isDark;
            this.blurController = blurController;
            this.navBar = navBar;
            setVisibility(View.GONE);
            setClickable(true);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClipChildren(false);
            setClipToPadding(false);
            setBackgroundColor(Color.TRANSPARENT);

            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });

            cardView = new GlassFlyoutCardView(activity);
            cardView.setOrientation(LinearLayout.VERTICAL);
            cardView.setClickable(true);

            // Top quick-action strip: Forward, Bookmark, Download page, Reload
            quickBar = new LinearLayout(activity);
            quickBar.setOrientation(LinearLayout.HORIZONTAL);
            quickBar.setGravity(Gravity.CENTER_VERTICAL);
            quickBar.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 6));
            int[] quickIds = {MENU_FORWARD, MENU_BOOKMARK_PAGE, MENU_DOWNLOAD_PAGE, MENU_RELOAD};
            int[] quickIcons = {ICON_FORWARD, ICON_STAR, ICON_SAVE, ICON_RELOAD};
            String[] quickDesc = {"Forward", "Bookmark", "Download", "Reload"};
            for (int i = 0; i < quickIds.length; i++) {
                final int qId = quickIds[i];
                LinearLayout btn = new LinearLayout(activity);
                btn.setGravity(Gravity.CENTER);
                btn.setContentDescription(quickDesc[i]);
                btn.setClickable(true);
                btn.setFocusable(true);
                btn.setMinimumHeight(dp(activity, 40));
                btn.setBackground(createRowHighlightDrawable(activity, isDark, dpF(activity, 12f)));
                FlyoutIconView iv = new FlyoutIconView(activity, quickIcons[i], isDark);
                btn.addView(iv, new LinearLayout.LayoutParams(dp(activity, 20), dp(activity, 20)));
                btn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        performCouiHaptic(v);
                        dismiss();
                        dispatchChromiumMenuAction(activity, qId);
                    }
                });
                LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                        0, dp(activity, 40), 1f);
                quickBar.addView(btn, qlp);
            }
            cardView.addView(quickBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            topDivider = new View(activity);
            topDivider.setBackgroundColor(isDark ? 0x22FFFFFF : 0x14000000);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(activity, 0.75f)));
            dlp.leftMargin = dp(activity, 14);
            dlp.rightMargin = dp(activity, 14);
            cardView.addView(topDivider, dlp);

            // Scrollable vertical menu rows
            ScrollView sv = new ScrollView(activity);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            sv.setVerticalScrollBarEnabled(false);

            listContainer = new LinearLayout(activity);
            listContainer.setOrientation(LinearLayout.VERTICAL);
            listContainer.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 8));
            buildMenuRows();
            sv.addView(listContainer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            int maxListH = Math.min(dp(activity, 456),
                    Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.56f));
            cardView.addView(sv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cardView.setMaxHeight(maxListH + dp(activity, 58));

            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                    dp(activity, 232), ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END | Gravity.BOTTOM);
            clp.rightMargin = dp(activity, 16);
            clp.bottomMargin = dp(activity, 90);
            addView(cardView, clp);

            if (blurController != null) {
                blurController.setSlot(SurfaceViewBlurController.SLOT_FLYOUT, cardView, 20f, 13);
            }
        }

        private void buildMenuRows() {
            listContainer.removeAllViews();
            int textColor = isDark ? 0xF0FFFFFF : 0xFF191919;
            for (final MenuItemSpec item : MENU_ITEMS) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setClickable(true);
                row.setFocusable(true);
                row.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
                row.setBackground(createRowHighlightDrawable(activity, isDark, dpF(activity, 12f)));

                FlyoutIconView icon = new FlyoutIconView(activity, item.iconType, isDark);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        dp(activity, 20), dp(activity, 20));
                ilp.rightMargin = dp(activity, 14);
                row.addView(icon, ilp);

                TextView label = new TextView(activity);
                label.setText(item.label);
                label.setTextColor(textColor);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
                label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                label.setSingleLine(true);
                row.addView(label, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                row.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        performCouiHaptic(v);
                        dismiss();
                        dispatchChromiumMenuAction(activity, item.id);
                    }
                });
                listContainer.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44)));
            }
        }

        private static Drawable createRowHighlightDrawable(Context ctx, boolean dark, float cornerPx) {
            GradientDrawable mask = new GradientDrawable();
            mask.setShape(GradientDrawable.RECTANGLE);
            mask.setCornerRadius(cornerPx);
            mask.setColor(0xFFFFFFFF);
            int rippleCol = dark ? 0x26FFFFFF : 0x16000000;
            return new RippleDrawable(ColorStateList.valueOf(rippleCol), null, mask);
        }

        public void updateAnchorMargin(int bottomMarginPx) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cardView.getLayoutParams();
            if (lp != null && lp.bottomMargin != bottomMarginPx) {
                lp.bottomMargin = bottomMarginPx;
                cardView.setLayoutParams(lp);
            }
        }

        public void updateTheme(boolean dark) {
            if (this.isDark != dark) {
                this.isDark = dark;
                topDivider.setBackgroundColor(dark ? 0x22FFFFFF : 0x14000000);
                for (int i = 0; i < quickBar.getChildCount(); i++) {
                    View c = quickBar.getChildAt(i);
                    c.setBackground(createRowHighlightDrawable(activity, dark, dpF(activity, 12f)));
                    if (c instanceof ViewGroup && ((ViewGroup) c).getChildCount() > 0) {
                        View iv = ((ViewGroup) c).getChildAt(0);
                        if (iv instanceof FlyoutIconView) ((FlyoutIconView) iv).setDarkMode(dark);
                    }
                }
                buildMenuRows();
                cardView.invalidate();
            }
        }

        void setNavBar(CouiLiquidGlassNavBar nb) {
            this.navBar = nb;
        }

        void invalidateCard() {
            if (cardView != null) cardView.invalidate();
        }

        public boolean isOpen() {
            return open;
        }

        public void show() {
            if (open) return;
            open = true;
            setVisibility(View.VISIBLE);
            requestFocus();
            cardView.animate().cancel();
            if (navBar != null) {
                navBar.markViewBackdropDirty();
            }
            cardView.setAlpha(0f);
            cardView.setScaleX(0.28f);
            cardView.setScaleY(0.28f);
            cardView.setTranslationY(dpF(activity, 18f));
            stateMachine.setMenuOpen(true);
            cardView.post(new Runnable() {
                @Override
                public void run() {
                    if (!open) return;
                    cardView.setPivotX(cardView.getWidth() * 0.88f);
                    cardView.setPivotY(cardView.getHeight());
                    startMenuSpringLoop();
                }
            });
        }

        public void dismiss() {
            if (!open) return;
            open = false;
            cardView.animate().cancel();
            cardView.setPivotX(cardView.getWidth() * 0.88f);
            cardView.setPivotY(cardView.getHeight());
            stateMachine.setMenuOpen(false);
            startMenuSpringLoop();
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (open && event.getKeyCode() == KeyEvent.KEYCODE_BACK
                    && event.getAction() == KeyEvent.ACTION_UP) {
                dismiss();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        private final class GlassFlyoutCardView extends LinearLayout {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF cardRect = new RectF();
            private int maxHeightPx = Integer.MAX_VALUE;

            GlassFlyoutCardView(Context ctx) {
                super(ctx);
                setWillNotDraw(false);
                setElevation(dpF(ctx, 18f));
                if (Build.VERSION.SDK_INT >= 28) {
                    setOutlineSpotShadowColor(0x40000000);
                    setOutlineAmbientShadowColor(0x22000000);
                }
                final float radiusPx = dpF(ctx, 20f);
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
                        outline.setAlpha(1.0f);
                    }
                });
                setClipToOutline(true);

                rimPaint.setStyle(Paint.Style.STROKE);
                rimPaint.setStrokeWidth(dpF(ctx, 1.0f));
            }

            void setMaxHeight(int px) {
                this.maxHeightPx = px;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (maxHeightPx > 0 && maxHeightPx < Integer.MAX_VALUE) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    float r = dpF(getContext(), 20f);
                    cardRect.set(0.75f, 0.75f, w - 0.75f, h - 0.75f);
                    boolean viewBlur = (navBar != null && navBar.drawViewBackdropBlur(canvas, this, cardRect, r));
                    boolean realBlur = viewBlur || ((navBar == null || !navBar.isInViewBackdropMode())
                            && blurController != null && blurController.isBlurActive());
                    if (realBlur) {
                        // Vibrant luminous frosted glass matching gallery-menu-animation.mp4 (works on Webpages, NTP & Tab Switcher!)
                        bgPaint.setColor(isDark ? 0x8A1E1E22 : 0x74FFFFFF);
                    } else {
                        bgPaint.setColor(isDark ? 0xF0222224 : 0xF5F8F9FB);
                    }
                    canvas.drawRoundRect(cardRect, r, r, bgPaint);

                    LinearGradient sheen = new LinearGradient(
                            0, 0, w * 0.55f, h * 0.75f,
                            isDark
                                    ? new int[]{0x28FFFFFF, 0x08FFFFFF, 0x10000000}
                                    : new int[]{0x64FFFFFF, 0x1CFFFFFF, 0x14E8EEF6},
                            new float[]{0f, 0.5f, 1f},
                            Shader.TileMode.CLAMP
                    );
                    sheenPaint.setShader(sheen);
                    canvas.drawRoundRect(cardRect, r, r, sheenPaint);

                    rimPaint.setShader(null);
                    rimPaint.setStrokeWidth(dpF(getContext(), 0.7f));
                    rimPaint.setColor(isDark ? 0x28FFFFFF : 0x16000000);
                    canvas.drawRoundRect(cardRect, r, r, rimPaint);

                    cardRect.inset(dpF(getContext(), 0.8f), dpF(getContext(), 0.8f));
                    LinearGradient rimGrad = new LinearGradient(
                            0, 0, w, h,
                            isDark
                                    ? new int[]{0x74FFFFFF, 0x22FFFFFF, 0x4CFFFFFF}
                                    : new int[]{0xFFFFFFFF, 0x56FFFFFF, 0xE6FFFFFF},
                            new float[]{0f, 0.5f, 1f},
                            Shader.TileMode.CLAMP
                    );
                    rimPaint.setStrokeWidth(dpF(getContext(), 1.1f));
                    rimPaint.setShader(rimGrad);
                    canvas.drawRoundRect(cardRect, r - dpF(getContext(), 0.8f), r - dpF(getContext(), 0.8f), rimPaint);
                }
                super.onDraw(canvas);
            }
        }

        private static final class FlyoutIconView extends View {
            private final int fluentIconId;
            private boolean isDark;
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            FlyoutIconView(Context ctx, int iconType, boolean isDark) {
                super(ctx);
                this.fluentIconId = mapToFluentIcon(iconType);
                this.isDark = isDark;
                p.setStyle(Paint.Style.FILL);
            }

            private static int mapToFluentIcon(int iconType) {
                switch (iconType) {
                    case ICON_NEW_TAB: return FluentIcons.NEW_TAB;
                    case ICON_INCOGNITO: return FluentIcons.INCOGNITO;
                    case ICON_BOOKMARKS: return FluentIcons.BOOKMARKS;
                    case ICON_STAR: return FluentIcons.BOOKMARK_STAR;
                    case ICON_HISTORY: return FluentIcons.HISTORY;
                    case ICON_DOWNLOADS: return FluentIcons.DOWNLOADS;
                    case ICON_SAVE: return FluentIcons.DOWNLOAD_PAGE;
                    case ICON_EXTENSIONS: return FluentIcons.EXTENSIONS;
                    case ICON_SHARE: return FluentIcons.SHARE;
                    case ICON_FIND: return FluentIcons.FIND_IN_PAGE;
                    case ICON_DESKTOP: return FluentIcons.DESKTOP_SITE;
                    case ICON_SETTINGS: return FluentIcons.SETTINGS;
                    case ICON_FORWARD: return FluentIcons.FORWARD;
                    case ICON_RELOAD: return FluentIcons.RELOAD;
                    default: return FluentIcons.SETTINGS;
                }
            }

            void setDarkMode(boolean dark) {
                this.isDark = dark;
                invalidate();
            }

            @Override
            protected void onDraw(Canvas c) {
                super.onDraw(c);
                float w = getWidth();
                float h = getHeight();
                if (w <= 0 || h <= 0) return;
                p.setColor(isDark ? 0xEBFFFFFF : 0xFF191919);
                float sz = Math.min(w, h) * 1.04f;
                FluentIcons.drawCentered(c, fluentIconId, w * 0.5f, h * 0.5f, sz, p);
            }
        }
    }
}
