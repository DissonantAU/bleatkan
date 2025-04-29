@file:Suppress("MemberVisibilityCanBePrivate", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package io.github.dissonantau.bleatkan.message


import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonClassDiscriminator


/**
 * Result Message
 *
 * Generic Class - not used directly
 *
 * @see ResultMessageWithEntryList
 * @see ResultMessageWithEntryList
 */
@Serializable(ResultMessageDeserializer::class)
//@JsonClassDiscriminator("event")
sealed class ResultMessage {
    //e.g. Current State, List of States, State Thumbnail
    abstract val event: String

    /**
     * Result Message with a Payload
     */
    @Serializable
    //@SerialName("payload")
    data class ResultMessageWithPayload(
        override val event: String,
        /** Type - e.g. stateEvents */
        val type: String,
        /** ID - e.g. mini */
        val id: String,
        /** Name - e.g. avatar state */
        val name: String,
        /** Payload - e.g. Current State, List of States, State Thumbnail */
        val payload: ResultPayload
    ) : ResultMessage()

    /**
     * Result Message with Boolean Payload
     */
    @Serializable
    //@SerialName("payload") //type number
    data class ResultMessageWithPayloadNumber(
        override val event: String,
        /** Type - e.g. number */
        val type: String,
        /** ID - e.g. mini */
        val id: String,
        /** Name - e.g. avatar state */
        val name: String,
        /** Payload - e.g. Current Value */
        val payload: ResultPayloadSpecialNumber
    ) : ResultMessage()

    /**
     * Result Message with Boolean Payload
     *
     * See https://veado.tube/docs/tech/api/nodes/#boolean
     */
    @Serializable
    //@SerialName("payload")//type boolean
    data class ResultMessageWithPayloadBoolean(
        override val event: String,
        /** Type - e.g. boolean */
        val type: String,
        /** ID - e.g. mini */
        val id: String,
        /** Name - e.g. avatar state */
        val name: String,
        /** Payload - e.g. Current Value */
        val payload: Boolean
    ) : ResultMessage()

    /**
     * Result Message with a List of Entries
     */
    @Serializable
    //@SerialName("list")
    data class ResultMessageWithEntryList(
        override val event: String,
        val entries: List<Entry>
    ) : ResultMessage()

    /**
     * Result Message with Instance info similar to [io.github.dissonantau.bleatkan.message.VeadoInstanceFile]
     *
     * This was added to API in mini version 2.1
     */
    @Serializable
    //@SerialName("info")
    data class ResultMessageWithInstanceInfo(
        override val event: String,
        /** Instance ID
         *
         * Same as name of Instance File Name/[io.github.dissonantau.bleatkan.instance.InstanceID]
         */
        val id: String,
        /** Instance Server address - *IP:Port* */
        val server: String,
        /** Instance name/title */
        val name: String,
        /** Instance Version - "2.1a" */
        val version: String,
    ) : ResultMessage()

    /**
     * Channel message was received from
     *
     * Transient value not included in JSON, but is added after decoding for use if needed
     *
     * Blank by default
     */
    @Transient
    var channel: String = ""
        internal set

}

/**
 * Result Payload
 *
 * e.g. Current State, List of States, State Thumbnail
 *
 * Generic Class - not used directly
 *
 * @see ResultPayloadState
 * @see ResultPayloadStateList
 * @see ResultPayloadPng
 */
@Serializable //(ResultPayloadDeserializer::class)
@JsonClassDiscriminator("event")
sealed class ResultPayload {
    abstract val event: String

    /** Payload with a List of States - e.g. List of Avatar States */
    @Serializable
    @SerialName("list")
    data class ResultPayloadStateList(
        override val event: String,
        val states: List<State>
    ) : ResultPayload()

    /** Payload with a Single State - e.g. Current Avatar State */
    @Serializable
    @SerialName("peek")
    data class ResultPayloadState(
        override val event: String,
        val state: String
    ) : ResultPayload()

    /** Payload with a State Thumbnail - e.g. Avatar State Thumbnail */
    @Serializable
    @SerialName("thumb")
    data class ResultPayloadPng(
        override val event: String,
        val state: String,
        /**
         * PNG Width in Pixels
         */
        val width: Int,
        /**
         * PNG Height in Pixels
         */
        val height: Int,
        /**
         * PNG Encoded as a Base64 Encoded String
         *
         * @see pngAsBytes to get PNG as a decoded ByteArray
         */
        val png: String,
        /**
         * Hash for Thumbnail
         *
         * Added 2.1, can be used to identify changes to State Thumbnail (e.g. to clear cached thumbnails when a state is changed)
         *
         * Consistent during the lifetime of a single run, but not across restarts
         *
         */
        val hash: String? = null,
    ) : ResultPayload() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ResultPayloadPng

            if (hash != other.hash) return false
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
            hash?.let { result = 31 * result + hash.hashCode() }
            return result
        }

        /**
         * Encodes PNG as Base64 Encoded String
         * @return PNG encoded as a Base64 String
         */
        fun pngAsString(): String = png


        /**
         * Returns a copy of the PNG Byte Array
         * @return PNG as a Byte Array
         */
        fun pngAsBytes(): ByteArray = png.decodeBase64Bytes()


        override fun toString(): String {
            // If received hash (2.1+)
            if (hash != null) return "ResultPayloadPng(event='$event', state='$state', width=$width, height=$height, hash=$hash, png={hashCode:${png.hashCode()}, count=${png.count()}})"
            // If not (2.0/a)
            return "ResultPayloadPng(event='$event', state='$state', width=$width, height=$height, png={hashCode:${png.hashCode()}, count=${png.count()}})"
        }
    }
}

/**
 * Payload with Number Value/Min/Max
 *
 * Values are null if they weren't defined in the payload
 *
 * More Info https://veado.tube/docs/tech/api/nodes/#number
 */
@Serializable
data class ResultPayloadSpecialNumber(
    val value: Double? = null,
    val min: Double? = null,
    val max: Double? = null
)


@Serializable
data class State(
    /**
     * Unique ID of State
     *
     * - 2.0/2.0a: is a short Base64 Value, consistent across saves and rearranged items
     * - 2.1 and later: Name is unique, and is used instead
     */
    val id: String,
    /**
     * Name of State
     *
     * - 2.0/2.0a: is NOT Unique, multiple States can share a name
     * - 2.1 and later: Name is unique
     */
    val name: String,
    /**
     * Hash for Thumbnail
     *
     * Added 2.1, can be used to identify changes to State Thumbnail (e.g. to clear cached thumbnails when a state is changed)
     *
     * Consistent during the lifetime of a single run, but not across restarts
     *
     */
    val thumbHash: String? = null
)


@Serializable
data class Entry(
    val type: String,
    val id: String,
    val name: String
)
