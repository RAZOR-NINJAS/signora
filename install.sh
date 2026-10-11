#!/usr/bin/env bash
# ==============================================================================
# Signora — Zero-Prerequisite Linux & macOS Installer
#
# Can be run locally:
#   ./install.sh
# Or piped directly from curl:
#   curl -fsSL https://raw.githubusercontent.com/RAZOR-NINJAS/signora/main/install.sh | bash
# ==============================================================================
set -euo pipefail

# ANSI color codes
BOLD="\033[1m"
CYAN="\033[36m"
GREEN="\033[32m"
YELLOW="\033[33m"
RED="\033[31m"
DIM="\033[2m"
RESET="\033[0m"

REPO_URL="https://github.com/RAZOR-NINJAS/signora.git"
REPO_BRANCH="main"
TAR_URL="https://github.com/RAZOR-NINJAS/signora/archive/refs/heads/${REPO_BRANCH}.tar.gz"

echo -e "${CYAN}${BOLD}"
cat << 'EOF'
  ███████╗██╗ ██████╗ ███╗   ██╗ ██████╗ ██████╗  █████╗ 
  ██╔════╝██║██╔════╝ ████╗  ██║██╔═══██╗██╔══██╗██╔══██╗
  ███████╗██║██║  ███╗██╔██╗ ██║██║   ██║██████╔╝███████║
  ╚════██║██║██║   ██║██║╚██╗██║██║   ██║██╔══██╗██╔══██║
  ███████║██║╚██████╔╝██║ ╚████║╚██████╔╝██║  ██║██║  ██║
  ╚══════╝╚═╝ ╚═════╝ ╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝
EOF
echo -e "${RESET}"
echo -e "${BOLD}Real-time sign language recognition. 100% local. Nothing leaves your machine.${RESET}\n"

# 1. Determine Target Directory
INSTALL_DIR=""
if [ -f "./pyproject.toml" ] && grep -q 'name = "signora"' "./pyproject.toml" 2>/dev/null; then
    INSTALL_DIR="$(pwd)"
elif [ -n "${BASH_SOURCE[0]:-}" ] && [ -f "$(dirname "${BASH_SOURCE[0]}")/pyproject.toml" ]; then
    INSTALL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
else
    INSTALL_DIR="${HOME}/Projects/signora"
fi

echo -e "${GREEN}[1/6] Target directory:${RESET} ${INSTALL_DIR}"

# 2. Fetch or verify source code
if [ ! -f "${INSTALL_DIR}/pyproject.toml" ]; then
    echo -e "${CYAN}[2/6] Downloading Signora source tree...${RESET}"
    mkdir -p "$(dirname "$INSTALL_DIR")"
    if command -v git >/dev/null 2>&1; then
        echo -e "      Cloning repository via git..."
        git clone -b "$REPO_BRANCH" "$REPO_URL" "$INSTALL_DIR" 2>/dev/null || git clone "$REPO_URL" "$INSTALL_DIR"
    else
        echo -e "      Downloading source tarball via curl..."
        mkdir -p "$INSTALL_DIR"
        curl -fsSL "$TAR_URL" | tar -xz -C "$INSTALL_DIR" --strip-components=1
    fi
else
    echo -e "${GREEN}[2/6] Source tree verified:${RESET} pyproject.toml present"
fi

cd "$INSTALL_DIR"

# 3. Detect or install uv / Python 3.11+
echo -e "${CYAN}[3/6] Configuring Python runtime (Python >=3.11, <3.13)...${RESET}"

UV_BIN=""
if command -v uv >/dev/null 2>&1; then
    UV_BIN="$(command -v uv)"
elif [ -f "${HOME}/.local/bin/uv" ]; then
    UV_BIN="${HOME}/.local/bin/uv"
elif [ -f "${HOME}/.cargo/bin/uv" ]; then
    UV_BIN="${HOME}/.cargo/bin/uv"
else
    echo -e "      ${DIM}Installing standalone uv package manager (fast, non-root)...${RESET}"
    curl -LsSf https://astral.sh/uv/install.sh | sh >/dev/null 2>&1 || true
    if [ -f "${HOME}/.local/bin/uv" ]; then
        UV_BIN="${HOME}/.local/bin/uv"
    elif [ -f "${HOME}/.cargo/bin/uv" ]; then
        UV_BIN="${HOME}/.cargo/bin/uv"
    fi
fi

if [ -n "$UV_BIN" ]; then
    echo -e "      ${GREEN}✓ Found uv:${RESET} $UV_BIN"
    # Ensure Python 3.11 is present via uv
    "$UV_BIN" python install 3.11 >/dev/null 2>&1 || true
    PYTHON_CMD="$UV_BIN"
    USE_UV=1
else
    # Fallback to system Python
    SYS_PY=""
    for cand in python3.11 python3.12 python3; do
        if command -v "$cand" >/dev/null 2>&1; then
            minor=$("$cand" -c 'import sys; print(sys.version_info.minor)' 2>/dev/null || echo "0")
            if [ "$minor" -ge 11 ] && [ "$minor" -le 12 ]; then
                SYS_PY="$cand"
                break
            fi
        fi
    done

    if [ -z "$SYS_PY" ]; then
        echo -e "${RED}[Error] Python 3.11 or 3.12 is required for MediaPipe compatibility.${RESET}"
        echo "Please install it via your distribution's package manager:"
        echo "  Arch:   sudo pacman -S python"
        echo "  Ubuntu: sudo apt update && sudo apt install -y python3.11 python3.11-venv python3-pip"
        echo "  Fedora: sudo dnf install -y python3.11"
        exit 1
    fi
    echo -e "      ${GREEN}✓ Using system Python:${RESET} $SYS_PY"
    PYTHON_CMD="$SYS_PY"
    USE_UV=0
fi

# 4. Set Up Virtual Environment & CPU PyTorch
echo -e "${CYAN}[4/6] Creating virtual environment (.venv)...${RESET}"
VENV_DIR="${INSTALL_DIR}/.venv"

if [ "$USE_UV" -eq 1 ]; then
    if [ ! -d "$VENV_DIR" ]; then
        "$UV_BIN" venv --python 3.11 "$VENV_DIR"
    fi
    PY_BIN="${VENV_DIR}/bin/python"
    
    echo -e "${CYAN}[5/6] Installing dependencies (CPU-optimized PyTorch)...${RESET}"
    "$UV_BIN" pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu --quiet
    "$UV_BIN" pip install -e . --quiet
else
    if [ ! -d "$VENV_DIR" ]; then
        "$PYTHON_CMD" -m venv "$VENV_DIR"
    fi
    PY_BIN="${VENV_DIR}/bin/python"
    "$PY_BIN" -m pip install --upgrade pip --quiet
    
    echo -e "${CYAN}[5/6] Installing dependencies (CPU-optimized PyTorch)...${RESET}"
    "$PY_BIN" -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu --quiet
    "$PY_BIN" -m pip install -e . --quiet
fi

# 5. Fetch Models
echo -e "${CYAN}[6/6] Verifying neural models and voice assets...${RESET}"
"$PY_BIN" scripts/fetch_models.py --target-dir "${INSTALL_DIR}/models"

# 6. Run System Doctor
echo -e "\n${CYAN}Running system self-test...${RESET}"
"$PY_BIN" src/signora/cli.py doctor || true

# Symlink to user PATH if ~/.local/bin exists
if [ -d "${HOME}/.local/bin" ]; then
    ln -sf "${INSTALL_DIR}/.venv/bin/signora" "${HOME}/.local/bin/signora"
    echo -e "\n${GREEN}✓ Symlink created:${RESET} ${HOME}/.local/bin/signora"
fi

echo -e "\n${BOLD}${GREEN}================================================================${RESET}"
echo -e "${BOLD}${GREEN}  Signora installation completed successfully!${RESET}"
echo -e "${BOLD}${GREEN}================================================================${RESET}\n"
echo -e "To launch Signora interactive TUI dashboard:"
echo -e "  ${BOLD}signora${RESET}   (or: ${INSTALL_DIR}/.venv/bin/signora)\n"
echo -e "Quick launch commands:"
echo -e "  ${CYAN}signora live${RESET}       ASL Fingerspelling"
echo -e "  ${CYAN}signora isl${RESET}        Indian Sign Language (ISL)"
echo -e "  ${CYAN}signora words${RESET}      ASL Dynamic Words"
echo -e "  ${CYAN}signora game${RESET}       Shadow-Play Dino Runner"
echo -e "  ${CYAN}signora learn${RESET}      Browser Learning Kiosk"
echo -e "  ${CYAN}signora doctor${RESET}     System Diagnostics\n"
