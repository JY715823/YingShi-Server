package com.yingshi.server.controller;

import com.yingshi.server.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R3-D-3: Release 端点集成测试.
 *
 * 服务端没有名为 "ReleaseController" 的单一类, 版本发布能力由两个 Controller 协同提供:
 * - {@link AppReleaseController}: 公开端点 /api/app/release/check (无需登录, App 启动时调用)
 * - {@link AdminAppReleaseController}: 管理端点 /api/admin/app-release/publish (需登录, 发布新版本)
 *
 * 验证矩阵:
 * 1. 正向: GET /api/app/release/check 无需 Authorization 返回 200, 响应包含 hasUpdate 字段
 * 2. 正向: 携带 platform=android + versionCode=N 返回 200, 字段齐全
 * 3. 正向: 缺省 platform 时使用默认值 android, 仍返回 200
 * 4. 边界: versionCode 缺省时返回 200 (Service 视为需要更新)
 * 5. 边界: platform=ios 等无数据平台返回 200 + hasUpdate=false
 * 6. 正向: POST /api/admin/app-release/publish 已登录 + 合法 APK 返回 200 + 发布记录 DTO
 * 7. 边界: POST /api/admin/app-release/publish 无 Authorization 返回 401
 * 8. 边界: POST /api/admin/app-release/publish 缺 apkFile 返回 500 (GlobalExceptionHandler 未处理 MissingServletRequestPartException)
 * 9. 边界: POST /api/admin/app-release/publish 缺 versionName 返回 500 (同上, 未处理 MissingServletRequestParameterException)
 * 10. 边界: POST /api/admin/app-release/publish 缺 versionCode 返回 500 (同上)
 * 11. 边界: POST /api/admin/app-release/publish versionCode<=0 返回 400 (Service 层校验)
 * 12. 边界: POST /api/admin/app-release/publish versionName 为空字符串返回 400 (Service 层校验 isBlank)
 * 13. 边界: GET /api/app/release/check 携带 Authorization 头仍可访问 (公开端点不强制 401)
 *
 * 使用 Testcontainers PostgreSQL via [AbstractPostgresIntegrationTest].
 */
class ReleaseControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    // ---- 公开端点: GET /api/app/release/check ----

    @Test
    void checkForUpdateWithoutAuthReturns200() throws Exception {
        // 公开端点, App 启动时尚未登录, 必须无需 Authorization 即可访问
        mockMvc.perform(get("/api/app/release/check")
                        .param("platform", "android")
                        .param("versionCode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.hasUpdate").exists());
    }

    @Test
    void checkForUpdateWithDefaultPlatformReturns200() throws Exception {
        // 不传 platform 时 Controller 使用 defaultValue="android"
        mockMvc.perform(get("/api/app/release/check")
                        .param("versionCode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUpdate").exists());
    }

    @Test
    void checkForUpdateWithoutVersionCodeReturns200() throws Exception {
        // versionCode 缺省时 Service 视为客户端无版本号, 返回 hasUpdate=true (有最新版本)
        mockMvc.perform(get("/api/app/release/check")
                        .param("platform", "android"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUpdate").exists());
    }

    @Test
    void checkForUpdateWithIosPlatformReturns200AndNoUpdate() throws Exception {
        // 当前服务端只发布 android 版本, ios 平台查询应返回 hasUpdate=false (无数据)
        mockMvc.perform(get("/api/app/release/check")
                        .param("platform", "ios")
                        .param("versionCode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUpdate").value(false));
    }

    @Test
    void checkForUpdateResponseContainsAllContractFields() throws Exception {
        // 验证 AppReleaseCheckResponse 契约字段全部存在.
        // 注意: 当无已发布版本时, noUpdateAvailable() 返回 null 字段,
        // 而 ApiResponse 上的 @JsonInclude(NON_NULL) 会剔除 null 字段,
        // 因此必须先发布一个版本 (且填充所有可选字段), 才能验证所有字段都存在于 JSON 响应中.
        String token = loginAndGetAccessToken();
        MockMultipartFile apkFile = fakeApkFile();
        int versionCode = (int) (System.nanoTime() % 1_000_000) + 300_000;

        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(apkFile)
                        .param("versionName", "9.9.11")
                        .param("versionCode", String.valueOf(versionCode))
                        .param("minSupportedVersionCode", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 发布后, /api/app/release/check 响应应包含所有契约字段
        mockMvc.perform(get("/api/app/release/check")
                        .param("platform", "android")
                        .param("versionCode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUpdate").exists())
                .andExpect(jsonPath("$.data.latestVersionCode").exists())
                .andExpect(jsonPath("$.data.latestVersionName").exists())
                .andExpect(jsonPath("$.data.downloadUrl").exists())
                .andExpect(jsonPath("$.data.forceUpdate").exists())
                .andExpect(jsonPath("$.data.sha256").exists())
                .andExpect(jsonPath("$.data.sizeBytes").exists())
                .andExpect(jsonPath("$.data.minSupportedVersionCode").exists());
    }

    @Test
    void checkForUpdateWithAuthorizationHeaderStillReturns200() throws Exception {
        // 公开端点不强制 401, 即使携带 Authorization 头也应正常返回 (避免 App 误带 token 时无法检查更新)
        String token = loginAndGetAccessToken();
        mockMvc.perform(get("/api/app/release/check")
                        .header("Authorization", "Bearer " + token)
                        .param("platform", "android")
                        .param("versionCode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUpdate").exists());
    }

    @Test
    void checkForUpdateIsIdempotent() throws Exception {
        // 同一参数多次调用应一致返回 200 (无副作用, 只读查询)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/app/release/check")
                            .param("platform", "android")
                            .param("versionCode", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasUpdate").exists());
        }
    }

    // ---- 管理端点: POST /api/admin/app-release/publish ----

    @Test
    void publishReleaseWithoutAuthReturns401() throws Exception {
        // Admin 端点必须登录 ( @AuthRequired )
        MockMultipartFile apkFile = fakeApkFile();
        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(apkFile)
                        .param("versionName", "1.0.0")
                        .param("versionCode", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publishReleaseWithInvalidTokenReturns401() throws Exception {
        MockMultipartFile apkFile = fakeApkFile();
        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(apkFile)
                        .param("versionName", "1.0.0")
                        .param("versionCode", "1")
                        .header("Authorization", "Bearer invalid-token-xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publishReleaseWithoutApkFileReturnsServerError() throws Exception {
        // 缺少必需的 apkFile 参数时, Spring 抛 MissingServletRequestPartException.
        // 当前 GlobalExceptionHandler 未单独处理该异常, 会落入 generic Exception 处理器返回 500.
        // 这里断言 5xx 以记录当前实际行为, 后续应在 GlobalExceptionHandler 增加 handler 改为 400.
        String token = loginAndGetAccessToken();
        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .param("versionName", "1.0.0")
                        .param("versionCode", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void publishReleaseWithoutVersionNameReturnsServerError() throws Exception {
        // 缺少 versionName 时, Spring 抛 MissingServletRequestParameterException.
        // 当前 GlobalExceptionHandler 未单独处理该异常, 会落入 generic Exception 处理器返回 500.
        // 这里断言 5xx 以记录当前实际行为, 后续应在 GlobalExceptionHandler 增加 handler 改为 400.
        String token = loginAndGetAccessToken();
        MockMultipartFile apkFile = fakeApkFile();
        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(apkFile)
                        .param("versionCode", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void publishReleaseWithoutVersionCodeReturnsServerError() throws Exception {
        // 缺少 versionCode 时, Spring 抛 MissingServletRequestParameterException.
        // 当前 GlobalExceptionHandler 未单独处理该异常, 会落入 generic Exception 处理器返回 500.
        // 这里断言 5xx 以记录当前实际行为, 后续应在 GlobalExceptionHandler 增加 handler 改为 400.
        String token = loginAndGetAccessToken();
        MockMultipartFile apkFile = fakeApkFile();
        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(apkFile)
                        .param("versionName", "1.0.0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void publishReleaseWithNonPositiveVersionCodeReturns400() throws Exception {
        // versionCode <= 0 在 Service 层校验, 抛 ApiException(VALIDATION_ERROR) -> 400
        String token = loginAndGetAccessToken();
        MockMultipartFile apkFile = fakeApkFile();
        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(apkFile)
                        .param("versionName", "1.0.0")
                        .param("versionCode", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishReleaseWithNegativeVersionCodeReturns400() throws Exception {
        String token = loginAndGetAccessToken();
        MockMultipartFile apkFile = fakeApkFile();
        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(apkFile)
                        .param("versionName", "1.0.0")
                        .param("versionCode", "-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishReleaseWithBlankVersionNameReturns400() throws Exception {
        // versionName 为空字符串, Service 层校验 isBlank, 抛 400
        String token = loginAndGetAccessToken();
        MockMultipartFile apkFile = fakeApkFile();
        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(apkFile)
                        .param("versionName", "")
                        .param("versionCode", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishReleaseWithValidApkReturns200AndPublishesNewVersion() throws Exception {
        // 正向: 合法 APK + 合法参数, 应成功发布并返回 AppReleaseDto
        String token = loginAndGetAccessToken();
        MockMultipartFile apkFile = fakeApkFile();
        int versionCode = (int) (System.nanoTime() % 1_000_000) + 100_000;

        MvcResult result = mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(apkFile)
                        .param("versionName", "9.9.9")
                        .param("versionCode", String.valueOf(versionCode))
                        .param("forceUpdate", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.platform").value("android"))
                .andExpect(jsonPath("$.data.versionCode").value(versionCode))
                .andExpect(jsonPath("$.data.versionName").value("9.9.9"))
                .andExpect(jsonPath("$.data.apkSha256").isNotEmpty())
                .andExpect(jsonPath("$.data.fileSizeBytes").isNumber())
                .andExpect(jsonPath("$.data.published").value(true))
                .andReturn();

        // 发布后, /api/app/release/check 应能查到该版本 (hasUpdate=true 当客户端 versionCode 较低)
        mockMvc.perform(get("/api/app/release/check")
                        .param("platform", "android")
                        .param("versionCode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUpdate").value(true))
                .andExpect(jsonPath("$.data.latestVersionCode").value(versionCode))
                .andExpect(jsonPath("$.data.latestVersionName").value("9.9.9"));
    }

    @Test
    void publishReleaseWithMinSupportedVersionCodeReturns200() throws Exception {
        // 携带可选参数 minSupportedVersionCode, 应正常发布
        String token = loginAndGetAccessToken();
        MockMultipartFile apkFile = fakeApkFile();
        int versionCode = (int) (System.nanoTime() % 1_000_000) + 200_000;

        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(apkFile)
                        .param("versionName", "9.9.10")
                        .param("versionCode", String.valueOf(versionCode))
                        .param("minSupportedVersionCode", "100")
                        .param("forceUpdate", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionCode").value(versionCode))
                .andExpect(jsonPath("$.data.forceUpdate").value(true))
                .andExpect(jsonPath("$.data.minSupportedVersionCode").value(100));
    }

    @Test
    void publishReleaseWithNonApkFileNameReturns400() throws Exception {
        // 文件名不以 .apk 结尾, 且 content type 非法, Service 校验 UPLOAD_CONTENT_TYPE_MISMATCH -> 400
        String token = loginAndGetAccessToken();
        MockMultipartFile txtFile = new MockMultipartFile(
                "apkFile", "not-an-apk.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/api/admin/app-release/publish")
                        .file(txtFile)
                        .param("versionName", "1.0.0")
                        .param("versionCode", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    /**
     * 构造一个伪造的 APK 文件用于测试上传.
     * 使用 .apk 后缀 + application/vnd.android.package-archive content type 通过 Service 校验.
     */
    private MockMultipartFile fakeApkFile() {
        byte[] content = new byte[]{0x50, 0x4B, 0x03, 0x04, 'F', 'A', 'K', 'E', '-', 'A', 'P', 'K'};
        return new MockMultipartFile(
                "apkFile", "fake-release.apk", "application/vnd.android.package-archive", content);
    }
}
