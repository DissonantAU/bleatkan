@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package io.github.dissonantau.bleatkan.message

import io.github.dissonantau.bleatkan.message.RequestMessage.*
import io.github.dissonantau.bleatkan.message.RequestPayload.*

/**
 * Factory for Building and Validating Request Messages.
 * Returns an Object that can be serialized to JSON.
 * Does not include a channel prefix (ie "nodes:")
 *
 * Common Reusable/Immutable Request and Payload Objects can be gotten from the Factory.
 * These objects are Lazy Initialised, and are also returned by build functions when possible
 */
class VeadoRequest {

    companion object FACTORY {

        /* Common Requests that can be reused - sometimes using lazy initialisation to only create when first accessed */
        /** Request of *event: info* - Should be sent to Instance Channel  */
        @JvmStatic
        val getEventInfo: RequestMessage =
            RequestMessageInstanceInfo(event = MessageEvent.INFO.value)


        /** Request of *event: list* - For getting a List of Nodes from Nodes Channel */
        @JvmStatic
        val getEventList: RequestMessage =
            RequestMessageNodeList(event = MessageEvent.LIST.value)


        /** Request Payload of *event: list* - For getting a List of States in a Node */
        @JvmStatic
        val getPayloadEventList: RequestPayload =
            RequestPayloadEvent(event = PayloadEvent.LIST.value)


        /** Request Payload of *event: peek* */
        @JvmStatic
        val getPayloadEventPeek: RequestPayload =
            RequestPayloadEvent(event = PayloadEvent.PEEK.value)


        /** Request Payload of *event: get* */
        @JvmStatic
        val getPayloadEventGet: RequestPayload =
            RequestPayloadEvent(event = PayloadEvent.GET.value)


        /** Request Payload of *event: set* */
        @JvmStatic
        val getPayloadEventSet: RequestPayload =
            RequestPayloadEvent(event = PayloadEvent.SET.value)


        /** Request Payload of *event: toggle* */
        @JvmStatic
        val getPayloadEventToggle: RequestPayload =
            RequestPayloadEvent(event = PayloadEvent.TOGGLE.value)


        /** Request Payload of *event: clear* */
        @JvmStatic
        val getPayloadEventClear: RequestPayload =
            RequestPayloadEvent(event = PayloadEvent.CLEAR.value)


        /** Common Prebuilt Request to get Avatar State List from Veadotube Mini
         *
         * Equivalent of
         * createRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = [getPayloadEventList]
         * )
         *
         * @see createRequest
         * @see getPayloadEventList
         * */
        @JvmStatic
        val getListStateMini: RequestMessage by lazy {
            createRequest(payload = getPayloadEventList)
        }

        /** Common Prebuilt Request to get (peek) the current Avatar State from Veadotube Mini
         *
         * Equivalent of
         * createRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = [getPayloadEventPeek]
         * )
         *
         * @see createRequest
         * @see getPayloadEventPeek
         * */
        @JvmStatic
        val getPeekStateMini: RequestMessage by lazy {
            createRequest(payload = getPayloadEventPeek)
        }

        /**
         * Returns a Request [RequestMessage] with a Set State Payload [RequestPayload]
         *
         * Convenience Function to build a Set Avatar State Request
         *
         * Equivalent of
         * createRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = createPayload(
         *   event = [PayloadEvent.SET],
         *   value = [stateID]
         *  )
         * )
         *
         * @see createRequest
         * @see createPayload
         *
         */
        @JvmStatic
        fun createSetStateMini(
            stateID: String
        ) = createRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = createPayload(
                event = PayloadEvent.SET,
                value = stateID
            )
        )

        /**
         * Returns a Request [RequestMessage] with a Push State Payload[RequestPayload]
         *
         * Convenience Function to build a Push Avatar State Request
         *
         * Equivalent of
         * createRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = createPayload(
         *   event = [PayloadEvent.PUSH],
         *   value = [stateID]
         *  )
         * )
         *
         * @see createRequest
         * @see createPayload
         *
         */
        @JvmStatic
        fun createPushStateMini(
            stateID: String
        ) = createRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = createPayload(
                event = PayloadEvent.PUSH,
                value = stateID
            )
        )

        /**
         * Returns a Request [RequestMessage] with a Pop State Payload [RequestPayload]
         *
         * Convenience Function to build a Pop Avatar State Request
         *
         * Equivalent of
         * createRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = createPayload(
         *   event = [PayloadEvent.POP],
         *   value = [stateID]
         *  )
         * )
         *
         * @see createRequest
         * @see createPayload
         *
         */
        @JvmStatic
        fun createPopStateMini(
            stateID: String
        ) = createRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = createPayload(
                event = PayloadEvent.POP,
                value = stateID
            )
        )

        /**
         * Returns a Request [RequestMessage] with a Listen State Payload [RequestPayload]
         *
         * Convenience Function to build a Listen Avatar State Request
         *
         * Equivalent of
         * createRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = createPayload(
         *   event = [PayloadEvent.LISTEN],
         *   value = [stateID]
         *  )
         * )
         *
         * @param stateID Avatar State ID
         *
         * @see createRequest
         * @see createPayload
         *
         */
        @JvmStatic
        fun createListenStateMini(
            stateID: String
        ) = createRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = createPayload(
                event = PayloadEvent.LISTEN,
                value = stateID
            )
        )

        /**
         * Returns a Request [RequestMessage] with an Unlisten State Payload [RequestPayload]
         *
         * Convenience Function to build an Unlisten Avatar State Request
         *
         * Equivalent of
         * createRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = createPayload(
         *   event = [PayloadEvent.UNLISTEN],
         *   value = [stateID]
         *  )
         * )
         *
         * @param stateID Avatar State ID
         *
         * @see createRequest
         * @see createPayload
         *
         */
        @JvmStatic
        fun createUnlistenStateMini(
            stateID: String
        ) = createRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = createPayload(
                event = PayloadEvent.UNLISTEN,
                value = stateID
            )
        )


        /**
         * Returns a Request [RequestMessage] with a Thumbnail State Payload [RequestPayload]
         *
         * Convenience Function to build a Thumbnail Avatar State Request
         *
         * Equivalent of
         * createRequest(
         *  event = [MessageEvent.PAYLOAD],
         *  type = [MessagePayloadType.STATE_EVENTS],
         *  id = [MessagePayloadId.MINI],
         *  payload = createPayload(
         *   event = [PayloadEvent.THUMB],
         *   value = [stateID]
         *  )
         * )
         *
         * @see createRequest
         * @see createPayload
         *
         */
        @JvmStatic
        fun createThumbnailStateMini(
            stateID: String
        ) = createRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = createPayload(
                event = PayloadEvent.THUMB,
                value = stateID
            )
        )


        /* Builder Functions */
        /**
         * Returns a Request that inherits [RequestMessage]
         *
         * Common Uses:
         *
         * [event] = [MessageEvent.LIST]
         * * Doesn't require any other parameters, anything provided will be ignored.
         * * Returns [VeadoRequest.getEventList] - it can be accessed directly, and should be used instead if possible
         * * (See [RequestPayload.RequestPayloadEvent])
         *
         *
         * [event] = [MessageEvent.PAYLOAD]
         * * Uses [type], [id], and [payload] must be provided
         * * All must be non-blank and non-null.
         * * (See [RequestPayload.RequestPayloadEventToken])
         *
         *
         * @see [RequestPayload.RequestPayloadEvent]
         * @see [RequestPayload.RequestPayloadEventToken]
         *
         */
        @JvmOverloads
        @JvmStatic
        fun createRequest(
            event: MessageEvent = MessageEvent.PAYLOAD,
            type: MessagePayloadType? = MessagePayloadType.STATE_EVENTS,
            id: MessagePayloadId? = MessagePayloadId.MINI,
            payload: RequestPayload? = null
        ): RequestMessage {
            val newRequest: RequestMessage = when (event) {
                /* List as base event is just a payload an Event Payload */
                MessageEvent.INFO -> {
                    /* Return InstanceInfo */
                    getEventInfo
                }

                /* List as base event is just a payload an Event Payload */
                MessageEvent.LIST -> {
                    /*Return Common/Reusable Object*/
                    getEventList
                }

                MessageEvent.PAYLOAD -> {
                    when (type) {

                        MessagePayloadType.STATE_EVENTS, MessagePayloadType.BOOLEAN, MessagePayloadType.NUMBER -> {
                            require(id != null) { "Type cannot be Null for Request ${MessageEvent.PAYLOAD} with Type ${MessagePayloadType.STATE_EVENTS}" }
                            require(payload != null) { "Payload cannot be Null for Request ${MessageEvent.PAYLOAD} with Type ${MessagePayloadType.STATE_EVENTS}" }

                            RequestMessageNodeEvent(
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
         * Returns a Payload that inherits [RequestPayload]
         *
         *
         * [event] = [PayloadEvent.LIST] or [PayloadEvent.PEEK]
         * * Doesn't require [value], anything provided will be ignored.
         * * A [PayloadEvent.LIST] Payload can also be sent without being put into a Request to get a list of nodes.
         * * Returns [VeadoRequest.getPayloadEventList] or [VeadoRequest.getPayloadEventPeek] respectively
         * - they can be accessed directly, and should be used instead if possible
         * * (See [RequestPayload.RequestPayloadEvent])
         *
         *
         * [event] = [PayloadEvent.LISTEN] & [PayloadEvent.UNLISTEN]
         * * Uses [value] as the Token.
         * * Must be non-blank and non-null.
         * * (See [RequestPayload.RequestPayloadEventToken])
         *
         *
         * [event] = [PayloadEvent.SET], [PayloadEvent.PUSH], [PayloadEvent.POP], & [PayloadEvent.THUMB]
         * * Uses [value] as the State ID if not a Boolean or Number
         * * Should be a valid State ID from a [MessageEvent.PAYLOAD] / [MessagePayloadType.STATE_EVENTS] / [PayloadEvent.LIST] request.
         * * (See [RequestPayload.RequestPayloadEventStateString])
         * * If a Boolean, it can be "toggle"
         *
         *
         * @param event event value, should match [PayloadEvent] (except [PayloadEvent.UNKNOWN])
         *
         *
         * @see [RequestPayload.RequestPayloadEvent]
         * @see [RequestPayload.RequestPayloadEventToken]
         *
         */
        @JvmOverloads
        @JvmStatic
        fun createPayload(event: PayloadEvent, value: String? = null): RequestPayload {
            require(event != PayloadEvent.UNKNOWN) { "Payload Event can't be UNKNOWN" }

            val newRequest: RequestPayload =
                when (event) {

                    PayloadEvent.LIST -> {
                        /*Return Common/Reusable Object*/
                        getPayloadEventList
                    }

                    PayloadEvent.PEEK -> {
                        /*Return Common/Reusable Object*/
                        getPayloadEventPeek
                    }

                    PayloadEvent.TOGGLE -> {
                        /*Return Common/Reusable Object*/
                        getPayloadEventToggle
                    }

                    PayloadEvent.CLEAR -> {
                        /*Return Common/Reusable Object*/
                        getPayloadEventClear
                    }

                    PayloadEvent.LISTEN, PayloadEvent.UNLISTEN -> {
                        require(!value.isNullOrBlank()) { "Token Value for Payload $event Event can't be Null or Blank" }
                        RequestPayloadEventToken(
                            event = event.value,
                            token = value.trim()
                        )
                    }

                    PayloadEvent.SET, PayloadEvent.PUSH, PayloadEvent.POP, PayloadEvent.THUMB -> {
                        require(!value.isNullOrBlank()) { "State Value for Payload $event Event can't be Null or Blank" }
                        RequestPayloadEventStateString(
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
         * Returns a Payload that inherits [RequestPayload] for Boolean
         *
         * [event] = [PayloadEvent.SET]
         * * [value] should be `true` or `false`.
         *
         * @param event event value, should match [PayloadEvent] (except [PayloadEvent.UNKNOWN])
         *
         *
         */
        @JvmStatic
        fun createPayload(event: PayloadEvent, value: Boolean): RequestPayload {
            require(event != PayloadEvent.UNKNOWN) { "Payload Event can't be UNKNOWN" }

            val newRequest: RequestPayload =
                when (event) {

                    PayloadEvent.SET -> {
                        RequestPayloadEventValueBoolean(
                            event = event.value,
                            value = value
                        )
                    }

                    PayloadEvent.PUSH, PayloadEvent.POP, PayloadEvent.THUMB, PayloadEvent.LIST, PayloadEvent.PEEK, PayloadEvent.LISTEN, PayloadEvent.UNLISTEN -> {
                        throw IllegalArgumentException("Event Doesn't support Boolean value: $event")
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown Event: $event")
                    }

                }

            return newRequest
        }

        /**
         * Returns a Payload that inherits [RequestPayload] for Boolean
         *
         * * [value] should be `true` or `false`.
         *
         */
        @JvmStatic
        fun createPayloadSetBoolean(value: Boolean): RequestPayload {
            return RequestPayloadEventValueBoolean(
                event = PayloadEvent.SET.value,
                value = value
            )
        }

        /**
         * Returns a Payload that inherits [RequestPayload] for Boolean
         *
         * * [value] us usually an ID, or 'toggle'/'clear'
         *
         */
        @JvmStatic
        fun createPayloadSetBoolean(value: String): RequestPayload {
            require(value.isNotEmpty()) { "Value for Payload Set Event can't be Empty" }
            return RequestPayloadEventValueString(
                event = PayloadEvent.SET.value,
                value = value
            )
        }

        /**
         * Returns a Payload that inherits [RequestPayload] for Number Nodes
         *
         * * [value] new node value to set
         * * [min] Optional, new minimum value to set
         * * [max] Optional, new minimum value to set
         */
        @JvmOverloads
        @JvmStatic
        fun createPayloadSetNumber(
            value: Double,
            min: Double? = null,
            max: Double? = null
        ): RequestPayload {
            return if (min != null || max != null)
                RequestPayloadEventValueNumberMinMax(
                    event = PayloadEvent.SET.value,
                    value = RequestPayloadEventNumberValueMulti(
                        value = value,
                        min = min,
                        max = max
                    )
                )
            else
                RequestPayloadEventValueNumber(
                    event = PayloadEvent.SET.value,
                    value = value
                )
        }

        /**
         * Returns a Payload that inherits [RequestPayload] for Number Nodes
         *
         * * [value] amount to add/remove from the node
         * * [min] Optional, new minimum value to set
         * * [max] Optional, new minimum value to set
         */
        @JvmOverloads
        @JvmStatic
        fun createPayloadAddNumber(
            value: Double,
            min: Double? = null,
            max: Double? = null
        ): RequestPayload {
            return if (min != null || max != null)
                RequestPayloadEventValueNumberMinMax(
                    event = PayloadEvent.ADD.value,
                    value = RequestPayloadEventNumberValueMulti(
                        value = value,
                        min = min,
                        max = max
                    )
                )
            else
                RequestPayloadEventValueNumber(
                    event = PayloadEvent.ADD.value,
                    value = value
                )
        }

        /**
         * Returns a Request that inherits [RequestMessage] with a Payload
         *
         * Convenience Function to combine [createRequest] and [createPayload]
         *
         *
         * Common Uses:
         *
         * [event] = [MessageEvent.LIST]
         * * Doesn't require any other parameters, anything provided will be ignored.
         * * Returns [VeadoRequest.getEventList] - it can be accessed directly, and should be used instead if possible
         * * (See [RequestPayload.RequestPayloadEvent])
         *
         *
         * [event] = [MessageEvent.PAYLOAD]
         * * Uses [type], [id], can be provided while [payloadEvent] must be provided, along with [payloadValue] if it's needed
         * * All must be non-blank and non-null.
         * * (See [RequestPayload.RequestPayloadEventToken])
         *
         *
         * @see [RequestPayload.RequestPayloadEvent]
         * @see [RequestPayload.RequestPayloadEventToken]
         * @see [createRequest]
         *
         */
        @JvmOverloads
        @JvmStatic
        fun createRequestWithPayload(
            event: MessageEvent = MessageEvent.PAYLOAD,
            type: MessagePayloadType? = MessagePayloadType.STATE_EVENTS,
            id: MessagePayloadId? = MessagePayloadId.MINI,
            payloadEvent: PayloadEvent, payloadValue: String? = null
        ) = createRequest(
            event = event,
            type = type,
            id = id,
            payload = createPayload(
                event = payloadEvent,
                value = payloadValue
            )
        )

        /**
         * Returns a Request that inherits [RequestMessage] with a Payload
         *
         * Convenience Function to combine [createRequest] and [createPayload]
         *
         *
         * Common Uses:
         *
         *
         * [event] = [MessageEvent.PAYLOAD]
         * * Uses [type], [id], can be provided while [payloadEvent] must be provided, along with [payloadValue]
         * * Boolean
         *
         *
         * @see [RequestPayload.RequestPayloadEvent]
         * @see [RequestPayload.RequestPayloadEventToken]
         * @see [createRequest]
         *
         */
        @JvmOverloads
        @JvmStatic
        fun createRequestWithPayload(
            event: MessageEvent = MessageEvent.PAYLOAD,
            type: MessagePayloadType? = MessagePayloadType.STATE_EVENTS,
            id: MessagePayloadId? = MessagePayloadId.MINI,
            payloadEvent: PayloadEvent, payloadValue: Boolean
        ) = createRequest(
            event = event,
            type = type,
            id = id,
            payload = createPayload(
                event = payloadEvent,
                value = payloadValue
            )
        )

        /**
         * Validates Data in a Request.
         *
         * Checks the Data Class and contents to make sure the combination is valid using [PayloadEvent] Values.
         *
         * If the Request has a Payload, it will also be validated
         *
         * @param requestData data object inheriting [RequestMessage]
         *
         * @return true if tests passed
         * @throws IllegalStateException if any invalid/unrecognised value is found. Message contains details of issues
         *
         * @see validatePayload Use to validate Payload Contents if applicable
         */
        @Throws(IllegalStateException::class)
        @JvmStatic
        fun validateRequestMessage(requestData: RequestMessage): Boolean {
            val errorSb by lazy { StringBuilder().append { "Request: " } }
            var valid = true

            when {
                /* Check vs Type Block Start */
                (requestData is RequestMessageInstanceInfo) -> {
                    /* Check VtRequestNodeList Block Start */
                    if (MessageEvent.fromValue(requestData.event) != MessageEvent.INFO) {
                        valid = false
                        errorSb.append { "event (${requestData.event});" }
                    }
                    /* Check VtRequestNodeList Block End */
                }

                (requestData is RequestMessageNodeList) -> {
                    /* Check VtRequestNodeList Block Start */
                    if (MessageEvent.fromValue(requestData.event) != MessageEvent.LIST) {
                        valid = false
                        errorSb.append { "event (${requestData.event});" }
                    }
                    /* Check VtRequestNodeList Block End */
                }

                (requestData is RequestMessageNodeEventToken) -> {
                    /* Check RequestMessageNodeEventToken Block Start */
                    when (MessageEvent.fromValue(requestData.event)) {
                        /* Token Check */
                        MessageEvent.LISTEN, MessageEvent.UNLISTEN -> {
                            /* Valid, make sure not blank */
                            if (requestData.token.isBlank()) {
                                valid = false
                                errorSb.append { "token (is blank);" }
                            }
                        }

                        else -> {
                            valid = false
                            errorSb.append { "event (${requestData.event});" }
                        }
                    }
                    /* Check RequestMessageNodeEventToken Block End */
                }

                (requestData is RequestMessageNodeEvent) -> {
                    /* Check VtRequestNodeMessage Block Start */
                    when (MessageEvent.fromValue(requestData.event)) {
                        MessageEvent.PAYLOAD -> {
                            /*Valid, but other values need checking */

                            /* Check Request Values Start */
                            if (MessagePayloadType.fromValue(requestData.type) != MessagePayloadType.STATE_EVENTS) {
                                valid = false
                                errorSb.append { "type (${requestData.type});" }
                            }

                            if (MessagePayloadId.fromValue(requestData.id) != MessagePayloadId.MINI) {
                                valid = false
                                errorSb.append { "id (${requestData.id});" }
                            }

                            /*Payload Check*/
                            try {
                                validatePayload(requestData.payload)
                            } catch (ex: Exception) {
                                valid = false
                                errorSb.append { ex.message }
                            }

                            /* Check Request Values End */
                        }

                        else -> {
                            // Other Values aren't valid here
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

        /**
         * Validates Data in a Payload.
         *
         * Checks the Data Class and contents to make sure the combination is valid using [PayloadEvent] Values.
         *
         * Dynamic values like Token/State are only checked to make sure they are not Blank
         *
         * @param payloadData data object inheriting [RequestPayload]
         *
         * @return true if tests passed
         * @throws IllegalStateException if any invalid/unrecognised value is found. Message contains details of issues
         */
        @Throws(IllegalStateException::class)
        @JvmStatic
        fun validatePayload(payloadData: RequestPayload): Boolean {
            val errorSb by lazy { StringBuilder().append { "Payload: " } }
            var valid = true

            when {
                (payloadData is RequestPayloadEvent) -> {
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

                (payloadData is RequestPayloadEventToken) -> {
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

                (payloadData is RequestPayloadEventStateString) -> {
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