package xyz.dissonant.veadotube.bleatkan


import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import xyz.dissonant.veadotube.bleatkan.instance.InstanceID

class InstanceIDTest {

    // Test 1 - Valid Input String and expected values
    val testData1 = "mini-08dc982355adb2b7-00002020" //Valid Instance ID string
    val testData1Type = "mini"
    val testData1Timestamp = 638552524708491959
    val testData1Process = 8224

    //Create Object
    val testInstanceID1: InstanceID = InstanceID(testData1)
    val testInstanceID1b: InstanceID = InstanceID(testData1) //Duplicate for equals test

    // Test 2 - Valid Input String and expected values
    val testData2 = "mini-08dc9cd35f178af0-00005ff0" //Valid Instance ID string
    val testData2Type = "mini"
    val testData2Timestamp = 638557678827178736
    val testData2Process = 24560

    //Create Object
    val testInstanceID2: InstanceID = InstanceID(testData2)




    @Test
    fun createWithInvalidString(){
        // Test Fail 1 - Blank Input
        val testDataFail1 = ""
        //Create Object, should Fail
        val exception1 = assertThrows(IllegalArgumentException::class.java) { InstanceID(testDataFail1); }

        assertEquals(exception1.message, "Instance ID String must not be empty or blank")

        //println("Error $exception1")

        // Test Fail 2 - Invalid Input String
        // Last character of 2nd group changed to x, invalid hex
        val testDataFail2 = "mini-08dc9cd35f178afx-00005ff0"
        //Create Object, should Fail
        val exception2 = assertThrows(IllegalArgumentException::class.java) {
            InstanceID(testDataFail2)
        }

      assertEquals(exception2.message, "Instance ID String Invalid: 2nd part is not valid Hex Value")

    }

    @Test
    fun getType() {
        assert(testInstanceID1.type==testData1Type)
        assert(testInstanceID2.type==testData2Type)
    }

    @Test
    fun getTimestamp() {
        assert(testInstanceID1.timestamp==testData1Timestamp)
        assert(testInstanceID2.timestamp==testData2Timestamp)
    }

    @Test
    fun getProcess() {
        assert(testInstanceID1.process==testData1Process)
        assert(testInstanceID2.process==testData2Process)
    }

    @Test
    fun isValid_Test1() {
        assert(testInstanceID1.isValid) //isValid should be true
        assert(testInstanceID2.isValid) //isValid should be true
    }

    @Test
    fun testToString_Test1() {
        //To String value should match on that was fed (Is reconstructed on first request)
        assert(testInstanceID1.toString()==testData1)
        assert(testInstanceID2.toString()==testData2)
    }

    @Test
    fun testHashCode_Test1() {
        // Hashes should NOT Match
        assert(testInstanceID1.hashCode()!=testInstanceID2.hashCode())

        // Hashes should Match (same input)
        assert(testInstanceID1.hashCode()==testInstanceID1b.hashCode())
    }

    @Test
    fun testEquals_Test1() {
        // Should NOT be equal
        assert(testInstanceID1!=testInstanceID2)
        // Should be equal (same input)
        assert(testInstanceID1==testInstanceID1b)
    }

    @Test
    fun compareTo_Test1() {
        // Should be -1
        assert(testInstanceID1.compareTo(testInstanceID2)==-1)
        // Should be 1 (reversed above)
        assert(testInstanceID2.compareTo(testInstanceID1)==1)

        // Should be 0 (same input)
        assert(testInstanceID1.compareTo(testInstanceID1b)==0)
    }
}