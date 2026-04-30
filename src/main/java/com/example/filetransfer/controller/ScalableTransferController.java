package com.example.filetransfer.controller;

import com.example.filetransfer.config.TransferProperties;
import com.example.filetransfer.dto.CreateScalableTransferPlanRequest;
import com.example.filetransfer.dto.ScalableTransferActionResponse;
import com.example.filetransfer.dto.ScalableTransferBatchPageResponse;
import com.example.filetransfer.dto.ScalableTransferMetricsResponse;
import com.example.filetransfer.dto.ScalableTransferPlanResponse;
import com.example.filetransfer.dto.ScalableTransferTaskSummaryResponse;
import com.example.filetransfer.exception.TransferException;
import com.example.filetransfer.service.ScalableTransferExecutionService;
import com.example.filetransfer.service.ScalableTransferPlannerService;
import com.example.filetransfer.service.ScalableTransferQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大规模文件迁移 REST 控制器。
 * 对外提供任务规划、执行控制、摘要查询、指标查询和批次分页查询能力。
 */
@Slf4j
@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class ScalableTransferController {

    /** 用于读取分页默认值和上限等控制参数。 */
    private final TransferProperties transferProperties;

    /** 负责后台规划任务。 */
    private final ScalableTransferPlannerService scalableTransferPlannerService;

    /** 负责执行、暂停、恢复、取消等动作。 */
    private final ScalableTransferExecutionService scalableTransferExecutionService;

    /** 负责查询摘要、指标和批次分页。 */
    private final ScalableTransferQueryService scalableTransferQueryService;

    /**
     * 创建迁移计划。
     * 该接口只负责接收请求和快速返回，真正的目录扫描与切批在后台异步执行。
     *
     * @param request 创建计划请求
     * @return 计划创建响应
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScalableTransferPlanResponse createPlan(@Valid @RequestBody CreateScalableTransferPlanRequest request) {
        log.info("Received create-plan request, sourcePath={}, targetPath={}, verificationMode={}",
                request.sourcePath(), request.targetPath(), request.verificationMode());
        return scalableTransferPlannerService.createPlan(
                request.sourcePath(),
                request.targetPath(),
                request.verificationMode()
        );
    }

    /**
     * 查询任务摘要。
     *
     * @param id 任务 ID
     * @return 任务摘要
     */
    @GetMapping("/{id}")
    public ScalableTransferTaskSummaryResponse getSummary(@PathVariable String id) {
        log.debug("Received task summary request, taskId={}", id);
        return scalableTransferQueryService.getSummary(id);
    }

    /**
     * 查询任务运行指标。
     *
     * @param id 任务 ID
     * @return 任务运行指标
     */
    @GetMapping("/{id}/metrics")
    public ScalableTransferMetricsResponse getMetrics(@PathVariable String id) {
        log.debug("Received task metrics request, taskId={}", id);
        return scalableTransferQueryService.getMetrics(id);
    }

    /**
     * 启动任务执行。
     *
     * @param id 任务 ID
     * @return 执行动作响应
     */
    @PostMapping("/{id}/execute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScalableTransferActionResponse execute(@PathVariable String id) {
        log.info("Received execute request, taskId={}", id);
        return scalableTransferExecutionService.execute(id);
    }

    /**
     * 请求暂停任务。
     *
     * @param id 任务 ID
     * @return 暂停动作响应
     */
    @PostMapping("/{id}/pause")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScalableTransferActionResponse pause(@PathVariable String id) {
        log.info("Received pause request, taskId={}", id);
        return scalableTransferExecutionService.pause(id);
    }

    /**
     * 请求恢复任务。
     *
     * @param id 任务 ID
     * @return 恢复动作响应
     */
    @PostMapping("/{id}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScalableTransferActionResponse resume(@PathVariable String id) {
        log.info("Received resume request, taskId={}", id);
        return scalableTransferExecutionService.resume(id);
    }

    /**
     * 请求取消任务。
     *
     * @param id 任务 ID
     * @return 取消动作响应
     */
    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScalableTransferActionResponse cancel(@PathVariable String id) {
        log.info("Received cancel request, taskId={}", id);
        return scalableTransferExecutionService.cancel(id);
    }

    /**
     * 分页查询任务批次。
     *
     * @param id 任务 ID
     * @param page 页码
     * @param size 页大小，可为空
     * @return 批次分页结果
     */
    @GetMapping("/{id}/batches")
    public ScalableTransferBatchPageResponse getBatches(@PathVariable String id,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(required = false) Integer size) {
        log.debug("Received batch-page request, taskId={}, page={}, requestedSize={}", id, page, size);
        // 页码不允许为负数，避免出现无意义查询。
        if (page < 0) {
            throw new TransferException("page must be greater than or equal to 0");
        }
        // 如果调用方未传 size，则使用系统默认分页大小。
        int resolvedSize = size == null ? transferProperties.getQueryDefaultPageSize() : size;
        // 页大小至少为 1，否则没有查询意义。
        if (resolvedSize < 1) {
            throw new TransferException("size must be greater than 0");
        }
        // 页大小不能超过系统上限，避免一次性拉取过多数据压垮数据库和内存。
        if (resolvedSize > transferProperties.getQueryMaxPageSize()) {
            throw new TransferException("size must be less than or equal to " + transferProperties.getQueryMaxPageSize());
        }
        return scalableTransferQueryService.getBatches(id, page, resolvedSize);
    }
}
