package org.petkitlocal.core.domain.pets

import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetFace

/**
 * A pet as the owner fills it in: created, or edited (`POST`/`PATCH /pets`). Weights are grams.
 * [deviceIds] are the devices that recognise it by its face (`Device.supportsAi`).
 */
data class PetDraft(val name: String, val weightG: Double?, val deviceIds: List<Long>)

/** What changes in an existing pet: a null field stays as it is. */
data class PetChanges(
    val name: String? = null,
    /** A new weight; see [clearWeight] to remove it. */
    val weightG: Double? = null,
    val clearWeight: Boolean = false,
    val deviceIds: List<Long>? = null,
) {
    val isEmpty: Boolean get() = name == null && weightG == null && !clearWeight && deviceIds == null
}

/** A pet's reference photos as the server lists them, and how many one pet may have. */
data class PetFaces(val faces: List<PetFace>, val maxFaces: Int)

/** A photo the server stored, and what it found wrong with it, when it checks (petkit-local#132). */
data class FaceUpload(val face: PetFace, val warnings: List<FaceWarning>)

/** What the server may find wrong with a reference photo; none of them blocks it. */
enum class FaceWarning {
    Blurry,
    TooDark,
    Overexposed,

    /** Far from square: probably not cropped around the head. */
    NotSquare,
}

/** Whose pets the devices recognise while the server relays PetKit's cloud (proxy mode). */
enum class PetsSource {
    /** PetKit's app's pets: pets edited here are not used for recognition. */
    PetKit,

    /** petkit-local's own: the pets of this app. */
    Local,
}

/**
 * The "Pets for recognition" setting (`/settings/pets-source`). It matters only while [proxyMode]
 * is on, which the panel alone sets; [effective] is whose pets the devices get now.
 */
data class PetsSourceSetting(
    val source: PetsSource,
    val proxyMode: Boolean,
    val effective: PetsSource,
    /** The values to offer, in the server's order. */
    val options: List<PetsSource>,
)

/** One device a pet's photos go to, and whether it said it took a photo in. */
data class FaceDeviceStatus(val device: Device, val accepted: Boolean)

/** What the pet editor needs: the pet (null for a new one) and the devices to offer. */
data class PetEditorContent(
    /** Null for a new pet, and for one the cache no longer has. */
    val pet: Pet?,
    /** The devices that recognise pets by their face, in the server's order. */
    val recognitionDevices: List<Device>,
)

/** What the photo screen shows: the pet, its colour, and the devices its photos go to. */
data class PetPhotosContent(
    val pet: Pet,
    val colorIndex: Int,
    /** The pet's own devices that recognise pets by their face. */
    val devices: List<Device>,
    /** Every device, which names the ones a photo's status lists. */
    val allDevices: List<Device> = devices,
)

/** The rules of managing pets, from the cache's data. */
object PetManagementRules {
    /** The heaviest weight that can be typed, as the server takes it. */
    const val MAX_WEIGHT_G = 100_000.0

    /** The devices a pet's photos can be sent to: those that recognise pets by their face. */
    fun recognitionDevices(devices: List<Device>): List<Device> = devices.filter { it.supportsAi && !it.isAccessory }

    /**
     * The devices that hold [face] and those still to fetch it, as the server says, in that order,
     * each one the cache knows; null from a server that does not say (older than petkit-local#132).
     */
    fun faceStatus(face: PetFace, devices: List<Device>): List<FaceDeviceStatus>? {
        val accepted = face.acceptedBy ?: return null
        val byId = devices.associateBy { it.id }
        return accepted.mapNotNull { byId[it] }.map { FaceDeviceStatus(it, accepted = true) } +
            face.waitingFor.orEmpty().filterNot { it in accepted }.mapNotNull { byId[it] }
                .map { FaceDeviceStatus(it, accepted = false) }
    }

    /**
     * What an edit changes, field by field: only those are sent, so a field changed meanwhile
     * elsewhere (the panel) is not overwritten with what the editor opened with. A weight the
     * editor shows rounded (to 10 g) is not a change.
     */
    fun changes(pet: Pet, draft: PetDraft): PetChanges {
        val weightChanged = when {
            draft.weightG == null -> pet.enteredWeightG != null
            else -> pet.enteredWeightG == null || kotlin.math.abs(pet.enteredWeightG - draft.weightG) >= WEIGHT_STEP_G
        }
        return PetChanges(
            name = draft.name.trim().takeIf { it != pet.name },
            weightG = draft.weightG.takeIf { weightChanged },
            clearWeight = weightChanged && draft.weightG == null,
            deviceIds = draft.deviceIds.takeIf { it.toSet() != pet.deviceIds.toSet() },
        )
    }

    /**
     * The devices to save for [pet] when the editor offered [offered] and the owner picked
     * [picked] among them: a device the editor did not offer (a litter box that tells the pet by
     * its weight, set in the panel) stays assigned.
     */
    fun deviceIds(pet: Pet?, offered: List<Device>, picked: Set<Long>): List<Long> {
        val offeredIds = offered.map { it.id }.toSet()
        val kept = pet?.deviceIds.orEmpty().filter { it !in offeredIds }
        return kept + offered.map { it.id }.filter { it in picked }
    }

    /** The precision a weight is typed with: 0.01 kg. */
    private const val WEIGHT_STEP_G = 10.0
}
