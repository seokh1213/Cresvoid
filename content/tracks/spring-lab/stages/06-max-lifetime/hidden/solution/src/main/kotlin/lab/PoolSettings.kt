package lab

import com.zaxxer.hikari.HikariConfig

fun poolSettings(jdbcUrl: String): HikariConfig = HikariConfig().apply {
    this.jdbcUrl = jdbcUrl
    username = "sa"
    password = ""
    maximumPoolSize = 4
    minimumIdle = 4
    connectionTimeout = 3_000
    validationTimeout = 1_000
    maxLifetime = 45_000
    keepaliveTime = 30_000
    poolName = "catalog"
}
