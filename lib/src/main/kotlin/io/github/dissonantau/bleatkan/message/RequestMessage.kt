@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package io.github.dissonantau.bleatkan.message


import kotlinx.serialization.*
import kotlinx.serialization.json.Json


/**
 * Class used to represent Veadotube Request Messages.
 *
 * The [validate] function can be used to help validate the message
 *
 * Must always have an [event] value at minimum
 */
@Serializable(RequestMessageDeserializer::class)
sealed class RequestMessage {
    abstract val event: String

    /**
     * Message for Listing Nodes
     */
    @Serializable
    data class RequestMessageNodeList(
        override val event: String
    ) : RequestMessage()

    @Serializable
    data class RequestMessageNodeEvent(

        override val event: String,
        /**
         * Node Type to send the request to
         *
         * e.g. stateEvents
         */
        val type: String,
        /**
         * Node ID to send the request to
         *
         * e.g. mini for veadotube mini
         * Veadotube 'full' needs actual node ID - will be random per-node such as `299f3e5e` unless set manually
         */
        val id: String,
        /**
         * Payload to send to node [type] / [id]
         */
        val payload: RequestPayload
    ) : RequestMessage() {
        constructor(
            event: MessageEvent, type: MessagePayloadType,
            id: String, payload: RequestPayload
        ) : this(
            event = event.formattedName, type = type.formattedName,
            id = id, payload = payload
        )
    }


    /**
     * Message for Listing Nodes
     */
    @Serializable
    data class RequestMessageInstanceInfo(
        override val event: String
    ) : RequestMessage()

    /**
     * Message for listening for Node list changes
     *
     * @param event the action to be carried out
     *
     * e.g. [PayloadEvent.LISTEN] (*Listen*) will result in a State message being sent every time it changes, even when changed through the GUI or another API request.
     *
     * @param token unique id for event, same token needs to be used in subsequent related requests
     *
     * e.g. a [PayloadEvent.LISTEN] (*Listen*) Request using token '*abc123*' can be removed later by sending a [PayloadEvent.UNLISTEN] (*Unlisten*) Request with the same token
     */
    @Serializable
    data class RequestMessageNodeEventToken(
        override val event: String,
        /**
         * Unique ID for the listener - can be anything
         * Token sent for UnListen must be the same as original Listen Request
         */
        val token: String
    ) : RequestMessage()

    fun toJsonString(): String {
        return Json.encodeToString(this)
    }

    /**
     * Validates Data in the Request
     *
     * Convenience function for [VeadoRequest.validateRequestMessage]
     *
     * Checks the Data Class and contents to make sure the combination is valid using [PayloadEvent] Values.
     *
     * If the Request has a Payload, it will also be validated
     *
     *
     * @return true if tests passed
     * @throws IllegalStateException if any invalid value is found. Message contains details of issues
     *
     * @see [VeadoRequest.validateRequestMessage] Use to validate Payload Contents if applicable
     */
    fun validate() = VeadoRequest.validateRequestMessage(this)

}


/**
 * Class used to restrict what can be a payload for [RequestMessage.RequestMessageNodeEvent.payload]
 *
 * Must always have an [event] value at minimum
 */
@Serializable(RequestPayloadDeserializer::class)
sealed class RequestPayload {
    abstract val event: String

    /**
     * Used for basic Request Payloads, like Peek
     *
     * @param event the action to be carried out
     *
     * e.g. [PayloadEvent.LIST] (*list*) will return the possible state values, [PayloadEvent.PEEK] (*peek*) will return the current state value, [PayloadEvent.GET] (*get*) for boolean
     */
    @Serializable
    class RequestPayloadEvent(
        override val event: String
    ) : RequestPayload() {
        constructor(event: PayloadEvent) : this(event.formattedName)
    }

    /**
     * Used for Request Payloads that need a token, usually Listeners
     *
     * @param event the action to be carried out
     *
     * e.g. [PayloadEvent.LISTEN] (*Listen*) will result in a State message being sent every time it changes, even when changed through the GUI or another API request.
     *
     * @param token unique id for event, same token needs to be used in subsequent related requests
     *
     * e.g. a [PayloadEvent.LISTEN] (*Listen*) Request using token '*abc123*' can be removed later by sending a [PayloadEvent.UNLISTEN] (*Unlisten*) Request with the same token
     */
    @Serializable
    data class RequestPayloadEventToken(
        override val event: String,
        /**
         * Unique ID for the listener - can be anything
         * Token sent for UnListen must be the same as original Listen Request
         */
        val token: String
    ) : RequestPayload() {
        constructor(event: PayloadEvent, token: String) : this(event.formattedName, token)
    }

    /**
     * Used for Request Payloads that need a State String, e.g. Set/Push/Pop/Thumb
     *
     * @param event the action to be carried out
     *
     * e.g. [PayloadEvent.SET] (*Set*) will change the State (Current Displayed group of PNGs) to the State ID provided
     *
     * @param state unique id for a state
     *
     * i.e. the State IF from a [MessageEvent.PAYLOAD] / [MessagePayloadType.STATE_EVENTS] / [PayloadEvent.LIST] request
     */
    @Serializable
    data class RequestPayloadEventStateString(
        override val event: String,
        /**
         * Unique ID for the State
         *
         * i.e. the state id from a [MessageEvent.PAYLOAD] / [MessagePayloadType.STATE_EVENTS] / [PayloadEvent.LIST] request
         */
        val state: String
    ) : RequestPayload() {
        constructor(event: PayloadEvent, state: String) : this(event.formattedName, state)
    }

    /**
     * Used for Request Payloads that need a Boolean Value
     *
     * @param event the action to be carried out
     *
     * e.g. [PayloadEvent.SET] (*Set*) will change the State (Current Displayed group of PNGs) to the State ID provided
     *
     * @param value true or false
     *
     */
    @Serializable
    data class RequestPayloadEventValueBoolean(
        override val event: String,
        /**
         * Unique ID for the State
         *
         * i.e. the state id from a [MessageEvent.PAYLOAD] / [MessagePayloadType.STATE_EVENTS] / [PayloadEvent.LIST] request
         */
        val value: Boolean
    ) : RequestPayload() {
        constructor(event: PayloadEvent, value: Boolean) : this(event.formattedName, value)
    }

    /**
     * Used for Request Payloads that needs a String Value
     *
     * @param event the action to be carried out
     *
     * e.g. [PayloadEvent.SET] (*Set*) will change the State based on the String Provided
     * * A Boolean Node accepts "toggle" or "clear"
     *
     * @param value String for node, e.g. "toggle" or "clear"
     *
     */
    @Serializable
    data class RequestPayloadEventValueString(
        override val event: String,
        /**
         * String for event. e.g. "toggle" or "clear"
         */
        val value: String
    ) : RequestPayload() {
        constructor(event: PayloadEvent, value: String) : this(event.formattedName, value)
    }

    /**
     * Used for Request Payloads that need a Value Number
     *
     * @param event the action to be carried out
     *
     * e.g. [PayloadEvent.SET] (*Set*) will change the number to the value
     * [PayloadEvent.ADD] (*Add*) will add the number to the value (negative number subtracts)
     *
     * @param value a number (Float/Double or Integer)
     *
     */
    @Serializable
    data class RequestPayloadEventValueNumber(
        override val event: String,
        val value: Double
    ) : RequestPayload() {
        constructor(event: PayloadEvent, value: Double) : this(event.formattedName, value)
    }

    /**
     * Used for Request Payloads that need a number and min/max values
     *
     * @param event the action to be carried out
     *
     * e.g. [PayloadEvent.SET] (*Set*) will change the number to the value
     * [PayloadEvent.ADD] (*Add*) will add the number to the value (negative number subtracts)
     *
     * @param value [RequestPayloadEventNumberValueMulti]
     *
     */
    @Serializable
    data class RequestPayloadEventValueNumberMinMax(
        override val event: String,
        val value: RequestPayloadEventNumberValueMulti
    ) : RequestPayload() {
        constructor(event: PayloadEvent, value: RequestPayloadEventNumberValueMulti)
                : this(event.formattedName, value)
    }

    /**
     * Encodes Request Payload as JSON String
     */
    fun toJsonString(): String {
        return Json.encodeToString(this)
    }

    /**
     * Validates Data in a Payload.
     *
     * Convenience function for [VeadoRequest.validatePayload]
     *
     * Checks the Data Class and contents to make sure the combination is valid using [PayloadEvent] Values.
     *
     * Dynamic values like Token/State are only checked to make sure they are not Blank
     *
     *
     * @return true if tests passed
     * @throws IllegalStateException if any invalid/unrecognised value is found. Message contains details of issues.
     *
     * @see VeadoRequest.validatePayload
     *
     */
    fun validate() = VeadoRequest.validatePayload(this)

}

/**
 * Payload with Number Value/Min/Max
 *
 * Min/Max Values can be null if they shouldn't be set/changed, but [RequestPayload.RequestPayloadEventValueNumber]
 *
 * Value is always required
 *
 * More Info https://veado.tube/docs/tech/api/nodes/#number
 *
 * @param value a number (Float/Double or Integer)
 * @param min a number (Float/Double or Integer)
 * @param max a number (Float/Double or Integer)
 *
 */
@Serializable
data class RequestPayloadEventNumberValueMulti(
    val value: Double,
    val min: Double? = null,
    val max: Double? = null
)