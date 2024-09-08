package xyz.dissonant.veadotube.bleatkan.connection

import xyz.dissonant.veadotube.bleatkan.message.ResultMessage

interface ConnectionReceiver {

    /**
     * Veadotube Connection Update - Connection Error
     *
     * @param connection Connection providing update
     * @param error Error Type
     */
    fun onError(connection: Connection, error: ConnectionError)

    /**
     * Veadotube Connection Update - Connection Active (Connect/Up) or Inactive (Disconnect/Down)
     *
     * @param connection Connection providing update
     * @param active Error Type
     */
    fun onConnect(connection: Connection, active: Boolean)

    /**
     * Veadotube Connection Update - Message Received
     *
     * @param connection Connection providing update
     * @param message Message Data Received
     */
    fun onReceive(connection: Connection, message: ResultMessage)
}

