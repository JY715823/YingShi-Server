[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$Account = "1085060329@qq.com",
    [string]$Password = "123456",
    [string]$LoginCode = "",
    [string]$PostgresContainer = "yingshi-postgres",
    [string]$PostgresUser = "yingshi",
    [string]$PostgresDatabase = "yingshi",
    [string]$MinioAlias = "local",
    [string]$MinioEndpoint = "http://minio:9000",
    [string]$MinioAccessKey = "yingshi_minio_access",
    [string]$MinioSecretKey = "yingshi_minio_secret",
    [string]$ComposeNetwork = "yingshi-server_default",
    [string]$MinioContainer = "yingshi-minio",
    [switch]$SkipObjectCheck
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$tempUpload = Join-Path $env:TEMP ("stage16-cloudlike-smoke-" + [Guid]::NewGuid().ToString("N") + ".png")

Add-Type -AssemblyName System.Net.Http

$loginChallengeId = $null
$loginMaskedEmail = $null

function Resolve-ComposeNetwork {
    docker network inspect $ComposeNetwork *> $null
    if ($LASTEXITCODE -eq 0) {
        return $ComposeNetwork
    }

    $networks = docker inspect `
        -f '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' `
        $MinioContainer 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($networks | Out-String))) {
        throw "Could not find Docker network '$ComposeNetwork' or inspect MinIO container '$MinioContainer'. Is docker compose running?"
    }

    return @($networks | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })[0]
}

function Get-HttpMethod([string]$Method) {
    switch ($Method.ToUpperInvariant()) {
        "GET" { return [System.Net.Http.HttpMethod]::Get }
        "POST" { return [System.Net.Http.HttpMethod]::Post }
        default { throw "Unsupported HTTP method: $Method" }
    }
}

function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$JsonBody,
        [string]$MultipartFilePath,
        [string]$MultipartContentType = "application/octet-stream",
        [switch]$Raw
    )

    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(120)
    $request = [System.Net.Http.HttpRequestMessage]::new((Get-HttpMethod $Method), "$BaseUrl$Path")
    try {
        foreach ($headerName in $Headers.Keys) {
            [void]$request.Headers.TryAddWithoutValidation($headerName, [string]$Headers[$headerName])
        }
        if ($PSBoundParameters.ContainsKey("JsonBody")) {
            $json = $JsonBody | ConvertTo-Json -Depth 20 -Compress
            $request.Content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")
        }
        if ($PSBoundParameters.ContainsKey("MultipartFilePath")) {
            $multipart = [System.Net.Http.MultipartFormDataContent]::new()
            $bytes = [System.IO.File]::ReadAllBytes($MultipartFilePath)
            $fileContent = [System.Net.Http.ByteArrayContent]::new($bytes)
            $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($MultipartContentType)
            $multipart.Add($fileContent, "file", [System.IO.Path]::GetFileName($MultipartFilePath))
            $request.Content = $multipart
        }
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            $body = [System.Text.Encoding]::UTF8.GetString($bytes)
            throw "HTTP $([int]$response.StatusCode) $($response.ReasonPhrase): $body"
        }
        if ($Raw) {
            return @{
                Bytes = $bytes
                ContentType = $response.Content.Headers.ContentType
                ContentLength = $bytes.Length
            }
        }
        $content = [System.Text.Encoding]::UTF8.GetString($bytes)
        if ([string]::IsNullOrWhiteSpace($content)) {
            return $null
        }
        return $content | ConvertFrom-Json
    }
    finally {
        $request.Dispose()
        $client.Dispose()
    }
}

function Invoke-Step {
    param([string]$Name, [scriptblock]$Action)
    try {
        $result = & $Action
        Write-Host "[PASS] $Name - $result" -ForegroundColor Green
        return $result
    }
    catch {
        Write-Host "[FAIL] $Name - $($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

function Resolve-LoginCode {
    param(
        [string]$MaskedEmail,
        [string]$ChallengeId
    )

    if (-not [string]::IsNullOrWhiteSpace($script:LoginCode)) {
        return $script:LoginCode.Trim()
    }

    Write-Host "验证码已发送到 $MaskedEmail" -ForegroundColor Yellow
    Write-Host "challengeId: $ChallengeId" -ForegroundColor DarkGray
    $enteredCode = Read-Host "请输入 6 位邮箱验证码"
    if ([string]::IsNullOrWhiteSpace($enteredCode)) {
        throw "login code was not provided"
    }
    return $enteredCode.Trim()
}

function ConvertFrom-PostgresCsv {
    param([string]$Sql)
    $command = "COPY ($Sql) TO STDOUT WITH CSV HEADER"
    $output = docker exec -i $PostgresContainer psql -U $PostgresUser -d $PostgresDatabase -c $command
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL query failed."
    }
    if ([string]::IsNullOrWhiteSpace(($output | Out-String))) {
        return @()
    }
    return $output | ConvertFrom-Csv
}

function Test-MinioObject {
    param(
        [string]$Bucket,
        [string]$ObjectKey
    )

    if ([string]::IsNullOrWhiteSpace($ObjectKey)) {
        return $false
    }
    if (-not $script:ResolvedNetwork) {
        $script:ResolvedNetwork = Resolve-ComposeNetwork
    }
    $target = "$MinioAlias/$Bucket/$ObjectKey"
    $script = "mc alias set $MinioAlias $MinioEndpoint $MinioAccessKey $MinioSecretKey >/dev/null && mc stat '$target' >/dev/null 2>&1"
    docker run --rm --network $script:ResolvedNetwork --entrypoint /bin/sh minio/mc:RELEASE.2025-04-16T18-13-26Z -c $script | Out-Null
    return $LASTEXITCODE -eq 0
}

try {
    [byte[]]$imageBytes = [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR4nGNgYPgPAAEDAQD4C9pPAAAAAElFTkSuQmCC")
    [System.IO.File]::WriteAllBytes($tempUpload, $imageBytes)

    Invoke-Step "health" {
        $response = Invoke-ApiRequest -Method GET -Path "/api/health"
        if ($response.data.status -ne "UP") {
            throw "expected UP, got $($response.data.status)"
        }
        "status=$($response.data.status), profiles=$($response.data.activeProfiles -join ',')"
    } | Out-Null

    $login = $null
    Invoke-Step "login challenge" {
        $challenge = Invoke-ApiRequest -Method POST -Path "/api/auth/login/challenge" -JsonBody @{
            account = $Account
            password = $Password
        }
        $script:loginChallengeId = $challenge.data.challengeId
        $script:loginMaskedEmail = $challenge.data.maskedEmail
        if ([string]::IsNullOrWhiteSpace($script:loginChallengeId)) {
            throw "missing challenge id"
        }
        "challengeId=$($script:loginChallengeId), email=$($script:loginMaskedEmail)"
    } | Out-Null

    Invoke-Step "login verify" {
        $resolvedCode = Resolve-LoginCode -MaskedEmail $script:loginMaskedEmail -ChallengeId $script:loginChallengeId
        $script:login = Invoke-ApiRequest -Method POST -Path "/api/auth/login/verify" -JsonBody @{
            challengeId = $script:loginChallengeId
            code = $resolvedCode
        }
        if ([string]::IsNullOrWhiteSpace($script:login.data.accessToken)) {
            throw "missing access token"
        }
        "userId=$($script:login.data.userId)"
    } | Out-Null
    $headers = @{ Authorization = "Bearer $($login.data.accessToken)" }

    Invoke-Step "media feed" {
        $feed = Invoke-ApiRequest -Method GET -Path "/api/media/feed?pageSize=20" -Headers $headers
        "count=$(@($feed.data).Count)"
    } | Out-Null

    $uploadToken = $null
    Invoke-Step "upload token" {
        $script:uploadToken = Invoke-ApiRequest -Method POST -Path "/api/uploads/token" -Headers $headers -JsonBody @{
            fileName = "stage16-smoke.png"
            mimeType = "image/png"
            fileSizeBytes = $imageBytes.Length
            mediaType = "image"
            width = 1
            height = 1
            durationMillis = $null
            displayTimeMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        }
        "uploadId=$($script:uploadToken.data.uploadId), provider=$($script:uploadToken.data.provider)"
    } | Out-Null

    $upload = $null
    Invoke-Step "upload file" {
        $script:upload = Invoke-ApiRequest `
            -Method POST `
            -Path "/api/uploads/$($uploadToken.data.uploadId)/file" `
            -Headers $headers `
            -MultipartFilePath $tempUpload `
            -MultipartContentType "image/png"
        "state=$($script:upload.data.state), mediaId=$($script:upload.data.media.mediaId)"
    } | Out-Null

    $mediaId = $upload.data.media.mediaId
    Invoke-Step "read original" {
        $raw = Invoke-ApiRequest -Method GET -Path "/api/media/files/$mediaId`?variant=original" -Headers $headers -Raw
        if ($raw.ContentLength -le 0) {
            throw "empty original response"
        }
        "bytes=$($raw.ContentLength), contentType=$($raw.ContentType)"
    } | Out-Null

    Invoke-Step "read preview" {
        $raw = Invoke-ApiRequest -Method GET -Path "/api/media/files/$mediaId`?variant=preview" -Headers $headers -Raw
        if ($raw.ContentLength -le 0) {
            throw "empty preview response"
        }
        "bytes=$($raw.ContentLength), contentType=$($raw.ContentType)"
    } | Out-Null

    Invoke-Step "database object fields" {
        $rows = ConvertFrom-PostgresCsv "select id, storage_provider, bucket, original_object_key, preview_object_key from media where id = '$mediaId'"
        if (@($rows).Count -ne 1) {
            throw "media row not found in PostgreSQL: $mediaId"
        }
        $row = @($rows)[0]
        if ([string]::IsNullOrWhiteSpace($row.original_object_key)) {
            throw "original_object_key is empty"
        }
        if ($row.original_object_key -match '^(https?|s3|oss|file)://') {
            throw "original_object_key looks like a URL: $($row.original_object_key)"
        }
        "provider=$($row.storage_provider), bucket=$($row.bucket), original=$($row.original_object_key), preview=$($row.preview_object_key)"
    } | Out-Null

    Invoke-Step "MinIO object exists" {
        if ($SkipObjectCheck) {
            return "skipped"
        }
        $rows = ConvertFrom-PostgresCsv "select storage_provider, bucket, original_object_key, preview_object_key from media where id = '$mediaId'"
        $row = @($rows)[0]
        if ($row.storage_provider -notin @("s3", "minio")) {
            return "skipped for provider=$($row.storage_provider)"
        }
        if (-not (Test-MinioObject -Bucket $row.bucket -ObjectKey $row.original_object_key)) {
            throw "missing original object in MinIO: $($row.bucket)/$($row.original_object_key)"
        }
        $hasPreviewKey = -not [string]::IsNullOrWhiteSpace($row.preview_object_key)
        $previewObjectMissing = $hasPreviewKey -and -not (Test-MinioObject -Bucket $row.bucket -ObjectKey $row.preview_object_key)
        if ($previewObjectMissing) {
            throw "missing preview object in MinIO: $($row.bucket)/$($row.preview_object_key)"
        }
        "bucket=$($row.bucket), original=$($row.original_object_key), preview=$($row.preview_object_key)"
    } | Out-Null
}
finally {
    if (Test-Path $tempUpload) {
        Remove-Item -LiteralPath $tempUpload -Force
    }
}

Write-Host ""
Write-Host "Stage16 cloudlike smoke completed." -ForegroundColor Green
