// Titanium Browser Native C++ Rive-Architecture State-Machine & Spring Runtime
// Models rive-cpp's StateMachineInstance::advance(float dt) + Artboard property evaluation
// coupled with Chromium ui/lottie's vsync-gated tick scheduler (0% idle CPU when settled).
//
// Drives:
//   1. Overflow menu popup 120Hz analytical spring physics (scale, translationY, fast-ramp alpha,
//      and staggered row reveal matching ColorOS 17 Gallery's ~1.035 overshoot & ~145ms exit).
//   2. Bottom-right Menu button interactive vector morph state machine (≡ <-> ×).
//   3. ColorOS 17 SpringOverScroller RK4 viscous cushioned overscroll solver (~480-560ms settle).

#include <jni.h>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <new>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "TitaniumRiveCpp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace titanium::rive {

struct SpringChannel {
    float value = 0.0f;
    float velocity = 0.0f;
    float target = 0.0f;
    float stiffness = 400.0f;
    float dampingRatio = 0.73f;
    float epsilon = 0.0005f;

    void configure(float k, float zeta, float eps = 0.0005f) {
        stiffness = std::max(1.0f, k);
        dampingRatio = std::max(0.05f, zeta);
        epsilon = eps;
    }

    void snap(float v) {
        value = v;
        target = v;
        velocity = 0.0f;
    }

    bool isSettled() const {
        return std::fabs(value - target) <= epsilon &&
               std::fabs(velocity) <= epsilon * 45.0f;
    }

    // Exact closed-form damped harmonic oscillator step (frame-rate independent 60/90/120Hz).
    bool advance(float dt) {
        if (dt <= 0.0f) return !isSettled();
        if (dt > 0.05f) dt = 0.05f;
        if (isSettled()) {
            snap(target);
            return false;
        }
        const double omega = std::sqrt(static_cast<double>(stiffness));
        const double zeta = static_cast<double>(dampingRatio);
        const double d0 = static_cast<double>(value - target);
        const double v0 = static_cast<double>(velocity);
        const double h = static_cast<double>(dt);

        double pos, vel;
        if (zeta < 0.9999) {
            const double wd = omega * std::sqrt(1.0 - zeta * zeta);
            const double b = (v0 + zeta * omega * d0) / wd;
            const double e = std::exp(-zeta * omega * h);
            const double c = std::cos(wd * h);
            const double s = std::sin(wd * h);
            pos = static_cast<double>(target) + e * (d0 * c + b * s);
            vel = e * ((b * wd - zeta * omega * d0) * c - (d0 * wd + zeta * omega * b) * s);
        } else if (zeta <= 1.0001) {
            const double b = v0 + omega * d0;
            const double e = std::exp(-omega * h);
            pos = static_cast<double>(target) + (d0 + b * h) * e;
            vel = (b - omega * (d0 + b * h)) * e;
        } else {
            const double sq = omega * std::sqrt(zeta * zeta - 1.0);
            const double r1 = -zeta * omega + sq;
            const double r2 = -zeta * omega - sq;
            const double c1 = (v0 - r2 * d0) / (r1 - r2);
            const double c2 = d0 - c1;
            const double e1 = std::exp(r1 * h);
            const double e2 = std::exp(r2 * h);
            pos = static_cast<double>(target) + c1 * e1 + c2 * e2;
            vel = c1 * r1 * e1 + c2 * r2 * e2;
        }

        value = static_cast<float>(pos);
        velocity = static_cast<float>(vel);
        if (isSettled()) {
            snap(target);
            return false;
        }
        return true;
    }
};

// StateMachineInstance: owns coordinated artboard channels for Titanium's overflow menu & trigger morph.
class MenuStateMachine {
public:
    SpringChannel scale;
    SpringChannel transY;
    SpringChannel alpha;
    SpringChannel iconMorph;
    SpringChannel rowCascade;
    bool menuOpen = false;

    MenuStateMachine() {
        scale.snap(0.28f);
        transY.snap(1.0f);
        alpha.snap(0.0f);
        iconMorph.snap(0.0f);
        rowCascade.snap(0.0f);
    }

    void setMenuOpen(bool open) {
        menuOpen = open;
        if (open) {
            // ColorOS 17 Gallery enter curve: brisk expand from bottom-right anchor with ~1.035 overshoot
            // settling smoothly over ~480-530ms (matches gallery-menu-animation.mp4).
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

            // Fast-ramp critically damped alpha so frosted glass & Fluent icons read crisp immediately
            alpha.configure(780.0f, 1.0f, 0.001f);
            alpha.target = 1.0f;

            // Morph ≡ -> × smoothly with crisp subtle spring bounce
            iconMorph.configure(460.0f, 0.74f, 0.0005f);
            iconMorph.target = 1.0f;

            rowCascade.configure(340.0f, 0.86f, 0.001f);
            rowCascade.target = 1.0f;
        } else {
            // Critically damped fast accelerated contraction back into bottom-right Menu circle (~145ms)
            scale.configure(980.0f, 1.0f, 0.002f);
            scale.target = 0.34f;

            transY.configure(950.0f, 1.0f, 0.002f);
            transY.target = 1.0f;

            alpha.configure(1180.0f, 1.0f, 0.003f);
            alpha.target = 0.0f;

            // Morph × -> ≡ cleanly
            iconMorph.configure(540.0f, 0.86f, 0.0008f);
            iconMorph.target = 0.0f;

            rowCascade.configure(1100.0f, 1.0f, 0.005f);
            rowCascade.target = 0.0f;
        }
    }

    // Returns true while any channel is still active (ui/lottie tick-scheduling contract).
    bool advance(float dt, float outState[5]) {
        bool a0 = scale.advance(dt);
        bool a1 = transY.advance(dt);
        bool a2 = alpha.advance(dt);
        bool a3 = iconMorph.advance(dt);
        bool a4 = rowCascade.advance(dt);

        outState[0] = scale.value;
        outState[1] = transY.value;
        outState[2] = std::clamp(alpha.value, 0.0f, 1.0f);
        outState[3] = std::clamp(iconMorph.value, -0.15f, 1.15f);
        outState[4] = std::clamp(rowCascade.value, 0.0f, 1.0f);
        return a0 || a1 || a2 || a3 || a4;
    }
};

// ColorOS 17 SpringOverScroller RK4 solver (exact Origami constants from COUIReboundOverScroller:
// SPRING_BACK_FRICTION = 12.19, SPRING_BACK_TENSION = 16 * 1.95 -> ~520-580ms cushioned glide).
static bool stepOverscrollRk4(float state[3], float dt) {
    // state[0] = position, state[1] = velocity, state[2] = tempPosition
    double pos = static_cast<double>(state[0]);
    double vel = static_cast<double>(state[1]);
    double tmpPos = static_cast<double>(state[2]);

    const double h = std::clamp(static_cast<double>(dt), 0.004, 0.024);
    // Origami -> physics conversion matching ColorOS 17 SpringOverScroller, tuned for lush 520-580ms settle
    constexpr double kFrictionOrigami = 12.19;
    constexpr double kTensionOrigami = 16.0 * 1.95; // 31.2 -> smooth cushioned ColorOS 17 Settings rebound
    constexpr double kFriction = (kFrictionOrigami - 8.0) * 3.0 + 25.0; // 37.57
    constexpr double kTension = (kTensionOrigami - 30.0) * 3.62 + 194.0; // 198.34
    constexpr double kRk4Weight = 0.16699999570846558;

    if (std::fabs(pos) < 0.35 && std::fabs(vel) < 18.0) {
        state[0] = 0.0f;
        state[1] = 0.0f;
        state[2] = 0.0f;
        return false;
    }

    const double a1 = (-tmpPos) * kTension - kFriction * vel;
    const double x2 = pos + vel * h * 0.5;
    const double v2 = vel + a1 * h * 0.5;
    const double a2 = (-x2) * kTension - kFriction * v2;
    const double x3 = pos + v2 * h * 0.5;
    const double v3 = vel + a2 * h * 0.5;
    const double a3 = (-x3) * kTension - kFriction * v3;
    const double x4 = pos + v3 * h;
    const double v4 = vel + a3 * h;
    const double a4 = (-x4) * kTension - kFriction * v4;

    pos += (vel + 2.0 * (v2 + v3) + v4) * kRk4Weight * h;
    vel += (a1 + 2.0 * (a2 + a3) + a4) * kRk4Weight * h;
    tmpPos = x4;

    if (std::fabs(pos) < 0.35 && std::fabs(vel) < 18.0) {
        state[0] = 0.0f;
        state[1] = 0.0f;
        state[2] = 0.0f;
        return false;
    }

    state[0] = static_cast<float>(pos);
    state[1] = static_cast<float>(vel);
    state[2] = static_cast<float>(tmpPos);
    return true;
}

} // namespace titanium::rive

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_chromium_chrome_browser_coui_TitaniumRiveEngine_nativeCreateMenuMachine(JNIEnv*, jclass) {
    void* mem = std::malloc(sizeof(titanium::rive::MenuStateMachine));
    if (!mem) return 0;
    auto* sm = new (mem) titanium::rive::MenuStateMachine();
    return reinterpret_cast<jlong>(sm);
}

JNIEXPORT void JNICALL
Java_org_chromium_chrome_browser_coui_TitaniumRiveEngine_nativeDestroyMenuMachine(JNIEnv*, jclass, jlong handle) {
    auto* sm = reinterpret_cast<titanium::rive::MenuStateMachine*>(handle);
    if (sm) {
        sm->~MenuStateMachine();
        std::free(sm);
    }
}

JNIEXPORT void JNICALL
Java_org_chromium_chrome_browser_coui_TitaniumRiveEngine_nativeSetMenuOpen(JNIEnv*, jclass, jlong handle, jboolean open) {
    auto* sm = reinterpret_cast<titanium::rive::MenuStateMachine*>(handle);
    if (sm) sm->setMenuOpen(open == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_org_chromium_chrome_browser_coui_TitaniumRiveEngine_nativeAdvanceMenu(JNIEnv* env, jclass, jlong handle, jfloat dt, jfloatArray outArr) {
    auto* sm = reinterpret_cast<titanium::rive::MenuStateMachine*>(handle);
    if (!sm || !outArr) return JNI_FALSE;
    float tmp[5] = {1.0f, 0.0f, 0.0f, 0.0f, 0.0f};
    bool active = sm->advance(dt, tmp);
    env->SetFloatArrayRegion(outArr, 0, 5, tmp);
    return active ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_chromium_chrome_browser_coui_TitaniumRiveEngine_nativeStepOverscrollRk4(JNIEnv* env, jclass, jfloatArray stateArr, jfloat dt) {
    if (!stateArr) return JNI_FALSE;
    float st[3] = {0.0f, 0.0f, 0.0f};
    env->GetFloatArrayRegion(stateArr, 0, 3, st);
    bool active = titanium::rive::stepOverscrollRk4(st, dt);
    env->SetFloatArrayRegion(stateArr, 0, 3, st);
    return active ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
