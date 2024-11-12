package io.github.dissonantau.bleatkan.message


import kotlinx.serialization.*


/**
 * Represents an instance file's contents
 */
@Serializable
data class VeadoInstanceFile(
    /** Instance Server address - *IP:Port* */
    val server: String,
    /** Instance name/title */
    val name: String,
    /** Instance Version - "2.1a"
     *
     * This was added in mini version 2.1 - if value missing defaults to 2.0
     */
    val version: String = "2.0",
    /** Instance File Update Time */
    var time: Long,
)
