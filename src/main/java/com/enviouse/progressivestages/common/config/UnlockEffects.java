package com.enviouse.progressivestages.common.config;

/**
 * v2.4: a stage's optional {@code [unlock]} presentation — toast / title / subtitle / sound /
 * particles shown when the stage is granted, plus opt-in progress nudges and the on-screen HUD bar.
 *
 * <p>Every field is OPTIONAL: an empty string / false means "don't do that part". A stage with no
 * {@code [unlock]} section at all uses {@link #NONE} and shows nothing beyond the legacy
 * {@code unlock_message} chat line.
 *
 * @param toast        toast popup text (advancement-style); empty = no toast
 * @param title        big title text; empty = no title
 * @param subtitle     subtitle text (only meaningful alongside a title); empty = none
 * @param sound        sound id to play on unlock (e.g. {@code minecraft:ui.toast.challenge_complete}); empty = none
 * @param particle     particle id to spray around the player on unlock; empty = none
 * @param progressNudges  if true, send a one-time chat hint at 50% / 75% / 90% trigger progress
 * @param hudBar       if true, show the custom blue "progress to next stage" bar above the XP bar while this stage is the active goal
 */
public record UnlockEffects(String toast, String title, String subtitle, String sound, String particle,
                            boolean progressNudges, boolean hudBar) {

    public static final UnlockEffects NONE = new UnlockEffects("", "", "", "", "", false, false);

    public UnlockEffects {
        toast = toast == null ? "" : toast;
        title = title == null ? "" : title;
        subtitle = subtitle == null ? "" : subtitle;
        sound = sound == null ? "" : sound;
        particle = particle == null ? "" : particle;
    }

    public boolean hasToast()    { return !toast.isEmpty(); }
    public boolean hasTitle()    { return !title.isEmpty(); }
    public boolean hasSound()    { return !sound.isEmpty(); }
    public boolean hasParticle() { return !particle.isEmpty(); }
}
