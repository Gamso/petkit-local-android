package org.petkitlocal.core.domain.device

import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityCategory

/**
 * The sections of a settings screen, in the order they are shown. Which ones a device gets
 * follows from its settings: a litter box has Cleaning and Litter, a fountain Water flow and
 * Water care. [Advanced] and [Diagnostics] are folded away until asked for.
 */
enum class SettingCategory {
    Cleaning,
    Deodorizing,
    Litter,
    Feeding,
    WaterFlow,
    WaterCare,
    ComfortAndSafety,
    Detection,
    Camera,
    Recordings,
    Sound,
    DisplayAndQuiet,

    /** A setting this app does not know yet: shown, in the server's words, with no explanation. */
    Other,

    /** Raw or rarely needed values. */
    Advanced,

    /** Settable values the server files under diagnostics. */
    Diagnostics,
    ;

    val folded: Boolean get() = this == Advanced || this == Diagnostics
}

/**
 * Where each setting goes, and whether the app can say in one line what it does, by its stable
 * entity key (the keys petkit-local defines, `ha/entities/`), never by model.
 *
 * An explanation is given only where its meaning is established: by petkit-local's own notes
 * (its entity definitions, `events/codes.py`, captures of PetKit's app) or by a name that leaves
 * no doubt. Where it is not, there is none rather than a guess (see [UNEXPLAINED]).
 */
object SettingCatalog {
    private val CATEGORIES: Map<String, SettingCategory> = buildMap {
        fun put(category: SettingCategory, vararg keys: String) = keys.forEach { put(it, category) }

        put(
            SettingCategory.Cleaning,
            "auto_work",
            "cleaning_delay",
            "avoid_repeat",
            "cleaning_interval",
            "fixed_time_clear",
            "deep_clean",
            "downpos",
        )
        put(SettingCategory.Deodorizing, "auto_spray", "fixed_time_spray", "deep_spray")
        put(SettingCategory.Litter, "sand_type", "sand_saving", "bury")
        put(
            SettingCategory.Feeding,
            "feeding_schedule_active",
            "hopper1_portions",
            "hopper2_portions",
            "surplus_level",
            "food_warn",
            "feed_sound",
            "voice_dispense",
        )
        put(
            SettingCategory.WaterFlow,
            "w5_power",
            "ctw3_power",
            "ctw3_working",
            "flow_mode",
            "fountain_time",
            "sleep_time",
            "w5_mode",
            "w5_smart_work",
            "w5_smart_sleep",
            "ctw3_mode",
            "ctw3_smart_work",
            "ctw3_smart_sleep",
        )
        put(
            SettingCategory.WaterCare,
            "auto_refill",
            "auto_water_change",
            "water_change_cycle",
            "water_change_time",
            "auto_flush",
            "flush_cycle",
            "flush_time",
        )
        put(
            SettingCategory.ComfortAndSafety,
            "kitten",
            "underweight",
            "child_lock",
            "w5_child_lock",
            "ctw3_child_lock",
            "click_ok",
        )
        put(
            SettingCategory.Detection,
            "pet_detection",
            "toilet_detection",
            "wander_detection",
            "yowling_detection",
            "ph_detection",
            "drink_detection",
            "eat_detection",
            "move_detection",
            "smart_frame",
        )
        put(
            SettingCategory.Camera,
            "camera",
            "microphone",
            "night_vision",
            "light_assist",
            "toilet_light",
            "camera_light",
            "video_timestamp",
            "time_display",
            "camera_upload",
            "micro_light",
            "feed_picture",
            "eat_video",
            "home_mode",
            "camera_inward",
            "camera_off",
            "no_sound",
        )
        put(
            SettingCategory.Recordings,
            "capability_full_video",
            "capability_event_image",
            "capability_dynamic_video",
            "capability_high_light",
        )
        put(SettingCategory.Sound, "voice_prompt", "volume", "voice_language", "selected_sound")
        put(
            SettingCategory.DisplayAndQuiet,
            "display",
            "indicator_light",
            "w5_light",
            "w5_brightness",
            "ctw3_light",
            "ctw3_brightness",
            "clean_water_lack_light",
            "clean_water_empty_light",
            "waste_water_full_light",
            "disturb_mode",
            "w5_dnd",
            "ctw3_dnd",
            "voice_disturb_mode",
            "voice_disturb_period",
            "refill_disturb_mode",
            "refill_disturb_period",
            "water_level_disturb_mode",
            "signal_lights_disturb_period",
        )
        put(
            SettingCategory.Advanced,
            "cleaning_schedule",
            "feeding_schedule",
            "ctw3_energy_interval",
            "ctw3_sleep_time",
        )
    }

    /**
     * Known settings with no explanation, because what they do on the device is not established.
     * Kept as a list so the gap is deliberate and visible, not an oversight.
     */
    val UNEXPLAINED: Set<String> = setOf(
        "downpos",
        "deep_clean",
        "auto_spray",
        "deep_spray",
        "sand_saving",
        "bury",
        "kitten",
        "underweight",
        "surplus_level",
        "feed_sound",
        "voice_dispense",
        "ctw3_working",
        "w5_dnd",
        "ctw3_dnd",
        "wander_detection",
        "light_assist",
        "toilet_light",
        "camera_light",
        "feed_picture",
        "eat_video",
        "capability_high_light",
        "selected_sound",
        "ctw3_energy_interval",
        "ctw3_sleep_time",
    )

    /** Every key the table places. */
    val KNOWN_KEYS: Set<String> get() = CATEGORIES.keys

    fun category(entity: Entity): SettingCategory = CATEGORIES[entity.key]
        ?: if (entity.category == EntityCategory.Diagnostic) SettingCategory.Diagnostics else SettingCategory.Other

    /**
     * The key of [entity]'s explanation on a device of [family], or null when there is none. It
     * is the entity key, except where one key means different things per family: `disturb_mode`
     * is the cleaning do-not-disturb on a litter box and is unexplained on a feeder.
     */
    fun explanationKey(entity: Entity, family: DeviceFamily): String? = when {
        entity.key !in CATEGORIES || entity.key in UNEXPLAINED -> null
        entity.key == DISTURB_MODE -> if (family == DeviceFamily.LitterBox) "${DISTURB_MODE}_litter" else null
        else -> entity.key
    }

    private const val DISTURB_MODE = "disturb_mode"
}
