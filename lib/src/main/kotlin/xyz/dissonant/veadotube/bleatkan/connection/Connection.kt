package xyz.dissonant.veadotube.bleatkan.connection


import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.utils.io.errors.*
import io.ktor.websocket.*

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.*

import xyz.dissonant.veadotube.bleatkan.Client as VtClient
import xyz.dissonant.veadotube.bleatkan.instance.Instance
import xyz.dissonant.veadotube.bleatkan.message.*

import java.net.URI
import java.util.*
import kotlin.collections.HashMap


/**
 * TBC
 * Connection Object
 * Holds InstanceID, Server, & Name values and generates WebSocket {@code URI}
 *
 * @param instance Instance this Connection is connected to
 * @param receiver ConnectionReceiver to get callbacks
 * @param connectionJobParent Optional Job that will be used in the Scope of the Websocket Receiver Loop.
 * A default Job and Supervisor is used of non is provided, and all Connections can be closed using [Connection.closeAll]
 *
 * @see <a href="https://gitlab.com/veadotube/bleatcan/-/blob/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan/Connection.cs">Veadotube bleatcan Connection.cs on Gitlab</a>
 * @see java.net.URI
 * @see xyz.dissonant.veadotube.bleatkan.instance.InstanceID
 */

class Connection
@JvmOverloads constructor(
    instance: Instance,
    receiver: ConnectionReceiver,
    name: String? = null,
    connectionJobParent: Job = Job(connectionDefaultJobParent)
) : AutoCloseable {

    companion object {

        private const val NULL_BYTE: Byte = 0
        
        private const val COLON_BYTE = ':'.code.toByte()

        /**
         * Maximum Connection Errors in a row before giving up and
         */
        private const val WS_CONN_ERROR_MAX: Int = 10

        /**
         * Wait timer after connection error
         */
        private const val WS_CONN_ERROR_WAIT_MS: Long = 500


        /**
         * Coroutine Supervisor Job - Parent of all jobs (if none provided on construction) and can be used to cancel all Connections
         */
        @JvmStatic
        private val connectionDefaultJobParent by lazy { SupervisorJob() }

        /**
         * Close all Connection Jobs tied to the default Connection Job Parent.
         *
         * Connections that were given another Job Parent are not closed
         */
        @JvmStatic
        fun closeAll() {
            LOGGER.debug { "Connection.closeAll: Cancelling Parent Jobs" }
            connectionDefaultJobParent.cancel("Connection.CloseAll() Called")
            LOGGER.debug { "Connection.closeAll: Done" }
        }

        @JvmStatic
        private val LOGGER = KotlinLogging.logger {}

    }


    private val connectionReceiver: ConnectionReceiver = receiver

    /**
     * Instance this Connection is connected to
     */
    val instance: Instance

    /**
     * Server this Connection is connected to
     */
    val server: String = instance.server

    /**
     * Name of Instance this Connection is connected to
     */
    val name: String = name ?: instance.name

    /**
     * Connection URI
     *
     * The URI Path this WebSocket communicates with
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val connUri: URI

    /**
     * Time Connection was created in Milliseconds.
     *
     * Set just before WebSocket Watcher is created.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val connectionTimeMillis: Long

    /**
     * ID for the Connection - Currently the URI String
     */
    val id: String

    /**
     * Coroutine Job - contains all Jobs for this Connection
     */
    private val connectionJob: Job = connectionJobParent

    /**
     * Coroutine Dispatcher for Connection
     */
    private val wsReceiveDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(
            1,
            "connection-dsp-$server-${this.name.replace(' ', '~')}"
        )

    /**
     * Scope for this Connection, should be used to launch Jobs
     */
    private val websocketScope: CoroutineScope = CoroutineScope(
        connectionJob + wsReceiveDispatcher + CoroutineName(
            "connection-cr_$server-${this.name.replace(' ', '~')}"
        )
    )


    /**
     * Mutex for HttpClient Creation/Removal
     */
    private var httpClientMutex: Mutex = Mutex()

    /**
     * HttpClient that creates Websocket Sessions, etc.
     */
    private var httpClient: HttpClient? = null

    /**
     * WebSocket Session
     */
    private var webSocketSession: WebSocketSession? = null

    /** Deferred Close Reason for the Websocket to get Close Reason after Close */
    private var wsscr: Deferred<CloseReason?>? = null


    /* Start Client Vars
     Not really implemented here, exists in BleatCan but not used by anything right now
     */

    private val clientsMap: HashMap<String, HashSet<VtClient>> = HashMap()
    private var clientsActive = false

    /* End Client Vars */

    private var activeLoop = false
    var isConnected = false
        private set
    var isClosed = false
        private set


    init {
        LOGGER.trace { "Constructing Connection" }
        require(this.server.isNotBlank())
        require(this.name.isNotBlank())


        //Assign internal Instance Value
        this.instance = instance

        //Get URI
        try {
            this.instance.getWebSocketUri(this.name)
            connUri = Instance.getWebSocketUri(this.server, this.name)
        } catch (ex: IllegalArgumentException) {
            receiver.onError(this, ConnectionError.InvalidServerOrName)
            throw ex
        }



        LOGGER.trace { "Connection Websocket Target: $connUri" }

        id = connUri.toString()

        connectionTimeMillis = System.currentTimeMillis()

        LOGGER.trace { "Connection Constructor: RunBlock Start" }



        runBlocking {
            LOGGER.trace { "Connection Constructor: HTTPClient Creation" }
            setupHttpClient()
            LOGGER.trace { "Connection Constructor: HTTPClient Creation Done" }

            activeLoop = true

            LOGGER.trace { "Connection Constructor: Launch runWebsocketReceive() in $websocketScope" }

            //Run in different scope/context, allowing RunBlock to exit
            websocketScope.async {
                LOGGER.trace { "Connection Constructor async: Launching runWebsocketReceive()" }
                runWebsocketWatcher()
            }.invokeOnCompletion {
                if (it == null) {
                    //Closed without Error
                    LOGGER.trace { "runWebsocketWatcher OnCompletion: Closed Normally" }
                } else {
                    if (it.cause is CancellationException) {
                        //Closed, Normal with Cancellation
                        LOGGER.trace { "runWebsocketWatcher OnCompletion: Closed Normally with Cancellation" }
                    } else {
                        //Closed with error
                        LOGGER.warn { "runWebsocketWatcher OnCompletion: Closed with Error: ${it.message}" }
                        stopWebsocket(closeReason = CloseReason.Codes.INTERNAL_ERROR, "Error: ${it.message}")
                    }
                }
                //Close/Cleanup this Connection
                close()
            }
            LOGGER.trace { "Connection Constructor: Launched runWebsocketWatcher" }
        }

        LOGGER.trace { "Connection Constructor: RunBlock Done" }

    }


    private fun setupHttpClient() {
        LOGGER.trace { "Connection Constructor: HTTPClient Creation" }

        runBlocking {
            //Lock to prevent Concurrent Creation/Destruction
            LOGGER.trace { "Connection Constructor: HTTPClient Mutex Locking" }
            httpClientMutex.lock(this)
            LOGGER.trace { "Connection Constructor: HTTPClient Mutex Locked" }

            //Client Setup
            if (httpClient?.isActive != true) {
                httpClient?.close()
                httpClient = null


                httpClient = HttpClient(CIO) {
                    install(WebSockets) {
                        pingInterval = 4_000
                    }
                    engine {
                        endpoint.connectTimeout = 6_000
                        endpoint.connectAttempts = 4
                        endpoint.keepAliveTime = 12_000
                        endpoint.socketTimeout = 12_000
                    }
                    install(Logging) {
                        logger = Logger.DEFAULT
                        level = if (LOGGER.isDebugEnabled()) LogLevel.INFO else LogLevel.NONE
                    }
                }
            }
            httpClientMutex.unlock(this)
            LOGGER.trace { "Connection Constructor: HTTPClient Mutex Unlocked" }
        }

        LOGGER.trace { "Connection Constructor: HTTPClient Creation Done" }
    }


    private fun shutdownHttpClient() {
        LOGGER.trace { "shutdownHttpClient(): HTTPClient Shutdown" }
        if (httpClient == null) return

        runBlocking {
            //Lock to prevent Concurrent Creation/Destruction
            LOGGER.trace { "shutdownHttpClient(): HTTPClient Mutex Locking" }
            httpClientMutex.lock(this)
            LOGGER.trace { "shutdownHttpClient(): HTTPClient Mutex Locked" }

            //Client Setup
            if (httpClient?.isActive != true) {
                httpClient?.close()

                httpClient = null
            }

            httpClientMutex.unlock(this)
            LOGGER.trace { "shutdownHttpClient(): HTTPClient Mutex Unlocked" }
        }
        LOGGER.trace { "shutdownHttpClient(): HTTPClient Shutdown Done" }
    }


    private fun stopWebsocket(
        closeReason: CloseReason.Codes = CloseReason.Codes.NORMAL,
        closeMessage: String = "bye"
    ) {
        LOGGER.trace { "stopWebsocket: Start" }
        if (webSocketSession != null) {
            LOGGER.trace { "stopWebsocket: Closing WS - Reason: $closeReason ; Message: $closeMessage" }

            runBlocking {
                webSocketSession?.close(
                    CloseReason(closeReason, closeMessage)
                )
            }

            webSocketSession = null
            LOGGER.trace { "stopWebsocket: Done" }
        }
    }

    //New Watcher
    private suspend fun runWebsocketWatcher() {
        LOGGER.trace { "runWebsocketWatcher: Started" }

        var errorCount = 0


        while (activeLoop) {
            /* Start Websocket Loop Block */
            LOGGER.trace { "runWebsocketWatcher: Start of Loop - webSocketSession?.isActive ${webSocketSession?.isActive} " }



            try {
                LOGGER.trace { "runWebsocketWatcher: Launch startWebsocket in $websocketScope" }

                //Launch Websocket & Receiver
                val receiver = websocketScope.async {
                    LOGGER.trace { "runWebsocketWatcher: Launching startWebsocket" }
                    startWebsocketNew()
                    LOGGER.trace { "runWebsocketWatcher: Launched startWebsocket" }
                }


                // Suspend until exit
                receiver.await()

                // Reset Error Count (if await doesn't Error)
                errorCount = 0


            } catch (ex: CancellationException) {
                // Exception - being closed, should quit
                LOGGER.error { "startWebsocket: Cancelled" }
                activeLoop = false

            } catch (ex: Exception) {
                // Other Exception

                val closeReason = wsscr?.await()

                if (closeReason?.knownReason == CloseReason.Codes.NORMAL || closeReason?.knownReason == CloseReason.Codes.GOING_AWAY) {
                    // Exception was thrown, but was closed normally from other side
                    LOGGER.trace { "runWebsocketWatcher: Closing > $closeReason" }
                    LOGGER.trace { "runWebsocketWatcher: Exception was thrown, but closure was normal ${ex.javaClass} - ${ex.message}" }
                } else {
                    errorCount++

                    LOGGER.error { "runWebsocketWatcher: Closing With Exception Reason: $closeReason" }

                    LOGGER.error { "runWebsocketWatcher: Error ($errorCount in a row) ${ex.javaClass} - ${ex.message}" }



                    webSocketSession?.close(
                        CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Error: ${ex.message}")
                    )
                    webSocketSession = null

                    if (ex is IOException) {
                        LOGGER.error { "runWebsocketWatcher: Error connecting to $connUri - Invalid Server or Name" }
                        connectionReceiver.onError(this, ConnectionError.FailedToConnect)
                    } else {
                        connectionReceiver.onError(this, ConnectionError.None)
                    }

                    if (errorCount >= WS_CONN_ERROR_MAX) {
                        //Max Retries Reached
                        LOGGER.warn { "runWebsocketWatcher: Max Retries reached" }
                        activeLoop = false
                        connectionReceiver.onError(this, ConnectionError.ExceededRetries)
                        throw ex
                    }

                    delay(WS_CONN_ERROR_WAIT_MS)
                }
            } finally {
                val closeReason = wsscr?.await()

                if (closeReason?.knownReason == CloseReason.Codes.NORMAL || closeReason?.knownReason == CloseReason.Codes.GOING_AWAY) {
                    LOGGER.trace { "runWebsocketWatcher: Closing > $closeReason" }
                } else {
                    errorCount++
                    LOGGER.warn { "runWebsocketWatcher: Closing Abnormally > $closeReason" }
                }

                webSocketSession?.close()
                webSocketSession = null

                // Websocket has quit, so we need to clean up
                if (isConnected) {
                    // isConnected is true but webSocket is not Connected
                    // Post-Disconnect Tear-down, etc.
                    LOGGER.trace { "runWebsocketWatcher: WS not Active & isConnected is true > cleanup start " }
                    connectionReceiver.onConnect(this, false)
                    updateClients(isConnected)
                    isConnected = false
                    LOGGER.trace { "runWebsocketWatcher: WS not Active & isConnected is true > cleanup done " }
                }
            }

            /* End Websocket Loop Block */
        }


        LOGGER.trace { "runWebsocketWatcher: Ended" }
    }

    // New Setup
    private suspend fun startWebsocketNew() {
        LOGGER.trace { "startWebsocket: Connecting: $connUri (${connUri.host}, ${connUri.port}, ${connUri.rawPath}?${connUri.rawQuery})" }
        check(httpClient != null && httpClient!!.isActive) { "HttpClient is not active" }
        check(webSocketSession?.isActive != true) { "webSocketSession is already active and in use" }

        httpClient?.webSocket(
            host = connUri.host,
            port = connUri.port,
            path = "${connUri.rawPath}?${connUri.rawQuery}"
        ) {
            /*Setup */
            LOGGER.trace { "websocket session block: Connected" }
            //Export WS Session and CloseReason
            webSocketSession = this
            wsscr = this.closeReason

            //Run Receiver
            LOGGER.trace { "websocket session block: Receiver Started" }
            runWebsocketReceiver(this.incoming)
            LOGGER.trace { "websocket session block: Receiver Closed" }
        }

        LOGGER.trace { "startWebsocket: Disconnected: $connUri" }
    }

    // New Receiver
    private suspend fun runWebsocketReceiver(incoming: ReceiveChannel<Frame>) {
        LOGGER.trace { "runWebsocketReceiver: Started" }


        // WebSocket Session is connected
        if (!isConnected) {
            // Connected, but isConnected is false
            // Post-Connect Setup
            LOGGER.trace { "runWebsocketReceiver: WS Active & isConnected is false > onConnect start " }
            connectionReceiver.onConnect(this, true)
            updateClients(isConnected)
            isConnected = true
            LOGGER.trace { "runWebsocketReceiver: WS Active & isConnected is false > onConnect done " }
        }


        /* Start Get and Process Message Block*/
        LOGGER.trace { "runWebsocketReceiver: webSocketSession?.isActive ${webSocketSession?.isActive} " }

        LOGGER.trace { "runWebsocketReceiver: Started Receiving Frames" }

        for (frame in incoming) {
            LOGGER.trace { "runWebsocketReceiver: Received Frame - $frame " }

            when (frame) {

                is Frame.Text -> {
                    //Should only receive Text/JSON, but processing is done using a Byte Array
                    val messageBytes = frame.readBytes()
                    LOGGER.trace { "runWebsocketReceiver: ${frame.frameType} Frame with ${frame.readBytes().size} Bytes" }
                    processReceivedMessage(messageBytes)
                }

                is Frame.Binary -> {
                    // Should never happen with Veadotube - catch and log
                    LOGGER.debug { "runWebsocketReceiver: ${frame.frameType} Frame with ${frame.readBytes().size} Bytes" }
                }

                is Frame.Close, is Frame.Ping, is Frame.Pong -> {
                    // Should never happen without Raw Socket
                    LOGGER.debug { "runWebsocketReceiver: ${frame.frameType} Frame with ${frame.readBytes().size} Bytes" }
                }

                else -> {
                    // Should never happen without Raw Socket
                    LOGGER.debug { "runWebsocketReceiver: ${frame.frameType} Frame" }
                }

            }

            if (!activeLoop) {
                LOGGER.trace { "runWebsocketReceiver: activeLoop False break out of Receive loop" }
                break
            }
        }
        LOGGER.trace { "runWebsocketReceiver: Stopped Receiving Frames" }

        /* End Get and Process Message Block*/


        LOGGER.trace { "runWebsocketReceiver: Ended" }
    }


    private fun processReceivedMessage(message: ByteArray) {
        LOGGER.debug { "processReceivedMessage ${message.hashCode()}: ByteArray to Process: ${message.size} Bytes" }

        /* Basic Decode Block Start */
        // Gets Index of first colon (':') - text before this should represent the Veadotube Channel
        val channelCharEnd = message.indexOf(COLON_BYTE)
        if (channelCharEnd < 0) {
            LOGGER.debug { "processReceivedMessage${message.hashCode()}: Message Missing 'channel:'" }
            return
        } // not found, invalid message

        // If message starts with 'nodes:' (or any other channel prefix) we need to get it
        // Only 'nodes' exists as a channel for now in veadotube mini, but this could change
        val channel = try {
            String(message, 0, channelCharEnd)
        } catch (ex: Exception) {
            LOGGER.debug { "processReceivedMessage ${message.hashCode()}: Error extracting Channel: ${ex.message}" }
            return
        }
        if (channel.isBlank()) {
            LOGGER.debug { "processReceivedMessage ${message.hashCode()}: Message Missing Channel" }
            return
        }

        LOGGER.trace { "processReceivedMessage ${message.hashCode()}: Channel '$channel'" }

        val nullCharIndex = message.indexOf(NULL_BYTE)
        val textTrimIndex =
            if (nullCharIndex > 0) {
                LOGGER.trace { "processReceivedMessage ${message.hashCode()}: Culling Nulls after $nullCharIndex" }
                nullCharIndex
            } else {
                LOGGER.trace { "processReceivedMessage ${message.hashCode()}: No Nulls to Cull" }
                message.size
            }
        //Extract JSON
        val textCleaned = try {
            String(message, channelCharEnd + 1, textTrimIndex - (channelCharEnd + 1))
        } catch (ex: Exception) {
            LOGGER.debug { "processReceivedMessage ${message.hashCode()}: Error extracting JSON: ${ex.message}" }
            return
        }

        LOGGER.trace { "processReceivedMessage ${message.hashCode()}: Final Processed Message:\nChannel: $channel\nJSON: $textCleaned" }
        /* Basic Decode Block End */

        // Decode to Object
        val messageObj: ResultMessage = try {
            convertMessage(textCleaned)
        } catch (ex: Exception) {
            LOGGER.debug { "processReceivedMessage ${message.hashCode()}: Error Decoding JSON: ${ex.message}" }
            return
        }
        LOGGER.trace { "processReceivedMessage ${message.hashCode()}: Decoded Message:\nVtResultMessage - ${messageObj.javaClass}\n$messageObj" }

        //Pass Decoded Message to connectionReceiver, clients
        passReceivedToClients(channel, messageObj)

    }


    private fun convertMessage(textCleaned: String): ResultMessage {
        //Decode and Convert JSON to Object
        LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: Attempting to decode JSON String to object:\n$textCleaned" }
        val jsonVtMessage: ResultMessage =
            try {
                Json.decodeFromString(textCleaned)
            } catch (ex: Exception) {
                LOGGER.warn { "convertMessage ${textCleaned.hashCode()}: Unable to decode JSON String to VtResultMessage: ${ex.message}\n$textCleaned" }

                try {
                    //Try to convert to generic JSON Element - this isn't passed, but will let us know if it's valid JSON
                    val jsonMessage = Json.parseToJsonElement(textCleaned)
                    LOGGER.warn { "convertMessage ${textCleaned.hashCode()}: Decoded Message JSON String to Generic JSON Element:\n$jsonMessage" }
                } catch (exInner: Exception) {
                    LOGGER.warn { "convertMessage ${textCleaned.hashCode()}: Unable to decode JSON String to Generic JSON Element: ${exInner.message}\n$textCleaned" }
                }

                throw ex
            }

        // If Decode failed, throw should have exited

        if (LOGGER.isTraceEnabled()) {
            // Trace is Enabled, process block to output info (Skip if not)
            LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Event: " + jsonVtMessage.event }
            if (jsonVtMessage is ResultMessage.ResultMessageEntries) {
                LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Class: VtResultMessageEntries" }
                LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Entries: ${jsonVtMessage.entries}" }
                for (entry in jsonVtMessage.entries) {
                    LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Entries -> Entry: $entry" }
                }
            } else if (jsonVtMessage is ResultMessage.ResultMessagePayload) {
                LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Class: VtResultMessagePayload" }
                LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> ID: ${jsonVtMessage.id}" }
                LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Type: ${jsonVtMessage.type}" }
                LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Name: ${jsonVtMessage.name}" }

                if (jsonVtMessage.payload is VtResultPayload.VTResultSEListPayload) {
                    LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Payload -> Event: ${jsonVtMessage.payload.event}" }

                    for (state in jsonVtMessage.payload.states) {
                        LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Payload -> States -> State: $state" }
                    }
                } else if (jsonVtMessage.payload is VtResultPayload.VTResultSEPeekPayload) {
                    LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Payload -> Event: ${jsonVtMessage.payload.event}" }
                    LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Payload -> State: ${jsonVtMessage.payload.state}" }
                }

            } else {
                LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Unknown Message Class: ${jsonVtMessage::class}" }
                LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: -> Contents: $jsonVtMessage" }
            }
        }

        LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: Decoded JSON String to ${jsonVtMessage::class}" }

        return jsonVtMessage
    }

    private fun passReceivedToClients(channel: String, data: ResultMessage) {

        connectionReceiver.onReceive(this, channel, data)

        synchronized(clientsMap) {
            clientsMap[channel]?.forEach { client -> client.emitReceive(channel, data) }
        }

    }

    private fun updateClients(isConnected: Boolean) {
        synchronized(clientsMap) {
            clientsActive = isConnected
            clientsMap.values.stream().flatMap { it.stream() }
                .distinct().forEach { it?.emitConnect(clientsActive) }
        }
    }


    override fun close() {
        LOGGER.trace { "Connection Closing" }
        runBlocking {
            LOGGER.trace { "Connection Close: activeLoop false" }
            activeLoop = false
            delay(200)
            LOGGER.trace { "Connection Close: stopWebsocket()" }
            stopWebsocket()
            delay(100)
            LOGGER.trace { "Connection Close: client Close Check" }
            shutdownHttpClient()
            isClosed = true
        }
        LOGGER.trace { "Connection Closed" }
    }

    // Method to add or remove clients from channels
    fun setClient(client: VtClient, active: Boolean) {
        synchronized(clientsMap) {
            if (active) {
                // Passed Client to be activated
                for (channel in client.channels) {
                    // For each channel in client channel list
                    // Get the HashSet against the Channel Name. If one doesn't exist, create a new one.
                    // Add Client to HashSet
                    clientsMap.getOrDefault(channel, HashSet<VtClient>())
                        .add(client)
                }
                //If clients are set to active, send Connect
                if (clientsActive) {
                    client.emitConnect(true)
                }
            } else {
                // Passed Client to be deactivated
                for (channel in client.channels) {
                    //Get Set against channel
                    val set: HashSet<VtClient>? = clientsMap[channel]
                    if (set != null && set.remove(client) && set.isEmpty()) {
                        //if Set exists against channel, remove Client from set, and remove Set from Map if empty
                        clientsMap.remove(channel)
                    }
                }
                //If clients are set to inactive, send Disconnect
                if (clientsActive) {
                    client.emitConnect(false)
                }
            }
        }
    }

    // Send message - Illegal State Exception if not active, Illegal Argument for Channel, ClosedSendChannelException if Channel is Closed other error if send fails
    fun send(channel: String = "nodes", requestData: RequestMessage, validateRequest: Boolean = false) {
        check(!isClosed) { "Connection is Closed" }
        check(activeLoop) { "Connection Websocket not active" }
        require(channel.isNotBlank()) { "Channel cannot be blank" }
        check(webSocketSession?.isActive ?: false) { "Connection Websocket Session is not active" }
        if (validateRequest) require(RequestMessage.validate(requestData)) { "Request is not valid" }

        runBlocking {
            try {
                //Convert to String with Channel Prefix and Send
                val dataAsString = "$channel:${Json.encodeToString(RequestMessage.serializer(), requestData)}"
                LOGGER.trace { "Sending message: '$dataAsString'" }
                webSocketSession?.send(dataAsString)
            } catch (e: Exception) {
                //Could be ClosedSendChannelException
                LOGGER.error { "send: Error: $e" }
                throw e
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Connection

        if (connectionTimeMillis != other.connectionTimeMillis) return false
        if (id != other.id) return false
        if (instance.id != other.instance.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = connectionTimeMillis.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + instance.id.hashCode()
        return result
    }

    override fun toString(): String {
        return "Connection(instance=${instance.id}, id='$id', connectionTimeMillis=$connectionTimeMillis)"
    }

}



