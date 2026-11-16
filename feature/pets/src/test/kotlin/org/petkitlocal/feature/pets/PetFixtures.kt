package org.petkitlocal.feature.pets

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.home.PetOverview
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.PetStats
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.WeightSample
import org.petkitlocal.core.domain.pets.PetRules
import org.petkitlocal.core.domain.pets.WeightRange
import org.petkitlocal.core.testing.Household

/** The household's two cats as the pet screens show them, on the morning of Household.today. */
@Suppress("MagicNumber") // Fixture data.
object PetFixtures {
    private val today = Household.today
    private val zone = Household.zone

    /** Mochi has reference faces, so a photo; the screenshots show her initial, since none is fetched. */
    val mochi = Household.pets[0].copy(photoUrl = "https://petkit.invalid/app/v1/pets/1/photo")
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
}
