package com.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {

    // 1. Core Primary DataSource bound 100% to spring.datasource.core.hikari in application.properties
    @Primary
    @Bean(name = "dataSource")
    @ConfigurationProperties(prefix = "spring.datasource.core.hikari")
    public HikariDataSource dataSource() {
        return new HikariDataSource();
    }

    // 2. Evidence Secondary DataSource bound 100% to spring.datasource.evidence.hikari in application.properties
    @Bean(name = "evidenceDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.evidence.hikari")
    public HikariDataSource evidenceDataSource() {
        return new HikariDataSource();
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