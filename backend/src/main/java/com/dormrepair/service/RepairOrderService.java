package com.dormrepair.service;

import com.dormrepair.exception.WorkflowConflictException;
import com.dormrepair.repository.RepairOrderRepository;
import com.dormrepair.repository.RepairOrderRepository.LockedOrder;
import com.dormrepair.security.CurrentActor;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepairOrderService {
    private final RepairOrderRepository repository;
    private final RepairWorkflowService workflow;

    public RepairOrderService(RepairOrderRepository repository, RepairWorkflowService workflow) {
        this.repository = repository; this.workflow = workflow;
    }

    public List<Map<String, Object>> getVisibleRepairOrders(Long ignoredId, String ignoredRole) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        var actor = CurrentActor.require(jwt.getClaimAsString("scope"));
        return repository.findVisible(actor.id(), actor.role());
    }

    public List<Map<String,Object>> getVisibleRepairOrdersPage(int page,int size,String status) {
        RepairQueryService.page(page,size);var actor=CurrentActor.current();return repository.findVisiblePage(actor.id(),actor.role(),page,size,status);
    }

    public List<Map<String, Object>> getAllRepairOrders() {
        CurrentActor.require("ADMIN");
        return repository.findAll();
    }

    @Transactional
    public Long createRepairOrder(Long ignoredStudentId, String title, String description, String location) {
        var actor = CurrentActor.require("STUDENT");
        workflow.requireEnabled();
        if (!repository.existsStudent(actor.id())) throw new IllegalArgumentException("Student not found");
        if (title == null || title.isBlank() || title.length() > 100 || description == null || description.isBlank()
                || location == null || location.isBlank() || location.length() > 200)
            throw new IllegalArgumentException("Invalid repair order fields");
        Long id = repository.save(actor.id(), title, description, location);
        String instance = workflow.start(id, actor.id());
        repository.linkProcess(id, instance);
        workflow.verifyInitial(repository.lock(id));
        return id;
    }

    private LockedOrder prepare(Long id, CurrentActor actor, String... allowed) {
        workflow.requireEnabled();
        var order = repository.lock(id);
        if ("STUDENT".equals(actor.role())) actor.owns(order.studentId());
        if ("REPAIRER".equals(actor.role())) actor.owns(order.repairerId());
        if (!List.of(allowed).contains(order.status())) throw new WorkflowConflictException("Current state does not allow this operation");
        boolean legacy = repository.isLegacy(id);
        if (order.processInstanceId() == null) {
            if (!legacy) throw new WorkflowConflictException("New order is missing its workflow");
        } else {
            if (legacy) throw new WorkflowConflictException("Legacy order unexpectedly linked to workflow");
            workflow.validate(order, actor);
        }
        return order;
    }

    private void changed(int count) {
        if (count != 1) throw new WorkflowConflictException("Order changed concurrently or state is invalid");
    }

    private void advance(LockedOrder order, CurrentActor actor, Map<String, Object> vars, String next) {
        if (order.processInstanceId() != null) workflow.complete(order, actor, vars, next);
    }

    @Transactional
    public void approveRepairOrder(Long id) {
        var actor = CurrentActor.require("ADMIN"); var order = prepare(id, actor, "PENDING");
        changed(repository.approve(id)); advance(order, actor, Map.of("reviewApproved", true), "APPROVED");
    }

    @Transactional
    public void rejectRepairOrder(Long id) {
        var actor = CurrentActor.require("ADMIN"); var order = prepare(id, actor, "PENDING");
        changed(repository.reject(id)); advance(order, actor, Map.of("reviewApproved", false), "REJECTED");
    }

    @Transactional
    public void assignRepairOrder(Long id, Long repairerId) {
        var actor = CurrentActor.require("ADMIN"); var order = prepare(id, actor, "APPROVED");
        if (repairerId == null || !repository.existsRepairer(repairerId)) throw new IllegalArgumentException("Repairer not found");
        changed(repository.assign(id, repairerId));
        advance(order, actor, Map.of("repairerId", repairerId.toString()), "ASSIGNED");
    }

    @Transactional
    public void startRepair(Long id, Long ignoredRepairerId) {
        var actor = CurrentActor.require("REPAIRER"); var order = prepare(id, actor, "ASSIGNED", "REWORK");
        changed(repository.startRepair(id, actor.id())); advance(order, actor, Map.of(), "IN_PROGRESS");
    }

    @Transactional
    public void finishRepair(Long id, Long ignoredRepairerId, String description) {
        var actor = CurrentActor.require("REPAIRER"); var order = prepare(id, actor, "IN_PROGRESS");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Repair description required");
        changed(repository.finishRepair(id, actor.id()));
        repository.addRepairRecord(id, actor.id(), description);
        advance(order, actor, Map.of(), "WAITING_ACCEPTANCE");
    }

    @Transactional
    public void acceptRepair(Long id, Long ignoredStudentId, boolean approved) {
        var actor = CurrentActor.require("STUDENT"); var order = prepare(id, actor, "WAITING_ACCEPTANCE");
        changed(repository.acceptRepair(id, actor.id(), approved));
        advance(order, actor, Map.of("acceptanceApproved", approved), approved ? "COMPLETED" : "REWORK");
    }
}
