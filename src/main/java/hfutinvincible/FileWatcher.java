package hfutinvincible;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class FileWatcher {
    private List<Runnable> callbacks;

    public FileWatcher(String folderPath, String fileName) {
        this.callbacks = new ArrayList<>();
        Thread thread = new Thread(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                Path path = Paths.get(folderPath);
                path.register(watchService, ENTRY_MODIFY);
                boolean poll = true;
                while (poll) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind().equals(ENTRY_MODIFY)) {
                            String eventFileName = event.context().toString();
                            if (fileName.equals(eventFileName)) { // 我监听的文件发生了更改
                                for (Runnable cb : callbacks) {
                                    cb.run();
                                }
                            }
                        }
                    }
                    poll = key.reset();
                }
            } catch (Exception e) {
                // ignored
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void addEventListener(Runnable cb) {
        this.callbacks.add(cb);
    }
}
