#!/usr/bin/env python3
"""A small stand-in for the Notion API, so the app can be driven end to end without a real token.

    python3 tools/fake_notion.py            # serves on http://127.0.0.1:8787

A debug build talks to it once the port is forwarded and the app is started with the
`notion_url` extra, which it remembers until it is given an empty one:

    adb reverse tcp:8787 tcp:8787
    adb shell am start -n app.stillnote/.MainActivity --es notion_url http://127.0.0.1:8787

Any token works except one starting with "bad", which gets a 401. It implements only what the
app calls, in API version 2026-03-11: users/me, search, retrieve and query a data source, and
create and update a page. `FakeNotion.fail` makes every call (or only writes) fail on demand.
"""
import json
import re
import threading
import uuid
from datetime import date, datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

VERSION = "2026-03-11"


def text(value):
    return [{"type": "text", "text": {"content": value}, "plain_text": value}]


def select(*names):
    return {"options": [{"id": str(uuid.uuid4()), "name": name, "color": "default"} for name in names]}


# The user's Tasks database, including the checkbox whose name is a single space.
TASKS_PROPERTIES = {
    "Name": {"id": "title", "name": "Name", "type": "title", "title": {}},
    " ": {"id": "f%5Cq%3A", "name": " ", "type": "checkbox", "checkbox": {}},
    "Date": {"id": "Qv%7Bz", "name": "Date", "type": "date", "date": {}},
    "Priority": {"id": "W%5BNU", "name": "Priority", "type": "select", "select": select("High", "Medium", "Low", "None")},
    "Project": {"id": "%7CbqX", "name": "Project", "type": "select", "select": select("Task", "David", "Arch", "Steady")},
}


class FakeNotion:
    def __init__(self):
        self.lock = threading.Lock()
        self.sources = {
            "ds-tasks": {"title": "Tasks", "properties": TASKS_PROPERTIES},
            "ds-expenses": {
                "title": "Expenses",
                "properties": {
                    "Name": {"id": "title", "name": "Name", "type": "title", "title": {}},
                    "Amount": {"id": "amt", "name": "Amount", "type": "number", "number": {}},
                    "Date": {"id": "when", "name": "Date", "type": "date", "date": {}},
                },
            },
            "ds-books": {
                "title": "Books",
                "properties": {
                    "Name": {"id": "title", "name": "Name", "type": "title", "title": {}},
                    "Read": {"id": "read", "name": "Read", "type": "checkbox", "checkbox": {}},
                    "Finished": {"id": "fin", "name": "Finished", "type": "date", "date": {}},
                },
            },
        }
        self.pages = {}
        self.requests = []  # (method, path, status), oldest first
        self.queried = []  # the (on_or_after, on_or_before) of every query, oldest first
        # None, or "all" / "writes" with an HTTP status to answer with instead.
        self.fail = None
        self.fail_status = 500

    def add_task(self, title, day, time=None, priority=None, project="Task", done=False, source="ds-tasks"):
        """Adds a page as if it had been made in Notion. `time` is "HH:MM" in UTC+02:00."""
        start = day.isoformat() if time is None else f"{day.isoformat()}T{time}:00.000+02:00"
        page_id = str(uuid.uuid4())
        self.pages[page_id] = {
            "object": "page",
            "id": page_id,
            "created_time": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z"),
            "in_trash": False,
            "is_archived": False,
            "parent": {"type": "data_source_id", "data_source_id": source},
            "properties": {
                "Name": {"id": "title", "type": "title", "title": text(title)},
                " ": {"id": "f%5Cq%3A", "type": "checkbox", "checkbox": done},
                "Date": {"id": "Qv%7Bz", "type": "date", "date": {"start": start, "end": None, "time_zone": None}},
                "Priority": {"id": "W%5BNU", "type": "select", "select": {"name": priority} if priority else None},
                "Project": {"id": "%7CbqX", "type": "select", "select": {"name": project} if project else None},
            },
        }
        return page_id

    def tasks(self):
        """A plain summary of the pages in Tasks, for assertions."""
        out = []
        for page in self.pages.values():
            p = page["properties"]
            out.append({
                "id": page["id"],
                "title": "".join(part["plain_text"] for part in p["Name"]["title"]),
                "start": (p["Date"]["date"] or {}).get("start"),
                "done": p[" "]["checkbox"],
                "priority": (p["Priority"]["select"] or {}).get("name"),
                "project": (p["Project"]["select"] or {}).get("name"),
                "in_trash": page["in_trash"],
            })
        return out

    # --- the API ---

    def handle(self, method, path, headers, body):
        """Returns (status, json body, extra headers)."""
        token = headers.get("Authorization", "").removeprefix("Bearer ")
        if headers.get("Notion-Version") != VERSION:
            return error(400, "missing_version", f"Notion-Version must be {VERSION}")
        if not token or token.startswith("bad"):
            return error(401, "unauthorized", "API token is invalid.")
        write = method == "PATCH" or path == "/v1/pages"
        if self.fail == "all" or (self.fail == "writes" and write):
            extra = {"Retry-After": "1"} if self.fail_status == 429 else {}
            codes = {400: "validation_error", 429: "rate_limited", 500: "internal_server_error", 502: "bad_gateway"}
            return error(self.fail_status, codes.get(self.fail_status, "internal_server_error"), "scripted failure", extra)

        if (method, path) == ("GET", "/v1/users/me"):
            return 200, {"object": "user", "id": "bot", "type": "bot", "name": "StillNote", "bot": {}}, {}
        if (method, path) == ("POST", "/v1/search"):
            results = [self.source(source_id) for source_id in self.sources]
            return 200, listing(results, body), {}
        match = re.fullmatch(r"/v1/data_sources/([^/]+)(/query)?", path)
        if match and match.group(1) not in self.sources:
            return error(404, "object_not_found", "Could not find data source.")
        if match and method == "GET" and not match.group(2):
            return 200, self.source(match.group(1)), {}
        if match and method == "POST" and match.group(2):
            return 200, listing(self.query(match.group(1), body.get("filter")), body), {}
        if (method, path) == ("POST", "/v1/pages"):
            return self.create(body)
        match = re.fullmatch(r"/v1/pages/([^/]+)", path)
        if match and method == "PATCH":
            return self.update(match.group(1), body)
        return error(400, "invalid_request_url", "Invalid request URL.")

    def source(self, source_id):
        source = self.sources[source_id]
        return {
            "object": "data_source",
            "id": source_id,
            "title": text(source["title"]),
            "in_trash": False,
            "parent": {"type": "database_id", "database_id": "db-" + source_id},
            "properties": source["properties"],
        }

    def query(self, source_id, filter_):
        conditions = (filter_ or {}).get("and", [filter_] if filter_ else [])
        dates = {key: value for condition in conditions for key, value in condition.get("date", {}).items()}
        self.queried.append((dates.get("on_or_after"), dates.get("on_or_before")))
        names = {p["id"]: name for name, p in self.sources[source_id]["properties"].items()}

        def matches(page):
            for condition in conditions:
                name = names.get(condition["property"], condition["property"])
                value = (page["properties"].get(name, {}).get("date") or {}).get("start")
                if value is None:
                    return False
                # Notion compares in UTC.
                day = value[:10] if "T" not in value else datetime.fromisoformat(value).astimezone(timezone.utc).date().isoformat()
                if "on_or_after" in condition["date"] and day < condition["date"]["on_or_after"]:
                    return False
                if "on_or_before" in condition["date"] and day > condition["date"]["on_or_before"]:
                    return False
            return True

        pages = [p for p in self.pages.values() if p["parent"]["data_source_id"] == source_id and not p["in_trash"]]
        return [page for page in pages if matches(page)]

    def resolve(self, source_id, properties):
        """Property values keyed by name, whether the request used names or ids."""
        schema = self.sources[source_id]["properties"]
        by_id = {p["id"]: name for name, p in schema.items()}
        out = {}
        for key, value in properties.items():
            name = key if key in schema else by_id.get(key)
            if name is None:
                raise ValueError(f"{key} is not a property that exists.")
            kind = schema[name]["type"]
            if kind not in value:
                raise ValueError(f"{name} is expected to be {kind}.")
            out[name] = {"id": schema[name]["id"], "type": kind, kind: value[kind]}
            if kind == "title":
                out[name]["title"] = text("".join(part["text"]["content"] for part in value["title"]))
            if kind == "date" and value["date"] is not None:
                out[name]["date"] = {"start": value["date"]["start"], "end": None, "time_zone": None}
        return out

    def create(self, body):
        parent = body.get("parent", {})
        source_id = parent.get("data_source_id")
        if parent.get("type") != "data_source_id" or source_id not in self.sources:
            return error(404, "object_not_found", "Could not find data source.")
        try:
            values = self.resolve(source_id, body.get("properties", {}))
        except (ValueError, KeyError, TypeError) as problem:
            return error(400, "validation_error", str(problem))
        page_id = self.add_task("", date.today(), source=source_id)
        self.pages[page_id]["properties"].update(values)
        return 200, self.pages[page_id], {}

    def update(self, page_id, body):
        page = self.pages.get(page_id)
        if page is None:
            return error(404, "object_not_found", "Could not find page.")
        try:
            values = self.resolve(page["parent"]["data_source_id"], body.get("properties", {}))
        except (ValueError, KeyError, TypeError) as problem:
            return error(400, "validation_error", str(problem))
        if values and page["in_trash"] and body.get("in_trash") is not False:
            return error(400, "validation_error", "Can't edit a page that is in the trash.")
        if "in_trash" in body:
            page["in_trash"] = bool(body["in_trash"])
        page["properties"].update(values)
        return 200, page, {}


def error(status, code, message, headers=None):
    return status, {"object": "error", "status": status, "code": code, "message": message}, headers or {}


def listing(results, body):
    size = min(int(body.get("page_size", 100)), 100)
    start = int(body.get("start_cursor") or 0)
    more = start + size < len(results)
    return {
        "object": "list",
        "results": results[start:start + size],
        "has_more": more,
        "next_cursor": str(start + size) if more else None,
        "type": "page_or_data_source",
        "page_or_data_source": {},
    }


def serve(notion, port=8787, quiet=False):
    """Starts the server on a background thread and returns it; call `.shutdown()` to stop."""

    class Handler(BaseHTTPRequestHandler):
        def respond(self):
            length = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(length) if length else b""
            try:
                body = json.loads(raw) if raw else {}
            except ValueError:
                body = None
            with notion.lock:
                if body is None:
                    status, payload, extra = error(400, "invalid_json", "Error parsing JSON body.")
                else:
                    status, payload, extra = notion.handle(self.command, self.path.split("?")[0], self.headers, body)
                notion.requests.append((self.command, self.path, status))
            data = json.dumps(payload).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            for name, value in extra.items():
                self.send_header(name, value)
            self.end_headers()
            self.wfile.write(data)

        do_GET = do_POST = do_PATCH = respond

        def log_message(self, format, *args):
            if not quiet:
                print(f"{self.command} {self.path} -> {args[1] if len(args) > 1 else ''}", flush=True)

    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


if __name__ == "__main__":
    notion = FakeNotion()
    today = date.today()
    notion.add_task("Reply to Marco about Friday", today, "10:30", "High")
    notion.add_task("Weekly review in Notion", today, "13:00", "Medium", project="David")
    notion.add_task("Read 20 pages", today)
    notion.add_task("Water the plants", today, "08:00", "Low", done=True)
    serve(notion)
    print("Fake Notion on http://127.0.0.1:8787. Ctrl+C stops it.", flush=True)
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        pass
