import base64
import json
import socket
from io import BytesIO
import os
from PIL import Image
from llama_cpp import Llama
from llama_cpp.llama_chat_format import Llava15ChatHandler

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "gemma-4-e2b-it-Q8_0.gguf")
MMPROJ_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mmproj-gemma-f16.gguf")
PORT = 5000

SYSTEM_PROMPT = """Tu es un assistant IA. Tu dois TOUJOURS répondre au format JSON strict.

Structure JSON obligatoire :
{
  "action_rag": "Information à retenir ou null",
  "action_oubli": "Information à effacer ou null",
  "nouvelle_memoire": "Résumé court du sujet (15 mots max)",
  "response": "Ta réponse"
}"""

def process_image(base64_clean: str) -> Image.Image:
    if "," in base64_clean:
        base64_clean = base64_clean.split(",")[1]
    image_bytes = base64.b64decode(base64_clean)
    image = Image.open(BytesIO(image_bytes)).convert("RGB")
    image.thumbnail((512, 512), Image.Resampling.LANCZOS)
    return image

def purger_kv_cache(llm_instance):
    """Purge le cache de tokens natif du modèle via les méthodes de la classe Llama."""
    try:
        # 1. Libère la mémoire des tokens générés et du contexte
        if hasattr(llm_instance, "clear_kv_cache"):
            llm_instance.clear_kv_cache()
        
        # 2. Reinitialise les pointeurs de la séquence conversationnelle
        llm_instance.reset()
        
        print("[CACHE] Cache réinitialisé proprement.")
    except Exception as e:
        print(f"[CACHE ERROR] Erreur lors de la purge : {e}")
        
def poser_question(question: str, base64_str: str = "", memoire_courte: str = "") -> dict:
    try:
        # Réinitialisation explicite du contexte avant l'inférence
        llm.reset()
        purger_kv_cache(llm)

        prompt_system_actuel = SYSTEM_PROMPT
        if memoire_courte:
            prompt_system_actuel += f"\nContexte des échanges récents : {memoire_courte}"

        if base64_str.strip():
            img_processed = process_image(base64_str)
            buffered = BytesIO()
            img_processed.save(buffered, format="JPEG", quality=85)
            clean_b64 = base64.b64encode(buffered.getvalue()).decode("utf-8")
            
            # Format structuré avec la balise <image> explicite dans le texte
            user_content = [
                {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{clean_b64}"}},
                {"type": "text", "text": f"<image>\n{question}"}
            ]
        else:
            user_content = question

        messages = [
            {"role": "system", "content": prompt_system_actuel},
            {"role": "user", "content": user_content}
        ]

        output = llm.create_chat_completion(
            messages=messages,
            max_tokens=512,
            response_format={"type": "json_object"}
        )

        reponse = output["choices"][0]["message"]["content"]
        llm.reset()
        purger_kv_cache(llm)

        try:
            parsed_json = json.loads(reponse)
        except json.JSONDecodeError:
            parsed_json = {
                "action_rag": None,
                "action_oubli": None,
                "nouvelle_memoire": memoire_courte,
                "response": reponse
            }

        return parsed_json

    except Exception as e:
        print(f"Erreur interne durant la question : {e}")
        return {
            "action_rag": None,
            "action_oubli": None,
            "nouvelle_memoire": memoire_courte,
            "response": f"Erreur serveur : {str(e)}"
        }

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
            client_socket.settimeout(60.0)
            
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

                if base64_image:
                    print("[IMAGE] Image reçue, envoi au modèle visuel...")

                print(f"Question reçue : {question}")
                print("Inférence en cours...")
                
                resultat_ia = poser_question(question, base64_image, memoire_courte)

                print(resultat_ia)

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

print("Chargement du modèle en VRAM...")
chat_handler = Llava15ChatHandler(clip_model_path=MMPROJ_PATH)
llm = Llama(
    model_path=MODEL_PATH,
    chat_handler=chat_handler,
    n_gpu_layers=-1,
    n_ctx=4096,
    verbose=True
)
print("Modèle prêt !\n")

if __name__ == "__main__":
    demarrer_serveur_socket(host="127.0.0.1", port=PORT)