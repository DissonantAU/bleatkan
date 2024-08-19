package xyz.dissonant.veadotube.bleatkan

import xyz.dissonant.veadotube.bleatkan.connection.Connection

import xyz.dissonant.veadotube.bleatkan.serializable.RequestMessage
import xyz.dissonant.veadotube.bleatkan.serializable.VtResultMessage

import java.util.*


/**
 * Contains a list of Channels and a Connection - is used to trigger actions on a channel against the related connection (if set)
 *
* @param clientConnection Connection for this Client
* @param clientChannels Array of Channel Names
 *
 * @see [Veadotube bleatcan Client.cs on Gitlab](https://gitlab.com/veadotube/bleatcan/-/blob/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan/Client.cs)
 */
abstract class Client(clientConnection: Connection, clientChannels: List<String>?) : AutoCloseable {
    var connection: Connection? // Holds the connection associated with this client
        private set

    val channels: List<String> // Array to store channels associated with this client


    /**
     * Constructor accepting a Connection and a single channel.<br></br>
     * This puts the String into an Array and passes it to `Client(Connection connection, String[] channels)`
     * @param clientConnection Connection for this Client
     * @param clientChannel Channel Name
     */
    constructor(clientConnection: Connection, clientChannel: String) :
            this(
                clientConnection,
                ArrayList<String>().also { it.add(clientChannel) }
            )


    init {
        connection = clientConnection

        if (clientChannels.isNullOrEmpty()) {
            // if passed null, create empty array
            this.channels = ArrayList<String>()
        } else {
            // Filter out null or empty channels, remove duplicates, and convert to array
            this.channels = clientChannels
                .filter { it.isNotBlank() }
                .distinct().toCollection(ArrayList<String>())

        }

        // Register this client with the connection as active if not null
        registerWithConnection()
    }

    protected fun registerWithConnection() {
        // Register this client with the connection as active if not null
        connection?.setClient(this, true)
    }

    // Method to send data to the server on the first channel in the list
    protected fun send(data: RequestMessage) {
        if (channels.isEmpty()) return

        connection?.send(channels[0], data)
    }

    // Method to send data to the server on a specific channel
    protected fun send(channel: String, data: RequestMessage) {
        connection?.send(channel, data)
    }

    // Method to emit a Received Message to the abstract onReceive
    fun emitReceive(channel: String, data: VtResultMessage) {
        onReceive(channel, data)
    }


    // Method to emit a Connect event to the abstract onReceive
    fun emitConnect(active: Boolean) {
        onConnect(active)
    }

    // Protected method to handle connection status changes
    protected abstract fun onConnect(active: Boolean)

    // Protected method to handle received data
    protected abstract fun onReceive(channel: String?, data: VtResultMessage)



    override fun close() {
        // Notify the connection that this client is no longer active, return if not
        connection?.setClient(this, false)
        // Release the reference to the connection
        connection = null
    }
}
