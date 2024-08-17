package xyz.dissonant.veadotube.bleatkan.instance

import xyz.dissonant.veadotube.bleatkan.connection.Connection
import xyz.dissonant.veadotube.bleatkan.connection.IConnectionReceiver
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Represents an Instance
 *
 * Holds *ID* [InstanceID], *Server*, & *Name* values.
 * Can Generate a WebSocket [URI]
 *
 *
 * Based on [Veadotube bleatcan Instance.cs on Gitlab](https://gitlab.com/veadotube/bleatcan/-/blob/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan/Instance.cs)
 *
 * @see URI
 *
 * @see InstanceID
 */
@Suppress("MemberVisibilityCanBePrivate")
class Instance(instanceID: InstanceID, instanceName: String, serverAddress: String, lastModified:Long=-1) {
    /**
     * Instance ID
     *
     * @see InstanceID
     */
    val id: InstanceID

    /**
     * Client Display Name (For example "veadotube mini")
     *
     * Unlikely to change, but can if Server in Veadotube is changed during run.
     *
     * In this case the connection may close, or may not fail until next request
     * The instance file will update, but connections may need updating, closing, reopening, etc.
     */
    val name: String


    /**
     * Server IP and Port separated with a colon (For example "127.0.0.1:12345")
     *
     * Unlikely to change, but can if Server in Veadotube is changed during run.
     *
     * In this case the connection will likely close, but may not fail until next request.
     * The instance file will update, but connections may need updating, closing, reopening, etc.
     */
    val server: String


    /**
     * Unix Timestamp, Long
     * Last retrieved Time Value from Instance file
     * Anything older than 10 seconds should be assumed dead and removed
     */
    var fileLastModified: Long
        internal set

    /**
     * ID Unique to this Instance combining Server, Name, and ID.
     *
     * This String is Unique to the Veadotube Instance and Name, and excludes the fileLastModified value.
     *
     * A new Instance created with the same inputs would have the same instanceConnectionID
     */
    val instanceConnectionID: String


    init {
        require(instanceID.isValid) { "InstanceID is not Valid" }
        require(instanceName.isNotBlank()) { "instanceName is blank" }
        require(serverAddress.isNotBlank()) { "serverAddress is blank" }

        id = instanceID
        name = instanceName
        server = serverAddress
        fileLastModified = lastModified

        instanceConnectionID = "$name-${server}_${id}"
    }

    fun propertiesEqual(i: Instance): Boolean {
        return name == i.name && server == i.server && id == i.id
    }

    /**
     * Returns the URI for this Instance
     * This is one-to-one for Veadotube Mini
     *
     * @return URI for Instance & Client. Characters encoded as needed (e.g. "ws://127.0.0.1:12345?n=veadotube%20mini")
     */
    fun getWebSocketUri(): URI {
        return getWebSocketUri(server, name)
    }

    /**
     * Returns the URI for the given Client Name on this Instance
     * This is one-to-one for Veadotube Mini
     *
     * @param name Client Display Name (e.g. "veadotube mini")
     * @return URI for Instance & Client. Characters encoded as needed (e.g. "ws://127.0.0.1:12345?n=veadotube%20mini")
     */

    fun getWebSocketUri(name: String): URI {
        val nameTrim = name.trim()
        require(nameTrim.isNotBlank()) { "Name must not be blank" }

        return getWebSocketUri(server, nameTrim)
    }

    /**
     * Returns a new Connection on the Instance Server for the Given Name and Receiver
     *
     * @param name     Client Display Name (e.g. "veadotube mini")
     * @param receiver Object to be sent events by Connection Object
     * @return Connection
     */
    fun connect(name: String, receiver: IConnectionReceiver): Connection {

        val nameTrim = name.trim()
        require(nameTrim.isNotBlank()) { "Name must not be blank" }

        return Connection(this, receiver, nameTrim)
    }

    /**
     * Returns a new Connection on the Instance Server and Name for the Given Receiver
     *
     * Equivalent of Connection(instance (this), receiver)
     *
     * @param receiver Object to be sent events by Connection Object
     * @return Connection
     */
    fun connect(receiver: IConnectionReceiver): Connection {
        return Connection(this, receiver)
    }

    companion object {
        /**
         * Returns the URI for the given Client Name on this Instance
         * This is one-to-one for Veadotube Mini
         *
         * @param server Server IP and Port separated with a colon (e.g. "127.0.0.1:12345")
         * @param name   Client Display Name (e.g. "veadotube mini")
         * @return URI for Instance & Client. Characters encoded as needed (e.g. "ws://127.0.0.1:12345?n=veadotube%20mini")
         */
        @JvmStatic
        fun getWebSocketUri(server: String, name: String): URI {
            require(server.isNotBlank()) { "Server can not be empty or blank" }
            require(name.isNotBlank()) { "Name can not be empty or blank" }

            val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
            return URI("ws://$server?n=$encodedName")

        }
    }
}
