package org.petkitlocal.core.domain.pets

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository

/** The pet editor's data, from the cache: the pet ([petId] null for a new one) and the devices to offer. */
class ObservePetEditorUseCase @Inject constructor(
    private val pets: PetRepository,
    private val devices: DeviceRepository,
) {
    operator fun invoke(petId: Long?): Flow<PetEditorContent> =
        combine(pets.observePets(), devices.observeDevices()) { pets, devices ->
            PetEditorContent(
                pet = petId?.let { id -> pets.firstOrNull { it.id == id } },
                recognitionDevices = PetManagementRules.recognitionDevices(devices),
            )
        }
}

/**
 * Saves the editor: a new pet when [pet] is null, else only the fields that changed. A device the
 * editor did not offer stays assigned ([PetManagementRules.deviceIds]).
 */
class SavePetUseCase @Inject constructor(private val pets: PetRepository) {
    suspend operator fun invoke(pet: Pet?, draft: PetDraft): Outcome<Pet> {
        if (pet == null) return pets.createPet(draft.copy(name = draft.name.trim()))
        val changes = PetManagementRules.changes(pet, draft)
        return if (changes.isEmpty) Outcome.Success(pet) else pets.updatePet(pet.id, changes)
    }
}

class DeletePetUseCase @Inject constructor(private val pets: PetRepository) {
    suspend operator fun invoke(petId: Long): Outcome<Unit> = pets.deletePet(petId)
}

/** The photo screen's data, from the cache; null while the cache does not have the pet. */
class ObservePetPhotosUseCase @Inject constructor(
    private val pets: PetRepository,
    private val devices: DeviceRepository,
) {
    operator fun invoke(petId: Long): Flow<PetPhotosContent?> =
        combine(pets.observePets(), devices.observeDevices()) { pets, devices ->
            val index = pets.indexOfFirst { it.id == petId }
            if (index < 0) return@combine null
            val pet = pets[index]
            PetPhotosContent(
                pet = pet,
                colorIndex = index,
                devices = PetManagementRules.recognitionDevices(devices).filter { it.id in pet.deviceIds },
                allDevices = devices,
            )
        }
}

class RefreshPetFacesUseCase @Inject constructor(private val pets: PetRepository) {
    suspend operator fun invoke(petId: Long): Outcome<PetFaces> = pets.refreshFaces(petId)
}

class AddPetFaceUseCase @Inject constructor(private val pets: PetRepository) {
    suspend operator fun invoke(petId: Long, jpeg: ByteArray): Outcome<FaceUpload> = pets.addFace(petId, jpeg)
}

class DeletePetFaceUseCase @Inject constructor(private val pets: PetRepository) {
    suspend operator fun invoke(petId: Long, faceId: Long): Outcome<Unit> = pets.deleteFace(petId, faceId)
}

class GetPetsSourceUseCase @Inject constructor(private val pets: PetRepository) {
    suspend operator fun invoke(): Outcome<PetsSourceSetting> = pets.petsSource()
}

class SetPetsSourceUseCase @Inject constructor(private val pets: PetRepository) {
    suspend operator fun invoke(source: PetsSource): Outcome<PetsSourceSetting> = pets.setPetsSource(source)
}
