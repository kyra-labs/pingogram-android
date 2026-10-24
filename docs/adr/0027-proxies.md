# 0027 · Proxies

Status: proposed (the owner's item 8 of 2026-10-23: "add, edit, test (pingProxy), enable/disable, delete SOCKS5/HTTP/
MTProto; tg://proxy and t.me/socks links through LinkRepository with a confirmation sheet; the connection pill
'Connecting to proxy…'"). The change to `REQUIREMENTS.md` §6.2 below waits for the owner's confirmation.

## Context

Telegram is blocked or throttled in some countries, and people there reach it through proxies. TDLib (a94b83d) does
all of it:

- `addProxy(proxy, enable, comment)`, `editProxy(proxy_id, proxy, enable, comment)`, `enableProxy`, `disableProxy`,
  `removeProxy`, `getProxies`, each `addedProxy(id, last_used_date, is_enabled, comment, proxy)`. TDLib keeps them in
  its database, credentials included, and connects through the enabled one, at most one at a time. There is no update
  for the list: only the function that changed it knows.
- `proxy(server, port, type)` with `proxyTypeSocks5(username, password)`, `proxyTypeHttp(username, password,
  http_only)` and `proxyTypeMtproto(secret)`.
- `pingProxy(proxy)` connects to the proxy and sends one request to Telegram through it, answering `seconds`;
  `testProxy` checks a single data center and is not needed for a list.
- `getInternalLinkType` turns `tg://proxy?…`, `tg://socks?…`, `t.me/proxy?…` and `t.me/socks?…` into
  `internalLinkTypeProxy(proxy)`.
- `connectionStateConnectingToProxy`, which the chat list's connection pill already says as "Connecting to proxy…".
- With an MTProto proxy whose owner registered a sponsor, TDLib adds the sponsor's channel to the main chat list
  (a chat position whose `source` is `chatSourceMtprotoProxy`).

## Decision

- **Storage**: TDLib's own, nothing in Pingogram's settings. `ProxyRepository` (`TdlibProxyRepository`, a singleton
  without a cache) reads `getProxies` when collected and again after every change made through it, so the Proxy page,
  the Data and storage row and a link's sheet stay in step without an update. Passwords and secrets never reach logs
  (`toString()` of `Proxy`, `ProxyType`, `SavedProxy` and `ProxyLinkRoute` leaves them out) or the saved state of the
  form (`EditProxyViewModel` keeps them in memory only).
- **Pages**: Settings → Data and storage → "Proxy" (a "Network" group): "Use proxy" (off connects directly, on uses the
  proxy used last), "Add proxy", "Check again", and the proxies as radio rows with their protocol and ping, each with
  Edit and Delete (asked first). The form checks the address, the port (1–65535) and an MTProto secret's characters on
  the device before TDLib sees them; "Check" pings the proxy as typed; a new proxy is used at once ("Save and
  connect"), an edited one keeps whether it was in use. Pings go to the proxies the user added and to nothing else,
  four at a time, once per proxy when the page opens and on "Check again"; never in the background.
- **Links**: `LinkRepository` resolves them to `LinkTarget.ProxyServer`; the app opens `ProxyLinkRoute`, a dialog
  destination of the settings pages (like a sticker set's sheet), which shows the server, port and protocol (never the
  credentials or the secret), pings it, says what a proxy's owner can see, and adds and uses it only on "Connect"; a
  proxy the user already has is used again rather than added twice. Links from other apps wait for the login like
  every link.
- **The sponsored channel** of an MTProto proxy is Telegram's terms' business (sponsored content must not be hidden):
  the chat list shows it as TDLib places it. Labelling it ("Proxy sponsor") is the chat list's, `feature:chats`
  (Session A), noted there as a follow-up.

## Consequences

- `REQUIREMENTS.md` §6.2 says the network goes "only to Telegram data centers (via TDLib) and user-requested links".
  A proxy is a server the user chose that Telegram's traffic goes through; pings go to it too. Proposed wording, for
  the owner to confirm before it is changed: "Network only to Telegram data centers (via TDLib), through a proxy only
  when the user added and enabled one (`docs/adr/0027-proxies.md`), and to user-requested links".
- Proxies cannot be set before signing in: the login screens have no settings, and a proxy link opened while signed
  out waits for the login. Where Telegram is blocked, that is when a proxy is needed most. Offering "Proxy" on the
  Welcome and phone screens (`feature:auth`) is a follow-up for the owner to decide.
- No new permission, library or network endpoint: everything goes through TDLib.
