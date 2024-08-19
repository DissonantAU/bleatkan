@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package xyz.dissonant.veadotube.bleatkan.serializable

import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject



object VtResultMessageSerializer : JsonContentPolymorphicSerializer<VtResultMessage>(VtResultMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<VtResultMessage> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.containsKey("payload") -> VtResultMessage.VtResultMessagePayload.serializer()
            jsonObject.containsKey("entries") -> VtResultMessage.VtResultMessageEntries.serializer()
            else -> throw IllegalArgumentException("Unsupported Payload type")
        }
    }
}


object VtResultPayloadSerializer : JsonContentPolymorphicSerializer<VtResultPayload>(VtResultPayload::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<VtResultPayload> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.containsKey("states") -> VtResultPayload.VTResultSEListPayload.serializer()
            jsonObject.containsKey("state") -> when {
                jsonObject.containsKey("png") -> VtResultPayload.VTResultSEThumbPayload.serializer()
                else -> VtResultPayload.VTResultSEPeekPayload.serializer()
            }

            else -> throw IllegalArgumentException("Unsupported Payload type")
        }
    }
}


/* Data Objects */
@Serializable(VtResultMessageSerializer::class)
sealed class VtResultMessage {
    /** Event - e.g. list, payload */
    abstract val event: String

    @Serializable
    data class VtResultMessagePayload(
        override val event: String,
        /** Type - e.g. stateEvents */
        val type: String,
        /** ID - e.g. mini */
        val id: String,
        /** Name - e.g. avatar state */
        val name: String,

        val payload: VtResultPayload
    ) : VtResultMessage()


    @Serializable
    data class VtResultMessageEntries(
        override val event: String,
        val entries: List<Entry>
    ) : VtResultMessage()

}


@Serializable(VtResultPayloadSerializer::class)
sealed class VtResultPayload {
    abstract val event: String

    //Multiple State Values
    @Serializable
    data class VTResultSEListPayload(
        override val event: String,
        val states: List<State>
    ) : VtResultPayload()

    //Single State Value
    @Serializable
    data class VTResultSEPeekPayload(
        override val event: String,
        val state: String
    ) : VtResultPayload()

    //Single State Value
    @Serializable
    data class VTResultSEThumbPayload(
        override val event: String,
        val state: String,
        val width: Int,
        val height: Int,
        /**
         * PNG Encoded as a Base64 Encoded String
         *
         * @see pngAsBytes to get PNG as a decoded ByteArray
         */
        val png: String
    ) : VtResultPayload() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as VTResultSEThumbPayload

            if (event != other.event) return false
            if (state != other.state) return false
            if (width != other.width) return false
            if (height != other.height) return false
            if (!png.contentEquals(other.png)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = event.hashCode()
            result = 31 * result + state.hashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + png.hashCode()
            return result
        }

        /**
         * Encodes PNG as Base64 Encoded String
         * @return PNG encoded as a Base64 String
         */
        fun pngAsString(): String {
            return png
        }

        /**
         * Returns a copy of the PNG Byte Array
         * @return PNG as a Byte Array
         */
        fun pngAsBytes(): ByteArray {
            return png.decodeBase64Bytes()
        }

        override fun toString(): String {
            return "VTResultSEThumbPayload(event='$event', state='$state', width=$width, height=$height, png={hash=${png.hashCode()}, count=${png.count()}})"
        }
    }
}


@Serializable
data class State(
    val id: String,
    val name: String
)


@Serializable
data class Entry(
    val type: String,
    val id: String,
    val name: String
)
