package org.petkitlocal.feature.pets

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** The pets list (SPEC.md §5.5), where Home's "See all" leads. */
@Serializable
data object PetsDestination

/** One pet's page (SPEC.md §5.5); the switcher at its top moves to another pet in place. */
@Serializable
data class PetDestination(val petId: Long)

/** Where the pet screens lead; the app wires each to its destination. */
data class PetsNavigation(
    val onBack: () -> Unit = {},
    val onOpenPet: (petId: Long) -> Unit = {},
    /** History narrowed to a pet, on [day] or on today. */
    val onOpenHistory: (petId: Long, day: LocalDate?) -> Unit = { _, _ -> },
)

fun NavController.navigateToPets() = navigate(PetsDestination)

fun NavController.navigateToPet(petId: Long) = navigate(PetDestination(petId))

fun NavGraphBuilder.petsScreens(navigation: PetsNavigation) {
    composable<PetsDestination> { PetsRoute(navigation) }
    composable<PetDestination> { PetRoute(navigation) }
}

/** The [PetDestination] property the pet's id is stored under. */
internal const val PET_ID_KEY = "petId"
