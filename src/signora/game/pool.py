"""Target letter generator and bag management for the shadow-play game."""

import random
from typing import Any, Iterable, List, Optional, Sequence

from signora.asl.config import LETTERS

DEFAULT_EXCLUDE = frozenset()
THIN_LABELS = frozenset()


def model_labels(path: Optional[str] = None) -> List[str]:
    """Known alphabet letters in canonical order."""
    return list(LETTERS)


def build_pool(
    include: Optional[Iterable[str]] = None,
    exclude: Optional[Iterable[str]] = None,
    thin: Iterable[str] = THIN_LABELS,
    labels: Optional[Sequence[str]] = None,
) -> List[str]:
    """Build a filtered list of letters safe to ask players for in game mode."""
    known = model_labels() if labels is None else list(labels)
    letters = [c for c in known if len(c) == 1 and c.isalpha()]

    if include:
        wanted = {c.upper() for c in include}
        letters = [c for c in letters if c in wanted]

    drop = set(DEFAULT_EXCLUDE if exclude is None else exclude) | set(thin or ())
    return [c for c in letters if c not in drop]


class LetterPool:
    """Manages randomized target selection with fair bag-draw cycling."""

    def __init__(self, letters: Sequence[str], rng: Any = None, mode: str = "bag"):
        self.letters = list(dict.fromkeys(letters))
        self.rng = rng if isinstance(rng, random.Random) else random.Random(rng)
        self.mode = mode
        self._bag: List[str] = []

    def __len__(self) -> int:
        return len(self.letters)

    @property
    def empty(self) -> bool:
        return not self.letters

    def _refill(self) -> None:
        bag = list(self.letters)
        if self.mode == "random":
            self._bag = [self.rng.choice(bag)]
        else:
            self.rng.shuffle(bag)
            self._bag = bag

    def next(self) -> Optional[str]:
        """Draw the next target letter, refilling the bag when exhausted."""
        if not self.letters:
            return None
        if not self._bag:
            self._refill()
        return self._bag.pop()

    def remaining_in_cycle(self) -> int:
        """Count of unseen targets remaining in the current cycle."""
        return len(self._bag)
