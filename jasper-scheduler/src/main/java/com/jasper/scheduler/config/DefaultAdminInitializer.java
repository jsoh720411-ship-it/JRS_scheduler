package com.jasper.scheduler.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 기동 시 admin 계정이 없으면 자동으로 생성합니다.
 * DDL에 미리 박아넣은 해시값을 신뢰하지 않고, 매번 PasswordEncoder로
 * 직접 인코딩하므로 "비밀번호가 안 맞는" 문제가 생기지 않습니다.
 *
 * 기본 계정: admin / admin1234
 * 운영 환경에서는 최초 기동 후 비밀번호를 변경하거나
 * 이 클래스를 비활성화하는 것을 권장합니다.
 */
@Component
public class DefaultAdminInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DefaultAdminInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.ADMIN_USER WHERE admin_id = ?",
            Integer.class, "admin"
        );

        if (count == null || count == 0) {
            String hash = passwordEncoder.encode("admin1234");
            jdbcTemplate.update(
                "INSERT INTO dbo.ADMIN_USER (admin_id, password_hash, admin_name, is_active) " +
                "VALUES (?, ?, ?, 1)",
                "admin", hash, "시스템 관리자"
            );
            System.out.println("[INIT] 기본 admin 계정이 생성되었습니다. (admin / admin1234)");
        } else {
            // 이미 있는 계정이지만, 비밀번호가 맞지 않는 경우를 대비해
            // 강제로 admin1234로 재설정하고 싶다면 아래 주석을 해제하세요.
            // String hash = passwordEncoder.encode("admin1234");
            // jdbcTemplate.update("UPDATE dbo.ADMIN_USER SET password_hash = ? WHERE admin_id = ?", hash, "admin");
            // System.out.println("[INIT] admin 비밀번호가 admin1234로 재설정되었습니다.");
        }
    }
}
