#!/usr/bin/env python3
"""Show a QR code that pairs the Hermes TTY app with this machine's Hermes gateway.

Scan it from the app's settings ([▣ scan QR]) and confirm the server. The code carries the API key, so
only show it on a screen you trust.
"""
import os, socket, subprocess, sys, urllib.parse
from pathlib import Path

HERE = Path(__file__).resolve().parent
VENV = HERE / ".pair-venv"

def ensure_qrcode():
    try:
        import qrcode  # noqa: F401
        return
    except ImportError:
        pass
    py = VENV / "bin" / "python"
    if not py.exists():
        subprocess.check_call([sys.executable, "-m", "venv", str(VENV)])
        subprocess.check_call([str(py), "-m", "pip", "install", "-q", "qrcode"])
    os.execv(str(py), [str(py), *sys.argv])

def env_value(name):
    env = Path.home() / ".hermes" / ".env"
    for line in env.read_text().splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip().strip("'\"")
    return None

def lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("192.0.2.1", 9))  # no packet is sent; just picks the outbound interface
        return s.getsockname()[0]
    finally:
        s.close()

def main():
    ensure_qrcode()
    import qrcode
    key = env_value("API_SERVER_KEY")
    if not key:
        sys.exit("API_SERVER_KEY not found in ~/.hermes/.env")
    host = sys.argv[1] if len(sys.argv) > 1 else lan_ip()
    port = env_value("API_SERVER_PORT") or "8642"
    url = f"http://{host}:{port}"
    link = "hermestty://connect?" + urllib.parse.urlencode({"url": url, "key": key})
    qr = qrcode.QRCode(border=2, error_correction=qrcode.constants.ERROR_CORRECT_L)
    qr.add_data(link)
    qr.print_ascii(invert=True)
    print(f"\n  pairs with {url}   (pass another host/IP as an argument, e.g. your Tailscale IP)\n")

if __name__ == "__main__":
    main()
