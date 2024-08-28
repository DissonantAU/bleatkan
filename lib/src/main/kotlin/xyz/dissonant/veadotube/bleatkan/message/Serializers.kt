@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package xyz.dissonant.veadotube.bleatkan.message

import kotlinx.serialization.*

import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject


/* Request Messages */

object VtRequestMessageSerializer : JsonContentPolymorphicSerializer<RequestMessage>(RequestMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestMessage> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject["event"]?.equals("list") ?: false -> RequestMessage.RequestMessageNodeList.serializer()
            jsonObject.containsKey("payload") -> RequestMessage.RequestMessageNodeEvent.serializer()
            else -> throw IllegalArgumentException("Unsupported request type")
        }
    }
}


object VtRequestPayloadSerializer : JsonContentPolymorphicSerializer<RequestPayload>(RequestPayload::class) {
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


/* Result Messages */

object VtResultMessageSerializer : JsonContentPolymorphicSerializer<ResultMessage>(ResultMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ResultMessage> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.containsKey("payload") -> ResultMessage.ResultMessagePayload.serializer()
            jsonObject.containsKey("entries") -> ResultMessage.ResultMessageEntries.serializer()
            else -> throw IllegalArgumentException("Unsupported Payload type")
        }
    }
}


object VtResultPayloadSerializer : JsonContentPolymorphicSerializer<VtResultPayload>(VtResultPayload::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<VtResultPayload> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.containsKey("states") -> VtResultPayload.VTResultSEListPayload.serializer()
            jsonObject.containsKey("state") -> when {
                jsonObject.containsKey("png") -> VtResultPayload.VTResultSEThumbPayload.serializer()
                else -> VtResultPayload.VTResultSEPeekPayload.serializer()
            }

            else -> throw IllegalArgumentException("Unsupported Payload type")
        }
    }
}
