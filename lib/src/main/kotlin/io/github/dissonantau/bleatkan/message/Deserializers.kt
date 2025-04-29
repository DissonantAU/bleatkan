@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package io.github.dissonantau.bleatkan.message


import kotlinx.serialization.*
import kotlinx.serialization.json.*


/* Request Message Deserializers */

object RequestMessageDeserializer : JsonContentPolymorphicSerializer<RequestMessage>(RequestMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestMessage> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject["event"]?.equals("list") ?: false -> RequestMessage.RequestMessageNodeList.serializer()
            jsonObject.containsKey("payload") -> RequestMessage.RequestMessageNodeEvent.serializer()

            else -> throw IllegalArgumentException("Unsupported request type")
        }
    }
}


object RequestPayloadDeserializer : JsonContentPolymorphicSerializer<RequestPayload>(RequestPayload::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestPayload> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.containsKey("token") -> RequestPayload.RequestPayloadEventToken.serializer()
            jsonObject.containsKey("state") -> RequestPayload.RequestPayloadEventState.serializer()
            jsonObject.containsKey("event") -> RequestPayload.RequestPayloadEvent.serializer()

            else -> throw IllegalArgumentException("Unsupported Payload type")
        }
    }
}


/* Result Message Deserializers */

object ResultMessageDeserializer : JsonContentPolymorphicSerializer<ResultMessage>(ResultMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ResultMessage> {
        val jsonObject = element.jsonObject

        return when (jsonObject["event"]?.jsonPrimitive?.content) {
            null -> throw IllegalArgumentException("Payload event is null")

            "payload" -> {
                //Work out if "special" or regular payload

                when (jsonObject["type"]?.jsonPrimitive?.content) {
                    null -> throw IllegalArgumentException("Payload type is null")

                    "number" -> ResultMessage.ResultMessageWithPayloadNumber.serializer()
                    "boolean" -> ResultMessage.ResultMessageWithPayloadBoolean.serializer()
                    else -> ResultMessage.ResultMessageWithPayload.serializer()
                }
            }

            "list" -> ResultMessage.ResultMessageWithEntryList.serializer()
            "info" -> ResultMessage.ResultMessageWithInstanceInfo.serializer()

            else -> throw IllegalArgumentException("Unsupported Payload type")
        }

    }
}


object ResultPayloadDeserializer : JsonContentPolymorphicSerializer<ResultPayload>(ResultPayload::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ResultPayload> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.containsKey("states") -> ResultPayload.ResultPayloadStateList.serializer()
            jsonObject.containsKey("state") -> when {
                jsonObject.containsKey("png") -> ResultPayload.ResultPayloadPng.serializer()
                else -> ResultPayload.ResultPayloadState.serializer()
            }

            else -> throw IllegalArgumentException("Unsupported Payload type")
        }
    }
}
