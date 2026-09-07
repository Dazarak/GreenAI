import chromadb
from chromadb.utils import embedding_functions

print("Chargement du RAG...")
embedding_fn = embedding_functions.SentenceTransformerEmbeddingFunction(
    model_name="paraphrase-multilingual-MiniLM-L12-v2"
)

chroma_client = chromadb.PersistentClient(path="./chroma_db")

collection = chroma_client.get_or_create_collection(
    name="souvenirs_greenai",
    embedding_function=embedding_fn
)

def ajouter_souvenir(info: str):
    """Vectorise et sauvegarde une information dans la base vectorielle."""
    if not info or info.lower() in ["null", "none", ""]:
        return
    
    doc_id = f"mem_{abs(hash(info))}"
    
    # upsert évite le crash si l'ID existe déjà
    collection.upsert(
        documents=[info],
        ids=[doc_id]
    )
    print(f"[RAG VECTORIEL] Souvenir indexé : '{info}'")

def chercher_souvenirs(question: str, n_results: int = 2) -> str:
    """Cherche les souvenirs les plus proches sémantiquement de la question."""
    if collection.count() == 0:
        return ""

    # Recherche vectorielle (distance cosinus / similarité)
    results = collection.query(
        query_texts=[question],
        n_results=min(n_results, collection.count())
    )

    documents = results.get("documents", [[]])[0]
    if not documents:
        return ""

    # Mise en forme pour le prompt
    souvenirs_formatted = "\n".join(f"- {doc}" for doc in documents)
    return souvenirs_formatted

def reinitialiser_rag():
    """Efface TOUS les souvenirs de la base vectorielle."""
    global collection
    chroma_client.delete_collection("souvenirs_greenai")
    collection = chroma_client.get_or_create_collection(
        name="souvenirs_greenai",
        embedding_function=embedding_fn
    )
    print("[RAG VECTORIEL] Base de données entièrement réinitialisée.")

def supprimer_souvenir(info_a_oublier: str):
    if not info_a_oublier or collection.count() == 0:
        return

    # Recherche du document le plus proche dans ChromaDB
    results = collection.query(
        query_texts=[info_a_oublier],
        n_results=1
    )

    ids = results.get("ids", [[]])[0]
    documents = results.get("documents", [[]])[0]

    if ids and documents:
        doc_id = ids[0]
        doc_texte = documents[0]
        collection.delete(ids=[doc_id])
        print(f"[RAG VECTORIEL] Souvenir supprimé : '{doc_texte}' (ID: {doc_id})")
    else:
        print("[RAG VECTORIEL] Aucun souvenir correspondant trouvé à supprimer.")