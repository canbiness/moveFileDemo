package com.example.filetransfer.controller;

import com.example.filetransfer.exception.TransferException;
import org.slf4j.MDC;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.example.filetransfer.config.RequestIdFilter.MDC_KEY;

/**
 * 全局异常处理器。
 * 负责把系统内部异常转换为统一的 HTTP 错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理迁移领域业务异常。
     *
     * @param ex 业务异常
     * @return RFC 7807 风格错误响应
     */
    @ExceptionHandler(TransferException.class)
    public ProblemDetail handleTransferException(TransferException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Transfer error");
        detail.setDetail(ex.getMessage());
        addRequestId(detail);
        return detail;
    }

    /**
     * 处理参数校验失败异常。
     *
     * @param ex 参数校验异常
     * @return RFC 7807 风格错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Validation error");
        detail.setDetail(ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("Invalid request"));
        addRequestId(detail);
        return detail;
    }

    /**
     * 如果当前线程上下文中存在 requestId，则写入错误响应。
     *
     * @param detail 错误响应对象
     */
    private void addRequestId(ProblemDetail detail) {
        String requestId = MDC.get(MDC_KEY);
        if (requestId != null && !requestId.isBlank()) {
            detail.setProperty("requestId", requestId);
        }
    }
}
