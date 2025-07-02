package io.github.dissonantau.bleatkan.instance


import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import io.github.dissonantau.bleatkan.MiscFunctions.getUnixTime
import io.github.dissonantau.bleatkan.message.VeadoInstanceFile
import java.io.FileReader
import java.io.IOException
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.time.Instant
import kotlin.io.path.name


/**
 * Checks Veadotube Instances Folder, Tracks Instances, and generates events for new/changed/closed Instances
 *
 *
 * Originally based on Instances.cs in [veadotube bleatcan (Commit b1d4f...)](https://gitlab.com/veadotube/bleatcan/-/tree/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan)
 */
@Suppress("unused")
class InstancesManager(
    listener: InstancesListener,
    managerJobParent: Job? = null
) : AutoCloseable {

    companion object {
        private val LOGGER = KotlinLogging.logger {}

        /** Timestamp Timeout in Seconds
         *
         * An Instance File with an internal timestamp older than 10s is considered out of date/dead */
        const val READ_TIMEOUT_SEC: Long = 10

        /** Max/Targeted time to sleep between loops (milliseconds)
         *
         * Actual Delay will be the ***largest*** of [READ_LOOP_DELAY_MAX_MS] minus `loop runtime`, and [READ_LOOP_DELAY_MIN_MS] */
        const val READ_LOOP_DELAY_MAX_MS: Long = 3 * 1000

        /** Minimum time to sleep between loops (milliseconds)*/
        const val READ_LOOP_DELAY_MIN_MS: Long = 100

        /**
         * JSON Deserializer
         */
        private val jsonDeserializer = Json {
            // Allows JSON with extra values to be processed instead of failing with an exception
            ignoreUnknownKeys = true
            useAlternativeNames = false
        }

        /* static vals to be initialised */
        /** Directory - Veadotube Instances (at <user profile/home>\.veadotube\instances\) */
        private val dirInstances: Path

        init {

            val dirHomeFolder: String = System.getProperty("user.home")
            LOGGER.trace { "Home Folder = '$dirHomeFolder'" }

            val dirVeadotubeInstances = Paths.get(dirHomeFolder, ".veadotube", "instances")
            LOGGER.info { "Veadotube Instances Folder = '$dirVeadotubeInstances'" }

            // If it doesn't exist, create
            if (!dirVeadotubeInstances.toFile().isDirectory) {
                Files.createDirectories(dirVeadotubeInstances)
            }

            dirInstances = dirVeadotubeInstances
        }

    }

    constructor(
        listener: InstancesListener
    ) : this(listener = listener, managerJobParent = null)

    /**
     * Coroutine Job - contains all Jobs for InstancesManager
     */
    private val instMgrJob: CompletableJob =
        if (managerJobParent != null)
            SupervisorJob(managerJobParent)
        else
            SupervisorJob()

    /**
     * Coroutine Dispatcher for Checker
     */
    private val instanceCheckerDispatcher: CoroutineDispatcher =
        Dispatchers.Default.limitedParallelism(1, "instancesManager-dsp")

    /**
     * Coroutine Dispatcher for Reader
     */
    private val instanceReaderDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1, "instanceReaderDispatcher")

    /**
     * Scope for this Connection, should be used to launch Jobs
     */
    private val instMgrScope: CoroutineScope =
        CoroutineScope(instMgrJob + CoroutineName("instancesManager-cr"))


    /** Filename, Instance ID Object */
    private val instancesIDMap = HashMap<String, InstanceID>()

    /** Instance ID Object, Instance Object */
    private val instancesMap = HashMap<InstanceID, Instance>()

    /** Mutex for Instances Map */
    private val instancesMapMutex = Mutex()

    /** Event Listener that should receive Start/Change/End events */
    private val instanceEventListener: InstancesListener = listener


    /** Watcher is set to active when loop is enabled, and loop will run while it's true */
    private var watcherActive = false

    /** Job object for instances directory watcher */
    private var watcherJob: Job? = null

    /** Job for instance checker */
    private var checkerJob: Job? = null


    init {
        LOGGER.trace { "Instance Manager Starting" }
        watcherActive = true

        instMgrScope.launch {
            LOGGER.trace { "Launching DirectoryWatcher Job" }
            watcherJob = launch { runDirectoryWatcherLoop() }
            LOGGER.trace { "Launching InstanceChecker Job" }
            checkerJob = launch { runInstanceCheckerLoop() }
        }.invokeOnCompletion { close() }

        LOGGER.trace { "Instance Manager Started" }
    }


    /**
     * Process an Instance file and add to instancesMap
     * @param eventPath Fully Resolved Path of an Instance File
     */
    private suspend fun processInstanceFileCreateModify(eventPath: Path) {
        val eventFilename = eventPath.name
        LOGGER.trace { "processInstanceFile: File Name > $eventPath" }

        val contents = try {
            //Get Contents of file - not bothering with a buffered reader since it's a small file, and we're loading the whole thing
            withContext(instanceReaderDispatcher) {
                FileReader(eventPath.toFile()).use { it.readText() }
            }
        } catch (ex: IOException) {
            LOGGER.debug { "processInstanceFile: $ex" }
            return
        }

        LOGGER.trace { "processInstanceFile: Done reading $eventFilename - ${contents.length} Chars" }

        // If contents are smaller than minimum-viable JSON size, skip decoding
        if (contents.length <= 5) return

        try {
            // Decode JSON to Serializable Object
            val veadoInstanceFile = jsonDeserializer.decodeFromString<VeadoInstanceFile>(contents)

            //Check - make sure values are filled before proceeding
            check(veadoInstanceFile.updatedTimestamp > 0) { "vtInstance missing timestamp" }
            check(veadoInstanceFile.updatedTimestamp >= getUnixTime() - READ_TIMEOUT_SEC) { "vtInstance read timeout expired" }

            check(veadoInstanceFile.title.isNotBlank()) { "vtInstance missing name" }

            LOGGER.trace { "processInstanceFile: $eventFilename Json - $veadoInstanceFile" }

            // Get Instance ID Object from Map, or Create if new
            val instanceID = instancesIDMap.getOrPut(eventFilename) { InstanceID(eventFilename) }

            LOGGER.trace { "processInstanceFile: instancesMapMutex - Lock Waiting" }

            instancesMapMutex.withLock {
                /* Sync Block Start */
                LOGGER.trace { "processInstanceFile: instancesMapMutex - Lock Acquired" }

                // Use this instead of [veadoInstanceFile].[server] We need to handle a bug in mini 2.0a where server can be ":0"
                // when Websocket server is set from on to off. If length is less than 3, it's definitely invalid
                val veadoInstanceFileServer =
                    if (veadoInstanceFile.server.length < 3) ""
                    else veadoInstanceFile.server

                // Check if last Char in Title is a star (*), remove it
                val veadoInstanceFileTitleLastCharId = veadoInstanceFile.title.length - 1
                val veadoInstanceFileTitle = if (veadoInstanceFile.title[veadoInstanceFileTitleLastCharId] == '*')
                    veadoInstanceFile.title.substring(0, veadoInstanceFileTitleLastCharId)
                else veadoInstanceFile.title

                // Check if name is in map - if missing, create new Instance, add Instance ID and add to Map, set newInstance to True
                var instanceInMapIsNew = false
                val instanceInMap: Instance =
                    instancesMap.getOrPut(instanceID) {
                        Instance(
                            id = instanceID, title = veadoInstanceFileTitle, server = veadoInstanceFileServer,
                            version = veadoInstanceFile.version, language = veadoInstanceFile.language
                        ).also {
                            instanceInMapIsNew = true
                        }
                    }

                instanceInMap.fileLastModified = veadoInstanceFile.updatedTimestamp

                //Check/Update values
                if (!instanceInMapIsNew) {
                    // Existing instance - compare and update
                    LOGGER.trace { "processInstanceFile: $eventFilename existing instance - $instanceInMap" }

                    when {
                        instanceInMap.server != veadoInstanceFileServer -> {
                            // This change would kill an existing connection and should involve a new Instance being created
                            LOGGER.debug { "processInstanceFile: server change ${instanceInMap.server} -> $veadoInstanceFileServer " }

                            val instanceReplacement =
                                Instance(
                                    id = instanceID,
                                    name = veadoInstanceFileTitle,
                                    server = veadoInstanceFileServer,
                                    version = veadoInstanceFile.version,
                                    lastModified = veadoInstanceFile.updatedTimestamp
                                )

                            var instanceChanged = false

                            if (veadoInstanceFileServer.isEmpty() && instanceInMap.server.isNotEmpty()) {
                                // Server was Enabled, now Disabled
                                try {
                                    instanceEventListener.onInstanceServerStop(instanceInMap)
                                    instanceChanged = true
                                } catch (ex: Exception) {
                                    LOGGER.debug { "processInstanceFile: Failed to call onInstanceServerStop for ${instanceInMap}/${instanceReplacement}: ${ex.message}; ${ex.stackTraceToString()}" }
                                }
                            } else if (veadoInstanceFileServer.isNotEmpty() && instanceInMap.server.isEmpty()) {
                                // Server was Disabled, now Enabled
                                try {
                                    instanceEventListener.onInstanceServerStart(instanceReplacement)
                                    instanceChanged = true
                                } catch (ex: Exception) {
                                    LOGGER.debug { "processInstanceFile: Failed to call onInstanceServerStart for ${instanceInMap}/${instanceReplacement}: ${ex.message}; ${ex.stackTraceToString()}" }
                                }
                            } else if (veadoInstanceFileServer.isNotEmpty() && instanceInMap.server.isNotEmpty()) {
                                // Server was active, still active
                                try {
                                    instanceEventListener.onInstanceChangeMajor(instanceReplacement, instanceInMap)
                                    instanceChanged = true
                                } catch (ex: Exception) {
                                    LOGGER.debug { "processInstanceFile: Failed to call onInstanceChangeMajor for ${instanceInMap}/${instanceReplacement}: ${ex.message}; ${ex.stackTraceToString()}" }
                                }
                            }

                            if (instanceChanged) {
                                instancesMap[instanceID] = instanceReplacement
                                LOGGER.debug { "processInstanceFile: Existing instance replaced - $instanceInMap > $instanceReplacement" }
                            }
                        }

                        instanceInMap.title != veadoInstanceFileTitle -> {
                            // Name is semi-important, mostly for matching title. Change should not kill an existing connection
                            LOGGER.debug { "processInstanceFile: name change ${instanceInMap.title} -> $veadoInstanceFileTitle" }

                            // Update title in existing Instance - we're not sending a new Instance to Listeners
                            val oldName = instanceInMap.title
                            instanceInMap.title = veadoInstanceFileTitle
                            try {
                                instanceEventListener.onInstanceChangeMinor(
                                    instance = instanceInMap,
                                    change = InstanceChange.TITLE,
                                    oldValue = oldName
                                )
                            } catch (ex: Exception) {
                                LOGGER.debug { "processInstanceFile: Failed to call onInstanceChangeMinor for ${instanceInMap}: ${ex.message}; ${ex.stackTraceToString()}" }
                            }
                        }
                    }

                } else {
                    // New instance - set up
                    LOGGER.debug { "processInstanceFile: New instance added - $instanceInMap" }
                    try {
                        instanceEventListener.onInstanceOpen(instanceInMap)
                    } catch (ex: Exception) {
                        LOGGER.debug { "processInstanceFile: Failed to call onInstanceOpen for ${instanceInMap}: ${ex.message}; ${ex.stackTraceToString()}" }
                    }

                    if (veadoInstanceFileServer.isNotEmpty()) {
                        // Server is Enabled
                        LOGGER.debug { "processInstanceFile: New instance $instanceInMap has an active file server" }
                        try {
                            instanceEventListener.onInstanceServerStart(instanceInMap)
                        } catch (ex: Exception) {
                            LOGGER.debug { "processInstanceFile: Failed to call onInstanceServerStart for ${instanceInMap}: ${ex.message}; ${ex.stackTraceToString()}" }
                        }
                    }
                }

            }

        } catch (ex: SerializationException) {
            /* Sometimes happens when the file happens to be read when it's still being written */
            LOGGER.debug { "processInstanceFile: $eventFilename content - $contents - ${ex.stackTraceToString()}" }
        } catch (ex: IllegalArgumentException) {
            // Not valid instance of VeadoInstanceFile - could be a newer/non-mini version of Veadotube
            LOGGER.debug { "processInstanceFile: $eventFilename content - $contents - ${ex.stackTraceToString()}" }
        } catch (ex: IllegalStateException) {
            // Missing vtInstance value, etc.
            LOGGER.debug { "processInstanceFile: $eventFilename content - $contents - ${ex.stackTraceToString()}" }
        } catch (ex: Exception) {
            // Other Exception
            LOGGER.debug { "processInstanceFile: $eventFilename content - $contents - ${ex.stackTraceToString()}" }
        }
    }


    private suspend fun runDirectoryWatcherLoop() = withContext(instanceReaderDispatcher) {
        LOGGER.trace { "DirectoryWatcher: Loop Start" }

        //Instance File Watcher Service
        val instDirWatchService: WatchService = FileSystems.getDefault().newWatchService()

        //Start Watching Directory
        val instDirPathKey: WatchKey =
            dirInstances.register(instDirWatchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)

        try {
            LOGGER.trace { "DirectoryWatcher: Watcher Loop Start" }
            while (watcherActive && isActive) {
                val loopStartTime = Instant.now().epochSecond
                val instDirLoopKey: WatchKey = instDirWatchService.take()
                var filesFound = false

                //Poll for changes in instances folder - does not block if not files found
                instDirLoopKey.pollEvents().asFlow().onStart {
                    LOGGER.trace { "DirectoryWatcher: Poll File Events Start" }
                }.onCompletion { LOGGER.trace { "DirectoryWatcher: Polling File Events" } }
                    .filterNot { event -> event.kind() === OVERFLOW }
                    .transform { event ->
                        // Resolve the filename from context of the event.
                        val eventPath: Path = dirInstances.resolve(event.context() as Path)

                        if (event.kind() === ENTRY_DELETE) {
                            // We won't do anything, there's a timeout for instances
                            LOGGER.trace { "DirectoryWatcher: File Deleted: ${eventPath.name}" }
                        } else {
                            // For 'Create' or 'Modify' Event - Launches coroutine to get and process for each file
                            LOGGER.trace { "DirectoryWatcher: File Created or Modified: ${eventPath.name}" }
                            filesFound = true
                            //Emit Path for processing
                            emit(eventPath)
                        }
                    }
                    .collect { path ->
                        //Process File
                        processInstanceFileCreateModify(path)
                    }

                // Reset key for next loop, if it fails loop ends
                check(instDirLoopKey.reset()) { "Folder Watch Key no longer Valid" }

                //Calculate loop time and delay before next loop
                val loopTimeSeconds = Instant.now().epochSecond - loopStartTime
                var delayTimeMSec =
                    (READ_LOOP_DELAY_MAX_MS - (loopTimeSeconds * 1000))
                        .coerceIn(
                            minimumValue = READ_LOOP_DELAY_MIN_MS,
                            maximumValue = READ_LOOP_DELAY_MAX_MS
                        )

                // If no files processed this loop, double wait time
                if (!filesFound) delayTimeMSec *= 2

                LOGGER.trace { "DirectoryWatcher: Loop took $loopTimeSeconds Seconds, Delaying ${delayTimeMSec / 1000f} Seconds before next check" }
                delay(delayTimeMSec)
            }
            LOGGER.trace { "DirectoryWatcher: Watcher Loop Ended" }
        } catch (ex: ClosedWatchServiceException) {
            LOGGER.trace { "DirectoryWatcher: WatchService Closed with ${ex.message}" }
        } catch (ex: Exception) {
            LOGGER.debug { "DirectoryWatcher: Exception in DirectoryWatcher: ${ex.message}" }
        } finally {
            LOGGER.trace { "DirectoryWatcher: Finally Cleanup" }
            watcherActive = false

            //Cleanup
            instDirPathKey.cancel()
            instDirWatchService.close()
        }

        LOGGER.trace { "DirectoryWatcher: Loop Closed" }
    }


    private suspend fun runInstanceCheckerLoop() = withContext(instanceCheckerDispatcher) {
        LOGGER.trace { "InstanceChecker: Start" }

        try {
            //Delay before loop
            delay(READ_LOOP_DELAY_MAX_MS)

            while (watcherActive && isActive) {
                val loopStartTime = Instant.now().epochSecond


                LOGGER.trace { "InstanceChecker: instancesMapMutex - Lock Waiting" }

                instancesMapMutex.withLock {
                    /* Sync Block Start */
                    LOGGER.trace { "InstanceChecker: instancesMapMutex - Lock Acquired" }

                    if (instancesMap.isNotEmpty()) {
                        // Find Instances to Remove and process
                        val instMapIterator = instancesMap.iterator()
                        for (instanceEntry in instMapIterator) {
                            if (instanceEntry.value.fileLastModified < getUnixTime() - READ_TIMEOUT_SEC) {
                                // Instance has aged out without file timestamp refresh, trigger onInstanceClose and remove from map
                                try {
                                    instanceEventListener.onInstanceClose(instanceEntry.value.id)
                                } catch (ex: Exception) {
                                    LOGGER.debug { "runInstanceCheckerLoop: Failed to call onInstanceClose for ${instanceEntry}: ${ex.message}; ${ex.stackTraceToString()}" }
                                }
                                instMapIterator.remove()
                            }
                        }

                    }

                    /* Sync Block End */
                }

                val loopTimeSeconds = Instant.now().epochSecond - loopStartTime
                val delayTimeMSec =
                    if (instancesMap.isNotEmpty()) READ_LOOP_DELAY_MAX_MS else READ_LOOP_DELAY_MAX_MS * 2 // If no instances are in the map, double wait time

                LOGGER.trace { "InstanceChecker: Loop took $loopTimeSeconds Seconds, Delaying ${delayTimeMSec / 1000f} Seconds before next check" }
                delay(delayTimeMSec)
            }

        } finally {
            watcherActive = false

            //Cleanup
            instancesMapMutex.withLock {
                for (instance in instancesMap.values) {
                    instanceEventListener.onInstanceClose(instance.id)
                }

                instancesMap.clear()
            }
            LOGGER.trace { "InstanceChecker: Cleanup Done" }
        }

    }


    /** Function to clean up after instMgrJob */
    private fun cleanup() {
        LOGGER.trace { "Instance Manager Cleanup Start" }
        watcherActive = false

        LOGGER.trace { "Closing DirectoryWatcher Job" }
        runCatching { watcherJob?.cancel("Instances Manager is Closing") }
        watcherJob = null

        LOGGER.trace { "Closing InstanceChecker Job" }
        runCatching { checkerJob?.cancel("Instances Manager is Closing") }
        checkerJob = null

        LOGGER.trace { "Instance Manager Cleanup Done" }
    }

    override fun close() {
        if (instMgrJob.isCancelled) {
            return
        }
        watcherActive = false

        runCatching { instMgrJob.cancel("Instances Manager is Closing") }
    }


    /**
     * Returns [Instance] Object from Map that matches [InstanceID] Object
     */
    fun getInstance(id: InstanceID): Instance? {
        var inst: Instance?
        runBlocking {
            instancesMapMutex.withLock {
                inst = instancesMap[id]
            }
        }

        return inst
    }

    /**
     * Removes [Instance] Object from Map that matches [InstanceID] Object
     *
     * This means next instance check it should be either cleared or restarted
     */
    fun markInstanceFailed(id: InstanceID) {

        runBlocking {
            instancesMapMutex.withLock {
                LOGGER.trace { "markInstanceFailed: Marking $id as failed" }
                instancesMap.remove(id)
                instanceEventListener.onInstanceClose(id)
            }
        }

    }

}