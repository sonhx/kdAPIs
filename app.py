import streamlit as st
import subprocess, os
from langchain_ollama import OllamaLLM
from llama_index.core import StorageContext, load_index_from_storage, Settings
from llama_index.embeddings.huggingface import HuggingFaceEmbedding

# ✅ Use HuggingFace embeddings (offline, no API key needed)
Settings.embed_model = HuggingFaceEmbedding(model_name="sentence-transformers/all-MiniLM-L6-v2")

# ✅ Project roots
SPRINGBOOT_ROOT = r"E:\eclipse-workspace\kdAPIs"
FRONTEND_ROOT = r"E:\eclipse-workspace\qa-ims"

# ✅ Load Ollama model
llm = OllamaLLM(model="codellama")

# ✅ Try loading saved LlamaIndex
try:
    storage_context = StorageContext.from_defaults(persist_dir="./project_index")
    index = load_index_from_storage(storage_context)
    query_engine = index.as_query_engine(llm=llm)
    index_ready = True
except Exception as e:
    query_engine = None
    index_ready = False

# --- Tool functions ---
def run_maven():
    result = subprocess.run(["mvn", "compile"], cwd=SPRINGBOOT_ROOT,
                            capture_output=True, text=True)
    return result.stdout

def run_npm_start():
    result = subprocess.run(["npm", "start"], cwd=FRONTEND_ROOT,
                            capture_output=True, text=True)
    return result.stdout

def read_file(path: str, root: str):
    full_path = os.path.join(root, path)
    with open(full_path, "r", encoding="utf-8") as f:
        return f.read()

def write_file(path: str, content: str, root: str):
    full_path = os.path.join(root, path)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)
    return f"✅ File {full_path} updated."

# --- Streamlit UI ---
st.title("Local Programming Agent")

# 🔹 Project toggle
project_choice = st.radio("Select project:", ["Backend (kdAPIs)", "Frontend (qa-ims)"])
current_root = SPRINGBOOT_ROOT if project_choice.startswith("Backend") else FRONTEND_ROOT

# 🔹 Ollama suggestions
user_input = st.text_area("Ask Ollama for suggestions:", "")
if st.button("Generate with Ollama"):
    response = llm.invoke(user_input)
    st.write("💡 Ollama suggests:")
    st.code(response, language="java" if "Backend" in project_choice else "javascript")
    st.session_state["ollama_output"] = response

# 🔹 Manual actions
st.subheader("Manual Actions")
if project_choice.startswith("Backend"):
    if st.button("Run Maven Compile"):
        st.write(run_maven())
else:
    if st.button("Run npm start"):
        st.write(run_npm_start())

# 🔹 File operations
st.subheader("File Operations")
file_path = st.text_input("File path (relative to selected project root):")

if st.button("Read File"):
    st.code(read_file(file_path, current_root))

new_content = st.text_area("New file content:")
if st.button("Write File"):
    st.write(write_file(file_path, new_content, current_root))

# 🔹 Apply Ollama suggestion directly
if "ollama_output" in st.session_state and file_path:
    if st.button("Preview Ollama Suggestion vs Current File"):
        current = read_file(file_path, current_root)
        suggestion = st.session_state["ollama_output"]

        col1, col2 = st.columns(2)
        with col1:
            st.subheader("Current File")
            st.code(current, language="java" if "Backend" in project_choice else "javascript")
        with col2:
            st.subheader("Ollama Suggestion")
            st.code(suggestion, language="java" if "Backend" in project_choice else "javascript")

    if st.button("Apply Ollama Suggestion to File"):
        suggestion = st.session_state["ollama_output"]
        st.write(write_file(file_path, suggestion, current_root))

# 🔹 Ask about your codebase (LlamaIndex)
st.subheader("Ask about your codebase")
if not index_ready:
    st.warning("⚠️ Index not found or incomplete. Please run build_index.py first.")
else:
    query = st.text_input("Enter a question about your project:")
    if st.button("Search Project"):
        response = query_engine.query(query)
        st.write(response)
