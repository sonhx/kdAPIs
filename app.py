import streamlit as st
from langchain_ollama import OllamaLLM
import subprocess, os

# ✅ Updated project roots
SPRINGBOOT_ROOT = r"E:\eclipse-workspace\kdAPIs"
FRONTEND_ROOT = r"E:\eclipse-workspace\qa-ims"

# Connect to Ollama (codellama)
llm = OllamaLLM(model="codellama")

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

user_input = st.text_area("Ask Ollama for suggestions:", "")

if st.button("Generate with Ollama"):
    response = llm.invoke(user_input)
    st.write("💡 Ollama suggests:")
    st.code(response, language="java" if "Backend" in project_choice else "javascript")
    st.session_state["ollama_output"] = response  # store suggestion

st.subheader("Manual Actions")
if project_choice.startswith("Backend"):
    if st.button("Run Maven Compile"):
        st.write(run_maven())
else:
    if st.button("Run npm start"):
        st.write(run_npm_start())

st.subheader("File Operations")
file_path = st.text_input("File path (relative to selected project root):")

if st.button("Read File"):
    st.code(read_file(file_path, current_root))

new_content = st.text_area("New file content:")
if st.button("Write File"):
    st.write(write_file(file_path, new_content, current_root))

# --- Apply Ollama suggestion directly ---
if "ollama_output" in st.session_state:
    if st.button("Apply Ollama Suggestion to File"):
        suggestion = st.session_state["ollama_output"]
        st.write(write_file(file_path, suggestion, current_root))
