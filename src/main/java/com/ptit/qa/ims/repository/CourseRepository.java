package com.ptit.qa.ims.repository;

import com.ptit.qa.ims.entity.Course;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

@Repository
public class CourseRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Course> rowMapper = (rs, rowNum) -> {
        Course c = new Course();
        c.setId(rs.getLong("id"));
        c.setProgramId(rs.getLong("program_id"));
        c.setCourseCode(rs.getString("course_code"));
        c.setCourseName(rs.getNString("course_name"));
        int credits = rs.getInt("credits");
        if (!rs.wasNull()) {
            c.setCredits(credits);
        }
        Timestamp ts = rs.getTimestamp("updated_at");
        if (ts != null) {
            c.setUpdatedAt(ts.toLocalDateTime());
        }
        return c;
    };

    public void deleteByProgramId(Long programId) {
        String sql = "DELETE FROM dbo.courses WHERE program_id = ?";
        jdbcTemplate.update(sql, programId);
    }

    public void saveAll(Long programId, List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return;
        }

        String insertSql = "INSERT INTO dbo.courses (program_id, course_code, course_name, credits, updated_at) " +
                "VALUES (?, ?, ?, ?, SYSDATETIME())";

        jdbcTemplate.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Course course = courses.get(i);
                ps.setLong(1, programId);

                if (course.getCourseCode() != null && !course.getCourseCode().isBlank()) {
                    ps.setString(2, course.getCourseCode());
                } else {
                    ps.setNull(2, Types.VARCHAR);
                }

                ps.setNString(3, course.getCourseName());

                if (course.getCredits() != null) {
                    ps.setInt(4, course.getCredits());
                } else {
                    ps.setNull(4, Types.INTEGER);
                }
            }

            @Override
            public int getBatchSize() {
                return courses.size();
            }
        });
    }

    public List<Course> findByProgramId(Long programId) {
        String sql = "SELECT id, program_id, course_code, course_name, credits, updated_at FROM dbo.courses WHERE program_id = ? ORDER BY id ASC";
        return jdbcTemplate.query(sql, rowMapper, programId);
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.courses", Integer.class);
        return (count != null) ? count : 0;
    }

    public int countByProgramId(Long programId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.courses WHERE program_id = ?", Integer.class, programId);
        return (count != null) ? count : 0;
    }
}
