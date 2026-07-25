# 腾讯云 COS 对象存储 - 开通与配置教程

> 本文档针对 YingShi App 的 2 人私人场景，目标是**24号前能上线**。
> 采用「COS 预签名 URL 直连」方案，**无需域名备案**。

---

## 一、方案对比与选择

| 方案 | 备案 | 月成本（2人） | 速度 | 推荐度 |
|---|---|---|---|---|
| **A. COS 预签名URL直连** | 不需要 | 0-5元 | 中 | ⭐⭐⭐⭐⭐ |
| B. COS + CDN 加速 | 需要 | 0-2元 | 快 | ⭐⭐⭐ |
| C. 自建 MinIO | 不需要 | 0元 | 中 | ⭐⭐ |

**选择方案A**：COS 预签名 URL 直连。
- 优点：免备案、24号能上线、6个月50GB免费存储
- 流量费：0.5元/GB（2人月10GB≈5元，可接受）
- 等以后备案后再切到方案B

---

## 二、COS 免费额度详解（2026年最新）

### 免费部分（6个月）

| 项目 | 个人用户额度 | 企业用户额度 | 有效期 |
|---|---|---|---|
| **标准存储容量** | 50GB | 1TB | 6个月（180天） |

### 不免费部分（按量计费）

| 计费项 | 单价 | 2人月用量预估 | 月成本 |
|---|---|---|---|
| 请求次数 | 0.01元/万次 | 1万次 | 0.01元 |
| 外网下行流量 | **0.5元/GB** | 10GB | 5元 |
| CDN回源流量 | 0.15元/GB | 不走CDN，0 | 0元 |

**6个月内总成本预估**：5元/月 × 6 = **30元**（仅流量费）

### 6个月后

免费额度到期，开始按量计费：
- 存储费：0.118元/GB/月 × 50GB = 5.9元/月
- 流量费：5元/月
- **合计约 11元/月**（2人场景）

> 建议免费额度到期前购买存储包，比按量便宜 30%。详见第五节。

---

## 三、开通 COS（5分钟）

### 步骤1：登录控制台

访问 [https://console.cloud.tencent.com/cos5](https://console.cloud.tencent.com/cos5)

首次进入会提示「开通对象存储服务」，点「**同意协议并开通**」即可（免费）。

### 步骤2：创建 Bucket（存储桶）

1. 点「**存储桶列表**」→「**创建存储桶**」
2. 填写：

| 字段 | 填什么 | 说明 |
|---|---|---|
| 名称 | `yingshi-prod` | 系统会自动追加 APPID 变成 `yingshi-prod-1258000000` |
| 选定地域 | **广州**（ap-guangzhou） | 必须和你的服务器同地域，内网回源免费 |
| 访问权限 | **私有读写** | ⚠️ 必须**私有**，不能公有读 |
| Bucket 标签 | 跳过 | 不需要 |
| 版本控制 | 不开启 | 2人场景不需要 |
| 多 AZ | 不开启 | 节省成本 |
| 服务端加密 | 不开启 | App 直读不需要 |
| 实时日志 | 不开启 | 收费功能，不需要 |
| 对象锁定 | 不开启 | 不需要 |

3. 点「**下一步**」→「**创建**」

### 步骤3：获取 API 密钥

1. 访问 [https://console.cloud.tencent.com/cam/capi](https://console.cloud.tencent.com/cam/capi)
2. 如果没有密钥，点「**新建密钥**」
3. 记录两个字段：
   - **SecretId**（30位左右）
   - **SecretKey**（32位左右）
4. ⚠️ SecretKey 只显示一次，**立刻保存到密码管理器**

> 建议为 YingShi 单独创建子账号密钥，权限只给 COS 读写，避免主密钥泄露风险。
> 操作：[访问管理](https://console.cloud.tencent.com/cam) → 用户 → 新建用户 → 关联策略 `QcloudCOSFullAccess`

### 步骤4：记录 Bucket 信息

在存储桶列表点开你创建的 bucket，记录以下信息：

```
Bucket Name: yingshi-prod-1258000000  （name-appid 格式）
地域: ap-guangzhou
Endpoint: https://cos.ap-guangzhou.myqcloud.com
APPID: 1258000000
```

---

## 四、配置 YingShi 服务端

### 步骤1：编辑 `.env.prod`

```bash
# 在服务器 /opt/yingshi/.env.prod 中
# 注释掉 MinIO 配置，启用 COS 配置

STORAGE_PROVIDER=cos
STORAGE_BUCKET=yingshi-prod-1258000000  # 你的实际 bucket name
STORAGE_ENDPOINT=https://cos.ap-guangzhou.myqcloud.com
STORAGE_REGION=ap-guangzhou
STORAGE_ACCESS_KEY=AKIDxxxxxxxxxxxxxxxxxxxxxx  # 你的 SecretId
STORAGE_SECRET_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxx  # 你的 SecretKey
STORAGE_FORCE_PATH_STYLE=false
STORAGE_DIRECT_UPLOAD_PUBLIC_ENDPOINT=https://cos.ap-guangzhou.myqcloud.com
```

### 步骤2：重启 server 容器

```bash
cd /opt/yingshi
docker compose -f docker-compose.prod.yml up -d server
```

### 步骤3：验证

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 上传一张测试图（通过 App 操作即可）
# 如果返回的 URL 是 cos.ap-guangzhou.myqcloud.com 开头，说明切换成功
```

---

## 五、套餐购买建议（免费额度用完后）

### 存储包（6个月后必买）

| 套餐 | 容量 | 价格 | 有效期 | 折合每月 | 适用场景 |
|---|---|---|---|---|---|
| 标准存储容量包 | 50GB | 18元 | 6个月 | 3元/月 | ⭐ 2人场景首选 |
| 标准存储容量包 | 100GB | 30元 | 1年 | 2.5元/月 | 长期使用更划算 |
| 标准存储容量包 | 1TB | 199元 | 1年 | 16.6元/月 | 数据增长到100GB+ |

购买入口：[https://buy.cloud.tencent.com/cos](https://buy.cloud.tencent.com/cos)

### 流量包（按需购买）

| 套餐 | 流量 | 价格 | 有效期 | 折合每GB | 适用场景 |
|---|---|---|---|---|---|
| 外网下行流量包 | 50GB | 22元 | 1年 | 0.44元/GB | 比 0.5元/GB 按量便宜 12% |
| 外网下行流量包 | 100GB | 38元 | 1年 | 0.38元/GB | 比 0.5元/GB 按量便宜 24% |
| 外网下行流量包 | 1TB | 299元 | 1年 | 0.29元/GB | 比 0.5元/GB 按量便宜 42% |

> 2人场景：每月10GB流量，1年=120GB，买「100GB+50GB」套餐约60元/年最划算。
> 等于每月5元流量费，比按量计费（0.5元/GB）省24%。

### CDN 流量包（备案后才有用）

| 套餐 | 流量 | 价格 | 有效期 | 折合每GB |
|---|---|---|---|---|
| CDN境内流量包 | 100GB | 21元 | 1个月 | 0.21元/GB |
| CDN境内流量包 | 500GB | 99元 | 1年 | 0.20元/GB |

> CDN 流量（0.21元/GB）比 COS 外网流量（0.5元/GB）便宜一半多。
> 但**必须备案**才能用 CDN。

### 2人场景推荐组合

**免费期（前6个月）**：
- 存储：免费 50GB
- 流量：按量 5元/月
- **总成本：5元/月**

**6个月后**：
- 买 100GB 存储包（30元/年）
- 买 100GB 流量包（38元/年）
- **总成本：5.7元/月**（68元/年）

**1年后（数据稳定增长）**：
- 买 1TB 存储包（199元/年）
- 买 1TB 流量包（299元/年）
- **总成本：41.5元/月**（498元/年）

---

## 六、CORS 配置（重要）

App 直传 COS 时需要配置 CORS，否则上传会被浏览器层拦截（虽然 Android App 不强制，但配了更稳）。

1. COS 控制台 → 你的 bucket → 「**安全管理**」→「**跨域访问 CORS 设置**」
2. 点「**添加规则**」，填：

| 字段 | 值 |
|---|---|
| 来源 Origin | `*` |
| 操作 Methods | `GET, PUT, POST, DELETE, HEAD` |
| Allow-Headers | `*` |
| Expose-Headers | `ETag, Content-Length, Content-Type` |
| 超时 Max-Age | `600` |

3. 点「**保存**」

---

## 七、生命周期配置（节省成本）

让 30 天前的媒体自动转低频存储（成本降 40%），不适合你的场景（2人随时会看历史照片），**不要开**。

---

## 八、常见问题

### Q1：为什么不用 CDN？

A：CDN 要求域名备案，你目前没备案。等24号上线稳定后，如果想优化速度，再走备案流程（1-3周），切到 CDN 流量费还便宜一半。

### Q2：COS 的内网回源是什么？

A：腾讯云同地域产品之间走内网。你的云服务器（CVM）在广州，COS 也在广州，服务器**访问 COS 不收流量费**。但 App 不在腾讯云内网，App 访问 COS 走公网，所以要收外网下行流量费。

### Q3：预签名 URL 安全吗？

A：安全。预签名 URL 有时效（默认15分钟），过期自动失效。即使 URL 泄露，15分钟后也无法访问。

### Q4：免费额度到期会怎么样？

A：不会立刻删数据，会按量计费。如果欠费超过7天，COS 会暂停服务（数据保留90天后才删除）。建议余额至少留 50 元。

### Q5：如何查看用量？

A：COS 控制台 → 「**资源包管理**」→「**免费额度资源包**」可看到剩余免费额度。
「**用量查询**」→「**存储用量**」可看到 bucket 实际用量。

---

## 九、相关文档

- [COS 免费额度官方说明](https://cloud.tencent.com/document/product/436/6240)
- [COS 产品定价](https://buy.cloud.tencent.com/price/cos)
- [COS Java SDK 文档](https://cloud.tencent.com/document/product/436/10199)
- [CDN 计费说明](https://cloud.tencent.com/document/product/228/2949)
