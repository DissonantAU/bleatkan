package io.github.dissonantau.bleatkan.message

import kotlinx.serialization.SerialName

// Enum for Known Message Events
enum class MessageEvent(val value: String) {
    UNKNOWN("UNKNOWN"),
    ERROR("ERROR"),
    /* Message is targeting a specific node */
    /** For a Request that targets a node (e.g. stateEvents) */
    @SerialName("payload")
    PAYLOAD("payload"),

    /* Requesting Values */
    /** Requests a List of Possible Values */
    @SerialName("list")
    LIST("list"),

    /** Requests for Instance Info (Target *Instance* Channel) */
    @SerialName("info")
    INFO("info"),

    /* Listening to Value Changes */
    /** Listens to changes to the Nodes List */
    @SerialName("listen")
    LISTEN("listen"),

    /** Stops listening to changes to the Nodes List */
    @SerialName("unlisten")
    UNLISTEN("unlisten"),
    ;

    companion object {
        /**
         * Gets Enum Constant with given Value
         *
         * Returns UNKNOWN ENUM if not found
         *
         * @return Enum Constant
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

    @SerialName("boolean")
    BOOLEAN("boolean"),

    @SerialName("number")
    NUMBER("number"),
    ;

    companion object {
        /**
         * Gets Enum Constant with given Value
         *
         * Returns UNKNOWN ENUM if not found
         *
         * @return Enum Constant
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
         * @return Enum Constant
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
    /** Requests a List of Possible Values */
    @SerialName("list")
    LIST("list"),

    /** Requests current Single Value of a stack (e.g State) */
    @SerialName("peek")
    PEEK("peek"),

    /** Requests the image related to a Possible Value */
    @SerialName("thumb")
    THUMB("thumb"),

    /* Setting Values */
    /** Sets a Single Value (Both State or Boolean/Number) */
    @SerialName("set")
    SET("set"),

    /** Pushes a Single Value */
    @SerialName("push")
    PUSH("push"),

    /** Pops the last Pushed Single Value */
    @SerialName("pop")
    POP("pop"),

    /* Listening to Value Changes */
    /** Listens to changes to a Channel Value */
    @SerialName("listen")
    LISTEN("listen"),

    /** Stops listening to changes to a Channel Value */
    @SerialName("unlisten")
    UNLISTEN("unlisten"),

    /* Non-stack Node Values */
    /** Requests current Value of a node (e.g. boolean/number)
     *
     * See:
     * * https://veado.tube/docs/tech/api/nodes/#number
     * * https://veado.tube/docs/tech/api/nodes/#boolean
     */
    @SerialName("get")
    GET("get"),

    /** Toggle boolean node (e.g. true to false & vice-versa)
     *
     * See https://veado.tube/docs/tech/api/nodes/#boolean
     */
    @SerialName("toggle")
    TOGGLE("toggle"),

    /** Add (or subtract) number node
     *
     * See https://veado.tube/docs/tech/api/nodes/#number
     */
    @SerialName("add")
    ADD("add"),

    /** Clears current Value of a node (e.g. boolean/number)
     *
     * See:
     * * https://veado.tube/docs/tech/api/nodes/#number
     * * https://veado.tube/docs/tech/api/nodes/#boolean
     */
    @SerialName("clear")
    CLEAR("clear"),
    ;

    companion object {
        /**
         * Gets Enum Constant with given Value
         *
         * Returns UNKNOWN ENUM if not found
         *
         * @return Enum Constant
         */
        @JvmStatic
        fun fromValue(value: String): PayloadEvent {
            return requireNotNull(entries.find { it.value == value }) { UNKNOWN }
        }
    }
}