package org.petkitlocal.feature.pets

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.home.PetOverview
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.PetFace
import org.petkitlocal.core.domain.model.PetStats
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.WeightSample
import org.petkitlocal.core.domain.pets.FaceWarning
import org.petkitlocal.core.domain.pets.PetPhotosContent
import org.petkitlocal.core.domain.pets.PetRules
import org.petkitlocal.core.domain.pets.PetsSource
import org.petkitlocal.core.domain.pets.PetsSourceSetting
import org.petkitlocal.core.domain.pets.WeightRange
import org.petkitlocal.core.testing.Household
import org.petkitlocal.feature.pets.edit.PetEditUiState
import org.petkitlocal.feature.pets.edit.WeightProblem
import org.petkitlocal.feature.pets.photos.CropProblem
import org.petkitlocal.feature.pets.photos.PetPhotosUiState
import org.petkitlocal.feature.pets.photos.PhotoNotice
import org.petkitlocal.feature.pets.photos.PhotoStep

/** The household's two cats as the pet screens show them, on the morning of Household.today. */
@Suppress("MagicNumber") // Fixture data.
object PetFixtures {
    private val today = Household.today
    private val zone = Household.zone

    /**
     * Mochi has two reference faces, so a photo; the screenshots show her initial and placeholders,
     * since none is fetched. The T5 took both in; the W7H has not yet taken the second.
     */
    val mochi = Household.pets[0].copy(
        photoUrl = "https://petkit.invalid/app/v1/pets/1/faces/11/photo",
        faces = listOf(
            PetFace(11, "https://petkit.invalid/app/v1/pets/1/faces/11/photo", listOf(1003, 3001), emptyList()),
            PetFace(12, "https://petkit.invalid/app/v1/pets/1/faces/12/photo", listOf(1003), listOf(3001)),
        ),
    )
    val tigrou = Household.pets[1]
    val pets = listOf(mochi, tigrou)

    fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant =
        ZonedDateTime.of(day.atTime(hour, minute), zone).toInstant()

    fun visit(id: Long, day: LocalDate, hour: Int, minute: Int, weight: Int, device: Long = 1002, pet: Long = 1) =
        Household.item(id, device, at(day, hour, minute), TimelineCategory.Toileting, pet = pet, duration = 95)
            .copy(weightG = weight)

    val recentVisits: List<TimelineItem> = listOf(
        visit(205, today, 9, 12, 4290),
        visit(204, today, 6, 40, 4330, device = 1003),
        visit(203, today, 1, 5, 4310),
        visit(202, today.minusDays(1), 21, 30, 4300),
        visit(201, today.minusDays(3), 18, 2, 4280, device = 1003),
    )

    val activity = PetActivity(
        petId = 1,
        stats = PetStats(
            medianWeightG = 4310.0,
            visitsToday = 3,
            lastVisitAt = at(today, 9, 12),
            lastVisitWeightG = 4290.0,
            lastVisitDurationSec = 95,
            lastDeviceId = 1002,
        ),
        recentVisits = recentVisits,
        fetchedAt = at(today, 9, 30),
    )

    /**
     * About a month of weighed visits, a slow gain with the day-to-day wobble of a real scale, and
     * three days with none: the line breaks there.
     */
    val weights: List<WeightSample> = (0L until 30L)
        .filter { it !in listOf(8L, 9L, 17L) }
        .flatMap { daysAgo ->
            val day = today.minusDays(daysAgo)
            val base = 4200 + (30 - daysAgo) * 4 + (daysAgo % 3) * 15
            listOf(
                WeightSample(daysAgo * 10 + 1, at(day, 7), base.toInt()),
                WeightSample(daysAgo * 10 + 2, at(day, 19), (base + 20).toInt()),
            )
        }

    /** Ten weeks of a pet added two months ago: the six-month chart starts with its first visit. */
    val halfYearWeights: List<WeightSample> = (0L until 70L step 2).map { daysAgo ->
        WeightSample(1000 + daysAgo, at(today.minusDays(daysAgo), 8), (4150 + (70 - daysAgo) * 2).toInt())
    }

    /** The household's devices; the T5 and the W7H recognise pets by their face. */
    val devices = Household.devices.map { if (it.id in setOf(1003L, 3001L)) it.copy(supportsAi = true) else it }

    private val recognitionDevices = devices.filter { it.supportsAi }

    /** The two cats as the repository has them: Mochi with her two faces. */
    val petsWithFaces = listOf(mochi, tigrou)

    private fun page(
        activity: PetActivity? = this.activity,
        weights: List<WeightSample> = this.weights,
        range: WeightRange = WeightRange.Month,
        petId: Long = 1,
    ) = PetRules.page(petId, pets, activity, weights, Household.devices, range, today, zone)

    fun state(
        page: Boolean = true,
        range: WeightRange = WeightRange.Month,
        weights: List<WeightSample> = this.weights,
        activity: PetActivity? = this.activity,
    ) = PetUiState(
        petId = 1,
        today = today,
        zone = zone,
        range = range,
        pets = pets,
        page = if (page) page(activity, weights, range) else null,
        cacheLoaded = true,
        loading = false,
        weightsLoading = false,
    )

    val content = state()

    val halfYear = state(range = WeightRange.HalfYear, weights = halfYearWeights)

    /** Tigrou: no photo, an entered weight, never read today, no weighed visit. */
    val sparse = PetUiState(
        petId = 2,
        today = today,
        zone = zone,
        pets = pets,
        page = page(activity = null, weights = emptyList(), petId = 2),
        cacheLoaded = true,
        loading = false,
        weightsLoading = false,
    )

    val loading = PetUiState(petId = 1, today = today, zone = zone, pets = pets, cacheLoaded = true)

    val failed = loading.copy(loading = false, error = DomainError.Network(), weightsLoading = false)

    val gone = loading.copy(
        loading = false,
        error = DomainError.NotFound("pet_not_found", "no such pet"),
        weightsLoading = false,
    )

    val list = PetsUiState(
        pets = listOf(PetOverview(mochi, 0, visitsToday = 3), PetOverview(tigrou, 1, visitsToday = 1)),
    )

    val petsSourceSetting =
        PetsSourceSetting(PetsSource.PetKit, proxyMode = true, PetsSource.PetKit, PetsSource.entries)

    /** The list in proxy mode: whose pets the cameras recognise, PetKit's for now. */
    val listInProxyMode = list.copy(petsSource = petsSourceSetting)

    val listEmpty = PetsUiState(pets = emptyList())

    val editNew = PetEditUiState(isNew = true, loaded = true, devices = recognitionDevices)

    val editMochi = PetEditUiState(
        isNew = false,
        pet = mochi,
        loaded = true,
        name = "Mochi",
        weight = "4.2",
        selected = mochi.deviceIds.toSet(),
        devices = recognitionDevices,
    )

    /** Saved with nothing in the name, a weight out of range, and the server unreachable before that. */
    val editProblems = editNew.copy(
        name = "",
        weight = "140",
        nameMissing = true,
        weightProblem = WeightProblem.OutOfRange,
        error = DomainError.Network(),
    )

    val editNoDevice = editNew.copy(devices = emptyList(), name = "Nala")

    val photos = PetPhotosUiState(
        petId = 1,
        content = PetPhotosContent(mochi, colorIndex = 0, devices = recognitionDevices, allDevices = devices),
        cacheLoaded = true,
        maxFaces = 6,
        loading = false,
    )

    /** Add pressed with the most photos a pet may have (two, on a stricter server). */
    val photosAtLimit = photos.copy(maxFaces = 2, notice = PhotoNotice.AtLimit(2))

    /** Tigrou: no photo yet, and no camera that recognises faces assigned. */
    val photosEmpty = PetPhotosUiState(
        petId = 2,
        content = PetPhotosContent(tigrou, colorIndex = 1, devices = emptyList()),
        cacheLoaded = true,
        maxFaces = 6,
        loading = false,
    )

    val tips = photos.copy(step = PhotoStep.Tips())

    val crop = photos.copy(step = PhotoStep.Crop("content://photo/1", CropProblem.TooSmall(180)))

    val review = photos.copy(
        step = PhotoStep.Review(mochi.faces[1], listOf(FaceWarning.Blurry, FaceWarning.TooDark)),
    )
}
