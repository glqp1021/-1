/**
 * RelaySwitch Cloudflare Worker
 * 功能 1：/api/providers  —— 用 Cloudflare KV 云端同步你的中转站列表（电脑编辑、小米手机一键拉取）
 * 功能 2：/proxy/<target> —— 可选透明转发，当某些中转站直连不稳定时可通过 Cloudflare 边缘节点中转
 *
 * 环境变量：
 * - RELAY_KV: 绑定的 KV Namespace
 * - SYNC_TOKEN: 自定义同步密码（用 `wrangler secret put SYNC_TOKEN` 设置）
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // 1. 中转站配置云同步接口
    if (url.pathname === "/api/providers") {
      const auth = request.headers.get("Authorization") || "";
      if (!env.SYNC_TOKEN || auth !== `Bearer ${env.SYNC_TOKEN}`) {
        return new Response(JSON.stringify({ error: "Unauthorized" }), {
          status: 401,
          headers: { "Content-Type": "application/json" }
        });
      }

      if (request.method === "GET") {
        const data = (await env.RELAY_KV.get("providers")) || "[]";
        return new Response(data, {
          status: 200,
          headers: { "Content-Type": "application/json; charset=utf-8" }
        });
      }

      if (request.method === "PUT") {
        const body = await request.text();
        try {
          JSON.parse(body);
        } catch {
          return new Response(JSON.stringify({ error: "Invalid JSON" }), {
            status: 400,
            headers: { "Content-Type": "application/json" }
          });
        }
        await env.RELAY_KV.put("providers", body);
        return new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { "Content-Type": "application/json" }
        });
      }

      return new Response("Method Not Allowed", { status: 405 });
    }

    // 2. 可选通用 API 转发：请求 https://你的worker/proxy/https://中转站域名/v1/chat/completions
    if (url.pathname.startsWith("/proxy/")) {
      const targetUrl = url.pathname.slice("/proxy/".length) + url.search;
      if (!targetUrl.startsWith("https://")) {
        return new Response("Target must start with https://", { status: 400 });
      }
      const forwardHeaders = new Headers(request.headers);
      forwardHeaders.delete("host");
      forwardHeaders.delete("cf-connecting-ip");

      return fetch(targetUrl, {
        method: request.method,
        headers: forwardHeaders,
        body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body
      });
    }

    return new Response("RelaySwitch Sync Worker is running.", { status: 200 });
  }
};
