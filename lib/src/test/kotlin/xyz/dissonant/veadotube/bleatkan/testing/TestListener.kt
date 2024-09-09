@file:Suppress("unused")

package xyz.dissonant.veadotube.bleatkan.testing

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

import xyz.dissonant.veadotube.bleatkan.connection.*
import xyz.dissonant.veadotube.bleatkan.instance.*
import xyz.dissonant.veadotube.bleatkan.message.ResultMessage


class TestListener : InstancesListener, ConnectionListener {

    private val logger = KotlinLogging.logger {}

    private val instanceMap = ConcurrentHashMap<InstanceID, Instance>()

    private val connectionMap = ConcurrentHashMap<Instance, Connection>()

    /* Instances Manager Events */

    override fun onInstanceStart(instance: Instance) {
        logger.debug { "TestReceiver: onInstanceStart '${instance}'" }
        instanceMap[instance.id] = instance

        //Create a connection
        val connection = Connection(
            instance = instance,
            receiver = this,
        )

        connectionMap[instance] = connection
    }

    override fun onInstanceChange(instance: Instance, oldInstance: Instance) {
        logger.debug { "TestReceiver: onInstanceChange > instance: ${instance}, oldInstance: $oldInstance" }
        instanceMap[instance.id] = instance
    }

    override fun onInstanceEnd(id: InstanceID) {
        logger.debug { "TestReceiver: onInstanceEnd '${id}'" }
        val closingInstance = instanceMap.remove(id)
        val closingConnection = connectionMap.remove(closingInstance)
        closingConnection?.close()
    }

    /* Connection Events */

    override fun onConnectionError(connection: Connection, error: ConnectionError) {
        logger.debug { "TestReceiver: onConnectionError '$connection',error: '$error'" }
    }

    override fun onConnectionChange(connection: Connection, active: Boolean) {
        logger.debug { "TestReceiver: onConnectionChange '$connection', active: '$active'" }
    }

    override fun onConnectionReceive(connection: Connection, message: ResultMessage) {
        logger.debug { "TestReceiver: onConnectionChange '$connection', data: '$message'" }
    }

}