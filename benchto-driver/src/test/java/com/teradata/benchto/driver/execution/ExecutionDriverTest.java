/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.teradata.benchto.driver.execution;

import com.facebook.presto.jdbc.internal.guava.collect.ImmutableList;
import com.teradata.benchto.driver.Benchmark;
import com.teradata.benchto.driver.BenchmarkProperties;
import com.teradata.benchto.driver.concurrent.ExecutorServiceFactory;
import com.teradata.benchto.driver.execution.BenchmarkExecutionResult.BenchmarkExecutionResultBuilder;
import com.teradata.benchto.driver.listeners.benchmark.BenchmarkExecutionListener;
import com.teradata.benchto.driver.listeners.benchmark.BenchmarkStatusReporter;
import com.teradata.benchto.driver.listeners.benchmark.DefaultBenchmarkExecutionListener;
import com.teradata.benchto.driver.loader.BenchmarkLoader;
import com.teradata.benchto.driver.macro.MacroService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.teradata.benchto.driver.utils.TimeUtils.sleep;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExecutionDriverTest
{
    @Mock
    BenchmarkExecutionDriver benchmarkExecutionDriver;

    @Mock
    BenchmarkProperties benchmarkProperties;

    @Mock
    MacroService macroService;

    @Mock
    BenchmarkLoader benchmarkLoader;

    @Mock
    BenchmarkStatusReporter benchmarkStatusReporter;

    @InjectMocks
    ExecutionDriver driver;

    @Before
    public void setUp()
    {
        Benchmark benchmark = mock(Benchmark.class);
        when(benchmark.getConcurrency()).thenReturn(1);

        when(benchmarkLoader.loadBenchmarks(anyString()))
                .thenReturn(ImmutableList.of(benchmark));
        when(benchmarkProperties.getBeforeAllMacros())
                .thenReturn(Optional.of(ImmutableList.of("before-macro")));
        when(benchmarkProperties.getAfterAllMacros())
                .thenReturn(Optional.of(ImmutableList.of("after-macro")));
        when(benchmarkProperties.getHealthCheckMacros())
                .thenReturn(Optional.of(ImmutableList.of("health-check-macro")));
        when(benchmarkProperties.getExecutionSequenceId())
                .thenReturn(Optional.of("sequence-id"));
        when(benchmarkExecutionDriver.execute(any(Benchmark.class), anyInt(), anyInt()))
                .thenReturn(successfulBenchmarkExecution());
        when(benchmarkProperties.getTimeLimit())
                .thenReturn(Optional.empty());
    }

    private BenchmarkExecutionResult successfulBenchmarkExecution()
    {
        return new BenchmarkExecutionResultBuilder(null).withExecutions(ImmutableList.of()).build();
    }

    @Test
    public void finishWhenTimeLimitEnds()
    {
        when(benchmarkProperties.getTimeLimit())
                .thenReturn(Optional.of(Duration.ofMillis(100)));

        sleepOnSecondDuringMacroExecution();

        driver.execute();

        verifyNoMoreInteractions(benchmarkExecutionDriver);
    }

    @Test
    public void benchmarkIsExecutedWhenNoTimeLimitEnds()
    {
        sleepOnSecondDuringMacroExecution();

        driver.execute();

        verify(benchmarkExecutionDriver).execute(any(Benchmark.class), anyInt(), anyInt());
        verifyNoMoreInteractions(benchmarkExecutionDriver);
    }

    @Test
    public void failOnListenerFailure()
    {
        BenchmarkExecutionListener failingListener = new DefaultBenchmarkExecutionListener()
        {
            @Override
            public Future<?> benchmarkFinished(BenchmarkExecutionResult benchmarkExecutionResult)
            {
                throw new IllegalStateException("programmatic listener failure in testFailingListener");
            }
        };

        BenchmarkStatusReporter statusReporter = new BenchmarkStatusReporter(singletonList(failingListener));
        /*
         * Listeners are called by BenchmarkExecutionDriver so we need to provide one.
         * Listeners results final check is invoked by ExecutionDriver, so this is tested here.
         */
        BenchmarkExecutionDriver benchmarkExecutionDriver = new BenchmarkExecutionDriver();
        ReflectionTestUtils.setField(benchmarkExecutionDriver, "macroService", mock(MacroService.class));
        ReflectionTestUtils.setField(benchmarkExecutionDriver, "executorServiceFactory", new ExecutorServiceFactory());
        ReflectionTestUtils.setField(benchmarkExecutionDriver, "executionSynchronizer", mock(ExecutionSynchronizer.class));
        ReflectionTestUtils.setField(benchmarkExecutionDriver, "statusReporter", statusReporter);
        ReflectionTestUtils.setField(driver, "benchmarkExecutionDriver", benchmarkExecutionDriver);
        ReflectionTestUtils.setField(driver, "benchmarkStatusReporter", statusReporter);

        assertThatThrownBy(() -> {
            driver.execute();
        }).hasMessageContaining("programmatic listener failure in testFailingListener");
    }

    private void sleepOnSecondDuringMacroExecution()
    {
        doAnswer(invocationOnMock -> {
            sleep(1, TimeUnit.SECONDS);
            return null;
        }).when(macroService).runBenchmarkMacros(anyList());
    }
}
