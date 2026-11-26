# COUI enhancer — bytecode hooks

`CouiBrowserUiEnhancer.java` is compiled with `d8` and merged into the release APK's dex. The UI
changes also need the small smali patches below, which are applied to the stock Titanium dex at build
time. Class and field names are R8-obfuscated and only valid for the build these were written
against (`chromium-ChromePublic.apk-default-807807974`). Re-derive them for any other build.

## Lifecycle entry points

| Target | Patch |
|---|---|
| `ChromeTabbedActivity.onResume()` | after `super.onResume()`: `CouiBrowserUiEnhancer.attachToBrowser(this)` |
| `SettingsActivity.onResume()` | after `super.onResume()`: `CouiBrowserUiEnhancer.attachToSettings(this)` |
| `SettingsActivity.f()Z` | return `true` (hides the help menu item) |
| `AboutChromeSettings.I1(String, Bundle)` | append `CouiBrowserUiEnhancer.onAboutSettingsCreated(pref)` |
| `Ldc3.<init>(Context)` / `Ldc3.a` | `onContainmentControllerInit(this, ctx)`; always use the card colour |
| `Lec3` (ItemDecoration) | add `i(Canvas, RecyclerView)` → `onSettingsDrawOver(...)` |
| `ToolbarPhone.r0()` / `s0()` | URL bar spans the full toolbar width |

## Round 11 — edge-to-edge status bar blur and the ColorOS 17 Settings search pill

| Target | Patch | Why |
|---|---|---|
| `Lo05.c(View, WindowInsetsCompat)` (edge-to-edge insets listener) | `EdgeToEdgeBaseLayout.setPadding(l,t,r,b)` → `CouiBrowserUiEnhancer.onEdgeToEdgePadding(view,l,t,r,b)` | Drops the top (status bar) inset for `ChromeTabbedActivity`, so the compositor `SurfaceView` and `control_container` start at `y = 0`. The SurfaceControl blur can then sample web content behind the status bar. The inset becomes top padding on `toolbar_container`. |
| `ToolbarControlContainer.k()I` (top controls height) | `return A + m() + CouiBrowserUiEnhancer.extraTopControlsHeight(this)` (`.registers 3`) | Keeps page content starting below the taller glass header at scroll-top. |
| `ToolbarPhone.m0(Canvas, long)` ("drawLocationBar") | `Lon8;->draw` → `nop`; `Canvas.clipRect(FFFF)` → `nop` | Chromium's flat location-bar background was the opaque rectangle behind the URL text, and its clip cut the 40dp glass capsule down to a 28dp band. Chromium re-animates its alpha, so `setAlpha(0)` alone never held. |
| `ToolbarPhone.dispatchDraw(Canvas)` | `s0` `ColorDrawable.draw` → `nop` | The page `theme-color` toolbar fill painted an opaque band over the frosted header. |
| `Lzdh.d` (TopToolbarSceneLayer binder → `updateToolbarLayer` JNI) | before the JNI call: background colour `v8`, URL-bar colour `v10`, `show_shadow` `v15` (`const/4`) and compositor toolbar `visible` `v16` (`const/16`) set to `0` | Prevents Chromium's compositor from painting an opaque toolbar fill, hairline, or duplicate bitmap behind our animated Android Liquid Glass header during Echo-Music auto-hide. |

## Round 12 — theme-color isolation, Echo-Music auto-hide, Tab Switcher inset, Search Engine & NTP COUI parity

| Target | Patch | Why |
|---|---|---|
| `Lula.b(Context, int, boolean)` (`ThemeUtils.getBrandedColorScheme`) | return `CouiBrowserUiEnhancer.getBrandedColorScheme(p0)` (`0` Light / `1` Dark) | Stops dark website `<meta name="theme-color">` (e.g. `keybox.tryigit.dev`, `github.com`) from emitting white `UrlEmphasisColorSpan`s on our Light frosted glass pill. |
| `Lz2f.h(Lw05, Activity, int, boolean)` (`StatusBarColorController.setStatusBarColor`) | `p2 = CouiBrowserUiEnhancer.overrideStatusBarColor(p1, p2)` (`0xFFECEDF0` Light / `0xFF101012` Dark for `ChromeTabbedActivity`) | Keeps `APPEARANCE_LIGHT_STATUS_BARS` locked strictly to Light/Dark mode instead of flipping status bar icons white on dark websites. |
| `Lw05.m()` (`EdgeToEdgeSystemBarColorHelper`) | before `Lkhf.I0(v4)`: `v4 = CouiBrowserUiEnhancer.overrideEdgeToEdgeStatusBarFill(v0, v4)` (`0` for `ChromeTabbedActivity`) | Keeps `EdgeToEdgeBaseLayout.C` transparent so edge-to-edge hardware blur behind the status bar stays unobstructed while `Ld71.r` still drives crisp status bar icon contrast. |
| `Law1.x()` (`BrowserControlsManager.notifyControlOffsetChanged`) | `nop` direct `control_container.setTranslationY(F)`; at `:cond_24` call `CouiBrowserUiEnhancer.onBrowserControlsOffset(v3, v4)` | Feeds every compositor scroll frame into the synchronized directional auto-hide state machine so BOTH top header and bottom 3-piece Liquid Glass nav bar glide offscreen/back in unison with zero status-bar collision. |
| `Law1.E()Z` (`BrowserControlsManager.shouldShowAndroidControls`) | return `true` whenever `control_container != null && !fullscreen` | Prevents Chromium from flashing `control_container` to `View.INVISIBLE` whenever `getBrowserControlHiddenRatio() > 0f`, allowing smooth synchronized `translationY` hide and immediate upward reveal. |

## Round 15 — universal ColorOS 17 switches, single-line omnibox cleanup, Rive C++ spring runtime & Fluent icons

| Target | Patch | Why |
|---|---|---|
| `Landroidx/appcompat/widget/SwitchCompat.onDraw(Canvas)` | early-return when `CouiBrowserUiEnhancer.drawCouiSwitch(this, p1, this.Q)` returns `true` | Renders authentic borderless ColorOS 17 pill switches (`#E2E4E8` off / `#0080FF` on, animated thumb position `Q:F`) across ALL Settings and sub-settings screens regardless of R8 method obfuscation on `SwitchCompat`/`MaterialSwitch`. |
| `Landroidx/appcompat/widget/SwitchCompat.toggle()` | append `CouiBrowserUiEnhancer.onCouiSwitchToggle(this)` | Triggers ROM ColorOS haptic `302` (`performHapticFeedback(302)`) on every switch toggle across Settings. |
| `Lorg/chromium/chrome/browser/omnibox/UrlBarApi26.o()V` | replace `setHorizontallyScrolling(false)` with `setHorizontallyScrolling(true)` | Keeps long omnibox queries on a single vertically-centered horizontally scrollable line instead of wrapping vertically inside the 40dp glass capsule. |
| `Lorg/chromium/chrome/browser/omnibox/LocationBarLayout.E(View, Z)` | filter `p2 = CouiBrowserUiEnhancer.shouldAllowOmniboxButton(p1, p2)` | Permanently suppresses Chromium Fusebox's stray left `+` (`fusebox_plus_button`), `fusebox_request_type`, and right blue `>` (`navigate_button`) pill from re-appearing or colliding with the Fluent `X` clear button during omnibox typing. |
| `Lorg/chromium/content/browser/GestureListenerManagerImpl.f(FFF)` / `l()Z` / `onScrollBegin(Z)` / `updateOnTouchDown()` / `updateOnTouchUp()` | request `ALL_UPDATES (2)` scroll offset frequency in `l()Z` and forward viewport-compensated physical scroll pixel deltas & touch state into `CouiBrowserUiEnhancer.onWebScrollChanged/Begin/TouchDown/TouchUp` | Drives reliable synchronized auto-hide (`26dp` downward hide trigger, `12dp` upward micro-jitter deadzone, `28dp` upward reveal) directly from Viz/Blink compositor scroll deltas from Scroll 1 frame 1 — 100% immune to Android 15+ `InputOnViz` (`InputVizard`) touch transfer! |
| `lib/arm64-v8a/libtitanium_rive.so` | 16KB-page-aligned native C++ Rive-architecture state-machine & ColorOS RK4 overscroll engine (`coui/native/titanium_rive_engine.cpp`) | Drives overflow menu spring physics (`scale 0.84 -> ~1.035 -> 1.00`, fast `~145ms` exit), `≡ <-> ×` menu icon morph, and cushioned `~520–580ms` Settings overscroll physics at 120Hz `Choreographer` vsync with 0% idle CPU. |

## Round 16 — root Settings large title, persistent Settings search, NTP redesign, universal 16dp sub-settings parity & flyout white-box fix

| Target | Patch | Why |
|---|---|---|
| `La7e.G(Landroid/view/View;Z)V` (`SettingsSearchCoordinator` show/hide transition) | bypass `TransitionManager` (`Fade` + `ChangeBounds`) and click-listener stripping; keep `p1` (`id/search_box`) `View.VISIBLE` whenever `this.F == 0` | Eliminates the jarring search bar disappear/reappear animation on sub-settings navigation (`titanium-search-broken-animation.mp4`) and preserves the tap listener across all screens. |
| `La7e.K()V` (`updateSearchBoxVisibility`) | replace `invoke-virtual {p0}, La7e;->t()Z` check with `const/4 p0, 0x1` | Keeps the redesigned COUI `42dp` `"Search"` capsule persistently `View.VISIBLE` and tappable on every sub-settings screen while preserving `La7e.t()Z` for root `MainSettings` detection. |
| `La7e.k()V` (`exitSearchState`) | unconditionally restore `id/search_box` (`0x7f010b05`) to `View.VISIBLE` when `v0 != null` | Ensures exiting an active Settings search from any sub-settings screen immediately restores the persistent search pill with zero flash. |

## Round 17 — Method 1 full-screen ColorOS 17 Settings page slide transitions

| Target | Patch | Why |
|---|---|---|
| `SettingsActivity.x1(Lk01, Lt86)` | immediate `return-void` | Disables Chromium's partial-pane `FragmentTransaction` slide animations (`0x7f030028..2b`) that slid cards under a static header. |
| `SettingsActivity.onResume()` (forward push `this.q0 != null`) | before `x1`: call `CouiBrowserUiEnhancer.onSettingsBeforeForwardPush(this)` | Captures a 1:1 full-screen outgoing snapshot of `id/settings_activity` (including `AppBarLayout`, large/inline title, `"Search"` capsule, and cards) before the fragment swap so the entire incoming screen slides `+100% -> 0%` over the `-22%` parallaxing outgoing screen (`340ms`, `PathInterpolator(0.20, 0, 0, 1)`). |
| `SettingsActivity.e(Lt86)` (back button / Esc pop) | before `Lda6.X()`: call `CouiBrowserUiEnhancer.onSettingsBeforeBackwardPop(this)` | Captures the full outgoing sub-settings snapshot before popping the back stack, reversing the full-screen slide cleanly (`0% -> +100%` over parent `-22% -> 0%`). |
| `SettingsActivity.onDestroy()` | top of method: `CouiBrowserUiEnhancer.onSettingsDestroyed(this)` | Cancels any in-flight slide animation and recycles transient bitmaps cleanly on activity teardown. |
| `Lo96.h(Lxz0)` & `Lo96.f()` (`FragmentManager` `OnBackPressedCallback`) | bypass half-pane predictive back in `h()` when host is `SettingsActivity`; before `Lda6.Z()` in `f()` call `CouiBrowserUiEnhancer.onFragmentManagerBeforeBackPop(p0)` | Ensures Android system back gestures trigger the exact same full-screen ColorOS 17 backward slide without partial-pane header/body tearing. |
| `Ljqe.n()` / `Ljqe.c()` / `Ljqe.s(F)` (`SlidingPaneLayout` used by `MultiColumnSettings`) | `onSlidingPaneBeforeSlide(this, true/false)` in `n()`/`c()`; `trySnapSlidingPaneImmediate(this, v2, targetLeft)` after `Ld6i.k()` in `s(F)` | Captures full-screen outgoing snapshot before pane open/close and snaps `SlidingPaneLayout` immediately underneath so `CouiSettingsPageSlideController` animates the entire screen (`AppBarLayout` + title + Search pill + cards) as one rigid ColorOS 17 sheet. |
| `MultiColumnSettings.Q1()` & `Lr4c.u()` (`PreferenceHeaderFragmentCompat`) | `onMultiColumnSettingsBeforePush(this)` + clear `TRANSIT_FRAGMENT_FADE (0x1003)` | Triggers the same Method 1 full-screen forward slide for nested sub-sub-settings pushes when the detail pane is already open. |

## Round 18 — static 16dp sub-settings margins, expressive ColorOS 17 EdgeEffect overscroll & COUI capsule sliders

| Target | Patch | Why |
|---|---|---|
| `Lhki.f(Lda6, Lt86, View)` (`WideDisplayPadding` lifecycle callback) | clear stock divider (`Lg4c.N1(null)`) and immediately `return-void` | Eliminates Chromium's dynamic `WideDisplayPadding` (`0x7f080ba2 = 16dp`) container/item-decoration padding (`Lgki`/`Lfki`/`Lbva`/`Lcki`) across every sub-settings fragment so all Settings lists stay 0px-padded horizontally and every card uses static fixed `16dp` margins from frame 1 (zero horizontal snap/resize during slide-in and zero double-inset on Accessibility `ContainedLinearLayout` cards). |
| `Lcom/google/android/material/slider/Slider.onDraw(Canvas)` | after `super.onDraw(canvas)`: early-return when `CouiBrowserUiEnhancer.drawCouiMaterialSlider(this, canvas)` returns `true` | Replaces Chromium's thin Material3 bar-thumb slider in Accessibility (`Default zoom` & `Text size contrast`) with the authentic ColorOS 17 `18dp` thick capsule slider (`#E5E6EA`/`#2C2C2E` track, `#007AFF`/`#1A8CFF` continuous active fill enclosing the `#FFFFFF` `13.6dp` circular thumb with subtle shadow, touch-aligned via `Slider.a0` & `Slider.S0`, plus ROM haptic `308`). |

## Runtime notes (Java side)

- The header glass is a dedicated, non-clickable child of `control_container`. It is appended last
  with `translationZ = -1`: Chromium addresses `control_container` children by index, and inserting
  at index 0 made URL taps open the NTP. It measures 0 on the `AT_MOST` pass, so it never grows the
  wrap-content container.
- Hidden toolbar buttons use `scale = 0.001`, not `0`. A scale-0 matrix is non-invertible, and
  touch hit-testing then ignores the −400dp translation.
- `StatusView` keeps Chromium's fixed 56dp LayoutParams height. It is forced to `MATCH_PARENT` so the
  search icon is centred in the 40dp pill.
