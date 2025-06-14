package io.github.dissonantau.bleatkan.connection


import io.github.dissonantau.bleatkan.message.ResultMessage


interface ConnectionListener {

    /**
     * Veadotube Connection Event - Connection Error
     *
     * @param connection Connection providing update
     * @param error Error Type
     * @param exception Optional Exception
     *
     * @return *true* if connection should continue (depending on error it may retry several times) or *false* if it should give up immediately
     *
     * - [ConnectionError.MiniV2DotOneConnectionError] and [ConnectionError.InvalidServerOrName] are always terminal failures, regardless of returning *true* or *false*
     * - [ConnectionError.FailedToConnect] may retry again several times unless a *false* is returned
     */
    fun onConnectionError(connection: Connection, error: ConnectionError, exception: Exception? = null): Boolean

    /**
     * Veadotube Connection Event - Connection Active (Connect/Up) or Inactive (Disconnect/Down)
     *
     * @param connection Connection providing update
     * @param active Connection active/inactive
     */
    fun onConnectionChange(connection: Connection, active: Boolean)

    /**
     * Veadotube Connection Event - Message Received
     *
     * @param connection Connection providing update
     * @param message Message Received
     */
    fun onConnectionReceive(connection: Connection, message: ResultMessage)
}
