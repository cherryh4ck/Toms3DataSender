package io.github.Cherryh4ck.toms3DataSender

import io.github.Cherryh4ck.toms3DataSender.Commands.AddQuantity
import io.github.Cherryh4ck.toms3DataSender.Commands.ChangeGradient
import io.github.Cherryh4ck.toms3DataSender.Listeners.BlockObfuscatedNC
import io.github.Cherryh4ck.toms3DataSender.Listeners.ChatListener
import io.github.Cherryh4ck.toms3DataSender.Listeners.ConnectionListener
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import java.math.BigDecimal
import com.sun.management.OperatingSystemMXBean
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Statistic
import kotlin.use

class Toms3DataSender : JavaPlugin() {
    val prefix = "<gold>[<red><bold>Toms3<gray>Core</gray></bold></red>]"

    val host = config.getString("host")
    val port = config.getInt("port")
    val db = config.getString("db")
    val user = config.getString("user")
    val password = config.getString("password")

    val forceUpdate = config.getBoolean("force-update")

    val currentPlayerPeak = java.util.concurrent.atomic.AtomicInteger(0)
    private val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(ChatListener(this), this)
        server.pluginManager.registerEvents(ConnectionListener(this), this)
        server.pluginManager.registerEvents(BlockObfuscatedNC(this), this)
        getCommand("addquantity")?.setExecutor(AddQuantity(this))
        getCommand("changegradient")?.setExecutor(ChangeGradient(this))
        logger.info("Toms3DataSender enabled.")
        logger.info("Hooked into chat successfully.")
        logger.info("Hooked into connections successfully.")

        DatabaseManager.connect(this)

        logger.info("Connected successfully.")

        if (forceUpdate){
            forceUpdate()
            logger.info("Force update successfully.")
            logger.info("Please disable it.")
        }

        logger.info("Checking tables...")
        createTables()

        logger.info("Loading last player peak value...")
        currentPlayerPeak.set(getPlayerPeak())
        logger.info("Last player peak value: $currentPlayerPeak")

        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            updateServerData()
            updatePlayerData()
        }, 20L, 600L)
    }

    fun getPlayerPeak() : Int {
        val sql = "SELECT peak_player_count FROM ServerData WHERE id = 1"

        DatabaseManager.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                val rs = ps.executeQuery()
                if (rs.next()) {
                    return rs.getInt("peak_player_count")
                }
            }
        }

        return 0
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
                cpu_usage TEXT NOT NULL,
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
                joindate BIGINT NOT NULL DEFAULT 0,
                is_donor TINYINT(1) NOT NULL DEFAULT 0,
                money_donated DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                gradient_id INT NOT NULL DEFAULT 1
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

        val rawCpu = osBean.processCpuLoad
        val cpuUsage = (if (rawCpu < 0) 0.0 else rawCpu * 100).toString()

        val worldSize = PlaceholderAPI.setPlaceholders(null, "%worldstats_size%")

        val onlinePlayers = Bukkit.getOnlinePlayers()
        val playerCount = Bukkit.getOnlinePlayers().size

        val uptime = "${days}d ${hours}h ${minutes}m ${seconds}s"

        val sql = """
            INSERT INTO ServerData (id, unique_joins, world_size, uptime, tps, mspt, cpu_usage, peak_player_count) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE 
                unique_joins = VALUES(unique_joins), 
                world_size = VALUES(world_size),
                uptime = VALUES(uptime),
                tps = VALUES(tps),
                mspt = VALUES(mspt),
                cpu_usage = VALUES(cpu_usage),
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
                    ps.setString(7, cpuUsage)
                    ps.setInt(8, currentPlayerPeak.get())
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
            INSERT INTO PlayerData(uuid, name, playtime, kills, deaths, joindate, is_donor)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                uuid = VALUES(uuid),
                name = VALUES(name),
                playtime = VALUES(playtime),
                kills = VALUES(kills),
                deaths = VALUES(deaths),
                joindate = VALUES(joindate),
                is_donor = VALUES(is_donor)
        """.trimIndent()

        DatabaseManager.connection.use { conn ->
            for (player in Bukkit.getOnlinePlayers()) {
                val isDonor = player.hasPermission("tmdonors.donors") && !player.isOp
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, player.uniqueId.toString())
                    ps.setString(2, player.name)
                    ps.setInt(3, player.getStatistic(Statistic.PLAY_ONE_MINUTE))
                    ps.setInt(4, player.getStatistic(Statistic.PLAYER_KILLS))
                    ps.setInt(5, player.getStatistic(Statistic.DEATHS))
                    ps.setLong(6, player.firstPlayed)
                    ps.setBoolean(7, isDonor)
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

    fun addQuantity(name: String, quantity: BigDecimal) {
        val sql = """
            UPDATE PlayerData
            SET money_donated = money_donated + ?
            WHERE name = ?
        """.trimIndent()

        DatabaseManager.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setBigDecimal(1, quantity)
                ps.setString(2, name)
                ps.executeUpdate()
            }
        }
    }

    fun updateGradient(uuid: String, gradientId: Int) {
        val sql = """
            UPDATE PlayerData
            SET gradient_id = ?
            WHERE uuid = ?
        """.trimIndent()

        DatabaseManager.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, gradientId)
                ps.setString(2, uuid)
                ps.executeUpdate()
            }
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
