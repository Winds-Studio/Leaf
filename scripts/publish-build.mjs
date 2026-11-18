#!/usr/bin/env node
// Publishes a build through Fill's v3 API.
// Files are uploaded straight to R2 via presigned URLs; Fill only handles metadata.
//
// Usage example:
//   FILL_API=https://api.example.com FILL_USERNAME=ci FILL_PASSWORD=... node publish.mjs \
//     --project myproject --family 1.21 --version 1.21.4 --build 42 --channel STABLE \
//     --repo /path/to/git/checkout \
//     --download server:default=build/libs/myproject-1.21.4-42.jar
//
// The uploaded file keeps its local file name, so rename it beforehand if needed.
// Commits are collected from the previous build's newest commit to HEAD (needs a full git history,
// e.g. actions/checkout with fetch-depth: 0). Use --since <sha> to override.

import { execFileSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { parseArgs } from "node:util";

const { values: args } = parseArgs({
  options: {
    api: { type: "string", default: process.env.FILL_API },
    project: { type: "string" },
    family: { type: "string" },
    version: { type: "string" },
    build: { type: "string" },
    channel: { type: "string", default: "ALPHA" },
    repo: { type: "string", default: "." },
    since: { type: "string" },
    download: { type: "string", multiple: true },
  },
});
for (const key of ["api", "project", "family", "version", "build", "download"]) {
  if (!args[key]) {
    console.error(`missing --${key}`);
    process.exit(2);
  }
}
const { FILL_USERNAME, FILL_PASSWORD, FILL_USER_AGENT } = process.env;
if (!FILL_USERNAME || !FILL_PASSWORD || !FILL_USER_AGENT) {
  console.error("FILL_USERNAME, FILL_PASSWORD and FILL_USER_AGENT are required");
  process.exit(2);
}

const api = args.api.replace(/\/+$/, "");
const auth = "Basic " + Buffer.from(`${FILL_USERNAME}:${FILL_PASSWORD}`).toString("base64");
const enc = encodeURIComponent;
const UA = FILL_USER_AGENT;

async function post(route, body) {
  const res = await fetch(api + route, {
    method: "POST",
    headers: { Authorization: auth, "Content-Type": "application/json", "User-Agent": UA },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`POST ${route} -> ${res.status}: ${text}`);
  return text ? JSON.parse(text) : null;
}

async function previousCommit() {
  if (args.since) return args.since;
  const res = await fetch(`${api}/v3/projects/${enc(args.project)}/versions/${enc(args.version)}/builds/latest`, {
    headers: { "Cache-Control": "no-cache", "User-Agent": UA },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`latest build lookup -> ${res.status}`);
  return (await res.json()).commits[0]?.sha ?? null;
}

function gitLog(range) {
  const out = execFileSync("git", ["-C", args.repo, "log", "--format=%H%x1f%cI%x1f%B%x1e", ...range], { encoding: "utf8" });
  return out
    .split("\x1e")
    .map((record) => record.trim())
    .filter(Boolean)
    .map((record) => {
      const [sha, time, message] = record.split("\x1f");
      return { sha, time: new Date(time).toISOString(), message: message.trim() };
    });
}

const since = await previousCommit();
// git log is newest first; Fill expects oldest first and reverses it on save
const commits = gitLog(since ? [`${since}..HEAD`] : ["-1", "HEAD"]).reverse();

const id = randomUUID();
const downloads = {};
for (const spec of args.download) {
  const at = spec.indexOf("=");
  if (at <= 0) throw new Error(`--download must be key=path, got ${spec}`);
  const key = spec.slice(0, at);
  const file = spec.slice(at + 1);
  const content = await readFile(file);
  const md5 = createHash("md5").update(content).digest();
  const download = {
    name: path.basename(file),
    type: file.endsWith(".jar") ? "application/java-archive" : "application/octet-stream",
    checksums: { md5: md5.toString("hex"), sha256: createHash("sha256").update(content).digest("hex") },
    size: content.length,
  };

  const { url } = await post("/v3/publishing/stage", { id, download });
  // These headers are part of the presigned signature and must match exactly.
  const upload = await fetch(url, {
    method: "PUT",
    headers: {
      "Content-Type": download.type,
      "Content-MD5": md5.toString("base64"),
      "x-amz-meta-sha256": download.checksums.sha256,
    },
    body: content,
  });
  if (!upload.ok) throw new Error(`upload of ${download.name} -> ${upload.status}: ${await upload.text()}`);
  console.log(`uploaded ${key} ${download.name} (${download.size} bytes)`);
  downloads[key] = download;
}

await post("/v3/publishing/publish", {
  id,
  project: args.project,
  family: args.family,
  version: args.version,
  build: Number(args.build),
  time: new Date().toISOString(),
  channel: args.channel.toUpperCase(),
  commits,
  downloads,
});
console.log(`published ${args.project} ${args.version} #${args.build} with ${commits.length} commit(s)`);
