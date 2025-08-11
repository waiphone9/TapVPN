object WireGuardHelper {
    lateinit var tunnelManager: TunnelManager

    fun init(context: Context) {
        if (!::tunnelManager.isInitialized) {
            tunnelManager = TunnelManager(context.applicationContext)
        }
    }

    fun isTunnelActive(): Boolean {
        return tunnelManager.tunnels.any { it.state == Tunnel.State.UP }
    }
}
