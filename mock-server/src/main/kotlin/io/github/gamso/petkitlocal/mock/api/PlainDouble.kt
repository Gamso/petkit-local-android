package io.github.gamso.petkitlocal.mock.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal

/**
 * A JSON number in plain notation: `1794701500.658`, never `1.794701500658E9` (Kotlin's
 * `Double.toString`). Both parse, but every timestamp of the API is an epoch in seconds, and the
 * real server (Python) writes them plainly; so does the mock.
 */
@OptIn(ExperimentalSerializationApi::class)
fun plainNumber(value: Double): JsonPrimitive =
    if (value.isFinite()) JsonUnquotedLiteral(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()) else JsonPrimitive(value)

object PlainDoubleSerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("PlainDouble", PrimitiveKind.DOUBLE)

    override fun serialize(
        encoder: Encoder,
        value: Double,
    ) {
        if (encoder is JsonEncoder) encoder.encodeJsonElement(plainNumber(value)) else encoder.encodeDouble(value)
    }

    override fun deserialize(decoder: Decoder): Double = decoder.decodeDouble()
}
