# BleatKan
A Veadotube WebSocket API Library written in Kotlin

Originally Created alongside the Touch Portal Veadotube Plugin, referencing the original Bleat Can C# Library

Very much a Work in Progress

Only Tested with Veadotube Mini v2.0 - Future Releases and the release of Veadotube (Full) will likely require updates if not breaking changes.

Any Breaking changes will involve a major version change


Built using

- Kotlin 1.9/2.0
- Coroutines Library for concurrency
- Ktor & CIO for WebSocket Client
- Kotlin Serialization for Serializing and Deserializing WebSocket Messaged (JSON)



Targets JVM 8 and aims to be usable with Java as well as Kotlin

### Use
See the Touch Portal Veadotube Plugin Code for an example of use

Includes:
- Instances Manager
  - Monitors .veadotube folder inside the user home
  - Decodes instance files and creates corresponding Objects
  - Sends Events/Objects to an Instances Receiver 
- Connection Class and Serializable
  - Connects to Veadotube
  - Object Factory/Builder and Convenience Functions for common API Messages
  - Serializes and Deserializes Messaged to/from objects to avoid exposing libraries or creating compatibility issues
  - Message Objects are sent to a Connection Receiver

### Links
- Veadotube: https://veado.tube
- Bleat Can (API): https://gitlab.com/veadotube/bleatcan
- Veadotube WebSocket Reference: https://veado.tube/help/docs/websocket
