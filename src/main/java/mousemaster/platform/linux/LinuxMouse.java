package mousemaster.platform.linux;

import mousemaster.platform.MouseController;

// Common supertype for X11Mouse and WaylandMouse, so LinuxPlatform's mouse field
// can only ever be one of this platform's own MouseController implementations.
public abstract class LinuxMouse implements MouseController {

    // Overridden by WaylandMouse: XWayland's XQueryPointer-mirrored pointer state doesn't
    // reliably reflect motion injected via zwlr_virtual_pointer_v1, so LinuxPlatform's
    // position-listener polling needs the last position we ourselves told the compositor
    // to move to, instead of (or in preference to) an XQueryPointer round-trip.
    public Integer lastSyntheticX() {
        return null;
    }

    public Integer lastSyntheticY() {
        return null;
    }
}
