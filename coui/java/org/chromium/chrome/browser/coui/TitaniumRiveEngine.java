package org.chromium.chrome.browser.coui;

import android.util.Log;

/**
 * JNI bridge to Titanium Browser's native C++ Rive-architecture State-Machine & Spring Runtime
 * (libtitanium_rive.so, compiled 16KB-page-aligned with Android NDK arm64-v8a clang++).
 *
 * Follows rive-cpp's StateMachineInstance::advance(dt) + Chromium ui/lottie's vsync-gated
 * tick-scheduling contract (zero idle ticks when all springs are settled), with an exact
 * pure-Java fallback so it never crashes even if native library loading is unavailable.
 */
public final class TitaniumRiveEngine {
    private static final String TAG = "TitaniumRive";
    private static final boolean NATIVE_LOADED;

    static {
        boolean ok = false;
        try {
            System.loadLibrary("titanium_rive");
            ok = true;
            Log.i(TAG, "libtitanium_rive.so loaded (16KB arm64-v8a C++ Rive state-machine runtime)");
        } catch (Throwable t) {
            Log.w(TAG, "libtitanium_rive.so unavailable, using identical Java analytical spring fallback", t);
        }
        NATIVE_LOADED = ok;
    }

    public static boolean isNativeActive() {
        return NATIVE_LOADED;
    }

    private static native long nativeCreateMenuMachine();
    private static native void nativeDestroyMenuMachine(long handle);
    private static native void nativeSetMenuOpen(long handle, boolean open);
    private static native boolean nativeAdvanceMenu(long handle, float dt, float[] outState);
    private static native boolean nativeStepOverscrollRk4(float[] state, float dt);

    /**
     * Stateful menu & button-morph controller wrapping native C++ MenuStateMachine (or Java mirror).
     * outState layout: [0]=scale, [1]=transYFrac, [2]=alpha, [3]=iconMorph (0=≡, 1=×), [4]=rowCascade.
     */
    public static final class MenuStateMachine {
        private long nativeHandle;
        // Pure-Java fallback mirror
        private final JavaSpring scale = new JavaSpring(0.28f);
        private final JavaSpring transY = new JavaSpring(1.0f);
        private final JavaSpring alpha = new JavaSpring(0.0f);
        private final JavaSpring iconMorph = new JavaSpring(0.0f);
        private final JavaSpring rowCascade = new JavaSpring(0.0f);

        public MenuStateMachine() {
            if (NATIVE_LOADED) {
                try {
                    nativeHandle = nativeCreateMenuMachine();
                } catch (Throwable ignored) {
                    nativeHandle = 0L;
                }
            }
        }

        public void setMenuOpen(boolean open) {
            if (nativeHandle != 0L) {
                nativeSetMenuOpen(nativeHandle, open);
                return;
            }
            if (open) {
                if (scale.value < 0.30f && alpha.value < 0.05f) {
                    scale.snap(0.28f);
                    transY.snap(1.0f);
                    alpha.snap(0.0f);
                    rowCascade.snap(0.0f);
                    scale.velocity = 1.25f;
                }
                scale.configure(405.0f, 0.725f, 0.0004f);
                scale.target = 1.0f;
                transY.configure(425.0f, 0.75f, 0.0005f);
                transY.target = 0.0f;
                alpha.configure(780.0f, 1.0f, 0.001f);
                alpha.target = 1.0f;
                iconMorph.configure(460.0f, 0.74f, 0.0005f);
                iconMorph.target = 1.0f;
                rowCascade.configure(340.0f, 0.86f, 0.001f);
                rowCascade.target = 1.0f;
            } else {
                scale.configure(980.0f, 1.0f, 0.002f);
                scale.target = 0.34f;
                transY.configure(950.0f, 1.0f, 0.002f);
                transY.target = 1.0f;
                alpha.configure(1180.0f, 1.0f, 0.003f);
                alpha.target = 0.0f;
                iconMorph.configure(540.0f, 0.86f, 0.0008f);
                iconMorph.target = 0.0f;
                rowCascade.configure(1100.0f, 1.0f, 0.005f);
                rowCascade.target = 0.0f;
            }
        }

        public boolean advance(float dt, float[] outState) {
            if (nativeHandle != 0L) {
                return nativeAdvanceMenu(nativeHandle, dt, outState);
            }
            boolean a0 = scale.advance(dt);
            boolean a1 = transY.advance(dt);
            boolean a2 = alpha.advance(dt);
            boolean a3 = iconMorph.advance(dt);
            boolean a4 = rowCascade.advance(dt);
            outState[0] = scale.value;
            outState[1] = transY.value;
            outState[2] = clamp(alpha.value, 0f, 1f);
            outState[3] = clamp(iconMorph.value, -0.15f, 1.15f);
            outState[4] = clamp(rowCascade.value, 0f, 1f);
            return a0 || a1 || a2 || a3 || a4;
        }

        public void release() {
            if (nativeHandle != 0L) {
                long h = nativeHandle;
                nativeHandle = 0L;
                nativeDestroyMenuMachine(h);
            }
        }
    }

    /** Steps ColorOS 17 SpringOverScroller RK4 viscous cushioned overscroll (state=[pos, vel, tmpPos]). */
    public static boolean stepOverscrollRk4(float[] state, float dt) {
        if (NATIVE_LOADED) {
            try {
                return nativeStepOverscrollRk4(state, dt);
            } catch (Throwable ignored) {}
        }
        double pos = state[0];
        double vel = state[1];
        double tmpPos = state[2];
        double h = Math.max(0.004, Math.min(0.024, dt));
        final double kFriction = (12.19 - 8.0) * 3.0 + 25.0; // 37.57
        final double kTension = (16.0 * 1.95 - 30.0) * 3.62 + 194.0; // 198.34
        final double kW = 0.16699999570846558;
        if (Math.abs(pos) < 0.35 && Math.abs(vel) < 18.0) {
            state[0] = 0f; state[1] = 0f; state[2] = 0f;
            return false;
        }
        double a1 = (-tmpPos) * kTension - kFriction * vel;
        double x2 = pos + vel * h * 0.5;
        double v2 = vel + a1 * h * 0.5;
        double a2 = (-x2) * kTension - kFriction * v2;
        double x3 = pos + v2 * h * 0.5;
        double v3 = vel + a2 * h * 0.5;
        double a3 = (-x3) * kTension - kFriction * v3;
        double x4 = pos + v3 * h;
        double v4 = vel + a3 * h;
        double a4 = (-x4) * kTension - kFriction * v4;
        pos += (vel + 2.0 * (v2 + v3) + v4) * kW * h;
        vel += (a1 + 2.0 * (a2 + a3) + a4) * kW * h;
        tmpPos = x4;
        if (Math.abs(pos) < 0.35 && Math.abs(vel) < 18.0) {
            state[0] = 0f; state[1] = 0f; state[2] = 0f;
            return false;
        }
        state[0] = (float) pos;
        state[1] = (float) vel;
        state[2] = (float) tmpPos;
        return true;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static final class JavaSpring {
        float value;
        float velocity;
        float target;
        float stiffness = 400f;
        float dampingRatio = 0.73f;
        float epsilon = 0.0005f;

        JavaSpring(float init) {
            value = init;
            target = init;
        }

        void configure(float k, float zeta, float eps) {
            stiffness = Math.max(1f, k);
            dampingRatio = Math.max(0.05f, zeta);
            epsilon = eps;
        }

        void snap(float v) {
            value = v;
            target = v;
            velocity = 0f;
        }

        boolean isSettled() {
            return Math.abs(value - target) <= epsilon && Math.abs(velocity) <= epsilon * 45f;
        }

        boolean advance(float dt) {
            if (dt <= 0f) return !isSettled();
            if (dt > 0.05f) dt = 0.05f;
            if (isSettled()) {
                snap(target);
                return false;
            }
            double omega = Math.sqrt(stiffness);
            double zeta = dampingRatio;
            double d0 = value - target;
            double v0 = velocity;
            double h = dt;
            double pos, vel;
            if (zeta < 0.9999) {
                double wd = omega * Math.sqrt(1.0 - zeta * zeta);
                double b = (v0 + zeta * omega * d0) / wd;
                double e = Math.exp(-zeta * omega * h);
                double c = Math.cos(wd * h);
                double s = Math.sin(wd * h);
                pos = target + e * (d0 * c + b * s);
                vel = e * ((b * wd - zeta * omega * d0) * c - (d0 * wd + zeta * omega * b) * s);
            } else {
                double b = v0 + omega * d0;
                double e = Math.exp(-omega * h);
                pos = target + (d0 + b * h) * e;
                vel = (b - omega * (d0 + b * h)) * e;
            }
            value = (float) pos;
            velocity = (float) vel;
            if (isSettled()) {
                snap(target);
                return false;
            }
            return true;
        }
    }
}
