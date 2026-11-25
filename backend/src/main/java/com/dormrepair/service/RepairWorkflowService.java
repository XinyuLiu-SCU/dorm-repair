package com.dormrepair.service;

import com.dormrepair.exception.WorkflowConflictException;
import com.dormrepair.repository.RepairOrderRepository.LockedOrder;
import com.dormrepair.security.CurrentActor;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RepairWorkflowService {
    private final ObjectProvider<RuntimeService> runtime;
    private final ObjectProvider<TaskService> tasks;
    private final boolean enabled;

    public RepairWorkflowService(ObjectProvider<RuntimeService> runtime, ObjectProvider<TaskService> tasks,
            @Value("${app.workflow.enabled:false}") boolean enabled) {
        this.runtime = runtime; this.tasks = tasks; this.enabled = enabled;
    }

    public void requireEnabled() {
        if (!enabled || runtime.getIfAvailable() == null || tasks.getIfAvailable() == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Workflow writes unavailable until migration and engine activation");
    }

    public String start(Long orderId, Long studentId) {
        requireEnabled();
        try {
            return runtime.getObject().startProcessInstanceByKey("dormRepair", "repair-order:" + orderId,
                    Map.of("orderId", orderId, "studentId", studentId.toString())).getId();
        } catch (org.flowable.common.engine.api.FlowableException ex) {
            throw new WorkflowConflictException("Unable to start repair workflow", ex);
        }
    }

    public Task current(LockedOrder order) {
        requireEnabled();
        var instance = runtime.getObject().createProcessInstanceQuery().processInstanceId(order.processInstanceId()).singleResult();
        if (instance == null || instance.isSuspended() || !("repair-order:" + order.id()).equals(instance.getBusinessKey()))
            throw new WorkflowConflictException("Order process missing, suspended or mismatched");
        var active = tasks.getObject().createTaskQuery().processInstanceId(order.processInstanceId()).list();
        if (active.size() != 1) throw new WorkflowConflictException("Expected exactly one active task");
        return active.get(0);
    }

    public void verifyInitial(LockedOrder order) {
        var task = current(order);
        boolean candidate = tasks.getObject().getIdentityLinksForTask(task.getId()).stream()
                .anyMatch(link -> "candidate".equals(link.getType()) && "ADMIN".equals(link.getGroupId()));
        if (!"adminReview".equals(task.getTaskDefinitionKey()) || !candidate || task.getAssignee() != null)
            throw new WorkflowConflictException("Initial review task mismatch");
    }

    public String node(String status) {
        return switch (status) {
            case "PENDING" -> "adminReview";
            case "APPROVED" -> "adminAssign";
            case "ASSIGNED" -> "repairStart";
            case "IN_PROGRESS" -> "repairFinish";
            case "WAITING_ACCEPTANCE" -> "studentAcceptance";
            case "REWORK" -> "reworkStart";
            default -> throw new WorkflowConflictException("Order is already terminal or has invalid status");
        };
    }

    public void validate(LockedOrder order, CurrentActor actor) {
        var task = current(order);
        if (!node(order.status()).equals(task.getTaskDefinitionKey()))
            throw new WorkflowConflictException("Business state and workflow node disagree");
        if ("ADMIN".equals(actor.role())) {
            boolean candidate = tasks.getObject().getIdentityLinksForTask(task.getId()).stream()
                    .anyMatch(link -> "candidate".equals(link.getType()) && "ADMIN".equals(link.getGroupId()));
            if (!candidate || (task.getAssignee() != null && !actor.id().toString().equals(task.getAssignee())))
                throw new WorkflowConflictException("Admin task assignment mismatch");
        } else if (!actor.id().toString().equals(task.getAssignee())) {
            throw new WorkflowConflictException("Task assignee mismatch");
        }
    }

    public void complete(LockedOrder before, CurrentActor actor, Map<String, Object> variables, String nextStatus) {
        try {
            validate(before, actor);
            var task = current(before);
            if ("ADMIN".equals(actor.role()) && task.getAssignee() == null)
                tasks.getObject().claim(task.getId(), actor.id().toString());
            tasks.getObject().complete(task.getId(), variables);
            if ("COMPLETED".equals(nextStatus) || "REJECTED".equals(nextStatus)) {
                if (runtime.getObject().createProcessInstanceQuery().processInstanceId(before.processInstanceId()).count() != 0)
                    throw new WorkflowConflictException("Workflow did not end");
            } else {
                var next = current(before);
                if (!node(nextStatus).equals(next.getTaskDefinitionKey()))
                    throw new WorkflowConflictException("Workflow advanced to unexpected node");
                String expected = switch (nextStatus) {
                    case "ASSIGNED", "IN_PROGRESS", "REWORK" -> variables.containsKey("repairerId")
                            ? variables.get("repairerId").toString() : before.repairerId().toString();
                    case "WAITING_ACCEPTANCE" -> before.studentId().toString();
                    default -> null;
                };
                if (expected != null && !expected.equals(next.getAssignee()))
                    throw new WorkflowConflictException("Next task assignee mismatch");
            }
        } catch (org.flowable.common.engine.api.FlowableException ex) {
            throw new WorkflowConflictException("Workflow task conflict", ex);
        }
    }
}
