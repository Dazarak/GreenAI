# GreenAI - Diagnostic & RAG Assistant

Architecture découplée composée d'un moteur d'inférence AI Python local et d'un serveur API TypeScript / Node.js.

---

## 🛠️ PRÉREQUIS SYSTÈME

* **OS** : Linux (Debian / Ubuntu / dérivés)
* **Python** : 3.10+ avec support GPU (CUDA / PyTorch)
* **Node.js** : v20+ LTS (`node -v`) & `npm`
* **Réseau** : ZeroTier (accès distant sécurisé)

---

## 🚀 INSTALLATION & PRÉPARATION

### 1. Moteur Python (`ai_engine`)

```bash
# Activation de l'environnement virtuel local
source ai_engine/GreenAI/bin/activate

# Installation des dépendances Python
pip install -r requirements.txt
```

### 2. Serveur API TypeScript (api_server)

```bash
cd api_server
npm install
cd ..
```

### 3 Téléchargement et compression du model

#### 3.1 Récupération /< HACHAGE_DOSSIER >

```bash
ls ~/.cache/huggingface/hub/models--google--gemma-4-e4b-it/snapshots/
```

#### 3.2 Compression du modèle
```bash
cd ai_engine/llama.cpp

# Extraction du modèle de discussion depuis le cache HF
python3 convert_hf_to_gguf.py $(ls -d ~/.cache/huggingface/hub/models--google--gemma-4-e4b-it/snapshots/* | head -n 1) --outfile ../gemma-temp.gguf --outtype f16

# Extraction du projecteur vision (mmproj) depuis le cache HF
python3 convert_hf_to_gguf.py ~/.cache/huggingface/hub/models--google--gemma-4-e4b-it/snapshots/ee0ef6023621cff504d758262d4e04895a5af4a2 --outfile mmproj-gemma-f16.gguf --mmproj

# Compilation de l'outil de quantification
cmake -B build
cmake --build build --config Release -j$(nproc) --target llama-quantize

# Quantification du modèle (Q8_0 recommandé pour GPU 8Go VRAM)
./build/bin/llama-quantize ../gemma-temp.gguf ../gemma-4-e2b-it-Q8_0.gguf q8_0

# Nettoyage du fichier temporaire
rm ../gemma-temp.gguf
cd ..
```

## 🖥️ LANCEMENT DES SERVICES
### Moteur AI (Socket / Inférence local)


```bash
# Autoriser le port si nécessaire
sudo ufw allow 8080/tcp
```

### Lancer le moteur d'inférence

```bash
source ai_engine/GreenAI/bin/activate
python ai_engine/main.py
```

### Serveur API TypeScript

```Bash
cd api_server
npx ts-node src/index.ts
```

## 💾 GESTION DU RAG (ChromaDB)

### Réinitialiser la base de données vectorielle :

```bash
rm -rf chroma_db
```

## 🐳 DÉPLOIEMENT (À venir)
    [ ] Containerisation Docker (GPU Passthrough pour ai_engine, image Node.js pour api_server).

    [ ] Orchestration avec Docker Compose.


---