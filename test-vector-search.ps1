# Test Vector Search for Chevaux de Course
Write-Host "🔍 Testing Vector Search for 'chevaux de course'" -ForegroundColor Green

$question = "je veux importer des chevaux de course"
$encodedQuestion = [System.Web.HttpUtility]::UrlEncode($question)
$uri = "http://localhost:8080/chat?question=$encodedQuestion`&sessionId=test-vector-debug"

Write-Host "`n📤 Sending question: $question" -ForegroundColor Yellow
Write-Host "📡 URI: $uri" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri $uri -Method GET
    
    if ($response.StatusCode -eq 200) {
        Write-Host "`n✅ Response received (Status: $($response.StatusCode))" -ForegroundColor Green
        
        $content = $response.Content | ConvertFrom-Json
        $cleanContent = $content[0] -replace '<br>', "`n" -replace '\\u[0-9A-F]{4}', ''
        
        Write-Host "`n📋 Response Content:" -ForegroundColor Cyan
        Write-Host "=" * 80
        Write-Output $cleanContent
        Write-Host "=" * 80
        
        # Check if it contains expected content
        if ($cleanContent -like "*0101292000*") {
            Write-Host "`n✅ SUCCESS: Found SH code 0101292000!" -ForegroundColor Green
        } else {
            Write-Host "`n❌ PROBLEM: SH code 0101292000 not found in response" -ForegroundColor Red
        }
        
        if ($cleanContent -like "*de course*") {
            Write-Host "✅ SUCCESS: Found 'de course' reference!" -ForegroundColor Green
        } else {
            Write-Host "❌ PROBLEM: 'de course' not found in response" -ForegroundColor Red
        }
        
        if ($cleanContent -like "*Mistral*" -or $cleanContent -like "*Position Tarifaire*") {
            Write-Host "✅ SUCCESS: Using Mistral LLM mode!" -ForegroundColor Green
        } else {
            Write-Host "❌ PROBLEM: Using fallback mode instead of Mistral" -ForegroundColor Red
        }
        
    } else {
        Write-Host "`n❌ HTTP Error: $($response.StatusCode)" -ForegroundColor Red
    }
} catch {
    Write-Host "`n❌ Request Error: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n🔚 Test completed" -ForegroundColor Blue
