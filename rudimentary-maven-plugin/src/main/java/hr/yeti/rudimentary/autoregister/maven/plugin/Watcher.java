package hr.yeti.rudimentary.autoregister.maven.plugin;

import com.sun.nio.file.SensitivityWatchEventModifier;
import hr.yeti.rudimentary.autoregister.maven.plugin.mojo.RunMojo;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Watcher {

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private final boolean recursive;
    private boolean trace = false;

    private RunMojo cmd;

    private AtomicInteger buildCounter = new AtomicInteger(1);

    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(
            watcher,
            new WatchEvent.Kind[]{ ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY },
            SensitivityWatchEventModifier.HIGH
        );

        if (trace) {
            Path prev = keys.get(key);
            if (Objects.isNull(prev)) {
                System.out.format("Register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("Update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    private void registerAll(final Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
                if (!dir.startsWith("target")) {
                    register(dir);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    public Watcher(Path dir, boolean recursive, RunMojo cmd) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>();
        this.recursive = recursive;
        this.cmd = cmd;

        if (recursive) {
            registerAll(dir);
        } else {
            register(dir);
        }

        this.trace = false;
    }

    public void processEvents() throws IOException, InterruptedException {
        for (;;) {

            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException ex) {
                return;
            }

            Path dir = keys.get(key);
            if (Objects.isNull(dir)) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }
            boolean reload = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                if (kind == OVERFLOW) {
                    continue;
                }

                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        }
                    } catch (IOException e) {
                        // Noop.
                    }
                }

                reload = ((child.toString().startsWith("src") && (!child.toString().startsWith("src/test")))
                    || child.toString().startsWith("pom.xml"));
            }

            if (reload) {
                System.out.println(System.lineSeparator() + "[Iteration #" + buildCounter.getAndIncrement() + "]");

                if (cmd.mavenCompileProject()) {
                    if (Objects.nonNull(cmd.pid)) {
                        cmd.consoleReader.setStop(true);
                        ProcessHandle.of(Long.valueOf(cmd.pid)).get().destroy();
                        cmd.pid = null;
                    }

                    cmd.mavenRunRudyApplication();
                    cmd.readProcessStdOut();
                }

            }

            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

}
