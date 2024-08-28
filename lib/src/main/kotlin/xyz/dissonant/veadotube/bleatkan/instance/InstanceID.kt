package xyz.dissonant.veadotube.bleatkan.instance

import java.util.*

/**
 * Object to hold parse basic Instance information from an instance filename:
 *
 * The constructor Takes the File Name, Splits into 3 Parts and stores for reference, etc.
 * The String should resemble *mini-08dc8d3c583c0587-00000f38*
 *
 *  * Instance Type (*mini* for veadotube mini, *live* for veadotube live, or *editor* for veadotube editor for example)
 *  * Launch Timestamp (Unix Timestamp as a Hexadecimal Number, 16 characters)
 *  * Process ID (as Hexadecimal Number, 8 characters)
 *
 * For more information, refer to [the veadotube websocket documentation page](https://veado.tube/help/docs/websocket/#finding-the-server)
 *
 * Immutable, can't be modified after creation
 *
 * @see [Veadotube bleatcan InstanceID.cs on Gitlab](https://gitlab.com/veadotube/bleatcan/-/blob/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan/InstanceID.cs)
 *
 * @param instanceIdString Instance Filename/ID
 */
class InstanceID(instanceIdString: String) : Comparable<InstanceID> {
    /**
     * Instance Type (mini, live, editor, etc.)
     */
    val type: String

    /**
     * Timestamp - Time Instance was Launched, converted from Hex, in C# DateTime Universal Time Ticks Format
     */
    val timestamp: Long

    /**
     * Process ID - Converted from Hex
     */
    val process: Int

    //Store Generated Values for reuse
    private var instanceID: String? = null //Never Returned as Null
    private var hashCode = 0 //Never returned as 0


    val isValid: Boolean


    init {
        /*
         Takes File Name, Splits into 3 Parts
         From: https://veado.tube/help/docs/websocket/
         - instance type, which should be mini for veadotube mini, live for veadotube live, editor for veadotube editor;
         - launch timestamp (18 Digit AD Timestamp), hex number, always 16 characters;
                Convert to UNIX TS after converting to Decimal
         - process id, hex number, always 8 characters.

         Example: mini-08dc8d3c583c0587-00000f38
        */

        require(instanceIdString.isNotBlank()) { "Instance ID String must not be empty or blank" }

        val instanceIdIn = instanceIdString.trim()
        val typeTemp: String?

        var timestampTemp: Long = 0L
        var processTemp: Int = 0


        //Split String
        val parts = instanceIdIn.split("-")
        //Test result
        stringFormatCheck(parts)

        // Try to Convert Timestamp from Hex
        try {
            timestampTemp = parts[1].toLong(radix = 16) //Convert from Hex to Instant
        } catch (e: NumberFormatException) {
            require(false) { "Instance ID String Invalid: 2nd part is not valid Hex Value" }
        }


        // Try to Convert Process ID from Hex
        try {
            processTemp = parts[2].toInt(16) //Convert from Hex to int
        } catch (e: NumberFormatException) {
            require(false) { "Instance ID String Invalid: 3rd part is not valid Hex Value" }
        }


        typeTemp = parts[0].trim()
        require(typeTemp.all { it.isLetter() }) { "Instance ID String Invalid: 1st part contains non-letter characters" }


        //Assign Values - this also runs when return is called
        type = typeTemp
        timestamp = timestampTemp
        process = processTemp


        // Set valid boolean here, only if no failure (may as well since we've done the checks already)
        isValid = true
    }


    /**
     * @return `String` in the format *Type-LaunchTime:Hex-ProcessId:Hex*
     */
    override fun toString(): String {
        if (!isValid) {
            return "" //Return blank string if not valid
        }

        // Set InstanceID String if blank - we'll return it instead of formatting a new string every time
        if (instanceID == null) {
            instanceID = String.format(FORMAT_STRING, type, timestamp, process)
        }

        return instanceID!!
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true //Same Object

        if (!isValid) return false //local not valid

        if (other !is InstanceID) return false // Not type InstanceID


        val that: InstanceID = other //Cast to type

        if (!other.isValid) return false //that not valid

        return timestamp == that.timestamp && process == that.process && type == that.type
    }

    override fun hashCode(): Int {
        //Return 0 if not valid
        if (!isValid) return 0

        // If cached Hash is empty, generate and store
        if (hashCode == 0) {
            hashCode = Objects.hash(type, timestamp, process)
        }

        return hashCode
    }

    /**
     * Comparison method for sorting, etc.
     * Sorted by Launch Time, then using ToString (Type-LaunchTime-ProcessId) if Launched at the same time
     *
     * @param other the InstanceID to be compared.
     * @return `int` representing whether the given InstanceID if before or after the first
     */
    override fun compareTo(other: InstanceID): Int {
        val c = this.timestamp.compareTo(other.timestamp)
        return if (c != 0) c else this.toString().compareTo(other.toString())
    }

    companion object {
        /**
         * String Format - Converts back to same format as instance file name
         */
        private const val FORMAT_STRING = "%s-%016x-%08x"

        /**
         * Takes the File Name, Splits into 3 Parts Tests
         * The String should resemble *mini-08dc8d3c583c0587-00000f38*
         *
         *  * Instance Type (*mini* for veadotube mini, *live* for veadotube live, or *editor* for veadotube editor for example)
         *  * Launch Timestamp (Unix Timestamp as a Hexadecimal Number, 16 characters)
         *  * Process ID (as Hexadecimal Number, 8 characters)
         *
         *  @throws IllegalArgumentException
         */
        fun stringFormatCheck(instanceIdString: String) {
            val parts = instanceIdString.trim().split("-")
            stringFormatCheck(parts)
        }

        /**
         * Takes pre-split File Name - Should be 3 parts
         * The String should resemble *mini-08dc8d3c583c0587-00000f38*
         *
         *  * Instance Type (*mini* for veadotube mini, *live* for veadotube live, or *editor* for veadotube editor for example)
         *  * Launch Timestamp (Unix Timestamp as a Hexadecimal Number, 16 characters)
         *  * Process ID (as Hexadecimal Number, 8 characters)
         *
         *  @throws IllegalArgumentException
         */
        private fun stringFormatCheck(instanceIdParts: List<String>) {
            //Check # of parts
            require(instanceIdParts.size == 3) { "Instance ID String Invalid: Must be 3 parts, separated by dashes (-)" }

            //Check Part lengths are correct
            require(instanceIdParts[1].length == 16) { "Instance ID String Invalid: 2nd part must be 16 Characters long" }
            require(instanceIdParts[2].length == 8) { "Instance ID String Invalid: 3nd part must be 8 Characters long" }
            require(instanceIdParts[0].isNotBlank()) { "Instance ID String Invalid: 1nd part must not be blank" }

        }

        fun equals(a: InstanceID, b: InstanceID): Boolean {
            return a == b
        }

        fun notEquals(a: InstanceID, b: InstanceID): Boolean {
            return a != b
        }


    }
}
