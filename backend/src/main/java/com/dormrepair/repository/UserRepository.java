package com.dormrepair.repository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class UserRepository {
 private final JdbcTemplate jdbc;
 public UserRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 public record User(Long id,String username,String passwordHash,String realName,String role) {}
 public Optional<User> find(String username) {
  return jdbc.query("select id, username, password_hash, real_name, role from sys_user where username = ?",(r,n)->new User(r.getLong("id"),r.getString("username"),r.getString("password_hash"),r.getString("real_name"),r.getString("role")),username).stream().findFirst();
 }
}
