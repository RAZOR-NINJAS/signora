"""Public Help categories and pre-made communication message templates."""

import json
import os
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Dict, List, Optional


@dataclass
class HelpCard:
    id: str
    category: str
    title: str
    message: str
    icon: str = "💬"


def get_custom_cards_path() -> Path:
    """Return persistent path for user custom public help cards."""
    if os.name == "nt":
        base = Path(os.environ.get("LOCALAPPDATA", str(Path.home() / "AppData" / "Local")))
        data_dir = base / "Signora"
    else:
        xdg = Path(os.environ.get("XDG_DATA_HOME", str(Path.home() / ".local" / "share")))
        data_dir = xdg / "signora"

    data_dir.mkdir(parents=True, exist_ok=True)
    return data_dir / "custom_help_cards.json"


DEFAULT_HELP_CARDS: Dict[str, List[HelpCard]] = {
    "Hospital": [
        HelpCard(
            id="hosp_1",
            category="Hospital",
            title="I am Deaf - Please Write",
            message="I am Deaf. Please write down all doctor instructions, questions, and medication details on paper or your phone.",
            icon="📝",
        ),
        HelpCard(
            id="hosp_2",
            category="Hospital",
            title="Where is Emergency / Counter?",
            message="Where is the Emergency Room / Outpatient Registration counter? Please point or write down the direction.",
            icon="🏥",
        ),
        HelpCard(
            id="hosp_3",
            category="Hospital",
            title="Acute Pain Explanation",
            message="I am experiencing severe pain right now. Please assist me to see a doctor or nurse immediately.",
            icon="🩹",
        ),
        HelpCard(
            id="hosp_4",
            category="Hospital",
            title="Interpreter Request",
            message="I communicate in Sign Language. Is a sign language interpreter or video relay service available?",
            icon="🤟",
        ),
        HelpCard(
            id="hosp_5",
            category="Hospital",
            title="Medication Instructions",
            message="Could you please write down the name of this medicine, how many times a day to take it, and whether before or after meals?",
            icon="💊",
        ),
    ],
    "Travel": [
        HelpCard(
            id="trav_1",
            category="Travel",
            title="Which Platform / Gate?",
            message="Which platform or gate does the train/bus/flight to my destination depart from? Please write the platform number.",
            icon="🚆",
        ),
        HelpCard(
            id="trav_2",
            category="Travel",
            title="Alert Me at My Stop",
            message="I am Deaf and cannot hear audio arrival announcements. Could you please tap my shoulder when we reach my stop?",
            icon="🔔",
        ),
        HelpCard(
            id="trav_3",
            category="Travel",
            title="Delay / Status Query",
            message="Is this train / bus on time or delayed? What is the expected arrival time?",
            icon="⏰",
        ),
        HelpCard(
            id="trav_4",
            category="Travel",
            title="Ticket Counter / Exit",
            message="Where is the ticket counter / customer help desk / main exit? Please point in the right direction.",
            icon="🎫",
        ),
        HelpCard(
            id="trav_5",
            category="Travel",
            title="Missed Connection",
            message="I missed my connecting train/bus due to a delay. Please help me find the rebooking desk.",
            icon="⚠️",
        ),
    ],
    "Public Office": [
        HelpCard(
            id="gov_1",
            category="Public Office",
            title="Appointment & Documents",
            message="I have an appointment at this office. Here are my documents and appointment confirmation.",
            icon="📄",
        ),
        HelpCard(
            id="gov_2",
            category="Public Office",
            title="Queue Alert Request",
            message="I cannot hear audio loudspeaker queue announcements. Please tap my shoulder or show my token number when it is my turn.",
            icon="📢",
        ),
        HelpCard(
            id="gov_3",
            category="Public Office",
            title="Which Form / Counter?",
            message="Which form do I need to fill out, and which counter handles this application?",
            icon="🖊️",
        ),
        HelpCard(
            id="gov_4",
            category="Public Office",
            title="Verification / Stamp",
            message="Where do I get these documents verified and stamped? Thank you for your assistance.",
            icon="🏛️",
        ),
    ],
    "Help & Shopping": [
        HelpCard(
            id="help_1",
            category="Help & Shopping",
            title="How Much Does This Cost?",
            message="How much does this cost? Could you please write down the price or show it on the register/calculator?",
            icon="🏷️",
        ),
        HelpCard(
            id="help_2",
            category="Help & Shopping",
            title="Digital Payment / UPI",
            message="Do you accept digital payments (UPI, QR code, debit/credit card)? Please show the QR code or card terminal.",
            icon="💳",
        ),
        HelpCard(
            id="help_3",
            category="Help & Shopping",
            title="Need Directions",
            message="I am looking for this address / location. Could you please point or draw the direction?",
            icon="🗺️",
        ),
        HelpCard(
            id="help_4",
            category="Help & Shopping",
            title="Accessible Restroom",
            message="Where is the nearest restroom? Please point in the direction.",
            icon="🚻",
        ),
    ],
}


class HelpCardManager:
    """Manages preset and user-customized public communication cards."""

    def __init__(self, storage_path: Optional[Path] = None):
        self.storage_path = storage_path or get_custom_cards_path()
        self.cards: Dict[str, List[HelpCard]] = {}
        self._load()

    def _load(self) -> None:
        # Start with defaults
        self.cards = {k: list(v) for k, v in DEFAULT_HELP_CARDS.items()}
        if self.storage_path.exists():
            try:
                data = json.loads(self.storage_path.read_text(encoding="utf-8"))
                for cat, card_list in data.items():
                    if cat not in self.cards:
                        self.cards[cat] = []
                    for item in card_list:
                        card = HelpCard(**item)
                        # Replace if exists or append
                        existing = [i for i, c in enumerate(self.cards[cat]) if c.id == card.id]
                        if existing:
                            self.cards[cat][existing[0]] = card
                        else:
                            self.cards[cat].append(card)
            except Exception:
                pass

    def save(self) -> None:
        """Persist custom cards to disk."""
        self.storage_path.parent.mkdir(parents=True, exist_ok=True)
        dump_data = {
            cat: [asdict(c) for c in card_list]
            for cat, card_list in self.cards.items()
        }
        self.storage_path.write_text(json.dumps(dump_data, indent=2), encoding="utf-8")

    def get_categories(self) -> List[str]:
        return list(self.cards.keys())

    def get_cards_for_category(self, category: str) -> List[HelpCard]:
        return self.cards.get(category, [])

    def add_or_update_card(self, card: HelpCard) -> None:
        if card.category not in self.cards:
            self.cards[card.category] = []
        existing = [i for i, c in enumerate(self.cards[card.category]) if c.id == card.id]
        if existing:
            self.cards[card.category][existing[0]] = card
        else:
            self.cards[card.category].append(card)
        self.save()
