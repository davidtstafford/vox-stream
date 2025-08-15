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

---
(End of Draft – to be iterated as dynamic config schema + event mapping mature.)
