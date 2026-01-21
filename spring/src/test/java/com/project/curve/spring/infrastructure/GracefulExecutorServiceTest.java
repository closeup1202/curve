package com.project.curve.spring.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GracefulExecutorService 테스트")
class GracefulExecutorServiceTest {

    @Nested
    @DisplayName("생성자 테스트")
    class ConstructorTest {

        @Test
        @DisplayName("기본 타임아웃으로 생성할 수 있다")
        void createWithDefaultTimeout() {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);

            // When
            GracefulExecutorService executor = new GracefulExecutorService(delegate);

            // Then
            assertThat(executor.getTerminationTimeoutSeconds()).isEqualTo(30);
            executor.shutdown();
        }

        @Test
        @DisplayName("커스텀 타임아웃으로 생성할 수 있다")
        void createWithCustomTimeout() {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);

            // When
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 10);

            // Then
            assertThat(executor.getTerminationTimeoutSeconds()).isEqualTo(10);
            executor.shutdown();
        }

        @Test
        @DisplayName("null delegate는 예외를 발생시킨다")
        void createWithNullDelegate_shouldThrowException() {
            // When & Then
            assertThatThrownBy(() -> new GracefulExecutorService(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        }

        @Test
        @DisplayName("0 이하의 타임아웃은 예외를 발생시킨다")
        void createWithInvalidTimeout_shouldThrowException() {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);

            // When & Then
            assertThatThrownBy(() -> new GracefulExecutorService(delegate, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");

            delegate.shutdown();
        }
    }

    @Nested
    @DisplayName("우아한 종료 테스트")
    class GracefulShutdownTest {

        @Test
        @DisplayName("실행 중인 작업이 완료될 때까지 대기한다")
        void waitForRunningTasks() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            AtomicBoolean taskCompleted = new AtomicBoolean(false);

            // When: 2초 걸리는 작업 제출
            executor.submit(() -> {
                try {
                    Thread.sleep(2000);
                    taskCompleted.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // 즉시 종료 시도
            executor.shutdown();

            // Then: 작업이 완료되었어야 함
            assertThat(taskCompleted.get()).isTrue();
            assertThat(executor.isTerminated()).isTrue();
        }

        @Test
        @DisplayName("타임아웃 내에 완료되지 않으면 강제 종료한다")
        void forceShutdownAfterTimeout() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 1);  // 1초 타임아웃

            CountDownLatch taskStarted = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean(false);

            // When: 5초 걸리는 작업 제출 (타임아웃 초과)
            executor.submit(() -> {
                try {
                    taskStarted.countDown();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            });

            taskStarted.await();  // 작업이 시작될 때까지 대기
            Thread.sleep(100);    // 작업이 실행 중임을 보장

            executor.shutdown();

            // Then: 강제 종료되어 인터럽트 발생
            assertThat(interrupted.get()).isTrue();
            assertThat(executor.isTerminated()).isTrue();
        }

        @Test
        @DisplayName("중복 shutdown 호출은 안전하다")
        void multipleShutdownCalls_shouldBeSafe() {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            // When & Then
            assertThatNoException().isThrownBy(() -> {
                executor.shutdown();
                executor.shutdown();  // 두 번째 호출
                executor.shutdown();  // 세 번째 호출
            });
        }
    }

    @Nested
    @DisplayName("작업 제출 및 실행 테스트")
    class TaskExecutionTest {

        @Test
        @DisplayName("submit(Callable)로 작업을 제출하고 결과를 받을 수 있다")
        void submitCallable() throws Exception {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            // When
            Future<String> future = executor.submit(() -> "test-result");
            String result = future.get(1, TimeUnit.SECONDS);

            // Then
            assertThat(result).isEqualTo("test-result");

            executor.shutdown();
        }

        @Test
        @DisplayName("submit(Runnable)로 작업을 제출할 수 있다")
        void submitRunnable() throws Exception {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            AtomicInteger counter = new AtomicInteger(0);

            // When
            Future<?> future = executor.submit(counter::incrementAndGet);
            future.get(1, TimeUnit.SECONDS);

            // Then
            assertThat(counter.get()).isEqualTo(1);

            executor.shutdown();
        }

        @Test
        @DisplayName("execute()로 작업을 실행할 수 있다")
        void executeRunnable() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            CountDownLatch latch = new CountDownLatch(1);

            // When
            executor.execute(latch::countDown);

            // Then
            boolean completed = latch.await(1, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            executor.shutdown();
        }

        @Test
        @DisplayName("invokeAll()로 여러 작업을 동시에 실행할 수 있다")
        void invokeAll() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            List<Callable<Integer>> tasks = List.of(
                () -> 1,
                () -> 2,
                () -> 3
            );

            // When
            List<Future<Integer>> futures = executor.invokeAll(tasks);

            // Then
            assertThat(futures).hasSize(3);
            assertThat(futures.stream().allMatch(Future::isDone)).isTrue();

            executor.shutdown();
        }

        @Test
        @DisplayName("invokeAny()는 가장 먼저 완료된 결과를 반환한다")
        void invokeAny() throws Exception {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            List<Callable<String>> tasks = List.of(
                () -> {
                    Thread.sleep(100);
                    return "slow";
                },
                () -> "fast"
            );

            // When
            String result = executor.invokeAny(tasks);

            // Then
            assertThat(result).isEqualTo("fast");

            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("상태 확인 테스트")
    class StateTest {

        @Test
        @DisplayName("shutdown 호출 후 isShutdown()은 true를 반환한다")
        void isShutdownAfterShutdown() {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            // When
            executor.shutdown();

            // Then
            assertThat(executor.isShutdown()).isTrue();
        }

        @Test
        @DisplayName("모든 작업 완료 후 isTerminated()는 true를 반환한다")
        void isTerminatedAfterCompletion() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            // When
            executor.submit(() -> {
                // 빠른 작업
            });
            executor.shutdown();
            boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);

            // Then
            assertThat(terminated).isTrue();
            assertThat(executor.isTerminated()).isTrue();
        }

        @Test
        @DisplayName("shutdownNow()는 대기 중인 작업을 반환한다")
        void shutdownNow_shouldReturnPendingTasks() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newSingleThreadExecutor();
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 5);

            // 첫 번째 작업으로 스레드 점유
            CountDownLatch taskRunning = new CountDownLatch(1);
            executor.submit(() -> {
                try {
                    taskRunning.countDown();
                    Thread.sleep(10000);  // 긴 작업
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            taskRunning.await();  // 첫 번째 작업이 시작될 때까지 대기

            // 두 번째 작업 제출 (대기 큐에 들어감)
            executor.submit(() -> {
                // 실행되지 않을 작업
            });

            // When
            List<Runnable> pendingTasks = executor.shutdownNow();

            // Then
            assertThat(pendingTasks).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("예외 처리 테스트")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("인터럽트 발생 시 즉시 강제 종료한다")
        void interruptDuringShutdown() throws InterruptedException {
            // Given
            ExecutorService delegate = Executors.newFixedThreadPool(2);
            GracefulExecutorService executor = new GracefulExecutorService(delegate, 10);

            CountDownLatch taskStarted = new CountDownLatch(1);

            executor.submit(() -> {
                try {
                    taskStarted.countDown();
                    Thread.sleep(20000);  // 긴 작업
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            taskStarted.await();

            // When: 다른 스레드에서 종료 시도하고 인터럽트
            Thread shutdownThread = new Thread(() -> executor.shutdown());
            shutdownThread.start();
            Thread.sleep(100);
            shutdownThread.interrupt();  // 종료 대기 중 인터럽트

            shutdownThread.join(5000);

            // Then
            assertThat(executor.isShutdown()).isTrue();
        }
    }
}
