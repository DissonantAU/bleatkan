package xyz.dissonant.veadotube.bleatkan.serializable


import org.junit.jupiter.api.Test


import org.junit.jupiter.api.assertThrows


class VtRequestFactoryTest {


    @Test
    fun getRequestEventListChannel() {
        // Gets Template Channels Event List - List of Channels
        // Expected JSON value
        val reqEventListExpected = """{"event":"list"}"""

        // Value from Factory
        val reqEventListExpectedObj = VtRequestFactory.requestEventList
        //Convert to JSON
        val reqEventListExpectedObjText = reqEventListExpectedObj.toJsonString()

        assert(reqEventListExpectedObjText == reqEventListExpected)
    }

    @Test
    fun getRequestPayloadEventList() {
        // Gets Template Payload Event List - the List of Possible States
        // Expected JSON value
        val paylEventListExpected = """{"event":"list"}"""

        // Value from Factory
        val paylEventListExpectedObj = VtRequestFactory.requestPayloadEventList
        // Convert to JSON
        val paylEventListExpectedObjText = paylEventListExpectedObj.toJsonString()

        assert(paylEventListExpectedObjText == paylEventListExpected)

    }


    @Test
    fun getRequestPayloadEventPeek() {
        // Gets Template Payload Event Peek - the Current State (Avatar)
        // Expected JSON value
        val paylEventPeekExpected = """{"event":"peek"}"""

        // Value from Factory
        val paylEventListExpectedObj = VtRequestFactory.requestPayloadEventPeek
        // Convert to JSON
        val paylEventListExpectedObjText = paylEventListExpectedObj.toJsonString()

        assert(paylEventListExpectedObjText == paylEventPeekExpected)

    }


    @Test
    fun buildRequestEventListChannel() {
        //Request that should get list of Channels (Eg nodes from Mini)
        val reqChannelListExpected = """{"event":"list"}"""

        // Value from Factory - Also should be what the Build Request Returns if it matches inputs
        val reqChannelListExpectedFactObj = VtRequestFactory.requestEventList
        //Convert to JSON
        val reqChannelListExpectedFactObjText = reqChannelListExpectedFactObj.toJsonString()

        assert(reqChannelListExpectedFactObjText == reqChannelListExpected)


        // If we submit more values, it should still return the same value
        val reqChannelListExpectedObj =
            VtRequestFactory.buildRequest(
                event = MessageEvent.LIST
            )
        val reqChannelListExpectedObjText = reqChannelListExpectedObj.toJsonString()

        assert(reqChannelListExpectedObjText == reqChannelListExpected)

        // Should be the same Object as requestEventList
        assert(reqChannelListExpectedObj === reqChannelListExpectedFactObj)


        // If we submit more values, it should still return the same value
        val reqChannelListExpectedObj2 =
            VtRequestFactory.buildRequest(
                event = MessageEvent.LIST,
                type = MessagePayloadType.STATE_EVENTS,
                id = MessagePayloadId.MINI,
                payload = VtRequestFactory.requestPayloadEventList
            )
        val reqChannelListExpectedObj2Text = reqChannelListExpectedObj2.toJsonString()


        assert(reqChannelListExpectedObj2Text == reqChannelListExpected)

        // Should be the same Object as requestEventList
        assert(reqChannelListExpectedObj2 === reqChannelListExpectedFactObj)

    }

    @Test
    fun buildRequestEventListState() {

        // Get list of States (Avatars) and IDs
        val reqEventsListExpected =
            """{"event":"payload","type":"stateEvents","id":"mini","payload":{"event":"list"}}"""

        val reqEventsListObj =
            VtRequestFactory.buildRequest(
                event = MessageEvent.PAYLOAD,
                type = MessagePayloadType.STATE_EVENTS,
                id = MessagePayloadId.MINI,
                payload = VtRequestFactory.requestPayloadEventList
            )
        val reqEventsListObjText = reqEventsListObj.toJsonString()


        assert(reqEventsListObjText == reqEventsListExpected)
    }

    @Test
    fun testBuildRequestPeek() {

        // Get current State
        val reqEventsPeekExpected =
            """{"event":"payload","type":"stateEvents","id":"mini","payload":{"event":"peek"}}"""

        val reqEventsPeekObj =
            VtRequestFactory.buildRequest(
                event = MessageEvent.PAYLOAD,
                type = MessagePayloadType.STATE_EVENTS,
                id = MessagePayloadId.MINI,
                payload = VtRequestFactory.requestPayloadEventPeek
            )
        val reqEventsPeekText = reqEventsPeekObj.toJsonString()

        assert(reqEventsPeekText == reqEventsPeekExpected)
    }


    @Test
    fun testBuildRequestSetAvatar() {

        val reqEventsSetStateExpected =
            """{"event":"payload","type":"stateEvents","id":"mini","payload":{"event":"set","state":"BF"}}"""
        val reqEventsSetStateExpectedObj =
            VtRequestFactory.buildRequest(
                event = MessageEvent.PAYLOAD,
                type = MessagePayloadType.STATE_EVENTS,
                id = MessagePayloadId.MINI,
                payload = VtRequestFactory.buildPayload(event = PayloadEvent.SET, value = "BF")
            )
        val reqEventsSetStateExpectedObjText = reqEventsSetStateExpectedObj.toJsonString()

        assert(reqEventsSetStateExpectedObjText == reqEventsSetStateExpected)
    }


    @Test
    fun testBuildRequestPushAvatar() {

        val reqEventsPushStateExpected =
            """{"event":"payload","type":"stateEvents","id":"mini","payload":{"event":"push","state":"26"}}"""
        val reqEventsPushStateObj =
            VtRequestFactory.buildRequest(
                event = MessageEvent.PAYLOAD,
                type = MessagePayloadType.STATE_EVENTS,
                id = MessagePayloadId.MINI,
                payload = VtRequestFactory.buildPayload(event = PayloadEvent.PUSH, value = "26")
            )
        val reqEventsPushStateObjText = reqEventsPushStateObj.toJsonString()

        assert(reqEventsPushStateObjText == reqEventsPushStateExpected)
    }


    @Test
    fun testBuildRequestPopAvatar() {

        val reqEventsPopStateExpected =
            """{"event":"payload","type":"stateEvents","id":"mini","payload":{"event":"pop","state":"26"}}"""

        val reqEventsPopStateObj =
            VtRequestFactory.buildRequest(
                event = MessageEvent.PAYLOAD,
                type = MessagePayloadType.STATE_EVENTS,
                id = MessagePayloadId.MINI,
                payload = VtRequestFactory.buildPayload(event = PayloadEvent.POP, value = "26")
            )
        val reqEventsPopStateObjText = reqEventsPopStateObj.toJsonString()

        assert(reqEventsPopStateObjText == reqEventsPopStateExpected)
    }


    @Test
    fun testBuildRequestThumbAvatar() {

        val reqEventsRequestThumbAvatarExpected =
            """{"event":"payload","type":"stateEvents","id":"mini","payload":{"event":"thumb","state":"3"}}"""
        val reqEventsRequestThumbAvatarObj =
            VtRequestFactory.buildRequest(
                event = MessageEvent.PAYLOAD,
                type = MessagePayloadType.STATE_EVENTS,
                id = MessagePayloadId.MINI,
                payload = VtRequestFactory.buildPayload(event = PayloadEvent.THUMB, value = "3")
            )
        val reqEventsRequestThumbAvatarObjText = reqEventsRequestThumbAvatarObj.toJsonString()

        assert(reqEventsRequestThumbAvatarObjText == reqEventsRequestThumbAvatarExpected)
    }

    @Test
    fun testBuildRequestListen() {
        // State Event Listener
        val reqEventsRequestListenExpected =
            """{"event":"payload","type":"stateEvents","id":"mini","payload":{"event":"listen","token":"mini.changestate"}}"""

        val reqEventsRequestListenObj =
            VtRequestFactory.buildRequest(
                event = MessageEvent.PAYLOAD,
                type = MessagePayloadType.STATE_EVENTS,
                id = MessagePayloadId.MINI,
                payload = VtRequestFactory.buildPayload(event = PayloadEvent.LISTEN, value = "mini.changestate")
            )
        val reqEventsRequestListenObjText = reqEventsRequestListenObj.toJsonString()

        assert(reqEventsRequestListenObjText == reqEventsRequestListenExpected)
    }

    @Test
    fun testBuildRequestUnlisten() {
    val reqEventsRequestUnlistenExpected =
        """{"event":"payload","type":"stateEvents","id":"mini","payload":{"event":"unlisten","token":"mini.changestate"}}"""

    val reqEventsRequestUnlistenObj =
        VtRequestFactory.buildRequest(
            event = MessageEvent.PAYLOAD,
            type = MessagePayloadType.STATE_EVENTS,
            id = MessagePayloadId.MINI,
            payload = VtRequestFactory.buildPayload(event = PayloadEvent.UNLISTEN, value = "mini.changestate")
        )
    val reqEventsRequestUnlistenObjText = reqEventsRequestUnlistenObj.toJsonString()

    assert(reqEventsRequestUnlistenObjText == reqEventsRequestUnlistenExpected)
}


    @Test
    fun buildPayloadEventList() {
        // Gets Template Payload Event List - the List of Possible States
        // Expected JSON value
        val paylEventListExpected = """{"event":"list"}"""

        // Value from Factory
        val paylEventListExpectedObj = VtRequestFactory.requestPayloadEventList
        // Convert to JSON
        val paylEventListExpectedObjText = paylEventListExpectedObj.toJsonString()

        assert(paylEventListExpectedObjText == paylEventListExpected)


        // Should give the same List Payload - Should be same object as requestPayloadEventList
        val payload1 = VtRequestFactory.buildPayload(event = PayloadEvent.LIST)
        val payload1Text = payload1.toJsonString()

        assert(payload1Text == paylEventListExpected)

        assert(payload1 === paylEventListExpectedObj)


        // Should give the same List Payload - Should be same object as requestPayloadEventList
        val payload2 = VtRequestFactory.buildPayload(event = PayloadEvent.LIST, value = "BF")
        val payload2Text = payload2.toJsonString()

        assert(payload2Text == paylEventListExpected)

        assert(payload2 === paylEventListExpectedObj)

    }

    @Test
        fun buildPayloadEventPeek() {

        // Gets Template Payload Event Peek - the Current State (Avatar)
        // Expected JSON value
        val paylEventPeekExpected = """{"event":"peek"}"""

        // Value from Factory
        val paylEventListExpectedObj = VtRequestFactory.requestPayloadEventPeek
        // Convert to JSON
        val paylEventListExpectedObjText = paylEventListExpectedObj.toJsonString()

        assert(paylEventListExpectedObjText == paylEventPeekExpected)

        // Should give the same List Payload - Should be same object as requestPayloadEventList
        val payload1 = VtRequestFactory.buildPayload(event = PayloadEvent.PEEK)
        val payload1Text = payload1.toJsonString()

        assert(payload1Text == paylEventPeekExpected)

        assert(payload1 === paylEventListExpectedObj)


        // Should give the same List Payload - Should be same object as requestPayloadEventList
        val payload2 = VtRequestFactory.buildPayload(event = PayloadEvent.PEEK, value = "BF")
        val payload2Text = payload2.toJsonString()

        assert(payload2Text == paylEventPeekExpected)


        assert(payload2 == paylEventListExpectedObj)

    }

        @Test
        fun buildPayloadSetState() {

            val payloadExpected = """{"event":"set","state":"BF"}"""
            val payloadObj = VtRequestFactory.buildPayload(event = PayloadEvent.SET, value = "BF")
            val payloadText = payloadObj.toJsonString()

            assert(payloadText == payloadExpected)

            assertThrows<IllegalArgumentException> { VtRequestFactory.buildPayload(event = PayloadEvent.SET) }
        }

    @Test
    fun buildPayloadPushState() {
            val payloadExpected ="""{"event":"push","state":"26"}"""
            val payloadObj = VtRequestFactory.buildPayload(event = PayloadEvent.PUSH, value = "26")
        val payloadText = payloadObj.toJsonString()

            assert(payloadText == payloadExpected)

        assertThrows<IllegalArgumentException> { VtRequestFactory.buildPayload(event = PayloadEvent.PUSH) }
    }

    @Test
    fun buildPayloadPopState() {
            val payloadExpected ="""{"event":"pop","state":"26"}"""
            val payloadObj = VtRequestFactory.buildPayload(event = PayloadEvent.POP, value = "26")
        val payloadText = payloadObj.toJsonString()

            assert(payloadText == payloadExpected)

        assertThrows<IllegalArgumentException> { VtRequestFactory.buildPayload(event = PayloadEvent.POP) }
    }

    @Test
    fun buildPayloadEventStateThumb() {
            val payloadExpected ="""{"event":"thumb","state":"3"}"""
            val payloadObj = VtRequestFactory.buildPayload(event = PayloadEvent.THUMB, value = "3")
        val payloadText = payloadObj.toJsonString()

            assert(payloadText == payloadExpected)

        assertThrows<IllegalArgumentException> { VtRequestFactory.buildPayload(event = PayloadEvent.THUMB) }
    }

    @Test
    fun buildPayloadListen() {
            val payloadExpected ="""{"event":"listen","token":"mini.changestate"}"""
            val payloadObj = VtRequestFactory.buildPayload(event = PayloadEvent.LISTEN, value = "mini.changestate")
        val payloadText = payloadObj.toJsonString()

            assert(payloadText == payloadExpected)
        assertThrows<IllegalArgumentException> { VtRequestFactory.buildPayload(event = PayloadEvent.LISTEN) }

    }

    @Test
    fun buildPayloadUnlisten() {
            val payloadExpected ="""{"event":"unlisten","token":"mini.changestate"}"""
            val payloadObj = VtRequestFactory.buildPayload(event = PayloadEvent.UNLISTEN, value = "mini.changestate")
        val payloadText = payloadObj.toJsonString()

            assert(payloadText == payloadExpected)

        assertThrows<IllegalArgumentException> { VtRequestFactory.buildPayload(event = PayloadEvent.UNLISTEN) }
    }












}