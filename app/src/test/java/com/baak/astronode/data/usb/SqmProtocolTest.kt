package com.baak.astronode.data.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SqmProtocolTest {

    @Test
    fun `standart SQM-LU yanıtı ayrıştırılır`() {
        val r = SqmProtocol.parseResponse("r, 19.29m,0000005915Hz,0000000000c,0000000.000s, 029.4C\r\n")
        assertNotNull(r)
        assertEquals(19.29, r!!.mpsas, 1e-9)
        assertEquals(29.4, r.temperature!!, 1e-9)
    }

    @Test
    fun `eksi sıcaklık işaretini korur`() {
        val r = SqmProtocol.parseResponse("r, 21.05m,0000000012Hz,0000000000c,0000000.000s,-005.2C")
        assertEquals(-5.2, r!!.temperature!!, 1e-9)
    }

    @Test
    fun `aydınlık ortamda eksi MPSAS işaretini korur`() {
        val r = SqmProtocol.parseResponse("r,-09.42m,0000999999Hz,0000000000c,0000000.000s, 031.0C")
        assertEquals(-9.42, r!!.mpsas, 1e-9)
    }

    @Test
    fun `virgüllü ondalık kabul edilir`() {
        val r = SqmProtocol.parseResponse("r, 18,75m,")
        assertEquals(18.75, r!!.mpsas, 1e-9)
    }

    @Test
    fun `anlamsız yanıt null döner`() {
        assertNull(SqmProtocol.parseResponse("garbage"))
        assertNull(SqmProtocol.parseResponse(""))
    }
}
