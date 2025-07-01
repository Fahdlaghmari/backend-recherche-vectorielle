# 📚 Chatbot RAG - API Endpoints

Ce document regroupe tous les chemins d’API disponibles pour tester les différentes fonctionnalités du chatbot RAG.

---

## 📤 Upload de documents PDF

### ➕ Uploader un ou plusieurs fichiers PDF
- **Méthode**: `POST`
- **URL**: `http://localhost:8080/upload`
- **Body**: `form-data` → `files` (type: File, multiple)
- **Description**: Ingeste un ou plusieurs fichiers PDF.

---

## 💬 Poser une question

### 🔍 Interroger le chatbot avec une session
- **Méthode**: `GET`
- **URL**: `http://localhost:8080/chat?question=....?&sessionId=.....`
- **Paramètres**:
  - `question`: la question à poser
  - `sessionId`: identifiant de session (facultatif, défaut = "default")
- **Description**: Envoie une question à l’IA et obtient une réponse contextualisée à partir des documents.

---

## 📚 Historique de conversation

### 🧾 Récupérer l’historique d’une session
- **Méthode**: `GET`
- **URL**: `/chat/history?sessionId=MON_ID_SESSION`

### 🧹 Supprimer l’historique d’une session
- **Méthode**: `DELETE`
- **URL**: `/chat/history?sessionId=MON_ID_SESSION`

---

## 🧩 Gestion des chunks

### 📜 Lister tous les chunks
- **Méthode**: `GET`
- **URL**: `/chunks`

### 🔎 Récupérer un chunk spécifique
- **Méthode**: `GET`
- **URL**: `/chunks/{chunkId}`

### 🔍 Récupérer tous les chunks d’un document
- **Méthode**: `GET`
- **URL**: `/chunks/document/{documentId}`

---

## ❌ Suppression

### 🗑️ Supprimer un chunk spécifique
- **Méthode**: `DELETE`
- **URL**: `/delete/chunk/{chunkId}`

### 🧼 Supprimer un document entier (et tous ses chunks)
- **Méthode**: `DELETE`
- **URL**: `/delete/document/{documentId}`

---

## ℹ️ Remarques

- Tous les identifiants de documents ou chunks (`documentId`, `chunkId`) peuvent être récupérés via `/chunks` ou `/upload`.
- Le système détecte automatiquement la langue du document et de la question pour adapter la réponse.

---

🚀 Bon test avec Postman ou un navigateur REST client !
