package com.example.nvr.utils

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.util.UUID

/**
 * 已发现的 ONVIF 设备元信息。
 *
 * @param xaddr ONVIF 设备服务地址（HTTP，包含 host:port 与路径）。
 * @param host  从 xaddr 解析出来的主机/IP，便于一键填充表单。
 * @param port  从 xaddr 解析出来的端口；为空时表示默认 80。
 * @param types ONVIF Probe 应答里的 d:Types 列表（用于显示设备粗分类）。
 * @param scopes ONVIF Probe 应答里的 d:Scopes，常包含厂商和型号信息。
 */
data class OnvifDevice(
    val xaddr: String,
    val host: String,
    val port: String,
    val types: List<String>,
    val scopes: List<String>,
) {
    val displayName: String
        get() {
            val nameScope = scopes.firstOrNull { it.contains("/name/") }?.substringAfterLast("/name/")
            val hardwareScope = scopes.firstOrNull { it.contains("/hardware/") }?.substringAfterLast("/hardware/")
            val candidate = listOfNotNull(nameScope, hardwareScope).firstOrNull { it.isNotBlank() }
            return candidate?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: host
        }
}

/**
 * 极简的 ONVIF 局域网发现：发送 WS-Discovery Probe（UDP 多播 239.255.255.250:3702），
 * 收集一段时间内的应答并解析成 [OnvifDevice] 列表。
 *
 * 仅做最小可用实现：使用正则解析 SOAP 响应，避开庞大的 XML 库依赖。
 */
object OnvifDiscovery {

    private const val MULTICAST_ADDRESS = "239.255.255.250"
    private const val MULTICAST_PORT = 3702

    /**
     * 启动一次发现。会阻塞最多 [timeoutMs]，期间收集所有应答并去重返回。
     */
    suspend fun probe(context: Context, timeoutMs: Int = 4000): List<OnvifDevice> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifi?.createMulticastLock("nvr-onvif-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        val socket = MulticastSocket()
        socket.soTimeout = 800
        try {
            val messageId = UUID.randomUUID()
            val probe = buildProbeMessage(messageId)
            val payload = probe.toByteArray(Charsets.UTF_8)
            val group = InetAddress.getByName(MULTICAST_ADDRESS)
            socket.send(DatagramPacket(payload, payload.size, group, MULTICAST_PORT))

            val deadline = System.currentTimeMillis() + timeoutMs
            val results = LinkedHashMap<String, OnvifDevice>()
            val buffer = ByteArray(8192)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (timeout: java.net.SocketTimeoutException) {
                    continue
                }
                val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                parseProbeMatches(response).forEach { device ->
                    results.putIfAbsent(device.xaddr, device)
                }
            }
            results.values.toList()
        } catch (t: Throwable) {
            emptyList()
        } finally {
            runCatching { socket.close() }
            runCatching { multicastLock?.release() }
        }
    }

    private fun buildProbeMessage(messageId: UUID): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
                    xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                    xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
                    xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
          <e:Header>
            <w:MessageID>uuid:$messageId</w:MessageID>
            <w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
            <w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
          </e:Header>
          <e:Body>
            <d:Probe>
              <d:Types>dn:NetworkVideoTransmitter</d:Types>
            </d:Probe>
          </e:Body>
        </e:Envelope>
    """.trimIndent()

    private val xaddrRegex = Regex("<[^>]*XAddrs[^>]*>([^<]+)</[^>]*XAddrs[^>]*>", RegexOption.IGNORE_CASE)
    private val typesRegex = Regex("<[^>]*Types[^>]*>([^<]+)</[^>]*Types[^>]*>", RegexOption.IGNORE_CASE)
    private val scopesRegex = Regex("<[^>]*Scopes[^>]*>([^<]+)</[^>]*Scopes[^>]*>", RegexOption.IGNORE_CASE)

    private fun parseProbeMatches(response: String): List<OnvifDevice> {
        val xaddrs = xaddrRegex.find(response)?.groupValues?.get(1)?.trim().orEmpty()
        if (xaddrs.isBlank()) return emptyList()
        val types = typesRegex.find(response)?.groupValues?.get(1)?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
        val scopes = scopesRegex.find(response)?.groupValues?.get(1)?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
        return xaddrs.split(Regex("\\s+")).filter { it.isNotBlank() }.mapNotNull { url ->
            val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return@mapNotNull null
            val host = parsed.host ?: return@mapNotNull null
            val port = if (parsed.port > 0) parsed.port.toString() else ""
            OnvifDevice(
                xaddr = url,
                host = host,
                port = port,
                types = types,
                scopes = scopes,
            )
        }
    }
}
