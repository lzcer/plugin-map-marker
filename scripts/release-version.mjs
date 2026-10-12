import { appendFileSync } from "node:fs";

const tag = process.env.RELEASE_TAG;
const match = /^v((?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*))*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?)$/.exec(tag ?? "");

if (!match) {
  throw new Error(`无效的版本 tag：${tag ?? "未设置"}，请使用 v1.2.3 或 v1.2.3-dev.1。`);
}

const version = match[1];
const prerelease = Boolean(match[2]);
const output = `version=${version}\nprerelease=${prerelease}\n`;

if (process.env.GITHUB_OUTPUT) {
  appendFileSync(process.env.GITHUB_OUTPUT, output, "utf8");
}

process.stdout.write(output);
