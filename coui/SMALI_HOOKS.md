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
| `Law1.x()` (`BrowserControlsManager.notifyControlOffsetChanged`) | `nop` direct `control_container.setTranslationY(F)`; at `:cond_24` call `CouiBrowserUiEnhancer.onBrowserControlsOffset(v3, v4)` | Feeds every compositor scroll frame into the Echo-Music `50.dp` directional auto-hide state machine (`spring(0.9f, 500f)` restore) so BOTH top header and bottom 3-piece Liquid Glass nav bar glide offscreen/back in unison with zero status-bar collision. |

## Runtime notes (Java side)

- The header glass is a dedicated, non-clickable child of `control_container`. It is appended last
  with `translationZ = -1`: Chromium addresses `control_container` children by index, and inserting
  at index 0 made URL taps open the NTP. It measures 0 on the `AT_MOST` pass, so it never grows the
  wrap-content container.
- Hidden toolbar buttons use `scale = 0.001`, not `0`. A scale-0 matrix is non-invertible, and
  touch hit-testing then ignores the −400dp translation.
- `StatusView` keeps Chromium's fixed 56dp LayoutParams height. It is forced to `MATCH_PARENT` so the
  search icon is centred in the 40dp pill.
