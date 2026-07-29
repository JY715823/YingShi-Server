Set-Location "e:\Study\App\YingShi-Server"
$testClasses = "UploadStateMachineTest,CursorCodecTest,LogSanitizerTest,SseControllerIntegrationTest,NotificationEventControllerIntegrationTest,ReleaseControllerIntegrationTest,DtoValidationTest"
$output = .\mvnw.cmd test "-Dtest=$testClasses" "-DfailIfNoTests=false" 2>&1
$output | Select-Object -Last 120
exit $LASTEXITCODE
