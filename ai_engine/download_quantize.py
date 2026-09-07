import torch
from transformers import AutoProcessor, Qwen2VLForConditionalGeneration, BitsAndBytesConfig

MODEL_ID = "Qwen/Qwen2-VL-7B-Instruct"
SAVE_PATH = "./qwen2_vl_7b_4bit" # Dossier local où sauvegarder la version réduite

print("1. Chargement de la configuration 4-bit...")
quantization_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_compute_dtype=torch.float16,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_use_double_quant=True
)

print("2. Chargement du modèle depuis le cache local...")
# Il utilise les fichiers déjà présent sur ton PC !
processor = AutoProcessor.from_pretrained(MODEL_ID)
model = Qwen2VLForConditionalGeneration.from_pretrained(
    MODEL_ID,
    quantization_config=quantization_config,
    device_map="auto",
    low_cpu_mem_usage=True
)

print("3. Sauvegarde du modèle quantifié en local...")
model.save_pretrained(SAVE_PATH)
processor.save_pretrained(SAVE_PATH)

print(f"Terminé ! Ton modèle 4-bit est sauvegardé dans le dossier : {SAVE_PATH}")