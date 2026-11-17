package dev.theoria.wings;

/**
 * The turntable angle of the wings screen's 3D player. Pure state machine (no client classes) so {@code /theoria test wings}
 * can pin its rules: 0° = the player's BACK faces the camera (the wings are on the back — ADR-002 b), it idles slowly, a drag
 * hands control to the user, and after {@link #IDLE_DELAY} seconds without touching it the idle spin resumes.
 * The vanilla {@code renderEntityInInventoryFollowsAngle} cannot do this: it turns body and head only within ±~30° (ADR-002 b).
 */
public final class PreviewAngle {
    public static final float IDLE_DEGREES_PER_SECOND = 14f;
    public static final float IDLE_DELAY = 3f;
    public static final float DRAG_YAW_PER_PIXEL = 1.6f;
    public static final float DRAG_PITCH_PER_PIXEL = 0.8f;
    public static final float MAX_PITCH = 25f;
    /** A frame longer than this (window drag, breakpoint) must not fling the player round. */
    public static final float MAX_STEP = 0.1f;

    private float yaw;
    private float pitch;
    private float sinceInteraction = IDLE_DELAY;
    private boolean dragging;

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public boolean idle() {
        return !dragging && sinceInteraction >= IDLE_DELAY;
    }

    /** Advances real time by {@code seconds} (clamped to {@link #MAX_STEP}). */
    public void advance(float seconds) {
        float dt = Math.max(0f, Math.min(seconds, MAX_STEP));
        if (dragging) return;
        sinceInteraction += dt;
        if (sinceInteraction >= IDLE_DELAY) {
            yaw = wrap(yaw + IDLE_DEGREES_PER_SECOND * dt);
        }
    }

    /** Horizontal drag turns the player, vertical drag tilts the camera (clamped). */
    public void drag(double dx, double dy) {
        dragging = true;
        sinceInteraction = 0f;
        yaw = wrap(yaw - (float) dx * DRAG_YAW_PER_PIXEL);
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch + (float) dy * DRAG_PITCH_PER_PIXEL));
    }

    public void release() {
        dragging = false;
        sinceInteraction = 0f;
    }

    private static float wrap(float degrees) {
        float r = degrees % 360f;
        return r < 0f ? r + 360f : r;
    }
}
