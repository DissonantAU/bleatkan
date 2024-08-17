package xyz.dissonant.veadotube.bleatkan.serializable

import kotlinx.serialization.*

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Encoder


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

@OptIn(ExperimentalEncodingApi::class)
object ByteArrayAsBase64Serializer : KSerializer<ByteArray> {
    private val base64 = Base64.Default

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(
            "ByteArrayAsBase64Serializer",
            PrimitiveKind.STRING
        )

    override fun serialize(encoder: Encoder, value: ByteArray) {
        val base64Encoded = base64.encode(value)
        encoder.encodeString(base64Encoded)
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        val base64Decoded = decoder.decodeString()
        return base64.decode(base64Decoded)
    }
}

/* Data Objects */
@Serializable(VtResultMessageSerializer::class)
sealed class VtResultMessage {
    abstract val event: String

    @Serializable
    data class VtResultMessagePayload(
        override val event: String,
        val type: String,
        val id: String,
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
         * PNG Encoded as a Byte Array
         * Decoded to a byte Array from Base64 String when received
         *
         * @see pngAsString to get PNG as Base64 String
         */
        @Serializable(with = ByteArrayAsBase64Serializer::class)
        val png: ByteArray
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
            result = 31 * result + png.contentHashCode()
            return result
        }

        /**
         * Encodes png as String, similar to how it's sent over Websocket
         * @return PNG encoded as a Base64 String
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun pngAsString(): String {
            return Base64.Default.encode(png)
        }

        override fun toString(): String {
            return "VTResultSEThumbPayload(event='$event', state='$state', width=$width, height=$height, png={hash=${png.contentHashCode()}, count=${png.count()}})"
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


