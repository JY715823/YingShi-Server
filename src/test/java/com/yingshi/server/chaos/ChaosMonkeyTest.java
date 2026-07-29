package com.yingshi.server.chaos;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.common.exception.GlobalExceptionHandler;
import com.yingshi.server.config.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R3-E-2: 服务端故障注入测试 — 验证 [GlobalExceptionHandler] 对各类异常的捕获能力.
 *
 * <h3>设计思路</h3>
 * 不引入 chaos-monkey-spring-boot 依赖, 而是创建一个简单的随机异常注入器 ([ChaosMonkey]),
 * 通过 MockMvc standalone setup 挂载测试 Controller + [GlobalExceptionHandler] + [RequestIdFilter],
 * 模拟生产环境中各类异常的随机注入, 验证:
 * <ol>
 *   <li>每种异常类型被对应的 {@code @ExceptionHandler} 捕获, 返回稳定错误信封 (requestId + error.code).</li>
 *   <li>5xx 异常不泄露 root cause 到客户端 (R0-H 安全要求, message 仅含 "Internal server error. Contact support with requestId: ...").</li>
 *   <li>数据完整性异常 (FK/约束违反) 返回 409 而非 500, 让客户端按 4xx 路径处理.</li>
 *   <li>随机注入 N 次故障, 全部被捕获, 无一逃逸 (混沌验证).</li>
 * </ol>
 *
 * <h3>使用 MockMvc standalone 而非 {@code @SpringBootTest} 的原因</h3>
 * <ul>
 *   <li>仅需验证 [GlobalExceptionHandler] 的异常捕获行为, 无需加载完整应用上下文 (数据库 / Flyway / etc.).</li>
 *   <li>standalone 模式直接挂载 ControllerAdvice + Filter, 测试更快更隔离.</li>
 *   <li>[RequestIdFilter] 无外部依赖, 可直接 {@code new} 实例挂载, 与生产路径一致.</li>
 *   <li>避免测试 Controller 被组件扫描注册到其他测试共享的 Spring 上下文.</li>
 * </ul>
 *
 * <h3>覆盖的异常类型 (对齐 [GlobalExceptionHandler] 的 @ExceptionHandler 列表)</h3>
 * <ul>
 *   <li>[ApiException] — 400 / 404 / 500 三态.</li>
 *   <li>[IllegalArgumentException] — 400 VALIDATION_ERROR.</li>
 *   <li>[NullPointerException] — 500 SERVER_ERROR (generic Exception handler).</li>
 *   <li>[IllegalStateException] — 500 SERVER_ERROR (generic Exception handler).</li>
 *   <li>[DataIntegrityViolationException] — 409 VALIDATION_ERROR.</li>
 *   <li>[TransactionSystemException] (root cause = 约束违反) — 409 VALIDATION_ERROR.</li>
 *   <li>[BadSqlGrammarException] — 500 SERVER_ERROR.</li>
 *   <li>未类型化的 [RuntimeException] — 500 SERVER_ERROR (generic Exception handler).</li>
 * </ul>
 */
class ChaosMonkeyTest {

    private MockMvc mockMvc;
    private final ChaosMonkey chaosMonkey = new ChaosMonkey(42L);

    @BeforeEach
    void setUp() {
        // standalone setup: 仅挂载 chaos controller + GlobalExceptionHandler + RequestIdFilter
        // RequestIdFilter 用于注入 requestId 属性, 与生产路径一致, 验证错误信封携带 requestId.
        mockMvc = MockMvcBuilders.standaloneSetup(new ChaosMonkeyController(chaosMonkey))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    // ===== 1. ApiException: 各 HTTP 状态码 =====

    @Test
    @DisplayName("ApiException 400 -> VALIDATION_ERROR")
    void apiException400ReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "api-400"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("chaos: api 400")))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("ApiException 404 -> NOT_FOUND")
    void apiException404ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "api-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(containsString("chaos: api 404")));
    }

    @Test
    @DisplayName("ApiException 500 -> SERVER_ERROR")
    void apiException500ReturnsServerError() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "api-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("chaos: api 500")));
    }

    // ===== 2. 通用 RuntimeException 家族 =====

    @Test
    @DisplayName("IllegalArgumentException -> 400 VALIDATION_ERROR")
    void illegalArgumentExceptionReturns400() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Invalid request data."));
    }

    @Test
    @DisplayName("NullPointerException -> 500 SERVER_ERROR (不泄露 root cause)")
    void nullPointerExceptionReturns500WithoutRootCause() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "npe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("Internal server error.")))
                // R0-H: root cause 不得泄露到客户端
                .andExpect(jsonPath("$.error.message").value(not(containsString("NullPointerException"))))
                .andExpect(jsonPath("$.error.message").value(not(containsString("chaos: npe secret"))));
    }

    @Test
    @DisplayName("IllegalStateException -> 500 SERVER_ERROR (generic handler 兜底)")
    void illegalStateExceptionReturns500() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "illegal-state"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("Internal server error.")));
    }

    @Test
    @DisplayName("未类型化 RuntimeException -> 500 SERVER_ERROR (generic handler 兜底)")
    void runtimeExceptionReturns500() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value(not(containsString("chaos: runtime secret"))));
    }

    // ===== 3. 数据库相关异常 (FK / 约束 / SQL 语法) =====

    @Test
    @DisplayName("DataIntegrityViolationException -> 409 VALIDATION_ERROR")
    void dataIntegrityViolationReturns409() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("Data conflict.")))
                // R0-H: 不向客户端泄露 DB root cause (表名/列名/约束名)
                .andExpect(jsonPath("$.error.message").value(not(containsString("chaos: fk violation secret"))));
    }

    @Test
    @DisplayName("TransactionSystemException (root cause = 约束违反) -> 409 VALIDATION_ERROR")
    void transactionSystemExceptionWithConstraintViolationReturns409() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "txn-constraint"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("Data integrity conflict.")));
    }

    @Test
    @DisplayName("BadSqlGrammarException -> 500 SERVER_ERROR")
    void badSqlGrammarReturns500() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "bad-sql"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Internal server error."));
    }

    // ===== 4. 混沌验证: 随机注入 N 次故障, 全部被捕获 =====

    @Test
    @DisplayName("随机注入 30 次故障 - 全部被 GlobalExceptionHandler 捕获, 无一逃逸")
    void randomChaosInjectionAllCaught() throws Exception {
        int iterations = 30;
        int fourXxCount = 0;
        int fiveXxCount = 0;

        for (int i = 0; i < iterations; i++) {
            int status = mockMvc.perform(get("/api/chaos").param("type", "random"))
                    .andReturn()
                    .getResponse()
                    .getStatus();

            // 断言: 每次随机故障都落入 4xx 或 5xx, 不应有 200 / 2xx (说明异常逃逸了)
            if (status >= 400 && status < 500) {
                fourXxCount++;
            } else if (status >= 500 && status < 600) {
                fiveXxCount++;
            } else {
                throw new AssertionError("第 " + (i + 1) + " 次随机故障未被捕获, status=" + status);
            }
        }

        // 断言: 30 次全部被捕获 (无逃逸)
        int totalCaught = fourXxCount + fiveXxCount;
        if (totalCaught != iterations) {
            throw new AssertionError(
                    "混沌验证失败: 期望 " + iterations + " 次全部被捕获, 实际 " + totalCaught + " 次"
            );
        }
    }

    @Test
    @DisplayName("随机注入 - 错误信封始终包含 requestId 和 error.code")
    void randomChaosInjectionEnvelopeAlwaysComplete() throws Exception {
        int iterations = 10;
        for (int i = 0; i < iterations; i++) {
            mockMvc.perform(get("/api/chaos").param("type", "random"))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        if (s < 400 || s >= 600) {
                            throw new AssertionError("Expected 4xx or 5xx but was " + s);
                        }
                    })
                    .andExpect(jsonPath("$.requestId").isNotEmpty())
                    .andExpect(jsonPath("$.error.code").isNotEmpty())
                    .andExpect(jsonPath("$.error.message").isNotEmpty())
                    .andExpect(header().exists("X-Request-Id"));
        }
    }

    @Test
    @DisplayName("未知故障类型 -> IllegalArgumentException (400 VALIDATION_ERROR)")
    void unknownFaultTypeReturns400() throws Exception {
        mockMvc.perform(get("/api/chaos").param("type", "unknown-fault-type"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("无 type 参数 -> 随机故障被捕获 (默认走 random 路径)")
    void noTypeParamDefaultsToRandomAndIsCaught() throws Exception {
        mockMvc.perform(get("/api/chaos"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s < 400 || s >= 600) {
                        throw new AssertionError("Expected 4xx or 5xx but was " + s);
                    }
                })
                .andExpect(jsonPath("$.error.code").isNotEmpty());
    }

    // ===== 测试 Controller & ChaosMonkey 实现 =====

    /**
     * 测试用 Controller: 按请求参数 {@code type} 注入指定异常, 或随机注入.
     * 仅在本测试类中通过 MockMvc standalone 注册, 不会被组件扫描到生产上下文.
     */
    @RestController
    @RequestMapping("/api/chaos")
    static class ChaosMonkeyController {

        private final ChaosMonkey chaosMonkey;

        ChaosMonkeyController(ChaosMonkey chaosMonkey) {
            this.chaosMonkey = chaosMonkey;
        }

        @GetMapping
        public void chaos(@RequestParam(required = false) String type) {
            chaosMonkey.inject(type);
        }
    }

    /**
     * 简单的随机异常注入器 (ChaosMonkey).
     * <p>
     * 维护一个命名异常表 ([ChaosMonkey#namedFaults]) 和一个全量异常列表 ([ChaosMonkey#allFaults]),
     * 按 {@code type} 参数注入指定异常, 或从全量列表中随机选取一个注入.
     * 使用固定种子的 [Random], 保证测试可重现.
     */
    static class ChaosMonkey {

        private final Random random;
        private final Map<String, Supplier<RuntimeException>> namedFaults = new LinkedHashMap<>();
        private final List<Supplier<RuntimeException>> allFaults = new ArrayList<>();

        ChaosMonkey(long seed) {
            this.random = new Random(seed);
            registerFaults();
        }

        private void registerFaults() {
            // ApiException: 各 HTTP 状态码
            register("api-400", () -> new ApiException(
                    HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "chaos: api 400"));
            register("api-404", () -> new ApiException(
                    HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "chaos: api 404"));
            register("api-500", () -> new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SERVER_ERROR, "chaos: api 500"));

            // 通用 RuntimeException 家族
            register("illegal", () -> new IllegalArgumentException("chaos: illegal argument"));
            register("npe", () -> new NullPointerException("chaos: npe secret"));
            register("illegal-state", () -> new IllegalStateException("chaos: illegal state"));
            register("runtime", () -> new RuntimeException("chaos: runtime secret"));

            // 数据库相关异常
            register("data-integrity", () -> new DataIntegrityViolationException("chaos: fk violation secret"));
            register("txn-constraint", () -> new TransactionSystemException(
                    "chaos: transaction system error",
                    new SQLIntegrityConstraintViolationException("chaos: fk constraint root cause")));
            register("bad-sql", () -> new BadSqlGrammarException(
                    "chaos: bad sql", "SELECT * FROM nonexistent_table", new SQLException("chaos: sql grammar")));
        }

        private void register(String name, Supplier<RuntimeException> fault) {
            namedFaults.put(name, fault);
            allFaults.add(fault);
        }

        /**
         * 注入异常. 若 {@code type} 为 null 或 "random", 从全量列表随机选取; 否则按 type 注入指定异常.
         *
         * @param type 故障类型名称, 或 null / "random" 表示随机注入
         */
        void inject(String type) {
            if (type == null || type.equals("random")) {
                throw allFaults.get(random.nextInt(allFaults.size())).get();
            }
            Supplier<RuntimeException> fault = namedFaults.get(type);
            if (fault == null) {
                throw new IllegalArgumentException("Unknown fault type: " + type);
            }
            throw fault.get();
        }
    }
}
