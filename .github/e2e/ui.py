"""ui.py focused | has TEXT  -- reads a uiautomator dump on stdin."""
import sys, xml.etree.ElementTree as ET
raw = sys.stdin.read()
root = ET.fromstring(raw[raw.index("<?xml"):raw.rindex(">") + 1])
if sys.argv[1] == "focused":
    for n in root.iter("node"):
        if n.get("focused") == "true":
            # A list keeps focus itself and marks its current item selected.
            picked = [m for m in n.iter("node") if m.get("selected") == "true"] or [n]
            texts = [m.get("text") for m in picked[0].iter("node") if m.get("text")]
            print(texts[0] if texts else "")
            break
else:
    sys.exit(0 if any(sys.argv[2] in (n.get("text") or "") for n in root.iter("node")) else 1)
