package com.cloakdroid.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyInputParserTest {
    @Test fun parsesSchemeLessHttp() {
        val result = ProxyInputParser.parse("proxy.example:8080")
        assertEquals(ProxyType.HTTP, result?.type)
        assertEquals("proxy.example", result?.host)
        assertEquals(8080, result?.port)
    }

    @Test fun parsesEncodedCredentials() {
        val result = ProxyInputParser.parse("socks5://user%40name:p%3Ass@127.0.0.1:1080")
        assertEquals(ProxyType.SOCKS5, result?.type)
        assertEquals("user@name", result?.username)
        assertEquals("p:ss", result?.password)
    }

    @Test fun rejectsMissingOrInvalidPort() {
        assertNull(ProxyInputParser.parse("proxy.example"))
        assertNull(ProxyInputParser.parse("proxy.example:0"))
        assertNull(ProxyInputParser.parse("proxy.example:65536"))
    }

    @Test fun directIsNotAnImportProtocol() {
        assertNull(ProxyInputParser.parse("proxy.example:8080", ProxyType.DIRECT))
    }
}
