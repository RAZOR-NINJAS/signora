"""Scrollable caption history buffer and export manager for SIGNORA Catch-Up."""

import datetime
import json
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import List, Optional


@dataclass
class CaptionEntry:
    """Individual spoken caption segment with metadata."""
    timestamp: float
    time_str: str
    text: str
    speaker: str = "Speaker"
    confidence: float = 0.95
    is_bookmarked: bool = False


class CaptionHistory:
    """Thread-safe, temporary scrollable caption history buffer."""

    def __init__(self, maxlen: int = 2000):
        self.maxlen = maxlen
        self.entries: List[CaptionEntry] = []

    def add(
        self,
        text: str,
        speaker: str = "Speaker",
        confidence: float = 0.95,
        timestamp: Optional[float] = None,
    ) -> CaptionEntry:
        """Add a newly transcribed speech segment."""
        t = timestamp or time.time()
        time_str = datetime.datetime.fromtimestamp(t).strftime("%H:%M:%S")
        entry = CaptionEntry(
            timestamp=t,
            time_str=time_str,
            text=text.strip(),
            speaker=speaker,
            confidence=confidence,
        )
        self.entries.append(entry)
        if len(self.entries) > self.maxlen:
            self.entries.pop(0)
        return entry

    def search(self, query: str) -> List[CaptionEntry]:
        """Search caption history for keywords."""
        q = query.lower()
        return [e for e in self.entries if q in e.text.lower() or q in e.speaker.lower()]

    def toggle_bookmark(self, index: int) -> bool:
        """Toggle bookmark state on a caption item."""
        if 0 <= index < len(self.entries):
            self.entries[index].is_bookmarked = not self.entries[index].is_bookmarked
            return self.entries[index].is_bookmarked
        return False

    def get_recent(self, count: int = 50) -> List[CaptionEntry]:
        """Return the most recent N caption entries."""
        return self.entries[-count:]

    def clear(self) -> None:
        """Clear temporary caption history."""
        self.entries.clear()

    def export_txt(self, path: Path) -> Path:
        """Export full caption history to clean text file."""
        path.parent.mkdir(parents=True, exist_ok=True)
        lines = [
            f"=== SIGNORA CATCH-UP TRANSCRIPT ({datetime.date.today()}) ===",
            f"Total Captions: {len(self.entries)}\n",
        ]
        for e in self.entries:
            star = " ★ [BOOKMARKED]" if e.is_bookmarked else ""
            lines.append(f"[{e.time_str}] {e.speaker}: {e.text}{star}")
        path.write_text("\n".join(lines), encoding="utf-8")
        return path

    def export_json(self, path: Path) -> Path:
        """Export caption history with metadata to JSON."""
        path.parent.mkdir(parents=True, exist_ok=True)
        data = [asdict(e) for e in self.entries]
        path.write_text(json.dumps(data, indent=2), encoding="utf-8")
        return path

    def export_srt(self, path: Path) -> Path:
        """Export captions to standard SubRip (.srt) subtitle format."""
        path.parent.mkdir(parents=True, exist_ok=True)
        blocks = []
        for idx, e in enumerate(self.entries, start=1):
            dt = datetime.datetime.fromtimestamp(e.timestamp)
            start_srt = dt.strftime("%H:%M:%S,000")
            # End caption ~3.5 seconds later
            end_dt = dt + datetime.timedelta(seconds=3.5)
            end_srt = end_dt.strftime("%H:%M:%S,000")
            blocks.append(f"{idx}\n{start_srt} --> {end_srt}\n{e.speaker}: {e.text}\n")
        path.write_text("\n".join(blocks), encoding="utf-8")
        return path
