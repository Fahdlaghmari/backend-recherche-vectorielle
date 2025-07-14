# Script de sauvegarde avant migration
# Sauvegarde les données importantes avant la mise à jour

Write-Host "🔄 Début de la sauvegarde avant migration..." -ForegroundColor Yellow

# Configuration de la base de données (à adapter selon votre configuration)
$dbHost = "localhost"
$dbPort = "3306"
$dbName = "chatbot_rag"  # Remplacer par le nom de votre base
$dbUser = "root"         # Remplacer par votre utilisateur
$backupPath = "C:\Users\PC\Desktop\pfe\backend\backup"

# Créer le dossier de sauvegarde
if (-not (Test-Path $backupPath)) {
    New-Item -ItemType Directory -Path $backupPath
}

# Nom du fichier de sauvegarde avec timestamp
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$backupFile = "$backupPath\backup_$timestamp.sql"

Write-Host "📁 Dossier de sauvegarde: $backupPath" -ForegroundColor Green
Write-Host "📄 Fichier de sauvegarde: $backupFile" -ForegroundColor Green

# Commande mysqldump (nécessite MySQL client installé)
# Décommentez et adaptez selon votre environnement
# $mysqldumpPath = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
# & $mysqldumpPath -h $dbHost -P $dbPort -u $dbUser -p $dbName > $backupFile

# Alternative: Afficher les commandes pour sauvegarde manuelle
Write-Host "💡 Commandes de sauvegarde manuelle:" -ForegroundColor Cyan
Write-Host "mysqldump -h $dbHost -P $dbPort -u $dbUser -p $dbName > $backupFile"
Write-Host ""

# Vérifier les tables existantes
Write-Host "📊 Tables actuelles dans la base:" -ForegroundColor Cyan
Write-Host "- chunk"
Write-Host "- document" 
Write-Host "- admin_user"
Write-Host "- product_metadata (sera créée)"
Write-Host ""

# Informations importantes
Write-Host "⚠️  IMPORTANT:" -ForegroundColor Red
Write-Host "1. Sauvegardez votre base de données avant de continuer"
Write-Host "2. Notez le nombre de chunks actuels"
Write-Host "3. Préparez-vous à re-uploader les documents"
Write-Host ""

# Vérifier la présence des nouveaux services
$servicesPath = "C:\Users\PC\Desktop\pfe\backend\src\main\java\com\example\chatbotrag\service"
$newServices = @(
    "ProductMetadataExtractionService.java",
    "HybridSearchService.java"
)

Write-Host "🔍 Vérification des nouveaux services:" -ForegroundColor Cyan
foreach ($service in $newServices) {
    $servicePath = Join-Path $servicesPath $service
    if (Test-Path $servicePath) {
        Write-Host "✅ $service" -ForegroundColor Green
    } else {
        Write-Host "❌ $service - MANQUANT" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "🚀 Prêt pour la migration!" -ForegroundColor Green
Write-Host "Prochaines étapes:"
Write-Host "1. Redémarrer l'application (la table sera créée automatiquement)"
Write-Host "2. Supprimer les anciens documents dans l'interface admin"
Write-Host "3. Re-uploader les documents pour générer les métadonnées"
