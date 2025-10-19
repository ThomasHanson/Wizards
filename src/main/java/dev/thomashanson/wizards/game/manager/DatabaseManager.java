package dev.thomashanson.wizards.game.manager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin, String host, int port, String database, String username, String password) {
        this.plugin = plugin;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        
        // Recommended settings for performance and reliability
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setMaximumPoolSize(10); // Max number of connections
        config.setConnectionTimeout(30000); // 30 seconds

        this.dataSource = new HikariDataSource(config);
    }

    /**
     * Closes the connection pool. Should be called in your plugin's onDisable().
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Executes an update query (INSERT, UPDATE, DELETE) asynchronously.
     *
     * @param sql    The SQL statement with '?' placeholders.
     * @param params The parameters to be safely inserted into the query.
     */
    public void executeUpdateAsync(String sql, Object... params) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                statement.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Could not execute database update: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Executes a query that returns data (SELECT) asynchronously.
     *
     * @param sql      The SQL statement with '?' placeholders.
     * @param callback A consumer that will handle the result list on the main server thread.
     * @param params   The parameters to be safely inserted into the query.
     */
    public void executeQueryAsync(String sql, Consumer<List<Map<String, Object>>> callback, Object... params) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Map<String, Object>> resultList = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }

                try (ResultSet resultSet = statement.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    while (resultSet.next()) {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(metaData.getColumnName(i), resultSet.getObject(i));
                        }
                        resultList.add(row);
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Could not execute database query: " + e.getMessage());
                e.printStackTrace();
            }

            // Run the callback on the main server thread to safely use the results
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(resultList));
        });
    }
}