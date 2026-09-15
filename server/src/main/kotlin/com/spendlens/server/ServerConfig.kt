package com.spendlens.server

/** Everything the server reads from its environment, read once at startup. */
data class ServerConfig(
    val port: Int,
    val databasePath: String,
    val token: String,
) {
    companion object {
        private const val DEFAULT_PORT = 8080

        /**
         * `SPENDLENS_TOKEN` has no default. A server that quietly started with a well-known token would be
         * open to anyone who has read this repository.
         */
        fun fromEnvironment(env: Map<String, String> = System.getenv()) =
            ServerConfig(
                port = env["PORT"]?.toInt() ?: DEFAULT_PORT,
                databasePath = env["SPENDLENS_DB"] ?: "spendlens-server.db",
                token = requireNotNull(env["SPENDLENS_TOKEN"]?.takeIf { it.isNotBlank() }) {
                    "Set SPENDLENS_TOKEN to the bearer token devices must send"
                },
            )
    }
}
