package org.petkitlocal.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A `Pet`. Weights are grams. */
@Entity(tableName = "pets")
data class PetRow(
    @PrimaryKey val id: Long,
    /** Its place in the server's list, which also orders the pet colours. */
    val position: Int,
    val name: String,
    /** A JSON array of device ids. */
    @ColumnInfo(name = "device_ids_json") val deviceIdsJson: String,
    @ColumnInfo(name = "entered_weight_g") val enteredWeightG: Double?,
    @ColumnInfo(name = "weight_g") val weightG: Double?,
    @ColumnInfo(name = "weight_learned") val weightLearned: Boolean,
    /** The photo's API path (`/app/v1/pets/{id}/photo`), as the server sent it. */
    val photo: String?,
    /**
     * Its reference face photos, oldest first: the JSON array of `PetFace` the server sent
     * (`[{"id":1,"photo":"/app/v1/pets/1/faces/1/photo"}]`). `[]` from a server that does not
     * send them.
     */
    @ColumnInfo(name = "faces_json", defaultValue = "'[]'") val facesJson: String = "[]",
)
