package io.github.dissonantau.bleatkan.instance


import io.github.dissonantau.bleatkan.connection.Connection
import io.github.dissonantau.bleatkan.connection.ConnectionListener
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Comparator
import kotlin.jvm.Throws


/**
 * Represents an Instance
 *
 * Holds *ID* [InstanceID], *Server*, & *Name* values.
 * Can Generate a WebSocket [URI]
 *
 * [InstancesManager] uses lastModified in the extended constructor for storing the timestamp in the Instance File, and for clearing stale Instances
 *
 * [InstanceVersion] was added in mini version 2.1 - if none is found, we assume it's v2.0
 *
 * Instance(id = [InstanceID], name = [String], version = [Double], server = [String], lastModified = [Long]) should be preferred
 *
 * Originally Based on [Veadotube bleatcan Instance.cs on Gitlab](https://gitlab.com/veadotube/bleatcan/-/blob/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan/Instance.cs)
 *
 * @param id [InstanceID] for Instance
 * @param title Name of Instance (Title Name from Instance File)
 * @param server Server connection Address & Port
 * @param version Version of Instance - should be combined with type from id to work out what features to support. Defaults to 2.0 which didn't provide the version.
 *
 * @see InstanceID
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
data class Instance(
    /**
     * Instance ID for this Instance (from File Name)
     *
     * @see InstanceID
     */
    val id: InstanceID,
    /**
     * Client Window Title (e.g. "veadotube mini" or "veadotube mini - main")
     *
     * Can change if user changes the title in Veadotube during run.
     *
     * In this case the connection may close, or may not fail until next request
     * The instance file will update, but connections may need updating, closing, reopening, etc.
     *
     * This value will be updated when the Instance changes
     */
    var title: String,

    /**
     * Server IP and Port separated with a colon (For example "127.0.0.1:12345")
     *
     * Unlikely to change, but can if Server in Veadotube is changed during run.
     *
     * In this case the connection will likely close, but may not fail until next request.
     * The instance file will update, but connections may need updating, closing, reopening, etc.
     *
     * This value will be updated when the Instance changes
     */
    var server: String = "",

    /**
     * Instance Version (For example "2.1")
     *
     * This was added in mini version 2.1 - if none is found, we assume it's 2.0
     *
     * For Comparable Version, use [instanceVersion]
     */
    val version: String = "2.0",

    /** Added with mini 2.1 */
    var language: String = "en"
) {

    /**
     * Last retrieved Unix Timestamp Value from Instance file
     *
     * Anything older than 10 seconds should be assumed dead and removed
     */
    var fileLastModified: Long = Long.MIN_VALUE
        internal set


    /**
     * Advanced Version for this Instance that is better comparable
     */
    val instanceVersion by lazy { InstanceVersion(version) }


    /**
     * ID Unique to this Instance combining Server, Name, and ID.
     *
     * This String is Unique to the Veadotube Instance and Name, and excludes the fileLastModified value.
     *
     * A new Instance created with the same inputs would have the same instanceConnectionID
     */
    val instanceConnectionID: String
        get() {
            return "${id}-$server-$title"
        }


    init {
        require(id.type.isNotEmpty()) { "InstanceID is not Valid" }
        //require(server.isNotBlank()) { "serverAddress is blank" } // Instance server might be blank temporarily
        require(title.isNotBlank()) { "instanceName is blank" }
    }

    @JvmOverloads
    constructor(
        id: InstanceID, name: String, server: String = "",
        version: String = "2.0", language: String = "en",
        lastModified: Long
    ) : this(
        id = id,
        title = name,
        server = server,
        version = version,
        language = language
    ) {
        fileLastModified = lastModified
    }

    /**
     * Returns the URI for this Instance, using the default connection name (Window Title) and Current time in millis
     *
     * It's recommended to provide a connection name to better identify the connection in Veadotube logs
     *
     * @return URI for Instance with the default connection name attached. Characters encoded as needed (e.g. "ws://127.0.0.1:12345?n=bleatkan-123456789")
     */
    @Throws(IllegalStateException::class)
    fun getWebSocketUri(): URI {
        check(server.isNotBlank()) { "Server can not be blank" }
        return generateWebSocketUri(server, "bleatkan-$id-${System.currentTimeMillis()}")
    }


    /**
     * Returns the URI for this Instance, using the provided connection name
     *
     * @param connectionName Name of Connection - this will appear in the Veadotube Logs (e.g. "api ab1234" > "?n=api%20ab1234")
     * @return URI for Instance & Client. Characters encoded as needed (e.g. "ws://127.0.0.1:12345?n=connection%20name")
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun getWebSocketUri(connectionName: String): URI {
        check(server.isNotBlank()) { "Server can not be blank" }
        require(connectionName.isNotBlank()) { "Name can not be blank" }
        return generateWebSocketUri(server, connectionName)
    }


    /**
     * Returns a new Connection on the Instance Server and Name for the Given Receiver
     *
     * Equivalent of Connection(instance (this), receiver)
     *
     * @param listener Object to be sent events by Connection Object
     * @param connectionName [String] Name used with Websocket to Identify Connection in Veadotube Logs - defaults to `"bleatkan-${System.currentTimeMillis()}"` if not provided.
     * @return Connection
     */
    fun connect(
        listener: ConnectionListener,
        connectionName: String = "bleatkan-${System.currentTimeMillis()}"
    ): Connection {
        return Connection(instance = this, listener = listener, connectionName = connectionName)
    }

    companion object {
        /**
         * Returns the URI for the given Client Name on this Instance
         *
         * This is one-to-one for Veadotube Mini
         *
         * @param server Server IP and Port separated with a colon (e.g. "127.0.0.1:12345")
         * @param connectionName Name of Connection - this will appear in the Veadotube Logs (e.g. "api ab1234" > "?n=api%20ab1234")
         * @return URI for Instance & Client. Characters encoded as needed (e.g. "ws://127.0.0.1:12345?n=connection%20name")
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun getWebSocketUri(server: String, connectionName: String): URI {
            require(server.isNotBlank()) { "Server can not be blank" }
            require(connectionName.isNotBlank()) { "Name can not be blank" }

            return generateWebSocketUri(server, connectionName)
        }

        /**
         * Generates and Returns the URI for the given Client Name on this Instance
         *
         * Internal with no checks on inputs - make sure server & connectionName are not blank
         *
         * @param server Server IP and Port separated with a colon (e.g. "127.0.0.1:12345")
         * @param connectionName Name of Connection - this will appear in the Veadotube Logs (e.g. "api ab1234" > "?n=api%20ab1234")
         * @return URI for Instance & Client. Characters encoded as needed (e.g. "ws://127.0.0.1:12345?n=connection%20name")
         */
        @Throws(IllegalArgumentException::class)
        private fun generateWebSocketUri(server: String, connectionName: String): URI {
            val encodedName = URLEncoder.encode(connectionName, StandardCharsets.UTF_8.toString())
            return URI("ws://$server?n=$encodedName")
        }

        val COMPARATOR_INSTANCE_BY_ID: Comparator<Instance> = compareBy { it.id.timestamp }

        val COMPARATOR_INSTANCE_BY_TITLE_LENGTH: Comparator<Instance> = compareBy { it.title.length }
    }
}
