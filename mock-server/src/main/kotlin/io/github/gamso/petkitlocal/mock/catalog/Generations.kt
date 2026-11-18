package io.github.gamso.petkitlocal.mock.catalog

/**
 * The product name of each generation of the codenames PetKit sells in several, as petkit-local
 * names them (`utils/const.py` `MODEL_GENERATIONS`). A generation not listed here is one the
 * server ignores rather than names by guess.
 */
object Generations {
    private val NAMES: Map<String, Map<Int, String>> =
        mapOf(
            "t4" to mapOf(1 to "Pura Max", 2 to "Pura Max 2", 3 to "Pura Max 3"),
            "d4sh" to mapOf(1 to "YumShare Dual-Hopper", 2 to "YumShare Dual-Hopper 2"),
            "d4h" to mapOf(1 to "YumShare Solo", 2 to "YumShare Solo 2"),
        )

    /** The name of [generation] of codename [type], or null when the server would not name it. */
    fun name(
        type: String,
        generation: Int?,
    ): String? = generation?.let { NAMES[type]?.get(it) }
}
