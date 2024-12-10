package io.github.dissonantau.bleatkan.instance


interface InstancesListener {

    /**
     * Instance Manager Event - New Instance Started/Detected
     */
    fun onInstanceStart(instance: Instance)

    /**
     * Instance Manager Event - Existing Instance Updated
     *
     * A Major change like a Server IP/Port changing which requires reconnection
     */
    fun onInstanceChangeMajor(instance: Instance, oldInstance: Instance)

    /**
     * Instance Manager Event - Existing Instance Updated
     *
     * A Minor change like a Window Title changing, not requiring reconnection
     */
    fun onInstanceChangeMinor(instance: Instance, change: InstanceChange, oldValue: String)

    /**
     * Instance Manager Event - Existing Instance Closed
     */
    fun onInstanceEnd(id: InstanceID)
}

