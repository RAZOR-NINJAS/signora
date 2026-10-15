"""Emergency profile manager and storage for Signora Emergency Card."""

import json
import os
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Optional


def get_emergency_profile_path() -> Path:
    """Return platform-appropriate persistent path for user emergency card profile."""
    if os.name == "nt":
        base = Path(os.environ.get("LOCALAPPDATA", str(Path.home() / "AppData" / "Local")))
        data_dir = base / "Signora"
    else:
        xdg = Path(os.environ.get("XDG_DATA_HOME", str(Path.home() / ".local" / "share")))
        data_dir = xdg / "signora"

    data_dir.mkdir(parents=True, exist_ok=True)
    return data_dir / "emergency_profile.json"


@dataclass
class EmergencyProfile:
    """Personal and medical emergency details displayed on the emergency card."""
    name: str = "Signora User"
    communication_note: str = "I am Deaf / Non-Verbal. Please communicate by writing down instructions."
    blood_group: str = "O+"
    allergies: str = "None reported"
    medical_conditions: str = "Deaf / Hard of Hearing"
    primary_contact_name: str = "Emergency Contact"
    primary_contact_phone: str = "+1-555-0199"
    secondary_contact: str = ""

    @classmethod
    def load(cls, path: Optional[Path] = None) -> "EmergencyProfile":
        """Load emergency profile from disk or return default template."""
        p = path or get_emergency_profile_path()
        if p.exists():
            try:
                data = json.loads(p.read_text(encoding="utf-8"))
                return cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})
            except Exception:
                pass
        return cls()

    def save(self, path: Optional[Path] = None) -> None:
        """Save emergency profile to disk."""
        p = path or get_emergency_profile_path()
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(asdict(self), indent=2), encoding="utf-8")
