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
| `Lzdh.d` (TopToolbarSceneLayer binder → `updateToolbarLayer` JNI) | before the JNI call: background colour `v8`, URL-bar colour `v10` and `show_shadow` `v15` set to `0` | The compositor painted the toolbar colour and hairline behind the pinned glass header. The pixel-aligned toolbar texture is kept. |

## Runtime notes (Java side)

- The header glass is a dedicated, non-clickable child of `control_container`. It is appended last
  with `translationZ = -1`: Chromium addresses `control_container` children by index, and inserting
  at index 0 made URL taps open the NTP. It measures 0 on the `AT_MOST` pass, so it never grows the
  wrap-content container.
- Hidden toolbar buttons use `scale = 0.001`, not `0`. A scale-0 matrix is non-invertible, and
  touch hit-testing then ignores the −400dp translation.
- `StatusView` keeps Chromium's fixed 56dp LayoutParams height. It is forced to `MATCH_PARENT` so the
  search icon is centred in the 40dp pill.
