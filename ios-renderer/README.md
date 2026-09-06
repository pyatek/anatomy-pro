# ios-renderer

The Swift and Objective-C++ Filament host for iOS. Empty until Phase 0.

This target implements `FilamentBridge` (declared in Kotlin, in the `renderer-filament`
module's `iosMain`) and registers itself with `FilamentBridgeRegistry.bridge` during app
startup. The Kotlin framework never links Filament directly, which makes the graphics
provider a link-time choice rather than a code change (spec §4.1).

Retiring the risk that this target is unworkable is the entire purpose of Phase 0. If it
proves unworkable, the documented fallback is three.js in a WKWebView behind the same
`AnatomyRenderer` interface (spec §14).
