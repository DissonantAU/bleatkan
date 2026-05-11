package io.github.dissonantau.bleatkan.connection

import org.jetbrains.annotations.TestOnly

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import java.net.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration


import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.json.*


import io.github.dissonantau.bleatkan.instance.Instance
import io.github.dissonantau.bleatkan.instance.InstanceID
import io.github.dissonantau.bleatkan.instance.InstancesManager.Companion.READ_LOOP_DELAY_MAX_MS
import io.github.dissonantau.bleatkan.message.*

/**
 * Represents a Connection to a Veadotube Instance.
 *
 * Contains Logic for Message processing, Websocket Creation/Clean up, etc
 *
 * @see <a href="https://gitlab.com/veadotube/bleatcan/-/blob/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan/Connection.cs">Veadotube bleatcan Connection.cs on Gitlab</a> (Original Reference)
 */
@Suppress("MemberVisibilityCanBePrivate")
class Connection : AutoCloseable {

    companion object {
        private const val NULL_BYTE: Byte = 0
        private const val COLON_BYTE = ':'.code.toByte()
        private const val BRACE_OPEN_BYTE = '{'.code.toByte()

        /** Compares by Instance Title Length, then Instance Start Timestamp */
        val COMPARATOR_CONNECTION_BY_INSTANCE_TITLE_LENGTH_TIMESTAMP: Comparator<Connection> =
            compareBy({ it.instance.title.length }, { it.instance.id.timestamp })

        /** Maximum Connection Errors in a row before giving up and */
        private const val WS_CONN_ERROR_MAX: Int = 5

        /** Wait timer after connection error */
        private const val WS_CONN_ERROR_WAIT_MS: Long = 500

        /** Coroutine Supervisor Job - Parent of all jobs (if none provided on construction) and can be used to cancel all Connections */
        @JvmStatic
        private val connectionDefaultJobParent by lazy { SupervisorJob() }

        /** JSON De/serializer */
        private val jsonDeserializer = Json {
            ignoreUnknownKeys = true
            useAlternativeNames = false
        }

        /**
         * Close all Connection Jobs tied to the default Connection Job Parent.
         *
         * Connections that were given another Job Parent must be closed separately
         */
        @JvmStatic
        fun closeAll() {
            LOGGER.trace { "Connection.closeAll: Cancelling Default Parent Jobs" }
            connectionDefaultJobParent.cancel("Connection.CloseAll() Called")
            LOGGER.trace { "Connection.closeAll: Done" }
        }

        @JvmStatic
        private val LOGGER = KotlinLogging.logger {}

    }

    /** ConnectionListener that will receive Events */
    private val connectionListener: ConnectionListener

    /** Instance this Connection is connected to */
    val instance: Instance

    /** Server this Connection is connected to */
    val server: String

    /**
     * Name of Connection - sent as part of Websocket URL and can be used to identify the Connection in Veadotube Logs
     *
     * Instance Name is used as part of the default name if none is provided
     */
    val name: String

    /**
     * Compatibility - Version Pre 2.1
     *
     * Signals that Version 2.0/2.0a API Compatibility should be used for this connection
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var compatibilityFlagMiniPre2dot1: Boolean = false
        private set

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

    /** ID for the Connection - Currently the URI String */
    val id: String

    /** Coroutine Job - contains all Jobs for this Connection */
    private val connectionJob: Job

    /** Coroutine Dispatcher for Connection */
    private val wsReceiveDispatcher: CoroutineDispatcher

    /** Context for this Connection */
    private val websocketContext: CoroutineContext

    /** Context & Job for this Connection */
    private val websocketJobContext: CoroutineContext

    /** Scope for this Connection, should be used to launch Jobs */
    private val websocketScope: CoroutineScope

    /** Parallelism for Websocket Context */
    private val websocketParallelism = 3

    /** Mutex for HttpClient Creation/Removal */
    private var httpClientMutex: Mutex = Mutex()

    /** HttpClient that creates Websocket Sessions, etc. */
    private var httpClient: HttpClient? = null

    /** WebSocket Session */
    private var webSocketSession: WebSocketSession? = null

    /** Deferred Close Reason for the Websocket to get Close Reason after Close */
    private var webSocketCloseReason: Deferred<CloseReason?>? = null

    /** Exception from runWebsocketReceiver (If thrown) */
    private var webSocketReceiverResult: Throwable? = null


    private var connectionActive = false
    var isConnected = false
        private set

    var isClosed = false
        private set


    /**
     * Represents a Connection to a Veadotube Instance.
     *
     * Contains Logic for Message processing, Websocket Creation/Clean up, etc
     *
     * @param instance [Instance] this Connection will connect to
     * @param listener [ConnectionListener] to get callbacks
     * @param connectionName [String] Name used with Websocket to Identify Connection in Veadotube Logs - defaults to `"bleatkan-${instance.id}"` if not provided. [connectionTimeMillis] is added to the end
     * @param connectionJobParent **Optional** [Job] that will be used in the Scope of the Websocket Receiver Loop.
     * A default Job and Supervisor is used of none is provided, allowing all Connections to be closed using [Connection.closeAll]
     *
     * @see <a href="https://gitlab.com/veadotube/bleatcan/-/blob/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan/Connection.cs">Veadotube bleatcan Connection.cs on Gitlab</a> (Original Reference)
     * @see java.net.URI
     * @see io.github.dissonantau.bleatkan.instance.InstanceID
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    constructor(
        instance: Instance,
        listener: ConnectionListener, connectionName: String = "bleatkan-${instance.id}",
        connectionJobParent: Job = connectionDefaultJobParent
    ) {
        LOGGER.trace { "Constructing Connection" }
        require(instance.server.isNotBlank())
        require(connectionName.isNotBlank())

        this.server = instance.server
        connectionTimeMillis = System.currentTimeMillis()
        this.name = "$connectionName-${connectionTimeMillis}"
        this.instance = instance
        connectionListener = listener


        //Get URI
        try {
            connUri = instance.getWebSocketUri(this.name)
        } catch (ex: RuntimeException) {
            listener.onConnectionError(this, ConnectionError.InvalidServerOrName, ex)
            throw ex
        }

        LOGGER.trace { "Connection Websocket Target: $connUri" }

        id = connUri.toString()

        // Compatibility flags
        if (instance.id.type == "mini" && instance.version == "2.0") {
            //Compatibility flag for pre 2.1
            compatibilityFlagMiniPre2dot1 = true
            LOGGER.debug { "API Compatibility Flag set: Mini Pre-Version 2.1" }
        }

        setupHttpClient()
        connectionActive = true

        /* Coroutine setup and Launch */
        connectionJob = SupervisorJob(connectionJobParent)

        wsReceiveDispatcher =
            Dispatchers.IO.limitedParallelism(
                parallelism = websocketParallelism,
                "connection-dsp-$server-${this.name.replace(' ', '~')}"
            )

        websocketContext = wsReceiveDispatcher + CoroutineName(
            "connection-cr_$server-${this.name.replace(' ', '~')}"
        )

        websocketJobContext = connectionJob + websocketContext
        websocketScope = CoroutineScope(websocketJobContext)

        LOGGER.trace { "Connection Constructor: Launch runWebsocketReceive() in $websocketScope" }

        // Run in different scope, allowing constructor to exit
        websocketScope.launch {
            runWebsocketWatcher()
        }

        LOGGER.trace { "Connection Constructor: Done" }
    }

    /**
     * Represents a Connection to a Veadotube Instance.
     *
     * Contains Logic for Message processing, Websocket Creation/Clean up, etc
     *
     * @param instance [Instance] this Connection will connect to
     * @param listener [ConnectionListener] to get callbacks
     * @param connectionName [String] Name used with Websocket to Identify Connection in Veadotube Logs - defaults to `"bleatkan-${instance.id}"` if not provided.
     *
     * A default Job and Supervisor is used for CoRoutine lifecycle management, allowing all Connections to be closed using [Connection.closeAll]
     *
     * @see <a href="https://gitlab.com/veadotube/bleatcan/-/blob/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan/Connection.cs">Veadotube bleatcan Connection.cs on Gitlab</a> (Original Reference)
     * @see java.net.URI
     * @see io.github.dissonantau.bleatkan.instance.InstanceID
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    constructor(
        instance: Instance, listener: ConnectionListener,
        connectionName: String = "bleatkan-${instance.id}",
    ) : this(
        instance = instance, listener = listener, connectionName = connectionName,
        connectionJobParent = connectionDefaultJobParent
    )

    /**
     * Represents a Connection to a Veadotube Instance.
     *
     * Contains Logic for Message processing, Websocket Creation/Clean up, etc
     *
     * @param instance [Instance] *Optional* A Default Dummy Instance is created and no connection is made, but a custom on could be made to test generated values, etc.
     * @param listener [ConnectionListener] to get callbacks
     * @param testFrameChannel [ReceiveChannel] that can be sent Fake Frames for testing runWebsocketReceiver/
     * @param mockWebSocketSession Mocked [WebSocketSession] that should return isActive as True and intercept send(dataAsString) for send to work
     *
     * A default Job and Supervisor is used fo lifecycle management, allowing all Connections to be closed using [Connection.closeAll]
     *
     * @see <a href="https://gitlab.com/veadotube/bleatcan/-/blob/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan/Connection.cs">Veadotube bleatcan Connection.cs on Gitlab</a> (Original Reference)
     * @see java.net.URI
     * @see io.github.dissonantau.bleatkan.instance.InstanceID
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    @TestOnly
    @VisibleForUnitTests
    internal constructor(
        instance: Instance = Instance(
            id = InstanceID("mini", 12345678, 1234),
            title = "dummy", server = "127.0.0.10:12345", version = "2.1a"
        ),
        listener: ConnectionListener,
        testFrameChannel: ReceiveChannel<Frame>, mockWebSocketSession: WebSocketSession,
        connectionName: String = "bleatkan-${instance.id}",
    ) {
        LOGGER.trace { "Constructing Connection" }
        require(instance.server.isNotBlank())
        require(connectionName.isNotBlank())

        this.server = instance.server
        connectionTimeMillis = System.currentTimeMillis()
        this.name = "$connectionName-${connectionTimeMillis}"
        this.instance = instance
        connectionListener = listener

        //Get URI
        try {
            connUri = instance.getWebSocketUri(this.name)
        } catch (ex: RuntimeException) {
            listener.onConnectionError(this, ConnectionError.InvalidServerOrName, ex)
            throw ex
        }

        LOGGER.trace { "Connection Websocket Target: $connUri" }

        id = connUri.toString()

        // Compatibility flags
        if (instance.id.type == "mini" && instance.version == "2.0") {
            //Compatibility flag for pre 2.1
            compatibilityFlagMiniPre2dot1 = true
            LOGGER.debug { "API Compatibility Flag set: Mini Version 2" }
        }

        //setupHttpClient() // Testing - Skipped
        connectionActive = true

        /* Coroutine setup and Launch */
        connectionJob = SupervisorJob(connectionDefaultJobParent)


        wsReceiveDispatcher =
            Dispatchers.IO.limitedParallelism(
                parallelism = websocketParallelism,
                "connection-dsp-$server-${this.name.replace(' ', '~')}"
            )

        websocketContext = wsReceiveDispatcher + CoroutineName(
            "connection-cr_$server-${this.name.replace(' ', '~')}"
        )

        websocketJobContext = connectionJob + websocketContext
        websocketScope = CoroutineScope(websocketJobContext)

        // Testing - Mock Session
        webSocketSession = mockWebSocketSession

        LOGGER.trace { "Connection Constructor: Launch runWebsocketReceiver() in $websocketScope" }

        //Run in different scope, allowing constructor to exit
        websocketScope.launch {
            runWebsocketReceiver(testFrameChannel) //Testing - Receive with fake Channel for Frames
        }

        LOGGER.trace { "Connection Constructor: Done" }
    }

    private fun setupHttpClient() {
        LOGGER.trace { "Connection Constructor: HTTPClient Creation Start" }

        runBlocking {
            // Lock to prevent Concurrent Creation/Destruction
            httpClientMutex.withLock(this) {
                // Run If HTTP Client is active
                if (httpClient?.isActive != true) {

                    // Cleanup any existing client
                    if (httpClient != null) {
                        LOGGER.trace { "Connection Constructor: httpClient not null - making sure it's closed" }
                        httpClient?.close()
                        httpClient = null
                    }

                    httpClient = HttpClient(CIO) {
                        install(WebSockets) {
                            pingInterval = 4_000.toDuration(DurationUnit.MILLISECONDS)
                        }
                        engine {
                            endpoint.connectTimeout = 6_000
                            endpoint.connectAttempts = 3
                            endpoint.keepAliveTime = 8_000
                            endpoint.socketTimeout = 12_000
                        }
                        install(Logging) {
                            logger = Logger.DEFAULT
                            level = if (LOGGER.isDebugEnabled()) LogLevel.INFO else LogLevel.NONE
                        }
                    }
                    LOGGER.trace { "Connection Constructor: httpClient created" }
                }
            }
        }
        LOGGER.trace { "Connection Constructor: HTTPClient Creation End" }
    }

    private fun shutdownHttpClient() {
        LOGGER.trace { "shutdownHttpClient(): HTTPClient Shutdown Start" }
        if (httpClient == null) return

        runBlocking {
            //Lock to prevent Concurrent Creation/Destruction
            LOGGER.trace { "shutdownHttpClient(): httpClientMutex lock pending" }

            httpClientMutex.withLock(this) {
                LOGGER.trace { "shutdownHttpClient(): httpClientMutex locked" }

                //Client Setup
                if (httpClient?.isActive == true) {
                    LOGGER.debug { "shutdownHttpClient(): httpClient is active" }

                    httpClient?.close()
                    httpClient = null

                    LOGGER.debug { "shutdownHttpClient(): httpClient closed" }
                }

            }

            LOGGER.trace { "shutdownHttpClient(): httpClientMutex unlocked" }
        }
        LOGGER.trace { "shutdownHttpClient(): HTTPClient Shutdown End" }
    }

    private fun stopWebsocket(
        closeReason: CloseReason.Codes = CloseReason.Codes.NORMAL, closeMessage: String = "bye"
    ) {
        LOGGER.trace { "stopWebsocket: Begin" }

        if (webSocketSession?.isActive == true) {
            LOGGER.debug { "stopWebsocket: Closing - Reason: $closeReason ; Message: $closeMessage" }

            runBlocking {
                webSocketSession?.close(
                    CloseReason(closeReason, closeMessage)
                )
            }

            webSocketSession = null
        } else {
            LOGGER.debug { "stopWebsocket: Already Closed" }
        }

        LOGGER.trace { "stopWebsocket: Complete" }
    }


    private suspend fun runWebsocketWatcher() {
        LOGGER.trace { "runWebsocketWatcher: Begin" }
        var closeReason: CloseReason? = null

        try {
            /* Start Websocket Block */
            try {
                try {
                    LOGGER.trace { "runWebsocketWatcher: Launch startWebsocketSession" }
                    startWebsocketSession()
                } finally {
                    //Make sure we get Close Reason
                    LOGGER.trace { "runWebsocketWatcher: Waiting for Websocket Close Reason" }
                    closeReason = webSocketCloseReason?.await()
                    LOGGER.trace { "runWebsocketWatcher: Websocket Close Reason = $closeReason" }
                }

            } catch (ex: CancellationException) {
                // CancellationException - Upstream Job is being closed, we should quit
                connectionActive = false
            } catch (ex: Exception) {
                val cancel: Boolean
                when (ex) {
                    is ConnectException -> {
                        LOGGER.debug { "runWebsocketWatcher: Error connecting to $connUri - Invalid Server or Name, or Server is not available" }
                        cancel = connectionListener.onConnectionError(this, ConnectionError.FailedToConnect, ex)
                    }
                    is IllegalStateException -> {
                        LOGGER.warn { "runWebsocketWatcher: Error connecting to $connUri - Illegal State: ${ex.message}" }
                        cancel = connectionListener.onConnectionError(this, ConnectionError.IllegalState, ex)
                    }
                    else -> {
                        LOGGER.debug { "runWebsocketWatcher: Connection Error with $connUri" }
                        cancel = connectionListener.onConnectionError(this, ConnectionError.Unknown, ex)
                    }
                }
                if (cancel) {
                    // Deactivate loop if told to cancel by onConnectionError
                    LOGGER.debug { "runWebsocketWatcher: onConnectionError returned true - cancelling connection" }
                    connectionActive = false
                }
            } finally {
                // If connection not told to close or closing gracefully
                when (closeReason?.knownReason) {
                    null -> {
                        LOGGER.debug { "runWebsocketWatcher: Closed Abnormally" }
                    }
                    CloseReason.Codes.NORMAL, CloseReason.Codes.GOING_AWAY -> {
                        //Normal Close
                        LOGGER.debug { "runWebsocketWatcher: startWebsocket was closed normally" }
                        connectionActive = false
                    }
                    CloseReason.Codes.byCode(1006) -> {
                        // Closed Abnormally - Happens when Veadotube Mini Closes - we don't seem to get a close frame, or KTOR Hides it and give us this
                        if (compatibilityFlagMiniPre2dot1) {
                            LOGGER.debug { "runWebsocketWatcher: Closed Abnormally > Connection was closed without close frame - veadotube mini probably closed, but may have crashed" }
                        } else {
                            LOGGER.error { "runWebsocketWatcher: Closed Abnormally > Connection was closed without close frame - veadotube may have crashed" }
                        }
                        // Wait one Instance Manager Loop - If the Instance Closed/Crashed This connection should be cleaned up in around this time
                        delay(READ_LOOP_DELAY_MAX_MS - WS_CONN_ERROR_WAIT_MS)
                        // If connectionActive is still true
                        if (!connectionActive)
                            connectionListener.onConnectionError(this, ConnectionError.MiniV2DotOneConnectionError)
                    }

                    else -> {
                        LOGGER.debug { "runWebsocketWatcher: Closed Abnormally > $closeReason" }
                    }
                }
            }

            /* End Websocket Block */


        } catch (ex: CancellationException) {
            // CancellationException - Upstream Job is being closed, we should quit (Mainly to catch a Cancelled Delay)
            LOGGER.trace { "runWebsocketWatcher: startWebsocket was cancelled" }
        } finally {
            //Cleanup this Connection
            cleanupConnection()
        }

        LOGGER.trace { "runWebsocketWatcher: Ended" }
    }

    private suspend fun startWebsocketSession() {
        LOGGER.trace { "startWebsocketSession: Connecting: $connUri (${connUri.host}, ${connUri.port}, ${connUri.rawPath}?${connUri.rawQuery})" }
        check(httpClient?.isActive == true) { "HttpClient is not active" }
        check(webSocketSession?.isActive != true) { "webSocketSession is already active and in use" }

        // Clear any Previous Session and Close Reason
        webSocketSession = null
        webSocketCloseReason = null
        webSocketReceiverResult = null

        try {
            httpClient?.webSocket(
                host = connUri.host,
                port = connUri.port,
                path = "${connUri.rawPath}?${connUri.rawQuery}"
            ) {
                /*Setup */
                LOGGER.trace { "webSocket Session Block: Connected" }

                // Export WS Session for Send
                webSocketSession = this

                //Export WS CloseReason
                webSocketCloseReason = this.closeReason

                //Run Receiver
                LOGGER.trace { "webSocket Session Block: Receiver Started" }
                try {
                    runWebsocketReceiver(this.incoming)
                    LOGGER.trace { "webSocket Session Block: Disconnected" }
                } catch (ex: Throwable) {
                    webSocketReceiverResult = ex
                    LOGGER.trace { "webSocket Session Block: Disconnected - Receiver Closed with Error" }

                    if (ex !is Exception) {
                        // Major Error - Throwable, not Exception
                        LOGGER.warn { "webSocket Session Block -> WebsocketReceiver Throwable: ${ex.message} - ${ex.cause}\n${ex.stackTraceToString()}" }
                        throw Exception("Major Error: Throwable ${ex.javaClass.simpleName}", ex)
                    } else {
                        //Regular Exception
                        LOGGER.debug { "webSocket Session Block -> WebsocketReceiver Exception: ${ex.message} - ${ex.cause}\n${ex.stackTraceToString()}" }
                        throw ex
                    }
                }
            } ?: {
                val ex = IllegalStateException("HttpClient is not active")
                webSocketReceiverResult = ex
                throw ex
            }

        } finally {
            //Clear WS Session
            webSocketSession = null
            LOGGER.trace { "startWebsocketSession: Disconnected: $connUri" }
        }
    }

    /**
     * Receives Incoming Frames from Websocket [ReceiveChannel] as a flow, processes them,
     * and sends them to the Registered Listener
     *
     *  Converts message to [ResultMessage] before calling Listener
     */
    private suspend fun runWebsocketReceiver(incomingFrames: ReceiveChannel<Frame>) {
        LOGGER.trace { "runWebsocketReceiver: Begin" }

        /* Start Get and Process Message Block*/
        val connection: Connection = this

        incomingFrames.receiveAsFlow().buffer(10).cancellable()
            .onStart {
                LOGGER.trace { "WebsocketReceiverFlow: Started Receiving Frames from $connUri" }
                isConnected = true
                connectionListener.onConnectionChange(connection, true)
            }
            .onCompletion {
                LOGGER.trace { "WebsocketReceiverFlow: Stopped Receiving Frames from $connUri" }
                isConnected = false
                connectionListener.onConnectionChange(connection, false)
            }
            .transform { frame ->
                LOGGER.trace { "WebsocketReceiverFlow: Received Frame - ${frame.hashCode()}" }
                // Get Frame and emit message content If it should be processed
                when (frame) {
                    is Frame.Text -> {
                        /* Should only receive Text/JSON from Veadotube, but processing is done using the ByteArray
                        from the frame instead of using readBytes in case we process it before converting */
                        val messageBytes = frame.data
                        LOGGER.trace { "WebsocketReceiverFlow: ${frame.frameType} Frame with ${frame.data.size} Bytes" }
                        emit(messageBytes)
                    }
                    else -> {
                        // Should never happen without Raw Socket
                        LOGGER.debug { "WebsocketReceiverFlow: Received unexpected Frame - ${frame.frameType} Frame with ${frame.data.size} Bytes" }
                    }
                }
            }.buffer(5) // Buffer up to X Messages to Process
            .transform { messageBytes ->
                LOGGER.trace { "WebsocketReceiverFlow: Parse ${messageBytes.contentHashCode()} to ApiMessage" }
                // Process message, emit if successful
                val processedMessage = processReceivedMessage(messageBytes)
                if (processedMessage != null) {
                    LOGGER.trace { "WebsocketReceiverFlow: Processed ${messageBytes.contentHashCode()} -> ApiMessage ${processedMessage.hashCode()}" }
                    emit(processedMessage)
                } else {
                    LOGGER.debug { "WebsocketReceiverFlow: Processed ${messageBytes.contentHashCode()} -> Received Null - Error likely" }
                }
            }.buffer(5) // Buffer up to X Messages to Pass
            //.flowOn(websocketContext)
            .onEach { message ->
                LOGGER.trace { "WebsocketReceiverFlow: Pass ApiMessage ${message.hashCode()} to Listener" }
                // Pass along to Listeners, etc
                passReceivedToListener(message)
            }
            .collect()
    }

    /**
     * Processes Received JSON Message, separating the channel prefix and deserializing to a [ResultMessage]
     *
     * The [ResultMessage] contains a [ResultMessage.channel] with the detected channel name
     *
     * Returns null if there's an error/unsupported message
     */
    private fun processReceivedMessage(message: ByteArray): ResultMessage? {
        LOGGER.debug { "processReceivedMessage ${message.contentHashCode()}: ByteArray to Process: ${message.size} Bytes" }

        /* Basic Decode Block Start */
        // Gets Index of first colon (':') - text before this should represent the Veadotube Channel
        val channelCharEnd = message.indexOf(COLON_BYTE)

        // Checks Value of first Colon is eq or less than 0 and is before the first Curly Brace - if not, we don't have a valid channel value
        // Open Brace is in the 1st UTF-8 Block (only 1 Byte) to we can check it as a byte without decoding
        if (channelCharEnd <= 0 && channelCharEnd < message.indexOf(BRACE_OPEN_BYTE)) {
            LOGGER.debug { "processReceivedMessage${message.contentHashCode()}: Received Message Missing '<channel>:'" }
            return null
        } // not found, invalid message


        // If message starts with 'nodes:' (or any other channel prefix) we need to get it
        // Only 'nodes' exists as a channel in veadotube mini v2, but this could change for other versions
        val channel = try {
            String(message, 0, channelCharEnd)
        } catch (ex: Exception) {
            LOGGER.debug { "processReceivedMessage ${message.contentHashCode()}: Error extracting Channel: ${ex.message}" }
            return null
        }
        if (channel.isBlank()) {
            LOGGER.debug { "processReceivedMessage ${message.contentHashCode()}: Received Message with blank Channel name" }
            return null
        } // not found, invalid message

        LOGGER.trace { "processReceivedMessage ${message.contentHashCode()}: Channel '$channel'" }

        // This is a workaround for pre version 2.1 which sometimes sends null bytes after the JSON - skip if Version 2
        val nullCharIndex =
            if (compatibilityFlagMiniPre2dot1) message.indexOf(NULL_BYTE)
            else -1

        val textTrimIndex =
            if (nullCharIndex > 0) {
                LOGGER.trace { "processReceivedMessage ${message.contentHashCode()}: Culling Nulls after $nullCharIndex" }
                nullCharIndex
            } else {
                LOGGER.trace { "processReceivedMessage ${message.contentHashCode()}: No Nulls to Cull" }
                message.size
            }

        // Extract JSON
        val textJsonExtracted = try {
            String(message, channelCharEnd + 1, textTrimIndex - (channelCharEnd + 1))
        } catch (ex: Exception) {
            LOGGER.debug { "processReceivedMessage ${message.contentHashCode()}: Error extracting JSON: ${ex.message}" }
            return null
        }


        LOGGER.trace { "processReceivedMessage ${message.contentHashCode()}: Final Processed Message:\nChannel: $channel\nJSON: $textJsonExtracted" }
        /* Basic Decode Block End */

        // Decode to Object
        val messageObj: ResultMessage = try {
            convertMessage(textJsonExtracted)
        } catch (ex: Exception) {
            LOGGER.debug { "processReceivedMessage ${message.contentHashCode()}: Error Decoding JSON: ${ex.message}" }
            return null
        }
        LOGGER.trace { "processReceivedMessage ${message.contentHashCode()}: Decoded Message:\nVtResultMessage - ${messageObj.javaClass}\n$messageObj" }

        // Add channel to messageObj for use in Flow
        messageObj.channel = channel

        // Return Decoded Message
        return messageObj
    }


    /**
     * Converts input to [ResultMessage]
     *
     * Also outputs object info to Logger if Logging is set to Trace
     * It Covers all types, so it's a good example of all possible API Values
     */
    private fun convertMessage(textCleaned: String): ResultMessage {
        // Decode and Convert JSON to Object
        LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: Attempting to decode JSON String to object:\n$textCleaned" }

        val convertedMessage: ResultMessage =
            try {
                jsonDeserializer.decodeFromString(textCleaned)
            } catch (thrown: Throwable) {
                val warningString = StringBuilder()

                if (thrown !is Exception) {
                    // Major Error - Throwable instead of Exception, could be something like NoClassDefFoundError or ExceptionInInitializerError
                    warningString.append("Major Error deserializing JSON String to ApiMessage")
                } else {
                    // Regular Exception
                    warningString.append("Failed to deserialize JSON String to ApiMessage")
                }

                LOGGER.debug { "convertMessage ${textCleaned.hashCode()}: Unable to decode JSON String to ApiMessage: ${thrown.message}\n$textCleaned" }

                try {
                    //Try to convert to generic JSON Element - this isn't passed, but will let us know if it's valid JSON
                    val jsonMessage = Json.parseToJsonElement(textCleaned)
                    warningString.append("; Successfully decoded to Generic JSON Element")
                    LOGGER.warn { "Warning - Failed to Deserialize JSON to Object: $jsonMessage" }

                } catch (exInner: Exception) {
                    warningString.append("; Failed decode to Generic JSON Element")
                    LOGGER.warn { "Warning - Failed to Deserialize JSON. Error: '${exInner.message}' > JSON: '$textCleaned'" }
                    //Add as Suppressed Exception
                    thrown.addSuppressed(exInner)
                }

                val exception = Exception(warningString.toString(), thrown)
                throw exception
            }

        LOGGER.trace { // If Trace is Enabled, process block to output info
            convertedMessage.printTraceResultMessage {
                StringBuilder().appendLine("convertMessage ${textCleaned.hashCode()}: ")
            }.toString().trimEnd('\n')
        }

        LOGGER.trace { "convertMessage ${textCleaned.hashCode()}: Decoded JSON String to:\n$convertedMessage" }

        return convertedMessage
    }

    private inline fun ResultMessage.printTraceResultMessage(initialMessageSB: () -> StringBuilder = { StringBuilder() }): StringBuilder {
        val message = this
        val messageStringBuilder = initialMessageSB()
        messageStringBuilder.appendLine("-> Event: " + message.event)
        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        when (message) {
            is ResultMessage.ResultMessageWithNodeEntryList -> {
                messageStringBuilder.appendLine("-> Class: ResultMessageWithNodeEntryList;")
                messageStringBuilder.appendLine("-> Entries: ${message.entries};")
                message.entries.forEach { entry ->
                    messageStringBuilder.appendLine("-> Entries -> Entry: $entry;")
                }
            }
            is ResultMessage.ResultMessageWithPayload -> {
                messageStringBuilder.appendLine("-> Class: ResultMessageWithPayload;")
                messageStringBuilder.appendLine("-> ID:   ${message.id};")
                messageStringBuilder.appendLine("-> Type: ${message.type};")
                messageStringBuilder.appendLine("-> Name: ${message.name};")

                if (message.payload is ResultPayload.ResultPayloadStateList) {
                    messageStringBuilder.appendLine("-> Payload -> Event: ${message.payload.event};")
                    message.payload.states.forEach { state ->
                        messageStringBuilder.appendLine("-> Payload -> States -> State: $state;")
                    }
                } else if (message.payload is ResultPayload.ResultPayloadState) {
                    messageStringBuilder.appendLine("-> Payload -> Event: ${message.payload.event};")
                    messageStringBuilder.appendLine("-> Payload -> State: ${message.payload.state};")
                }
            }
            is ResultMessage.ResultMessageWithPayloadBoolean -> {
                messageStringBuilder.appendLine("-> Class: ResultMessageWithPayloadBoolean;")
                messageStringBuilder.appendLine("-> ID:    ${message.id};")
                messageStringBuilder.appendLine("-> Type:  ${message.type};")
                messageStringBuilder.appendLine("-> Name:  ${message.name};")
                messageStringBuilder.appendLine("-> Payload: ${message.payload};")
            }
            is ResultMessage.ResultMessageWithPayloadNumber -> {
                messageStringBuilder.appendLine("-> Class: ResultMessageWithPayloadNumber;")
                messageStringBuilder.appendLine("-> ID:    ${message.id};")
                messageStringBuilder.appendLine("-> Type:  ${message.type};")
                messageStringBuilder.appendLine("-> Name:  ${message.name};")

                messageStringBuilder.appendLine("-> Payload -> Value: ${message.payload.value};")
                if (message.payload.isMaxSet)
                    messageStringBuilder.appendLine("-> Payload -> Value: ${message.payload.max};")
                if (message.payload.isMinSet)
                    messageStringBuilder.appendLine("-> Payload -> Value: ${message.payload.min};")
            }
            is ResultMessage.ResultMessageWithInstanceInfo -> {
                messageStringBuilder.appendLine("-> Class: ResultMessageWithInstanceInfo;")
                messageStringBuilder.appendLine("-> ID:      ${message.id};")
                messageStringBuilder.appendLine("-> Name:    ${message.name};")
                messageStringBuilder.appendLine("-> Server:  ${message.server};")
                messageStringBuilder.appendLine("-> Version: ${message.version};")
            }
            else -> {
                messageStringBuilder.appendLine("-> Unknown Message Class: ${message.javaClass.name};")
                messageStringBuilder.appendLine("-> Contents: $message")
            }
        }
        return messageStringBuilder
    }

    private fun passReceivedToListener(message: ResultMessage) {
        try {
            connectionListener.onConnectionReceive(this, message)
        } catch (ex: Exception) {
            LOGGER.debug { "passReceivedToListener: Exception passing Message to ConnectionListener - ${ex.message}" }
        }
    }

    private fun cleanupConnection() {
        LOGGER.trace { "cleanupConnection: activeLoop false" }
        connectionActive = false

        LOGGER.trace { "cleanupConnection: stopWebsocket()" }
        stopWebsocket()
    }

    @Synchronized
    override fun close() {
        if (!isClosed) {
            isClosed = true

            LOGGER.trace { "Connection Closing" }

            cleanupConnection()

            LOGGER.trace { "Connection Close: client Close Check" }
            shutdownHttpClient()

            connectionJob.cancel()

            LOGGER.debug { "Connection Closed" }
        }
    }

    /**
     * Send a message to the Veadotube Instance on this Connection
     *
     * @param channel *Optional* Veadotube Channel this should be sent to - defaults to "nodes"
     * @param requestData [RequestMessage] to send
     * @param validateRequest *Optional* whether the [RequestMessage] should be validated (using [RequestMessage.validate])
     */
    @Suppress("unused")
    @Throws(IllegalStateException::class)
    fun send(channel: String = "nodes", requestData: RequestMessage, validateRequest: Boolean = false) {
        check(!isClosed) { "Connection is Closed" }
        check(connectionActive) { "Connection Websocket not active" }
        check(isConnected) { "Connection Websocket not connected" }
        require(channel.isNotBlank()) { "Channel cannot be blank" }
        check(webSocketSession?.isActive ?: false) { "Connection Websocket Session is not active" }
        if (validateRequest) requestData.validate() // Throws error if not true

        runBlocking {
            //Convert to String with Channel Prefix and Send
            val dataAsString = "$channel:${Json.encodeToString(RequestMessage.serializer(), requestData)}"
            LOGGER.trace { "Sending message: '$dataAsString'" }
            webSocketSession?.send(Frame.Text(dataAsString))
        }
    }

    /**
     * Send a message to the Veadotube Instance on this Connection
     *
     * @param channel *Optional* Veadotube Channel this should be sent to - defaults to "nodes"
     * @param requestData JSON [String] to send
     */
    @Suppress("unused")
    @Throws(IllegalStateException::class)
    fun send(channel: String = "nodes", requestData: String) {
        check(!isClosed) { "Connection is Closed" }
        check(connectionActive) { "Connection Websocket not active" }
        check(isConnected) { "Connection Websocket not connected" }
        require(channel.isNotBlank()) { "Channel cannot be blank" }
        check(webSocketSession?.isActive ?: false) { "Connection Websocket Session is not active" }

        runBlocking {
            //Convert to String with Channel Prefix and Send
            val dataAsString = "${channel}:${requestData}"
            LOGGER.trace { "Sending message: '$dataAsString'" }
            webSocketSession?.send(Frame.Text(dataAsString))
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

/**
 * Marks Test Constructors/Functions that shouldn't be used elsewhere
 */
@RequiresOptIn("This is `internal` only for unit tests")
internal annotation class VisibleForUnitTests
