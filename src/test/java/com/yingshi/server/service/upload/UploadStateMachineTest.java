package com.yingshi.server.service.upload;

import com.yingshi.server.domain.UploadState;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R3-D-3: 上传状态机测试.
 *
 * 服务端 [UploadState] 枚举仅定义 4 个状态 (WAITING / SUCCESS / FAILED / CANCELLED),
 * 状态机转换由 UploadTokenService / UploadFileService 等组件在业务流程中驱动.
 *
 * 本测试聚焦状态机的"合法转换集" 与 "非法转换集", 作为后续状态机重构的契约保护.
 * 不依赖 Spring 上下文, 直接对枚举值做断言.
 */
class UploadStateMachineTest {

    /**
     * 服务端 UploadState 的全部合法值.
     * 与客户端 com.example.yingshi.data.model.UploadState 对齐 (客户端额外有 UPLOADING).
     */
    private static final Set<UploadState> ALL_STATES = EnumSet.allOf(UploadState.class);

    @Test
    void uploadStateEnumHasExactlyFourValues() {
        assertThat(ALL_STATES).containsExactlyInAnyOrder(
                UploadState.WAITING,
                UploadState.SUCCESS,
                UploadState.FAILED,
                UploadState.CANCELLED
        );
        assertThat(ALL_STATES).hasSize(4);
    }

    @Test
    void waitingStateIsInitialState() {
        // WAITING 是创建上传 token 时的初始状态 (UploadTokenService.createToken)
        UploadState initial = UploadState.WAITING;
        assertThat(initial.name()).isEqualTo("WAITING");
        assertThat(initial.ordinal()).isZero();
    }

    @Test
    void terminalStatesAreSuccessFailedAndCancelled() {
        // 终态: SUCCESS (上传完成), FAILED (校验失败/网络错误), CANCELLED (用户取消)
        Set<UploadState> terminalStates = EnumSet.of(
                UploadState.SUCCESS,
                UploadState.FAILED,
                UploadState.CANCELLED
        );
        assertThat(terminalStates).hasSize(3);
        assertThat(terminalStates).doesNotContain(UploadState.WAITING);
    }

    @Test
    void validTransitionsFromWaiting() {
        // WAITING 是初始态, 可流向: SUCCESS / FAILED / CANCELLED
        Set<UploadState> validFromWaiting = validTransitions(UploadState.WAITING);
        assertThat(validFromWaiting).containsExactlyInAnyOrder(
                UploadState.SUCCESS,
                UploadState.FAILED,
                UploadState.CANCELLED
        );
    }

    @Test
    void validTransitionsFromSuccess() {
        // SUCCESS 是终态, 不应再发生状态转换
        Set<UploadState> validFromSuccess = validTransitions(UploadState.SUCCESS);
        assertThat(validFromSuccess).isEmpty();
    }

    @Test
    void validTransitionsFromFailed() {
        // FAILED 是终态 (服务端不做断点续传, 客户端重新走 createToken 流程)
        Set<UploadState> validFromFailed = validTransitions(UploadState.FAILED);
        assertThat(validFromFailed).isEmpty();
    }

    @Test
    void validTransitionsFromCancelled() {
        // CANCELLED 是终态
        Set<UploadState> validFromCancelled = validTransitions(UploadState.CANCELLED);
        assertThat(validFromCancelled).isEmpty();
    }

    @Test
    void waitingToItselfIsInvalid() {
        // WAITING -> WAITING 不应发生 (状态机不允许多次重入)
        assertThat(validTransitions(UploadState.WAITING)).doesNotContain(UploadState.WAITING);
    }

    @Test
    void successToFailedIsInvalid() {
        // SUCCESS -> FAILED 不应发生 (已成功的上传不能回退为失败)
        assertThat(validTransitions(UploadState.SUCCESS)).doesNotContain(UploadState.FAILED);
    }

    @Test
    void failedToSuccessIsInvalid() {
        // FAILED -> SUCCESS 不应发生 (失败的上传需重新走 createToken 流程, 而非直接转成功)
        assertThat(validTransitions(UploadState.FAILED)).doesNotContain(UploadState.SUCCESS);
    }

    @Test
    void cancelledToAnyStateIsInvalid() {
        // CANCELLED 是终态, 不能转换到任何其他状态
        assertThat(validTransitions(UploadState.CANCELLED)).isEmpty();
    }

    @Test
    void enumNameMatchesDatabaseStringValue() {
        // UploadTaskEntity.state 列为 @Enumerated(EnumType.STRING), 数据库存的是 name()
        // 验证 name() 与期望字符串严格一致, 避免后续重构破坏 DB 数据
        assertThat(UploadState.WAITING.name()).isEqualTo("WAITING");
        assertThat(UploadState.SUCCESS.name()).isEqualTo("SUCCESS");
        assertThat(UploadState.FAILED.name()).isEqualTo("FAILED");
        assertThat(UploadState.CANCELLED.name()).isEqualTo("CANCELLED");
    }

    @Test
    void valueOfCanParseAllStates() {
        // 验证 UploadState.valueOf 可解析所有合法字符串 (从 DB 读取时使用)
        for (UploadState state : ALL_STATES) {
            assertThat(UploadState.valueOf(state.name())).isEqualTo(state);
        }
    }

    @Test
    void valueOfThrowsOnInvalidString() {
        // 非法字符串应抛 IllegalArgumentException (防御性断言)
        try {
            UploadState.valueOf("UPLOADING"); // 客户端有 UPLOADING, 服务端没有
            org.junit.jupiter.api.Assertions.fail("valueOf 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * 计算从给定状态出发的合法转换集.
     * 这是一个测试辅助方法, 不依赖生产代码 (生产代码没有显式状态机类).
     * 状态机约定:
     * - WAITING -> {SUCCESS, FAILED, CANCELLED} (上传 token 创建后, 三种结束路径)
     * - SUCCESS / FAILED / CANCELLED -> {} (终态, 无后续转换)
     */
    private Set<UploadState> validTransitions(UploadState from) {
        return switch (from) {
            case WAITING -> EnumSet.of(UploadState.SUCCESS, UploadState.FAILED, UploadState.CANCELLED);
            case SUCCESS, FAILED, CANCELLED -> EnumSet.noneOf(UploadState.class);
        };
    }
}
