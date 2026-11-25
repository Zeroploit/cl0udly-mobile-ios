// A small stand-in for `opencode serve` (1.x API), for the desktop preview.
//
// It exists so the shared screens can be looked at on Windows with realistic
// data without pointing the app at somebody's real server: three sessions, one
// conversation with markdown, a code block and tool calls, and an SSE endpoint
// that stays open (the app reconnects if it closes).
//
//   node tools/preview-server.mjs [port]
//
// The shapes mirror what `OpenCodeApi` parses; this is not a general mock.

import { createServer } from "node:http";

const port = Number(process.argv[2] ?? 4097);
const now = Date.now();

const sessions = [
  {
    id: "ses_preview_1",
    title: "Add a dark mode toggle to settings",
    directory: "C:\\Users\\Luna\\Desktop\\cl0udly-mobile-main",
    time: { created: now - 3 * 3600_000, updated: now - 12 * 60_000 },
  },
  {
    id: "ses_preview_2",
    title: "Why is the iOS build 12 MB larger",
    directory: "C:\\Users\\Luna\\Desktop\\cl0udly-mobile-main",
    time: { created: now - 26 * 3600_000, updated: now - 5 * 3600_000 },
  },
  {
    id: "ses_preview_3",
    title: "New session",
    directory: "/root/chats",
    time: { created: now - 50 * 3600_000, updated: now - 49 * 3600_000 },
  },
];

const text = (id, messageID, body) => ({
  id, messageID, sessionID: "ses_preview_1", type: "text", text: body,
});

const tool = (id, messageID, name, state) => ({
  id, messageID, sessionID: "ses_preview_1", type: "tool", tool: name, state,
});

const messages = [
  {
    info: {
      id: "msg_01", sessionID: "ses_preview_1", role: "user",
      time: { created: now - 40 * 60_000, completed: now - 40 * 60_000 },
    },
    parts: [text("prt_01", "msg_01", "Add a dark mode toggle to the settings screen, and remember the choice.")],
  },
  {
    info: {
      id: "msg_02", sessionID: "ses_preview_1", role: "assistant",
      providerID: "anthropic", modelID: "claude-sonnet-4-6",
      time: { created: now - 39 * 60_000, completed: now - 37 * 60_000 },
      tokens: { input: 8123, output: 640 },
    },
    parts: [
      text("prt_02", "msg_02", "I'll find where settings are stored first."),
      tool("prt_03", "msg_02", "grep", {
        status: "completed",
        title: "grep  dark_mode  app/src",
        input: { pattern: "dark_mode" },
        output: "app/src/main/java/app/ocmobile/data/Prefs.kt:69",
      }),
      tool("prt_04", "msg_02", "read", {
        status: "completed",
        title: "read  Prefs.kt",
        input: { filePath: "app/src/main/java/app/ocmobile/data/Prefs.kt" },
      }),
      text("prt_05", "msg_02",
        "The preference already exists in `Prefs`, so the work is the switch plus the **theme** wiring:\n\n" +
        "```kotlin\nvar colorMode by str(\"color_mode\", \"system\") // system | light | dark\n```\n\n" +
        "I'll add a row in *Settings → Appearance* and make `OcTheme` read it instead of only the system flag."),
      tool("prt_06", "msg_02", "bash", {
        status: "running",
        title: "bash  ./gradlew :app:assembleDebug",
        input: { command: "./gradlew :app:assembleDebug" },
      }),
    ],
  },
];

const json = (res, code, body) => {
  const payload = JSON.stringify(body);
  res.writeHead(code, { "content-type": "application/json", "content-length": Buffer.byteLength(payload) });
  res.end(payload);
};

createServer((req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${port}`);
  const path = url.pathname;

  if (path === "/global/health") return json(res, 200, { healthy: true, version: "1.9.4" });
  if (path === "/global/event") {
    res.writeHead(200, { "content-type": "text/event-stream", "cache-control": "no-cache" });
    res.write(": connected\n\n");
    const keep = setInterval(() => res.write(": ping\n\n"), 10_000);
    req.on("close", () => clearInterval(keep));
    return;
  }
  if (path === "/session" && req.method === "GET") return json(res, 200, sessions);
  if (path === "/session" && req.method === "POST") return json(res, 200, sessions[2]);
  if (path === "/session/status") return json(res, 200, {});
  if (path === "/project") return json(res, 200, []);
  if (path === "/permission") return json(res, 200, []);
  if (path === "/question") return json(res, 200, []);
  if (path === "/path") return json(res, 200, { directory: sessions[0].directory });

  const messagesMatch = path.match(/^\/session\/([^/]+)\/message$/);
  if (messagesMatch) {
    return json(res, 200, messagesMatch[1] === "ses_preview_1" ? messages : []);
  }
  if (/^\/session\/[^/]+\/todo$/.test(path)) return json(res, 200, []);
  if (/^\/session\/[^/]+\/(prompt_async|abort|summarize|revert|unrevert)$/.test(path)) {
    req.resume();
    return json(res, 200, true);
  }
  if (/^\/session\/[^/]+$/.test(path)) {
    const id = path.split("/")[2];
    return json(res, 200, sessions.find((s) => s.id === id) ?? sessions[0]);
  }
  if (path === "/agent") return json(res, 200, []);
  if (path === "/command" || path === "/skill") return json(res, 200, []);

  json(res, 404, { message: "not found" });
}).listen(port, "127.0.0.1", () => {
  console.log(`preview server on http://127.0.0.1:${port}`);
});
