package org.petkitlocal.core.designsystem.photo

import androidx.annotation.DrawableRes
import org.petkitlocal.core.designsystem.R

/**
 * PetKit's product photo of a model the app knows (issue #69): the only PetKit images the app
 * ships, © PetKit and not covered by the project's MIT licence (NOTICE). Each one is the owner's
 * shot of the device alone, a lossless WebP of at most 900 px whose pixels were never edited:
 * its white background disappears with a multiply blend on whatever is behind it ([PkDevicePhoto]).
 *
 * A device is matched by its codename and the generation the server reports ([of]).
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
        /**
         * The photo of each generation of each codename, first generation first (petkit-local,
         * `utils/const.py` `DEVICE_NAMES` and `MODEL_GENERATIONS`). PetKit sells several
         * generations that look different under `t4` (Pura Max, Pura Max 2, Pura Max 3), `d4h`
         * (YumShare Solo, Solo 2) and `d4sh` (YumShare Dual-Hopper, Dual-Hopper 2); every other
         * codename is one product. A generation with no photo here (a Pura Max 3) keeps our drawing.
         */
        private val BY_GENERATION: Map<String, List<DevicePhoto>> = mapOf(
            "t3" to listOf(PuraX),
            "t4" to listOf(PuraMax, PuraMax2),
            "t5" to listOf(PurobotMaxPro2),
            "t6" to listOf(PurobotUltra),
            "t7" to listOf(PurobotCrystalDuo),
            "d4" to listOf(FreshElementSolo),
            "d4s" to listOf(FreshElementGemini),
            "d4h" to listOf(YumShareSolo, YumShareSolo2),
            "d4sh" to listOf(YumShareDualHopper, YumShareDualHopper2),
            "w7h" to listOf(EverSweetUltraAi),
            "w5" to listOf(EverSweet3Pro),
            "ctw2" to listOf(EverSweetSolo2),
            "ctw3" to listOf(EverSweetMax2),
            "k3" to listOf(PuraAir),
        )

        /**
         * The photo of a device of codename [type] and [generation] as the server reports them, or
         * null for none: that generation's photo, and never another generation's, so a Pura Max
         * 3 keeps our drawing rather than look like a Pura Max 2.
         *
         * A null [generation] (the server does not know it, the codename has one product, or the
         * server predates generations) is the first: the server then names the device by its
         * codename alone ("Pura Max"), which is the first generation's name, so the photo still
         * matches the name shown (issue #69).
         */
        fun of(type: String, generation: Int?): DevicePhoto? =
            BY_GENERATION[type.lowercase()]?.getOrNull((generation ?: 1) - 1)
    }
}
