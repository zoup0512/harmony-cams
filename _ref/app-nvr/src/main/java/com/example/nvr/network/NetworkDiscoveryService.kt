package com.example.nvr.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import jcifs.CIFSContext
import jcifs.context.BaseContext
import jcifs.config.PropertyConfiguration
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Properties
import java.util.UUID
import kotlin.coroutines.resume

/**
 * 网络发现服务
 *
 * 真实实现局域网中的 SMB 与 UPnP/DLNA 设备发现：
 * - SMB：使用 jcifs-ng 枚举 `smb://` 工作组/主机
 * - UPnP/DLNA：使用系统 NsdManager（mDNS）发现 _http._tcp / _airplay._tcp 等服务
 *
 * SMB 枚举依赖多播锁；如果未持有 WifiManager.MulticastLock，
 * 仅能发现同一工作组中已注册的主机。调用方通常需要先申请
 * CHANGE_WIFI_MULTICAST_STATE 权限并获取多播锁。
 */
class NetworkDiscoveryService(private val context: Context) {

    private val tag = "NetworkDiscovery"

    data class DiscoveredDevice(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val url: String,
        val host: String = "",
        val port: Int = 0,
        val type: DeviceType,
        val description: String = "",
    )

    enum class DeviceType {
        SMB,
        UPNP_MEDIA_SERVER,
        DLNA,
        RTSP_CAMERA,
    }

    /* ------------------------------------------------------------------ */
    /*  SMB                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 发现局域网中的 SMB 服务器。
     *
     * jcifs-ng 的 `SmbFile("smb://")` 枚举依赖 NetBIOS 名称解析与工作组浏览。
     * 在多数家用网络中可枚举出同一工作组（默认 WORKGROUP）的 NAS / PC。
     *
     * 注意：调用方需要先获取多播锁（见 [acquireMulticastLock]）。
     */
    suspend fun discoverSmbDevices(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(SMB_SCAN_TIMEOUT_MS) {
            runCatching {
                Log.d(tag, "开始扫描 SMB 设备...")
                val cifsContext = buildSmbContext()
                val devices = mutableListOf<DiscoveredDevice>()

                // 枚举工作组 -> 每个工作组下的主机 -> 每个主机的共享
                enumerateSmbHosts(cifsContext).forEach { host ->
                    runCatching {
                        val hostFile = SmbFile(host.url, cifsContext)
                        hostFile.listFiles()?.forEach { share ->
                            val shareName = share.name.trimEnd('/', '\\')
                            if (shareName.isBlank() || shareName.startsWith("IPC$")) return@forEach
                            devices.add(
                                DiscoveredDevice(
                                    name = "${host.name} / $shareName",
                                    url = share.url.toString(),
                                    host = host.host,
                                    port = 445,
                                    type = DeviceType.SMB,
                                    description = host.description.ifBlank { "SMB 共享 ($shareName)" },
                                ),
                            )
                        }
                    }.onFailure {
                        Log.w(tag, "枚举主机 ${host.name} 的共享失败: ${it.message}")
                    }
                }

                Log.d(tag, "SMB 扫描完成，发现 ${devices.size} 个共享")
                devices
            }.getOrElse {
                Log.e(tag, "SMB 扫描失败: ${it.message}", it)
                emptyList()
            }
        } ?: run {
            Log.w(tag, "SMB 扫描超时")
            emptyList()
        }
    }

    /** 构建 jcifs-ng 配置上下文（允许 SMB2/3，禁用严格签名以提升兼容性） */
    private fun buildSmbContext(): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.netbios.wins", ",")
            setProperty("jcifs.resolveOrder", "BCAST,DNS")
            // 匿名访客枚举
            setProperty("jcifs.smb.client.disablePlainTextPasswords", "false")
        }
        val base = BaseContext(PropertyConfiguration(props))
        // 使用匿名凭据（guest）进行枚举
        return base.withAnonymousCredentials()
    }

    private data class SmbHost(val name: String, val url: String, val host: String, val description: String)

    /** 枚举工作组下所有主机。jcifs-ng 的 `smb://` 会列出工作组，再列出每个组的主机。 */
    private fun enumerateSmbHosts(ctx: CIFSContext): List<SmbHost> {
        val hosts = mutableListOf<SmbHost>()
        runCatching {
            val root = SmbFile("smb://", ctx)
            val workgroups = root.listFiles() ?: return@runCatching
            workgroups.forEach { group ->
                runCatching {
                    val groupFile = SmbFile(group.url, ctx)
                    groupFile.listFiles()?.forEach { server ->
                        val serverName = server.name.trimEnd('/')
                        if (serverName.isBlank()) return@forEach
                        val host = resolveHost(server)
                        hosts.add(
                            SmbHost(
                                name = serverName,
                                url = server.url.toString(),
                                host = host,
                                description = "工作组 ${group.name.trimEnd('/')}",
                            ),
                        )
                    }
                }.onFailure { Log.w(tag, "枚举工作组 ${group.name} 失败: ${it.message}") }
            }
        }.onFailure { Log.w(tag, "SMB 工作组枚举失败: ${it.message}") }
        return hosts
    }

    private fun resolveHost(smbFile: SmbFile): String {
        return runCatching {
            smbFile.url.host ?: smbFile.name.trimEnd('/')
        }.getOrDefault("")
    }

    /* ------------------------------------------------------------------ */
    /*  UPnP / DLNA（基于系统 NsdManager / mDNS）                           */
    /* ------------------------------------------------------------------ */

    /**
     * 通过 mDNS（NsdManager）发现 UPnP / DLNA 媒体服务。
     *
     * 常见服务类型：
     * - `_http._tcp.`：DLNA 媒体服务器（如 Kodi、VLC、NAS 的 DLNA 插件）
     * - `_airplay._tcp.`：AirPlay 设备
     * - `_raop._tcp.`：AirPlay 音频
     * - `_smb._tcp.`：SMB（部分路由器会广播）
     */
    suspend fun discoverUpnpDevices(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        if (!isNsdAvailable()) {
            Log.w(tag, "NsdManager 不可用，跳过 UPnP 扫描")
            return@withContext emptyList()
        }

        val found = mutableMapOf<String, DiscoveredDevice>()

        UPNP_SERVICE_TYPES.forEach { serviceType ->
            val results = discoverNsdService(serviceType)
            results.forEach { device ->
                // 以 host:port 去重
                val key = "${device.host}:${device.port}"
                found.putIfAbsent(key, device)
            }
        }

        Log.d(tag, "UPnP/DLNA 扫描完成，发现 ${found.size} 个设备")
        found.values.toList()
    }

    private suspend fun discoverNsdService(serviceType: String): List<DiscoveredDevice> =
        withTimeoutOrNull(NSD_DISCOVERY_WINDOW_MS) {
            suspendCancellableCoroutine { cont ->
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                if (nsdManager == null) {
                    cont.resume(emptyList())
                    return@suspendCancellableCoroutine
                }

                val results = mutableListOf<DiscoveredDevice>()
                var finished = false
                lateinit var listener: NsdManager.DiscoveryListener

                fun finish() {
                    if (!finished && cont.isActive) {
                        finished = true
                        runCatching { nsdManager.stopServiceDiscovery(listener) }
                        cont.resume(results.toList())
                    }
                }

                listener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                        Log.w(tag, "NSD 开始发现失败: $serviceType ($errorCode)")
                        finish()
                    }

                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                        Log.w(tag, "NSD 停止发现失败: $serviceType ($errorCode)")
                    }

                    override fun onDiscoveryStarted(serviceType: String?) {
                        Log.d(tag, "NSD 开始发现: $serviceType")
                    }

                    override fun onDiscoveryStopped(serviceType: String?) {
                        Log.d(tag, "NSD 停止发现: $serviceType")
                    }

                    override fun onServiceFound(info: NsdServiceInfo) {
                        // 异步解析服务地址
                        resolveService(nsdManager, info) { resolved ->
                            if (resolved != null) synchronized(results) { results.add(resolved) }
                        }
                    }

                    override fun onServiceLost(info: NsdServiceInfo) {
                        Log.d(tag, "NSD 服务丢失: ${info.serviceName}")
                    }
                }

                runCatching {
                    nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                }.onFailure {
                    Log.e(tag, "启动 NSD 发现失败: ${it.message}", it)
                    finish()
                }

                // 发现窗口结束后统一返回
                cont.invokeOnCancellation { runCatching { nsdManager.stopServiceDiscovery(listener) } }
            }
        } ?: run {
            Log.d(tag, "NSD 发现窗口超时: $serviceType")
            emptyList()
        }

    private fun resolveService(
        nsdManager: NsdManager,
        info: NsdServiceInfo,
        onResolved: (DiscoveredDevice?) -> Unit,
    ) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host: String = serviceInfo.host?.hostAddress ?: return onResolved(null)
                val port = serviceInfo.port
                val name = serviceInfo.serviceName.ifBlank { host }
                val isDlna = serviceInfo.serviceType.contains("http")
                val type = if (isDlna) DeviceType.DLNA else DeviceType.UPNP_MEDIA_SERVER
                onResolved(
                    DiscoveredDevice(
                        name = name,
                        url = "http://$host:${if (port > 0) port else 80}",
                        host = host,
                        port = if (port > 0) port else 80,
                        type = type,
                        description = if (isDlna) "DLNA 媒体服务器" else "UPnP 设备",
                    ),
                )
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(tag, "NSD 解析失败: ${serviceInfo.serviceName} ($errorCode)")
                onResolved(null)
            }
        }
        runCatching { nsdManager.resolveService(info, resolveListener) }
            .onFailure { Log.w(tag, "调用 resolveService 失败: ${it.message}") }
    }

    private fun isNsdAvailable(): Boolean =
        runCatching { context.getSystemService(Context.NSD_SERVICE) is NsdManager }.getOrDefault(false)

    /* ------------------------------------------------------------------ */
    /*  多播锁                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * 申请多播锁，使 mDNS / NetBIOS 广播包能被接收。
     * 调用方应在扫描开始时调用 [acquireMulticastLock]，扫描结束时 [releaseMulticastLock]。
     */
    fun acquireMulticastLock() {
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.createMulticastLock(MULTICAST_LOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire()
                _multicastLock = this
                Log.d(tag, "已获取多播锁")
            }
        }.onFailure { Log.w(tag, "获取多播锁失败: ${it.message}") }
    }

    fun releaseMulticastLock() {
        runCatching {
            _multicastLock?.takeIf { it.isHeld }?.release()
            _multicastLock = null
            Log.d(tag, "已释放多播锁")
        }.onFailure { Log.w(tag, "释放多播锁失败: ${it.message}") }
    }

    @Volatile
    private var _multicastLock: WifiManager.MulticastLock? = null

    /* ------------------------------------------------------------------ */
    /*  完整扫描                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 执行完整的网络扫描（SMB + UPnP/DLNA）。
     * 会自动管理多播锁。
     */
    suspend fun discoverAll(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        acquireMulticastLock()
        try {
            val all = mutableListOf<DiscoveredDevice>()
            runCatching { all.addAll(discoverSmbDevices()) }
                .onFailure { Log.e(tag, "SMB 扫描异常: ${it.message}", it) }
            runCatching { all.addAll(discoverUpnpDevices()) }
                .onFailure { Log.e(tag, "UPnP 扫描异常: ${it.message}", it) }
            all
        } finally {
            releaseMulticastLock()
        }
    }

    companion object {
        private const val SMB_SCAN_TIMEOUT_MS = 30_000L
        private const val MULTICAST_LOCK_TAG = "nvr-network-discovery"

        /** UPnP / DLNA / SMB 相关的 mDNS 服务类型 */
        private val UPNP_SERVICE_TYPES = listOf(
            "_http._tcp.",        // DLNA 媒体服务器
            "_airplay._tcp.",     // AirPlay
            "_smb._tcp.",         // SMB（部分设备会广播）
            "_rtsp._tcp.",        // RTSP 相机/流媒体
        )

        /** 为 NSD 发现设置固定时间窗口（毫秒） */
        private const val NSD_DISCOVERY_WINDOW_MS = 5_000L
    }
}
