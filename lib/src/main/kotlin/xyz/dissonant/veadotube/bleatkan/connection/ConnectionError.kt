package xyz.dissonant.veadotube.bleatkan.connection

enum class ConnectionError {
    None,
    InvalidServerOrName,
    FailedToConnect,
    ExceededRetries
}
