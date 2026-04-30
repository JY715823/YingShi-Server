[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Account = "demo.a@yingshi.local",
    [string]$Password = "demo123456"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$failures = New-Object System.Collections.Generic.List[string]
$uploadTempPath = Join-Path $PSScriptRoot "integration-smoke-upload.jpg"
$trashItemId = $null

Add-Type -AssemblyName System.Net.Http

function Get-HttpMethod([string]$Method) {
    switch ($Method.ToUpperInvariant()) {
        "GET" { return [System.Net.Http.HttpMethod]::Get }
        "POST" { return [System.Net.Http.HttpMethod]::Post }
        "PATCH" { return [System.Net.Http.HttpMethod]::Patch }
        "DELETE" { return [System.Net.Http.HttpMethod]::Delete }
        default { throw "Unsupported HTTP method: $Method" }
    }
}

function New-Client {
    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(30)
    return $client
}

function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$JsonBody,
        [string]$MultipartFilePath,
        [string]$MultipartContentType = "application/octet-stream"
    )

    $client = New-Client
    $request = [System.Net.Http.HttpRequestMessage]::new((Get-HttpMethod $Method), "$BaseUrl$Path")
    try {
        foreach ($headerName in $Headers.Keys) {
            [void]$request.Headers.TryAddWithoutValidation($headerName, [string]$Headers[$headerName])
        }

        if ($PSBoundParameters.ContainsKey("JsonBody")) {
            $json = $JsonBody | ConvertTo-Json -Depth 20 -Compress
            $request.Content = [System.Net.Http.StringContent]::new(
                $json,
                [System.Text.Encoding]::UTF8,
                "application/json"
            )
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
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "HTTP $([int]$response.StatusCode) $($response.ReasonPhrase): $content"
        }
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
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    try {
        $result = & $Action
        if ([string]::IsNullOrWhiteSpace([string]$result)) {
            $result = "OK"
        }
        Write-Host "[PASS] $Name - $result" -ForegroundColor Green
        return $result
    }
    catch {
        $message = $_.Exception.Message
        $failures.Add("${Name}: ${message}") | Out-Null
        Write-Host "[FAIL] $Name - $message" -ForegroundColor Red
        return $null
    }
}

function Require-Value {
    param(
        $Value,
        [string]$Message
    )

    if ($null -eq $Value -or ([string]$Value).Length -eq 0) {
        throw $Message
    }
    return $Value
}

try {
    $fileBytes = [System.Text.Encoding]::UTF8.GetBytes("fake-image-data")
    [System.IO.File]::WriteAllBytes($uploadTempPath, $fileBytes)

    $healthResponse = Invoke-Step "health" {
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/health"
        if ($response.data.status -ne "UP") {
            throw "expected UP but got $($response.data.status)"
        }
        "status=$($response.data.status), app=$($response.data.application)"
    }

    $loginResponse = $null
    Invoke-Step "login token" {
        $script:loginResponse = Invoke-ApiRequest -Method "POST" -Path "/api/auth/login" -JsonBody @{
            account = $Account
            password = $Password
        }
        $token = Require-Value $script:loginResponse.data.accessToken "login succeeded but accessToken is empty"
        "userId=$($script:loginResponse.data.userId), tokenLength=$($token.Length)"
    } | Out-Null

    $accessToken = $null
    if ($loginResponse) {
        $accessToken = $loginResponse.data.accessToken
    }
    $authHeaders = @{}
    if ($accessToken) {
        $authHeaders["Authorization"] = "Bearer $accessToken"
    }

    Invoke-Step "me" {
        if (-not $accessToken) {
            throw "missing access token from login step"
        }
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/auth/me" -Headers $authHeaders
        "userId=$($response.data.userId), spaceId=$($response.data.spaceId)"
    } | Out-Null

    $albumsResponse = $null
    Invoke-Step "albums" {
        if (-not $accessToken) {
            throw "missing access token from login step"
        }
        $script:albumsResponse = Invoke-ApiRequest -Method "GET" -Path "/api/albums" -Headers $authHeaders
        $albumCount = @($script:albumsResponse.data).Count
        if ($albumCount -lt 1) {
            throw "album list is empty"
        }
        "count=$albumCount, firstAlbum=$($script:albumsResponse.data[0].albumId)"
    } | Out-Null

    $albumId = if ($albumsResponse -and @($albumsResponse.data).Count -gt 0) { $albumsResponse.data[0].albumId } else { "album_001" }
    $albumPostsResponse = $null
    Invoke-Step "album posts" {
        if (-not $accessToken) {
            throw "missing access token from login step"
        }
        $script:albumPostsResponse = Invoke-ApiRequest -Method "GET" -Path "/api/albums/$albumId/posts" -Headers $authHeaders
        $postCount = @($script:albumPostsResponse.data).Count
        if ($postCount -lt 1) {
            throw "album $albumId has no posts"
        }
        "album=$albumId, count=$postCount, firstPost=$($script:albumPostsResponse.data[0].postId)"
    } | Out-Null

    $postId = if ($albumPostsResponse -and @($albumPostsResponse.data).Count -gt 0) { $albumPostsResponse.data[0].postId } else { "post_001" }
    Invoke-Step "post detail" {
        if (-not $accessToken) {
            throw "missing access token from login step"
        }
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/posts/$postId" -Headers $authHeaders
        "postId=$($response.data.postId), mediaCount=$($response.data.mediaCount)"
    } | Out-Null

    $mediaFeedResponse = $null
    Invoke-Step "media feed" {
        if (-not $accessToken) {
            throw "missing access token from login step"
        }
        $script:mediaFeedResponse = Invoke-ApiRequest -Method "GET" -Path "/api/media/feed" -Headers $authHeaders
        $mediaCount = @($script:mediaFeedResponse.data).Count
        if ($mediaCount -lt 1) {
            throw "media feed is empty"
        }
        "count=$mediaCount, firstMedia=$($script:mediaFeedResponse.data[0].mediaId)"
    } | Out-Null

    $mediaId = if ($mediaFeedResponse -and @($mediaFeedResponse.data).Count -gt 0) { $mediaFeedResponse.data[0].mediaId } else { "media_001" }

    Invoke-Step "post comments" {
        if (-not $accessToken) {
            throw "missing access token from login step"
        }
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/posts/post_001/comments?page=1&size=20" -Headers $authHeaders
        "count=$(@($response.data.comments).Count), hasMore=$($response.data.hasMore)"
    } | Out-Null

    Invoke-Step "media comments" {
        if (-not $accessToken) {
            throw "missing access token from login step"
        }
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/media/$mediaId/comments?page=1&size=20" -Headers $authHeaders
        "mediaId=$mediaId, count=$(@($response.data.comments).Count)"
    } | Out-Null

    $uploadTokenResponse = $null
    Invoke-Step "upload token" {
        if (-not $accessToken) {
            throw "missing access token from login step"
        }
        $script:uploadTokenResponse = Invoke-ApiRequest -Method "POST" -Path "/api/uploads/token" -Headers $authHeaders -JsonBody @{
            fileName = "integration-smoke.jpg"
            mimeType = "image/jpeg"
            fileSizeBytes = $fileBytes.Length
            mediaType = "image"
            width = 64
            height = 64
            durationMillis = $null
            displayTimeMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        }
        "uploadId=$($script:uploadTokenResponse.data.uploadId), state=$($script:uploadTokenResponse.data.state)"
    } | Out-Null

    $uploadId = if ($uploadTokenResponse) { $uploadTokenResponse.data.uploadId } else { $null }
    $uploadCompleteResponse = $null
    Invoke-Step "local upload" {
        if (-not $accessToken) {
            throw "missing access token from login step"
        }
        if (-not $uploadId) {
            throw "missing uploadId from upload token step"
        }
        $script:uploadCompleteResponse = Invoke-ApiRequest `
            -Method "POST" `
            -Path "/api/uploads/$uploadId/file" `
            -Headers $authHeaders `
            -MultipartFilePath $uploadTempPath `
            -MultipartContentType "image/jpeg"
        "state=$($script:uploadCompleteResponse.data.state), mediaId=$($script:uploadCompleteResponse.data.media.mediaId)"
    } | Out-Null

    Invoke-Step "trash list / detail / restore" {
        if (-not $accessToken) {
            throw "missing access token from login step"
        }
        $deleteResponse = Invoke-ApiRequest -Method "DELETE" -Path "/api/posts/post_001/media/media_002?deleteMode=directory" -Headers $authHeaders
        $script:trashItemId = Require-Value $deleteResponse.data.trashItemId "directory delete did not return trashItemId"
        $listResponse = Invoke-ApiRequest -Method "GET" -Path "/api/trash/items?itemType=mediaRemoved&page=1&size=20" -Headers $authHeaders
        $detailResponse = Invoke-ApiRequest -Method "GET" -Path "/api/trash/items/$trashItemId" -Headers $authHeaders
        $restoreResponse = Invoke-ApiRequest -Method "POST" -Path "/api/trash/items/$trashItemId/restore" -Headers $authHeaders
        $script:trashItemId = $null
        "trashCount=$(@($listResponse.data.items).Count), trashItem=$($detailResponse.data.item.trashItemId), restoreState=$($restoreResponse.data.state)"
    } | Out-Null
}
finally {
    if ($trashItemId) {
        try {
            $restoreHeaders = @{}
            if ($loginResponse -and $loginResponse.data.accessToken) {
                $restoreHeaders["Authorization"] = "Bearer $($loginResponse.data.accessToken)"
            }
            [void](Invoke-ApiRequest -Method "POST" -Path "/api/trash/items/$trashItemId/restore" -Headers $restoreHeaders)
        }
        catch {
            Write-Host "[WARN] 自动 restore 失败: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }

    if (Test-Path $uploadTempPath) {
        Remove-Item -LiteralPath $uploadTempPath -Force
    }
}

Write-Host ""
if ($failures.Count -eq 0) {
    Write-Host "Integration smoke completed with 0 failures." -ForegroundColor Green
    exit 0
}

Write-Host "Integration smoke completed with $($failures.Count) failure(s):" -ForegroundColor Red
foreach ($failure in $failures) {
    Write-Host " - $failure" -ForegroundColor Red
}
exit 1
