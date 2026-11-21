package org.petkitlocal.feature.pets

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import java.time.LocalDate
import kotlinx.serialization.Serializable
import org.petkitlocal.feature.pets.edit.PetEditRoute
import org.petkitlocal.feature.pets.photos.PetPhotosRoute

/** The pets list (SPEC.md §5.5), where Home's "See all" leads. */
@Serializable
data object PetsDestination

/** One pet's page (SPEC.md §5.5); the switcher at its top moves to another pet in place. */
@Serializable
data class PetDestination(val petId: Long)

/** The pet editor: a new pet with [NEW_PET], else the pet [petId]. */
@Serializable
data class PetEditDestination(val petId: Long = NEW_PET) {
    companion object {
        /** No pet yet: the editor creates one. */
        const val NEW_PET = -1L
    }
}

/** A pet's reference face photos: list, add (tips, camera or gallery, crop), delete. */
@Serializable
data class PetPhotosDestination(val petId: Long)

/** Where the pet screens lead; the app wires each to its destination. */
data class PetsNavigation(
    val onBack: () -> Unit = {},
    val onOpenPet: (petId: Long) -> Unit = {},
    /** History narrowed to a pet, on [day] or on today. */
    val onOpenHistory: (petId: Long, day: LocalDate?) -> Unit = { _, _ -> },
    val onAddPet: () -> Unit = {},
    val onEditPet: (petId: Long) -> Unit = {},
    val onOpenPhotos: (petId: Long) -> Unit = {},
    /** The editor saved: back from it, then to the pet's page when it was [created]. */
    val onPetSaved: (petId: Long, created: Boolean) -> Unit = { _, _ -> },
    /** The editor deleted its pet: back to the list, past the pet's own page. */
    val onPetDeleted: () -> Unit = {},
)

fun NavController.navigateToPets() = navigate(PetsDestination)

fun NavController.navigateToPet(petId: Long) = navigate(PetDestination(petId))

fun NavController.navigateToPetEditor(petId: Long = PetEditDestination.NEW_PET) = navigate(PetEditDestination(petId))

fun NavController.navigateToPetPhotos(petId: Long) = navigate(PetPhotosDestination(petId))

/** The editor saved: it closes, and a pet it [created] opens in its place. */
fun NavController.closePetEditor(petId: Long, created: Boolean) {
    popBackStack<PetEditDestination>(inclusive = true)
    if (created) navigateToPet(petId)
}

/**
 * The editor deleted its pet: it closes, and so do the pages of that pet under it, its page and
 * its photos, which would show a pet that is gone.
 */
fun NavController.closeDeletedPet() {
    popBackStack<PetEditDestination>(inclusive = true)
    while (previousBackStackEntry != null && currentBackStackEntry?.destination?.isPetPage() == true) {
        popBackStack()
    }
}

private fun NavDestination.isPetPage(): Boolean = hasRoute<PetDestination>() || hasRoute<PetPhotosDestination>()

fun NavGraphBuilder.petsScreens(navigation: PetsNavigation) {
    composable<PetsDestination> { PetsRoute(navigation) }
    composable<PetDestination> { PetRoute(navigation) }
    composable<PetEditDestination> { PetEditRoute(navigation) }
    composable<PetPhotosDestination> { PetPhotosRoute(navigation) }
}

/** The property of [PetDestination], [PetEditDestination] and [PetPhotosDestination] the pet's id is stored under. */
internal const val PET_ID_KEY = "petId"
