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
         * e.g. mini
         */
        val id: String,
        /**
         * Payload to send to node [type] / [id]
         */
        val payload: RequestPayload
    ) : RequestMessage()


    /**
     * Message for Listing Nodes
     */
    @Serializable
    data class RequestMessageInstanceInfo(
        override val event: String
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
     * e.g. [PayloadEvent.LIST] (*list*) will return the possible state values, [PayloadEvent.PEEK] (*peek*) will return the current state value
     */
    @Serializable
    class RequestPayloadEvent(
        override val event: String
    ) : RequestPayload()

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
    ) : RequestPayload()

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
    data class RequestPayloadEventState(
        override val event: String,
        /**
         * Unique ID for the State
         *
         * i.e. the state id from a [MessageEvent.PAYLOAD] / [MessagePayloadType.STATE_EVENTS] / [PayloadEvent.LIST] request
         */
        val state: String
    ) : RequestPayload()

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
