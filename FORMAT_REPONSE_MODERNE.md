# 🎨 Format de Réponse Moderne du Chatbot

## 📋 Vue d'ensemble

Le chatbot utilise maintenant un format de réponse moderne avec emojis, couleurs et structure claire pour une meilleure expérience utilisateur.

## 🏗️ Structure de la Réponse

### 1. 🎯 Position Tarifaire (Bleu - #2563eb)
```
🎯 Position Tarifaire
Code SH : [Code à 10 chiffres exact]
Description : [Description complète du produit]
```

### 2. 💰 Droits et Taxes (Vert - #059669)
```
💰 Droits et Taxes
• Droit d'Importation (DI) : [Pourcentage]
• Taxe Parafiscale à l'Importation (TPI) : [Pourcentage]
• Taxe sur la Valeur Ajoutée à l'Importation (TVA) : [Pourcentage]
```

### 3. 🤝 Accords et Conventions (Violet - #7c3aed)
```
🤝 Accords et Conventions
• Union Européenne : [Taux]% de droit préférentiel
• Ligue Arabe : [Taux]% de droit préférentiel
• [Autres accords...]
```

### 4. 📋 Informations Complémentaires (Rouge - #dc2626)
```
📋 Informations Complémentaires
[Contingents, quotas, conditions spéciales, etc.]
```

## 🎨 Styling HTML

Le système applique automatiquement les styles suivants :

- **Titres** : Police agrandie (1.1em) avec couleurs spécifiques
- **Labels importants** : Texte en gras pour Code SH, Description, etc.
- **Puces** : Formatage avec • pour les listes
- **Espacements** : Sauts de ligne appropriés entre sections

## 💡 Exemple de Réponse Attendue

```
Pour l'importation de chevaux reproducteurs de race pure au Maroc, voici les informations clés à prendre en compte :

🎯 Position Tarifaire
Code SH : 0101210000
Description : Chevaux, ânes, mulets et bardots, vivants - Reproducteurs de race pure

💰 Droits et Taxes
• Droit d'Importation (DI) : 2,5%
• Taxe Parafiscale à l'Importation (TPI) : 0,25%
• Taxe sur la Valeur Ajoutée à l'Importation (TVA) : 0%

🤝 Accords et Conventions
• Union Européenne : 0% de droit préférentiel
• Ligue Arabe : 0% de droit préférentiel
• Accord d'Agadir : 0% de droit préférentiel
• États-Unis : 0% de droit préférentiel
[...]

📋 Informations Complémentaires
Les chevaux reproducteurs de race pure bénéficient d'un régime préférentiel...
```

## 🔧 Configuration Technique

- **Service** : `ChatService.java`
- **Méthode principale** : `construireMistralPrompt()`
- **Formatage** : `formatForWeb()`
- **LLM utilisé** : Mistral via Ollama

## 📝 Règles de Génération

1. **Exactitude** : Extraire les données exactement comme dans les documents
2. **Cohérence** : Respecter strictement le format avec emojis
3. **Complétude** : Lister TOUS les accords trouvés
4. **Clarté** : Si une info manque, indiquer "Non précisé dans les documents"

## 🎯 Codes SH Spéciaux

- **0101210000** : Chevaux reproducteurs de race pure
- **0101292000** : Chevaux de course âgés de moins de 6 ans
- **[Autres codes selon les documents]**
