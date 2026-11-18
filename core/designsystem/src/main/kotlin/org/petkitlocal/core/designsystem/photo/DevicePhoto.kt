package org.petkitlocal.core.designsystem.photo

import androidx.annotation.DrawableRes
import org.petkitlocal.core.designsystem.R

/**
 * PetKit's product photo of a model the app knows (issue #69): the only PetKit images the app
 * ships, © PetKit and not covered by the project's MIT licence (NOTICE). Each one is the owner's
 * shot of the device alone, a lossless WebP of at most 900 px whose pixels were never edited:
 * its white background disappears with a multiply blend on whatever is behind it ([PkDevicePhoto]).
 *
 * A device is matched by its codename, and by its generation where PetKit sells several under one
 * codename ([of]).
 */
enum class DevicePhoto(@param:DrawableRes val drawable: Int) {
    PuraX(R.drawable.device_photo_pura_x_t3),
    PuraMax(R.drawable.device_photo_puramax_t4),
    PuraMax2(R.drawable.device_photo_puramax_2_t4_2),
    PurobotMaxPro2(R.drawable.device_photo_purobot_max_pro_2_t5),
    PurobotUltra(R.drawable.device_photo_purobot_ultra_t6),
    PurobotCrystalDuo(R.drawable.device_photo_purobot_crystal_duo_t7),
    FreshElementSolo(R.drawable.device_photo_fresh_element_solo_d4),
    FreshElementGemini(R.drawable.device_photo_fresh_element_gemini_d4s),
    YumShareSolo(R.drawable.device_photo_yumshare_solo_d4h),
    YumShareSolo2(R.drawable.device_photo_yumshare_solo_2_d4h),
    YumShareDualHopper(R.drawable.device_photo_yumshare_dual_hopper_d4sh),
    YumShareDualHopper2(R.drawable.device_photo_yumshare_dual_hopper_2_d4sh),
    EverSweetUltraAi(R.drawable.device_photo_eversweet_ultra_ai_w7h),
    EverSweet3Pro(R.drawable.device_photo_eversweet_3_pro_w5),
    EverSweetSolo2(R.drawable.device_photo_eversweet_solo_2_ctw2),
    EverSweetMax2(R.drawable.device_photo_eversweet_max_2_ctw3),
    PuraAir(R.drawable.device_photo_pura_air_k3),
    ;

    companion object {
        /** One product per codename (petkit-local, `utils/const.py` `DEVICE_NAMES`). */
        private val BY_TYPE = mapOf(
            "t3" to PuraX,
            "t5" to PurobotMaxPro2,
            "t6" to PurobotUltra,
            "t7" to PurobotCrystalDuo,
            "d4" to FreshElementSolo,
            "d4s" to FreshElementGemini,
            "w7h" to EverSweetUltraAi,
            "w5" to EverSweet3Pro,
            "ctw2" to EverSweetSolo2,
            "ctw3" to EverSweetMax2,
            "k3" to PuraAir,
        )

        /**
         * Codenames PetKit uses for products of several generations that look different, by
         * generation: the Pura Max and Pura Max 2 (`t4`), the YumShare Solo and Solo 2 (`d4h`), the
         * YumShare Dual-Hopper and Dual-Hopper 2 (`d4sh`). A generation with no photo (a Pura Max 3)
         * keeps our drawing.
         */
        private val GENERATIONS = mapOf(
            "t4" to listOf(PuraMax, PuraMax2),
            "d4h" to listOf(YumShareSolo, YumShareSolo2),
            "d4sh" to listOf(YumShareDualHopper, YumShareDualHopper2),
        )

        private val TRAILING_NUMBER = Regex("""\b(\d+)$""")

        /**
         * The generation of a product, read from the name the server gives it: its trailing number
         * ("Pura Max 2"), else the first. `/app/v1` has no generation of its own yet and petkit-local
         * names both generations alike today ("Pura Max", `utils/const.py` `DEVICE_NAMES`), so the
         * photo always matches the name the app shows. The one place to change when the server
         * reports the generation (petkit-local#107, app issue #81).
         */
        internal fun generationOf(model: String): Int =
            TRAILING_NUMBER.find(model.trim())?.groupValues?.get(1)?.toIntOrNull() ?: 1

        /** The photo of a device of codename [type] named [model] by the server, or null for none. */
        fun of(type: String, model: String): DevicePhoto? {
            val code = type.lowercase()
            return BY_TYPE[code] ?: GENERATIONS[code]?.getOrNull(generationOf(model) - 1)
        }
    }
}
