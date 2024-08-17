package xyz.dissonant.veadotube.bleatkan.serializable

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject


// Enum for Known Message Events
enum class MessageEvent(val value: String) {
    UNKNOWN("UNKNOWN"),
    ERROR("ERROR"),
    /* Message is targeting a specific node */
    /** For a Request that targets a node (eg stateEvents)*/
    @SerialName("payload")
    PAYLOAD("payload"),

    /* Requesting Values*/
    /** Requests a List of Possible Values*/
    @SerialName("list")
    LIST("list"),
    ;

    companion object {
        /**
         * Gets Enum Constant with given Value
         *
         * Returns UNKNOWN ENUM if not found
         *
         *@return Enum Constant
         */
        @Suppress("unused")
        @JvmStatic
        fun fromValue(value: String): MessageEvent {
            return entries.find { it.value == value } ?: UNKNOWN
        }
    }
}


// Enum for Known Message Types
enum class MessagePayloadType(val value: String) {
    UNKNOWN("UNKNOWN"),

    @SerialName("stateEvents")
    STATE_EVENTS("stateEvents"),
    ;

    companion object {
        /**
         * Gets Enum Constant with given Value
         *
         * Returns UNKNOWN ENUM if not found
         *
         *@return Enum Constant
         */
        @Suppress("unused")
        @JvmStatic
        fun fromValue(value: String): MessagePayloadType {
            return entries.find { it.value == value } ?: UNKNOWN
        }
    }
}


// Enum for Known Message IDs
enum class MessagePayloadId(val value: String) {
    UNKNOWN("UNKNOWN"),

    @SerialName("mini")
    MINI("mini"),
    ;

    companion object {
        /**
         * Gets Enum Constant with given Value
         *
         * Returns UNKNOWN ENUM if not found
         *
         *@return Enum Constant
         */
        @Suppress("unused")
        @JvmStatic
        fun fromValue(value: String): MessagePayloadId {
            return entries.find { it.value == value } ?: UNKNOWN
        }
    }
}

// Enum for Known Payload Events
enum class PayloadEvent(val value: String) {
    UNKNOWN("UNKNOWN"),

    /* Requesting Values*/
    /** Requests a List of Possible Values*/
    @SerialName("list")
    LIST("list"),

    /** Requests current Single Value*/
    @SerialName("peek")
    PEEK("peek"),

    /** Requests the image related to a Possible Value*/
    @SerialName("thumb")
    THUMB("thumb"),

    /* Setting Values */
    /** Sets a Single Value*/
    @SerialName("set")
    SET("set"),

    /** Pushes a Single Value*/
    @SerialName("push")
    PUSH("push"),

    /** Pops the last Pushed Single Value*/
    @SerialName("pop")
    POP("pop"),

    /* Listening to Value Changes */
    /** Listens to changes to a Channel Value*/
    @SerialName("listen")
    LISTEN("listen"),

    /** Stops listening to changes to a Channel Value*/
    @SerialName("unlisten")
    UNLISTEN("unlisten"),
    ;

    companion object {
        /**
         * Gets Enum Constant with given Value
         *
         * Returns UNKNOWN ENUM if not found
         *
         *@return Enum Constant
         */
        @Suppress("unused")
        @JvmStatic
        fun fromValue(value: String): PayloadEvent {
            return requireNotNull(entries.find { it.value == value }) { UNKNOWN }
        }
    }
}


object VtRequestMessageSerializer : JsonContentPolymorphicSerializer<VtRequest>(VtRequest::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<VtRequest> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject["event"]?.equals("list") ?: false -> VtRequest.VtRequestNodeList.serializer()
            jsonObject.containsKey("payload") -> VtRequest.VtRequestNodeMessage.serializer()
            else -> throw IllegalArgumentException("Unsupported request type")
        }
    }
}


object VtRequestPayloadSerializer : JsonContentPolymorphicSerializer<VtRequestPayload>(VtRequestPayload::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<VtRequestPayload> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.containsKey("token") -> VtRequestPayload.VTRequestPayloadEventToken.serializer()
            jsonObject.containsKey("state") -> VtRequestPayload.VTRequestPayloadEventState.serializer()
            jsonObject.containsKey("event") -> VtRequestPayload.VtRequestPayloadEvent.serializer()
            else -> throw IllegalArgumentException("Unsupported Payload type")
        }
    }
}


/**
 * Factory for Building and Validating Request Messages.
 * Returns an Object that can be serialized to JSON.
 * Does not include a channel prefix (ie "nodes:")
 *
 * Common Reusable/Immutable Request and Payload Objects can be gotten from the Factory.
 * These objects are Lazy Initialised, and are also returned by build functions when possible
 */
class VtRequestFactory {

    @Suppress("unused")
    companion object FACTORY {
        /* Common Requests that can be reused - using lays initialisation to only create when first accessed*/
        /** Request of *event: list* - Lazy Initialized equivalent of *[requestPayloadEventList] as VtRequest* */
        @JvmStatic
        val requestEventList: VtRequest by lazy {
            VtRequest.VtRequestNodeList(event = MessageEvent.LIST.value)
        }

        /* Common Request Payloads that can be reused - using lays initialisation to only create when first accessed*/
        /** Request Payload of *event: list* */
        @JvmStatic
        val requestPayloadEventList: VtRequestPayload by lazy {
            VtRequestPayload.VtRequestPayloadEvent(event = PayloadEvent.LIST.value)
        }

        /** Request Payload of *event: peek* */
        @JvmStatic
        val requestPayloadEventPeek: VtRequestPayload by lazy {
            VtRequestPayload.VtRequestPayloadEvent(event = PayloadEvent.PEEK.value)
        }


        /**
         * Returns a Request [VtRequest] with a Set State Payload [VtRequestPayload]
         *
         * Convenience Function to build a Set Avatar State Request
         *
         * Equivalent of
         * buildRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = buildPayload(
         *   event = [PayloadEvent.SET],
         *   value = [stateID]
         *  )
         * )
         *
         * @see buildRequest
         * @see buildPayload
         *
         */
        @JvmStatic
        fun buildRequestSetMiniState(
            stateID: String
        ) = buildRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = buildPayload(
                event = PayloadEvent.SET,
                value = stateID
            )
        )


        /**
         * Returns a Request [VtRequest] with a Push State Payload[VtRequestPayload]
         *
         * Convenience Function to build a Push Avatar State Request
         *
         * Equivalent of
         * buildRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = buildPayload(
         *   event = [PayloadEvent.PUSH],
         *   value = [stateID]
         *  )
         * )
         *
         * @see buildRequest
         * @see buildPayload
         *
         */
        @JvmStatic
        fun buildRequestPushMiniState(
            stateID: String
        ) = buildRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = buildPayload(
                event = PayloadEvent.PUSH,
                value = stateID
            )
        )


        /**
         * Returns a Request [VtRequest] with a Pop State Payload [VtRequestPayload]
         *
         * Convenience Function to build a Pop Avatar State Request
         *
         * Equivalent of
         * buildRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = buildPayload(
         *   event = [PayloadEvent.POP],
         *   value = [stateID]
         *  )
         * )
         *
         * @see buildRequest
         * @see buildPayload
         *
         */
        @JvmStatic
        fun buildRequestPopMiniState(
            stateID: String
        ) = buildRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = buildPayload(
                event = PayloadEvent.POP,
                value = stateID
            )
        )


        /**
         * Returns a Request [VtRequest] with a Listen State Payload [VtRequestPayload]
         *
         * Convenience Function to build a Listen Avatar State Request
         *
         * Equivalent of
         * buildRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = buildPayload(
         *   event = [PayloadEvent.LISTEN],
         *   value = [stateID]
         *  )
         * )
         *
         * @param stateID Avatar State ID
         *
         * @see buildRequest
         * @see buildPayload
         *
         */
        @JvmStatic
        fun buildRequestListenMiniState(
            stateID: String
        ) = buildRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = buildPayload(
                event = PayloadEvent.LISTEN,
                value = stateID
            )
        )


        /**
         * Returns a Request [VtRequest] with an Unlisten State Payload [VtRequestPayload]
         *
         * Convenience Function to build an Unlisten Avatar State Request
         *
         * Equivalent of
         * buildRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = buildPayload(
         *   event = [PayloadEvent.UNLISTEN],
         *   value = [stateID]
         *  )
         * )
         *
         * @param stateID Avatar State ID
         *
         * @see buildRequest
         * @see buildPayload
         *
         */
        @JvmStatic
        fun buildRequestUnlistenMiniState(
            stateID: String
        ) = buildRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = buildPayload(
                event = PayloadEvent.UNLISTEN,
                value = stateID
            )
        )


        /**
         * Returns a Request [VtRequest] with a Thumbnail State Payload [VtRequestPayload]
         *
         * Convenience Function to build a Thumbnail Avatar State Request
         *
         * Equivalent of
         * buildRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = buildPayload(
         *   event = [PayloadEvent.THUMB],
         *   value = [stateID]
         *  )
         * )
         *
         * @see buildRequest
         * @see buildPayload
         *
         */
        @JvmStatic
        fun buildRequestThumbMiniState(
            stateID: String
        ) = buildRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = buildPayload(
                event = PayloadEvent.THUMB,
                value = stateID
            )
        )

        /** Common Prebuilt Request to get Avatar State List from Veadotube Mini
         *
         * Equivalent of
         * buildRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = [requestPayloadEventList]
         * )
         *
         * @see buildRequest
         * @see requestPayloadEventList
         * */
        @JvmStatic
        val requestListMiniState: VtRequest by lazy {
            buildRequest(payload = requestPayloadEventList)
        }

        /** Common Prebuilt Request to get (peek) the current Avatar State from Veadotube Mini
         *
         * Equivalent of
         * buildRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = [requestPayloadEventPeek]
         * )
         *
         * @see buildRequest
         * @see requestPayloadEventPeek
         * */
        @JvmStatic
        val requestPeekStateMini: VtRequest by lazy {
            buildRequest(payload = requestPayloadEventPeek)
        }


        /* Builders */
        /**
         * Returns a Request that inherits [VtRequest]
         *
         * Common Uses:
         *
         * [event] = [MessageEvent.LIST]
         * * Doesn't require any other parameters, anything provided will be ignored.
         * * Returns [VtRequestFactory.requestEventList] - it can be accessed directly, and should be used instead if possible
         * * (See [VtRequestPayload.VtRequestPayloadEvent])
         *
         *
         * [event] = [MessageEvent.PAYLOAD]
         * * Uses [type], [id], and [payload] must be provided
         * * All must be non-blank and non-null.
         * * (See [VtRequestPayload.VTRequestPayloadEventToken])
         *
         *
         * @see [VtRequestPayload.VtRequestPayloadEvent]
         * @see [VtRequestPayload.VTRequestPayloadEventToken]
         *
         */
        @JvmOverloads
        @JvmStatic
        fun buildRequest(
            event: MessageEvent = MessageEvent.PAYLOAD,
            type: MessagePayloadType? = MessagePayloadType.STATE_EVENTS,
            id: MessagePayloadId? = MessagePayloadId.MINI,
            payload: VtRequestPayload? = null
        ): VtRequest {
            val newRequest: VtRequest = when (event) {
                /* List as base event is just a payload an Event Payload */
                MessageEvent.LIST -> {
                    /*Return Common/Reusable Object*/
                    requestEventList
                }

                MessageEvent.PAYLOAD -> {
                    when (type) {

                        MessagePayloadType.STATE_EVENTS -> {
                            require(id != null) { "Type cannot be Null for Request ${MessageEvent.PAYLOAD} with Type ${MessagePayloadType.STATE_EVENTS}" }
                            require(payload != null) { "Payload cannot be Null for Request ${MessageEvent.PAYLOAD} with Type ${MessagePayloadType.STATE_EVENTS}" }

                            VtRequest.VtRequestNodeMessage(
                                event = event.value,
                                type = type.value,
                                id = id.value,
                                payload = payload
                            )
                        }

                        /* Type not specified - invalid */
                        null -> {
                            throw IllegalArgumentException("Type cannot be Null for Request ${MessageEvent.PAYLOAD}")
                        }

                        MessagePayloadType.UNKNOWN -> {
                            throw IllegalArgumentException("Type cannot be UNKNOWN")
                        }
                    }
                }

                else -> {
                    throw IllegalArgumentException("Event can't be $event, must be ${MessageEvent.LIST} or ${MessageEvent.PAYLOAD}")
                }

            }

            return newRequest
        }


        /**
         * Returns a Payload that inherits [VtRequestPayload]
         *
         *
         * [event] = [PayloadEvent.LIST] or [PayloadEvent.PEEK]
         * * Doesn't require [value], anything provided will be ignored.
         * * A [PayloadEvent.LIST] Payload can also be sent without being put into a Request to get a list of nodes.
         * * Returns [VtRequestFactory.requestPayloadEventList] or [VtRequestFactory.requestPayloadEventPeek] respectively
         * - they can be accessed directly, and should be used instead if possible
         * * (See [VtRequestPayload.VtRequestPayloadEvent])
         *
         *
         * [event] = [PayloadEvent.LISTEN] & [PayloadEvent.UNLISTEN]
         * * Uses [value] as the Token.
         * * Must be non-blank and non-null.
         * * (See [VtRequestPayload.VTRequestPayloadEventToken])
         *
         *
         * [event] = [PayloadEvent.SET], [PayloadEvent.PUSH], [PayloadEvent.POP], & [PayloadEvent.THUMB]
         * * Uses [value] as the State ID.
         * * Should be a valid State ID from a [MessageEvent.PAYLOAD] / [MessagePayloadType.STATE_EVENTS] / [PayloadEvent.LIST] request.
         * * (See [VtRequestPayload.VTRequestPayloadEventState])
         *
         *
         * @param event event value, should match [PayloadEvent] (except [PayloadEvent.UNKNOWN])
         *
         *
         * @see [VtRequestPayload.VtRequestPayloadEvent]
         * @see [VtRequestPayload.VTRequestPayloadEventToken]
         *
         */
        @JvmOverloads
        @JvmStatic
        fun buildPayload(event: PayloadEvent, value: String? = null): VtRequestPayload {
            require(event != PayloadEvent.UNKNOWN) { "Payload Event can't be UNKNOWN" }


            val newRequest: VtRequestPayload =

                when (event) {

                    PayloadEvent.LIST -> {
                        /*Return Common/Reusable Object*/
                        requestPayloadEventList
                    }

                    PayloadEvent.PEEK -> {
                        /*Return Common/Reusable Object*/
                        requestPayloadEventPeek
                    }

                    PayloadEvent.LISTEN, PayloadEvent.UNLISTEN -> {
                        require(!value.isNullOrBlank()) { "Token Value for Payload $event Event can't be Null or Blank" }
                        VtRequestPayload.VTRequestPayloadEventToken(
                            event = event.value,
                            token = value.trim()
                        )
                    }

                    PayloadEvent.SET, PayloadEvent.PUSH, PayloadEvent.POP, PayloadEvent.THUMB -> {
                        require(!value.isNullOrBlank()) { "State Value for Payload $event Event can't be Null or Blank" }
                        VtRequestPayload.VTRequestPayloadEventState(
                            event = event.value,
                            state = value.trim()
                        )
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown Event: $event")
                    }

                }

            return newRequest
        }


        /**
         * Returns a Request that inherits [VtRequest] with a Payload
         *
         * Convenience Function to combine [buildRequest] and [buildPayload]
         *
         *
         * Common Uses:
         *
         * [event] = [MessageEvent.LIST]
         * * Doesn't require any other parameters, anything provided will be ignored.
         * * Returns [VtRequestFactory.requestEventList] - it can be accessed directly, and should be used instead if possible
         * * (See [VtRequestPayload.VtRequestPayloadEvent])
         *
         *
         * [event] = [MessageEvent.PAYLOAD]
         * * Uses [type], [id], can be provided while [payloadEvent] must be provided, along with [payloadValue] if it's needed
         * * All must be non-blank and non-null.
         * * (See [VtRequestPayload.VTRequestPayloadEventToken])
         *
         *
         * @see [VtRequestPayload.VtRequestPayloadEvent]
         * @see [VtRequestPayload.VTRequestPayloadEventToken]
         * @see [buildRequest]
         *
         */
        @JvmOverloads
        @JvmStatic
        fun buildRequestWithPayload(
            event: MessageEvent = MessageEvent.PAYLOAD,
            type: MessagePayloadType? = MessagePayloadType.STATE_EVENTS,
            id: MessagePayloadId? = MessagePayloadId.MINI,
            payloadEvent: PayloadEvent, payloadValue: String? = null
        ) = buildRequest(
            event = event,
            type = type,
            id = id,
            payload = buildPayload(
                event = payloadEvent,
                value = payloadValue
            )
        )


    }
}

/**
 * Class used to represent Veadotube Request Messages.
 *
 * The [validate] function can be used to help validate the message
 *
 * Must always have an [event] value at minimum
 */
@Serializable(VtRequestMessageSerializer::class)
sealed class VtRequest {
    abstract val event: String

    /**
     * Message for Listing Nodes
     */
    @Serializable
    data class VtRequestNodeList(
        override val event: String
    ) : VtRequest()

    @Serializable
    data class VtRequestNodeMessage(
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
         * e.g. mini
         */
        val id: String,
        /**
         * Payload to send to node [type] / [id]
         */
        val payload: VtRequestPayload
    ) : VtRequest()

    fun toJsonString(): String {
        return Json.encodeToString(this)
    }

    companion object {
        /**
         * Validates Data in a Request.
         *
         * Checks the Data Class and contents to make sure the combination is valid using [PayloadEvent] Values.
         *
         * If the Request is a Payload, it will also be validated
         *
         * @param requestData data object inheriting [VtRequest]
         *
         * @return true if tests passed
         * @throws IllegalStateException if any invalid value is found. Message contains details of issues
         *
         * @see VtRequestPayload.validate Use to validate Payload Contents if applicable
         */
        @Throws(IllegalStateException::class)
        @JvmStatic
        fun validate(requestData: VtRequest): Boolean {
            val errorSb by lazy { StringBuilder().append { "Request: " } }
            var valid = true

            when {
                /* Check vs Type Block Start */
                (requestData is VtRequestNodeList) -> {
                    /* Check VtRequestNodeList Block Start */
                    when (MessageEvent.fromValue(requestData.event)) {
                        MessageEvent.LIST -> {/*Valid, no more to do */
                        }

                        else -> {
                            valid = false
                            errorSb.append { "event (${requestData.event});" }
                        }
                    }
                    /* Check VtRequestNodeList Block End */
                }

                (requestData is VtRequestNodeMessage) -> {
                    /* Check VtRequestNodeMessage Block Start */
                    when (MessageEvent.fromValue(requestData.event)) {
                        MessageEvent.PAYLOAD -> {
                            /*Valid, but other values need checking */

                            /* Check Request Values Start */
                            when (MessagePayloadType.fromValue(requestData.type)) {
                                MessagePayloadType.STATE_EVENTS -> {/*Valid, no more to do */
                                }

                                else -> {
                                    valid = false
                                    errorSb.append { "type (${requestData.type});" }
                                }
                            }

                            when (MessagePayloadId.fromValue(requestData.id)) {
                                MessagePayloadId.MINI -> {/*Valid, no more to do */
                                }

                                else -> {
                                    valid = false
                                    errorSb.append { "id (${requestData.id});" }
                                }
                            }

                            /*Payload Check*/
                            try {
                                VtRequestPayload.validate(requestData.payload)
                            } catch (ex: Exception) {
                                valid = false
                                errorSb.append { ex.message }
                            }

                            /* Check Request Values End */
                        }

                        else -> {
                            valid = false
                            errorSb.append { "event (${requestData.event});" }
                        }
                    }
                    /* Check VtRequestNodeMessage Block End */
                }
                /* Check vs Type Block End */
                else -> {
                    valid = false
                    errorSb.append { "class (${requestData::javaClass});" }
                }
            }


            //If not Valid Throw IllegalStateException with Error String
            if (!valid) {
                throw IllegalStateException(errorSb.toString())
            }

            //return true
            return true
        }
    }


}


/**
 * Class used to restrict what can be a payload for [VtRequest.VtRequestNodeMessage.payload]
 *
 * Must always have an [event] value at minimum
 */
@Serializable(VtRequestPayloadSerializer::class)
sealed class VtRequestPayload {
    abstract val event: String

    /**
     * Used for basic Request Payloads, like Peek
     *
     * @param event the action to be carried out
     *
     * e.g. [PayloadEvent.LIST] (*list*) will return the possible state values, [PayloadEvent.PEEK] (*peek*) will return the current state value
     */
    @Serializable
    class VtRequestPayloadEvent(
        override val event: String
    ) : VtRequestPayload()

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
    data class VTRequestPayloadEventToken(
        override val event: String,
        /**
         * Unique ID for the listener - can be anything
         * Token sent for UnListen must be the same as original Listen Request
         */
        val token: String
    ) : VtRequestPayload()

    /**
     * Used for Request Payloads that need a state, e.g. Set/Push/Pop/Thumb
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
    data class VTRequestPayloadEventState(
        override val event: String,
        /**
         * Unique ID for the State
         *
         * i.e. the state id from a [MessageEvent.PAYLOAD] / [MessagePayloadType.STATE_EVENTS] / [PayloadEvent.LIST] request
         */
        val state: String
    ) : VtRequestPayload()

    fun toJsonString(): String {
        return Json.encodeToString(this)
    }

    companion object {
        /**
         * Validates Data in a Payload.
         *
         * Checks the Data Class and contents to make sure the combination is valid using [PayloadEvent] Values.
         *
         * Dynamic values like Token/State are only checked to make sure they are not Blank
         *
         * @param payloadData data object inheriting [VtRequestPayload]
         *
         * @return true if tests passed
         * @throws IllegalStateException if any invalid value is found. Message contains details of issues
         */
        @Throws(IllegalStateException::class)
        @JvmStatic
        fun validate(payloadData: VtRequestPayload): Boolean {
            val errorSb by lazy { StringBuilder().append { "Payload: " } }
            var valid = true

            when {
                (payloadData is VtRequestPayloadEvent) -> {
                    /* Check VtRequestPayloadEvent Block Start */
                    when (PayloadEvent.fromValue(payloadData.event)) {
                        PayloadEvent.LIST, PayloadEvent.PEEK -> {/* Valid, no more to do */
                        }

                        else -> {
                            valid = false
                            errorSb.append { "event (${payloadData.event});" }
                        }
                    }
                    /* Check VtRequestPayloadEvent Block End */
                }

                (payloadData is VTRequestPayloadEventToken) -> {
                    /* Check VTRequestPayloadEventToken Block Start */
                    when (PayloadEvent.fromValue(payloadData.event)) {
                        PayloadEvent.LISTEN, PayloadEvent.UNLISTEN -> {
                            /* Valid, make sure not blank */
                            if (payloadData.token.isBlank()) {
                                valid = false
                                errorSb.append { "token (is blank);" }
                            }
                        }

                        else -> {
                            valid = false
                            errorSb.append { "event (${payloadData.event});" }
                        }
                    }
                    /* Check VTRequestPayloadEventToken Block End */
                }

                (payloadData is VTRequestPayloadEventState) -> {
                    /* Check VTRequestPayloadEventState Block Start */
                    when (PayloadEvent.fromValue(payloadData.event)) {
                        PayloadEvent.SET, PayloadEvent.PUSH, PayloadEvent.POP, PayloadEvent.THUMB -> {
                            /* Valid, make sure not blank */
                            if (payloadData.state.isBlank()) {
                                valid = false
                                errorSb.append { "state (is blank);" }
                            }
                        }

                        else -> {
                            valid = false
                            errorSb.append { "event (${payloadData.event});" }
                        }
                    }
                    /* Check VTRequestPayloadEventState Block End */
                }

                /* Check vs Type Block End */
                else -> {
                    valid = false
                    errorSb.append { "class (${payloadData::javaClass});" }
                }

            }

            //If not Valid Throw IllegalStateException with Error String
            if (!valid) {
                throw IllegalStateException(errorSb.toString())
            }

            //return true
            return true
        }
    }
}