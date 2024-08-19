package xyz.dissonant.veadotube.bleatkan.instance

interface InstancesReceiver {
    fun onStart(instance: Instance)
    fun onChange(instance: Instance, oldInstance: Instance)
    fun onEnd(id: InstanceID)
}
