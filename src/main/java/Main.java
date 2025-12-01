import engine.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Main {
    private static final AtomicBoolean shouldExit = new AtomicBoolean(false);
    private static final int UPDATE_INTERVAL_MS = 3000;

    public static void main(String[] args) {
        // Отключаем warning от jSensors о правах администратора
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");

        printWelcomeMessage();

        // Создаем сервис
        SystemInfoService service = new SystemInfoService(new OshiSensorProvider());

        // Создаем и запускаем поток для отслеживания ввода
        startInputThread();

        // Запускаем мониторинг
        runMonitoringLoop(service);
    }

    private static void printWelcomeMessage() {
        System.out.println("=== System Monitor ===");
        System.out.println("ВНИМАНИЕ: Антивирус может выдавать предупреждение");
        System.out.println("Это нормально, так как программа читает системную информацию.");
        System.out.println("Программа безопасна. При необходимости добавьте исключение в антивирусе.\n");
        System.out.println("Запуск мониторинга системы...\n");
        System.out.println("Для выхода нажмите 'q' и затем Enter\n");
    }

    private static void startInputThread() {
        Thread inputThread = new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (!shouldExit.get() && scanner.hasNextLine()) {
                    String input = scanner.nextLine().trim().toLowerCase();
                    if (input.equals("q") || input.equals("quit") || input.equals("exit")) {
                        System.out.println("\nВыход по запросу пользователя...");
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

                // Получаем и отображаем данные
                displaySystemInfo(service, prevCpuLoad, prevGpuLoad, firstIteration);

                // Обновляем предыдущие значения
                CpuInfo cpu = service.readCpu();
                GpuInfo gpu = service.readGpu();
                prevCpuLoad = Math.min(100.0, cpu.usage);
                prevGpuLoad = gpu.gpuLoad;
                firstIteration = false;

                // Ждем до следующего обновления, проверяя флаг выхода
                waitForNextUpdate(iterationStartTime);
            }
        } catch (Exception e) {
            System.err.println("\nОшибка получения данных: " + e.getMessage());
            System.err.println("Программа завершена.");
        } finally {
            System.out.println("Программа завершена.");
        }
    }

    private static void displaySystemInfo(SystemInfoService service,
                                          double prevCpuLoad, double prevGpuLoad,
                                          boolean firstIteration) throws Exception {
        // Получаем данные
        CpuInfo cpu = service.readCpu();
        RamInfo ram = service.readRam();
        GpuInfo gpu = service.readGpu();

        // Очищаем консоль
        clearConsole();

        // Выводим заголовок
        System.out.println("=== System Monitor ===");
        System.out.println("Обновлено: " + LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println("Для выхода введите 'q' и нажмите Enter");
        System.out.println();

        // CPU информация
        displayCpuInfo(cpu, prevCpuLoad, firstIteration);

        // RAM информация
        displayRamInfo(ram);

        // GPU информация
        displayGpuInfo(gpu, prevGpuLoad, firstIteration);

        // Статистика использования
        displayUsageStatistics(cpu, ram, gpu);

        // Разделитель и подсказка
        System.out.println("\n════════════════════════════════════════\n");
        System.out.println("Для выхода введите 'q' и нажмите Enter");
        System.out.println("Обновление каждые 3 секунды...");
    }

    private static void displayCpuInfo(CpuInfo cpu, double prevCpuLoad, boolean firstIteration) {
        double cpuLoad = Math.min(100.0, cpu.usage);

        System.out.println("CPU");
        System.out.printf("Загрузка: %.1f%%\n", cpuLoad);
        System.out.printf("Частота: %.0f MHz\n\n", cpu.baseClock);

        System.out.print("CPU [");
        printProgressBar(cpuLoad);
        System.out.printf("] %.1f%%", cpuLoad);

        if (!firstIteration && prevCpuLoad >= 0) {
            double diff = cpuLoad - prevCpuLoad;
            System.out.printf(" (%+.1f%%)", diff);
        }
        System.out.println("\n");
    }

    private static void displayRamInfo(RamInfo ram) {
        long usedMB = ram.used / 1024 / 1024;
        long totalMB = ram.total / 1024 / 1024;
        long freeMB = ram.free / 1024 / 1024;
        double usagePercent = Math.min(100.0, (double) usedMB / totalMB * 100);

        System.out.println("RAM");
        System.out.printf("Использовано: %,d MB\n", usedMB);
        System.out.printf("Всего: %,d MB\n", totalMB);
        System.out.printf("Свободно: %,d MB\n", freeMB);
        System.out.printf("Использование: %.1f%%\n\n", usagePercent);

        System.out.print("RAM [");
        printProgressBar(usagePercent);
        System.out.printf("] %.1f%%\n\n", usagePercent);
    }

    private static void displayGpuInfo(GpuInfo gpu, double prevGpuLoad, boolean firstIteration) {
        System.out.println("GPU");

        // Название GPU
        String gpuName = (gpu.name == null || gpu.name.trim().isEmpty())
                ? "Не определено"
                : gpu.name;
        System.out.println(gpuName);

        // VRAM информация
        if (gpu.vramTotal > 0) {
            long vramUsedMB = gpu.vramUsed / 1024 / 1024;
            long vramTotalMB = gpu.vramTotal / 1024 / 1024;
            double vramUsagePercent = (double) vramUsedMB / vramTotalMB * 100;

            System.out.printf("VRAM: %,d / %,d MB (%.1f%%)\n",
                    vramUsedMB, vramTotalMB, vramUsagePercent);
        } else {
            System.out.println("VRAM: информация недоступна");
        }

        // Загрузка GPU
        System.out.printf("Загрузка GPU: %.1f%%\n\n", gpu.gpuLoad);

        // Индикатор GPU
        System.out.print("GPU [");
        printProgressBar(gpu.gpuLoad);
        System.out.printf("] %.1f%%", gpu.gpuLoad);

        if (!firstIteration && prevGpuLoad >= 0) {
            double diff = gpu.gpuLoad - prevGpuLoad;
            System.out.printf(" (%+.1f%%)", diff);
        }
        System.out.println("\n");
    }

    private static void displayUsageStatistics(CpuInfo cpu, RamInfo ram, GpuInfo gpu) {
        double cpuLoad = Math.min(100.0, cpu.usage);
        long usedMB = ram.used / 1024 / 1024;
        long totalMB = ram.total / 1024 / 1024;
        double ramUsagePercent = Math.min(100.0, (double) usedMB / totalMB * 100);

        System.out.println("Статистика");
        System.out.printf("CPU: %.1f%% | RAM: %.1f%% | GPU: %.1f%%",
                cpuLoad, ramUsagePercent, gpu.gpuLoad);
        System.out.println();
    }

    private static void waitForNextUpdate(long iterationStartTime) {
        long elapsedTime = System.currentTimeMillis() - iterationStartTime;
        long remainingTime = UPDATE_INTERVAL_MS - elapsedTime;

        if (remainingTime > 0) {
            try {
                // Используем один sleep вместо цикла
                Thread.sleep(Math.min(remainingTime, 100));
            } catch (InterruptedException e) {
                shouldExit.set(true);
                Thread.currentThread().interrupt();
            }
        }
    }

    // Метод для отрисовки прогресс-бара
    private static void printProgressBar(double percentage) {
        final int BAR_LENGTH = 30;
        int bars = (int) (Math.min(100.0, percentage) / (100.0 / BAR_LENGTH));
        bars = Math.min(bars, BAR_LENGTH);

        for (int i = 0; i < BAR_LENGTH; i++) {
            if (i < bars) {
                if (percentage > 80) {
                    System.out.print("█");
                } else if (percentage > 50) {
                    System.out.print("▓");
                } else {
                    System.out.print("░");
                }
            } else {
                System.out.print(" ");
            }
        }
    }

    // Метод для очистки консоли
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