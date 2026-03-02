package ru.typik.mi.router.android.client

import org.junit.Ignore
import org.junit.Test

class AdminClientTest {

    @Ignore
    @Test
    fun test() {
        val client = AdminClient("http://192.168.31.1", "admin", "12@Wifi@21")
        client.init()

        println("VPN: ${client.getVpnList()}")
        println("Status: ${client.getVpnStatus()}")

        client.changeStatusVpn(client.getVpnList().first(), !client.getVpnStatus())

        println("VPN: ${client.getVpnList()}")
        println("Status: ${client.getVpnStatus()}")
    }

}