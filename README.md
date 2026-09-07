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