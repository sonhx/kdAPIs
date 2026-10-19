package com.ptit.qa.ims.repository;

import com.ptit.qa.ims.entity.TrainingProgram;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TrainingProgramRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<TrainingProgram> rowMapper = (rs, rowNum) -> {
        TrainingProgram p = new TrainingProgram();
        p.setId(rs.getLong("id"));
        p.setName(rs.getNString("name"));
        p.setUrl(rs.getString("url"));
        p.setCode(rs.getString("code"));
        Timestamp ts = rs.getTimestamp("updated_at");
        if (ts != null) {
            p.setUpdatedAt(ts.toLocalDateTime());
        }
        return p;
    };

    public Optional<TrainingProgram> findByUrl(String url) {
        String sql = "SELECT id, name, url, code, updated_at FROM dbo.training_programs WHERE url = ?";
        try {
            TrainingProgram p = jdbcTemplate.queryForObject(sql, rowMapper, url);
            return Optional.ofNullable(p);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<TrainingProgram> findById(Long id) {
        String sql = "SELECT id, name, url, code, updated_at FROM dbo.training_programs WHERE id = ?";
        try {
            TrainingProgram p = jdbcTemplate.queryForObject(sql, rowMapper, id);
            return Optional.ofNullable(p);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Long save(TrainingProgram program) {
        if (program.getUpdatedAt() == null) {
            program.setUpdatedAt(LocalDateTime.now());
        }

        Long existingId = program.getId();
        if (existingId == null && program.getUrl() != null) {
            Optional<TrainingProgram> existing = findByUrl(program.getUrl());
            if (existing.isPresent()) {
                existingId = existing.get().getId();
                program.setId(existingId);
            }
        }

        if (existingId != null) {
            String updateSql = "UPDATE dbo.training_programs SET name = ?, code = ?, updated_at = SYSDATETIME() WHERE id = ?";
            Long finalId = existingId;
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(updateSql);
                ps.setNString(1, program.getName());
                ps.setString(2, program.getCode());
                ps.setLong(3, finalId);
                return ps;
            });
            return existingId;
        } else {
            String insertSql = "INSERT INTO dbo.training_programs (name, url, code, updated_at) VALUES (?, ?, ?, SYSDATETIME())";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                ps.setNString(1, program.getName());
                ps.setString(2, program.getUrl());
                ps.setString(3, program.getCode());
                return ps;
            }, keyHolder);

            Number generatedKey = keyHolder.getKey();
            Long id = (generatedKey != null) ? generatedKey.longValue() : null;
            program.setId(id);
            return id;
        }
    }

    public List<TrainingProgram> findAll() {
        String sql = "SELECT id, name, url, code, updated_at FROM dbo.training_programs ORDER BY id ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.training_programs", Integer.class);
        return (count != null) ? count : 0;
    }
}
