package engine.libre;

import java.io.*;
import java.nio.file.*;

public class LibreHardwareMonitorLauncher {

    private static final String EXECUTABLE = "LibreHardwareMonitor.exe";

    public static void startIfNeeded() {
        try {
            // Проверяем, запущен ли уже
            if (isRunning()) return;

            // Куда разархивировать exe
            Path tempDir = Paths.get(System.getProperty("user.home"), ".hardware_sensors");
            Files.createDirectories(tempDir);

            Path exePath = tempDir.resolve(EXECUTABLE);

            // Копируем из ресурсов, если его там ещё нет
            if (!Files.exists(exePath)) {
                try (InputStream in = LibreHardwareMonitorLauncher.class
                        .getClassLoader()
                        .getResourceAsStream("sensors/" + EXECUTABLE)) {

                    if (in == null) throw new RuntimeException("LHM exe not found inside resources!");

                    Files.copy(in, exePath);
                }
            }

            // Запускаем тихо в фоне
            new ProcessBuilder(exePath.toString())
                    .directory(tempDir.toFile())
                    .start();

            System.out.println("LibreHardwareMonitor started in background");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isRunning() {
        return ProcessHandle.allProcesses()
                .anyMatch(p -> p.info().command().orElse("").toLowerCase()
                        .contains("librehardwaremonitor"));
    }
}
