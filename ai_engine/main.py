# Lib pour fonctionner
import base64
import json
import socket
from io import BytesIO
import torch
from PIL import Image
from transformers import AutoProcessor, Qwen2VLForConditionalGeneration
import qrcode
import os
# Lib locale
from rag import ajouter_souvenir, chercher_souvenirs, supprimer_souvenir

# VARIABLE CONSTANTE
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_ID = os.path.join(os.path.dirname(os.path.abspath(__file__)), "qwen2_vl_7b_4bit")
PORT = 5000

SYSTEM_PROMPT = """Tu es un assistant IA. Tu dois TOUJOURS répondre au format JSON strict.

Règles pour les actions RAG :
- "action_rag": Remplis avec un fait court UNIQUEMENT si l'utilisateur te donne un fait personnel, une préférence, ou un NOM pour un objet/plante (ex: "Je vais appeler mon arbuste Trou de balle" -> "L'arbuste de l'utilisateur s'appelle Trou de balle"). Sinon "null".
- "action_oubli": Si l'utilisateur te demande d'oublier une info, extrais le fait à effacer. Sinon "null".

Structure JSON obligatoire :
{
  "action_rag": "Information à retenir ou null",
  "action_oubli": "Information à effacer ou null",
  "nouvelle_memoire": "Résumé court du contexte actuel (30 mots max)",
  "response": "Ta réponse à l'utilisateur"
}"""

# VARIABLE FONCTIONNEMENT
print("Chargement du modèle en mémoire GPU...")
processor = AutoProcessor.from_pretrained(MODEL_ID)
model = Qwen2VLForConditionalGeneration.from_pretrained(
    MODEL_ID,
    device_map="auto"
)
print("Modèle prêt !\n")

def poser_question(question: str, base64_str: str = "", memoire_courte: str = "") -> dict:
    try:
        # 1. Recherche RAG vectorielle
        souvenirs = chercher_souvenirs(question, n_results=2)

        # 2. Construction du System Prompt
        prompt_system_actuel = SYSTEM_PROMPT
        if memoire_courte:
            prompt_system_actuel += f"\nContexte des échanges récents : {memoire_courte}"

        if souvenirs:
            prompt_system_actuel += f"\nInformations pertinentes retrouvées en mémoire :\n{souvenirs}"

        base64_clean = base64_str.strip() if base64_str else ""
        has_image = len(base64_clean) > 0

        # 3. Structure des messages pour le modèle
        messages = [
            {"role": "system", "content": [{"type": "text", "text": prompt_system_actuel}]}
        ]

        content = []
        if has_image:
            if "," in base64_clean:
                base64_clean = base64_clean.split(",")[1]
            image_bytes = base64.b64decode(base64_clean)
            image = Image.open(BytesIO(image_bytes)).convert("RGB")
            content.append({"type": "image", "image": image})

        content.append({"type": "text", "text": question})
        messages.append({"role": "user", "content": content})

        # 4. Traitement Transformers
        text_prompt = processor.apply_chat_template(messages, add_generation_prompt=True)
        inputs_args = {"text": [text_prompt], "padding": True, "return_tensors": "pt"}

        if has_image:
            inputs_args["images"] = [image]

        inputs = processor(**inputs_args).to("cuda")

        with torch.no_grad():
            output_ids = model.generate(**inputs, max_new_tokens=256)

        generated_ids = [
            out[len(inp):] for inp, out in zip(inputs.input_ids, output_ids)
        ]
        
        reponse = processor.batch_decode(
            generated_ids, 
            skip_special_tokens=True, 
            clean_up_tokenization_spaces=False
        )[0]

        # Décoder le JSON généré par l'IA
        try:
            parsed_json = json.loads(reponse)
        except json.JSONDecodeError:
            parsed_json = {
                "action_rag": None,
                "action_oubli": None,
                "nouvelle_memoire": memoire_courte,
                "response": reponse
            }

        action_rag = parsed_json.get("action_rag")
        if action_rag and isinstance(action_rag, str):
            txt = action_rag.strip().lower()
            if txt not in ["null", "none", ""] and "null" not in txt and "none" not in txt:
                ajouter_souvenir(action_rag)
                print(f"[ACTION RAG] Sauvegardé : {action_rag}")

        action_oubli = parsed_json.get("action_oubli")
        if action_oubli and isinstance(action_oubli, str):
            txt = action_oubli.strip().lower()
            if txt not in ["null", "none", ""] and "null" not in txt and "none" not in txt:
                print(f"[ACTION OUBLI] Demande de suppression pour : {action_oubli}")
                supprimer_souvenir(action_oubli)

        return parsed_json

    except Exception as e:
        print(f"Erreur interne durant la question : {e}")
        return {
            "action_rag": None,
            "action_oubli": None,
            "nouvelle_memoire": memoire_courte,
            "response": f"Erreur serveur : {str(e)}"
        }

def afficher_qrcode_connexion(ip: str, port: int):
    target_data = f"{ip}:{port}"
    print("\n" + "="*50)
    print(f"ADRESSE ZEROTIER : {target_data}")
    print("Scanne le QR Code ci-dessous avec ton application mobile :")
    print("="*50 + "\n")
    
    qr = qrcode.QRCode()
    qr.add_data(target_data)
    qr.print_ascii(invert=True)

# --- SERVEUR SOCKET LOCAL ---
def demarrer_serveur_socket(host="127.0.0.1", port=5000):
    memoire_courte = ""

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((host, port))
    server.listen(5)
    
    print(f"=== MOTEUR AI PRÊT (Socket local sur {host}:{port}) ===")

    while True:
        client_socket, client_address = server.accept()
        print(f"Connexion reçue de : {client_address}")

        try:
            client_socket.settimeout(60.0) # Augmenté pour les inférences de 7B tokens sur GPU
            
            data_bytes = bytearray()
            while True:
                try:
                    chunk = client_socket.recv(8192)
                    if not chunk:
                        break
                    data_bytes.extend(chunk)
                    if b'\n' in chunk:
                        break
                except socket.timeout:
                    break

            if data_bytes:
                raw_str = data_bytes.decode('utf-8').strip()

                if raw_str.lower() in ["ping", "test"]:
                    print("Ping de validation reçu !")
                    client_socket.sendall(b"PONG\n")
                    continue

                payload = json.loads(raw_str)

                question = payload.get("question", "").strip()
                base64_image = payload.get("base64_image", "")

                # Interception de la commande /reset envoyée dans le champ question
                if question.lower() == "/reset" or payload.get("action") == "reset":
                    memoire_courte = ""
                    print("=== Mémoire courte réinitialisée via /reset ===")
                    reset_response = {
                        "action_rag": None,
                        "action_oubli": None,
                        "nouvelle_memoire": "",
                        "response": "Le contexte de la session a été remis à zéro."
                    }
                    response_payload = json.dumps(reset_response, ensure_ascii=False) + "\n"
                    client_socket.sendall(response_payload.encode('utf-8'))
                    continue

                print(f"Question reçue : {question}")
                print("Inférence en cours...")
                
                resultat_ia = poser_question(question, base64_image, memoire_courte)

                # Mise à jour propre de la mémoire si elle est renvoyée dans le JSON
                if isinstance(resultat_ia, dict):
                    memoire_courte = resultat_ia.get("nouvelle_memoire", memoire_courte)
                    print(f"[MÉMOIRE MAJ] : {memoire_courte}")

                response_payload = json.dumps(resultat_ia, ensure_ascii=False) + "\n"
                client_socket.sendall(response_payload.encode('utf-8'))
                print("Réponse envoyée au client.\n")

        except json.JSONDecodeError:
            print("Format JSON invalide reçu.")
            err_msg = json.dumps({"error": "Format JSON invalide"}) + "\n"
            client_socket.sendall(err_msg.encode('utf-8'))
        except Exception as e:
            print(f"Erreur Socket : {e}")
            err_msg = json.dumps({"error": str(e)}) + "\n"
            client_socket.sendall(err_msg.encode('utf-8'))
        
        finally:
            client_socket.close()

if __name__ == "__main__":
    demarrer_serveur_socket(host="127.0.0.1", port=PORT)