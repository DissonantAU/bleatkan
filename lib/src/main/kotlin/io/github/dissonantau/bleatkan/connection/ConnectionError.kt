package io.github.dissonantau.bleatkan.connection


enum class ConnectionError {
    Unknown,
    InvalidServerOrName,
    FailedToConnect,
    IllegalState,
    MiniV2DotOneConnectionError
}
