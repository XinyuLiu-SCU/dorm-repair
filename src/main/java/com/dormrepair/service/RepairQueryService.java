package com.dormrepair.service;

import com.dormrepair.security.CurrentActor;
import com.dormrepair.exception.WorkflowConflictException;
import java.util.*;
import org.flowable.engine.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class RepairQueryService {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<RuntimeService> runtime;
    private final ObjectProvider<TaskService> tasks;
    private final ObjectProvider<HistoryService> history;
    private final boolean enabled;
    public RepairQueryService(JdbcTemplate jdbc, ObjectProvider<RuntimeService> runtime, ObjectProvider<TaskService> tasks,
            ObjectProvider<HistoryService> history, @Value("${app.workflow.enabled:false}") boolean enabled) {
        this.jdbc=jdbc; this.runtime=runtime; this.tasks=tasks; this.history=history; this.enabled=enabled;
    }
    public record Page<T>(List<T> items, int page, int size, long total) {}
    public record TaskView(String id, String key, String name, Long assigneeId) {}
    public record WorkflowView(Long orderId, String mode, String status, String processInstanceId,
            String currentNode, TaskView currentTask, boolean ended, List<String> allowedActions) {}
    public static void page(int page, int size) {
        if(page<0 || page>100000 || size<1 || size>100) throw new IllegalArgumentException("page must be 0..100000 and size 1..100");
    }
    private String scope(CurrentActor actor, List<Object> args, String prefix) {
        return switch(actor.role()) {
            case "ADMIN" -> "";
            case "STUDENT" -> { args.add(actor.id()); yield " and "+prefix+"student_id = ?"; }
            case "REPAIRER" -> { args.add(actor.id()); yield " and "+prefix+"repairer_id = ?"; }
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Role not permitted");
        };
    }
    private Map<String,Object> visible(Long id) {
        var args=new ArrayList<Object>();args.add(id);var actor=CurrentActor.current();
        var rows=jdbc.queryForList("select id, student_id, repairer_id, title, description, location, status, created_at, updated_at from repair_order where id = ?"+scope(actor,args,""),args.toArray());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found");
        return rows.get(0);
    }
    public Map<String,Object> detail(Long id) { return visible(id); }
    public Page<Map<String,Object>> records(Long id,int page,int size) {
        page(page,size);visible(id);
        long total=jdbc.queryForObject("select count(*) from repair_record where order_id = ?",Long.class,id);
        var rows=jdbc.queryForList("select id, order_id, repairer_id, description, created_at from repair_record where order_id = ? order by created_at asc, id asc limit ? offset ?",id,size,(long)page*size);
        return new Page<>(rows,page,size,total);
    }
    private void available() {
        if(!enabled || runtime.getIfAvailable()==null || tasks.getIfAvailable()==null || history.getIfAvailable()==null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Workflow queries unavailable until migration and activation");
    }
    private boolean terminal(String status) { return Set.of("COMPLETED","REJECTED").contains(status); }
    private String node(String status) {
        return switch(status) {case "PENDING"->"adminReview";case "APPROVED"->"adminAssign";case "ASSIGNED"->"repairStart";case "IN_PROGRESS"->"repairFinish";case "WAITING_ACCEPTANCE"->"studentAcceptance";case "REWORK"->"reworkStart";default->null;};
    }
    private List<String> actions(CurrentActor actor,String status,String taskAssignee,boolean adminCandidate,boolean legacy) {
        if(!legacy && "ADMIN".equals(actor.role()) && (!adminCandidate || taskAssignee!=null && !actor.id().toString().equals(taskAssignee))) return List.of();
        if(!legacy && !"ADMIN".equals(actor.role()) && !actor.id().toString().equals(taskAssignee)) return List.of();
        return switch(actor.role()+":"+status) {
            case "ADMIN:PENDING" -> List.of("approve","reject");case "ADMIN:APPROVED" -> List.of("assign");
            case "REPAIRER:ASSIGNED", "REPAIRER:REWORK" -> List.of("start");case "REPAIRER:IN_PROGRESS" -> List.of("finish");
            case "STUDENT:WAITING_ACCEPTANCE" -> List.of("accept");default -> List.of();
        };
    }
    public WorkflowView workflow(Long id) {
        var order=visible(id);available();var actor=CurrentActor.current();String status=order.get("status").toString();
        String pi=jdbc.queryForObject("select process_instance_id from repair_order where id = ?",String.class,id);
        boolean legacy=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from repair_order_legacy where order_id = ?)",Boolean.class,id));
        if(pi==null) {
            if(!legacy)throw new WorkflowConflictException("Missing workflow association");
            return new WorkflowView(id,"LEGACY",status,null,null,null,terminal(status),actions(actor,status,null,false,true));
        }
        if(legacy)throw new WorkflowConflictException("Legacy association mismatch");
        String businessKey="repair-order:"+id;
        if(terminal(status)) {
            var ended=history.getObject().createHistoricProcessInstanceQuery().processInstanceId(pi).singleResult();
            if(ended==null || ended.getEndTime()==null || ended.getDeleteReason()!=null || !businessKey.equals(ended.getBusinessKey())
                    || runtime.getObject().createProcessInstanceQuery().processInstanceId(pi).count()!=0)
                throw new WorkflowConflictException("Finished process history mismatch");
            String end="COMPLETED".equals(status)?"completedEnd":"rejectedEnd";
            if(history.getObject().createHistoricActivityInstanceQuery().processInstanceId(pi).activityId(end).finished().count()!=1)
                throw new WorkflowConflictException("Unexpected end event");
            return new WorkflowView(id,"FLOWABLE",status,pi,null,null,true,List.of());
        }
        var instance=runtime.getObject().createProcessInstanceQuery().processInstanceId(pi).singleResult();
        if(instance==null || !businessKey.equals(instance.getBusinessKey()))throw new WorkflowConflictException("Process missing or mismatched");
        var active=tasks.getObject().createTaskQuery().processInstanceId(pi).listPage(0,2);
        if(active.size()!=1 || !Objects.equals(node(status),active.get(0).getTaskDefinitionKey()))throw new WorkflowConflictException("Task node mismatch");
        var task=active.get(0);
        String expected= switch(status){case "ASSIGNED","IN_PROGRESS","REWORK"->Objects.toString(order.get("repairer_id"),null);case "WAITING_ACCEPTANCE"->order.get("student_id").toString();default->null;};
        if(expected!=null && !expected.equals(task.getAssignee()))throw new WorkflowConflictException("Task assignee mismatch");
        boolean candidate=tasks.getObject().getIdentityLinksForTask(task.getId()).stream().anyMatch(l->"candidate".equals(l.getType()) && "ADMIN".equals(l.getGroupId()));
        var allowed=instance.isSuspended()?List.<String>of():actions(actor,status,task.getAssignee(),candidate,false);
        return new WorkflowView(id,"FLOWABLE",status,pi,task.getTaskDefinitionKey(),new TaskView(task.getId(),task.getTaskDefinitionKey(),task.getName(),task.getAssignee()==null?null:Long.valueOf(task.getAssignee())),false,allowed);
    }
    public Page<Map<String,Object>> repairers(int page,int size) {
        CurrentActor.require("ADMIN");page(page,size);
        long total=jdbc.queryForObject("select count(*) from sys_user where role = 'REPAIRER'",Long.class);
        return new Page<>(jdbc.queryForList("select id, username, real_name from sys_user where role = 'REPAIRER' order by id limit ? offset ?",size,(long)page*size),page,size,total);
    }
    public Page<Map<String,Object>> pendingTasks(int page,int size) {
        page(page,size);var actor=CurrentActor.current();available();
        // Read-only query against the pinned Flowable 7.2 schema: one join and one count, no per-task lookups.
        var args=new ArrayList<Object>();
        String filters=" where t.suspension_state_ = 1 and e.suspension_state_ = 1 and e.business_key_ = 'repair-order:' || o.id::text and not exists(select 1 from repair_order_legacy l where l.order_id=o.id)"+scope(actor,args,"o.");
        if("ADMIN".equals(actor.role())) {
            filters+=" and ((o.status = 'PENDING' and t.task_def_key_ = 'adminReview') or (o.status = 'APPROVED' and t.task_def_key_ = 'adminAssign')) and exists(select 1 from act_ru_identitylink l where l.task_id_=t.id_ and l.type_='candidate' and l.group_id_='ADMIN') and (t.assignee_ is null or t.assignee_ = ?)";args.add(actor.id().toString());
        } else {
            filters+=" and t.assignee_ = ?";args.add(actor.id().toString());
            filters+="STUDENT".equals(actor.role())?" and o.status = 'WAITING_ACCEPTANCE' and t.task_def_key_ = 'studentAcceptance'":" and ((o.status = 'ASSIGNED' and t.task_def_key_ = 'repairStart') or (o.status = 'IN_PROGRESS' and t.task_def_key_ = 'repairFinish') or (o.status = 'REWORK' and t.task_def_key_ = 'reworkStart'))";
        }
        String from=" from act_ru_task t join repair_order o on o.process_instance_id=t.proc_inst_id_ join act_ru_execution e on e.id_=t.proc_inst_id_";
        long total=jdbc.queryForObject("select count(*)"+from+filters,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var rows=jdbc.queryForList("select t.id_ as task_id, t.task_def_key_ as task_key, t.name_ as task_name, t.assignee_ as assignee_id, o.id as order_id, o.title, o.status"+from+filters+" order by t.create_time_, t.id_ limit ? offset ?",args.toArray());
        return new Page<>(rows,page,size,total);
    }
}
