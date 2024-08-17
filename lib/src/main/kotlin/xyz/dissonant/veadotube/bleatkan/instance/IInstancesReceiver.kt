package xyz.dissonant.veadotube.bleatkan.instance

interface IInstancesReceiver {
    fun onStart(instance: Instance)
    fun onChange(instance: Instance, oldInstance: Instance)
    fun onEnd(id: InstanceID)
}
