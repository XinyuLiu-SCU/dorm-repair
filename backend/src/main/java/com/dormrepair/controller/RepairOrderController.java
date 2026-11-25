
package com.dormrepair.controller;

import com.dormrepair.service.RepairOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repair-orders")
public class RepairOrderController {

    private final RepairOrderService repairOrderService;

    public RepairOrderController(RepairOrderService repairOrderService) {
        this.repairOrderService = repairOrderService;
    }

    private Jwt actor() { return (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal(); }
    private Long actorId() { return Long.valueOf(actor().getSubject()); }

    // 查询所有报修单
    @GetMapping
    public List<Map<String, Object>> getAllRepairOrders(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="100") int size, @RequestParam(required=false) String status) {
        com.dormrepair.service.RepairQueryService.page(page,size);
        if(page==0 && size==100 && status==null) return repairOrderService.getVisibleRepairOrders(actorId(), actor().getClaimAsString("scope"));
        return repairOrderService.getVisibleRepairOrdersPage(page,size,status);
    }

    // 提交报修申请
    @PostMapping
    public Map<String, Object> createRepairOrder(
            @Valid @RequestBody CreateRepairOrderRequest request) {

        Long id = repairOrderService.createRepairOrder(
                actorId(),
                request.title(),
                request.description(),
                request.location()
        );

        return Map.of(
                "message", "报修申请提交成功",
                "id", id
        );
    }

    // 审核通过报修单
    @PostMapping("/{id}/approve")
    public Map<String, Object> approveRepairOrder(
            @PathVariable Long id) {

        repairOrderService.approveRepairOrder(id);

        return Map.of(
                "message", "报修单审核通过",
                "id", id
        );
    }

    // 驳回报修单
    @PostMapping("/{id}/reject")
    public Map<String, Object> rejectRepairOrder(
            @PathVariable Long id) {

        repairOrderService.rejectRepairOrder(id);

        return Map.of(
                "message", "报修单已驳回",
                "id", id
        );
    }

    // 管理员派单
    @PostMapping("/{id}/assign")
    public Map<String, Object> assignRepairOrder(
            @PathVariable Long id,
            @Valid @RequestBody AssignRepairOrderRequest request) {

        repairOrderService.assignRepairOrder(id, request.repairerId());

        return Map.of(
                "message", "维修人员分配成功",
                "id", id,
                "repairerId", request.repairerId()
        );
    }

    // 派单请求参数
    public record AssignRepairOrderRequest(
            @NotNull(message = "维修人员ID不能为空")
            Long repairerId
    ) {}



    // 维修人员开始维修
    @PostMapping("/{id}/start")
    public Map<String, Object> startRepair(
            @PathVariable Long id,
            @RequestBody(required = false) StartRepairRequest request) {

        repairOrderService.startRepair(id, actorId());

        return Map.of(
                "message", "维修任务已开始",
                "id", id
        );
    }

    // 开始维修请求参数
    public record StartRepairRequest(
            @NotNull(message = "维修人员ID不能为空")
            Long repairerId
    ) {}

    // 维修人员提交维修结果
    @PostMapping("/{id}/finish")
    public Map<String, Object> finishRepair(
            @PathVariable Long id,
            @Valid @RequestBody FinishRepairRequest request) {

        repairOrderService.finishRepair(
                id,
                actorId(),
                request.description()
        );

        return Map.of(
                "message", "维修结果提交成功，等待学生验收",
                "id", id
        );
    }

    // 提交维修结果请求参数
    public record FinishRepairRequest(
            Long repairerId,

            @NotBlank(message = "维修说明不能为空")
            String description
    ) {}


    // 接收前端提交的数据
    public record CreateRepairOrderRequest(

            
            Long studentId,

            @NotBlank(message = "报修标题不能为空")
            @Size(max = 100, message = "报修标题不能超过100个字符")
            String title,

            @NotBlank(message = "报修描述不能为空")
            String description,

            @NotBlank(message = "报修位置不能为空")
            @Size(max = 200, message = "报修位置不能超过200个字符")
            String location
    ) {}

    // 学生验收维修结果
    @PostMapping("/{id}/accept")
    public Map<String, Object> acceptRepair(
            @PathVariable Long id,
            @Valid @RequestBody AcceptRepairRequest request) {

        repairOrderService.acceptRepair(
                id,
                actorId(),
                request.approved()
        );

        return Map.of(
                "message", request.approved()
                        ? "验收通过，报修完成"
                        : "验收未通过，已要求返工",
                "id", id
        );
    }

    // 验收请求参数
    public record AcceptRepairRequest(
            
            Long studentId,

            @NotNull(message = "请选择验收结果")
            Boolean approved
    ) {}

}
