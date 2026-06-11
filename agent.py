from langchain_ollama import OllamaLLM
from langchain.agents import initialize_agent, Tool
import subprocess
import os

# 🔹 Point to your project root
PROJECT_ROOT = r"E:\eclipse-workspace\qa-ims"

# Connect to local Ollama model
llm = OllamaLLM(model="codellama")

# Tool: read a file
def read_file(path: str) -> str:
    full_path = os.path.join(PROJECT_ROOT, path)
    with open(full_path, "r", encoding="utf-8") as f:
        return f.read()

# Tool: write to a file
def write_file(args: dict) -> str:
    full_path = os.path.join(PROJECT_ROOT, args["path"])
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(args["content"])
    return f"File {full_path} updated."

# Tool: run Maven compile
def run_maven(_: str) -> str:
    result = subprocess.run(
        ["mvn", "compile"],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True
    )
    return result.stdout

tools = [
    Tool(name="ReadFile", func=read_file, description="Read a file from disk"),
    Tool(name="WriteFile", func=write_file, description="Write content to a file"),
    Tool(name="RunMaven", func=run_maven, description="Compile the Maven project"),
]

# Initialize agent
agent = initialize_agent(tools, llm, agent="zero-shot-react-description", verbose=True)

# 🔹 Example usage
agent.run("Open src/main/java/com/example/demo/OrdersController.java and add a new REST endpoint for /api/orders, then run Maven compile.")
