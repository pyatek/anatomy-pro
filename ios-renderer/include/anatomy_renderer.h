/*
 * The C seam between Kotlin/Native and Filament.
 *
 * Kotlin reaches Filament only through this header, via cinterop (spec §4.1). Nothing
 * C++ appears here, and no callback crosses the boundary in either direction: events are
 * queued inside the shim and drained by the caller with ar_poll_event. That matters
 * because Filament delivers picking results on its own backend thread, and calling into
 * Kotlin/Native from an arbitrary thread is not safe.
 *
 * Node names, not StructureIds, cross this boundary. Mapping names back to structures is
 * the app's job; keeping that knowledge out of the shim is what keeps the shim narrow.
 */
#ifndef ANATOMY_RENDERER_H
#define ANATOMY_RENDERER_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ar_renderer ar_renderer;
typedef ar_renderer* ar_renderer_ref;

typedef enum {
    AR_EVENT_READY = 0,
    AR_EVENT_MODEL_LOADED = 1,
    AR_EVENT_MODEL_UNLOADED = 2,
    AR_EVENT_LOAD_PROGRESS = 3,
    AR_EVENT_PICKED = 4,
    AR_EVENT_MEMORY_PRESSURE = 5,
    AR_EVENT_ERROR = 6
} ar_event_type;

/*
 * A drained event. All char pointers are owned by the renderer and remain valid only
 * until the next ar_poll_event call on the same renderer. Fields not carried by a given
 * event type are NULL or zero.
 */
typedef struct {
    ar_event_type type;
    const char* uri;
    const char* node_name;
    const char* code;
    const char* message;
    float fraction;
    int64_t resident_bytes;
} ar_event;

ar_renderer_ref ar_create(void);
void ar_destroy(ar_renderer_ref renderer);

/* Renders to an offscreen swap chain, so contract tests need no window and no UI. */
void ar_attach_headless(ar_renderer_ref renderer, uint32_t width, uint32_t height);
/* Renders to a CAMetalLayer supplied by the host app. */
void ar_attach_layer(ar_renderer_ref renderer, void* ca_metal_layer, uint32_t width, uint32_t height);

void ar_load_model(ar_renderer_ref renderer, const char* uri);
void ar_unload_model(ar_renderer_ref renderer, const char* uri);

/* Enumerates the loaded asset's named nodes, from which the caller builds its index. */
size_t ar_node_count(ar_renderer_ref renderer);
const char* ar_node_name_at(ar_renderer_ref renderer, size_t index);

void ar_set_highlight(ar_renderer_ref renderer, const char* const* node_names, size_t count,
                      int32_t outline_argb, float luminance_shift);
void ar_clear_highlight(ar_renderer_ref renderer);

void ar_set_picking_enabled(ar_renderer_ref renderer, bool enabled);
/* Asynchronous: the result arrives as AR_EVENT_PICKED after subsequent frames render. */
void ar_pick_at(ar_renderer_ref renderer, float x_px, float y_px);

/*
 * Renders one frame.
 *
 * `vsync_nanos` must be the display's vsync timestamp when drawing to a layer — Filament
 * paces against it, and given an arbitrary clock reading it renders one frame and then
 * refuses every subsequent one. Pass 0 for offscreen rendering, where pacing is off.
 *
 * Returns false when Filament skipped the frame because too many are in flight.
 */
bool ar_render_frame(ar_renderer_ref renderer, uint64_t vsync_nanos);
/*
 * Blocks until the GPU has caught up.
 *
 * Exists for tests: picking results arrive some frames after ar_pick_at, and without a
 * sync point a contract test can only guess how many frames to render. Production render
 * loops must not call this.
 */
void ar_wait_for_gpu(ar_renderer_ref renderer);
/* Returns false when the queue is empty. */
bool ar_poll_event(ar_renderer_ref renderer, ar_event* out_event);

#ifdef __cplusplus
}
#endif

#endif /* ANATOMY_RENDERER_H */
