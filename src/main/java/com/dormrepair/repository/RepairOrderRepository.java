
package com.dormrepair.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class RepairOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public RepairOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record LockedOrder(Long id, Long studentId, Long repairerId, String status, String processInstanceId) {}

    public LockedOrder lock(Long id) {
        return jdbcTemplate.query("select id, student_id, repairer_id, status, process_instance_id from repair_order where id = ? for update",
                (r, n) -> new LockedOrder(r.getLong("id"), r.getLong("student_id"),
                        (Long) r.getObject("repairer_id"), r.getString("status"), r.getString("process_instance_id")), id)
                .stream().findFirst().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found"));
    }

    public boolean isLegacy(Long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists(select 1 from repair_order_legacy where order_id = ?)", Boolean.class, id));
    }

    public void linkProcess(Long id, String instance) {
        if (jdbcTemplate.update("update repair_order set process_instance_id = ? where id = ? and process_instance_id is null", instance, id) != 1)
            throw new com.dormrepair.exception.WorkflowConflictException("Process already linked");
    }

    public List<Map<String, Object>> findVisible(Long id, String role) {
        if ("ADMIN".equals(role)) return findAll();
        String column = switch(role) { case "STUDENT" -> "student_id"; case "REPAIRER" -> "repairer_id"; default -> throw new IllegalArgumentException("Invalid role"); };
        return jdbcTemplate.queryForList("select id, student_id, repairer_id, title, description, location, status, created_at, updated_at from repair_order where " + column + " = ? order by id desc limit 100", id);
    }

    public List<Map<String,Object>> findVisiblePage(Long id,String role,int page,int size,String status) {
        var args=new java.util.ArrayList<Object>();String where=" where 1=1";
        if (!"ADMIN".equals(role)) { where += "STUDENT".equals(role)?" and student_id = ?":" and repairer_id = ?";args.add(id); }
        if(status!=null) {
            if(!java.util.Set.of("PENDING","APPROVED","ASSIGNED","IN_PROGRESS","WAITING_ACCEPTANCE","REWORK","COMPLETED","REJECTED").contains(status)) throw new IllegalArgumentException("Invalid status");
            where+=" and status = ?";args.add(status);
        }
        args.add(size);args.add((long)page*size);
        return jdbcTemplate.queryForList("select id, student_id, repairer_id, title, description, location, status, created_at, updated_at from repair_order"+where+" order by id desc limit ? offset ?",args.toArray());
    }

    // 查询所有报修单
    public List<Map<String, Object>> findAll() {
        String sql = """
                select id, student_id, title, description,
                       location, status, created_at, updated_at
                from repair_order
                order by id desc limit 100
                """;

        return jdbcTemplate.queryForList(sql);
    }

    // 检查是否存在指定学生
    public boolean existsStudent(Long studentId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_user where id = ? and role = 'STUDENT'",
                Integer.class,
                studentId
        );

        return count != null && count > 0;
    }

    // 保存新的报修单
    public Long save(Long studentId,
                     String title,
                     String description,
                     String location) {

        String sql = """
                insert into repair_order
                    (student_id, title, description, location)
                values (?, ?, ?, ?)
                returning id
                """;

        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                studentId,
                title,
                description,
                location
        );
    }

    // 审核通过：仅允许待审核的报修单进入已审核状态
    public int approve(Long orderId) {
        String sql = """
            update repair_order
            set status = 'APPROVED',
                updated_at = current_timestamp
            where id = ? and status = 'PENDING'
            """;

        return jdbcTemplate.update(sql, orderId);
    }

    // 审核驳回：仅允许驳回待审核的报修单
    public int reject(Long orderId) {
        String sql = """
            update repair_order
            set status = 'REJECTED',
                updated_at = current_timestamp
            where id = ? and status = 'PENDING'
            """;

        return jdbcTemplate.update(sql, orderId);
    }

    // 检查维修人员是否存在
    public boolean existsRepairer(Long repairerId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_user where id = ? and role = 'REPAIRER'",
                Integer.class,
                repairerId
        );

        return count != null && count > 0;
    }

    // 分配维修人员
    public int assign(Long orderId, Long repairerId) {
        String sql = """
            update repair_order
            set repairer_id = ?,
                status = 'ASSIGNED',
                updated_at = current_timestamp
            where id = ? and status = 'APPROVED'
            """;

        return jdbcTemplate.update(sql, repairerId, orderId);
    }

    // 维修人员开始维修
    public int startRepair(Long orderId, Long repairerId) {
        String sql = """
            update repair_order
            set status = 'IN_PROGRESS',
                updated_at = current_timestamp
            where id = ?
              and repairer_id = ?
              and status in ('ASSIGNED', 'REWORK')
            """;

        return jdbcTemplate.update(sql, orderId, repairerId);
    }

    // 保存维修记录
    public void addRepairRecord(
            Long orderId, Long repairerId, String description) {

        String sql = """
                insert into repair_record
                    (order_id, repairer_id, description)
                values (?, ?, ?)
                """;

        jdbcTemplate.update(sql, orderId, repairerId, description);
    }

    // 提交维修结果，等待学生验收
    public int finishRepair(Long orderId, Long repairerId) {
        String sql = """
                update repair_order
                set status = 'WAITING_ACCEPTANCE',
                    updated_at = current_timestamp
                where id = ?
                  and repairer_id = ?
                  and status = 'IN_PROGRESS'
                """;

        return jdbcTemplate.update(sql, orderId, repairerId);
    }

    // 学生验收：通过或要求返工
    public int acceptRepair(
            Long orderId, Long studentId, boolean approved) {

        String status = approved ? "COMPLETED" : "REWORK";

        String sql = """
                update repair_order
                set status = ?,
                    updated_at = current_timestamp
                where id = ?
                  and student_id = ?
                  and status = 'WAITING_ACCEPTANCE'
                """;

        return jdbcTemplate.update(sql, status, orderId, studentId);
    }



}
