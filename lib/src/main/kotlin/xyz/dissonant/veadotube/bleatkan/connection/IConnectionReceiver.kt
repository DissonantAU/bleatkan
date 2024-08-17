package xyz.dissonant.veadotube.bleatkan.connection

import xyz.dissonant.veadotube.bleatkan.serializable.VtResultMessage


interface IConnectionReceiver {
    fun onError(connection: Connection, error: ConnectionError)

    fun onConnect(connection: Connection, active: Boolean)

    fun onReceive(connection: Connection, channel: String, data: VtResultMessage)

}

