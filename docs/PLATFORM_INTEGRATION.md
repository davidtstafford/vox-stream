# VoxStream Platform Integration Guide (Draft)

## 1. Purpose
Provide a structured process for adding new streaming platform connectors (first-party or plugin) to VoxStream using the Platform API, Spring DI, and Java SPI fallback.

## 2. Core Concepts
- PlatformConnection: Runtime connection lifecycle + status + event emission.
- PlatformConnectionFactory: Creates a PlatformConnection and supplies PlatformMetadata.
- PlatformMetadata: Describes id, display name, version, capabilities.
- Capability enum: Declares optional feature areas (EVENTS, CHAT, TTS_INPUT, RESPONSES, WEBHOOK, IRC_CHAT) surfaced in UI tooltips.
- PlatformConnectionRegistry: Discovers factories (Spring first, then SPI) and exposes metadata + lazy singleton connections.
- PlatformConnectionManager: Orchestrates connect/reconnect, status propagation, metrics, and publishes SYSTEM + UNKNOWN events.

## 3. Discovery & Loading
1. Spring Bean (preferred): Define a @Component (or @Service) implementing PlatformConnectionFactory to leverage DI (credentials services, REST clients, etc.).
2. SPI Fallback: Package an implementation on classpath and add a service descriptor file:
   META-INF/services/com.voxstream.platform.api.PlatformConnectionFactory
   containing the fully-qualified implementation class name (one per line).
3. Precedence: If both mechanisms provide the same platformId, the Spring bean wins; SPI instance is ignored.

## 4. Platform Identifier Rules
- Stable, lowercase, URL-safe string (e.g. twitch, youtube, dummy2).
- Used in config keys, events (sourcePlatform), and UI rows.
- Changing an id is a breaking change (treat as new platform + migration if ever required).

## 5. Enabling a Platform
- Global master switch: platform.enabled (CoreConfigKeys.PLATFORM_ENABLED) must be true.
- Per-platform dynamic flag: platform.<id>.enabled (Boolean, default=false) evaluated via ConfigurationService.getDynamicBoolean().
- For legacy Twitch key twitch.enabled is still respected (precedence over dynamic key for id twitch).
- No static registration needed—once factory present and flag enabled, PlatformConnectionManager attempts connection on start().

## 6. Implementing PlatformConnectionFactory
Minimal example:
```
@Component // or omit and use SPI
public class MyPlatformConnectionFactory implements PlatformConnectionFactory {
    @Override
    public String platformId() { return "myplatform"; }

    @Override
    public PlatformConnection create() { return new MyPlatformConnection(); }

    @Override
    public PlatformMetadata metadata() {
        return new PlatformMetadata(platformId(), "My Platform", "1.0.0",
                Set.of(Capability.EVENTS, Capability.CHAT));
    }
}
```

## 7. Implementing PlatformConnection
Responsibilities:
- Manage actual network sessions (WS/HTTP/etc.).
- Maintain internal state and expose via status().
- Fire status transition callbacks to registered listeners (addStatusListener()).
- Emit PlatformEvent objects for platform-originated activities (addPlatformEventListener()).
- Provide connect(), disconnect(), requestReconnect() (optional), healthCheck() (optional) returning CompletableFuture<Boolean>.

Status Handling Guidelines:
- Use PlatformStatus.connected(connectedSinceEpochMs), .connecting(), .failed(detail[, fatal]), .reconnectScheduled(delayMs, attempt), .disconnected().
- Mark fatal=true only when retrying is pointless (e.g. invalid credentials).

## 8. Events & Mapping
Current Phase (3.4): PlatformConnectionManager wraps PlatformEvent into internal BaseEvent with EventType.UNKNOWN (placeholder until dedicated mapping layer introduced). Future phases will:
- Introduce per-platform event mapper -> internal rich Event types.
- Support filtering and TTS routing based on capabilities.

PlatformEvent Contract (summary):
- platform(): platform id
- type(): platform-specific classification string/enum
- timestamp(): Instant of occurrence

## 9. Capabilities
Declare only what is actually implemented:
- EVENTS: Emits generic events.
- CHAT / IRC_CHAT: Real-time chat stream (IRC_CHAT implies raw IRC semantics; CHAT for abstracted message API).
- TTS_INPUT: Source events can feed TTS subsystem.
- RESPONSES: Supports sending commands or chat messages back.
- WEBHOOK: Uses outbound webhook style callbacks.

UI presently displays capabilities as tooltip text; future enhancements may conditionally enable UI panels.

## 10. Configuration Strategy
Two layers:
1. Static keys (CoreConfigKeys) for common cross-platform behavior (reconnect.* etc.).
2. Dynamic per-platform enable flags (platform.<id>.enabled) + future dynamic platform.<id>.* namespace for plugin-specific settings.

Plugin-Specific Settings (Future Plan):
- Provide a descriptor or self-registration of config metadata (name, type, default, validator) to allow UI generation without code changes.

## 11. Reconnect & Backoff
- Initial + max delay, jitter %, max attempts, and reset-after-stable configurable via CoreConfigKeys.
- Manager doubles base delay until max; applies symmetric jitter; schedules reconnect unless fatal.
- Stable connection beyond reset threshold zeroes backoff (base exponent retained for next failure doubling logic).

## 12. Metrics & Observability
PlatformConnectionManager.Metrics per platform:
- connects, disconnects, failedAttempts, currentBackoffMs.
Snapshot & status events surfaced via SYSTEM events (payload contains platform, state, detail, backoff, lastSuccessful).

## 13. Testing a New Platform
Recommended Tests:
- Factory metadata: id uniqueness, capabilities set.
- Connection lifecycle: successful connect/disconnect.
- Failure & retry: inject transient failures -> verify backoff progression & reset.
- Fatal path: ensure fatal prevents further attempts.
- Event emission: synthetic PlatformEvent delivered to EventBus as UNKNOWN.
- SPI discovery (if using SPI): registry loads when Spring bean absent.

Use DummyPlatformConnection / DummySecondPlatformConnection as reference for minimal always-connected or scripted-failure implementations.

## 14. Packaging & Distribution
If distributing an external plugin:
- Provide your jar with dependency shading if necessary (avoid classpath conflicts).
- Include META-INF/services descriptor.
- Version alignment: maintain semantic version in PlatformMetadata.version (used for future compatibility checks / UI display).

## 15. Future Roadmap (Related to Platforms)
- Multi-account per platform (registry moving from singleton -> keyed instances).
- Dynamic configuration schema publication for plugins.
- Event type mapping layer (platform -> unified internal model).
- Response system leveraging RESPONSES capability.
- Hot-reloadable plugin directory scan.

## 16. Checklist (Quick Start)
- [ ] Choose platform id (lowercase, stable).
- [ ] Implement PlatformConnection.
- [ ] Implement PlatformConnectionFactory with metadata().
- [ ] Add Spring @Component OR SPI descriptor.
- [ ] Provide capability set.
- [ ] Test connection lifecycle & backoff.
- [ ] Add platform.<id>.enabled=true to config for local run.
- [ ] Verify UI row appears with tooltip capabilities.
- [ ] Write unit tests (registry discovery, status transitions, metadata).

## 17. Example: Dummy Second Platform
See: voxstream-platform-api/src/main/java/com/voxstream/platform/api/dummy2/
Demonstrates:
- SPI + metadata (EVENTS only).
- Always CONNECTED behavior used in tests & UI enumeration.

## 18. Twitch OAuth & PKCE Authentication Reference
This section documents the Twitch-specific OAuth implementation so platform/plugin authors understand how authentication is currently handled and where future multi-platform credential abstractions will align.

### 18.1 Key Configuration Properties
- `twitch.clientId` (String) – REQUIRED. Pre-seeded with the default development client id in code; replace with your own in production.
- `twitch.clientSecret` (String) – OPTIONAL when `twitch.oauth.pkce.enabled=true` (default). Required only if you disable PKCE.
- `twitch.oauth.pkce.enabled` (Boolean, default `true`) – Enables Authorization Code + PKCE (S256) flow. When true, no client secret is sent or stored; the UI labels the secret field as optional and will not persist an empty value.
- `twitch.redirectPort` (Integer, default `51515`) – Local loopback HTTP server port used to capture the authorization code. The effective redirect URI is: `http://localhost:<port>/callback` (register this exact URL in your Twitch application).
- `twitch.scopes` (Comma-separated) – Stored as comma list; converted to space-delimited during auth request (Twitch requires space separated scopes).
- `twitch.enabled` (Boolean) – Legacy enable flag for Twitch (still honored); also governed by global `platform.enabled`.

### 18.2 Authorization Flow (PKCE Enabled)
1. User clicks Connect (or token missing on startup); service generates:
   - `state` (UUID)
   - `code_verifier` (64 random bytes base64url, no padding)
   - `code_challenge = BASE64URL(SHA256(code_verifier))`
2. Browser opened to `https://id.twitch.tv/oauth2/authorize` with parameters:
   - `response_type=code`
   - `client_id`
   - `redirect_uri=http://localhost:<port>/callback`
   - `scope` (space encoded as `%20`)
   - `state`
   - `code_challenge`, `code_challenge_method=S256`
3. Loopback server receives `code`; service exchanges it (POST `/oauth2/token`) with body including `code_verifier` (no client secret when PKCE enabled).
4. Access + refresh token stored (encrypted DAO) along with expiry and scopes; user id/login populated by validation + Helix `/users` enrichment.
5. Periodic validator task:
   - Calls `/oauth2/validate` to refresh expiry and confirm validity.
   - Refreshes the token proactively if <10m remaining.
6. Refresh flow (`grant_type=refresh_token`) omits `client_secret` when PKCE enabled.

### 18.3 Disabling PKCE (Not Recommended)
Set `twitch.oauth.pkce.enabled=false` to revert to classic Authorization Code flow which requires:
- `twitch.clientSecret` to be configured and non-blank.
The UI will resume requiring the secret and validation will enforce presence when Twitch is enabled. Only disable PKCE if a future Twitch requirement or unsupported edge case appears.

### 18.4 Testing & Automation Hooks
- System property `twitch.oauth.disableInteractive=true` suppresses browser launch / loopback flow (used in tests).
- Tokens near expiry (<5 min) trigger an asynchronous refresh.
- 401 responses on validation or refresh cause token deletion and automatic re-login prompt.

### 18.5 Security Notes
- PKCE removes the need to ship or persist a client secret, reducing credential leakage risk.
- Refresh tokens are persisted encrypted; revocation + deletion is performed when the user signs out.
- When PKCE enabled and the secret field is left blank, nothing is stored for `twitch.clientSecret`.

### 18.6 Planned Abstractions
Later phases will generalize:
- Per-platform credential descriptor publication.
- Unified OAuth/credential UI panels driven by metadata.
- Secure secret storage pluggability.

---
(End of Draft – to be iterated as dynamic config schema + event mapping mature.)
