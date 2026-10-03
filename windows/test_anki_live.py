"""Tests for anki_live against a fake AnkiConnect server. Run: python -m unittest discover -s windows"""

import base64
import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

import anki_live


def b64(text):
    return base64.b64encode(text.encode("utf-8")).decode("ascii")


class FakeAnki:
    def __init__(self):
        self.reviewing = True
        self.card_id = 1700000000123
        self.requests = []

    def answer(self, request):
        action = request["action"]
        self.requests.append(action)
        if action == "getNumCardsReviewedToday":
            return {"result": 120, "error": None}
        if action == "guiCurrentCard":
            if not self.reviewing:
                return {"result": None, "error": "Gui review is not currently active."}
            return {"result": {"cardId": self.card_id, "deckName": "Biology::Cells 🧬",
                               "buttons": [1, 2, 3, 4], "nextReviews": ["<1m", "<6m", "<10m", "4d"]},
                    "error": None}
        if action == "getDeckStats":
            assert request["params"]["decks"] == ["Biology::Cells 🧬"]
            return {"result": {"1651445861967": {"deck_id": 1651445861967, "name": "Biology::Cells 🧬",
                                                 "new_count": 3, "learn_count": 1, "review_count": 40,
                                                 "total_in_deck": 900}}, "error": None}
        return {"result": None, "error": "unsupported action"}


def serve(fake):
    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            reply = json.dumps(fake.answer(body)).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(reply)))
            self.end_headers()
            self.wfile.write(reply)

        def log_message(self, *args):
            pass

    server = HTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


class AnkiLiveTest(unittest.TestCase):
    def setUp(self):
        self.fake = FakeAnki()
        self.server = serve(self.fake)
        self.client = anki_live.AnkiConnect(f"http://127.0.0.1:{self.server.server_port}", timeout=2)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def test_review_line(self):
        line = anki_live.poll(self.client)
        self.assertEqual(line, "ANKI state=review deck=" + b64("Biology::Cells 🧬")
                         + " new=3 learn=1 review=40 today=120 card=1700000000123 next="
                         + b64("<1m|<6m|<10m|4d"))

    def test_idle_when_reviewer_closed(self):
        self.fake.reviewing = False
        self.assertEqual(anki_live.poll(self.client), "ANKI state=idle today=120")

    def test_offline_when_unreachable(self):
        client = anki_live.AnkiConnect("http://127.0.0.1:9", timeout=0.5)
        self.assertEqual(anki_live.poll(client), "ANKI state=offline")

    def test_relay_sends_changes_and_heartbeats(self):
        sent = []
        relay = anki_live.LiveRelay(self.client, sent.append, heartbeat=10)
        self.assertIsNotNone(relay.step(0))
        self.assertIsNone(relay.step(1))
        self.fake.card_id += 1
        self.assertIsNotNone(relay.step(2))
        self.assertIsNone(relay.step(3))
        self.assertIsNotNone(relay.step(12.5))
        self.assertEqual(len(sent), 3)
        self.assertTrue(all(line.endswith("\n") for line in sent))

    def test_next_intervals_for_three_button_cards(self):
        card = {"buttons": [1, 3, 4], "nextReviews": ["<1m", "<10m", "4d"]}
        self.assertEqual(anki_live.next_intervals(card), ["<1m", "", "<10m", "4d"])
        self.assertEqual(anki_live.next_intervals({}), ["", "", "", ""])


if __name__ == "__main__":
    unittest.main()
