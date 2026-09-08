import express from 'express';
import cors from 'cors';
import net from 'net';
import os from 'os';
import QRCode from 'qrcode';
import fs from 'fs';
import path from 'path';

import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function loadApiKey(): string {
  const possiblePaths = [
    path.join(process.cwd(), 'API_KEY.txt'),
    path.join(process.cwd(), '..', 'API_KEY.txt'),
    path.resolve(__dirname, '../API_KEY.txt'),
    path.resolve(__dirname, '../../API_KEY.txt')
  ];

  for (const keyPath of possiblePaths) {
    if (fs.existsSync(keyPath)) {
      try {
        let key = fs.readFileSync(keyPath, 'utf-8').trim();
        // Suppression des caractères non-ASCII (dont 0x1b) et retours à la ligne
        key = key.replace(/[^\x20-\x7E]/g, '');
        if (key.length > 0) {
          console.log(`[API] Clé chargée depuis : ${keyPath}`);
          return key;
        }
      } catch (err) {
        // Ignorer
      }
    }
  }

  console.warn("Aucun fichier API_KEY.txt valide trouvé.");
  return "";
}

const PORT = 8080;
const API_SECRET_KEY = loadApiKey();
const app = express();

app.use(cors());
app.use(express.json({ limit: '50mb' }));

export function envoyerAuMoteurAI(payload: object): Promise<any> {
  return new Promise((resolve, reject) => {
    const client = new net.Socket();
    let rawData = '';

    client.connect(5000, '127.0.0.1', () => {
      client.write(JSON.stringify(payload) + '\n');
    });

    client.on('data', (data) => {
      rawData += data.toString();
      if (rawData.endsWith('\n')) {
        client.end();
      }
    });

    client.on('end', () => {
      try {
        resolve(JSON.parse(rawData.trim()));
      } catch (err) {
        reject(new Error('Réponse invalide reçue du moteur AI'));
      }
    });

    client.on('error', (err) => {
      reject(new Error(`Impossible de joindre l'engine Python : ${err.message}`));
    });
  });
}

/**
 * Récupère l'IP v4 de l'interface ZeroTier (supporte Node 18+ et versions antérieures)
 */
function getZeroTierIP(): string | null {
  const interfaces = os.networkInterfaces();
  
  for (const name of Object.keys(interfaces)) {
    // Les interfaces ZeroTier commencent par zt (ex: ztpxxxxxxx)
    if (name.startsWith('zt')) {
      for (const netInterface of interfaces[name] || []) {
        const isIPv4 = netInterface.family === 'IPv4' || (netInterface.family as any) === 4;
        if (isIPv4 && !netInterface.internal) {
          return netInterface.address;
        }
      }
    }
  }
  return null;
}

// 1. ROUTE PUBLIQUE (Doit être impérativement déclarée AVANT le middleware de token)
app.get('/ping', (req, res) => {
  res.status(200).json({ status: 'OK', message: 'Pong' });
});

// 2. MIDDLEWARE DE SÉCURITÉ (S'applique aux routes déclarées en dessous)
app.use((req, res, next) => {
  const authHeader = req.headers.authorization;
  
  if (!authHeader || authHeader !== `Bearer ${API_SECRET_KEY}`) {
    return res.status(401).json({ error: "Accès refusé : Token invalide ou absent" });
  }
  
  next();
});

// Route protégée
app.post('/chat', async (req, res) => {
  try {
    const { question, base64_image, action } = req.body;
    console.log(`[API] Requête reçue : ${question}`);

    if (!question && !action) {
      return res.status(400).json({ error: 'La question ou une action est requise.' });
    }

    const reponse = await envoyerAuMoteurAI({
      question,
      base64_image,
      action
    });

    res.json(reponse);
    console.log(`[API] Réponse envoyée`);
  } catch (err: any) {
    res.status(500).json({ error: err.message });
  }
});

const ztIP = getZeroTierIP() || '127.0.0.1';

// Lancement du serveur sur l'ensemble des cartes réseau (0.0.0.0)
app.listen(PORT, '0.0.0.0', () => {
  console.log(`[API SERVER] Écoute sur http://0.0.0.0:${PORT}`);
  
  const connectionPayload = JSON.stringify({
    ip: ztIP,
    port: PORT,
    token: API_SECRET_KEY
  });

  console.log("\n==================================================");
  console.log(`IP ZEROTIER DÉTECTÉE : ${ztIP}`);
  console.log("Scanne ce QR code avec l'application mobile :");
  console.log("==================================================\n");

  // inverse: false assure les modules noirs sur fond clair
  QRCode.toString(connectionPayload, { type: 'terminal', inverse: false }, (err, url) => {
    if (err) console.error("Erreur QR Code :", err);
    else console.log(url);
  });
});