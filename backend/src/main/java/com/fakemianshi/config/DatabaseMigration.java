package com.fakemianshi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 轻量数据库迁移：schema.sql 使用 CREATE TABLE IF NOT EXISTS（幂等），
 * 已存在的旧库不会新增列。这里在启动时检测缺失列/表并补齐，
 * 保证旧版本数据库（如 main 分支的 Java 版）升级后仍可用。
 */
@Slf4j
@Component
public class DatabaseMigration {

    private final DataSource dataSource;

    public DatabaseMigration(DataSource dataSource) {
        this.dataSource = dataSource;
        migrate();
    }

    private void migrate() {
        try (Connection conn = dataSource.getConnection()) {
            ensureColumn(conn, "interview_project", "target_position", "VARCHAR(200)");

            // session_analysis 后续新增列（旧库升级补齐）
            ensureColumn(conn, "session_analysis", "overall_level", "VARCHAR(200)");
            ensureColumn(conn, "session_analysis", "expected_salary_range", "VARCHAR(100)");
            ensureColumn(conn, "session_analysis", "personality_summary", "VARCHAR(5000)");
            ensureColumn(conn, "session_analysis", "character_traits", "VARCHAR(3000)");
            ensureColumn(conn, "session_analysis", "character_defects", "VARCHAR(5000)");
            ensureColumn(conn, "session_analysis", "communication_evaluation", "VARCHAR(5000)");
            ensureColumn(conn, "session_analysis", "humor_summary", "VARCHAR(500)");
            ensureColumn(conn, "session_analysis", "improvement_plan", "VARCHAR(10000)");

            // question_analysis 后续新增列（旧库升级补齐）
            ensureColumn(conn, "question_analysis", "accuracy_reason", "VARCHAR(1000)");
            ensureColumn(conn, "question_analysis", "depth_reason", "VARCHAR(1000)");
            ensureColumn(conn, "question_analysis", "clarity_reason", "VARCHAR(1000)");
            ensureColumn(conn, "question_analysis", "fluency_reason", "VARCHAR(1000)");
            ensureColumn(conn, "question_analysis", "tone_evaluation", "VARCHAR(255)");
            ensureColumn(conn, "question_analysis", "category", "VARCHAR(200)");
            ensureColumn(conn, "question_analysis", "difficulty", "VARCHAR(100)");
            ensureColumn(conn, "question_analysis", "focus_point", "VARCHAR(1000)");
            ensureColumn(conn, "question_analysis", "answer_approach", "VARCHAR(3000)");
            ensureColumn(conn, "question_analysis", "example", "VARCHAR(3000)");
            ensureColumn(conn, "question_analysis", "improvement_suggestion", "VARCHAR(5000)");

            log.info("数据库迁移检查完成");
        } catch (Exception e) {
            log.warn("数据库迁移失败（可忽略，后续启动会重试）: {}", e.getMessage());
        }
    }

    /** 检测表是否存在指定列，缺失则 ALTER TABLE 补齐 */
    private void ensureColumn(Connection conn, String table, String column, String ddl) throws Exception {
        if (!tableExists(conn, table)) {
            return;
        }
        boolean has = false;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    has = true;
                    break;
                }
            }
        }
        if (!has) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
                log.info("已为表 {} 添加缺失列 {}", table, column);
            }
        }
    }

    private boolean tableExists(Connection conn, String table) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }
}
