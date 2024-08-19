package xyz.dissonant.veadotube.bleatkan.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import xyz.dissonant.veadotube.bleatkan.MiscFunctions.SHARED.getUnixTime
import xyz.dissonant.veadotube.bleatkan.serializable.VtInstance
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.time.Instant
import kotlin.io.path.isDirectory
import kotlin.io.path.name


/**
 *
 * Based on Instances.cs
 *
 * Based on [veadotube bleatcan (Commit b1d4f...)](https://gitlab.com/veadotube/bleatcan/-/tree/b1d4faf70138c1e839b449c3cf799b6fd59c837b/bleatcan)
 *
 */
class InstancesManager
@JvmOverloads constructor(
    receiver: InstancesReceiver,
    managerJobParent: Job? = null
) : AutoCloseable {

    private val logger = KotlinLogging.logger {}

    companion object {
        /** Timestamp Timeout in Seconds - File with an older than 10s is considered out of date, possibly dead. */
        const val READ_TIMEOUT_SEC: Long = 10

        /** Max/Targeted time to sleep between loops (milliseconds)
         *
         * Actual Delay will be the ***largest*** of [READ_LOOP_DELAY_MAX_MS] minus `loop runtime`, and [READ_LOOP_DELAY_MIN_MS] */
        const val READ_LOOP_DELAY_MAX_MS: Long = 2 * 1000

        /** Minimum time to sleep between loops (milliseconds)*/
        const val READ_LOOP_DELAY_MIN_MS: Long = 100

        /* static vals to be initialised */
        /** Directory - Veadotube Instances (at <user profile/home>\.veadotube\instances\) */
        private val dirInstances: Path

        init {
            val logger = KotlinLogging.logger {}

            val dirHomeFolder: String = System.getProperty("user.home")
            logger.info { "Dir: Home Folder = '$dirHomeFolder'" }

            val dirVeadotubeInstances = Paths.get(dirHomeFolder, ".veadotube", "instances")
            logger.info { "Dir: Veadotube Instances Folder = '$dirVeadotubeInstances'" }

            // If it doesn't exist, create
            if (!dirVeadotubeInstances.toFile().isDirectory) {
                Files.createDirectories(dirVeadotubeInstances)
            }

            dirInstances = dirVeadotubeInstances
        }

    }

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


    /** Filename, Instance Object */
    private val instancesMap = HashMap<InstanceID, Instance>()

    /** Mutex for Instances Map */
    private val instancesMapMutex = Mutex()

    private val instanceEventReceiver: InstancesReceiver = receiver


    /** Watcher is set to active when loop is enabled, and loop will run while it's true */
    private var watcherActive = false

    /** Job object for instances directory watcher */
    private var watcherJob: Job? = null

    /** Job for instance checker */
    private var checkerJob: Job? = null


    init {
        logger.trace { "Instance Manager Starting" }
        watcherActive = true

        instMgrScope.launch {
            logger.trace { "Launching DirectoryWatcher Job" }
            watcherJob = launch { runDirectoryWatcherLoop() }
            logger.trace { "Launching InstanceChecker Job" }
            checkerJob = launch { runInstanceCheckerLoop() }
        }.invokeOnCompletion { close() }

        logger.trace { "Instance Manager Started" }
    }


    /**
     * Process an Instance file and add to instancesMap
     * @param eventPath Fully Resolved Path of an Instance File
     */


    private suspend fun processInstanceFileCreateModify(eventPath: Path) {
        val eventFilename = eventPath.name

        try {
            logger.trace { "processInstanceFile: File Name > $eventFilename > Full Path: $eventPath" }

            val instanceID: InstanceID = InstanceID(eventFilename)

            //Get Contents of file - Suppress Warning, this is called from the Dispatcher.IO Context already
            @Suppress("BlockingMethodInNonBlockingContext")
            val contents = FileInputStream(eventPath.toFile()).bufferedReader()
                .use { it.readText() }.trim()

            logger.trace { "processInstanceFile: Done reading $eventFilename" }


            try {

                if (contents.isNotBlank() && contents.length > 2) {

                    val vtInstance = Json.decodeFromString<VtInstance>(contents)

                    //Check - make sure values are filled before proceeding
                    check(vtInstance.time > 0) { "vtInstance missing timestamp" }
                    check(vtInstance.time >= getUnixTime() - READ_TIMEOUT_SEC) { "vtInstance read timeout expired" }

                    check(vtInstance.name.isNotBlank()) { "vtInstance missing name" }
                    check(vtInstance.server.isNotBlank()) { "vtInstance missing server" }

                    logger.trace { "processInstanceFile: $eventFilename Json - $vtInstance" }

                    logger.trace { "processInstanceFile: Waiting for Sync on instancesMap for $eventFilename" }

                    instancesMapMutex.withLock {
                        /* Sync Block Start */

                        // Check if name is in map - if missing, create new Instance, add Instance ID and add to Map, set newInstance to True
                        var newInstance = false
                        val existingInstance: Instance =
                            instancesMap.getOrPut(instanceID) {
                                Instance(instanceID, vtInstance.name, vtInstance.server).also {
                                    newInstance = true
                                }
                            }

                        existingInstance.fileLastModified = vtInstance.time

                        //Check/Update values
                        if (!newInstance) {
                            //Existing instance - compare and update
                            logger.trace { "processInstanceFile: $eventFilename existing instance - $existingInstance" }
                            if (existingInstance.name != vtInstance.name || existingInstance.server != vtInstance.server) {

                                //Important Value Changed, this should trigger a change event
                                if (existingInstance.name != vtInstance.name) {
                                    logger.trace { "processInstanceFile: name change ${existingInstance.name} -> ${vtInstance.name} " }
                                }

                                if (existingInstance.server != vtInstance.server) {
                                    logger.trace { "processInstanceFile: server change ${existingInstance.server} -> ${vtInstance.server} " }

                                }
                                val newInstanceObj = Instance(instanceID, vtInstance.name, vtInstance.server,vtInstance.time)
                                instancesMap[instanceID] = newInstanceObj

                                logger.debug { "processInstanceFile: Existing instance updated - $existingInstance" }

                                instanceEventReceiver.onChange(newInstanceObj,existingInstance)
                            }
                        } else {
                            logger.trace { "processInstanceFile: $eventFilename new instance - $existingInstance" }
                            logger.debug { "processInstanceFile: New instance added - $existingInstance" }
                            instanceEventReceiver.onStart(existingInstance)
                        }
                    }

                    logger.trace { "processInstanceFile: $eventFilename Json - $vtInstance" }
                }
            } catch (ex: SerializationException) {
                /* Sometimes happens when the file happens to be read when it's still being written */
                logger.debug { "processInstanceFile: $ex" }
                logger.debug { "processInstanceFile: $eventFilename content - $contents" }
            } catch (ex: IllegalArgumentException) {
                // Not valid instance of VtInstance - could be a newer/non-mini version of Veadotube
                logger.warn { "processInstanceFile: $ex" }
                logger.debug { "processInstanceFile: $eventFilename content - $contents" }
            }catch (ex:IllegalStateException ){
                // Missing vtInstance value, etc.
                logger.debug { "processInstanceFile: $ex" }
                logger.debug { "processInstanceFile: $eventFilename content - $contents" }
            }
        } catch (ex: IOException) {
            logger.warn { "processInstanceFile: $ex" }
        }
    }


    //Instance File Watcher Service
    private var instDirWatchService: WatchService = FileSystems.getDefault().newWatchService()

    private suspend fun runDirectoryWatcherLoop() {
        withContext(instanceReaderDispatcher) {
            logger.trace { "DirectoryWatcher: coroutineScope Start" }

            // Initial Directory check
            try {

                for (file in Files.walk(dirInstances, 1)) {
                    if (file != null && !file.isDirectory()) {
                        //Process File
                        processInstanceFileCreateModify(file.toRealPath())
                    }
                }
            } catch (ex: Exception) {
                // Log, but we can move on, as files will refresh regularly
                logger.debug { "DirectoryWatcher: Error with initial File check: ${ex.message}" }
            }


            //Start Watching Directory
            val instDirPathKey: WatchKey =
                dirInstances.register(instDirWatchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)

            try {
                logger.trace { "DirectoryWatcher: Watcher Loop Start" }
                while (watcherActive && isActive) {
                    val loopStartTime = Instant.now().epochSecond
                    val instDirLoopKey: WatchKey = withContext(instanceReaderDispatcher) { instDirWatchService.take() }

                    logger.trace { "DirectoryWatcher: Polling File Events" }
                    //Poll for changes in instances folder - does not block if not files found
                    instDirLoopKey.pollEvents()
                        .filter { event -> event.kind() !== OVERFLOW }
                        .forEach { event ->
                            // Resolve the filename from context of the event.
                            val eventFile = event.context() as Path
                            val eventPath: Path = dirInstances.resolve(eventFile)

                            if (event.kind() === ENTRY_DELETE) {
                                // We won't do anything, there's a timeout for
                                logger.trace { "DirectoryWatcher: File Deleted: ${eventPath.name}" }
                            } else {
                                // For 'Create' or 'Modify' Event - Launches coroutine to get and process for each file
                                logger.trace { "DirectoryWatcher: File Created or Modified: ${eventFile.name}" }

                                //Process File
                                processInstanceFileCreateModify(eventPath)
                            }
                        }

                    // Reset key for next loop, if it fails loop ends
                    check(instDirLoopKey.reset()) { "Folder Watch Key no longer Valid" }

                    //Calculate loop time and delay before next loop
                    val loopEndTime = Instant.now().epochSecond
                    val loopTimeSeconds = loopEndTime - loopStartTime
                    var delayTimeMSec =
                        READ_LOOP_DELAY_MAX_MS - (loopTimeSeconds * 1000) //Start time minus End Time = Seconds Passed
                    when {
                        (delayTimeMSec < READ_LOOP_DELAY_MIN_MS) -> delayTimeMSec = READ_LOOP_DELAY_MIN_MS //min wait
                        (delayTimeMSec > READ_LOOP_DELAY_MAX_MS) -> delayTimeMSec = READ_LOOP_DELAY_MAX_MS //max wait
                    }
                    logger.trace { "DirectoryWatcher: Loop took $loopTimeSeconds Seconds, Delaying ${delayTimeMSec / 1000f} Seconds before next check" }
                    delay(delayTimeMSec)
                }
                logger.trace { "DirectoryWatcher: Watcher Loop Ended" }
            } catch (ex: ClosedWatchServiceException) {
                logger.trace { "DirectoryWatcher: WatchService Closed with ${ex.message}" }
            } finally {
                logger.trace { "DirectoryWatcher: Finally Cleanup" }
                watcherActive = false

                //Cleanup
                instDirPathKey.cancel()
                instDirWatchService.close()
            }
        }
    }

    private suspend fun runInstanceCheckerLoop() {
        withContext(instanceCheckerDispatcher) {
            logger.trace { "InstanceChecker: coroutineScope Start" }

            val instancesToRemove = HashSet<Instance>()

            try {

                while (watcherActive && isActive) {
                    //Delay before loop
                    delay(READ_LOOP_DELAY_MAX_MS)
                    val loopStartTime = Instant.now().epochSecond

                    instancesToRemove.clear()

                    logger.trace { "InstanceChecker: Waiting for Sync on instancesMap" }

                    instancesMapMutex.withLock {
                        /* Sync Block Start */
                        logger.trace { "InstanceChecker: Acquired Sync Lock on instancesMap" }

                        //Get Instances to Remove
                        for (instance in instancesMap.values) {
                            if (instance.fileLastModified < getUnixTime() - READ_TIMEOUT_SEC) {
                                //Instance has aged out without file refresh
                                instancesToRemove.add(instance)
                            }
                        }

                        //Process Instances to Remove
                        for (instance in instancesToRemove) {
                            instancesMap.remove(instance.id)
                            instanceEventReceiver.onEnd(instance.id)
                        }


                        /* Sync Block End */
                    }

                    val loopEndTime = Instant.now().epochSecond
                    val loopTimeSeconds = loopEndTime - loopStartTime
                    logger.trace { "InstanceChecker: Loop took $loopTimeSeconds Seconds, Delaying ${READ_LOOP_DELAY_MAX_MS / 1000f} Seconds before next check" }
                }

            } finally {
                watcherActive = false
                logger.trace { "InstanceChecker: Finally" }
                //Cleanup
                instancesMapMutex.withLock {
                    for (instance in instancesMap.values) {
                        instancesMap.remove(instance.id)
                        instanceEventReceiver.onEnd(instance.id)
                    }
                }
            }

        }
    }


    override fun close() {
        logger.trace { "Instance Manager Closing" }
        watcherActive = false
        instMgrJob.complete()

        runBlocking {
            logger.trace { "Launching DirectoryWatcher Job" }
            runCatching { instDirWatchService.close() }
            watcherJob?.cancel("Instances Manager is Closing")

            logger.trace { "Launching InstanceChecker Job" }
            checkerJob?.cancel("Instances Manager is Closing")

            delay(50)

            if (!instMgrJob.isCompleted) {
                instMgrJob.cancel("Instances Manager is Closing")
            }
        }

        logger.trace { "Instance Manager Closed" }
    }

    /**
     * Returns [Instance] Object from Map that matches [InstanceID] Object
     */
    fun getInstance(id: InstanceID): Instance? {
        var inst : Instance? = null;
        runBlocking {
            instancesMapMutex.withLock {
                inst= instancesMap[id]
            }
        }

       return inst
    }

}