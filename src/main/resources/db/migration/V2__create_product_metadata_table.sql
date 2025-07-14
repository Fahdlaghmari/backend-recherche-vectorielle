-- Migration pour créer la table product_metadata
-- Cette table stocke les métadonnées structurées des produits alimentaires

CREATE TABLE product_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code_sh VARCHAR(10) NOT NULL UNIQUE,
    description TEXT,
    
    -- Métadonnées normalisées pour filtrage
    type_produit VARCHAR(50),
    etat_produit VARCHAR(20),
    desossage VARCHAR(20),
    age_animal VARCHAR(50),
    partie_anatomique VARCHAR(100),
    usage_specifique VARCHAR(100),
    
    -- Droits de douane
    droit_importation DECIMAL(5,2),
    tpi DECIMAL(5,2),
    tva DECIMAL(5,2),
    
    -- Préférences tarifaires et quotas
    preferentiels JSON,
    quotas TEXT,
    
    -- Relation avec le chunk
    chunk_id VARCHAR(255) UNIQUE,
    
    -- Métadonnées additionnelles pour recherche
    mots_cles_normalises TEXT,
    synonymes TEXT,
    
    -- Contraintes
    CONSTRAINT fk_product_metadata_chunk 
        FOREIGN KEY (chunk_id) REFERENCES chunk(id) 
        ON DELETE CASCADE,
    
    -- Index pour améliorer les performances
    INDEX idx_code_sh (code_sh),
    INDEX idx_type_produit (type_produit),
    INDEX idx_etat_produit (etat_produit),
    INDEX idx_desossage (desossage),
    INDEX idx_usage_specifique (usage_specifique),
    INDEX idx_droit_importation (droit_importation),
    INDEX idx_chunk_id (chunk_id)
);

-- Commentaires pour documentation
ALTER TABLE product_metadata 
    COMMENT = 'Table des métadonnées structurées pour les produits alimentaires importés';

ALTER TABLE product_metadata 
    MODIFY COLUMN code_sh VARCHAR(10) NOT NULL UNIQUE 
    COMMENT 'Code SH à 10 chiffres du produit';

ALTER TABLE product_metadata 
    MODIFY COLUMN type_produit VARCHAR(50) 
    COMMENT 'Type de produit: bovine, porcine, ovine, caprine, etc.';

ALTER TABLE product_metadata 
    MODIFY COLUMN etat_produit VARCHAR(20) 
    COMMENT 'État du produit: frais, congele, refrigere';

ALTER TABLE product_metadata 
    MODIFY COLUMN desossage VARCHAR(20) 
    COMMENT 'État de désossage: desossee, non_desossee, avec_os, sans_os';

ALTER TABLE product_metadata 
    MODIFY COLUMN preferentiels JSON 
    COMMENT 'Accords préférentiels au format JSON';

ALTER TABLE product_metadata 
    MODIFY COLUMN mots_cles_normalises TEXT 
    COMMENT 'Mots-clés normalisés pour la recherche vectorielle';

ALTER TABLE product_metadata 
    MODIFY COLUMN synonymes TEXT 
    COMMENT 'Synonymes pour améliorer la recherche';
