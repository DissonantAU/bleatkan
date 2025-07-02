package io.github.dissonantau.bleatkan.message


import kotlinx.serialization.*


/**
 * Represents a veadotube Instance file's contents
 */
@Serializable
data class VeadoInstanceFile(
    /** Server address of Instance
     * - Usually in "***IP:Port***" format
     * - IP is most likely loopback (127.0.0.1)
     * - Port is randomly chosen by Instance
     * - Can be blank if Websocket server is disabled in Instance
     */
    val server: String = "",
    /** Window Title/Name of Instance
     * - Value is called ***name*** in JSON, but is called [title] here for clarity
     */
    @SerialName("name")
    val title: String,
    /** Version of Instance
     * - **Not** Semantic Versioning
     * - Most likely in a format similar to: "2.1a"
     * - Value was added in veadotube mini 2.1
     * - Defaults to "2.0" if value is missing from Instance file
     */
    val version: String = "2.0",
    /** Timestamp of when the Instance File was last updated */
    @SerialName("time")
    val updatedTimestamp: Long,
    /** Language of Instance
     * - Value was added in veadotube mini 2.1
     * - Defaults to "en" if not found in file
     * - Value is called ***time*** in JSON, but is called [updatedTimestamp] here for clarity
     */
    val language: String = "en"
)
