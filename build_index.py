from llama_index.core import SimpleDirectoryReader, VectorStoreIndex, Settings
from llama_index.embeddings.huggingface import HuggingFaceEmbedding

# ✅ Use HuggingFace embeddings
Settings.embed_model = HuggingFaceEmbedding(model_name="sentence-transformers/all-MiniLM-L6-v2")

SPRINGBOOT_ROOT = r"E:\eclipse-workspace\kdAPIs"
FRONTEND_ROOT = r"E:\eclipse-workspace\qa-ims"

allowed_exts = [".java", ".js", ".ts", ".html"]

def load_filtered(root):
    return SimpleDirectoryReader(
        root,
        recursive=True,
        required_exts=allowed_exts,
        exclude=["target", "node_modules", "build", "dist"]
    ).load_data()

print("📂 Loading source files...")
documents = load_filtered(SPRINGBOOT_ROOT) + load_filtered(FRONTEND_ROOT)
print(f"✅ Loaded {len(documents)} source files")

if len(documents) == 0:
    print("⚠️ No files found. Check your paths or extensions.")
else:
    index = VectorStoreIndex.from_documents(documents)
    index.storage_context.persist(persist_dir="./project_index")
    print("✅ Index built and saved to ./project_index")
