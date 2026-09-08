#!/bin/bash

# Arrêter le script en cas d'erreur
set -e

echo "=== Initialisation de GreenAI ==="

# 1. Vérification de Python 3 et Node.js
echo "--> Vérification des dépendances système..."
if ! command -v python3 &> /dev/null; then
    echo "Erreur : Python3 n'est pas installé."
    exit 1
fi

if ! command -v node &> /dev/null; then
    echo "Erreur : Node.js n'est pas installé."
    exit 1
fi

# 2. Configuration du fichier API_KEY.txt
echo "--> Gestion de la clé API..."
if [ ! -f "API_KEY.txt" ]; then
    read -p "Entrez la clé API pour sécuriser le serveur (ou Entrée pour en générer une) : " USER_KEY
    if [ -z "$USER_KEY" ]; then
        USER_KEY=$(openssl rand -hex 16)
        echo "Clé générée automatiquement : $USER_KEY"
    fi
    echo "$USER_KEY" > API_KEY.txt
    echo "Fichier API_KEY.txt créé."
else
    echo "Fichier API_KEY.txt déjà présent."
fi

# 3. Setup de l'environnement Python (ai_engine)
echo "--> Configuration du moteur Python..."
if [ ! -d "ai_engine/GreenAI" ]; then
    echo "Création de venv Python..."
    python3 -m venv ai_engine/GreenAI
fi

echo "Installation des paquets Python..."
source ai_engine/GreenAI/bin/activate
pip install --upgrade pip
if [ -f "requirements.txt" ]; then
    pip install -r requirements.txt
elif [ -f "ai_engine/requirements.txt" ]; then
    pip install -r ai_engine/requirements.txt
fi
deactivate

# 4. Setup du serveur Node.js (api_server)
echo "--> Configuration du serveur API Node.js..."
if [ -d "api_server" ]; then
    cd api_server
    npm install
    cd ..
else
    echo "Erreur : Dossier api_server introuvable !"
    exit 1
fi

echo ""
echo "=== Installation terminée avec succès ! ==="
echo "Pour lancer le moteur Python : source ai_engine/GreenAI/bin/activate && python ai_engine/main.py"
echo "Pour lancer le serveur API   : cd api_server && npm run dev"