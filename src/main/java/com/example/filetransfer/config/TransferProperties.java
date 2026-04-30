package com.example.filetransfer.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 文件传输系统配置项。
 * 当前项目聚焦超大规模数据迁移，因此这里重点承载批次切分、执行吞吐和校验策略相关参数。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "transfer")
public class TransferProperties {

    /**
     * 最大并发数，决定文件复制线程池大小。
     */
    @Min(1)
    private int maxConcurrency = 4;

    /**
     * 单文件复制和哈希计算使用的缓冲区大小。
     */
    @Min(8192)
    private int bufferSize = 1024 * 1024;

    /**
     * 单文件失败后的最大重试次数。
     */
    @Min(0)
    private int maxRetries = 3;

    /**
     * 指数退避的初始等待时间。
     */
    @Min(100)
    private long initialRetryIntervalMillis = 1000L;

    /**
     * 全量哈希校验时使用的摘要算法。
     */
    @NotBlank
    private String hashAlgorithm = "SHA-256";

    /**
     * 断点续传临时文件目录。
     */
    @NotBlank
    private String tempDir = "./temp-transfer";

    /**
     * 进度持久化字节间隔。
     * 间隔越大，数据库写入越少；间隔越小，恢复点越精细。
     */
    @Min(1)
    private long progressSaveIntervalBytes = 64L * 1024 * 1024;

    /**
     * 单批次允许容纳的最大文件数。
     */
    @Min(100)
    private int scalableBatchFileCount = 10_000;

    /**
     * 单批次允许累计的最大字节数。
     */
    @Min(1)
    private long scalableBatchBytes = 8L * 1024 * 1024 * 1024;

    /**
     * 规划阶段批量入库时每组提交的记录数。
     * 过大容易推高单事务内存占用，过小则会降低插入吞吐。
     */
    @Min(100)
    private int scalablePersistChunkSize = 1_000;

    /**
     * 执行阶段每次从数据库拉取的批次数量。
     */
    @Min(1)
    private int scalableExecutionBatchPageSize = 4;

    /**
     * 执行阶段每次从数据库拉取的文件记录数量。
     */
    @Min(1)
    private int scalableExecutionFilePageSize = 1_000;

    /**
     * 批次查询接口的默认分页大小。
     */
    @Min(1)
    private int queryDefaultPageSize = 100;

    @Min(1)
    private int queryMaxPageSize = 1000;

    /**
     * 调度预抓取倍率。
     * 执行器每轮会拉取 batchPageSize * 该倍率数量的候选批次，再进行优先级排序。
     */
    @Min(1)
    private int schedulingPrefetchMultiplier = 4;

    /**
     * 执行线程池背压高水位百分比。
     * 当活跃线程数和队列占用达到该比例时，调度器会主动减速。
     */
    @Min(1)
    private int backpressureHighWatermarkPercent = 85;

    /**
     * 单任务允许的最大在途字节数。
     */
    @Min(1)
    private long maxInFlightBytes = 32L * 1024 * 1024 * 1024;

    /**
     * HOT 批次的字节阈值。
     */
    @Min(1)
    private long hotBatchBytesThreshold = 512L * 1024 * 1024;

    /**
     * WARM 批次的字节阈值。
     * 超过该阈值的批次会被视为 COLD。
     */
    @Min(1)
    private long warmBatchBytesThreshold = 4L * 1024 * 1024 * 1024;

    /**
     * HOT 批次的文件数阈值。
     */
    @Min(1)
    private int hotBatchFileCountThreshold = 2_000;

    /**
     * COLD 批次允许的最大并发数。
     */
    @Min(1)
    private int coldBatchConcurrencyLimit = 1;

    /**
     * HOT 批次每轮调度允许连续出队的数量。
     * 值越大，越倾向于优先清空小批次。
     */
    @Min(1)
    private int hotDispatchBurst = 4;

    /**
     * WARM 批次每轮调度允许连续出队的数量。
     */
    @Min(1)
    private int warmDispatchBurst = 2;

    /**
     * COLD 批次每轮调度允许连续出队的数量。
     * 通常保持较小，避免超大批次长期占满系统资源。
     */
    @Min(1)
    private int coldDispatchBurst = 1;

    /**
     * 背压触发后的调度等待时长。
     * 等待期间协调线程会周期性重新检查是否可以继续派发批次。
     */
    @Min(10)
    private long dispatchThrottleSleepMillis = 100L;

    /**
     * 规划阶段扫描流水线队列容量。
     */
    @Min(100)
    private int scanningPipelineQueueCapacity = 5_000;

    /**
     * 文件状态异步刷库批量大小。
     */
    @Min(1)
    private int stateFlushBatchSize = 500;

    /**
     * 文件状态异步刷库时间间隔，单位毫秒。
     */
    @Min(50)
    private long stateFlushIntervalMillis = 500L;

    /**
     * 是否启用 Redis 作为热状态层和热队列层。
     */
    private boolean redisEnabled = false;

    /**
     * Redis 键前缀。
     */
    @NotBlank
    private String redisKeyPrefix = "file-transfer";

    /**
     * Redis 热状态过期时间，单位秒。
     */
    @Min(60)
    private long redisStateTtlSeconds = 172800L;

    @AssertTrue(message = "transfer.query-max-page-size must be greater than or equal to transfer.query-default-page-size")
    public boolean isQueryPageSizeRangeValid() {
        return queryMaxPageSize >= queryDefaultPageSize;
    }

    @AssertTrue(message = "transfer.warm-batch-bytes-threshold must be greater than or equal to transfer.hot-batch-bytes-threshold")
    public boolean isBatchTemperatureRangeValid() {
        return warmBatchBytesThreshold >= hotBatchBytesThreshold;
    }

    @AssertTrue(message = "transfer.cold-batch-concurrency-limit must be less than or equal to transfer.max-concurrency")
    public boolean isColdBatchConcurrencyValid() {
        return coldBatchConcurrencyLimit <= maxConcurrency;
    }
}
