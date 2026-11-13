// probe displays: prints the bounds of each display in points, "x y width height", the main
// display first.
// probe cursor: prints the pointer location in points, "x y".
// probe window [x y]: moves the focused window of the frontmost app to that point, in points, and
// prints its frame in points, "x y width height".
#import <AppKit/AppKit.h>
#import <ApplicationServices/ApplicationServices.h>

int main(int argc, char **argv) {
    @autoreleasepool {
        if (strcmp(argv[1], "displays") == 0) {
            CGDirectDisplayID displays[8];
            uint32_t count;
            CGGetActiveDisplayList(8, displays, &count);
            CGDirectDisplayID main = CGMainDisplayID();
            for (int pass = 0; pass < 2; pass++)
                for (uint32_t i = 0; i < count; i++) {
                    if ((displays[i] == main) != (pass == 0))
                        continue;
                    CGRect bounds = CGDisplayBounds(displays[i]);
                    printf("%.0f %.0f %.0f %.0f\n", bounds.origin.x, bounds.origin.y,
                            bounds.size.width, bounds.size.height);
                }
            return 0;
        }
        if (strcmp(argv[1], "cursor") == 0) {
            CGEventRef event = CGEventCreate(NULL);
            CGPoint location = CGEventGetLocation(event);
            CFRelease(event);
            printf("%.0f %.0f\n", location.x, location.y);
            return 0;
        }
        pid_t pid = NSWorkspace.sharedWorkspace.frontmostApplication.processIdentifier;
        AXUIElementRef app = AXUIElementCreateApplication(pid);
        AXUIElementRef window = NULL;
        AXUIElementCopyAttributeValue(app, kAXFocusedWindowAttribute, (CFTypeRef *) &window);
        if (window == NULL) {
            fprintf(stderr, "no focused window\n");
            return 1;
        }
        if (argc == 4) {
            CGPoint point = {atof(argv[2]), atof(argv[3])};
            AXValueRef value = AXValueCreate(kAXValueCGPointType, &point);
            AXUIElementSetAttributeValue(window, kAXPositionAttribute, value);
            usleep(500000);
        }
        AXValueRef positionValue, sizeValue;
        CGPoint position;
        CGSize size;
        AXUIElementCopyAttributeValue(window, kAXPositionAttribute, (CFTypeRef *) &positionValue);
        AXUIElementCopyAttributeValue(window, kAXSizeAttribute, (CFTypeRef *) &sizeValue);
        AXValueGetValue(positionValue, kAXValueCGPointType, &position);
        AXValueGetValue(sizeValue, kAXValueCGSizeType, &size);
        printf("%.0f %.0f %.0f %.0f\n", position.x, position.y, size.width, size.height);
    }
    return 0;
}
