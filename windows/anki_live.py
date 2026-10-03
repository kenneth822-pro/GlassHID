"""Relay live Anki desktop review state to the GlassHID phone over the USB link.

Reads the AnkiConnect add-on (https://ankiweb.net/shared/info/2055492159) on this PC and
produces one line for the phone, for example:

    ANKI state=review deck=<b64> new=3 learn=1 review=40 today=120 card=17 next=<b64>

Only the Python standard library is used, so the module also runs (and is tested) on
Linux and macOS.
"""

from __future__ import annotations

import base64
import json
import urllib.error
import urllib.request
from typing import Any, Callable, Optional

DEFAULT_URL = "http://127.0.0.1:8765"
API_VERSION = 6


class AnkiConnectError(Exception):
    """AnkiConnect answered with an error, or could not be reached."""


def _b64(text: str) -> str:
    return base64.b64encode(text.encode("utf-8")).decode("ascii")


class AnkiConnect:
    def __init__(self, url: str = DEFAULT_URL, timeout: float = 1.5) -> None:
        self.url = url
        self.timeout = timeout
        # AnkiConnect is local: never route it through a system or environment proxy.
        self._opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def invoke(self, action: str, **params: Any) -> Any:
        payload = json.dumps({"action": action, "version": API_VERSION, "params": params}).encode("utf-8")
        request = urllib.request.Request(self.url, data=payload, headers={"Content-Type": "application/json"})
        try:
            with self._opener.open(request, timeout=self.timeout) as response:
                body = json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, OSError, ValueError) as error:
            raise AnkiConnectError(f"AnkiConnect unreachable: {error}") from error
        if not isinstance(body, dict) or "result" not in body:
            raise AnkiConnectError("Unexpected AnkiConnect response")
        if body.get("error"):
            raise AnkiConnectError(str(body["error"]))
        return body["result"]


def next_intervals(card: dict) -> list:
    """Map AnkiConnect's button/nextReviews pair onto Again, Hard, Good, Easy slots."""
    buttons = card.get("buttons") or []
    reviews = card.get("nextReviews") or []
    slots = ["", "", "", ""]
    if len(buttons) == len(reviews):
        for button, review in zip(buttons, reviews):
            if isinstance(button, int) and 1 <= button <= 4:
                slots[button - 1] = str(review)
    elif len(reviews) == 4:
        slots = [str(review) for review in reviews]
    return slots


def poll(client: AnkiConnect) -> str:
    """One status line for the phone. Never raises."""
    try:
        today = client.invoke("getNumCardsReviewedToday")
    except AnkiConnectError:
        return "ANKI state=offline"
    today_value = int(today) if isinstance(today, int) else -1
    try:
        card = client.invoke("guiCurrentCard")
    except AnkiConnectError:
        card = None  # AnkiConnect reports an error when the reviewer is not open.
    if not isinstance(card, dict):
        return f"ANKI state=idle today={today_value}"

    deck = str(card.get("deckName") or "")
    counts = {"new": 0, "learn": 0, "review": 0}
    if deck:
        try:
            stats = client.invoke("getDeckStats", decks=[deck])
            if isinstance(stats, dict):
                for entry in stats.values():
                    if isinstance(entry, dict) and entry.get("name") == deck:
                        counts = {
                            "new": int(entry.get("new_count", 0)),
                            "learn": int(entry.get("learn_count", 0)),
                            "review": int(entry.get("review_count", 0)),
                        }
                        break
        except (AnkiConnectError, TypeError, ValueError):
            pass
    card_id = card.get("cardId")
    card_value = int(card_id) if isinstance(card_id, int) else 0
    return (
        f"ANKI state=review deck={_b64(deck)} new={counts['new']} learn={counts['learn']} "
        f"review={counts['review']} today={today_value} card={card_value} "
        f"next={_b64('|'.join(next_intervals(card)))}"
    )


class LiveRelay:
    """Calls ``send`` with a fresh line when the state changes, and at least every ``heartbeat`` s."""

    def __init__(self, client: AnkiConnect, send: Callable[[str], None], heartbeat: float = 10.0) -> None:
        self.client = client
        self.send = send
        self.heartbeat = heartbeat
        self._last_line: Optional[str] = None
        self._last_sent = 0.0

    def step(self, now: float) -> Optional[str]:
        line = poll(self.client)
        if line != self._last_line or now - self._last_sent >= self.heartbeat:
            self.send(line + "\n")
            self._last_line = line
            self._last_sent = now
            return line
        return None
