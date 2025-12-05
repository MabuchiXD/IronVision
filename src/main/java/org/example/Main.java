package org.example;

import engine.*;
import java.util.Scanner;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Main {
    private static final AtomicBoolean shouldExit = new AtomicBoolean(false);
    private static final int UPDATE_INTERVAL_MS = 3000;

    public static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");
        printWelcomeMessage();

        SystemInfoService service = SystemInfoService.createDefault();
        startInputThread();
        runMonitoringLoop(service);
    }

    private static void printWelcomeMessage() {
        System.out.println("=== System Monitor ===");
        System.out.println("Для выхода нажмите 'q' и затем Enter\n");
    }

    private static void startInputThread() {
        Thread inputThread = new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (!shouldExit.get() && scanner.hasNextLine()) {
                    String input = scanner.nextLine().trim().toLowerCase();
                    if (input.equals("q") || input.equals("quit") || input.equals("exit")) {
                        System.out.println("\nВыход...");
                        shouldExit.set(true);
                        break;
                    }
                }
            }
        });
        inputThread.setDaemon(true);
        inputThread.start();
    }

    private static void runMonitoringLoop(SystemInfoService service) {
        double prevCpuLoad = -1;
        double prevGpuLoad = -1;
        boolean firstIteration = true;

        try {
            while (!shouldExit.get()) {
                long iterationStartTime = System.currentTimeMillis();
                displaySystemInfo(service, prevCpuLoad, prevGpuLoad, firstIteration);

                CpuInfo cpu = service.readCpu();
                GpuInfo gpu = service.readGpu();
                prevCpuLoad = cpu.getUsage();
                prevGpuLoad = gpu.getGpuLoad();
                firstIteration = false;

                waitForNextUpdate(iterationStartTime);
            }
        } catch (Exception e) {
            System.err.println("\nОшибка: " + e.getMessage());
        } finally {
            System.out.println("Программа завершена.");
        }
    }

    private static void displaySystemInfo(SystemInfoService service,
                                          double prevCpuLoad, double prevGpuLoad,
                                          boolean firstIteration) throws Exception {
        CpuInfo cpu = service.readCpu();
        RamInfo ram = service.readRam();
        GpuInfo gpu = service.readGpu();
        List<DiskInfo> disks = service.readDisks();

        clearConsole();

        System.out.println("=== System Monitor ===");
        System.out.println("Время: " + LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println();

        displayCpuInfo(cpu, prevCpuLoad, firstIteration);
        displayRamInfo(ram);
        displayGpuInfo(gpu, prevGpuLoad, firstIteration);
        displayDiskInfo(disks);

        System.out.println("\n════════════════════════════════════════");
        System.out.println("Для выхода введите 'q' и нажмите Enter");
        System.out.println("Обновление каждые 3 секунды...");
    }

    private static void displayCpuInfo(CpuInfo cpu, double prevCpuLoad, boolean firstIteration) {
        double cpuLoad = cpu.getUsage();

        System.out.println("CPU");
        System.out.printf("Модель: %s\n", cpu.getName());
        System.out.printf("Загрузка: %s\n", cpu.getFormattedUsage());
        System.out.printf("Частота: %s\n", cpu.getFormattedClock());
        System.out.printf("Ядра: %d\n", cpu.getCores());

        System.out.print(" [");
        printProgressBar(cpuLoad);
        System.out.printf("] %s", cpu.getFormattedUsage());

        if (!firstIteration && prevCpuLoad >= 0) {
            double diff = cpuLoad - prevCpuLoad;
            System.out.printf(" (%+.1f%%)", diff);
        }
        System.out.println("\n");
    }

    private static void displayRamInfo(RamInfo ram) {
        System.out.println("RAM");
        System.out.printf("Использовано: %s\n", ram.getFormattedUsed());
        System.out.printf("Всего: %s\n", ram.getFormattedTotal());
        System.out.printf("Свободно: %s\n", ram.getFormattedFree());
        System.out.printf("Использование: %.1f%%\n", ram.getUsagePercent());

        System.out.print(" [");
        printProgressBar(ram.getUsagePercent());
        System.out.printf("] %.1f%%\n\n", ram.getUsagePercent());
    }

    private static void displayGpuInfo(GpuInfo gpu, double prevGpuLoad, boolean firstIteration) {
        System.out.println("GPU");
        System.out.printf("Модель: %s\n", gpu.getName());

        if (gpu.getVramTotal() > 0) {
            long totalMB = gpu.getVramTotal() / 1024 / 1024;
            long usedMB = gpu.getVramUsed() / 1024 / 1024;
            double percent = (double) usedMB / totalMB * 100;
            System.out.printf("VRAM: %,d / %,d MB (%.1f%%)\n", usedMB, totalMB, percent);
        }

        System.out.printf("Загрузка GPU: %.1f%%\n", gpu.getGpuLoad());

        System.out.print(" [");
        printProgressBar(gpu.getGpuLoad());
        System.out.printf("] %.1f%%", gpu.getGpuLoad());

        if (!firstIteration && prevGpuLoad >= 0) {
            double diff = gpu.getGpuLoad() - prevGpuLoad;
            System.out.printf(" (%+.1f%%)", diff);
        }
        System.out.println("\n");
    }

    private static void displayDiskInfo(List<DiskInfo> disks) {
        System.out.println("ДИСКИ");

        if (disks == null || disks.isEmpty()) {
            System.out.println("Информация о дисках недоступна\n");
            return;
        }

        for (int i = 0; i < disks.size(); i++) {
            DiskInfo disk = disks.get(i);
            System.out.printf("%s\n", disk.getName());
            System.out.printf("  Всего: %s, Свободно: %s\n",
                    disk.getFormattedTotalSpace(), disk.getFormattedFreeSpace());

            System.out.print("  [");
            printProgressBar(disk.getUsagePercent());
            System.out.printf("] %.1f%%\n\n", disk.getUsagePercent());
        }
    }

    private static void waitForNextUpdate(long iterationStartTime) {
        long elapsedTime = System.currentTimeMillis() - iterationStartTime;
        long remainingTime = UPDATE_INTERVAL_MS - elapsedTime;

        if (remainingTime > 0) {
            try {
                Thread.sleep(Math.min(remainingTime, 100));
            } catch (InterruptedException e) {
                shouldExit.set(true);
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void printProgressBar(double percentage) {
        final int BAR_LENGTH = 30;
        int bars = (int) (Math.min(100.0, percentage) / (100.0 / BAR_LENGTH));
        bars = Math.min(bars, BAR_LENGTH);

        for (int i = 0; i < BAR_LENGTH; i++) {
            if (i < bars) {
                System.out.print(percentage > 80 ? "█" : percentage > 50 ? "▓" : "░");
            } else {
                System.out.print(" ");
            }
        }
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            System.out.println("\n\n════════════════════════════════════════\n");
        }
    }
}