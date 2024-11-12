package io.github.dissonantau.bleatkan.instance

/**
 * Represents the Instance Version for compatibility and features
 *
 * String format: [major 0-9+].[minor 0-9+][optional: patch a-z][optional: suffix string proceeded by a dash]
 *
 * Expects a String with one dot and an option letter
 * E.g.
 * * 2.0
 * * 2.1a
 * * 2.2c
 * * 2.12b-dev
 *
 *
 * Note regarding comparing versions with a suffix:
 *  * A version with a suffix is 'lower'/'before' one without and is assumed to be along the lines of *-dev*, *-test*, *beta*, etc.
 *
 *      **e.g. *2.1-dev*, is before *2.1***
 * * Two versions with a suffix are compared with a regular string compare
 */
class InstanceVersion(version: String) : Comparable<InstanceVersion> {
    /** Major Version */
    val major: Int

    /** Minor Version */
    val minor: Int

    /** Patch (Optional) */
    val patch: Char?

    /** Suffix (Optional) - excludes proceeding dash */
    val suffix: String?

    init {
        require(version.length<3) { "Version is too short" }

        // Proceeds optional suffix, negative 1 if it doesn't exist
        val dashIndex = version.indexOf('-')

        // Proceeds minor version (and patch/suffix
        val dotIndex = version.indexOf('.')

        require(dotIndex > 0) { "Missing Version Dot" }

        val tempMajor: String = version.substring(0, dotIndex)

        val tempMinor: String

        val tempPatch: Char?

        val tempSuffix: String?

        if (dashIndex > 0) {
            //Suffix exists
            tempSuffix = version.substring(dashIndex + 1, version.length)

            /*
              with patch char:
                minor:    version.substring(dotIndex +1, dashIndex -1)
                patch:    dashIndex -1
              without patch char:
                minor:    version.substring(dotIndex +1, dashIndex)
             */

            if (version[dashIndex - 1].isLetter()) {
                // has patch letter
                tempPatch = version[dashIndex - 1]
                tempMinor = version.substring(dotIndex + 1, dashIndex - 1)
            } else {
                // no patch letter
                tempPatch = null
                tempMinor = version.substring(dotIndex + 1, dashIndex)
            }

        } else {
            // No Suffix
            tempSuffix = null

            /*
              with patch char:
                minor:    version.substring(dotIndex +1, version.length -1)
                patch:    version.length -1
              without patch char:
                minor:    version.substring(dotIndex +1, version.length)
            */

            if (version[version.length - 1].isLowerCase()) {
                // has patch letter
                tempPatch = version[version.length - 1]
                tempMinor = version.substring(dotIndex + 1, version.length - 1)
            } else {
                // no patch letter
                tempPatch = null
                tempMinor = version.substring(dotIndex + 1, version.length)
            }

        }


        major = tempMajor.toInt()

        minor = tempMinor.toInt()

        patch = tempPatch

        suffix = tempSuffix

    }

    /**
     * Compares this object with the specified object for order. Returns zero if this object is equal
     * to the specified [other] object, a negative number if it's less than [other], or a positive number
     * if it's greater than [other].
     *
     * Note regarding versions with a suffix:
     * * A version with a suffix is 'lower'/'before' one without and is assumed to be along the lines of *-dev*, *-test*, *beta*, etc.
     *
     *       **e.g. *2.1-dev*, is before *2.1***
     * * Two versions with a suffix are compared with a regular string compare
     */
    override fun compareTo(other: InstanceVersion): Int {
        if (this === other) return 0

        if (this.major == other.major) {
            if (this.minor == other.minor) {
                if (this.patch == other.patch) {
                    // same, could be same letter or null
                    if (this.suffix == other.suffix) {
                        // same, could be same suffix or null
                        return 0
                    } else {
                        //suffix doesn't match - we assume that a version with a suffix is 'lower' than one without (e.g. -dev, -test, -beta, etc.)
                        when {
                            (this.suffix == null) -> {
                                // this is null, other isn't null with prior == check
                                // this = 2.1 - other is 2.1-dev - this is 'higher'/'after'
                                return 1
                            }

                            (other.suffix == null) -> {
                                // other is null, this isn't null with prior == check
                                // this = 2.1-dev - other is 2.1 - this is 'lower'/'before'
                                return -1
                            }

                            else -> {
                                //Both have values, return string compare
                                return this.suffix.compareTo(other.suffix)
                            }

                        }

                    }

                } else {
                    // patch doesn't match
                    when {
                        (this.patch == null) -> {
                            // this is null, other isn't null with prior == check
                            // this = 2.1 - other is 2.1a - this is 'lower'/'before'
                            return -1
                        }

                        (other.patch == null) -> {
                            // other is null, this isn't null with prior == check
                            // this = 2.1a - other is 2.1 - this is 'higher'/'after'
                            return 1
                        }

                        else -> {
                            //Both have values, return char compare
                            return this.patch.compareTo(other.patch)
                        }

                    }
                }
            } else return this.minor - other.minor
        } else return this.major - other.major

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InstanceVersion

        if (major != other.major) return false
        if (minor != other.minor) return false
        if (patch != other.patch) return false
        if (suffix != other.suffix) return false

        return true
    }

    override fun hashCode(): Int {
        var result = major
        result = 31 * result + minor
        result = 31 * result + (patch?.hashCode() ?: 0)
        result = 31 * result + (suffix?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return if (suffix != null)
            "$major.$minor${patch ?: ""}-$suffix"
        else
            "$major.$minor${patch ?: ""}"

    }

}