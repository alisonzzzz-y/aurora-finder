package com.aurora.observation.record;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/** Retry only connection acquisition, before any application SQL can execute. */
final class RetryingDriverDataSource extends DriverManagerDataSource {
    RetryingDriverDataSource(String url, String username, String password) {
        super(url, username, password);
        if (url.startsWith("jdbc:mysql:")) {
            Properties properties = new Properties();
            properties.setProperty("connectTimeout", "5000");
            properties.setProperty("socketTimeout", "15000");
            setConnectionProperties(properties);
        }
    }

    @Override
    protected Connection getConnectionFromDriverManager(String url, Properties properties) throws SQLException {
        for (int attempt = 0; ; attempt++) {
            try {
                return openConnection(url, properties);
            } catch (SQLException failure) {
                // SQLState 08 means a connection failure. Never retry authentication or SQL errors.
                if (attempt == 2 || failure.getSQLState() == null || !failure.getSQLState().startsWith("08")) {
                    throw failure;
                }
                try {
                    Thread.sleep(250L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failure.addSuppressed(interrupted);
                    throw failure;
                }
            }
        }
    }

    protected Connection openConnection(String url, Properties properties) throws SQLException {
        return super.getConnectionFromDriverManager(url, properties);
    }
}
