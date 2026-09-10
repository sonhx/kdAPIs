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

import org.springframework.beans.factory.annotation.Value;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.core.hikari.jdbc-url:jdbc:sqlserver://localhost:1433;databaseName=IQA;encrypt=false;trustServerCertificate=true;sendStringParametersAsUnicode=true;loginTimeout=60;}")
    private String coreJdbcUrl;

    @Value("${spring.datasource.core.hikari.username:sa1}")
    private String coreUsername;

    @Value("${spring.datasource.core.hikari.password:Cdit@mothai34nam}")
    private String corePassword;

    @Value("${spring.datasource.evidence.hikari.jdbc-url:jdbc:sqlserver://localhost:1433;databaseName=kiemdinh;encrypt=false;trustServerCertificate=true;sendStringParametersAsUnicode=true;loginTimeout=60;}")
    private String evidenceJdbcUrl;

    @Value("${spring.datasource.evidence.hikari.username:sa1}")
    private String evidenceUsername;

    @Value("${spring.datasource.evidence.hikari.password:Cdit@mothai34nam}")
    private String evidencePassword;

    // 1. Core DataSource using Hikari
    @Primary
    @Bean(name = "dataSource")
    public HikariDataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("HikariPool-Core");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setJdbcUrl(coreJdbcUrl);
        ds.setUsername(coreUsername);
        ds.setPassword(corePassword);
        ds.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        ds.setMaximumPoolSize(30);
        ds.setMinimumIdle(2);
        ds.setInitializationFailTimeout(0);
        ds.setConnectionTimeout(10000);        // 10 seconds wait timeout
        ds.setMaxLifetime(60000);              // 60 seconds max lifetime (recycle before SQL Server drops idle socket)
        ds.setIdleTimeout(30000);               // 30 seconds idle timeout
        ds.setKeepaliveTime(15000);              // 15 seconds keepalive ping to prevent SQL Server socket drop
        ds.setValidationTimeout(2000);          // 2 seconds validation timeout
        ds.setLeakDetectionThreshold(120000);   // 2 minutes leak detection threshold
        return ds;
    }

    // 2. Evidence DataSource using Hikari
    @Bean(name = "evidenceDataSource")
    public HikariDataSource evidenceDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("HikariPool-Evidence");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setJdbcUrl(evidenceJdbcUrl);
        ds.setUsername(evidenceUsername);
        ds.setPassword(evidencePassword);
        ds.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        ds.setMaximumPoolSize(20);
        ds.setMinimumIdle(2);
        ds.setInitializationFailTimeout(0);
        ds.setConnectionTimeout(10000);        // 10 seconds wait timeout
        ds.setMaxLifetime(60000);              // 60 seconds max lifetime (recycle before SQL Server drops idle socket)
        ds.setIdleTimeout(30000);               // 30 seconds idle timeout
        ds.setKeepaliveTime(15000);              // 15 seconds keepalive ping to prevent SQL Server socket drop
        ds.setValidationTimeout(2000);          // 2 seconds validation timeout
        ds.setLeakDetectionThreshold(120000);   // 2 minutes leak detection threshold
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