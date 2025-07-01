# Test de l'API de recherche vectorielle
Write-Host "🔍 Test de la recherche vectorielle avec Mistral" -ForegroundColor Green

$question = "je veux importer des chevaux destinés à la boucherie"
$encodedQuestion = [System.Web.HttpUtility]::UrlEncode($question)
$uri = "http://localhost:8080/chat?question=$encodedQuestion&sessionId=test-vectorielle"

Write-Host "`n📤 Envoi de la question: $question" -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri $uri -Method GET
    
    if ($response.StatusCode -eq 200) {
        Write-Host "`n✅ Réponse reçue (Status: $($response.StatusCode))" -ForegroundColor Green
        
        # Nettoyer et afficher la réponse
        $content = $response.Content | ConvertFrom-Json
        $cleanContent = $content[0] -replace '<br>', "`n" -replace '\\u[0-9A-F]{4}', ''
        
        Write-Host "`n📋 Contenu de la réponse:" -ForegroundColor Cyan
        Write-Host "=" * 60
        Write-Output $cleanContent
        Write-Host "=" * 60
        
        # Analyser si c'est une réponse LLM ou fallback
        if ($cleanContent -like "*📌*" -or $cleanContent -like "*📦*" -or $cleanContent -like "*📊*") {
            Write-Host "`n🤖 Mode LLM détecté - Mistral a généré cette réponse" -ForegroundColor Green
        } elseif ($cleanContent -like "*📋 Informations trouvées*") {
            Write-Host "`n⚙️ Mode Fallback détecté - Réponse basique générée" -ForegroundColor Yellow
        } else {
            Write-Host "`n🔍 Type de réponse: Non déterminé" -ForegroundColor Magenta
        }
        
    } else {
        Write-Host "`n❌ Erreur HTTP: $($response.StatusCode)" -ForegroundColor Red
    }
} catch {
    Write-Host "`n❌ Erreur lors de la requête: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n🔚 Test terminé" -ForegroundColor Blue
