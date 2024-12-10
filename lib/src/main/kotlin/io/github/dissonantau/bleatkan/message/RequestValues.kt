package io.github.dissonantau.bleatkan.message

import kotlinx.serialization.SerialName

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

    /** Requests for Instance Info (Target *Instance* Channel) */
    @SerialName("info")
    INFO("info"),
    ;

    companion object {
        /**
         * Gets Enum Constant with given Value
         *
         * Returns UNKNOWN ENUM if not found
         *
         *@return Enum Constant
         */
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
        @JvmStatic
        fun fromValue(value: String): PayloadEvent {
            return requireNotNull(entries.find { it.value == value }) { UNKNOWN }
        }
    }
}