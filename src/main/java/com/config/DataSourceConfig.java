package com.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {

    // 1. Core DataSource using Hikari
    @Primary
    @Bean(name = "dataSource")
    public HikariDataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("HikariPool-Core");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setJdbcUrl("jdbc:sqlserver://localhost:1433;databaseName=IQA;encrypt=false;sendStringParametersAsUnicode=true;");
        ds.setUsername("sa1");
        ds.setPassword("Cdit@mothai34nam");
        ds.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        ds.setMaximumPoolSize(30);
        ds.setMinimumIdle(2);                   // Low minimum idle for fast async startup
        ds.setInitializationFailTimeout(0);     // Don't block application startup
        ds.setConnectionTimeout(30000);        // 30 seconds wait timeout
        ds.setMaxLifetime(1800000);            // 30 minutes max connection lifetime
        ds.setIdleTimeout(600000);              // 10 minutes idle timeout
        ds.setKeepaliveTime(60000);             // 1 minute keepalive ping
        ds.setValidationTimeout(3000);          // 3 seconds
        ds.setLeakDetectionThreshold(60000);    // 60 seconds leak detection threshold
        return ds;
    }

    // 2. Evidence DataSource using Hikari
    @Bean(name = "evidenceDataSource")
    public HikariDataSource evidenceDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("HikariPool-Evidence");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setJdbcUrl("jdbc:sqlserver://localhost:1433;databaseName=kiemdinh;encrypt=false;sendStringParametersAsUnicode=true;");
        ds.setUsername("sa1");
        ds.setPassword("Cdit@mothai34nam");
        ds.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        ds.setMaximumPoolSize(20);
        ds.setMinimumIdle(2);                   // Low minimum idle for fast async startup
        ds.setInitializationFailTimeout(0);     // Don't block application startup
        ds.setConnectionTimeout(30000);        // 30 seconds wait timeout
        ds.setMaxLifetime(1800000);            // 30 minutes max connection lifetime
        ds.setIdleTimeout(600000);              // 10 minutes idle timeout
        ds.setKeepaliveTime(60000);             // 1 minute keepalive ping
        ds.setValidationTimeout(3000);          // 3 seconds
        ds.setLeakDetectionThreshold(60000);    // 60 seconds leak detection threshold
        return ds;
    }

    @Primary
    @Bean(name = "jdbcTemplate")
    public JdbcTemplate coreJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "evidenceJdbcTemplate")
    public JdbcTemplate evidenceJdbcTemplate(@Qualifier("evidenceDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "evidenceTransactionManager")
    public PlatformTransactionManager evidenceTransactionManager(@Qualifier("evidenceDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}