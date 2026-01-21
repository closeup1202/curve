package com.project.curve.spring.infrastructure;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * ExecutorService의 우아한 종료를 지원하는 래퍼 클래스.
 * <p>
 * 애플리케이션 종료 시 실행 중인 작업이 완료될 때까지 대기하며,
 * 타임아웃 초과 시 강제 종료합니다.
 * <p>
 * <b>우아한 종료 절차:</b>
 * <ol>
 *   <li>새로운 작업 수락 중지 (shutdown())</li>
 *   <li>실행 중인 작업 완료 대기 (awaitTermination())</li>
 *   <li>타임아웃 초과 시 실행 중인 작업 중단 (shutdownNow())</li>
 * </ol>
 */
@Slf4j
public class GracefulExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    @Getter
    private final long terminationTimeoutSeconds;

    private volatile boolean isShutdown = false;

    /**
     * GracefulExecutorService를 생성합니다.
     *
     * @param delegate                  실제 ExecutorService
     * @param terminationTimeoutSeconds 종료 대기 시간 (초)
     */
    public GracefulExecutorService(ExecutorService delegate, long terminationTimeoutSeconds) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate ExecutorService must not be null");
        }
        if (terminationTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Termination timeout must be positive, but was: " + terminationTimeoutSeconds);
        }
        this.delegate = delegate;
        this.terminationTimeoutSeconds = terminationTimeoutSeconds;
    }

    /**
     * 기본 타임아웃(30초)으로 GracefulExecutorService를 생성합니다.
     *
     * @param delegate 실제 ExecutorService
     */
    public GracefulExecutorService(ExecutorService delegate) {
        this(delegate, 30);
    }

    @Override
    @PreDestroy
    public void shutdown() {
        if (isShutdown) {
            log.debug("ExecutorService already shutdown");
            return;
        }

        isShutdown = true;
        log.info("Initiating graceful shutdown of ExecutorService (timeout: {}s)", terminationTimeoutSeconds);

        // 1. 새로운 작업 수락 중지
        delegate.shutdown();

        try {
            // 2. 실행 중인 작업 완료 대기
            if (!delegate.awaitTermination(terminationTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("ExecutorService did not terminate within {}s, forcing shutdown", terminationTimeoutSeconds);

                // 3. 타임아웃 초과 - 강제 종료
                List<Runnable> remainingTasks = delegate.shutdownNow();

                if (!remainingTasks.isEmpty()) {
                    log.warn("Forced shutdown cancelled {} pending tasks", remainingTasks.size());
                }

                // 4. 강제 종료 후 짧은 대기
                if (!delegate.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("ExecutorService did not terminate even after shutdownNow()");
                }
            } else {
                log.info("ExecutorService shutdown gracefully");
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for ExecutorService shutdown", e);

            // 인터럽트 발생 - 즉시 강제 종료
            List<Runnable> remainingTasks = delegate.shutdownNow();
            log.warn("Shutdown interrupted, cancelled {} pending tasks", remainingTasks.size());

            // 인터럽트 상태 복원
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        isShutdown = true;
        log.warn("Forcing immediate shutdown of ExecutorService");
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }
}
