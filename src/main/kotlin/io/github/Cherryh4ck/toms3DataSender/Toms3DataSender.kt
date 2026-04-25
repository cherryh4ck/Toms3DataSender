package io.github.Cherryh4ck.toms3DataSender

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Statistic
import kotlin.use

class Toms3DataSender : JavaPlugin() {
    val host = config.getString("host")
    val port = config.getInt("port")
    val db = config.getString("db")
    val user = config.getString("user")
    val password = config.getString("password")

    val forceUpdate = config.getBoolean("force-update")

    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(ChatListener(this), this)
        logger.info("Toms3DataSender enabled.")
        logger.info("Hooked into chat successfully.")

        DatabaseManager.connect(this)

        if (forceUpdate){
            forceUpdate()
            logger.info("Force update successfully.")
            logger.info("Please disable it.")
        }

        logger.info("Checking tables...")
        createTables()

        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            updateServerData()
            updatePlayerData()
        }, 20L, 600L)
    }

    fun createTables(){
        val sql = """
            CREATE TABLE IF NOT EXISTS ServerData(
                ID INT AUTO_INCREMENT PRIMARY KEY, 
                unique_joins INT NOT NULL, 
                world_size TEXT NOT NULL, 
                uptime VARCHAR(60) NOT NULL, 
                tps DECIMAL(10, 2) NOT NULL, 
                mspt DECIMAL(10, 2) NOT NULL,
                peak_player_count INT NOT NULL,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4; 
            
            CREATE TABLE IF NOT EXISTS ConnectedPlayers(
                name VARCHAR(100) NOT NULL UNIQUE PRIMARY KEY,
                uuid CHAR(36) NULL,
                ping INT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            
            CREATE TABLE IF NOT EXISTS PlayerData(
                uuid CHAR(36) PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                playtime BIGINT NOT NULL DEFAULT 0,
                kills BIGINT NOT NULL DEFAULT 0,
                deaths BIGINT NOT NULL DEFAULT 0,
                joindate BIGINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            
            CREATE TABLE IF NOT EXISTS ChatData(
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                author VARCHAR(100) NOT NULL,
                message TEXT NOT NULL,
                event_type ENUM('CHAT', 'CHAT_GREENTEXT', 'CHAT_REDTEXT', 'EVENT'),
                date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """.trimIndent()

        DatabaseManager.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
                logger.info("Executed server & player data table creation.")
            }
        }
    }

    fun forceUpdate(){
        val sql = """
            DELETE FROM ServerData;
        """.trimIndent()

        DatabaseManager.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }
        }
    }

    fun updateServerData(){
        val uniqueJoins = server.offlinePlayers.size
        val tps = server.tps[0]
        val mspt = server.averageTickTime

        val uptimeMs = ManagementFactory.getRuntimeMXBean().uptime

        val days = TimeUnit.MILLISECONDS.toDays(uptimeMs)
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMs) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(uptimeMs) % 60

        val worldSize = PlaceholderAPI.setPlaceholders(null, "%worldstats_size%")

        val onlinePlayers = Bukkit.getOnlinePlayers()
        val playerCount = Bukkit.getOnlinePlayers().size

        val uptime = "${days}d ${hours}h ${minutes}m ${seconds}s"

        val sql = """
            INSERT INTO ServerData (id, unique_joins, world_size, uptime, tps, mspt, peak_player_count) 
            VALUES (?, ?, ?, ?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE 
                unique_joins = VALUES(unique_joins), 
                world_size = VALUES(world_size),
                uptime = VALUES(uptime),
                tps = VALUES(tps),
                mspt = VALUES(mspt),
                peak_player_count = GREATEST(peak_player_count, VALUES(peak_player_count));
        """.trimIndent()

        val removeConnectedPlayers = """
            DELETE FROM ConnectedPlayers;
        """.trimIndent()

        val addConnectedPlayer = """
            INSERT INTO ConnectedPlayers(name, ping) VALUES (?, ?)
        """.trimIndent()

        try {
            DatabaseManager.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, 1)
                    ps.setInt(2, uniqueJoins)
                    ps.setString(3, worldSize)
                    ps.setString(4, uptime)
                    ps.setDouble(5, tps)
                    ps.setDouble(6, mspt)
                    ps.setInt(7, playerCount)
                    ps.executeUpdate()
                }

                conn.createStatement().use { stmt ->
                    stmt.execute(removeConnectedPlayers)
                }

                for (player in onlinePlayers){
                    conn.prepareStatement(addConnectedPlayer).use { ps ->
                        ps.setString(1, player.name)
                        ps.setInt(2, player.ping)
                        ps.executeUpdate()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updatePlayerData(){
        val sql = """
            INSERT INTO PlayerData(uuid, name, playtime, kills, deaths, joindate)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                uuid = VALUES(uuid),
                name = VALUES(name),
                playtime = VALUES(playtime),
                kills = VALUES(kills),
                deaths = VALUES(deaths),
                joindate = VALUES(joindate)
        """.trimIndent()

        DatabaseManager.connection.use { conn ->
            for (player in Bukkit.getOnlinePlayers()) {
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, player.uniqueId.toString())
                    ps.setString(2, player.name)
                    ps.setInt(3, player.getStatistic(Statistic.PLAY_ONE_MINUTE))
                    ps.setInt(4, player.getStatistic(Statistic.PLAYER_KILLS))
                    ps.setInt(5, player.getStatistic(Statistic.DEATHS))
                    ps.setLong(6, player.firstPlayed)
                    ps.executeUpdate()
                }
            }
        }
    }

    fun insertChatData(name: String, message: String, type: String) {
        val sqlInsert = "INSERT INTO ChatData(author, message, event_type) VALUES (?, ?, ?)"
        val sqlLimit = """
        DELETE FROM ChatData 
        WHERE id NOT IN (
            SELECT id FROM (
                SELECT id FROM ChatData ORDER BY date DESC LIMIT 150
            ) as temp
        )
        """.trimIndent()

        try {
            DatabaseManager.connection.use { conn ->
                conn.prepareStatement(sqlInsert).use { ps ->
                    ps.setString(1, name)
                    ps.setString(2, message)
                    ps.setString(3, type)
                    ps.executeUpdate()
                }
                conn.createStatement().use { stmt ->
                    stmt.execute(sqlLimit)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
