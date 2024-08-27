package xyz.dissonant.veadotube.bleatkan.testing

import io.github.oshai.kotlinlogging.KotlinLogging

import xyz.dissonant.veadotube.bleatkan.connection.*
import xyz.dissonant.veadotube.bleatkan.instance.*

import xyz.dissonant.veadotube.bleatkan.message.ResultMessage


import java.util.concurrent.ConcurrentHashMap

class TestReceiver : InstancesReceiver, ConnectionReceiver {

    private val logger = KotlinLogging.logger {}

    private val instanceMap = ConcurrentHashMap<InstanceID, Instance>()

    //private val connectionMap = ConcurrentHashMap<String, Connection>()

        fun getInstances(): Map<InstanceID, Instance> {
        return instanceMap.toMap()
    }

    /*  */

    override fun onStart(instance: Instance) {
        logger.debug { "TestReceiver: onStart '${instance}'" }
        instanceMap[instance.id] = instance

        //val connection: Connection = Connection(instance.server!!,instance.name!!,this)
        //connection.runWS()
    }

    override fun onChange(instance: Instance, oldInstance: Instance) {
        logger.debug { "TestReceiver: onChange > instance: ${instance}, oldInstance: $oldInstance" }
        instanceMap[instance.id] = instance
    }

    override fun onEnd(id: InstanceID) {
        logger.debug { "TestReceiver: onEnd '${id}'" }
        instanceMap.remove(id)
    }

    /*  */

    override fun onError(connection: Connection, error: ConnectionError) {
        logger.debug { "TestReceiver: onError '$connection',error: '$error'" }
    }

    override fun onConnect(connection: Connection, active: Boolean) {
        logger.debug { "TestReceiver: onConnect '$connection', active: '$active'" }
    }

    override fun onReceive(connection: Connection, channel: String, data: ResultMessage) {
        logger.debug { "TestReceiver: onConnect '$connection', data: '$data'" }
    }

}