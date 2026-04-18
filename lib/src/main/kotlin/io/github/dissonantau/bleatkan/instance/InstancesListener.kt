package io.github.dissonantau.bleatkan.instance

import kotlin.jvm.Throws


interface InstancesListener {
    /**
     * Instance Manager Event - New Instance Opened & Detected
     *
     * Instance may not have an active Websocket Server, [onInstanceServerStart] is called when the Websocket Server is detected
     *
     * @throws IllegalArgumentException if [Instance.id.type][io.github.dissonantau.bleatkan.instance.InstanceID.type] not recognised - Should be 'mini' or 'veado'
     */
    @Throws(IllegalArgumentException::class)
    fun onInstanceOpen(instance: Instance)

    /**
     * Instance Manager Event - Instance Websocket Server Started
     *
     * @throws IllegalArgumentException if [Instance.id.type][io.github.dissonantau.bleatkan.instance.InstanceID.type] not recognised - Should be 'mini' or 'veado'
     */
    @Throws(IllegalArgumentException::class)
    fun onInstanceServerStart(instance: Instance)

    /**
     * Instance Manager Event - Existing Instance Websocket Server has had a major change
     *
     * e.g. The Server IP/Port changed, requiring reconnection
     *
     * Listener will need to handle reconnection, and updating/relinking any cached data.
     */
    fun onInstanceChangeMajor(instance: Instance, oldInstance: Instance)

    /**
     * Instance Manager Event - Existing Instance Websocket Server  has had a minor change
     *
     * e.g. the Window Title has changed. Does not require reconnection, but Listener may need to handle the change in some way.
     *
     * Change is an Enum from [InstanceChange]
     * - [InstanceChange.TITLE] is for Window Title Changes, [oldValue] is the old Window Title. The Title in [instance] will have already been updated.
     */
    fun onInstanceChangeMinor(instance: Instance, change: InstanceChange, oldValue: String)

    /**
     * Instance Manager Event - Instance Websocket Server Stopped.
     *
     * Instance may still be running and Websocket Server may be restarted.
     *
     * Cached Data could be kept, or Instance ID may be kept for ordering in case of Instance starting, etc.
     *
     * [onInstanceServerStart] will be called if/when the Websocket Server is started again.
     */
    fun onInstanceServerStop(instance: Instance)

    /**
     * Instance Manager Event - Instance Closed/Exited
     *
     * The Instance ID of any future Instances will not have the same ID.
     *
     * Any remaining data should be cleaned up.
     */
    fun onInstanceClose(id: InstanceID)
}

