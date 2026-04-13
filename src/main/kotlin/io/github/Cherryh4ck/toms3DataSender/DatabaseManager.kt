package io.github.Cherryh4ck.toms3DataSender

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection

object DatabaseManager {
    private var dataSource: HikariDataSource? = null

    fun connect(plugin: Toms3DataSender) {
        val host = plugin.host
        val port = plugin.port
        val db = plugin.db
        val user = plugin.user
        val pass = plugin.password
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://$host:$port/$db?allowMultiQueries=true&autoReconnect=true"
            username = user
            password = pass

            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")

            maximumPoolSize = 2
            minimumIdle = 1

            connectionTestQuery = "SELECT 1"

            connectionTimeout = 3000
            idleTimeout = 30000
            maxLifetime = 60000
            keepaliveTime = 20000
        }

        dataSource = HikariDataSource(config)
    }

    val connection: Connection
        get() = dataSource?.connection ?: throw IllegalStateException("Base de datos no conectada")

    fun close() {
        dataSource?.close()
    }
}