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
$loginResponse = $null
$refreshResponse = $null
$accessToken = $null
$refreshToken = $null
$currentUserId = $null

Add-Type -AssemblyName System.Net.Http

function Get-HttpMethod([string]$Method) {
    switch ($Method.ToUpperInvariant()) {
        "GET" { return [System.Net.Http.HttpMethod]::Get }
        "POST" { return [System.Net.Http.HttpMethod]::Post }
        "PATCH" { return [System.Net.Http.HttpMethod]::new("PATCH") }
        "DELETE" { return [System.Net.Http.HttpMethod]::Delete }
        default { throw "Unsupported HTTP method: $Method" }
    }
}

function New-Client {
    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(30)
    return $client
}

function Get-AuthHeaders {
    if ([string]::IsNullOrWhiteSpace($script:accessToken)) {
        return @{}
    }
    return @{
        Authorization = "Bearer $($script:accessToken)"
    }
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

function Invoke-ApiBinaryRequest {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{}
    )

    $client = New-Client
    $request = [System.Net.Http.HttpRequestMessage]::new((Get-HttpMethod $Method), "$BaseUrl$Path")
    try {
        foreach ($headerName in $Headers.Keys) {
            [void]$request.Headers.TryAddWithoutValidation($headerName, [string]$Headers[$headerName])
        }

        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            $text = [System.Text.Encoding]::UTF8.GetString($bytes)
            throw "HTTP $([int]$response.StatusCode) $($response.ReasonPhrase): $text"
        }
        return [pscustomobject]@{
            Bytes = $bytes
            Length = $bytes.Length
            ContentType = [string]$response.Content.Headers.ContentType
        }
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
    $jpegBase64 = "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD9U6KKKAP/2Q=="
    $fileBytes = [Convert]::FromBase64String($jpegBase64)
    [System.IO.File]::WriteAllBytes($uploadTempPath, $fileBytes)

    Invoke-Step "health" {
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/health"
        if ($response.data.status -ne "UP") {
            throw "expected UP but got $($response.data.status)"
        }
        "status=$($response.data.status), app=$($response.data.application)"
    } | Out-Null

    Invoke-Step "login token" {
        $script:loginResponse = Invoke-ApiRequest -Method "POST" -Path "/api/auth/login" -JsonBody @{
            account = $Account
            password = $Password
        }
        $script:accessToken = Require-Value $script:loginResponse.data.accessToken "login succeeded but accessToken is empty"
        $script:refreshToken = Require-Value $script:loginResponse.data.refreshToken "login succeeded but refreshToken is empty"
        $script:currentUserId = Require-Value $script:loginResponse.data.userId "login succeeded but userId is empty"
        "userId=$($script:currentUserId), tokenLength=$($script:accessToken.Length)"
    } | Out-Null

    Invoke-Step "refresh token" {
        if (-not $script:refreshToken) {
            throw "missing refresh token from login step"
        }
        $script:refreshResponse = Invoke-ApiRequest -Method "POST" -Path "/api/auth/refresh-token" -JsonBody @{
            refreshToken = $script:refreshToken
        }
        $script:accessToken = Require-Value $script:refreshResponse.data.accessToken "refresh succeeded but accessToken is empty"
        $script:refreshToken = Require-Value $script:refreshResponse.data.refreshToken "refresh succeeded but refreshToken is empty"
        "accessTokenLength=$($script:accessToken.Length), refreshTokenLength=$($script:refreshToken.Length)"
    } | Out-Null

    Invoke-Step "me" {
        if (-not $script:accessToken) {
            throw "missing access token from login step"
        }
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/auth/me" -Headers (Get-AuthHeaders)
        $script:currentUserId = Require-Value $response.data.userId "me response missing userId"
        "userId=$($response.data.userId), libraryId=$($response.data.libraryId)"
    } | Out-Null

    Invoke-Step "avatar upload" {
        if (-not $script:currentUserId) {
            throw "missing current user id"
        }
        $response = Invoke-ApiRequest `
            -Method "POST" `
            -Path "/api/auth/me/avatar" `
            -Headers (Get-AuthHeaders) `
            -MultipartFilePath $uploadTempPath `
            -MultipartContentType "image/jpeg"
        $avatarUrl = Require-Value $response.data.avatarUrl "avatar upload did not return avatarUrl"
        "avatarUrl=$avatarUrl"
    } | Out-Null

    Invoke-Step "avatar fetch" {
        if (-not $script:currentUserId) {
            throw "missing current user id"
        }
        $response = Invoke-ApiBinaryRequest `
            -Method "GET" `
            -Path "/api/auth/avatar/$($script:currentUserId)" `
            -Headers (Get-AuthHeaders)
        if ($response.ContentType -notlike "image/jpeg*") {
            throw "expected image/jpeg but got $($response.ContentType)"
        }
        if ($response.Length -lt 1) {
            throw "avatar response body is empty"
        }
        "contentType=$($response.ContentType), bytes=$($response.Length)"
    } | Out-Null

    $albumsResponse = $null
    Invoke-Step "albums" {
        $script:albumsResponse = Invoke-ApiRequest -Method "GET" -Path "/api/albums" -Headers (Get-AuthHeaders)
        $albumCount = @($script:albumsResponse.data).Count
        if ($albumCount -lt 1) {
            throw "album list is empty"
        }
        "count=$albumCount, firstAlbum=$($script:albumsResponse.data[0].albumId)"
    } | Out-Null

    $albumId = if ($albumsResponse -and @($albumsResponse.data).Count -gt 0) {
        $albumsResponse.data[0].albumId
    } else {
        "album_001"
    }

    $albumPostsResponse = $null
    Invoke-Step "album posts" {
        $script:albumPostsResponse = Invoke-ApiRequest -Method "GET" -Path "/api/albums/$albumId/posts" -Headers (Get-AuthHeaders)
        $postCount = @($script:albumPostsResponse.data).Count
        if ($postCount -lt 1) {
            throw "album $albumId has no posts"
        }
        "album=$albumId, count=$postCount, firstPost=$($script:albumPostsResponse.data[0].postId)"
    } | Out-Null

    $postsListResponse = $null
    Invoke-Step "posts list" {
        $script:postsListResponse = Invoke-ApiRequest -Method "GET" -Path "/api/posts" -Headers (Get-AuthHeaders)
        $postCount = @($script:postsListResponse.data).Count
        if ($postCount -lt 1) {
            throw "posts list is empty"
        }
        "count=$postCount, firstPost=$($script:postsListResponse.data[0].postId)"
    } | Out-Null

    $postId = if ($albumPostsResponse -and @($albumPostsResponse.data).Count -gt 0) {
        $albumPostsResponse.data[0].postId
    } elseif ($postsListResponse -and @($postsListResponse.data).Count -gt 0) {
        $postsListResponse.data[0].postId
    } else {
        "post_001"
    }

    $postDetailResponse = $null
    Invoke-Step "post detail" {
        $script:postDetailResponse = Invoke-ApiRequest -Method "GET" -Path "/api/posts/$postId" -Headers (Get-AuthHeaders)
        $mediaIds = @($script:postDetailResponse.data.mediaItems | ForEach-Object { $_.media.mediaId })
        "postId=$($script:postDetailResponse.data.postId), mediaCount=$($mediaIds.Count)"
    } | Out-Null

    $originalTitle = if ($postDetailResponse) { [string]$postDetailResponse.data.title } else { "" }
    $originalSummary = if ($postDetailResponse) { [string]$postDetailResponse.data.summary } else { "" }
    $originalDisplayTimeMillis = if ($postDetailResponse) { [long]$postDetailResponse.data.displayTimeMillis } else { [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }
    $originalAlbumIds = if ($postDetailResponse) { @($postDetailResponse.data.albumIds) } else { @($albumId) }
    $originalMediaIds = if ($postDetailResponse) { @($postDetailResponse.data.mediaItems | ForEach-Object { $_.media.mediaId }) } else { @() }
    $originalCoverMediaId = if ($postDetailResponse) { [string]$postDetailResponse.data.coverMediaId } else { $null }
    $managementPostId = $postId
    $managementPostDetailResponse = $postDetailResponse

    if (@($originalMediaIds).Count -lt 2 -and $albumsResponse) {
        foreach ($album in @($albumsResponse.data)) {
            $candidatePostsResponse = Invoke-ApiRequest -Method "GET" -Path "/api/albums/$($album.albumId)/posts" -Headers (Get-AuthHeaders)
            foreach ($candidatePost in @($candidatePostsResponse.data)) {
                $candidateDetail = Invoke-ApiRequest -Method "GET" -Path "/api/posts/$($candidatePost.postId)" -Headers (Get-AuthHeaders)
                $candidateMediaIds = @($candidateDetail.data.mediaItems | ForEach-Object { $_.media.mediaId })
                if ($candidateMediaIds.Count -ge 2) {
                    $managementPostId = $candidatePost.postId
                    $managementPostDetailResponse = $candidateDetail
                    break
                }
            }
            if ($managementPostId -ne $postId) {
                break
            }
        }
    }

    if ($managementPostDetailResponse) {
        $originalTitle = [string]$managementPostDetailResponse.data.title
        $originalSummary = [string]$managementPostDetailResponse.data.summary
        $originalDisplayTimeMillis = [long]$managementPostDetailResponse.data.displayTimeMillis
        $originalAlbumIds = @($managementPostDetailResponse.data.albumIds)
        $originalMediaIds = @($managementPostDetailResponse.data.mediaItems | ForEach-Object { $_.media.mediaId })
        $originalCoverMediaId = [string]$managementPostDetailResponse.data.coverMediaId
    }

    Invoke-Step "post update" {
        $updatedTitle = "联调更新标题"
        $updatedSummary = "联调脚本基础信息更新测试"
        $updated = Invoke-ApiRequest -Method "PATCH" -Path "/api/posts/$managementPostId" -Headers (Get-AuthHeaders) -JsonBody @{
            title = $updatedTitle
            summary = $updatedSummary
            contributorLabel = $null
            displayTimeMillis = $originalDisplayTimeMillis
            albumIds = $originalAlbumIds
        }
        if ($updated.data.title -ne $updatedTitle) {
            throw "post title was not updated"
        }
        [void](Invoke-ApiRequest -Method "PATCH" -Path "/api/posts/$managementPostId" -Headers (Get-AuthHeaders) -JsonBody @{
            title = $originalTitle
            summary = $originalSummary
            contributorLabel = $null
            displayTimeMillis = $originalDisplayTimeMillis
            albumIds = $originalAlbumIds
        })
        "postId=$managementPostId, titleUpdated=$updatedTitle"
    } | Out-Null

    Invoke-Step "post cover" {
        if (@($originalMediaIds).Count -lt 2) {
            return "skipped: mediaCount=$(@($originalMediaIds).Count)"
        }
        $newCoverMediaId = if ($originalCoverMediaId -eq $originalMediaIds[0]) { $originalMediaIds[1] } else { $originalMediaIds[0] }
        $updated = Invoke-ApiRequest -Method "PATCH" -Path "/api/posts/$managementPostId/cover" -Headers (Get-AuthHeaders) -JsonBody @{
            coverMediaId = $newCoverMediaId
        }
        if ($updated.data.coverMediaId -ne $newCoverMediaId) {
            throw "coverMediaId was not updated"
        }
        [void](Invoke-ApiRequest -Method "PATCH" -Path "/api/posts/$managementPostId/cover" -Headers (Get-AuthHeaders) -JsonBody @{
            coverMediaId = $originalCoverMediaId
        })
        "postId=$managementPostId, coverMediaId=$newCoverMediaId"
    } | Out-Null

    Invoke-Step "post media order" {
        if (@($originalMediaIds).Count -lt 2) {
            return "skipped: mediaCount=$(@($originalMediaIds).Count)"
        }
        $reorderedMediaIds = [string[]]$originalMediaIds.Clone()
        [array]::Reverse($reorderedMediaIds)
        $updated = Invoke-ApiRequest -Method "PATCH" -Path "/api/posts/$managementPostId/media-order" -Headers (Get-AuthHeaders) -JsonBody @{
            orderedMediaIds = $reorderedMediaIds
        }
        $responseOrder = @($updated.data.mediaItems | ForEach-Object { $_.media.mediaId })
        if (($responseOrder -join ",") -ne ($reorderedMediaIds -join ",")) {
            throw "media order was not updated"
        }
        [void](Invoke-ApiRequest -Method "PATCH" -Path "/api/posts/$managementPostId/media-order" -Headers (Get-AuthHeaders) -JsonBody @{
            orderedMediaIds = $originalMediaIds
        })
        "postId=$managementPostId, order=$($reorderedMediaIds -join ' > ')"
    } | Out-Null

    $mediaFeedResponse = $null
    Invoke-Step "media feed" {
        $script:mediaFeedResponse = Invoke-ApiRequest -Method "GET" -Path "/api/media/feed" -Headers (Get-AuthHeaders)
        $mediaCount = @($script:mediaFeedResponse.data).Count
        if ($mediaCount -lt 1) {
            throw "media feed is empty"
        }
        "count=$mediaCount, firstMedia=$($script:mediaFeedResponse.data[0].mediaId)"
    } | Out-Null

    $mediaId = if ($mediaFeedResponse -and @($mediaFeedResponse.data).Count -gt 0) {
        $mediaFeedResponse.data[0].mediaId
    } else {
        "media_001"
    }

    Invoke-Step "post comments" {
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/posts/$managementPostId/comments?page=1&size=20" -Headers (Get-AuthHeaders)
        "count=$(@($response.data.comments).Count), hasMore=$($response.data.hasMore)"
    } | Out-Null

    Invoke-Step "media comments" {
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/media/$mediaId/comments?page=1&size=20" -Headers (Get-AuthHeaders)
        "mediaId=$mediaId, count=$(@($response.data.comments).Count)"
    } | Out-Null

    $uploadTokenResponse = $null
    Invoke-Step "upload token" {
        $script:uploadTokenResponse = Invoke-ApiRequest -Method "POST" -Path "/api/uploads/token" -Headers (Get-AuthHeaders) -JsonBody @{
            fileName = "integration-smoke.jpg"
            mimeType = "image/jpeg"
            fileSizeBytes = $fileBytes.Length
            mediaType = "image"
            width = 1
            height = 1
            durationMillis = $null
            displayTimeMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        }
        "uploadId=$($script:uploadTokenResponse.data.uploadId), state=$($script:uploadTokenResponse.data.state)"
    } | Out-Null

    $uploadId = if ($uploadTokenResponse) { $uploadTokenResponse.data.uploadId } else { $null }
    $uploadStatusBeforeResponse = $null
    Invoke-Step "upload task status" {
        if (-not $uploadId) {
            throw "missing uploadId from upload token step"
        }
        $script:uploadStatusBeforeResponse = Invoke-ApiRequest -Method "GET" -Path "/api/uploads/$uploadId" -Headers (Get-AuthHeaders)
        if ($script:uploadStatusBeforeResponse.data.state -ne "waiting") {
            throw "expected waiting but got $($script:uploadStatusBeforeResponse.data.state)"
        }
        "uploadId=$uploadId, state=$($script:uploadStatusBeforeResponse.data.state)"
    } | Out-Null

    $uploadCompleteResponse = $null
    Invoke-Step "local upload" {
        if (-not $uploadId) {
            throw "missing uploadId from upload token step"
        }
        $script:uploadCompleteResponse = Invoke-ApiRequest `
            -Method "POST" `
            -Path "/api/uploads/$uploadId/file" `
            -Headers (Get-AuthHeaders) `
            -MultipartFilePath $uploadTempPath `
            -MultipartContentType "image/jpeg"
        "state=$($script:uploadCompleteResponse.data.state), mediaId=$($script:uploadCompleteResponse.data.media.mediaId)"
    } | Out-Null

    Invoke-Step "upload confirm" {
        if (-not $uploadId) {
            throw "missing uploadId from upload token step"
        }
        $response = Invoke-ApiRequest -Method "POST" -Path "/api/uploads/$uploadId/confirm" -Headers (Get-AuthHeaders)
        if ($response.data.state -ne "success") {
            throw "expected success but got $($response.data.state)"
        }
        "uploadId=$uploadId, state=$($response.data.state)"
    } | Out-Null

    Invoke-Step "upload task status after upload" {
        if (-not $uploadId) {
            throw "missing uploadId from upload token step"
        }
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/uploads/$uploadId" -Headers (Get-AuthHeaders)
        if ($response.data.state -ne "success") {
            throw "expected success but got $($response.data.state)"
        }
        "uploadId=$uploadId, state=$($response.data.state), progress=$($response.data.progressPercent)"
    } | Out-Null

    $cancelUploadTokenResponse = $null
    Invoke-Step "upload token for cancel" {
        $script:cancelUploadTokenResponse = Invoke-ApiRequest -Method "POST" -Path "/api/uploads/token" -Headers (Get-AuthHeaders) -JsonBody @{
            fileName = "integration-smoke-cancel.jpg"
            mimeType = "image/jpeg"
            fileSizeBytes = $fileBytes.Length
            mediaType = "image"
            width = 1
            height = 1
            durationMillis = $null
            displayTimeMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        }
        "uploadId=$($script:cancelUploadTokenResponse.data.uploadId), state=$($script:cancelUploadTokenResponse.data.state)"
    } | Out-Null

    $cancelUploadId = if ($cancelUploadTokenResponse) { $cancelUploadTokenResponse.data.uploadId } else { $null }
    Invoke-Step "upload cancel" {
        if (-not $cancelUploadId) {
            throw "missing uploadId from cancel token step"
        }
        $response = Invoke-ApiRequest -Method "POST" -Path "/api/uploads/$cancelUploadId/cancel" -Headers (Get-AuthHeaders)
        if ($response.data.state -ne "cancelled") {
            throw "expected cancelled but got $($response.data.state)"
        }
        "uploadId=$cancelUploadId, state=$($response.data.state)"
    } | Out-Null

    Invoke-Step "upload task status after cancel" {
        if (-not $cancelUploadId) {
            throw "missing uploadId from cancel token step"
        }
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/uploads/$cancelUploadId" -Headers (Get-AuthHeaders)
        if ($response.data.state -ne "cancelled") {
            throw "expected cancelled but got $($response.data.state)"
        }
        "uploadId=$cancelUploadId, state=$($response.data.state), progress=$($response.data.progressPercent)"
    } | Out-Null

    Invoke-Step "trash list / detail / restore" {
        $trashMediaId = if ($uploadCompleteResponse) { $uploadCompleteResponse.data.media.mediaId } else { $null }
        if (-not $trashMediaId) {
            throw "missing uploaded mediaId from local upload step"
        }
        $deleteResponse = Invoke-ApiRequest -Method "DELETE" -Path "/api/media/$trashMediaId" -Headers (Get-AuthHeaders)
        $script:trashItemId = Require-Value $deleteResponse.data.trashItemId "system delete did not return trashItemId"
        $listResponse = Invoke-ApiRequest -Method "GET" -Path "/api/trash/items?itemType=mediaSystemDeleted&page=1&size=20" -Headers (Get-AuthHeaders)
        $detailResponse = Invoke-ApiRequest -Method "GET" -Path "/api/trash/items/$($script:trashItemId)" -Headers (Get-AuthHeaders)
        $restoreResponse = Invoke-ApiRequest -Method "POST" -Path "/api/trash/items/$($script:trashItemId)/restore" -Headers (Get-AuthHeaders)
        $script:trashItemId = $null
        "trashCount=$(@($listResponse.data.items).Count), mediaId=$trashMediaId, trashItem=$($detailResponse.data.item.trashItemId), restoreState=$($restoreResponse.data.state)"
    } | Out-Null

    $notificationsResponse = $null
    Invoke-Step "notifications list" {
        $script:notificationsResponse = Invoke-ApiRequest -Method "GET" -Path "/api/notifications?limit=10" -Headers (Get-AuthHeaders)
        $count = @($script:notificationsResponse.data).Count
        if ($count -lt 1) {
            throw "notification list is empty"
        }
        "count=$count, firstNotification=$($script:notificationsResponse.data[0].notificationId)"
    } | Out-Null

    $notificationId = if ($notificationsResponse -and @($notificationsResponse.data).Count -gt 0) {
        $notificationsResponse.data[0].notificationId
    } else {
        $null
    }

    Invoke-Step "notification detail" {
        if (-not $notificationId) {
            throw "missing notificationId from list step"
        }
        $response = Invoke-ApiRequest -Method "GET" -Path "/api/notifications/$notificationId" -Headers (Get-AuthHeaders)
        if ($response.data.notificationId -ne $notificationId) {
            throw "notification detail id mismatch"
        }
        "notificationId=$notificationId, type=$($response.data.type)"
    } | Out-Null

    Invoke-Step "notification read" {
        if (-not $notificationId) {
            throw "missing notificationId from list step"
        }
        $response = Invoke-ApiRequest -Method "POST" -Path "/api/notifications/$notificationId/read" -Headers (Get-AuthHeaders)
        if (-not $response.data.isRead) {
            throw "notification was not marked as read"
        }
        "notificationId=$notificationId, isRead=$($response.data.isRead)"
    } | Out-Null

    Invoke-Step "notification read all" {
        $response = Invoke-ApiRequest -Method "POST" -Path "/api/notifications/read-all" -Headers (Get-AuthHeaders)
        if (-not $response.data.success) {
            throw "read-all did not return success"
        }
        "success=$($response.data.success), affectedCount=$($response.data.affectedCount)"
    } | Out-Null
}
finally {
    if ($trashItemId) {
        try {
            [void](Invoke-ApiRequest -Method "POST" -Path "/api/trash/items/$trashItemId/restore" -Headers (Get-AuthHeaders))
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
