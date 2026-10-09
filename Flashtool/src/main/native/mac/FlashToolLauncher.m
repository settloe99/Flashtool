#import <Foundation/Foundation.h>

#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>

int main(int argc, const char *argv[]) {
    @autoreleasepool {

        NSFileManager *fm = [NSFileManager defaultManager];

        // Locate the application bundle.
        NSString *bundlePath = [[NSBundle mainBundle] bundlePath];

        NSString *workingDir =
            [bundlePath stringByAppendingPathComponent:
                @"Contents/Resources/Flashtool"];

        NSString *javaPath =
            [bundlePath stringByAppendingPathComponent:
                @"Contents/runtime/Contents/Home/bin/java"];

        NSString *jniPath =
            [workingDir stringByAppendingPathComponent:
                @"x10flasher_native/udev"];

        if (![fm isExecutableFileAtPath:javaPath]) {
            fprintf(stderr,
                "Flashtool: Bundled Java runtime not found.\n");
            return 1;
        }

        // Build the Java classpath from bundled JAR files.
        NSMutableArray<NSString *> *jars =
            [NSMutableArray array];

        NSDirectoryEnumerator *enumerator =
            [fm enumeratorAtPath:workingDir];

        NSString *relativePath;

        while ((relativePath = [enumerator nextObject])) {

            if ([[[relativePath pathExtension] lowercaseString]
                    isEqualToString:@"jar"]) {

                NSString *fullPath =
                    [workingDir stringByAppendingPathComponent:
                        relativePath];

                BOOL isDirectory = NO;

                if ([fm fileExistsAtPath:fullPath
                             isDirectory:&isDirectory]
                        && !isDirectory) {

                    [jars addObject:fullPath];
                }
            }
        }

        if ([jars count] == 0) {
            fprintf(stderr,
                "Flashtool: No Java libraries found.\n");
            return 1;
        }

        [jars sortUsingSelector:@selector(compare:)];

        NSString *classpath =
            [jars componentsJoinedByString:@":"];

        // Preserve Flashtool's expected working directory.
        if (chdir([workingDir fileSystemRepresentation]) != 0) {
            fprintf(stderr,
                "Flashtool: Cannot set working directory: %s\n",
                strerror(errno));
            return 1;
        }

        setenv("working_dir",
            [workingDir fileSystemRepresentation], 1);

        setenv("JAVA_HOME",
            [[bundlePath stringByAppendingPathComponent:@"Contents/runtime/Contents/Home"]
                fileSystemRepresentation], 1);

        setenv("JNI_PATH",
            [jniPath fileSystemRepresentation], 1);

        setenv("os_name", "Darwin", 1);
        setenv("os_platform", "arm64", 1);

        unsetenv("COCOA");

        NSString *javaLibraryPath =
            [@"-Djava.library.path=" stringByAppendingString:jniPath];

        NSString *jnaLibraryPath =
            [@"-Djna.library.path=" stringByAppendingString:jniPath];

        // Start Java in the current process.
        // This preserves SWT's first-thread requirement.
        char *const javaArgs[] = {
            (char *)[javaPath fileSystemRepresentation],

            "-classpath",
            (char *)[classpath UTF8String],

            "-XstartOnFirstThread",

            "-Xms256m",
            "-Xmx1024m",

            "-Duser.country=US",
            "-Duser.language=en",

            (char *)[javaLibraryPath UTF8String],
            (char *)[jnaLibraryPath UTF8String],

            "-Djsse.enableSNIExtension=false",

            "org.flashtool.gui.Main",

            NULL
        };

        execv([javaPath fileSystemRepresentation], javaArgs);

        // Only reached if Java could not start.
        fprintf(stderr,
            "Flashtool: Failed to launch Java: %s\n",
            strerror(errno));

        return 1;
    }
}
